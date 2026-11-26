package com.example.money_saver_android.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceAuthCapabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun test_device_auth_capability_detects_biometric_pin_only_and_no_lock() {
        val biometricAvailable = DeviceAuthCapabilityChecker(
            biometricStatusProvider = { BiometricManager.BIOMETRIC_SUCCESS },
            deviceCredentialStatusProvider = { BiometricManager.BIOMETRIC_SUCCESS },
            isDeviceSecureProvider = { true }
        )
        assertEquals(DeviceAuthCapability.BIOMETRIC_AND_PIN, biometricAvailable.getCapability())

        val pinOnlyNoHardware = DeviceAuthCapabilityChecker(
            biometricStatusProvider = { BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE },
            deviceCredentialStatusProvider = { BiometricManager.BIOMETRIC_SUCCESS },
            isDeviceSecureProvider = { true }
        )
        assertEquals(DeviceAuthCapability.PIN_ONLY, pinOnlyNoHardware.getCapability())

        val pinOnlyNotEnrolled = DeviceAuthCapabilityChecker(
            biometricStatusProvider = { BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED },
            deviceCredentialStatusProvider = { BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED },
            isDeviceSecureProvider = { true }
        )
        assertEquals(DeviceAuthCapability.PIN_ONLY, pinOnlyNotEnrolled.getCapability())

        val noLock = DeviceAuthCapabilityChecker(
            biometricStatusProvider = { BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED },
            deviceCredentialStatusProvider = { BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED },
            isDeviceSecureProvider = { false }
        )
        assertEquals(DeviceAuthCapability.NONE, noLock.getCapability())

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguardManager).setIsDeviceSecure(false)
        val androidChecker = AndroidDeviceAuthCapabilityChecker(context)
        assertEquals(DeviceAuthCapability.NONE, androidChecker.getCapability())

        shadowOf(keyguardManager).setIsDeviceSecure(true)
        val capabilityWithSecureKeyguard = androidChecker.getCapability()
        assertEquals(
            true,
            capabilityWithSecureKeyguard == DeviceAuthCapability.PIN_ONLY ||
                capabilityWithSecureKeyguard == DeviceAuthCapability.BIOMETRIC_AND_PIN
        )
    }
}
