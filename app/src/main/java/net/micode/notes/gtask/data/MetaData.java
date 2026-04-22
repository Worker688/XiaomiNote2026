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

import android.database.Cursor; // 导入数据库游标类
import android.util.Log; // 导入日志工具类

import net.micode.notes.tool.GTaskStringUtils; // 导入GTask字符串常量工具类

import org.json.JSONException; // 导入JSON异常类
import org.json.JSONObject; // 导入JSON对象类

// 元数据类，继承自Task，专门用于存储同步关联信息
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName(); // 日志标签

    private String mRelatedGid = null; // 存储关联的云端任务GID

    // 设置元数据：将GID存入JSON，并把JSON作为备注保存
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid); // 向JSON中放入关联GID
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid"); // 捕获放入GID失败异常
        }
        setNotes(metaInfo.toString()); // 将JSON字符串设置为任务备注
        setName(GTaskStringUtils.META_NOTE_NAME); // 设置固定的元数据名称
    }

    // 获取关联的GID
    public String getRelatedGid() {
        return mRelatedGid;
    }

    // 重写是否值得保存方法：只要备注不为空就保存
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    // 重写远程JSON解析方法：先调用父类方法，再解析关联GID
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js); // 调用父类方法解析基础数据
        if (getNotes() != null) { // 备注不为空时
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim()); // 解析备注为JSON
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID); // 取出关联GID
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid"); // 解析失败打印日志
                mRelatedGid = null; // 置空GID
            }
        }
    }

    // 重写本地JSON解析方法：不支持，抛出异常
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    // 重写获取本地JSON方法：不支持，抛出异常
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    // 重写获取同步动作方法：不支持，抛出异常
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}