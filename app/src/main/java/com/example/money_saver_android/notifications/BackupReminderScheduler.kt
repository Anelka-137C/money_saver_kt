package com.example.money_saver_android.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.money_saver_android.domain.usecase.BackupReminderScheduleCalculator

interface BackupReminderScheduler {
    fun scheduleNextReminder(lastBackupTimestamp: Long?)
    fun cancelReminders()
}

class AndroidBackupReminderScheduler(
    private val context: Context,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : BackupReminderScheduler {

    companion object {
        const val ACTION_BACKUP_REMINDER = "com.example.money_saver_android.ACTION_BACKUP_REMINDER"
        private const val REQUEST_CODE = 1301
    }

    override fun scheduleNextReminder(lastBackupTimestamp: Long?) {
        val triggerAt = BackupReminderScheduleCalculator.nextReminderTimeMillis(
            lastBackupTimestamp = lastBackupTimestamp,
            nowMillis = nowProvider()
        )
        if (triggerAt == null) {
            cancelReminders()
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            buildPendingIntent()
        )
    }

    override fun cancelReminders() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent())
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, BackupReminderReceiver::class.java).apply {
            action = ACTION_BACKUP_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
