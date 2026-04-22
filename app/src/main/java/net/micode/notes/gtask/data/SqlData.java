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

package net.micode.notes.gtask.data; // 包声明

import android.content.ContentResolver; // 内容解析器，用于访问数据库
import android.content.ContentUris; // 处理Uri与ID的工具类
import android.content.ContentValues; // 存储数据库键值对
import android.content.Context; // 上下文环境
import android.database.Cursor; // 数据库查询游标
import android.net.Uri; // 资源定位符
import android.util.Log; // 日志打印

import net.micode.notes.data.Notes; // 便签数据常量
import net.micode.notes.data.Notes.DataColumns; // 数据表字段
import net.micode.notes.data.Notes.DataConstants; // 数据类型常量
import net.micode.notes.data.Notes.NoteColumns; // 便签表字段
import net.micode.notes.data.NotesDatabaseHelper.TABLE; // 数据库表名
import net.micode.notes.gtask.exception.ActionFailureException; // 操作失败异常

import org.json.JSONException; // JSON异常
import org.json.JSONObject; // JSON对象

// 数据库数据操作类，负责与SQLite交互
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName(); // 日志TAG

    private static final int INVALID_ID = -99999; // 无效ID常量

    // 数据库查询投影列
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    public static final int DATA_ID_COLUMN = 0; // 列索引：ID

    public static final int DATA_MIME_TYPE_COLUMN = 1; // 列索引：MIME类型

    public static final int DATA_CONTENT_COLUMN = 2; // 列索引：内容

    public static final int DATA_CONTENT_DATA_1_COLUMN = 3; // 列索引：DATA1

    public static final int DATA_CONTENT_DATA_3_COLUMN = 4; // 列索引：DATA3

    private ContentResolver mContentResolver; // 内容解析器实例

    private boolean mIsCreate; // 是否为新建数据

    private long mDataId; // 数据ID

    private String mDataMimeType; // MIME类型

    private String mDataContent; // 内容

    private long mDataContentData1; // 扩展数据1

    private String mDataContentData3; // 扩展数据3

    private ContentValues mDiffDataValues; // 待更新的差异数据

    // 构造方法：创建新数据
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver(); // 获取内容解析器
        mIsCreate = true; // 标记为新建
        mDataId = INVALID_ID; // 初始化无效ID
        mDataMimeType = DataConstants.NOTE; // 默认类型为便签
        mDataContent = ""; // 内容为空
        mDataContentData1 = 0; // 扩展数据1为0
        mDataContentData3 = ""; // 扩展数据3为空
        mDiffDataValues = new ContentValues(); // 初始化差异数据
    }

    // 构造方法：从游标加载数据
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver(); // 获取内容解析器
        mIsCreate = false; // 标记为已存在
        loadFromCursor(c); // 从游标加载数据
        mDiffDataValues = new ContentValues(); // 初始化差异数据
    }

    // 从游标加载数据到成员变量
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN); // 获取ID
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN); // 获取MIME类型
        mDataContent = c.getString(DATA_CONTENT_COLUMN); // 获取内容
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN); // 获取DATA1
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN); // 获取DATA3
    }

    // 从JSON设置数据内容
    public void setContent(JSONObject js) throws JSONException {
        // 解析并设置ID
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 解析并设置MIME类型
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 解析并设置内容
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 解析并设置DATA1
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 解析并设置DATA3
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    // 将数据转为JSON
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    // 提交数据到数据库
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) { // 新建数据
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else { // 更新数据
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        mDiffDataValues.clear(); // 清空差异数据
        mIsCreate = false; // 标记为已保存
    }

    // 获取数据ID
    public long getId() {
        return mDataId;
    }
}