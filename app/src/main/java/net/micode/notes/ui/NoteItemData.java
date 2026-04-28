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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记列表项的数据封装类
 * 用于从数据库游标中提取单条笔记或文件夹的信息，并提供便捷的访问方法。
 * 同时记录该项在列表中的位置关系（是否第一个、最后一个、仅有一项等），
 * 以及处理通话记录笔记的姓名和电话号码。
 */
public class NoteItemData {
    // 查询笔记列表时所需的数据库列名投影
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
    };

    // 各列在投影中的索引常量，便于快速访问游标
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    // 笔记/文件夹的基本属性
    private long mId;                // 笔记或文件夹的ID
    private long mAlertDate;         // 提醒时间（0表示无提醒）
    private int mBgColorId;          // 背景颜色资源ID
    private long mCreatedDate;       // 创建时间
    private boolean mHasAttachment;  // 是否包含附件
    private long mModifiedDate;      // 最后修改时间
    private int mNotesCount;         // 如果是文件夹，表示其中笔记的数量
    private long mParentId;          // 父文件夹ID
    private String mSnippet;         // 内容摘要（去除勾选框标记后的纯文本）
    private int mType;               // 类型：笔记(NOTE)或文件夹(FOLDER)等
    private int mWidgetId;           // 关联的桌面小部件ID
    private int mWidgetType;         // 小部件类型（2x2或4x4）

    private String mName;            // 通话记录笔记的联系人姓名
    private String mPhoneNumber;     // 通话记录笔记的电话号码

    // 列表位置状态标志
    private boolean mIsLastItem;                 // 是否为列表中的最后一项
    private boolean mIsFirstItem;                // 是否为列表中的第一项
    private boolean mIsOnlyOneItem;              // 列表是否只有这一项
    private boolean mIsOneNoteFollowingFolder;   // 是否为某个文件夹后的唯一笔记
    private boolean mIsMultiNotesFollowingFolder; // 是否为某个文件夹后的多个笔记中的第一个

    /**
     * 构造方法：从游标当前行读取数据，并初始化各项属性
     * @param context 上下文，用于获取联系人信息
     * @param cursor  数据库游标，已指向需要读取的行
     */
    public NoteItemData(Context context, Cursor cursor) {
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 去除摘要中的待办清单标记（√和□），保持界面简洁
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        mPhoneNumber = "";
        // 如果笔记属于“通话记录”特殊文件夹，则获取对应的电话号码和联系人姓名
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber; // 若无联系人姓名，则直接显示号码
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }

    /**
     * 检查当前项在列表中的位置关系（基于游标位置和前后项类型）
     * 主要判断是否为某个文件夹后的第一个笔记，以及是否唯一等，用于界面中显示间距或分割线
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 当当前项是笔记且不是列表第一项时，检查前一项是否为文件夹或系统项
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true; // 文件夹后跟多个笔记
                    } else {
                        mIsOneNoteFollowingFolder = true;   // 文件夹后只跟一个笔记
                    }
                }
                // 将游标移回原来的位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // ===================== 位置判断方法 =====================
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    // ===================== 通话记录相关方法 =====================
    public String getCallName() {
        return mName;
    }

    // ===================== 属性获取方法 =====================
    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 判断是否为通话记录笔记（属于通话记录文件夹且电话号码非空）
     */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 静态工具方法：从游标中直接获取笔记类型，无需构造完整对象
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}