package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.CurrencyDao
import com.example.money_saver_android.data.local.CurrencyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCurrencyRepository(
    private val dao: CurrencyDao
) : CurrencyRepository {

    override fun getAllCurrencies(): Flow<List<Currency>> {
        return dao.getAllCurrencies().map { list ->
            list.map {
                Currency(
                    code = it.code,
                    name = it.name,
                    symbol = it.symbol,
                    formatPattern = it.formatPattern
                )
            }
        }
    }

    override suspend fun preloadDefaultCurrenciesIfNeeded() {
        if (dao.isCurrencyTableCreated() > 0 && dao.getCurrencyCount() == 0) {
            val entities = defaultCurrencies.map {
                CurrencyEntity(
                    code = it.code,
                    name = it.name,
                    symbol = it.symbol,
                    formatPattern = it.formatPattern
                )
            }
            dao.insertCurrencies(entities)
        }
    }
}
