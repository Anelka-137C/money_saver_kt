package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeIncomeRepoForUseCase : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        _incomes.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertIncome(income: Income): Long {
        val id = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _incomes.value = _incomes.value + income.copy(id = id)
        return id
    }

    override suspend fun updateIncome(income: Income) {
        _incomes.value = _incomes.value.map { if (it.id == income.id) income else it }
    }

    override suspend fun deleteIncome(id: Long) {
        _incomes.value = _incomes.value.filter { it.id != id }
    }

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

    override suspend fun countIncomesForCategory(categoryName: String): Int {
        return _incomes.value.count { it.category == categoryName }
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name == categoryName) {
                cat.copy(subcategories = cat.subcategories.filter { it.name != subcategoryName })
            } else {
                cat
            }
        }
    }

    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int {
        return _incomes.value.count { it.category == categoryName && it.subcategory == subcategoryName }
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = listOf(
                Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salary")))
            )
        }
    }
}

class IncomeUseCaseTest {

    private lateinit var repository: FakeIncomeRepoForUseCase
    private lateinit var useCase: IncomeUseCase

    @Before
    fun setup() {
        repository = FakeIncomeRepoForUseCase()
        useCase = IncomeUseCase(repository)
    }

    @Test
    fun saveIncome_withValidData_returnsSuccessAndInserts() = runBlocking {
        val result = useCase.saveIncome(
            amountInput = "2323.0",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "Main salary",
            periodId = 1L
        )

        assertTrue(result is IncomeResult.Success)
        val list = useCase.getIncomesByPeriod(1L).first()
        assertEquals(1, list.size)
        assertEquals(2323.0, list.first().amount, 0.0)
    }

    @Test
    fun saveIncome_withInvalidAmount_returnsError() = runBlocking {
        val resultEmpty = useCase.saveIncome(
            amountInput = "",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultEmpty is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultEmpty as IncomeResult.Error).message)

