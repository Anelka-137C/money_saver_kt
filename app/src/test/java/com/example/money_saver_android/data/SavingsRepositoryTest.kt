package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SavingsRepositoryTest {

    private lateinit var db: SavingsDatabase
    private lateinit var repository: RoomSavingsRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = RoomSavingsRepository(db.savingDao(), db.savingCategoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun updateCategory_renamesMigratesSubcategoriesAndSavings() = runBlocking {
        repository.insertCategory(
            Category(
                name = "Viajes",
                icon = "✈️",
                color = "#4E73DF",
                subcategories = listOf(Subcategory("Vuelos"), Subcategory("Hoteles"))
            )
        )
        repository.insertSaving(
            Saving(
                amount = 450.0,
                category = "Viajes",
                subcategory = "Vuelos",
                periodId = 1L,
                incomeId = 1L,
                dateTime = "2026-11-10 10:00"
            )
        )

        repository.updateCategory(
            oldName = "Viajes",
            newName = "Vacaciones",
            color = "#1CC88A",
            icon = "🏖️"
        )

        val categories = repository.getAllCategories().first()
        assertNull(categories.find { it.name == "Viajes" })
        val renamed = categories.find { it.name == "Vacaciones" }
        assertTrue(renamed != null)
        assertEquals("#1CC88A", renamed!!.color)
        assertEquals("🏖️", renamed.icon)
        assertEquals(listOf("Hoteles", "Vuelos"), renamed.subcategories.map { it.name }.sorted())

        val savings = repository.getSavingsByPeriod(1L).first()
        assertEquals(1, savings.size)
        assertEquals("Vacaciones", savings.first().category)
    }

    @Test
    fun updateCategory_casingOnlyRename_preservesSubcategoriesAndSavings() = runBlocking {
        repository.insertCategory(
            Category(
                name = "ahorro",
                icon = "🏦",
                color = "#4E73DF",
                subcategories = listOf(Subcategory("Reserva"))
            )
        )
        repository.insertSaving(
            Saving(
                amount = 200.0,
                category = "ahorro",
                subcategory = "Reserva",
                periodId = 1L,
                incomeId = 1L,
                dateTime = "2026-11-10 10:00"
            )
        )

        repository.updateCategory(
            oldName = "ahorro",
            newName = "Ahorro",
            color = "#F6C23E",
            icon = "🏦"
        )

        val categories = repository.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Ahorro", categories.first().name)
        assertEquals("#F6C23E", categories.first().color)
        assertEquals(listOf("Reserva"), categories.first().subcategories.map { it.name })

        val savings = repository.getSavingsByPeriod(1L).first()
        assertEquals(1, savings.size)
        assertEquals("Ahorro", savings.first().category)
    }
}
