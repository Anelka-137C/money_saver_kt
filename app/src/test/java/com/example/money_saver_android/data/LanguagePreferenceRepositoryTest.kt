package com.example.money_saver_android.data

import android.content.Context
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LanguagePreferenceRepositoryTest {

    private lateinit var repository: LanguagePreferenceRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SharedPrefsLanguageRepository(prefs)
    }

    @Test
    fun test_language_preference_repository_saves_and_retrieves_selected_locale() {
        repository.saveLanguage("pt")
        assertEquals("pt", repository.getLanguage())

        repository.saveLanguage("de")
        assertEquals("de", repository.getLanguage())
    }

    @Test
    fun test_language_preference_repository_defaults_to_device_language_or_fallback_english() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_app_prefs_fallback", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val repoEs = SharedPrefsLanguageRepository(prefs, deviceLocaleSupplier = { java.util.Locale("es") })
        assertEquals("es", repoEs.getLanguage())

        val repoPt = SharedPrefsLanguageRepository(prefs, deviceLocaleSupplier = { java.util.Locale("pt", "BR") })
        assertEquals("pt", repoPt.getLanguage())

        val repoUnsupported = SharedPrefsLanguageRepository(prefs, deviceLocaleSupplier = { java.util.Locale.FRANCE })
        assertEquals("en", repoUnsupported.getLanguage())
    }
}
