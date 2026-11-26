package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.FakeSavingsRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.FakeCurrencyPreferenceRepository
import com.example.money_saver_android.viewmodel.FakeCurrencyRepository
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeIncomeRepository
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
import com.example.money_saver_android.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var i18nManager: I18nManager
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var fakeCurrPrefRepo: FakeCurrencyPreferenceRepository

    @Before
    fun setUp() {
        i18nManager = I18nManager()
        val esJson = """
            {
              "settings": {
                "header": { "title": "Configuración" },
                "language_section": {
                  "title": "Idioma",
                  "description": "Selecciona el idioma"
                },
                "currency_section": {
                  "title": "Moneda de preferencia",
                  "description": "Selecciona la divisa para ver los importes"
                }
              }
            }
        """.trimIndent()
        i18nManager.loadTranslation("es", esJson)
        i18nManager.setLanguage("es")

        val fakeLangRepo = FakeLanguagePreferenceRepository("es")
        fakeCurrPrefRepo = FakeCurrencyPreferenceRepository("COP")
        val fakeCurrRepo = FakeCurrencyRepository(defaultCurrencies)

        settingsViewModel = SettingsViewModel(
            languageRepository = fakeLangRepo,
            i18nManager = i18nManager,
            currencyRepository = fakeCurrRepo,
            currencyPreferenceRepository = fakeCurrPrefRepo
        )
    }

    @Test
    fun test_settings_screen_displays_currencies_and_allows_selection() {
        composeTestRule.setContent {
            SettingsView(
                settingsViewModel = settingsViewModel,
                i18nManager = i18nManager
            )
        }

        // Verify currency section title is displayed
        composeTestRule.onNodeWithText("Moneda de preferencia").assertExists()

        // Verify available currencies exist
        composeTestRule.onNodeWithTag("curr_option_COP").assertExists()
        composeTestRule.onNodeWithTag("curr_option_USD").assertExists()
        composeTestRule.onNodeWithTag("curr_option_EUR").assertExists()
        composeTestRule.onNodeWithTag("curr_option_BRL").assertExists()

        // Select USD
        composeTestRule.onNodeWithTag("curr_option_USD").performScrollTo().performClick()

        // Verify ViewModel and Repository updated
        assertEquals("USD", settingsViewModel.uiState.value.selectedCurrency)
        assertEquals("USD", fakeCurrPrefRepo.getCurrency())
    }

    @Test
    fun test_settings_screen_displays_backup_section_triggers_action_and_shows_feedback() {
        val backupPrefs = object : BackupPreferenceRepository {
            var value: Long? = null
            override fun getLastBackupTimestamp(): Long? = value
            override fun saveLastBackupTimestamp(timestampMillis: Long) { value = timestampMillis }
        }
        val fixedNow = 1_764_100_000_000L
        val vm = SettingsViewModel(
            languageRepository = FakeLanguagePreferenceRepository("es"),
            i18nManager = i18nManager,
            currencyRepository = FakeCurrencyRepository(defaultCurrencies),
            currencyPreferenceRepository = fakeCurrPrefRepo,
            backupUseCase = BackupUseCase(
                periodRepository = FakeFinancialPeriodRepository(FinancialPeriod(1L, "2026-11-01", "2026-11-30")),
                incomeRepository = FakeIncomeRepository(),
                budgetRepository = FakeBudgetRepository(),
                expenseRepository = FakeExpenseRepository(),
                expenseCategoryRepository = FakeCategoryRepoForVm(),
                savingsRepository = FakeSavingsRepository(),
                languagePreferenceRepository = FakeLanguagePreferenceRepository("es"),
                currencyPreferenceRepository = fakeCurrPrefRepo,
                backupPreferenceRepository = backupPrefs,
                nowProvider = { fixedNow }
            ),
            backupPreferenceRepository = backupPrefs
        )

        var backupClicked = false
        composeTestRule.setContent {
            SettingsView(
                settingsViewModel = vm,
                i18nManager = i18nManager,
                onCreateBackupClick = { backupClicked = true }
            )
        }

        composeTestRule.onNodeWithTag("card_backup_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_last_backup_status").assertTextEquals("Sin respaldos")
        composeTestRule.onNodeWithTag("btn_create_backup").performScrollTo().performClick()
        assertTrue(backupClicked)

        // Simulate write error -> error feedback shown, timestamp remains unset
        composeTestRule.runOnUiThread {
            vm.performBackup { throw IllegalStateException("fail") }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_backup_feedback").performScrollTo()
            .assertTextEquals("No se pudo guardar el respaldo")
        composeTestRule.onNodeWithTag("text_last_backup_status").assertTextEquals("Sin respaldos")

        // Simulate successful backup -> success feedback & timestamp updated
        composeTestRule.runOnUiThread {
            vm.performBackup { }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_backup_feedback").performScrollTo()
            .assertTextEquals("Respaldo guardado correctamente")
        assertEquals(fixedNow, vm.uiState.value.lastBackupTimestamp)
    }

    @Test
    fun test_settings_screen_restore_button_and_confirmation_dialog_flow() {
        val backupPrefs = object : BackupPreferenceRepository {
            var value: Long? = null
            override fun getLastBackupTimestamp(): Long? = value
            override fun saveLastBackupTimestamp(timestampMillis: Long) { value = timestampMillis }
        }
        val backupUseCase = BackupUseCase(
            periodRepository = FakeFinancialPeriodRepository(FinancialPeriod(1L, "2026-11-01", "2026-11-30")),
            incomeRepository = FakeIncomeRepository(),
            budgetRepository = FakeBudgetRepository(),
            expenseRepository = FakeExpenseRepository(),
            expenseCategoryRepository = FakeCategoryRepoForVm(),
            savingsRepository = FakeSavingsRepository(),
            languagePreferenceRepository = FakeLanguagePreferenceRepository("es"),
            currencyPreferenceRepository = fakeCurrPrefRepo,
            backupPreferenceRepository = backupPrefs
        )
        val validJson = kotlinx.coroutines.runBlocking {
            backupUseCase.buildBackupJson(exportedAtMillis = 1_764_200_000_000L)
        }
        val vm = SettingsViewModel(
            languageRepository = FakeLanguagePreferenceRepository("es"),
            i18nManager = i18nManager,
            currencyRepository = FakeCurrencyRepository(defaultCurrencies),
            currencyPreferenceRepository = fakeCurrPrefRepo,
            backupUseCase = backupUseCase,
            backupPreferenceRepository = backupPrefs
        )

        var restoreClicked = false
        var restoreConfirmedCallback = false
        composeTestRule.setContent {
            SettingsView(
                settingsViewModel = vm,
                i18nManager = i18nManager,
                onRestoreBackupClick = { restoreClicked = true },
                onRestoreConfirmed = { restoreConfirmedCallback = true }
            )
        }

        composeTestRule.onNodeWithTag("btn_restore_backup").performScrollTo().performClick()
        assertTrue(restoreClicked)

        // Prepare restore -> opens confirmation dialog with summary and SlideToConfirmButton
        composeTestRule.runOnUiThread {
            vm.prepareRestore { validJson }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_restore_backup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_restore_backup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_restore_backup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_restore_backup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("text_restore_feedback").performScrollTo()
            .assertTextEquals("Restauración cancelada")

        // Prepare again and confirm restore -> success shown and last backup stays "Sin respaldos"
        composeTestRule.runOnUiThread {
            vm.prepareRestore { validJson }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            vm.confirmRestore { restoreConfirmedCallback = true }
        }
        composeTestRule.waitForIdle()
        assertTrue(restoreConfirmedCallback)
        composeTestRule.onNodeWithTag("text_restore_feedback").performScrollTo()
            .assertTextEquals("Datos restaurados correctamente")
        composeTestRule.onNodeWithTag("text_last_backup_status").performScrollTo()
            .assertTextEquals("Sin respaldos")
    }
}
