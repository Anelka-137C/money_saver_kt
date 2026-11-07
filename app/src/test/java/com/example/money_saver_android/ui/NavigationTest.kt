package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
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
                "tab_settings": "Configuración"
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
              }
            }
        """.trimIndent()
        val enJson = """
            {
              "navigation": {
                "tab_expenses": "Expenses",
                "tab_settings": "Settings"
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
}
