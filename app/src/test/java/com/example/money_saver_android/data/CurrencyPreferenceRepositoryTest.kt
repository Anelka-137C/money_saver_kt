package com.example.money_saver_android.data

import android.content.Context
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsCurrencyRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CurrencyPreferenceRepositoryTest {

    private lateinit var repository: CurrencyPreferenceRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_currency_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SharedPrefsCurrencyRepository(prefs)
    }

    @Test
    fun test_currency_preference_persists_and_restores_selected_currency() {
        // Default is COP
        assertEquals("COP", repository.getCurrency())

        // Save USD
        repository.saveCurrency("USD")
        assertEquals("USD", repository.getCurrency())

        // Simulate app restart with a new repository instance pointing to same preferences
        val newInstance = SharedPrefsCurrencyRepository(
            context.getSharedPreferences("test_currency_prefs", Context.MODE_PRIVATE)
        )
        assertEquals("USD", newInstance.getCurrency())
    }
}
