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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 * 作用：接收广播后，从数据库中查询所有未触发的笔记闹钟，重新注册到系统闹钟管理器中
 * 保证应用重启/系统重启后，未触发的闹钟仍能正常执行
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 查询数据库时需要的字段：笔记ID、闹钟提醒时间
    private static final String[] PROJECTION = new String[]{
            NoteColumns.ID,          // 笔记唯一标识
            NoteColumns.ALERTED_DATE // 闹钟触发的时间戳（毫秒）
    };

    // 游标（Cursor）中对应字段的索引，简化取值操作
    private static final int COLUMN_ID = 0;                // 笔记ID的索引
    private static final int COLUMN_ALERTED_DATE = 1;      // 闹钟时间的索引

    /**
     * 广播接收回调方法：当接收到指定广播时执行，核心逻辑为重新注册未触发的闹钟
     * @param context 上下文对象，用于获取系统服务、访问内容解析器等
     * @param intent  触发广播的意图对象，可携带广播相关数据
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间戳（毫秒），用于筛选未触发的闹钟
        long currentDate = System.currentTimeMillis();

        // 从内容解析器查询符合条件的笔记：
        // 条件1：闹钟触发时间 > 当前时间（未触发）；条件2：笔记类型为普通笔记（TYPE_NOTE）
        Cursor c = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,    // 笔记内容的Uri
                PROJECTION,                // 需要查询的字段
                // 查询条件：ALERTED_DATE > 当前时间 且 笔记类型为TYPE_NOTE
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[]{String.valueOf(currentDate)}, // 替换查询条件中的占位符（当前时间）
                null                        // 排序方式：默认不排序
        );

        // 游标非空时处理查询结果
        if (c != null) {
            // 移动游标到第一条数据，判断是否有符合条件的笔记
            if (c.moveToFirst()) {
                // 循环遍历所有符合条件的笔记
                do {
                    // 获取当前笔记的闹钟触发时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 构建闹钟触发时要发送的意图：指定接收者为AlarmReceiver（闹钟触发接收器）
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 为意图添加笔记ID作为数据标识，便于AlarmReceiver识别是哪条笔记的闹钟
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));

                    // 创建延迟意图：用于闹钟触发时发送广播给AlarmReceiver
                    // 参数说明：上下文、请求码（0表示无特殊标识）、意图对象、标志位（0为默认）
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // 获取系统闹钟服务
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 注册闹钟到系统：
                    // 类型：RTC_WAKEUP（基于实时时钟，触发时唤醒设备）
                    // 触发时间：alertDate（从数据库查询的笔记闹钟时间）
                    // 触发动作：发送pendingIntent对应的广播
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext()); // 移动游标到下一条，直到遍历完所有数据
            }
            // 关闭游标，释放数据库连接资源（必须执行，否则会造成内存泄漏）
            c.close();
        }
    }
}