package com.example.money_saver_android.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Launches the system-provided BiometricPrompt allowing fingerprint + device PIN
 * or device PIN only depending on the detected [DeviceAuthCapability].
 */
class NativeBiometricAuthenticator(
    private val activity: FragmentActivity,
    private val titleProvider: () -> String,
    private val subtitleProvider: () -> String
) {
    fun authenticate(
        capability: DeviceAuthCapability,
        onSuccess: () -> Unit,
        onFailureOrCancel: () -> Unit
    ) {
        if (capability == DeviceAuthCapability.NONE) {
            onFailureOrCancel()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailureOrCancel()
                }
            }
        )

        val allowedAuthenticators = if (
            capability == DeviceAuthCapability.PIN_ONLY &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titleProvider())
                .setSubtitle(subtitleProvider())
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            onFailureOrCancel()
        }
    }
}
