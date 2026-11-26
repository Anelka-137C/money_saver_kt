package com.example.money_saver_android.data

import android.content.Context
import com.example.money_saver_android.data.repository.AppLockPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsAppLockPreferenceRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLockPreferenceRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: AppLockPreferenceRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_app_lock_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SharedPrefsAppLockPreferenceRepository(prefs)
    }

    @Test
    fun test_app_lock_preference_defaults_to_disabled_and_persists_changes() {
        assertFalse(repository.isAppLockEnabled())

        repository.setAppLockEnabled(true)
        assertTrue(repository.isAppLockEnabled())

        val reloaded = SharedPrefsAppLockPreferenceRepository(
            context.getSharedPreferences("test_app_lock_prefs", Context.MODE_PRIVATE)
        )
        assertTrue(reloaded.isAppLockEnabled())

        reloaded.setAppLockEnabled(false)
        assertFalse(repository.isAppLockEnabled())
    }
}
