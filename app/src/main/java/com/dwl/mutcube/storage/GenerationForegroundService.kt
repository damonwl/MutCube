package com.dwl.mutcube.storage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.dwl.mutcube.MainActivity

class GenerationForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "后台生成", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, progressNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun progressNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicNotification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("MutCube 正在生成")
            .setOngoing(true)
            .build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("MutCube 正在生成回复")
            .setContentText("可以返回应用查看进度或停止生成")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "background_generation"
        private const val NOTIFICATION_ID = 1000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GenerationForegroundService::class.java))
        }

        fun stop(context: Context): Boolean =
            context.stopService(Intent(context, GenerationForegroundService::class.java))
    }
}
