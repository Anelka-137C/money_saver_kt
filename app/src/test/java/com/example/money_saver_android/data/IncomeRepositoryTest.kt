package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.repository.RoomIncomeRepository
import com.example.money_saver_android.domain.model.Income
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class IncomeRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomIncomeRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomIncomeRepository(db.incomeDao(), db.incomeCategoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_income_and_category_dao_insert_and_queries() = runBlocking {
        // Test income categories and subcategories
        val category = Category(
            name = "Employment",
            icon = "💼",
            color = "#4E73DF",
            subcategories = listOf(Subcategory("Salary"), Subcategory("Bonus"))
        )
        repository.insertCategory(category)

        val categories = repository.getAllCategories().first()
        assertTrue(categories.isNotEmpty())
        val foundCategory = categories.find { it.name == "Employment" }
        assertTrue(foundCategory != null)
        assertEquals(2, foundCategory!!.subcategories.size)

        // Test income persistence and filtering by periodId
        val income1 = Income(
            amount = 2323.0,
            dateTime = "2026-05-15 10:30",
            category = "Employment",
            subcategory = "Salary",
            description = "Main salary",
            periodId = 1L
        )
        val income2 = Income(
            amount = 500.0,
            dateTime = "2026-06-10 14:00",
            category = "Employment",
            subcategory = "Bonus",
            description = "June bonus",
            periodId = 2L
        )

        repository.insertIncome(income1)
        repository.insertIncome(income2)

        val period1Incomes = repository.getIncomesByPeriod(1L).first()
        assertEquals(1, period1Incomes.size)
        assertEquals(2323.0, period1Incomes.first().amount, 0.0)
        assertEquals("2026-05-15 10:30", period1Incomes.first().dateTime)
        assertEquals("Salary", period1Incomes.first().subcategory)
        assertEquals(1L, period1Incomes.first().periodId)

        val period2Incomes = repository.getIncomesByPeriod(2L).first()
        assertEquals(1, period2Incomes.size)
        assertEquals(500.0, period2Incomes.first().amount, 0.0)
    }

    @Test
    fun test_delete_income_category_succeeds_when_no_incomes_associated() = runBlocking {
        val category = Category(
            name = "Investments",
            icon = "📈",
            color = "#1CC88A",
            subcategories = listOf(Subcategory("Stocks"))
        )
        repository.insertCategory(category)

        var categories = repository.getAllCategories().first()
        assertTrue(categories.any { it.name == "Investments" })
        assertEquals(0, repository.countIncomesForCategory("Investments"))

        repository.deleteCategory("Investments")

        categories = repository.getAllCategories().first()
        assertTrue(categories.none { it.name == "Investments" })
    }

    @Test
    fun test_income_category_dao_delete_subcategory_by_name() = runBlocking {
        val category = Category(
            name = "Employment",
            icon = "💼",
            color = "#4E73DF",
            subcategories = listOf(Subcategory("Salary"), Subcategory("Bonus"))
        )
        repository.insertCategory(category)

        var categories = repository.getAllCategories().first()
        var empCategory = categories.first { it.name == "Employment" }
        assertEquals(2, empCategory.subcategories.size)

        repository.deleteSubcategory("Employment", "Bonus")

        categories = repository.getAllCategories().first()
        empCategory = categories.first { it.name == "Employment" }
        assertEquals(1, empCategory.subcategories.size)
        assertTrue(empCategory.subcategories.none { it.name == "Bonus" })
        assertTrue(empCategory.subcategories.any { it.name == "Salary" })
    }
}
