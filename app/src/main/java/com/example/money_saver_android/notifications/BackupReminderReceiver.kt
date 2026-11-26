package com.example.money_saver_android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.money_saver_android.MainActivity
import com.example.money_saver_android.R
import com.example.money_saver_android.data.repository.SharedPrefsBackupPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import com.example.money_saver_android.domain.usecase.BackupReminderScheduleCalculator
import com.example.money_saver_android.i18n.I18nManager

class BackupReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "backup_reminders"
        const val NOTIFICATION_ID = 1301
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_SETTINGS = "SETTINGS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val backupRepo = SharedPrefsBackupPreferenceRepository(prefs)
        val lastBackup = backupRepo.getLastBackupTimestamp() ?: return
        val now = System.currentTimeMillis()

        val isBoot = intent?.action == Intent.ACTION_BOOT_COMPLETED
        if (!isBoot && BackupReminderScheduleCalculator.shouldNotifyNow(lastBackup, now)) {
            showReminderNotification(context, prefs)
        }

        AndroidBackupReminderScheduler(context).scheduleNextReminder(lastBackup)
    }

    private fun showReminderNotification(
        context: Context,
        prefs: android.content.SharedPreferences
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val language = SharedPrefsLanguageRepository(prefs).getLanguage()
        val i18n = I18nManager().apply {
            initFromContext(context)
            setLanguage(language)
        }

        val channelName = i18n.tOrDefault("settings.backup_section.reminder_channel_name", "Recordatorios de respaldo")
        val title = i18n.tOrDefault("settings.backup_section.reminder_title", "Respaldo pendiente")
        val body = i18n.tOrDefault(
            "settings.backup_section.reminder_body",
            "Han pasado 7 días desde tu último backup. Guárdalo en Google Drive desde Configuración."
        )

        ensureChannel(context, channelName)

        val openSettingsIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, TAB_SETTINGS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
    }

    private fun I18nManager.tOrDefault(key: String, fallback: String): String {
        val resolved = t(key)
        return if (resolved == key) fallback else resolved
    }
}
