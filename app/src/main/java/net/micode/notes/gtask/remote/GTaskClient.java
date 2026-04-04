/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import okhttp3.*;
import okio.BufferedSource;
import okio.GzipSource;
import okio.InflaterSource;
import okio.Okio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.JavaNetCookieJar;

public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    private static final String GTASK_URL = "https://mail.google.com/tasks/";
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    private static GTaskClient mInstance = null;

    // 替换为 OkHttpClient
    private OkHttpClient mHttpClient;

    private String mGetUrl;
    private String mPostUrl;
    private long mClientVersion;
    private boolean mLoggedin;
    private long mLastLoginTime;
    private int mActionId;
    private Account mAccount;
    private JSONArray mUpdateArray;

    private GTaskClient() {
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;

        // OkHttp 初始化 + 超时 + Cookie 管理
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(new JavaNetCookieJar(new java.net.CookieManager()))
                .build();
    }

    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    public boolean login(Activity activity) {
        final long interval = 1000 * 60 * 5;
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        if (mLoggedin && getSyncAccount() != null) {
            String current = NotesPreferenceActivity.getSyncAccountName(activity);
            if (!TextUtils.equals(getSyncAccount().name, current)) {
                mLoggedin = false;
            }
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis();
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // 非 gmail 域名尝试自定义登录地址
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com")
                || mAccount.name.toLowerCase().endsWith("googlemail.com"))) {

            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix).append("/");
            mGetUrl = url + "ig";
            mPostUrl = url + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // 官方地址登录
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            Log.e(TAG, "no google account");
            return null;
        }

        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account target = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                target = a;
                break;
            }
        }

        if (target == null) {
            Log.e(TAG, "account not found in system");
            return null;
        }
        mAccount = target;

        try {
            AccountManagerFuture<Bundle> future = accountManager.getAuthToken(
                    mAccount, "goanna_mobile", null, activity, null, null);

            Bundle bundle = future.getResult();
            String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);

            if (invalidateToken && token != null) {
                accountManager.invalidateAuthToken("com.google", token);
                return loginGoogleAccount(activity, false);
            }
            return token;

        } catch (Exception e) {
            Log.e(TAG, "getAuthToken failed", e);
            return null;
        }
    }

    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (loginGtask(authToken)) {
            return true;
        }

        // 重试：失效 token 再获取一次
        String newToken = loginGoogleAccount(activity, true);
        if (newToken == null) {
            return false;
        }
        return loginGtask(newToken);
    }

    private boolean loginGtask(String authToken) {
        String url = mGetUrl + "?auth=" + authToken;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = mHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "loginGtask HTTP code: " + response.code());
                return false;
            }

            String body = getResponseContent(response);
            if (body == null) return false;

            // 解析 _setup(...) )}</script>
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = body.indexOf(jsBegin);
            int end = body.lastIndexOf(jsEnd);

            if (begin == -1 || end == -1 || begin >= end) {
                Log.e(TAG, "setup json not found");
                return false;
            }

            String jsonStr = body.substring(begin + jsBegin.length(), end);
            JSONObject js = new JSONObject(jsonStr);
            mClientVersion = js.getLong("v");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "loginGtask exception", e);
            return false;
        }
    }

    // 统一解析响应（支持 gzip / deflate）
    private String getResponseContent(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) return null;

        String encoding = response.header("Content-Encoding");
        BufferedSource source = Okio.buffer(body.source());

        if ("gzip".equalsIgnoreCase(encoding)) {
            source = Okio.buffer(new GzipSource(source));
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            source = Okio.buffer(new InflaterSource(source, new java.util.zip.Inflater(true)));
        }

        return source.readUtf8();
    }

    private int getActionId() {
        return mActionId++;
    }

    // POST 请求封装
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            throw new ActionFailureException("not logged in");
        }

        RequestBody formBody = new FormBody.Builder()
                .add("r", js.toString())
                .build();

        Request request = new Request.Builder()
                .url(mPostUrl)
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .addHeader("AT", "1")
                .build();

        try (Response response = mHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new NetworkFailureException("HTTP failed: " + response.code());
            }

            String jsonStr = getResponseContent(response);
            return new JSONObject(jsonStr);

        } catch (JSONException e) {
            Log.e(TAG, "json parse error", e);
            throw new ActionFailureException("json parse failed");
        } catch (IOException e) {
            Log.e(TAG, "network error", e);
            throw new NetworkFailureException("network error");
        }
    }

    // ===================== 业务方法基本保持原接口不变 =====================

    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            actionList.put(task.getCreateAction(getActionId()));

            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject resp = postRequest(jsPost);
            JSONObject result = resp.getJSONArray(GTaskStringUtils.GTASK_JSON_RESULTS).getJSONObject(0);
            task.setGid(result.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            throw new ActionFailureException("createTask json error");
        }
    }

    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            actionList.put(tasklist.getCreateAction(getActionId()));

            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject resp = postRequest(jsPost);
            JSONObject result = resp.getJSONArray(GTaskStringUtils.GTASK_JSON_RESULTS).getJSONObject(0);
            tasklist.setGid(result.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            throw new ActionFailureException("createTaskList json error");
        }
    }

    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray == null || mUpdateArray.length() == 0) {
            return;
        }
        try {
            JSONObject jsPost = new JSONObject();
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;

        } catch (JSONException e) {
            throw new ActionFailureException("commitUpdate json error");
        }
    }

    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node == null) return;

        if (mUpdateArray != null && mUpdateArray.length() > 10) {
            commitUpdate();
        }
        if (mUpdateArray == null) {
            mUpdateArray = new JSONArray();
        }
        mUpdateArray.put(node.getUpdateAction(getActionId()));
    }

    public void moveTask(Task task, TaskList preParent, TaskList curParent) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE, GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());

            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }

            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());

            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            throw new ActionFailureException("moveTask json error");
        }
    }

    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            node.setDeleted(true);

            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            actionList.put(node.getUpdateAction(getActionId()));

            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;

        } catch (JSONException e) {
            throw new ActionFailureException("deleteNode json error");
        }
    }

    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            throw new ActionFailureException("not logged in");
        }

        Request request = new Request.Builder().url(mGetUrl).get().build();

        try (Response response = mHttpClient.newCall(request).execute()) {
            String body = getResponseContent(response);

            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = body.indexOf(jsBegin);
            int end = body.lastIndexOf(jsEnd);
            String jsonStr = body.substring(begin + jsBegin.length(), end);

            JSONObject js = new JSONObject(jsonStr);
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);

        } catch (Exception e) {
            throw new NetworkFailureException("getTaskLists failed");
        }
    }

    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE, GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject resp = postRequest(jsPost);
            return resp.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);

        } catch (JSONException e) {
            throw new ActionFailureException("getTaskList json error");
        }
    }

    public Account getSyncAccount() {
        return mAccount;
    }

    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}