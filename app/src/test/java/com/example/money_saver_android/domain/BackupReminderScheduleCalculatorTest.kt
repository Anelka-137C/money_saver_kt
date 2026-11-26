package com.example.money_saver_android.domain

import com.example.money_saver_android.domain.usecase.BackupReminderScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BackupReminderScheduleCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun nextReminderTimeMillis_returnsNullWhenNeverBackedUp() {
        val now = utcMillis(2026, 11, 25, 10, 0)
        assertNull(BackupReminderScheduleCalculator.nextReminderTimeMillis(null, now, utc))
        assertFalse(BackupReminderScheduleCalculator.shouldNotifyNow(null, now, utc))
    }

    @Test
    fun nextReminderTimeMillis_schedulesExactlySevenDaysLaterInDaytime_andSkipsNight() {
        val backupAt10am = utcMillis(2026, 11, 25, 10, 0)
        val firstReminder = BackupReminderScheduleCalculator.nextReminderTimeMillis(
            lastBackupTimestamp = backupAt10am,
            nowMillis = backupAt10am,
            timeZone = utc
        )
        assertEquals(utcMillis(2026, 12, 2, 10, 0), firstReminder)

        // 8h after 10:00 -> 18:00 (same day, daytime)
        val secondReminder = BackupReminderScheduleCalculator.nextReminderTimeMillis(
            lastBackupTimestamp = backupAt10am,
            nowMillis = firstReminder!!,
            timeZone = utc
        )
        assertEquals(utcMillis(2026, 12, 2, 18, 0), secondReminder)

        // 8h after 18:00 -> 02:00 (night) -> advances to 08:00 next morning
        val thirdReminder = BackupReminderScheduleCalculator.nextReminderTimeMillis(
            lastBackupTimestamp = backupAt10am,
            nowMillis = secondReminder!!,
            timeZone = utc
        )
        assertEquals(utcMillis(2026, 12, 3, 8, 0), thirdReminder)
    }

    @Test
    fun shouldNotifyNow_onlyTrueAfterSevenDaysAndDuringDaytime() {
        val backup = utcMillis(2026, 11, 25, 10, 0)

        assertFalse(BackupReminderScheduleCalculator.shouldNotifyNow(backup, utcMillis(2026, 12, 2, 9, 59), utc))
        assertTrue(BackupReminderScheduleCalculator.shouldNotifyNow(backup, utcMillis(2026, 12, 2, 10, 0), utc))
        assertFalse(BackupReminderScheduleCalculator.shouldNotifyNow(backup, utcMillis(2026, 12, 2, 22, 0), utc))
        assertFalse(BackupReminderScheduleCalculator.shouldNotifyNow(backup, utcMillis(2026, 12, 3, 7, 59), utc))
    }
}
