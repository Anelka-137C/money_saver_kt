package com.example.money_saver_android.security

import com.example.money_saver_android.data.repository.AppLockPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppLockUiState(
    val isLocked: Boolean = false,
    val isAuthenticating: Boolean = false,
    val showAuthError: Boolean = false
)

/**
 * Coordinates native device unlock state across app launch and background/foreground transitions.
 */
class AppLockController(
    private val appLockPreferenceRepository: AppLockPreferenceRepository,
    private val deviceAuthCapabilityProvider: DeviceAuthCapabilityProvider,
    private val launchDeviceAuth: (
        capability: DeviceAuthCapability,
        onSuccess: () -> Unit,
        onFailureOrCancel: () -> Unit
    ) -> Unit
) {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var suppressNextBackgroundLock = false
    private var activeAuthToken: Long = 0L
    private var backgroundedDuringAuth = false

    fun onAppLaunch() {
        val capability = availableCapabilityIfEnabled() ?: return
        _uiState.update { it.copy(isLocked = true, isAuthenticating = false, showAuthError = false) }
        promptAuthentication(capability)
    }

    fun onAppBackgrounded() {
        if (suppressNextBackgroundLock) return
        if (availableCapabilityIfEnabled() == null) return
        if (_uiState.value.isAuthenticating) {
            backgroundedDuringAuth = true
            return
        }

        _uiState.update {
            it.copy(isLocked = true, isAuthenticating = false, showAuthError = false)
        }
    }

    fun onAppForegrounded() {
        if (suppressNextBackgroundLock) {
            suppressNextBackgroundLock = false
            return
        }
        val capability = availableCapabilityIfEnabled() ?: run {
            activeAuthToken++
            backgroundedDuringAuth = false
            _uiState.update { AppLockUiState() }
            return
        }
        val current = _uiState.value
        if (current.isAuthenticating && backgroundedDuringAuth) {
            backgroundedDuringAuth = false
            _uiState.update {
                it.copy(isLocked = true, isAuthenticating = false, showAuthError = true)
            }
            return
        }
        if (!current.isLocked) {
            _uiState.update { it.copy(isLocked = true, showAuthError = false) }
        }
        if (!current.isAuthenticating && !current.showAuthError) {
            promptAuthentication(capability)
        }
    }

    fun retryAuthentication() {
        val capability = availableCapabilityIfEnabled() ?: run {
            activeAuthToken++
            backgroundedDuringAuth = false
            _uiState.update { AppLockUiState() }
            return
        }
        if (!_uiState.value.isLocked || _uiState.value.isAuthenticating) return
        _uiState.update { it.copy(showAuthError = false) }
        promptAuthentication(capability)
    }

    fun suppressNextBackgroundLock() {
        suppressNextBackgroundLock = true
    }

    fun clearBackgroundLockSuppression() {
        suppressNextBackgroundLock = false
    }

    fun isBackgroundLockSuppressed(): Boolean = suppressNextBackgroundLock

    fun restoreLockedState(wasLocked: Boolean, showAuthError: Boolean = false) {
        if (wasLocked && availableCapabilityIfEnabled() != null) {
            _uiState.update {
                it.copy(isLocked = true, isAuthenticating = false, showAuthError = showAuthError)
            }
        }
    }

    private fun availableCapabilityIfEnabled(): DeviceAuthCapability? {
        if (!appLockPreferenceRepository.isAppLockEnabled()) return null
        val capability = deviceAuthCapabilityProvider.getCapability()
        return capability.takeIf { it != DeviceAuthCapability.NONE }
    }

    private fun promptAuthentication(capability: DeviceAuthCapability) {
        val token = ++activeAuthToken
        backgroundedDuringAuth = false
        _uiState.update { it.copy(isAuthenticating = true, showAuthError = false) }
        try {
            launchDeviceAuth(
                capability,
                {
                    if (token != activeAuthToken) return@launchDeviceAuth
                    activeAuthToken++
                    backgroundedDuringAuth = false
                    _uiState.update {
                        it.copy(isLocked = false, isAuthenticating = false, showAuthError = false)
                    }
                },
                {
                    if (token != activeAuthToken) return@launchDeviceAuth
                    activeAuthToken++
                    backgroundedDuringAuth = false
                    _uiState.update {
                        it.copy(isLocked = true, isAuthenticating = false, showAuthError = true)
                    }
                }
            )
        } catch (_: Exception) {
            if (token == activeAuthToken) {
                activeAuthToken++
                backgroundedDuringAuth = false
                _uiState.update {
                    it.copy(isLocked = true, isAuthenticating = false, showAuthError = true)
                }
            }
        }
    }
}
