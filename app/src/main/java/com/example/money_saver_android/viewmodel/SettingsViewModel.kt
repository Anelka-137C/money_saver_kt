package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.i18n.I18nManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LanguageOption(
    val code: String,
    val nativeName: String
)

data class SettingsUiState(
    val selectedLanguage: String = "es",
    val availableLanguages: List<LanguageOption> = listOf(
        LanguageOption("es", "Español"),
        LanguageOption("en", "English"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("pt", "Português (Brasil)")
    ),
    val selectedCurrency: String = "COP",
    val availableCurrencies: List<Currency> = emptyList()
)

class SettingsViewModel(
    private val languageRepository: LanguagePreferenceRepository,
    private val i18nManager: I18nManager,
    private val currencyRepository: CurrencyRepository? = null,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val initialLanguage = languageRepository.getLanguage()
        i18nManager.setLanguage(initialLanguage)
        val initialCurrency = currencyPreferenceRepository?.getCurrency() ?: "COP"
        _uiState.update {
            it.copy(
                selectedLanguage = initialLanguage,
                selectedCurrency = initialCurrency
            )
        }

        currencyRepository?.let { repo ->
            viewModelScope.launch {
                repo.preloadDefaultCurrenciesIfNeeded()
                repo.getAllCurrencies().collect { currencies ->
                    _uiState.update { it.copy(availableCurrencies = currencies) }
                }
            }
        }
    }

    fun onLanguageSelected(languageCode: String) {
        languageRepository.saveLanguage(languageCode)
        i18nManager.setLanguage(languageCode)
        _uiState.update { it.copy(selectedLanguage = languageCode) }
    }

    fun onCurrencySelected(currencyCode: String) {
        currencyPreferenceRepository?.saveCurrency(currencyCode)
        _uiState.update { it.copy(selectedCurrency = currencyCode) }
    }
}
