package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.repository.RoomCurrencyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CurrencyRepositoryTest {

    private lateinit var db: ConfigDatabase
    private lateinit var repository: RoomCurrencyRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ConfigDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCurrencyRepository(db.currencyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_currency_dao_provides_prepopulated_currencies() = runBlocking {
        repository.preloadDefaultCurrenciesIfNeeded()
        val currencies = repository.getAllCurrencies().first()

        assertEquals(4, currencies.size)

        val cop = currencies.find { it.code == "COP" }
        assertNotNull(cop)
        assertEquals("Peso colombiano", cop!!.name)
        assertEquals("$", cop.symbol)
        assertEquals("#,##0", cop.formatPattern)

        val brl = currencies.find { it.code == "BRL" }
        assertNotNull(brl)
        assertEquals("Real brasileño", brl!!.name)
        assertEquals("R$", brl.symbol)
        assertEquals("#,##0.00", brl.formatPattern)

        val eur = currencies.find { it.code == "EUR" }
        assertNotNull(eur)
        assertEquals("Euro", eur!!.name)
        assertEquals("€", eur.symbol)
        assertEquals("#,##0.00", eur.formatPattern)

        val usd = currencies.find { it.code == "USD" }
        assertNotNull(usd)
        assertEquals("Dólar", usd!!.name)
        assertEquals("$", usd.symbol)
        assertEquals("#,##0.00", usd.formatPattern)
    }
}
