package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var i18nManager: I18nManager
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setUp() {
        i18nManager = I18nManager()
        val esJson = """
            {
              "navigation": {
                "tab_expenses": "Gastos",
                "tab_incomes": "Contabilidad",
                "tab_loans": "Préstamos",
                "tab_settings": "Configuración",
                "tab_period": "Período"
              },
              "expenses": {
                "header": { "title": "Gastos" },
                "history": { "title": "Historial de gastos" }
              },
              "settings": {
                "header": { "title": "Configuración" },
                "language_section": {
                  "title": "Idioma",
                  "description": "Selecciona el idioma"
                }
              },
              "period": {
                "title": "Período Financiero",
                "active_period_title": "Período activo",
                "start_date": "Fecha de inicio",
                "end_date": "Fecha de fin",
                "save_button": "Guardar período",
                "edit_button": "Modificar período"
              }
            }
        """.trimIndent()
        val enJson = """
            {
              "navigation": {
                "tab_expenses": "Expenses",
                "tab_incomes": "Accounting",
                "tab_loans": "Loans",
                "tab_settings": "Settings",
                "tab_period": "Period"
              },
              "expenses": {
                "header": { "title": "Expenses" },
                "history": { "title": "Expense history" }
              },
              "settings": {
                "header": { "title": "Settings" },
                "language_section": {
                  "title": "Language",
                  "description": "Select language"
                }
              },
              "period": {
                "title": "Financial Period",
                "active_period_title": "Active period",
                "start_date": "Start date",
                "end_date": "End date",
                "save_button": "Save period",
                "edit_button": "Modify period"
              }
            }
        """.trimIndent()
        i18nManager.loadTranslation("es", esJson)
        i18nManager.loadTranslation("en", enJson)
        i18nManager.setLanguage("es")

        val fakeRepo = FakeLanguagePreferenceRepository("es")
        settingsViewModel = SettingsViewModel(fakeRepo, i18nManager)
    }

    @Test
    fun test_bottom_navigation_switches_between_accounting_and_settings() {
        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Bottom bar no longer has Gastos tab and defaults to Contabilidad
        composeTestRule.onNodeWithTag("tab_expenses").assertDoesNotExist()
        composeTestRule.onNodeWithText("Contabilidad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()

        // Click Settings tab
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Click Contabilidad tab
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()
    }

    @Test
    fun test_language_selection_updates_ui_texts_in_real_time() {
        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text(i18nManager.t("navigation.tab_incomes"))
                }
            )
        }

        // Open Settings
        composeTestRule.onNodeWithTag("tab_settings").performClick()

        // Verify Spanish texts
        composeTestRule.onNodeWithText("Idioma").assertIsDisplayed()

        // Select English
        composeTestRule.onNodeWithTag("lang_option_en").performClick()

        // Verify immediate real-time update to English in Settings
        composeTestRule.onNodeWithText("Language").assertIsDisplayed()

        // Switch back to Contabilidad tab and verify both tab and content updated to Accounting
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onAllNodesWithText("Accounting").assertCountEquals(2)
    }

    @Test
    fun test_app_starts_on_period_tab_and_locks_navigation_when_no_active_period() {
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Screen is locked to Period tab and Gastos bottom tab does not exist
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_expenses").assertDoesNotExist()

        // Attempting to click Contabilidad tab keeps lock on Period
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertDoesNotExist()

        // Attempting to click Settings tab is allowed even when no active period exists (to allow Drive backup restore)
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Returning to Contabilidad still keeps lock on Period
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertDoesNotExist()
    }

    @Test
    fun test_saving_valid_period_unlocks_navigation_and_shows_active_period_with_edit_option() {
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Fill valid dates covering 2026-11-07
        composeTestRule.onNodeWithTag("input_start_date").performTextInput("2026-11-01")
        composeTestRule.onNodeWithTag("input_end_date").performTextInput("2026-11-30")
        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        // Active period info is now visible with the range and create button stays
        composeTestRule.onNodeWithTag("active_period_info").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026-11-01 - 2026-11-30").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar período").assertIsDisplayed()

        // Navigation is now unlocked
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()

        // Switch to Settings
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Switch back to Period
        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
    }

    @Test
    fun test_incomes_tab_navigation_and_lock_when_no_active_period() {
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Ingresos")
                }
            )
        }

        // 1. Without active period: app starts on Period and navigation is locked
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Ingresos").assertDoesNotExist()

        // 2. Configure active period
        composeTestRule.onNodeWithTag("input_start_date").performTextInput("2026-11-01")
        composeTestRule.onNodeWithTag("input_end_date").performTextInput("2026-11-30")
        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        // 3. Navigation is unlocked: navigate to Contabilidad tab
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Vista de Ingresos").assertIsDisplayed()
    }

    @Test
    fun test_app_starts_on_accounting_tab_when_current_date_in_active_period() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = activePeriod)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Must display accounting view directly on initial launch
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_screen").assertDoesNotExist()

        // Navigation is unlocked: can visit Period and Settings
        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Can return to Contabilidad
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()
    }

    @Test
    fun test_app_starts_on_period_tab_when_period_is_expired() {
        // Expired period: ended on 2026-10-31, but current date is 2026-11-07
        val expiredPeriod = FinancialPeriod(id = 1L, startDate = "2026-10-01", endDate = "2026-10-31")
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = expiredPeriod)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Current date is not in active period -> must start on Period tab
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertDoesNotExist()

        // Navigation is locked
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertDoesNotExist()

        // Settings tab remains accessible even when period is expired so user can restore backups
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun test_requestedTab_settings_navigates_directly_to_settings_and_allows_switching_back() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val periodViewModel = PeriodViewModel(
            PeriodUseCase(FakeFinancialPeriodRepository(initialPeriod = activePeriod), currentDateProvider = { "2026-11-07" })
        )
        var consumed = false

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                requestedTab = AppTab.SETTINGS,
                onRequestedTabConsumed = { consumed = true },
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        org.junit.Assert.assertTrue(consumed)

        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Vista de Contabilidad").assertIsDisplayed()
    }

    @Test
    fun test_floating_bottom_bar_displays_all_tabs_and_reflects_selected_state() {
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                }
            )
        }

        // Floating bottom bar container and all 4 tabs render; Period starts selected while locked
        composeTestRule.onNodeWithTag("floating_bottom_nav").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_incomes").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_loans").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_settings").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_period").assertIsDisplayed().assertIsSelected()

        // Locked Contabilidad tab does not become selected on click
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("tab_incomes").assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_period").assertIsSelected()

        // Settings remains selectable while period is locked
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("tab_settings").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_period").assertIsNotSelected()

        // Configure valid period to unlock Contabilidad
        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("input_start_date").performTextInput("2026-11-01")
        composeTestRule.onNodeWithTag("input_end_date").performTextInput("2026-11-30")
        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        // Unlocked: switching tabs updates selected state accurately
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithTag("tab_incomes").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_settings").assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_period").assertIsNotSelected()

        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("tab_settings").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_incomes").assertIsNotSelected()

        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("tab_period").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_settings").assertIsNotSelected()
    }

    @Test
    fun test_loans_tab_is_enabled_even_when_no_active_period() {
        val fakePeriodRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val periodUseCase = PeriodUseCase(fakePeriodRepo, currentDateProvider = { "2026-11-07" })
        val periodViewModel = PeriodViewModel(periodUseCase)

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                incomesContent = {
                    androidx.compose.material3.Text("Vista de Contabilidad")
                },
                loansContent = {
                    androidx.compose.material3.Text("Vista de Préstamos")
                }
            )
        }

        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_loans").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("tab_loans").assertIsSelected()
        composeTestRule.onNodeWithText("Vista de Préstamos").assertIsDisplayed()
    }
}
