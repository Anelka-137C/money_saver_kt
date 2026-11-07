package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.i18n.I18nManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
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
}
