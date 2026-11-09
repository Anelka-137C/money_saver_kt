package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeBudgetRepository : BudgetRepository {
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())

    override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
        _budgets.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertBudget(budget: Budget): Long {
        val id = (_budgets.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _budgets.value = _budgets.value + budget.copy(id = id)
        return id
    }

    override suspend fun updateBudget(budget: Budget) {
        _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        return _budgets.value.find { it.id == id }
    }

    override suspend fun deleteBudget(id: Long) {
        _budgets.value = _budgets.value.filter { it.id != id }
    }

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    override fun getAllCategories(): Flow<List<Category>> = _categories
    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }
    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map {
            if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
        }
    }
    override suspend fun deleteCategory(categoryName: String) {
        _categories.value = _categories.value.filter { it.name != categoryName }
    }
    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map {
            if (it.name == categoryName) it.copy(subcategories = it.subcategories.filter { sub -> sub.name != subcategoryName }) else it
        }
    }
    override suspend fun countBudgetsForCategory(categoryName: String): Int =
        _budgets.value.count { it.category == categoryName }
    override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int =
        _budgets.value.count { it.category == categoryName && it.subcategory == subcategoryName }
    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = com.example.money_saver_android.data.defaultCategories
        }
    }
}

class FakeIncomeRepositoryForBudget : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        _incomes.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertIncome(income: Income): Long {
        val id = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _incomes.value = _incomes.value + income.copy(id = id)
        return id
    }

    override suspend fun updateIncome(income: Income) {}
    override suspend fun deleteIncome(id: Long) {}
    override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override suspend fun insertCategory(category: Category) {}
    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
    override suspend fun deleteCategory(categoryName: String) {}
    override suspend fun countIncomesForCategory(categoryName: String): Int = 0
    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
    override suspend fun preloadDefaultCategoriesIfNeeded() {}
}

class BudgetUseCaseTest {

    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var incomeRepository: FakeIncomeRepositoryForBudget
    private lateinit var useCase: BudgetUseCase

    @Before
    fun setup() {
        budgetRepository = FakeBudgetRepository()
        incomeRepository = FakeIncomeRepositoryForBudget()
        useCase = BudgetUseCase(budgetRepository, incomeRepository)
    }

    @Test
    fun test_budget_use_case_returns_total_budgeted_for_period() = runBlocking {
        val periodId = 1L
        budgetRepository.insertBudget(Budget(amount = 200.0, category = "Food", periodId = periodId))
        budgetRepository.insertBudget(Budget(amount = 150.0, category = "Transport", periodId = periodId))
        budgetRepository.insertBudget(Budget(amount = 500.0, category = "Housing", periodId = 2L))

        val period1Budgets = useCase.getBudgetsByPeriod(periodId).first()
        val totalBudgeted = useCase.calculateTotalBudgeted(period1Budgets)

        assertEquals(350.0, totalBudgeted, 0.001)
    }

    @Test
    fun test_budget_use_case_calculates_available_income_and_validates_exceeding_budget() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-05-15 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )

        // Initial available income
        val availableInitial = useCase.calculateAvailableIncome(1000.0, 0.0)
        assertEquals(1000.0, availableInitial, 0.001)

        // Invalid budget: amount exceeds total available income
        val resultExceeding = useCase.saveBudget(
            amountInput = "1200",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(resultExceeding is BudgetResult.Error)
        assertEquals("El monto supera el ingreso disponible", (resultExceeding as BudgetResult.Error).message)

        // Invalid budget: negative or zero amount
        val resultZero = useCase.saveBudget(
            amountInput = "0",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(resultZero is BudgetResult.Error)

        // Valid budget
        val resultSuccess = useCase.saveBudget(
            amountInput = "400",
            categoryInput = "Food",
            subcategoryInput = "Supermarket",
            periodId = periodId
        )
        assertTrue(resultSuccess is BudgetResult.Success)

        val budgets = useCase.getBudgetsByPeriod(periodId).first()
        assertEquals(1, budgets.size)
        assertEquals(400.0, budgets.first().amount, 0.001)

        // Now available income should be 600
        val totalBudgeted = useCase.calculateTotalBudgeted(budgets)
        val availableNow = useCase.calculateAvailableIncome(1000.0, totalBudgeted)
        assertEquals(600.0, availableNow, 0.001)

        // Second budget: exceeding remaining 600
        val resultSecondExceeding = useCase.saveBudget(
            amountInput = "700",
            categoryInput = "Transport",
            periodId = periodId
        )
        assertTrue(resultSecondExceeding is BudgetResult.Error)

        // Updating budget: modify 400 to 900 (allowed since 1000 total available for this item)
        val budgetId = budgets.first().id
        val updateSuccess = useCase.updateBudget(
            id = budgetId,
            amountInput = "900",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(updateSuccess is BudgetResult.Success)

        val updatedBudgets = useCase.getBudgetsByPeriod(periodId).first()
        assertEquals(900.0, updatedBudgets.first().amount, 0.001)

        // Updating budget: modify 900 to 1100 (exceeds 1000 available)
        val updateExceeding = useCase.updateBudget(
            id = budgetId,
            amountInput = "1100",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(updateExceeding is BudgetResult.Error)
    }
}
