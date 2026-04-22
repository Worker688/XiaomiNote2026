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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 笔记提醒弹窗界面
 * 功能：闹钟时间到了后弹出提醒，播放铃声，显示笔记内容
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {

    // 当前提醒的笔记ID
    private long mNoteId;
    // 笔记预览内容
    private String mSnippet;
    // 笔记预览内容最大长度
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    // 铃声播放器
    MediaPlayer mPlayer;

    /**
     * 页面创建
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 去掉标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 锁屏状态下也能显示此界面
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕没亮，点亮屏幕并保持常亮
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        // 获取启动此页面的意图
        Intent intent = getIntent();

        try {
            // 从意图中获取笔记ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 获取笔记预览内容
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            // 预览内容过长时截取
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        // 初始化播放器
        mPlayer = new MediaPlayer();

        // 检查笔记是否有效
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            // 显示提醒弹窗
            showActionDialog();
            // 播放提醒铃声
            playAlarmSound();
        } else {
            // 笔记无效，关闭页面
            finish();
        }
    }

    /**
     * 判断屏幕是否亮着
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放系统默认闹钟铃声
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟声音
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取静音模式设置
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 设置声音播放类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            // 设置声音源
            mPlayer.setDataSource(this, url);
            // 准备播放
            mPlayer.prepare();
            // 循环播放
            mPlayer.setLooping(true);
            // 开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        // 设置标题
        dialog.setTitle(R.string.app_name);
        // 显示笔记内容
        dialog.setMessage(mSnippet);
        // 设置确定按钮
        dialog.setPositiveButton(R.string.notealert_ok, this);

        // 屏幕亮时显示“进入笔记”按钮
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }

        // 显示对话框并监听关闭
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击事件
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            // 进入笔记按钮
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                // 传入当前笔记ID
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            // 确定按钮，不做操作
            default:
                break;
        }
    }

    /**
     * 对话框关闭时停止铃声并关闭页面
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止铃声并释放播放器资源
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}