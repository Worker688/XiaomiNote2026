/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 便签内容提供者
 * 作用：统一封装对 note.db 的所有增删改查操作
 * 全APP唯一的数据读写出口，安全、统一、可被外部调用
 */
public class NotesProvider extends ContentProvider {
    // URI 匹配器，识别要操作的是便签/内容/搜索
    private static final UriMatcher mMatcher;

    // 数据库帮助类实例
    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // 定义各种 URI 类型
    private static final int URI_NOTE            = 1;    // 操作便签列表
    private static final int URI_NOTE_ITEM       = 2;    // 操作单个便签
    private static final int URI_DATA            = 3;    // 操作便签内容数据
    private static final int URI_DATA_ITEM       = 4;    // 操作单条内容数据
    private static final int URI_SEARCH          = 5;    // 搜索便签
    private static final int URI_SEARCH_SUGGEST  = 6;    // 搜索建议

    // 初始化 URI 匹配规则
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索便签的SQL投影：格式化标题、内容、图标、点击事件
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            +R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    // 搜索便签的SQL：模糊查询，排除回收站，只查便签
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    // 初始化：获取数据库单例
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据
     * 支持：便签列表、单便签、内容数据、搜索、搜索建议
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询便签/文件夹列表
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询单个便签详情
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询内容数据列表
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询单条内容
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 系统搜索 + 搜索建议
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                // 模糊匹配查询
                searchString = String.format("%%%s%%", searchString);
                c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY, new String[] { searchString });
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 数据变化时通知刷新
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据：新增便签 / 新增内容
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入便签/文件夹
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 插入便签内容数据
                noteId = values.getAsLong(DataColumns.NOTE_ID);
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 通知数据变化，刷新界面
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据：删除便签 / 删除内容
     * 系统文件夹不可删除
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                count = db.delete(TABLE.NOTE, "(" + selection + ") AND " + NoteColumns.ID + ">0 ", selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 系统ID<=0，禁止删除
                long noteId = Long.valueOf(uri.getPathSegments().get(1));
                if (noteId > 0) {
                    count = db.delete(TABLE.NOTE, NoteColumns.ID + "=" + noteId + parseSelection(selection), selectionArgs);
                }
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 通知刷新
        if (count > 0) {
            if (deleteData) getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据：修改便签 / 修改内容
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs); // 版本+1（同步用）
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (updateData) getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    // 拼接查询条件：如果已有条件，自动加 AND
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    // 便签版本号+1，用于数据同步判断是否修改
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + id);
        }
        if (!TextUtils.isEmpty(selection)) {
            sql.append(id > 0 ? parseSelection(selection) : selection);
        }
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    // 暂未使用
    @Override
    public String getType(Uri uri) {
        return null;
    }
}