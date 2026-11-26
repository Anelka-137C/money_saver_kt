package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.AppLockPreferenceRepository
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.FakeSavingsRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.notifications.BackupReminderScheduler
import com.example.money_saver_android.security.DeviceAuthCapability
import com.example.money_saver_android.security.DeviceAuthCapabilityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeLanguagePreferenceRepository(
    var currentLanguage: String = "es"
) : LanguagePreferenceRepository {
    override fun getLanguage(): String = currentLanguage
    override fun saveLanguage(languageCode: String) {
        currentLanguage = languageCode
    }
}

class FakeCurrencyPreferenceRepository(
    var currentCurrency: String = "COP"
) : CurrencyPreferenceRepository {
    override fun getCurrency(): String = currentCurrency
    override fun saveCurrency(currencyCode: String) {
        currentCurrency = currencyCode
    }
}

class FakeCurrencyRepository(
    private val currencies: List<Currency> = defaultCurrencies
) : CurrencyRepository {
    override fun getAllCurrencies(): Flow<List<Currency>> = flowOf(currencies)
    override suspend fun preloadDefaultCurrenciesIfNeeded() {}
}

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @Test
    fun test_app_initialization_loads_saved_language_preference() {
        val fakeRepo = FakeLanguagePreferenceRepository(currentLanguage = "de")
        val i18nManager = I18nManager()

        val viewModel = SettingsViewModel(fakeRepo, i18nManager)

        assertEquals("de", viewModel.uiState.value.selectedLanguage)
        assertEquals("de", i18nManager.currentLanguage)
    }

    @Test
    fun test_settings_viewmodel_change_language_updates_state_and_persists() {
        val fakeRepo = FakeLanguagePreferenceRepository(currentLanguage = "es")
        val i18nManager = I18nManager()

        val viewModel = SettingsViewModel(fakeRepo, i18nManager)
        assertEquals("es", viewModel.uiState.value.selectedLanguage)

        viewModel.onLanguageSelected("pt")

        assertEquals("pt", viewModel.uiState.value.selectedLanguage)
        assertEquals("pt", fakeRepo.getLanguage())
        assertEquals("pt", i18nManager.currentLanguage)
    }

    @Test
    fun test_settings_viewmodel_loads_currencies_and_selects_currency() {
        val fakeLangRepo = FakeLanguagePreferenceRepository()
        val fakeCurrPrefRepo = FakeCurrencyPreferenceRepository(currentCurrency = "COP")
        val fakeCurrencyRepo = FakeCurrencyRepository()
        val i18nManager = I18nManager()

        val viewModel = SettingsViewModel(
            languageRepository = fakeLangRepo,
            i18nManager = i18nManager,
            currencyRepository = fakeCurrencyRepo,
            currencyPreferenceRepository = fakeCurrPrefRepo
        )

        assertEquals("COP", viewModel.uiState.value.selectedCurrency)
        assertEquals(4, viewModel.uiState.value.availableCurrencies.size)

        viewModel.onCurrencySelected("USD")

        assertEquals("USD", viewModel.uiState.value.selectedCurrency)
        assertEquals("USD", fakeCurrPrefRepo.getCurrency())
    }

    @Test
    fun test_settings_viewmodel_initializes_with_persisted_currency() {
        val fakeLangRepo = FakeLanguagePreferenceRepository()
        val fakeCurrPrefRepo = FakeCurrencyPreferenceRepository(currentCurrency = "EUR")
        val fakeCurrencyRepo = FakeCurrencyRepository()
        val i18nManager = I18nManager()

        val viewModel = SettingsViewModel(
            languageRepository = fakeLangRepo,
            i18nManager = i18nManager,
            currencyRepository = fakeCurrencyRepo,
            currencyPreferenceRepository = fakeCurrPrefRepo
        )

        assertEquals("EUR", viewModel.uiState.value.selectedCurrency)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun performBackup_updatesTimestampAndReschedulesReminder_andHandlesCancelOrError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val backupPrefs = object : BackupPreferenceRepository {
                var value: Long? = null
                override fun getLastBackupTimestamp(): Long? = value
                override fun saveLastBackupTimestamp(timestampMillis: Long) { value = timestampMillis }
            }
            val scheduledTimestamps = mutableListOf<Long?>()
            val fakeScheduler = object : BackupReminderScheduler {
                override fun scheduleNextReminder(lastBackupTimestamp: Long?) {
                    scheduledTimestamps.add(lastBackupTimestamp)
                }
                override fun cancelReminders() {}
            }
            val now = 1_764_100_000_000L
            val backupUseCase = BackupUseCase(
                periodRepository = FakeFinancialPeriodRepository(
                    FinancialPeriod(1L, "2026-11-01", "2026-11-30")
                ),
                incomeRepository = FakeIncomeRepository(),
                budgetRepository = FakeBudgetRepository(),
                expenseRepository = FakeExpenseRepository(),
                expenseCategoryRepository = FakeCategoryRepoForVm(),
                savingsRepository = FakeSavingsRepository(),
                languagePreferenceRepository = FakeLanguagePreferenceRepository(),
                currencyPreferenceRepository = FakeCurrencyPreferenceRepository(),
                backupPreferenceRepository = backupPrefs,
                nowProvider = { now }
            )

            val viewModel = SettingsViewModel(
                languageRepository = FakeLanguagePreferenceRepository(),
                i18nManager = I18nManager(),
                backupUseCase = backupUseCase,
                backupPreferenceRepository = backupPrefs,
                backupReminderScheduler = fakeScheduler
            )
            assertNull(viewModel.uiState.value.lastBackupTimestamp)
            assertEquals(BackupStatus.IDLE, viewModel.uiState.value.backupStatus)

            // Cancelled does not change timestamp
            viewModel.onBackupCancelled()
            assertEquals(BackupStatus.CANCELLED, viewModel.uiState.value.backupStatus)
            assertNull(viewModel.uiState.value.lastBackupTimestamp)

            // Error does not change timestamp
            viewModel.performBackup { throw IllegalStateException("IO failed") }
            advanceUntilIdle()
            assertEquals(BackupStatus.ERROR, viewModel.uiState.value.backupStatus)
            assertNull(viewModel.uiState.value.lastBackupTimestamp)

            // Success updates timestamp and schedules 7-day reminder
            viewModel.performBackup { /* write succeeds */ }
            advanceUntilIdle()
            assertEquals(BackupStatus.SUCCESS, viewModel.uiState.value.backupStatus)
            assertEquals(now, viewModel.uiState.value.lastBackupTimestamp)
            assertEquals(listOf(now), scheduledTimestamps)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun prepareAndConfirmRestore_updatesLanguageAndCurrency_withoutChangingLastBackupTimestamp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val uploadTimestamp = 1_764_000_000_000L
            val backupPrefs = object : BackupPreferenceRepository {
                var value: Long? = uploadTimestamp
                override fun getLastBackupTimestamp(): Long? = value
                override fun saveLastBackupTimestamp(timestampMillis: Long) { value = timestampMillis }
            }
            val scheduledTimestamps = mutableListOf<Long?>()
            val fakeScheduler = object : BackupReminderScheduler {
                override fun scheduleNextReminder(lastBackupTimestamp: Long?) {
                    scheduledTimestamps.add(lastBackupTimestamp)
                }
                override fun cancelReminders() {}
            }
            val langRepo = FakeLanguagePreferenceRepository("es")
            val currRepo = FakeCurrencyPreferenceRepository("COP")
            val i18n = I18nManager()
            val backupUseCase = BackupUseCase(
                periodRepository = FakeFinancialPeriodRepository(
                    FinancialPeriod(1L, "2026-11-01", "2026-11-30")
                ),
                incomeRepository = FakeIncomeRepository(),
                budgetRepository = FakeBudgetRepository(),
                expenseRepository = FakeExpenseRepository(),
                expenseCategoryRepository = FakeCategoryRepoForVm(),
                savingsRepository = FakeSavingsRepository(),
                languagePreferenceRepository = FakeLanguagePreferenceRepository("en"),
                currencyPreferenceRepository = FakeCurrencyPreferenceRepository("EUR"),
                backupPreferenceRepository = backupPrefs
            )
            val validJson = backupUseCase.buildBackupJson(exportedAtMillis = 1_764_500_000_000L)

            val viewModel = SettingsViewModel(
                languageRepository = langRepo,
                i18nManager = i18n,
                currencyPreferenceRepository = currRepo,
                backupUseCase = backupUseCase,
                backupPreferenceRepository = backupPrefs,
                backupReminderScheduler = fakeScheduler
            )
            scheduledTimestamps.clear()

            // Invalid JSON sets INVALID_FILE
            viewModel.prepareRestore { "{invalid" }
            advanceUntilIdle()
            assertEquals(RestoreStatus.INVALID_FILE, viewModel.uiState.value.restoreStatus)
            assertNull(viewModel.uiState.value.pendingRestoreSnapshot)

            // Valid JSON sets pendingRestoreSnapshot, cancel clears it
            viewModel.prepareRestore { validJson }
            advanceUntilIdle()
            assertEquals(1_764_500_000_000L, viewModel.uiState.value.pendingRestoreSnapshot?.exportedAtMillis)
            viewModel.cancelPendingRestore()
            assertNull(viewModel.uiState.value.pendingRestoreSnapshot)
            assertEquals(RestoreStatus.CANCELLED, viewModel.uiState.value.restoreStatus)

            // Prepare again and confirm restore
            viewModel.prepareRestore { validJson }
            advanceUntilIdle()
            var restoredCallbackInvoked = false
            viewModel.confirmRestore { restoredCallbackInvoked = true }
            advanceUntilIdle()

            assertEquals(RestoreStatus.SUCCESS, viewModel.uiState.value.restoreStatus)
            assertEquals("en", viewModel.uiState.value.selectedLanguage)
            assertEquals("en", i18n.currentLanguage)
            assertEquals("EUR", viewModel.uiState.value.selectedCurrency)
            assertEquals(true, restoredCallbackInvoked)
            // Must NOT change lastBackupTimestamp or reschedule reminder
            assertEquals(uploadTimestamp, viewModel.uiState.value.lastBackupTimestamp)
            assertEquals(emptyList<Long?>(), scheduledTimestamps)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun test_settings_viewmodel_loads_and_toggles_app_lock_preference_with_capability() {
        val fakeAppLockRepo = object : AppLockPreferenceRepository {
            var enabled = true
            override fun isAppLockEnabled(): Boolean = enabled
            override fun setAppLockEnabled(enabled: Boolean) {
                this.enabled = enabled
            }
        }
        var currentCapability = DeviceAuthCapability.BIOMETRIC_AND_PIN
        val fakeCapabilityProvider = object : DeviceAuthCapabilityProvider {
            override fun getCapability(): DeviceAuthCapability = currentCapability
        }

        val viewModel = SettingsViewModel(
            languageRepository = FakeLanguagePreferenceRepository(),
            i18nManager = I18nManager(),
            appLockPreferenceRepository = fakeAppLockRepo,
            deviceAuthCapabilityProvider = fakeCapabilityProvider
        )

        assertEquals(true, viewModel.uiState.value.isAppLockEnabled)
        assertEquals(DeviceAuthCapability.BIOMETRIC_AND_PIN, viewModel.uiState.value.deviceAuthCapability)

        viewModel.onAppLockToggled(false)
        assertEquals(false, viewModel.uiState.value.isAppLockEnabled)
        assertEquals(false, fakeAppLockRepo.enabled)

        currentCapability = DeviceAuthCapability.PIN_ONLY
        viewModel.refreshDeviceAuthCapability()
        assertEquals(DeviceAuthCapability.PIN_ONLY, viewModel.uiState.value.deviceAuthCapability)

        viewModel.onAppLockToggled(true)
        assertEquals(true, viewModel.uiState.value.isAppLockEnabled)
        assertEquals(true, fakeAppLockRepo.enabled)

        // When no lock is configured, cannot enable protection
        viewModel.onAppLockToggled(false)
        currentCapability = DeviceAuthCapability.NONE
        viewModel.refreshDeviceAuthCapability()
        assertEquals(DeviceAuthCapability.NONE, viewModel.uiState.value.deviceAuthCapability)

        viewModel.onAppLockToggled(true)
        assertEquals(false, viewModel.uiState.value.isAppLockEnabled)
        assertEquals(false, fakeAppLockRepo.enabled)
    }
}
