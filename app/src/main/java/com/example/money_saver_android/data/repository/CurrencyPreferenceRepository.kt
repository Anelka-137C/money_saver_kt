package com.example.money_saver_android.data.repository

import android.content.SharedPreferences

interface CurrencyPreferenceRepository {
    fun getCurrency(): String
    fun saveCurrency(currencyCode: String)
}

class SharedPrefsCurrencyRepository(
    private val sharedPreferences: SharedPreferences
) : CurrencyPreferenceRepository {

    companion object {
        const val KEY_CURRENCY = "selected_currency"
        const val DEFAULT_CURRENCY = "COP"
    }

    override fun getCurrency(): String {
        return sharedPreferences.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
    }

    override fun saveCurrency(currencyCode: String) {
        sharedPreferences.edit().putString(KEY_CURRENCY, currencyCode).apply()
    }
}
