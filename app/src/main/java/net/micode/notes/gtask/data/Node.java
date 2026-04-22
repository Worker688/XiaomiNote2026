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

import android.database.Cursor; // 数据库查询结果游标

import org.json.JSONObject; // JSON对象类

// 抽象基类，定义任务/任务列表的通用属性与行为
public abstract class Node {
    public static final int SYNC_ACTION_NONE = 0; // 同步动作：无操作

    public static final int SYNC_ACTION_ADD_REMOTE = 1; // 同步动作：添加到远程

    public static final int SYNC_ACTION_ADD_LOCAL = 2; // 同步动作：添加到本地

    public static final int SYNC_ACTION_DEL_REMOTE = 3; // 同步动作：删除远程

    public static final int SYNC_ACTION_DEL_LOCAL = 4; // 同步动作：删除本地

    public static final int SYNC_ACTION_UPDATE_REMOTE = 5; // 同步动作：更新远程

    public static final int SYNC_ACTION_UPDATE_LOCAL = 6; // 同步动作：更新本地

    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7; // 同步动作：同步冲突

    public static final int SYNC_ACTION_ERROR = 8; // 同步动作：同步错误

    private String mGid; // 云端唯一标识ID

    private String mName; // 名称

    private long mLastModified; // 最后修改时间

    private boolean mDeleted; // 删除标记

    public Node() { // 构造方法
        mGid = null; // 初始化云端ID为空
        mName = ""; // 初始化名称为空
        mLastModified = 0; // 初始化修改时间为0
        mDeleted = false; // 初始化未删除
    }

    // 抽象方法：生成创建操作JSON
    public abstract JSONObject getCreateAction(int actionId);

    // 抽象方法：生成更新操作JSON
    public abstract JSONObject getUpdateAction(int actionId);

    // 抽象方法：从远程JSON设置数据
    public abstract void setContentByRemoteJSON(JSONObject js);

    // 抽象方法：从本地JSON设置数据
    public abstract void setContentByLocalJSON(JSONObject js);

    // 抽象方法：从内容生成本地JSON
    public abstract JSONObject getLocalJSONFromContent();

    // 抽象方法：获取同步动作
    public abstract int getSyncAction(Cursor c);

    // 设置云端ID
    public void setGid(String gid) {
        this.mGid = gid;
    }

    // 设置名称
    public void setName(String name) {
        this.mName = name;
    }

    // 设置最后修改时间
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    // 设置删除状态
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    // 获取云端ID
    public String getGid() {
        return this.mGid;
    }

    // 获取名称
    public String getName() {
        return this.mName;
    }

    // 获取最后修改时间
    public long getLastModified() {
        return this.mLastModified;
    }

    // 获取删除状态
    public boolean getDeleted() {
        return this.mDeleted;
    }

}