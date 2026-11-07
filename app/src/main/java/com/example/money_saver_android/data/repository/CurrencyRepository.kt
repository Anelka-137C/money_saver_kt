package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Currency
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun getAllCurrencies(): Flow<List<Currency>>
    suspend fun preloadDefaultCurrenciesIfNeeded()
}
