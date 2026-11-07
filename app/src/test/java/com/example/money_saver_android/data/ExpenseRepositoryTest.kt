package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomExpenseRepository

    @Before
    fun createDb() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomExpenseRepository(db.expenseDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun expenses_persistAndCanBeRetrieved() = runBlocking {
        val expense = Expense(
            amount = 2500.0,
            date = "2026-11-06",
            description = "Salario",
            category = "Ingresos",
            subcategory = "Nómina"
        )

        repository.insertExpense(expense)

        val expenses = repository.getAllExpenses().first()
        assertEquals(1, expenses.size)
        val retrieved = expenses.first()
        assertEquals(2500.0, retrieved.amount, 0.0)
        assertEquals("2026-11-06", retrieved.date)
        assertEquals("Salario", retrieved.description)
        assertEquals("Ingresos", retrieved.category)
        assertEquals("Nómina", retrieved.subcategory)
    }

    @Test
    fun updateExpense_updatesExistingRecordInDatabase() = runBlocking {
        val initial = Expense(
            amount = 100.0,
            date = "2026-11-06",
            description = "Almuerzo",
            category = "Alimentación",
            subcategory = "Restaurante"
        )
        repository.insertExpense(initial)
        val inserted = repository.getAllExpenses().first().first()

        val updated = inserted.copy(
            amount = 120.0,
            description = "Cena familiar",
            subcategory = "Cena"
        )
        repository.updateExpense(updated)

        val expenses = repository.getAllExpenses().first()
        assertEquals(1, expenses.size)
        val retrieved = expenses.first()
        assertEquals(120.0, retrieved.amount, 0.0)
        assertEquals("Cena familiar", retrieved.description)
        assertEquals("Cena", retrieved.subcategory)
        assertEquals(inserted.id, retrieved.id)
    }
}
