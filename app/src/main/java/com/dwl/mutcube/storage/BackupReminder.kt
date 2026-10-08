package com.dwl.mutcube.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dwl.mutcube.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class BackupReminderStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getInt(KEY_DAYS, 0))
    val days = state.asStateFlow()

    fun write(days: Int) {
        require(days in ALLOWED_DAYS) { "不支持的提醒周期" }
        preferences.edit().putInt(KEY_DAYS, days).apply()
        state.value = days
    }

    companion object {
        const val PREFERENCES = "backup_reminder"
        private const val KEY_DAYS = "days"
        val ALLOWED_DAYS = setOf(0, 7, 14, 30)
    }
}

class BackupReminderScheduler(
    context: Context,
    private val store: BackupReminderStore,
) {
    private val appContext = context.applicationContext

    fun update(days: Int) {
        store.write(days)
        val manager = WorkManager.getInstance(appContext)
        if (days == 0) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(days.toLong(), TimeUnit.DAYS).build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object { private const val UNIQUE_WORK = "mutcube-backup-reminder" }
}

class BackupReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "备份提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "定期提醒创建 MutCube 数据备份"
            },
        )
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("该备份 MutCube 数据了")
                .setContentText("打开数据与备份，创建本地备份或上传到远程存储。")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "backup_reminder"
        private const val NOTIFICATION_ID = 2302
    }
}