        val resultZero = useCase.saveIncome(
            amountInput = "0",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultZero is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultZero as IncomeResult.Error).message)

        val resultNegative = useCase.saveIncome(
            amountInput = "-100",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNegative is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultNegative as IncomeResult.Error).message)
    }

    @Test
    fun saveIncome_withoutCategory_returnsError() = runBlocking {
        val resultNoCategory = useCase.saveIncome(
            amountInput = "500",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNoCategory is IncomeResult.Error)
        assertEquals("Debes seleccionar una categoría", (resultNoCategory as IncomeResult.Error).message)
    }

    @Test
    fun saveIncome_withoutSubcategory_returnsSuccessAndInserts() = runBlocking {
        val resultNoSubcategory = useCase.saveIncome(
            amountInput = "500",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNoSubcategory is IncomeResult.Success)
        val list = useCase.getIncomesByPeriod(1L).first()
        assertEquals(1, list.size)
        assertEquals("Employment", list.first().category)
        assertEquals("", list.first().subcategory)
    }

    @Test
    fun updateIncome_withoutSubcategory_returnsSuccessAndUpdates() = runBlocking {
        useCase.saveIncome(
            amountInput = "500",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        val saved = useCase.getIncomesByPeriod(1L).first().first()

        val updateResult = useCase.updateIncome(
            id = saved.id,
            amountInput = "600",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(updateResult is IncomeResult.Success)
        val updated = useCase.getIncomesByPeriod(1L).first().first()
        assertEquals(600.0, updated.amount, 0.0)
        assertEquals("", updated.subcategory)
    }

    @Test
    fun calculateTotalIncome_sumsAllAmountsCorrectly() {
        val incomes = listOf(
            Income(amount = 1000.0, dateTime = "", category = "", subcategory = "", periodId = 1L),
            Income(amount = 1323.0, dateTime = "", category = "", subcategory = "", periodId = 1L)
        )
        val total = useCase.calculateTotalIncome(incomes)
        assertEquals(2323.0, total, 0.0)
    }

    @Test
    fun deleteCategory_whenIncomesAssociated_returnsErrorAndDoesNotDelete() = runBlocking {
        useCase.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList()))
        useCase.saveIncome(
            amountInput = "1000",
            dateTimeInput = "2026-05-01 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )

        val errorResult = useCase.deleteCategory("Employment")
        assertTrue(errorResult is IncomeResult.Error)
        assertEquals("No se puede eliminar la categoría porque tiene ingresos asociados", (errorResult as IncomeResult.Error).message)

        val categories = useCase.getCategories().first()
        assertTrue(categories.any { it.name == "Employment" })
    }

    @Test
    fun deleteCategory_whenNoIncomesAssociated_deletesSuccessfully() = runBlocking {
        useCase.insertCategory(Category(name = "Freelance", icon = "💻", color = "#1CC88A", subcategories = emptyList()))
        var categories = useCase.getCategories().first()
        assertTrue(categories.any { it.name == "Freelance" })

        val successResult = useCase.deleteCategory("Freelance")
        assertTrue(successResult is IncomeResult.Success)

        categories = useCase.getCategories().first()
        assertTrue(categories.none { it.name == "Freelance" })
    }

    @Test
    fun deleteSubcategory_whenIncomesAssociated_returnsErrorAndDoesNotDelete() = runBlocking {
        useCase.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salary"))))
        useCase.saveIncome(
            amountInput = "1000",
            dateTimeInput = "2026-05-01 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )

        val errorResult = useCase.deleteSubcategory("Employment", "Salary")
        assertTrue(errorResult is IncomeResult.Error)
        assertEquals("No se puede eliminar la subcategoría porque tiene ingresos asociados", (errorResult as IncomeResult.Error).message)

        val categories = useCase.getCategories().first()
        val employment = categories.first { it.name == "Employment" }
        assertTrue(employment.subcategories.any { it.name == "Salary" })
    }

    @Test
    fun deleteSubcategory_whenNoIncomesAssociated_deletesSuccessfully() = runBlocking {
        useCase.insertCategory(Category(name = "Freelance", icon = "💻", color = "#1CC88A", subcategories = listOf(Subcategory("Web"))))
        val initialCategories = useCase.getCategories().first()
        val initialFreelance = initialCategories.first { it.name == "Freelance" }
        assertTrue(initialFreelance.subcategories.any { it.name == "Web" })

        val successResult = useCase.deleteSubcategory("Freelance", "Web")
        assertTrue(successResult is IncomeResult.Success)

        val categories = useCase.getCategories().first()
        val freelance = categories.first { it.name == "Freelance" }
        assertTrue(freelance.subcategories.none { it.name == "Web" })
    }

    @Test
    fun test_calculate_category_budgets_consolidates_subcategories_into_single_category() {
        val cats = listOf(Category(name = "Food & Dining", icon = "🍔", color = "#1CC88A", subcategories = emptyList()))
        val budgets = listOf(
            Budget(id = 1L, amount = 300.0, category = "Food & Dining", subcategory = "Supermarket", periodId = 1L),
            Budget(id = 2L, amount = 200.0, category = "Food & Dining", subcategory = "Restaurants", periodId = 1L)
        )
        val result = useCase.calculateCategoryBudgets(budgets, cats, emptyList(), "2026-05-01", "2026-05-31")
        assertEquals(1, result.size)
        assertEquals("Food & Dining", result[0].categoryName)
        assertEquals("#1CC88A", result[0].colorHex)
        assertEquals(500.0, result[0].budgetAmount, 0.0)
    }

    @Test
    fun test_calculate_category_budgets_with_zero_expenses_returns_full_budget_as_remaining_and_progress_1() {
        val cats = listOf(Category(name = "Transport", icon = "🚗", color = "#E74A3B", subcategories = emptyList()))
        val budgets = listOf(Budget(id = 1L, amount = 400.0, category = "Transport", periodId = 1L))
        val result = useCase.calculateCategoryBudgets(budgets, cats, emptyList(), "2026-05-01", "2026-05-31")
        assertEquals(400.0, result[0].remainingAmount, 0.0)
        assertEquals(1.0f, result[0].progress, 0.001f)
        assertEquals(100, result[0].percent)
    }

    @Test
    fun test_calculate_category_budgets_with_partial_expenses_in_period_calculates_remaining_correctly() {
        val cats = listOf(Category(name = "Food & Dining", icon = "🍔", color = "#1CC88A", subcategories = emptyList()))
        val budgets = listOf(Budget(id = 1L, amount = 500.0, category = "Food & Dining", periodId = 1L))
        val expenses = listOf(Expense(id = 1L, amount = 125.0, date = "2026-05-10", description = "Lunch", category = "Food & Dining", subcategory = "Restaurants"))
        val result = useCase.calculateCategoryBudgets(budgets, cats, expenses, "2026-05-01", "2026-05-31")
        assertEquals(375.0, result[0].remainingAmount, 0.0)
        assertEquals(0.75f, result[0].progress, 0.001f)
        assertEquals(75, result[0].percent)
    }

    @Test
    fun test_calculate_category_budgets_ignores_expenses_outside_period_date_range() {
        val cats = listOf(Category(name = "Food & Dining", icon = "🍔", color = "#1CC88A", subcategories = emptyList()))
        val budgets = listOf(Budget(id = 1L, amount = 500.0, category = "Food & Dining", periodId = 1L))
        val expenses = listOf(
            Expense(id = 1L, amount = 200.0, date = "2026-06-05", description = "Next month", category = "Food & Dining", subcategory = "Coffee")
        )
        val result = useCase.calculateCategoryBudgets(budgets, cats, expenses, "2026-05-01", "2026-05-31")
        assertEquals(500.0, result[0].remainingAmount, 0.0)
        assertEquals(100, result[0].percent)
    }

    @Test
    fun test_calculate_category_and_global_budgets_deduct_by_budgetId_category_and_periodId() {
        val cats = listOf(Category(name = "Food & Dining", icon = "🍔", color = "#1CC88A", subcategories = emptyList()))
        val budgets = listOf(Budget(id = 10L, amount = 500.0, category = "Food & Dining", periodId = 1L))
        val expenses = listOf(
            Expense(id = 1L, amount = 150.0, date = "2026-05-10", description = "Lunch", category = "Restaurantes", periodId = 1L, budgetId = 10L, budgetCategory = "Food & Dining"),
            Expense(id = 2L, amount = 90.0, date = "2026-05-12", description = "Other period", category = "Restaurantes", periodId = 2L, budgetId = 10L, budgetCategory = "Food & Dining"),
            Expense(id = 3L, amount = 40.0, date = "2026-05-14", description = "Wrong cat", category = "Restaurantes", periodId = 1L, budgetId = 20L, budgetCategory = "Transport")
        )
        val result = useCase.calculateCategoryBudgets(budgets, cats, expenses, "2026-05-01", "2026-05-31", periodId = 1L)
        assertEquals(150.0, result[0].spentAmount, 0.0)
        assertEquals(350.0, result[0].remainingAmount, 0.0)

        val totalSpent = useCase.calculateTotalBudgetSpent(budgets, expenses, "2026-05-01", "2026-05-31", periodId = 1L)
        assertEquals(150.0, totalSpent, 0.0)
        assertEquals(350.0, useCase.calculateRemainingBudget(500.0, totalSpent), 0.0)
        assertEquals(0.0, useCase.calculateRemainingBudget(100.0, totalSpent), 0.0)
    }

    @Test
    fun updateCategory_validatesEmptyNotFoundDuplicate_andUpdatesSuccessfully() = runBlocking {
        repository.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList()))
        repository.insertCategory(Category(name = "Investments", icon = "📈", color = "#1CC88A", subcategories = emptyList()))

        val emptyRes = useCase.updateCategory("Employment", "   ", "#F6C23E")
        assertTrue(emptyRes is IncomeResult.Error)

        val notFoundRes = useCase.updateCategory("Missing", "Other", "#F6C23E")
        assertTrue(notFoundRes is IncomeResult.Error)

        val duplicateRes = useCase.updateCategory("Employment", "investments", "#F6C23E")
        assertTrue(duplicateRes is IncomeResult.Error)

        val okRes = useCase.updateCategory("Employment", "Trabajo", "#F6C23E", "🏢")
        assertTrue(okRes is IncomeResult.Success)
    }

    @Test
    fun updateBudgetCategory_validatesAndReassignsExpenseBudgetCategory() = runBlocking {
        val fakeBudgetRepo = FakeBudgetRepository()
        fakeBudgetRepo.insertCategory(Category(name = "Food & Dining", icon = "🍔", color = "#4E73DF", subcategories = emptyList()))
        fakeBudgetRepo.insertCategory(Category(name = "Transport", icon = "🚗", color = "#1CC88A", subcategories = emptyList()))

        val reassignedPairs = mutableListOf<Pair<String, String>>()
        val trackingExpenseRepo = object : com.example.money_saver_android.data.repository.ExpenseRepository {
            override fun getAllExpenses(): Flow<List<Expense>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun insertExpense(expense: Expense) {}
            override suspend fun updateExpense(expense: Expense) {}
            override suspend fun deleteExpense(id: Long) {}
            override suspend fun reassignBudgetCategory(oldName: String, newName: String) {
                reassignedPairs.add(oldName to newName)
            }
        }
        val uc = IncomeUseCase(repository = repository, budgetRepository = fakeBudgetRepo, expenseRepository = trackingExpenseRepo)

        assertTrue(uc.updateBudgetCategory("Food & Dining", "transport", "#F6C23E") is IncomeResult.Error)
        assertTrue(uc.updateBudgetCategory("Food & Dining", "", "#F6C23E") is IncomeResult.Error)

        val res = uc.updateBudgetCategory("Food & Dining", "Alimentación", "#F6C23E")
        assertTrue(res is IncomeResult.Success)
        assertEquals(listOf("Food & Dining" to "Alimentación"), reassignedPairs)
    }
}
