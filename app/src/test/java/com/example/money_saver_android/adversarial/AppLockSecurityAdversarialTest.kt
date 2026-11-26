package com.example.money_saver_android.adversarial

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.fragment.app.FragmentActivity
import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.MainActivity
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.AppLockPreferenceRepository
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsAppLockPreferenceRepository
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.FakeSavingsRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.security.AndroidDeviceAuthCapabilityChecker
import com.example.money_saver_android.security.AppLockController
import com.example.money_saver_android.security.AppLockUiState
import com.example.money_saver_android.security.DeviceAuthCapability
import com.example.money_saver_android.security.DeviceAuthCapabilityChecker
import com.example.money_saver_android.security.DeviceAuthCapabilityProvider
import com.example.money_saver_android.security.NativeBiometricAuthenticator
import com.example.money_saver_android.ui.SettingsView
import com.example.money_saver_android.ui.components.security.AppLockGate
import com.example.money_saver_android.viewmodel.FakeCurrencyPreferenceRepository
import com.example.money_saver_android.viewmodel.FakeCurrencyRepository
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeIncomeRepository
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
import com.example.money_saver_android.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Story #14:
 * Native device unlock protection (fingerprint and/or device PIN).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockSecurityAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // SECTION 1: SharedPrefsAppLockPreferenceRepository (Corruption & PBT)
    // =========================================================================

    @Test
    fun repository_corruptedSharedPreferencesTypes_safelyDefaultToFalseAndRecoverOnWrite() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("adv_app_lock_corrupt", Context.MODE_PRIVATE)
        val repo = SharedPrefsAppLockPreferenceRepository(prefs)

        // Corrupt with String, Int, Long, Float, StringSet
        prefs.edit().clear().putString(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, "true").commit()
        assertFalse("String corruption must safely default to false", repo.isAppLockEnabled())

        prefs.edit().clear().putInt(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, 1).commit()
        assertFalse("Int corruption must safely default to false", repo.isAppLockEnabled())

        prefs.edit().clear().putLong(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, 1L).commit()
        assertFalse("Long corruption must safely default to false", repo.isAppLockEnabled())

        prefs.edit().clear().putFloat(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, 1.0f).commit()
        assertFalse("Float corruption must safely default to false", repo.isAppLockEnabled())

        prefs.edit().clear().putStringSet(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, setOf("true")).commit()
        assertFalse("StringSet corruption must safely default to false", repo.isAppLockEnabled())

        // Subsequent valid boolean write must recover cleanly
        repo.setAppLockEnabled(true)
        assertTrue("Valid write after corruption must persist true", repo.isAppLockEnabled())
    }

    @Test
    fun property_repository_lastWriteAlwaysWinsAcross200RandomToggles() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("adv_app_lock_pbt", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val repo = SharedPrefsAppLockPreferenceRepository(prefs)
        val rng = Random(seed = 2026112601L)

        var expected = false
        assertEquals(expected, repo.isAppLockEnabled())

        repeat(200) {
            val next = rng.nextBoolean()
            repo.setAppLockEnabled(next)
            expected = next
            val freshReader = SharedPrefsAppLockPreferenceRepository(
                context.getSharedPreferences("adv_app_lock_pbt", Context.MODE_PRIVATE)
            )
            assertEquals(expected, repo.isAppLockEnabled())
            assertEquals(expected, freshReader.isAppLockEnabled())
        }
    }

    // =========================================================================
    // SECTION 2: DeviceAuthCapabilityChecker (Property-Based Invariants)
    // =========================================================================

    @Test
    fun property_deviceAuthCapability_invariantsHoldAcross300RandomStatusCombinations() {
        val knownStatuses = listOf(
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            -1,
            Int.MIN_VALUE,
            Int.MAX_VALUE
        )
        val deviceSecureValues = listOf(true, false, null)
        val rng = Random(seed = 2026112602L)

        repeat(300) {
            val bioStatus = knownStatuses[rng.nextInt(knownStatuses.size)]
            val credStatus = knownStatuses[rng.nextInt(knownStatuses.size)]
            val isSecure = deviceSecureValues[rng.nextInt(deviceSecureValues.size)]

            val checker = DeviceAuthCapabilityChecker(
                biometricStatusProvider = { bioStatus },
                deviceCredentialStatusProvider = { credStatus },
                isDeviceSecureProvider = { isSecure }
            )

            val result = checker.getCapability()
            val expectedHasLock = isSecure ?: (credStatus == BiometricManager.BIOMETRIC_SUCCESS)

            // Invariant 1: No lock screen => NEVER BIOMETRIC_AND_PIN or PIN_ONLY
            if (!expectedHasLock) {
                assertEquals(
                    "Without device lock (isSecure=$isSecure, cred=$credStatus, bio=$bioStatus) must be NONE",
                    DeviceAuthCapability.NONE,
                    result
                )
            } else if (bioStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                // Invariant 2: Device lock + BIOMETRIC_SUCCESS => BIOMETRIC_AND_PIN
                assertEquals(DeviceAuthCapability.BIOMETRIC_AND_PIN, result)
            } else {
                // Invariant 3: Device lock without BIOMETRIC_SUCCESS => PIN_ONLY
                assertEquals(DeviceAuthCapability.PIN_ONLY, result)
            }
        }
    }

    // =========================================================================
    // SECTION 3: AppLockController — Security Bypasses, Races & Liveness Bugs
    // =========================================================================

    /**
     * Adversarial Attack #1 (Stale Callback Security Bypass):
     * Prompt #1 succeeds and unlocks the app. The app goes to background (re-locking)
     * and returns to foreground (launching Prompt #2). If the stale onSuccess callback
     * from Prompt #1 fires again, it MUST NOT unlock the newly locked Prompt #2 session.
     */
    @Test
    fun adversarial_staleOnSuccessFromPreviousPrompt_mustNotUnlockNewLockedSession() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        val capturedSuccessCallbacks = mutableListOf<() -> Unit>()

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, onSuccess, _ ->
                capturedSuccessCallbacks.add(onSuccess)
            }
        )

        // Session 1: launch and unlock
        controller.onAppLaunch()
        assertTrue(controller.uiState.value.isLocked)
        capturedSuccessCallbacks[0].invoke()
        assertFalse(controller.uiState.value.isLocked)

        // Session 2: background -> foreground (locked, waiting for Prompt #2)
        controller.onAppBackgrounded()
        controller.onAppForegrounded()
        assertTrue(controller.uiState.value.isLocked)
        assertEquals(2, capturedSuccessCallbacks.size)

        // Attack: stale callback from Prompt #1 fires while Prompt #2 is pending
        capturedSuccessCallbacks[0].invoke()

        // Expected invariant: Session 2 MUST remain locked until Prompt #2's own callback succeeds
        assertTrue(
            "SECURITY BYPASS: Stale onSuccess from Prompt #1 unlocked Session 2!",
            controller.uiState.value.isLocked
        )
    }

    /**
     * Adversarial Attack #1b (Stale Failure Callback after Success):
     * If onFailureOrCancel from an already-succeeded prompt fires late,
     * it MUST NOT re-lock the active authenticated session or show an error.
     */
    @Test
    fun adversarial_staleOnFailureFromCompletedPrompt_mustNotRelockAuthenticatedSession() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var capturedSuccess: (() -> Unit)? = null
        var capturedFailure: (() -> Unit)? = null

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, onSuccess, onFailure ->
                capturedSuccess = onSuccess
                capturedFailure = onFailure
            }
        )

        controller.onAppLaunch()
        capturedSuccess!!.invoke()
        assertFalse(controller.uiState.value.isLocked)

        // Late/duplicate error callback from the same already-finished prompt
        capturedFailure!!.invoke()

        assertFalse(
            "Stale onFailureOrCancel must not re-lock an already unlocked session",
            controller.uiState.value.isLocked
        )
        assertFalse(
            "Stale onFailureOrCancel must not set showAuthError on an unlocked session",
            controller.uiState.value.showAuthError
        )
    }

    /**
     * Adversarial Attack #2 (Permanent Lockout on Launcher Exception):
     * If launchDeviceAuth throws a synchronous RuntimeException (e.g. FragmentManager state error),
     * AppLockController MUST catch/recover by setting isAuthenticating=false and showAuthError=true
     * so the user sees the error and can tap Retry instead of being stranded forever.
     */
    @Test
    fun adversarial_exceptionInLaunchDeviceAuth_mustTransitionToRetryableErrorState() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var shouldThrow = true
        var successfulRetryInvoked = false

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, onSuccess, _ ->
                if (shouldThrow) {
                    throw IllegalStateException("FragmentManager state already saved")
                } else {
                    successfulRetryInvoked = true
                    onSuccess()
                }
            }
        )

        // Should not crash the app and should leave controller in retryable error state
        runCatching { controller.onAppLaunch() }

        assertTrue("App must stay locked when prompt fails to launch", controller.uiState.value.isLocked)
        assertFalse(
            "LIVENESS BUG: isAuthenticating remained stuck true after launchDeviceAuth threw!",
            controller.uiState.value.isAuthenticating
        )
        assertTrue(
            "LIVENESS BUG: showAuthError must be true so the user sees the Retry button!",
            controller.uiState.value.showAuthError
        )

        shouldThrow = false
        controller.retryAuthentication()
        assertTrue("Retry must launch device auth after previous launch exception", successfulRetryInvoked)
        assertFalse("Retry success must unlock the app", controller.uiState.value.isLocked)
    }

    /**
     * Adversarial Attack #3 (Dead Lock Screen When Backgrounded During Prompt):
     * User opens app -> BiometricPrompt starts (isAuthenticating=true).
     * User backgrounds the app while prompt is showing and returns later when the OS
     * dismissed the prompt without delivering onAuthenticationError.
     * Returning to foreground MUST either re-launch the prompt or expose the Retry button
     * (never remain stuck with isLocked=true, isAuthenticating=true, showAuthError=false and no new prompt).
     */
    @Test
    fun adversarial_backgroundingWhileAuthenticatingAndReturning_mustNotStrandUserWithoutPromptOrRetry() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var launchCount = 0

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, _, _ ->
                launchCount++
            }
        )

        controller.onAppLaunch()
        assertEquals(1, launchCount)
        assertTrue(controller.uiState.value.isLocked)
        assertTrue(controller.uiState.value.isAuthenticating)

        // App goes to background while prompt was active (OS dismisses prompt silently)
        controller.onAppBackgrounded()
        // User comes back to the app
        controller.onAppForegrounded()

        val state = controller.uiState.value
        assertTrue("Must remain locked", state.isLocked)
        val hasRecoveryPath = (launchCount >= 2) || state.showAuthError
        assertTrue(
            "LIVENESS BUG: User stranded on lock screen with no new prompt (launchCount=$launchCount) and showAuthError=${state.showAuthError}",
            hasRecoveryPath
        )
    }

    /**
     * Adversarial Attack #4 (Property-Based State Machine Fuzzing):
     * Over 500 random transitions, verify core security invariants:
     * - When app lock is disabled, state is NEVER locked after launch/foreground/background.
     * - Whenever lock is enabled & capable and backgrounded without suppress, isLocked MUST be true.
     */
    @Test
    fun property_appLockController_neverLocksWhenDisabled_andAlwaysLocksOnUnsuppressedBackground() {
        val prefs = InMemoryAppLockPrefs(enabled = false)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var lastSuccess: (() -> Unit)? = null
        var lastError: (() -> Unit)? = null

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, onSuccess, onError ->
                lastSuccess = onSuccess
                lastError = onError
            }
        )

        val rng = Random(seed = 2026112603L)
        repeat(500) {
            when (rng.nextInt(8)) {
                0 -> {
                    prefs.enabled = rng.nextBoolean()
                }
                1 -> {
                    cap.current = DeviceAuthCapability.entries[rng.nextInt(DeviceAuthCapability.entries.size)]
                }
                2 -> {
                    controller.onAppLaunch()
                    if (!prefs.enabled || cap.current == DeviceAuthCapability.NONE) {
                        // Launch when disabled must not lock if already unlocked
                    } else {
                        assertTrue("Launch with lock enabled & capable must lock", controller.uiState.value.isLocked)
                    }
                }
                3 -> {
                    val wasAuthenticating = controller.uiState.value.isAuthenticating
                    controller.onAppBackgrounded()
                    if (prefs.enabled && cap.current != DeviceAuthCapability.NONE && !wasAuthenticating) {
                        assertTrue("Unsuppressed background with lock enabled must lock", controller.uiState.value.isLocked)
                    }
                }
                4 -> {
                    controller.onAppForegrounded()
                    if (!prefs.enabled || cap.current == DeviceAuthCapability.NONE) {
                        assertFalse(
                            "Foreground when disabled or NONE capability must unlock",
                            controller.uiState.value.isLocked
                        )
                    }
                }
                5 -> controller.retryAuthentication()
                6 -> lastSuccess?.invoke()
                7 -> lastError?.invoke()
            }
        }
    }

    // =========================================================================
    // SECTION 4: NativeBiometricAuthenticator (Empty Title Crash & API Levels)
    // =========================================================================

    /**
     * Adversarial Attack #5 (Crash on Empty Prompt Title):
     * BiometricPrompt.PromptInfo.Builder.build() throws IllegalArgumentException when title is empty.
     * NativeBiometricAuthenticator MUST catch this and invoke onFailureOrCancel() rather than crashing.
     */
    @Test
    fun adversarial_nativeBiometricAuthenticator_emptyTitleMustInvokeFailureCallbackInsteadOfCrashing() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var failureCalled = false
        var successCalled = false

        val authenticator = NativeBiometricAuthenticator(
            activity = activity,
            titleProvider = { "" },
            subtitleProvider = { "Subtitle" }
        )

        authenticator.authenticate(
            capability = DeviceAuthCapability.BIOMETRIC_AND_PIN,
            onSuccess = { successCalled = true },
            onFailureOrCancel = { failureCalled = true }
        )

        assertFalse("Empty title must not unlock", successCalled)
        assertTrue(
            "CRASH/UNHANDLED BUG: Empty title should trigger onFailureOrCancel gracefully",
            failureCalled
        )
    }

    @Test
    fun adversarial_nativeBiometricAuthenticator_pinOnlyOnApi24_doesNotThrowUnsupportedCombination() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var successCalled = false

        val authenticator = NativeBiometricAuthenticator(
            activity = activity,
            titleProvider = { "Desbloquear" },
            subtitleProvider = { "PIN" }
        )

        // On API 24, DEVICE_CREDENTIAL alone throws in AuthenticatorUtils;
        // NativeBiometricAuthenticator must handle API < 30 without throwing IllegalArgumentException.
        val originalSdkInt = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 24)
        try {
            authenticator.authenticate(
                capability = DeviceAuthCapability.PIN_ONLY,
                onSuccess = { successCalled = true },
                onFailureOrCancel = { }
            )
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdkInt)
        }

        assertFalse(successCalled)
    }

    @Test
    fun adversarial_nativeBiometricAuthenticator_noneCapabilityImmediatelyFails() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var failureCalled = false
        var successCalled = false

        val authenticator = NativeBiometricAuthenticator(
            activity = activity,
            titleProvider = { "Unlock" },
            subtitleProvider = { "Subtitle" }
        )

        authenticator.authenticate(
            capability = DeviceAuthCapability.NONE,
            onSuccess = { successCalled = true },
            onFailureOrCancel = { failureCalled = true }
        )

        assertFalse(successCalled)
        assertTrue(failureCalled)
    }

    // =========================================================================
    // SECTION 5: SettingsViewModel & SecuritySectionCard (NONE Capability Bug)
    // =========================================================================

    /**
     * Adversarial Attack #6 (Stuck Checked & Disabled Switch When System Lock Removed):
     * User enables App Lock while PIN is configured. Later, user removes screen lock in
     * Android System Settings (capability becomes NONE) and returns to Settings.
     * Invariant: When deviceAuthCapability == NONE, the Settings UI MUST NOT show
     * the switch as checked=true while disabled (unrecoverable contradictory state).
     */
    @Test
    fun adversarial_removingSystemScreenLock_mustNotLeaveSwitchStuckCheckedAndDisabled() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.PIN_ONLY)
        val i18n = I18nManager().apply {
            initFromContext(RuntimeEnvironment.getApplication())
            setLanguage("es")
        }

        val vm = SettingsViewModel(
            languageRepository = FakeLanguagePreferenceRepository("es"),
            i18nManager = i18n,
            currencyRepository = FakeCurrencyRepository(defaultCurrencies),
            currencyPreferenceRepository = FakeCurrencyPreferenceRepository("COP"),
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap
        )

        // Device lock removed in system settings
        cap.current = DeviceAuthCapability.NONE
        vm.refreshDeviceAuthCapability()

        composeTestRule.setContent {
            SettingsView(settingsViewModel = vm, i18nManager = i18n)
        }

        composeTestRule.onNodeWithTag("switch_app_lock")
            .performScrollTo()
            .assertIsNotEnabled()
            .assertIsOff()
    }

    // =========================================================================
    // SECTION 6: MainActivity Lifecycle Security Bypass on API 24-27 Process Death
    // =========================================================================

    /**
     * Adversarial Attack #7 (API 24-27 Process-Death Authentication Bypass):
     * On Android API 24-27 (minSdk = 24), the system calls onSaveInstanceState() BEFORE onStop().
     * If the user is unlocked, backgrounds the app, and the OS kills the process:
     * 1. onSaveInstanceState saves KEY_APP_LOCKED = false (before onStop locks it).
     * 2. onStop runs and locks only in-memory state, which is then lost on process death.
     * 3. On restart with that savedInstanceState Bundle, MainActivity restores wasLocked = false
     *    and EXPOSES financial data without authentication!
     */
    @Test
    fun adversarial_api27_processDeathAfterBackground_mustNotBypassAppLockOnRecreation() {
        val app = RuntimeEnvironment.getApplication()
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsDeviceSecure(true)

        val sharedPrefs = app.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear()
            .putBoolean(SharedPrefsAppLockPreferenceRepository.KEY_APP_LOCK_ENABLED, true)
            .commit()

        // Simulate saved state captured by API 27 before onStop() ran while user was unlocked:
        val savedStateFromPreOnStop = Bundle().apply {
            putBoolean("app_lock_is_locked", false)
            putBoolean("app_lock_show_error", false)
        }

        // Recreate MainActivity after OS killed the backgrounded process (new process / fresh Activity)
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create(savedStateFromPreOnStop)
            .start()
            .resume()

        val activity = controller.get()
        // Inspect lock state via reflection or public UI: verify lock state is locked after process recreation
        val lockControllerField = MainActivity::class.java.getDeclaredField("appLockController\$delegate")
        lockControllerField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lazyLock = lockControllerField.get(activity) as Lazy<AppLockController>
        val isLockedAfterProcessDeath = lazyLock.value.uiState.value.isLocked

        assertTrue(
            "SECURITY BYPASS (API 24-27): Process death after background recreated MainActivity UNLOCKED because onSaveInstanceState ran before onStop!",
            isLockedAfterProcessDeath
        )
    }

    // =========================================================================
    // SECTION 7: Out-of-Scope Guarantee — Backup & Restore Isolation
    // =========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun adversarial_backupExportAndRestore_neverIncludeOrOverwriteAppLockPreference() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val appLockPrefs = InMemoryAppLockPrefs(enabled = true)
            val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
            val backupPrefs = object : BackupPreferenceRepository {
                var value: Long? = null
                override fun getLastBackupTimestamp(): Long? = value
                override fun saveLastBackupTimestamp(timestampMillis: Long) { value = timestampMillis }
            }
            val backupUseCase = BackupUseCase(
                periodRepository = FakeFinancialPeriodRepository(
                    FinancialPeriod(1L, "2026-11-01", "2026-11-30")
                ),
                incomeRepository = FakeIncomeRepository(),
                budgetRepository = FakeBudgetRepository(),
                expenseRepository = FakeExpenseRepository(),
                expenseCategoryRepository = FakeCategoryRepoForVm(),
                savingsRepository = FakeSavingsRepository(),
                languagePreferenceRepository = FakeLanguagePreferenceRepository("es"),
                currencyPreferenceRepository = FakeCurrencyPreferenceRepository("COP"),
                backupPreferenceRepository = backupPrefs
            )

            val exportedJson = backupUseCase.buildBackupJson(exportedAtMillis = 1_764_100_000_000L)
            assertFalse("Backup JSON must NOT contain app_lock_enabled", exportedJson.contains("app_lock", ignoreCase = true))
            assertFalse("Backup JSON must NOT contain appLockEnabled", exportedJson.contains("appLock", ignoreCase = true))

            val vm = SettingsViewModel(
                languageRepository = FakeLanguagePreferenceRepository("es"),
                i18nManager = I18nManager(),
                currencyPreferenceRepository = FakeCurrencyPreferenceRepository("COP"),
                backupUseCase = backupUseCase,
                backupPreferenceRepository = backupPrefs,
                appLockPreferenceRepository = appLockPrefs,
                deviceAuthCapabilityProvider = cap
            )

            vm.prepareRestore { exportedJson }
            advanceUntilIdle()
            vm.confirmRestore()
            advanceUntilIdle()

            assertTrue("Restore must NOT disable local appLockPreference", appLockPrefs.isAppLockEnabled())
            assertTrue("Restore must NOT change SettingsUiState.isAppLockEnabled", vm.uiState.value.isAppLockEnabled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // SECTION 8: AppLockGate UI Security Invariant & Double-Tap Retry Guard
    // =========================================================================

    @Test
    fun property_appLockGate_neverExposesFinancialContentWhileLockedAcrossAllLocalesAndStates() {
        val i18n = I18nManager().apply {
            initFromContext(RuntimeEnvironment.getApplication())
        }
        val secretAmount = "CONFIDENTIAL_BALANCE_999999"
        var lockState by mutableStateOf(AppLockUiState(isLocked = true))
        var retryClicks = 0

        composeTestRule.setContent {
            AppLockGate(
                lockState = lockState,
                i18nManager = i18n,
                onRetry = { retryClicks++ }
            ) {
                Text(secretAmount)
            }
        }

        val locales = listOf("es", "en", "de", "pt")
        val rng = Random(seed = 2026112604L)

        repeat(40) {
            val locale = locales[rng.nextInt(locales.size)]
            val nextState = AppLockUiState(
                isLocked = rng.nextBoolean(),
                isAuthenticating = rng.nextBoolean(),
                showAuthError = rng.nextBoolean()
            )
            composeTestRule.runOnUiThread {
                i18n.setLanguage(locale)
                lockState = nextState
            }
            composeTestRule.waitForIdle()

            if (nextState.isLocked) {
                composeTestRule.onNodeWithText(secretAmount).assertDoesNotExist()
                composeTestRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
                if (nextState.showAuthError) {
                    composeTestRule.onNodeWithTag("text_app_lock_error").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("btn_app_lock_retry").assertIsDisplayed()
                }
            } else {
                composeTestRule.onNodeWithTag("app_lock_screen").assertDoesNotExist()
                composeTestRule.onNodeWithText(secretAmount).assertIsDisplayed()
            }
        }
    }

    @Test
    fun adversarial_rapidDoubleClickOnRetryButton_triggersOnlySingleAuthAttempt() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var launchCount = 0
        var lastError: (() -> Unit)? = null

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, _, onError ->
                launchCount++
                lastError = onError
            }
        )
        controller.onAppLaunch()
        lastError!!.invoke()
        assertEquals(1, launchCount)

        val i18n = I18nManager().apply {
            initFromContext(RuntimeEnvironment.getApplication())
            setLanguage("es")
        }

        composeTestRule.setContent {
            val state by controller.uiState.collectAsState()
            AppLockGate(
                lockState = state,
                i18nManager = i18n,
                onRetry = controller::retryAuthentication
            ) {
                Text("Unlocked")
            }
        }

        // Rapid double click on Retry while state is still showing button
        composeTestRule.onNodeWithTag("btn_app_lock_retry").performClick()
        controller.retryAuthentication()
        assertEquals("Rapid duplicate retry must only launch prompt once", 2, launchCount)
    }

    /**
     * Adversarial Attack #8 (Suppress Flag Leak Across Unrelated Future Backgrounding):
     * Calling suppressNextBackgroundLock() when launching a translucent system dialog
     * (e.g., Android 13+ POST_NOTIFICATIONS dialog in MainActivity.requestNotificationPermissionIfNeeded)
     * that only pauses/resumes without triggering onStop()/onStart() leaves the suppress flag armed.
     * If onAppBackgrounded() is then called much later on an unrelated Home press,
     * a leaked suppress flag skips locking completely!
     * Also verifies calling suppressNextBackgroundLock() when already locked must NOT clear lock.
     */
    @Test
    fun adversarial_suppressNextBackgroundLock_whileLockedMustNotBypassLockOnForeground() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var launchCount = 0

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, _, _ -> launchCount++ }
        )

        controller.onAppLaunch()
        assertTrue(controller.uiState.value.isLocked)
        assertEquals(1, launchCount)

        // If suppressNextBackgroundLock is ever triggered while already locked,
        // returning to foreground must STILL keep the app locked and not leave it dead.
        controller.suppressNextBackgroundLock()
        controller.onAppBackgrounded()
        controller.onAppForegrounded()

        assertTrue("App must remain locked", controller.uiState.value.isLocked)
    }

    @Test
    fun adversarial_clearBackgroundLockSuppression_preventsLeakedSuppressionAfterPermissionDialog() {
        val prefs = InMemoryAppLockPrefs(enabled = true)
        val cap = MutableCapabilityProvider(DeviceAuthCapability.BIOMETRIC_AND_PIN)
        var launchCount = 0
        var lastSuccess: (() -> Unit)? = null

        val controller = AppLockController(
            appLockPreferenceRepository = prefs,
            deviceAuthCapabilityProvider = cap,
            launchDeviceAuth = { _, onSuccess, _ ->
                launchCount++
                lastSuccess = onSuccess
            }
        )

        controller.onAppLaunch()
        lastSuccess!!.invoke()
        assertFalse(controller.uiState.value.isLocked)

        // Simulate translucent permission dialog: suppresses, finishes without onStop, clears on callback
        controller.suppressNextBackgroundLock()
        controller.clearBackgroundLockSuppression()

        // Later, user sends app to background -> must lock normally
        controller.onAppBackgrounded()
        assertTrue("Cleared suppression must not skip future background lock", controller.uiState.value.isLocked)
    }

    // =========================================================================
    // Helper Fakes
    // =========================================================================

    private class InMemoryAppLockPrefs(var enabled: Boolean) : AppLockPreferenceRepository {
        override fun isAppLockEnabled(): Boolean = enabled
        override fun setAppLockEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class MutableCapabilityProvider(
        var current: DeviceAuthCapability
    ) : DeviceAuthCapabilityProvider {
        override fun getCapability(): DeviceAuthCapability = current
    }
}
