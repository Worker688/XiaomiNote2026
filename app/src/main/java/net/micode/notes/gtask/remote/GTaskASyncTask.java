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

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;


public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext;
    private NotificationManager mNotifiManager;
    private GTaskManager mTaskManager;
    private OnCompleteListener mOnCompleteListener;

    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    private void showNotification(int tickerId, String content) {
        // 渠道 ID（必须和清单文件一致）
        String channelId = "GTASK_SYNC_CHANNEL";

        // Android 8.0 必须创建渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "GTask 同步通知",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            mNotifiManager.createNotificationChannel(channel);
        }

        // 构建 PendingIntent
        PendingIntent pendingIntent;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(
                    mContext, 0,
                    new Intent(mContext, NotesPreferenceActivity.class),
                    flags
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    mContext, 0,
                    new Intent(mContext, NotesListActivity.class),
                    flags
            );
        }

        // 新版通知构建（完全废弃旧 API）
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.icon_app) // 用你项目里存在的图标
                .setTicker(mContext.getString(tickerId))
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        Notification notification = builder.build();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    @Override
    protected Integer doInBackground(Void... unused) {
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success,
                    mContext.getString(R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext.getString(R.string.error_sync_cancelled));
        }

        if (mOnCompleteListener != null) {
            new Thread(() -> mOnCompleteListener.onComplete()).start();
        }
    }
}