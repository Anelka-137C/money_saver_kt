package com.example.money_saver_android.security

import com.example.money_saver_android.data.repository.AppLockPreferenceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {

    @Test
    fun test_app_lock_locks_on_launch_and_background_return_and_unlocks_on_success() {
        val fakePrefs = object : AppLockPreferenceRepository {
            var enabled = false
            override fun isAppLockEnabled(): Boolean = enabled
            override fun setAppLockEnabled(enabled: Boolean) {
                this.enabled = enabled
            }
        }
        var currentCapability = DeviceAuthCapability.BIOMETRIC_AND_PIN
        val fakeCapability = object : DeviceAuthCapabilityProvider {
            override fun getCapability(): DeviceAuthCapability = currentCapability
        }
        val launchedCapabilities = mutableListOf<DeviceAuthCapability>()
        var activeOnSuccess: (() -> Unit)? = null
        var activeOnErrorOrCancel: (() -> Unit)? = null

        val controller = AppLockController(
            appLockPreferenceRepository = fakePrefs,
            deviceAuthCapabilityProvider = fakeCapability,
            launchDeviceAuth = { capability, onSuccess, onFailureOrCancel ->
                launchedCapabilities.add(capability)
                activeOnSuccess = onSuccess
                activeOnErrorOrCancel = onFailureOrCancel
            }
        )

        // 1. Disabled -> stays unlocked on launch and background
        controller.onAppLaunch()
        assertFalse(controller.uiState.value.isLocked)
        assertEquals(0, launchedCapabilities.size)

        // 2. Enabled + BIOMETRIC_AND_PIN -> locks on launch and prompts
        fakePrefs.enabled = true
        controller.onAppLaunch()
        assertTrue(controller.uiState.value.isLocked)
        assertFalse(controller.uiState.value.showAuthError)
        assertEquals(listOf(DeviceAuthCapability.BIOMETRIC_AND_PIN), launchedCapabilities)

        activeOnSuccess!!.invoke()
        assertFalse(controller.uiState.value.isLocked)
        assertFalse(controller.uiState.value.showAuthError)

        // 3. Backgrounding locks immediately; foregrounding prompts with current capability (PIN_ONLY)
        currentCapability = DeviceAuthCapability.PIN_ONLY
        controller.onAppBackgrounded()
        assertTrue(controller.uiState.value.isLocked)

        controller.onAppForegrounded()
        assertEquals(
            listOf(DeviceAuthCapability.BIOMETRIC_AND_PIN, DeviceAuthCapability.PIN_ONLY),
            launchedCapabilities
        )

        // Failure/cancel keeps locked and sets error; retry relaunches prompt
        activeOnErrorOrCancel!!.invoke()
        assertTrue(controller.uiState.value.isLocked)
        assertTrue(controller.uiState.value.showAuthError)

        // Simulated foreground return from system credential activity while error is shown does not auto-relaunch
        controller.onAppForegrounded()
        assertEquals(2, launchedCapabilities.size)

        controller.retryAuthentication()
        assertTrue(controller.uiState.value.isLocked)
        assertFalse(controller.uiState.value.showAuthError)
        assertEquals(3, launchedCapabilities.size)

        activeOnSuccess!!.invoke()
        assertFalse(controller.uiState.value.isLocked)

        // 4. Suppressing next background lock (e.g. Google Drive file picker) skips lock once
        controller.suppressNextBackgroundLock()
        controller.onAppBackgrounded()
        assertFalse(controller.uiState.value.isLocked)
        controller.onAppForegrounded()
        assertFalse(controller.uiState.value.isLocked)
        assertEquals(3, launchedCapabilities.size)

        // Subsequent backgrounding locks normally again
        controller.onAppBackgrounded()
        assertTrue(controller.uiState.value.isLocked)
    }
}
