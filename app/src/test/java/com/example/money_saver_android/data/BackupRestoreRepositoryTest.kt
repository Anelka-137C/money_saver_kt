package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomFinancialPeriodRepository
import com.example.money_saver_android.data.repository.RoomIncomeRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
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
class BackupRestoreRepositoryTest {

    private lateinit var configDb: ConfigDatabase
    private lateinit var appDb: AppDatabase
    private lateinit var budgetDb: BudgetDatabase
    private lateinit var savingsDb: SavingsDatabase

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        configDb = Room.inMemoryDatabaseBuilder(context, ConfigDatabase::class.java)
            .allowMainThreadQueries().build()
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        budgetDb = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries().build()
        savingsDb = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        configDb.close()
        appDb.close()
        budgetDb.close()
        savingsDb.close()
    }

    @Test
    fun replaceAllRepositories_wipesPreviousData_andPreservesExactIdsAndSubcategories() = runBlocking {
        val periodRepo = RoomFinancialPeriodRepository(configDb.financialPeriodDao())
        val incomeRepo = RoomIncomeRepository(appDb.incomeDao(), appDb.incomeCategoryDao())
        val expenseRepo = RoomExpenseRepository(appDb.expenseDao())
        val expenseCatRepo = RoomCategoryRepository(appDb.categoryDao())
        val budgetRepo = RoomBudgetRepository(budgetDb.budgetDao(), budgetDb.budgetCategoryDao())
        val savingsRepo = RoomSavingsRepository(savingsDb.savingDao(), savingsDb.savingCategoryDao())

        // Pre-existing local data that must be replaced
        periodRepo.savePeriod(FinancialPeriod(id = 1L, startDate = "2026-01-01", endDate = "2026-01-31"))
        incomeRepo.insertCategory(Category("OldInc", "💰", "#111111", listOf(Subcategory("OldSub"))))
        incomeRepo.insertIncome(Income(id = 1L, amount = 100.0, dateTime = "2026-01-05 10:00", category = "OldInc", periodId = 1L))

        // Restore snapshot with explicit IDs
        val restoredPeriod = FinancialPeriod(id = 7L, startDate = "2026-11-01", endDate = "2026-11-30")
        val restoredIncome = Income(id = 12L, amount = 3200.0, dateTime = "2026-11-02 08:30", category = "Trabajo", subcategory = "Nómina", description = "Pago", periodId = 7L)
        val restoredBudget = Budget(id = 21L, amount = 900.0, category = "Hogar", subcategory = "Renta", periodId = 7L)
        val restoredExpense = Expense(id = 33L, amount = 150.0, date = "2026-11-05 12:00", description = "Mercado", category = "Comida", subcategory = "Super", periodId = 7L, budgetId = 21L, budgetCategory = "Hogar", fundingSource = ExpenseFundingSource.BUDGET, savingCategory = "")
        val restoredSaving = Saving(id = 44L, amount = 500.0, category = "Viajes", subcategory = "Vuelos", periodId = 7L, incomeId = 12L, incomeCategory = "Trabajo", dateTime = "2026-11-03 09:00", note = "Eurotrip")

        periodRepo.replaceAllPeriods(listOf(restoredPeriod))
        incomeRepo.replaceAllCategories(listOf(Category("Trabajo", "💼", "#4E73DF", listOf(Subcategory("Nómina")))))
        incomeRepo.replaceAllIncomes(listOf(restoredIncome))
        budgetRepo.replaceAllCategories(listOf(Category("Hogar", "🏠", "#1CC88A", listOf(Subcategory("Renta")))))
        budgetRepo.replaceAllBudgets(listOf(restoredBudget))
        expenseCatRepo.replaceAllCategories(listOf(Category("Comida", "🍔", "#E74A3B", listOf(Subcategory("Super")))))
        expenseRepo.replaceAllExpenses(listOf(restoredExpense))
        savingsRepo.replaceAllCategories(listOf(Category("Viajes", "✈️", "#36B9CC", listOf(Subcategory("Vuelos")))))
        savingsRepo.replaceAllSavings(listOf(restoredSaving))

        assertEquals(listOf(restoredPeriod), periodRepo.getAllPeriods().first())
        assertEquals(listOf(restoredIncome), incomeRepo.getIncomesByPeriod(7L).first())
        assertEquals(listOf("Trabajo"), incomeRepo.getAllCategories().first().map { it.name })
        assertEquals(listOf("Nómina"), incomeRepo.getAllCategories().first().single().subcategories.map { it.name })
        assertEquals(listOf(restoredBudget), budgetRepo.getBudgetsByPeriod(7L).first())
        assertEquals(listOf(restoredExpense), expenseRepo.getAllExpenses().first())
        assertEquals(listOf("Comida"), expenseCatRepo.getAllCategories().first().map { it.name })
        assertEquals(listOf(restoredSaving), savingsRepo.getAllSavings().first())
        assertEquals(listOf("Viajes"), savingsRepo.getAllCategories().first().map { it.name })
    }
}
