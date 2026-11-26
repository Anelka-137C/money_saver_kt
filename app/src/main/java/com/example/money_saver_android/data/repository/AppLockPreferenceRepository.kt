package com.example.money_saver_android.data.repository

import android.content.SharedPreferences

/**
 * Persists whether the user has enabled native device unlock protection on app launch/resume.
 */
interface AppLockPreferenceRepository {
    fun isAppLockEnabled(): Boolean
    fun setAppLockEnabled(enabled: Boolean)
}

class SharedPrefsAppLockPreferenceRepository(
    private val sharedPreferences: SharedPreferences
) : AppLockPreferenceRepository {

    companion object {
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    }

    override fun isAppLockEnabled(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false)
        } catch (_: ClassCastException) {
            false
        }
    }

    override fun setAppLockEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }
}
