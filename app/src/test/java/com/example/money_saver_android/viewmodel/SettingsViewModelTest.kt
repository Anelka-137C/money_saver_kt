package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.i18n.I18nManager
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
}
