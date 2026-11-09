package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.domain.model.Budget
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
class BudgetRepositoryTest {

    private lateinit var db: BudgetDatabase
    private lateinit var repository: RoomBudgetRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomBudgetRepository(db.budgetDao(), db.budgetCategoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_budget_dao_save_and_update_budget_by_category_and_period() = runBlocking {
        val budget = Budget(
            amount = 500.0,
            category = "Food & Dining",
            subcategory = "Supermarket",
            periodId = 1L
        )

        val insertedId = repository.insertBudget(budget)
        val budgetsPeriod1 = repository.getBudgetsByPeriod(1L).first()

        assertEquals(1, budgetsPeriod1.size)
        val saved = budgetsPeriod1.first()
        assertEquals(insertedId, saved.id)
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Food & Dining", saved.category)
        assertEquals("Supermarket", saved.subcategory)
        assertEquals(1L, saved.periodId)

        // Update budget amount
        val updatedBudget = saved.copy(amount = 750.0)
        repository.updateBudget(updatedBudget)

        val updatedList = repository.getBudgetsByPeriod(1L).first()
        assertEquals(1, updatedList.size)
        assertEquals(750.0, updatedList.first().amount, 0.001)

        // Verify period isolation
        val budgetsPeriod2 = repository.getBudgetsByPeriod(2L).first()
        assertEquals(0, budgetsPeriod2.size)
    }

    @Test
    fun test_budget_category_and_subcategory_persistence_and_preload() = runBlocking {
        repository.preloadDefaultCategoriesIfNeeded()
        val categories = repository.getAllCategories().first()
        assertEquals(defaultCategories.size, categories.size)

        // Insert new category in budget_category
        val customCategory = Category(
            name = "Inversiones",
            icon = "📈",
            color = "#10B981",
            subcategories = listOf(Subcategory("Acciones"), Subcategory("Cripto"))
        )
        repository.insertCategory(customCategory)

        val updatedCategories = repository.getAllCategories().first()
        val found = updatedCategories.find { it.name == "Inversiones" }
        assertEquals("Inversiones", found?.name)
        assertEquals(2, found?.subcategories?.size)

        // Insert subcategory in budget_subcategory
        repository.insertSubcategory("Inversiones", Subcategory("Bienes Raíces"))
        val categoriesWithNewSub = repository.getAllCategories().first()
        val foundUpdated = categoriesWithNewSub.find { it.name == "Inversiones" }
        assertEquals(3, foundUpdated?.subcategories?.size)
    }
}
