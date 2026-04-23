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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 便签核心模型类
 * 作用：封装单条便签的所有数据操作（创建、修改、保存、同步数据库）
 * 包含便签基础信息 + 文本/通话附属数据
 */
public class Note {
    // 存储便签基础信息的修改内容（用于批量更新数据库）
    private ContentValues mNoteDiffValues;
    // 便签附属数据（文本内容、通话记录内容）内部类对象
    private NoteData mNoteData;
    // 日志TAG
    private static final String TAG = "Note";

    /**
     * 创建一条新便签，并返回新便签的ID（静态同步方法，线程安全）
     * @param context 上下文
     * @param folderId 便签所属文件夹ID
     * @return 新创建的便签ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 封装新便签的基础数据
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        // 创建时间
        values.put(NoteColumns.CREATED_DATE, createdTime);
        // 修改时间（新建时与创建时间一致）
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        // 便签类型：普通便签
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        // 标记为本地已修改（需要同步）
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        // 所属文件夹ID
        values.put(NoteColumns.PARENT_ID, folderId);

        // 向ContentProvider插入数据，获取返回的URI
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从URI中解析出便签ID（URI格式：content://xxx/notes/123，取第二段数字）
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }

        // ID非法则抛出异常
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造方法：初始化便签修改数据容器和附属数据对象
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置便签基础信息（标题、类型、文件夹等）
     * 自动更新：本地修改标记 + 修改时间
     * @param key 字段名
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置便签文本内容数据
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据的ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据的ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据的ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置便签中的通话记录数据
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断便签是否有本地修改（基础信息/附属内容任意一个修改都返回true）
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 同步本地修改到数据库（核心保存方法）
     * @param context 上下文
     * @param noteId 便签ID
     * @return 同步成功/失败
     */
    public boolean syncNote(Context context, long noteId) {
        // 参数合法性校验
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 无修改，直接返回成功
        if (!isLocalModified()) {
            return true;
        }

        /**
         * 理论上数据修改后会自动更新修改标记和时间
         * 为了数据安全，即使基础信息更新失败，也继续执行附属数据更新
         */
        // 更新便签基础信息到数据库
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
        }
        // 清空已同步的基础修改数据
        mNoteDiffValues.clear();

        // 如果附属数据有修改，执行同步，失败则返回false
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类：封装便签的附属数据（文本内容、通话记录内容）
     * 文本和通话数据分开存储，共用一个数据表
     */
    private class NoteData {
        // 文本数据ID
        private long mTextDataId;
        // 文本数据修改容器
        private ContentValues mTextDataValues;

        // 通话数据ID
        private long mCallDataId;
        // 通话数据修改容器
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        /**
         * 构造方法：初始化附属数据
         */
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断附属数据是否有本地修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据ID（合法性校验）
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据ID（合法性校验）
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据，并自动更新便签的修改标记和时间
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据，并自动更新便签的修改标记和时间
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将附属数据（文本/通话）同步到数据库
         * 逻辑：无ID则插入，有ID则更新
         * @param context 上下文
         * @param noteId 所属便签ID
         * @return 同步成功返回URI，失败返回null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            // 参数校验
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            // 批量操作列表（用于原子性更新数据库）
            ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
            ContentProviderOperation.Builder builder = null;

            // ============= 处理文本数据 =============
            if(mTextDataValues.size() > 0) {
                // 绑定所属便签ID
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 无ID：新增文本数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mTextDataValues);
                    try {
                        // 解析并保存新生成的ID
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 有ID：更新已有文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                // 清空已同步数据
                mTextDataValues.clear();
            }

            // ============= 处理通话数据 =============
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 无ID：新增通话数据
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI, mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 有ID：更新已有通话数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 执行批量数据库操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    // 返回结果判断
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}