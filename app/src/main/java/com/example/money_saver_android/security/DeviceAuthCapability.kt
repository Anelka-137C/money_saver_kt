package com.example.money_saver_android.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Native device unlock capability detected on the user's phone.
 */
enum class DeviceAuthCapability {
    BIOMETRIC_AND_PIN,
    PIN_ONLY,
    NONE
}

/**
 * Evaluates whether the device supports fingerprint + PIN, PIN only, or has no screen lock set.
 */
interface DeviceAuthCapabilityProvider {
    fun getCapability(): DeviceAuthCapability
}

class DeviceAuthCapabilityChecker(
    private val biometricStatusProvider: () -> Int,
    private val deviceCredentialStatusProvider: () -> Int,
    private val isDeviceSecureProvider: () -> Boolean?
) : DeviceAuthCapabilityProvider {

    override fun getCapability(): DeviceAuthCapability {
        val hasDeviceLock = isDeviceSecureProvider()
            ?: (deviceCredentialStatusProvider() == BiometricManager.BIOMETRIC_SUCCESS)
        if (!hasDeviceLock) {
            return DeviceAuthCapability.NONE
        }

        return if (biometricStatusProvider() == BiometricManager.BIOMETRIC_SUCCESS) {
            DeviceAuthCapability.BIOMETRIC_AND_PIN
        } else {
            DeviceAuthCapability.PIN_ONLY
        }
    }
}

class AndroidDeviceAuthCapabilityChecker(
    private val context: Context
) : DeviceAuthCapabilityProvider {

    private val biometricManager by lazy { BiometricManager.from(context) }
    private val keyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    private val delegate = DeviceAuthCapabilityChecker(
        biometricStatusProvider = {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        },
        deviceCredentialStatusProvider = {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        },
        isDeviceSecureProvider = {
            keyguardManager?.isDeviceSecure
        }
    )

    override fun getCapability(): DeviceAuthCapability = delegate.getCapability()
}
