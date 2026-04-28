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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器，继承自CursorAdapter，用于将文件夹数据（Cursor）绑定到列表视图
 * 主要功能是为文件夹列表提供数据适配，展示文件夹名称，区分根文件夹和普通文件夹的显示文本
 */
public class FoldersListAdapter extends CursorAdapter {
    // 查询投影字段，指定需要从数据库中获取的列：文件夹ID和文件夹名称（摘要字段）
    public static final String [] PROJECTION = {
            NoteColumns.ID,
            NoteColumns.SNIPPET
    };

    // 对应PROJECTION数组的列索引：ID列索引
    public static final int ID_COLUMN   = 0;
    // 对应PROJECTION数组的列索引：名称列索引
    public static final int NAME_COLUMN = 1;

    /**
     * 构造方法，初始化文件夹列表适配器
     * @param context 上下文对象
     * @param c 包含文件夹数据的Cursor对象
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    /**
     * 创建新的列表项视图，返回自定义的FolderListItem布局容器
     * @param context 上下文对象
     * @param cursor 当前位置的Cursor数据
     * @param parent 父视图容器
     * @return 新创建的FolderListItem视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 绑定数据到列表项视图，根据Cursor数据设置文件夹名称
     * 若为根文件夹则显示预设的父文件夹文本，否则显示Cursor中的文件夹名称
     * @param view 要绑定数据的列表项视图
     * @param context 上下文对象
     * @param cursor 当前位置的Cursor数据
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 根据列表位置获取对应的文件夹名称
     * 用于外部获取指定位置的文件夹名称，区分根文件夹和普通文件夹的文本显示
     * @param context 上下文对象
     * @param position 列表项位置
     * @return 对应位置的文件夹名称（根文件夹返回预设文本，普通文件夹返回实际名称）
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /**
     * 自定义的文件夹列表项视图，继承自LinearLayout
     * 用于承载文件夹名称的显示控件，封装视图初始化和数据绑定逻辑
     */
    private class FolderListItem extends LinearLayout {
        // 显示文件夹名称的文本控件
        private TextView mName;

        /**
         * 初始化文件夹列表项视图，加载布局并获取文本控件引用
         * @param context 上下文对象
         */
        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称到文本控件
         * @param name 要显示的文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}