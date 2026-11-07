package com.example.money_saver_android.i18n

import android.content.Context
import com.i18next.android.I18Next
import org.json.JSONObject

class I18nManager(
    private val i18next: I18Next = I18Next()
) {
    var currentLanguage: String = "es"
        private set

    fun initFromContext(context: Context) {
        val assetManager = context.assets
        listOf("es", "en", "de", "pt").forEach { lang ->
            try {
                val content = assetManager.open("$lang.json").bufferedReader().use { it.readText() }
                loadTranslation(lang, content)
            } catch (e: Exception) {
                // Ignore missing file
            }
        }
    }

    fun loadTranslation(locale: String, jsonContent: String) {
        val jsonObject = JSONObject(jsonContent)
        i18next.loader()
            .lang(locale)
            .from(jsonObject)
            .load()
    }

    fun setLanguage(locale: String) {
        currentLanguage = locale
        i18next.options.language = locale
    }

    fun t(key: String): String {
        return i18next.t(key) ?: key
    }
}
