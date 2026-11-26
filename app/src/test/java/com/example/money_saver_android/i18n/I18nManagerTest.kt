package com.example.money_saver_android.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class I18nManagerTest {

    @Test
    fun test_nested_keys_resolution() {
        val i18n = I18nManager()
        val json = """
            {
              "expenses": {
                "header": {
                  "title": "Gastos"
                }
              }
            }
        """.trimIndent()
        i18n.loadTranslation("es", json)
        i18n.setLanguage("es")
        assertEquals("Gastos", i18n.t("expenses.header.title"))
    }

    @Test
    fun test_language_manager_loads_and_translates_keys_for_all_locales() {
        val i18n = I18nManager()

        val esJson = """{"navigation": {"tab_expenses": "Gastos", "tab_settings": "Configuración"}}"""
        val enJson = """{"navigation": {"tab_expenses": "Expenses", "tab_settings": "Settings"}}"""
        val deJson = """{"navigation": {"tab_expenses": "Ausgaben", "tab_settings": "Einstellungen"}}"""
        val ptJson = """{"navigation": {"tab_expenses": "Despesas", "tab_settings": "Configurações"}}"""

        i18n.loadTranslation("es", esJson)
        i18n.loadTranslation("en", enJson)
        i18n.loadTranslation("de", deJson)
        i18n.loadTranslation("pt", ptJson)

        i18n.setLanguage("es")
        assertEquals("Gastos", i18n.t("navigation.tab_expenses"))
        assertEquals("Configuración", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("en")
        assertEquals("Expenses", i18n.t("navigation.tab_expenses"))
        assertEquals("Settings", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("de")
        assertEquals("Ausgaben", i18n.t("navigation.tab_expenses"))
        assertEquals("Einstellungen", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("pt")
        assertEquals("Despesas", i18n.t("navigation.tab_expenses"))
        assertEquals("Configurações", i18n.t("navigation.tab_settings"))
    }

    @Test
    fun test_app_lock_translations_present_in_all_four_languages() {
        val i18n = I18nManager()
        i18n.initFromContext(RuntimeEnvironment.getApplication())

        val requiredKeys = listOf(
            "settings.security_section.title",
            "settings.security_section.description",
            "settings.security_section.toggle_label",
            "settings.security_section.capability_biometric_and_pin",
            "settings.security_section.capability_pin_only",
            "settings.security_section.capability_none",
            "app_lock.prompt_title",
            "app_lock.prompt_subtitle",
            "app_lock.screen_title",
            "app_lock.screen_subtitle",
            "app_lock.auth_error",
            "app_lock.retry_button"
        )

        listOf("es", "en", "de", "pt").forEach { locale ->
            i18n.setLanguage(locale)
            requiredKeys.forEach { key ->
                val resolved = i18n.t(key)
                assertNotEquals("Missing key $key for locale $locale", key, resolved)
                assertTrue("Blank key $key for locale $locale", resolved.isNotBlank())
            }
        }
    }
}
