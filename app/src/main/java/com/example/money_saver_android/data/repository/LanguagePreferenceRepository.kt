package com.example.money_saver_android.data.repository

import android.content.SharedPreferences
import java.util.Locale

interface LanguagePreferenceRepository {
    fun getLanguage(): String
    fun saveLanguage(languageCode: String)
}

class SharedPrefsLanguageRepository(
    private val sharedPreferences: SharedPreferences,
    private val deviceLocaleSupplier: () -> Locale = { Locale.getDefault() }
) : LanguagePreferenceRepository {

    companion object {
        const val KEY_LANGUAGE = "selected_language"
        val SUPPORTED_LANGUAGES = setOf("es", "en", "de", "pt")
        const val FALLBACK_LANGUAGE = "en"
    }

    override fun getLanguage(): String {
        val saved = sharedPreferences.getString(KEY_LANGUAGE, null)
        if (saved != null) {
            return saved
        }
        val deviceLang = deviceLocaleSupplier().language.lowercase()
        return if (SUPPORTED_LANGUAGES.contains(deviceLang)) {
            deviceLang
        } else {
            FALLBACK_LANGUAGE
        }
    }

    override fun saveLanguage(languageCode: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }
}
