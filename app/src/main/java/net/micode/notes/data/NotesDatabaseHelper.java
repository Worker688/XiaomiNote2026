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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 数据库帮助类
 * 作用：创建、升级、管理便签的SQLite数据库
 * 负责：建表、创建触发器、初始化系统文件夹、数据库版本升级
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";

    // 数据库版本（用于升级）
    private static final int DB_VERSION = 5;

    // 表名常量接口
    public interface TABLE {
        public static final String NOTE = "note";       // 便签/文件夹表
        public static final String DATA = "data";       // 便签内容详情表
    }

    private static final String TAG = "NotesDatabaseHelper";
    // 单例实例（全局唯一，避免多次创建数据库连接）
    private static NotesDatabaseHelper mInstance;

    // ====================== 创建 note 表（便签/文件夹） ======================
    private static final String CREATE_NOTE_TABLE_SQL =
            "CREATE TABLE " + TABLE.NOTE + "(" +
                    NoteColumns.ID + " INTEGER PRIMARY KEY," +             // 主键ID
                    NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 父文件夹ID
                    NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," + // 提醒时间
                    NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," + // 背景色ID
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 创建时间
                    NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," + // 是否有附件
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
                    NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," + // 文件夹内便签数量
                    NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," + // 便签摘要/文件夹名
                    NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," + // 类型：便签/文件夹
                    NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," + // 桌面部件ID
                    NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," + // 部件类型
                    NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," + // 同步ID
                    NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," + // 本地是否修改
                    NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," + // 原始父ID
                    NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," + // 同步任务ID
                    NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0," + // 数据版本

                    NoteColumns.PINNED + " INTEGER NOT NULL DEFAULT 0" + // 【新增】置顶状态：0未置顶，1已置顶




                    ")";

    // ====================== 创建 data 表（便签内容详情） ======================
    private static final String CREATE_DATA_TABLE_SQL =
            "CREATE TABLE " + TABLE.DATA + "(" +
                    DataColumns.ID + " INTEGER PRIMARY KEY," + // 主键
                    DataColumns.MIME_TYPE + " TEXT NOT NULL," + // 数据类型（文本/通话）
                    DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," + // 所属便签ID
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 创建时间
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 修改时间
                    DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," + // 便签正文内容
                    DataColumns.DATA1 + " INTEGER," + // 扩展字段1
                    DataColumns.DATA2 + " INTEGER," + // 扩展字段2
                    DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," + // 扩展字段3
                    DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," + // 扩展字段4
                    DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" + // 扩展字段5
                    ")";

    // 为 data 表的 note_id 创建索引，加速查询
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                    TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ===================== 触发器：移动便签到文件夹 → 文件夹计数+1 ====================
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update "+
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    // ===================== 触发器：移动便签离开文件夹 → 文件夹计数-1 ====================
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
                    " END";

    // ===================== 触发器：新建便签 → 对应文件夹计数+1 ====================
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    // ===================== 触发器：删除便签 → 对应文件夹计数-1 ====================
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
                    " END";

    // ===================== 触发器：新增内容 → 同步更新便签摘要 ====================
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.DATA +
                    " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    // ===================== 触发器：修改内容 → 同步更新便签摘要 ====================
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    // ===================== 触发器：删除内容 → 清空便签摘要 ====================
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
                    " END";

    // ===================== 触发器：删除便签 → 级联删除内容 ====================
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
            "CREATE TRIGGER delete_data_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.DATA +
                    "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    // ===================== 触发器：删除文件夹 → 级联删除便签 ====================
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.NOTE +
                    "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    // ===================== 触发器：文件夹丢进回收站 → 里面便签一起进回收站 ====================
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.NOTE +
                    " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    // 构造方法
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // 创建 note 表 + 触发器 + 系统文件夹
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    // 重建 note 表的所有触发器
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 先删旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    // 初始化4个系统文件夹
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        // 通话记录文件夹
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 根文件夹（默认）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 临时文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 回收站文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    // 创建 data 表 + 触发器 + 索引
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    // 重建 data 表触发器
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    // 单例模式：全局只有一个数据库帮助类实例
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    // 首次创建数据库：建两张表
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    // 数据库版本升级（v1 → v2 → v3 → v4）
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;
            oldVersion++;
        }

        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        if (oldVersion == 4) {
            upgradeToV5(db);
            oldVersion++;
        }

        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    // v1升级v2：重建表
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    // v2升级v3：增加gtask_id，新增回收站
    private void upgradeToV3(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    // v3升级v4：增加版本号字段
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }

    // 【新增】v4升级v5：增加置顶字段
    private void upgradeToV5(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.PINNED
                + " INTEGER NOT NULL DEFAULT 0");
    }
}