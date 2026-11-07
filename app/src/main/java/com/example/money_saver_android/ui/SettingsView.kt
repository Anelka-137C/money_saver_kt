package com.example.money_saver_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.components.settings.CurrencySectionCard
import com.example.money_saver_android.ui.components.settings.LanguageSectionCard
import com.example.money_saver_android.ui.components.settings.SettingsHeader
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.viewmodel.SettingsViewModel

@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    i18nManager: I18nManager,
    modifier: Modifier = Modifier
) {
    val state by settingsViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(
            title = i18nManager.t("settings.header.title")
        )

        LanguageSectionCard(
            title = i18nManager.t("settings.language_section.title"),
            description = i18nManager.t("settings.language_section.description"),
            availableLanguages = state.availableLanguages,
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = settingsViewModel::onLanguageSelected
        )

        CurrencySectionCard(
            title = i18nManager.t("settings.currency_section.title"),
            description = i18nManager.t("settings.currency_section.description"),
            availableCurrencies = state.availableCurrencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = settingsViewModel::onCurrencySelected
        )
    }
}
