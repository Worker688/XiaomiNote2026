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

package net.micode.notes.data;

import android.net.Uri;

/**
 * 【核心作用】
 * 这是小米便签的 **数据契约类 / 数据库常量定义类**
 * 统一管理：数据库表名、字段名、类型、URI、文件夹ID、Intent参数
 * 相当于整个APP的数据“字典”，所有地方读写数据都靠它
 */
public class Notes {
    // 内容提供者的授权名（Android系统识别这个APP的唯一标识）
    public static final String AUTHORITY = "micode_notes";
    // 日志TAG
    public static final String TAG = "Notes";

    // ====================== 数据类型常量 ======================
    public static final int TYPE_NOTE     = 0;    // 普通便签
    public static final int TYPE_FOLDER   = 1;    // 文件夹
    public static final int TYPE_SYSTEM   = 2;    // 系统类型（特殊用途）

    /**
     * 系统内置固定文件夹ID
     * 整个APP的文件夹结构都在这里定义
     */
    public static final int ID_ROOT_FOLDER = 0;          // 根文件夹（默认主目录）
    public static final int ID_TEMPARAY_FOLDER = -1;     // 临时文件夹（无分类便签）
    public static final int ID_CALL_RECORD_FOLDER = -2;   // 通话记录便签文件夹
    public static final int ID_TRASH_FOLER = -3;          // 回收站文件夹

    // ====================== Intent 传参键名 ======================
    // 页面跳转时携带的数据参数名，统一管理，避免写错
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ====================== 桌面部件类型 ======================
    public static final int TYPE_WIDGET_INVALIDE      = -1;   // 无效部件
    public static final int TYPE_WIDGET_2X            = 0;    // 2x大小小部件
    public static final int TYPE_WIDGET_4X            = 1;    // 4x大小小部件

    // 数据类型常量（文本便签 / 通话便签）
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // ====================== 访问数据库的 URI ======================
    /**
     * 查询所有便签 + 文件夹的 URI
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询便签具体内容数据的 URI
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    // ====================== 便签/文件夹 表字段 ======================
    public interface NoteColumns {
        public static final String ID = "_id";                  // 唯一ID（主键）
        public static final String PARENT_ID = "parent_id";     // 父文件夹ID
        public static final String CREATED_DATE = "created_date"; // 创建时间
        public static final String MODIFIED_DATE = "modified_date"; // 修改时间
        public static final String ALERTED_DATE = "alert_date"; // 提醒时间
        public static final String SNIPPET = "snippet";         // 便签摘要/文件夹名称
        public static final String WIDGET_ID = "widget_id";    // 桌面部件ID
        public static final String WIDGET_TYPE = "widget_type"; // 部件类型
        public static final String BG_COLOR_ID = "bg_color_id"; // 背景色ID
        public static final String HAS_ATTACHMENT = "has_attachment"; // 是否有附件
        public static final String NOTES_COUNT = "notes_count"; // 文件夹内便签数量
        public static final String TYPE = "type";              // 类型：便签 / 文件夹
        public static final String SYNC_ID = "sync_id";        // 同步ID（云端同步）
        public static final String LOCAL_MODIFIED = "local_modified"; // 本地是否修改过
        public static final String ORIGIN_PARENT_ID = "origin_parent_id"; // 原始父文件夹ID
        public static final String GTASK_ID = "gtask_id";      // 同步任务ID
        public static final String VERSION = "version";         // 数据版本号
    }

    // ====================== 便签内容详情表字段 ======================
    public interface DataColumns {
        public static final String ID = "_id";              // 主键ID
        public static final String MIME_TYPE = "mime_type"; // 数据类型（文本/通话）
        public static final String NOTE_ID = "note_id";     // 所属便签ID
        public static final String CREATED_DATE = "created_date"; // 创建时间
        public static final String MODIFIED_DATE = "modified_date"; // 修改时间
        public static final String CONTENT = "content";     // 便签内容正文

        // 通用扩展字段，不同类型便签复用这5个字段存不同数据
        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
    }

    // ====================== 文本便签（普通便签）结构 ======================
    public static final class TextNote implements DataColumns {
        public static final String MODE = DATA1; // 模式：普通/清单模式
        public static final int MODE_CHECK_LIST = 1; // 清单模式

        // 内容提供者URI
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    // ====================== 通话便签（自动记录通话）结构 ======================
    public static final class CallNote implements DataColumns {
        public static final String CALL_DATE = DATA1;    // 通话时间
        public static final String PHONE_NUMBER = DATA3; // 电话号码

        // 内容提供者URI
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}