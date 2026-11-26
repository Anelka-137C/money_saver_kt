package com.example.money_saver_android.domain.usecase

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure schedule calculator for backup reminders: activates 7 days after the last successful
 * backup and repeats every 8 hours during daytime (08:00–21:59), skipping night hours.
 */
object BackupReminderScheduleCalculator {

    const val SEVEN_DAYS_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
    const val REMINDER_INTERVAL_MILLIS: Long = 8L * 60 * 60 * 1000
    const val DAYTIME_START_HOUR: Int = 8
    const val DAYTIME_END_HOUR_EXCLUSIVE: Int = 22

    fun isDaytime(
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in DAYTIME_START_HOUR until DAYTIME_END_HOUR_EXCLUSIVE
    }

    fun shouldNotifyNow(
        lastBackupTimestamp: Long?,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (lastBackupTimestamp == null || lastBackupTimestamp <= 0L) return false
        if (nowMillis < lastBackupTimestamp) return false
        return (nowMillis - lastBackupTimestamp) >= SEVEN_DAYS_MILLIS && isDaytime(nowMillis, timeZone)
    }

    fun nextReminderTimeMillis(
        lastBackupTimestamp: Long?,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (lastBackupTimestamp == null || lastBackupTimestamp <= 0L) return null
        if (lastBackupTimestamp > Long.MAX_VALUE - SEVEN_DAYS_MILLIS) return null
        val sevenDaysAfter = lastBackupTimestamp + SEVEN_DAYS_MILLIS
        val rawCandidate = when {
            nowMillis < sevenDaysAfter -> sevenDaysAfter
            !isDaytime(nowMillis, timeZone) -> nowMillis
            nowMillis <= Long.MAX_VALUE - REMINDER_INTERVAL_MILLIS -> nowMillis + REMINDER_INTERVAL_MILLIS
            else -> return null
        }
        if (rawCandidate > Long.MAX_VALUE - 2L * 24 * 60 * 60 * 1000) return null
        val adjusted = adjustToDaytime(rawCandidate, timeZone)
        if (adjusted > nowMillis) return adjusted
        if (nowMillis > Long.MAX_VALUE - REMINDER_INTERVAL_MILLIS - 2L * 24 * 60 * 60 * 1000) return null
        return adjustToDaytime(nowMillis + REMINDER_INTERVAL_MILLIS, timeZone)
    }

    internal fun adjustToDaytime(
        candidateMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = candidateMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            hour < DAYTIME_START_HOUR -> {
                cal.set(Calendar.HOUR_OF_DAY, DAYTIME_START_HOUR)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            hour >= DAYTIME_END_HOUR_EXCLUSIVE -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, DAYTIME_START_HOUR)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            else -> candidateMillis
        }
    }
}
