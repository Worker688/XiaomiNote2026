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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 工作便签类（核心业务类）
 * 作用：封装【当前正在编辑/查看】的单条便签的所有业务逻辑
 * 负责：从数据库加载便签、编辑内容、保存修改、监听便签设置变化、桌面小部件关联
 * 是 UI 层与数据模型（Note）之间的桥梁
 */
public class WorkingNote {
    // 数据模型对象（负责底层数据库操作）
    private Note mNote;
    // 当前便签ID
    private long mNoteId;
    // 便签文本内容
    private String mContent;
    // 便签模式：普通/清单模式
    private int mMode;

    // 提醒时间
    private long mAlertDate;
    // 修改时间
    private long mModifiedDate;
    // 背景色ID
    private int mBgColorId;
    // 绑定的桌面小部件ID
    private int mWidgetId;
    // 桌面小部件类型
    private int mWidgetType;
    // 所属文件夹ID
    private long mFolderId;

    // 【新增】置顶状态
    private boolean mPinned;

    // 上下文
    private Context mContext;
    // 日志TAG
    private static final String TAG = "WorkingNote";
    // 标记是否已删除
    private boolean mIsDeleted;
    // 便签设置变化监听器（背景色、提醒、清单模式等）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // ====================== 数据库查询投影 ======================
    // 查询 data 表需要的字段（ID、内容、类型、模式等）
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询 note 表需要的字段（文件夹、提醒、背景色、小部件等）
    // 【修改】添加了 PINNED 字段
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.PINNED // 【新增】
    };

    // ====================== 字段索引常量 ======================
    // data 表索引
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;

    // note 表索引
    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;
    private static final int NOTE_PINNED_COLUMN = 6; // 【新增】

    /**
     * 构造方法：创建【新空白便签】
     * @param context 上下文
     * @param folderId 所属文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;          // 默认无提醒
        mModifiedDate = System.currentTimeMillis(); // 修改时间为当前时间
        mFolderId = folderId;    // 所属文件夹
        mNote = new Note();      // 初始化数据模型
        mNoteId = 0;             // 新便签ID为0（未存入数据库）
        mIsDeleted = false;      // 未删除
        mMode = 0;               // 默认普通模式
        mPinned = false;         // 【新增】默认不置顶
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE; // 默认无桌面小部件
    }

    /**
     * 构造方法：加载【数据库中已存在的便签】
     * @param context 上下文
     * @param noteId 便签ID
     * @param folderId 文件夹ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mPinned = false; // 【新增】初始化默认值
        mNote = new Note();
        loadNote(); // 从数据库加载便签信息
    }

    /**
     * 加载便签基础信息（文件夹、背景色、小部件、提醒等）
     */
    private void loadNote() {
        // 查询 note 表
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                // 读取字段值
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
                // 【新增】读取置顶状态
                mPinned = cursor.getInt(NOTE_PINNED_COLUMN) > 0;
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        // 继续加载便签内容数据
        loadNoteData();
    }

    /**
     * 加载便签内容数据（文本、通话记录）
     */
    private void loadNoteData() {
        // 根据 noteId 查询 data 表
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                        String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    // 普通文本便签
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    }
                    // 通话记录便签
                    else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    }
                    // 未知类型
                    else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 静态方法：创建空白新便签（供外部调用）
     * 用于从桌面小部件创建新便签
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 静态方法：根据ID从数据库加载已有便签
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 同步保存便签到数据库（核心保存方法）
     * @return 保存成功/失败
     */
    public synchronized boolean saveNote() {
        // 判断是否需要保存
        if (isWorthSaving()) {
            // 不存在数据库 → 创建新便签并获取ID
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 调用数据模型同步到数据库
            mNote.syncNote(mContext, mNoteId);

            // 如果绑定了桌面小部件 → 更新小部件显示
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断便签是否已存在于数据库（ID>0表示已保存）
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断是否需要执行保存操作
     * 不需要保存的情况：
     * 1. 已删除
     * 2. 新建便签但内容为空
     * 3. 已存在但无任何修改
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置便签设置变化监听器
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒时间
     * @param date 提醒时间戳
     * @param set 是否开启提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记便签为删除状态
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        // 删除后更新小部件
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置便签背景色
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置清单模式/普通模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 【新增】设置置顶状态
     * @param pinned true为置顶，false为取消置顶
     */
    public void setPinned(boolean pinned) {
        if (mPinned != pinned) {
            mPinned = pinned;
            // 将状态存入数据模型，标记为需要同步
            mNote.setNoteValue(NoteColumns.PINNED, mPinned ? "1" : "0");
        }
    }

    /**
     * 设置桌面小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置桌面小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置便签文本内容（编辑时调用）
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 转换为通话记录便签
     * 自动存入通话号码、日期，并移动到通话记录文件夹
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 判断是否设置了提醒
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    // ====================== 各种 Getter 方法 ======================
    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    // 获取背景色资源ID
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    // 获取标题栏背景资源ID
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    // 【新增】获取置顶状态
    public boolean isPinned() {
        return mPinned;
    }

    /**
     * 便签设置变化监听器接口
     * 用于 UI 层监听便签状态变化并刷新界面
     */
    public interface NoteSettingChangedListener {
        // 背景色改变
        void onBackgroundColorChanged();
        // 提醒设置改变
        void onClockAlertChanged(long date, boolean set);
        // 桌面小部件改变
        void onWidgetChanged();
        // 清单/普通模式切换
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}