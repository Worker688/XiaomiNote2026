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

package net.micode.notes.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 笔记桌面小部件【抽象基类】
 * 作用：统一处理桌面 Widget 的更新、删除、数据查询、点击事件
 * 子类必须实现：背景资源、布局、Widget 类型三个抽象方法
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    // 数据库查询投影：只查询 ID、背景色、笔记摘要这三列，提高效率
    public static final String [] PROJECTION = new String [] {
            NoteColumns.ID,           // 笔记ID
            NoteColumns.BG_COLOR_ID,  // 背景色ID
            NoteColumns.SNIPPET       // 笔记摘要（预览文字）
    };

    // 对应上面投影数组的列索引，方便取值
    public static final int COLUMN_ID           = 0;  // 笔记ID列索引
    public static final int COLUMN_BG_COLOR_ID  = 1;  // 背景色列索引
    public static final int COLUMN_SNIPPET      = 2;  // 摘要列索引

    private static final String TAG = "NoteWidgetProvider"; // 日志TAG

    /**
     * 当小部件【被删除】时调用
     * 作用：把数据库中对应 Widget ID 的笔记清空绑定关系
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 准备要更新的数据：将 WIDGET_ID 设为无效值
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // 遍历被删除的Widget ID，更新数据库
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(
                    Notes.CONTENT_NOTE_URI,       // 笔记内容URI
                    values,                      // 要更新的字段
                    NoteColumns.WIDGET_ID + "=?",// 条件：Widget ID = ?
                    new String[] { String.valueOf(appWidgetIds[i]) } // 条件参数
            );
        }
    }

    /**
     * 根据 Widget ID 查询对应的笔记数据
     * 条件：Widget ID 匹配 + 不是回收站笔记
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                PROJECTION,
                // 查询条件：WidgetID匹配 且 父ID不是回收站
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                // 参数：WidgetID、回收站ID
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null
        );
    }

    /**
     * 对外提供的更新方法（默认非隐私模式）
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 核心：更新桌面小部件显示内容
     * @param privacyMode true=隐私模式（隐藏内容） false=正常模式
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        // 遍历所有需要更新的 Widget
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgId = ResourceParser.getDefaultBgId(context); // 默认背景色
                String snippet = "";                                // 笔记摘要

                // 创建点击 Widget 后跳转的 Intent：跳转到笔记编辑页
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); // 单例模式，避免重复创建
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]); // 携带Widget ID
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType()); // 携带Widget类型

                // 查询该Widget绑定的笔记
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    // 异常：一个Widget绑定多条笔记，直接报错返回
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 从数据库取出笔记数据
                    snippet = c.getString(COLUMN_SNIPPET);       // 摘要
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);         // 背景色
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID)); // 笔记ID
                    intent.setAction(Intent.ACTION_VIEW);        // 动作：查看
                } else {
                    // 没有绑定笔记 → 显示默认提示文字
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT); // 动作：新建/编辑
                }

                if (c != null) {
                    c.close(); // 关闭游标，防止内存泄漏
                }

                // 加载 Widget 布局（RemoteViews 用于跨进程更新桌面控件）
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId)); // 设置背景
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId); // 传递背景色

                /**
                 * 创建 Widget 点击跳转的延迟意图
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式：显示“访问模式”，点击跳转到笔记列表
                    rv.setTextViewText(R.id.widget_text, context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(
                            context, appWidgetIds[i],
                            new Intent(context, NotesListActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
                } else {
                    // 正常模式：显示笔记摘要，点击跳转到对应笔记
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(
                            context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
                }

                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent); // 绑定点击事件
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv); // 刷新桌面Widget
            }
        }
    }

    // ———————————————— 子类必须实现的抽象方法 ————————————————
    /**
     * 根据背景色ID获取对应的图片资源
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取 Widget 对应的布局文件 ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取 Widget 类型（不同尺寸/样式）
     */
    protected abstract int getWidgetType();
}