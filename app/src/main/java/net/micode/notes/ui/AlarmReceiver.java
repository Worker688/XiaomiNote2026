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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟广播接收器
 * 用于接收闹钟触发的广播，并启动闹钟提醒界面
 * 继承自 BroadcastReceiver，是 Android 四大组件之一，用于监听系统/应用广播事件
 */
public class AlarmReceiver extends BroadcastReceiver {
    /**
     * 广播接收回调方法：当闹钟广播触发时执行
     * @param context 上下文对象，用于访问应用环境信息、启动组件等
     * @param intent 触发广播的意图对象，携带广播相关的参数/标识
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将意图的目标组件设置为闹钟提醒界面 Activity
        intent.setClass(context, AlarmAlertActivity.class);
        // 添加 FLAG_ACTIVITY_NEW_TASK 标记：在新的任务栈中启动 Activity（BroadcastReceiver 中启动 Activity 必须添加此标记）
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 启动闹钟提醒界面
        context.startActivity(intent);
    }
}