package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
    fun test_bottom_navigation_switches_between_expenses_and_settings() {
        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                i18nManager = i18nManager,
                expensesContent = {
                    androidx.compose.material3.Text("Vista de Gastos")
                }
            )
        }

        // Initially shows Expenses content
        composeTestRule.onNodeWithText("Vista de Gastos").assertIsDisplayed()

        // Click Settings tab
        composeTestRule.onNodeWithTag("tab_settings").performClick()

        // Now shows Settings content
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Click Expenses tab
        composeTestRule.onNodeWithTag("tab_expenses").performClick()

        // Back to Expenses content
        composeTestRule.onNodeWithText("Vista de Gastos").assertIsDisplayed()
    }

    @Test
    fun test_language_selection_updates_ui_texts_in_real_time() {
        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                i18nManager = i18nManager,
                expensesContent = {
                    androidx.compose.material3.Text(i18nManager.t("expenses.header.title"))
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

        // Switch back to Expenses tab and verify both the tab and screen content updated in real-time to English
        composeTestRule.onNodeWithTag("tab_expenses").performClick()
        composeTestRule.onAllNodesWithText("Expenses").assertCountEquals(2)
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
                expensesContent = {
                    androidx.compose.material3.Text("Vista de Gastos")
                }
            )
        }

        // Screen is locked to Period tab
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()

        // Attempting to click Expenses tab
        composeTestRule.onNodeWithTag("tab_expenses").performClick()

        // Still on Period screen, expenses not displayed
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vista de Gastos").assertDoesNotExist()

        // Attempting to click Settings tab
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen").assertDoesNotExist()
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
                expensesContent = {
                    androidx.compose.material3.Text("Vista de Gastos")
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
        composeTestRule.onNodeWithTag("tab_expenses").performClick()
        composeTestRule.onNodeWithText("Vista de Gastos").assertIsDisplayed()

        // Switch to Settings
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Switch back to Period
        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
    }
}
