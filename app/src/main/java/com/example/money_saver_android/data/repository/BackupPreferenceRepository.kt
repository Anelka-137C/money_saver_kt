package com.example.money_saver_android.data.repository

import android.content.SharedPreferences

/**
 * Persists the epoch timestamp (in milliseconds) of the latest successful backup.
 */
interface BackupPreferenceRepository {
    fun getLastBackupTimestamp(): Long?
    fun saveLastBackupTimestamp(timestampMillis: Long)
}

class SharedPrefsBackupPreferenceRepository(
    private val sharedPreferences: SharedPreferences
) : BackupPreferenceRepository {

    companion object {
        const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
    }

    override fun getLastBackupTimestamp(): Long? {
        if (!sharedPreferences.contains(KEY_LAST_BACKUP_TIMESTAMP)) return null
        val value = try {
            sharedPreferences.getLong(KEY_LAST_BACKUP_TIMESTAMP, -1L)
        } catch (_: ClassCastException) {
            return null
        }
        return value.takeIf { it > 0L }
    }

    override fun saveLastBackupTimestamp(timestampMillis: Long) {
        if (timestampMillis <= 0L) return
        sharedPreferences.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestampMillis).apply()
    }
}
