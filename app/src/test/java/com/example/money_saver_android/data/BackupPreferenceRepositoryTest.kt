package com.example.money_saver_android.data

import android.content.Context
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsBackupPreferenceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupPreferenceRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: BackupPreferenceRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SharedPrefsBackupPreferenceRepository(prefs)
    }

    @Test
    fun backupTimestamp_defaultsToNull_andPersistsSavedValue() {
        assertNull(repository.getLastBackupTimestamp())

        val timestamp = 1_764_100_000_000L
        repository.saveLastBackupTimestamp(timestamp)
        assertEquals(timestamp, repository.getLastBackupTimestamp())

        val newInstance = SharedPrefsBackupPreferenceRepository(
            context.getSharedPreferences("test_backup_prefs", Context.MODE_PRIVATE)
        )
        assertEquals(timestamp, newInstance.getLastBackupTimestamp())
    }
}
