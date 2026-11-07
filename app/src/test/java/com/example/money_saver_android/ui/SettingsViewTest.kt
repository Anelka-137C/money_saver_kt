package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.FakeCurrencyPreferenceRepository
import com.example.money_saver_android.viewmodel.FakeCurrencyRepository
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
import com.example.money_saver_android.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
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
}
