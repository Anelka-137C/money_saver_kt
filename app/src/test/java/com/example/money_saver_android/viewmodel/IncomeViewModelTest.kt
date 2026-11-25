package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeIncomeRepository : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> {
        return _incomes.map { list -> list.filter { it.periodId == periodId } }
    }

    override suspend fun insertIncome(income: Income): Long {
        val newId = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val saved = income.copy(id = newId)
        _incomes.value = _incomes.value + saved
        return newId
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

    override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        _categories.value = _categories.value.map {
            if (it.name == oldName) it.copy(name = newName, color = color, icon = icon) else it
        }
        _incomes.value = _incomes.value.map {
            if (it.category.equals(oldName, ignoreCase = true)) it.copy(category = newName) else it
        }
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

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeIncomeRepository
    private lateinit var viewModel: IncomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeIncomeRepository()
        viewModel = IncomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_income_validation_rejects_empty_or_non_positive_amount() = runTest {
        viewModel.onPeriodSelected(1L)
        viewModel.onCategorySelected("Employment", "Salary")
        viewModel.onDateTimeChange("2026-05-15 10:00")

        // 1. Empty amount
        viewModel.onAmountChange("")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 2. Zero amount
        viewModel.onAmountChange("0")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 3. Negative amount
        viewModel.onAmountChange("-150.0")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())
    }

    @Test
    fun test_income_validation_rejects_missing_category_and_allows_empty_subcategory() = runTest {
        viewModel.onPeriodSelected(1L)
        viewModel.onAmountChange("1500.0")
        viewModel.onDateTimeChange("2026-05-15 10:00")

        // 1. Without category
        viewModel.onCategorySelected("", "")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Debes seleccionar una categoría", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 2. With category and without subcategory -> succeeds
        viewModel.onCategorySelected("Employment", "")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        val savedIncomes = repository.getIncomesByPeriod(1L).first()
        assertEquals(1, savedIncomes.size)
        assertEquals("Employment", savedIncomes.first().category)
        assertEquals("", savedIncomes.first().subcategory)
    }

    @Test
    fun test_save_valid_income_persists_with_date_time_and_selected_period() = runTest {
        viewModel.onPeriodSelected(10L)
        advanceUntilIdle()

        viewModel.onAmountChange("2323.0")
        viewModel.onDateTimeChange("2026-05-15 14:30")
        viewModel.onCategorySelected("Employment", "Salary")
        viewModel.onDescriptionChange("Monthly salary payment")

        viewModel.saveIncome()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals("Ingreso guardado exitosamente", state.toastMessage)
        assertEquals("", state.amountInput)
        assertEquals("", state.descriptionInput)

        val persisted = repository.getIncomesByPeriod(10L).first()
        assertEquals(1, persisted.size)
        val income = persisted.first()
        assertEquals(2323.0, income.amount, 0.0)
        assertEquals("2026-05-15 14:30", income.dateTime)
        assertEquals("Employment", income.category)
        assertEquals("Salary", income.subcategory)
        assertEquals("Monthly salary payment", income.description)
        assertEquals(10L, income.periodId)

        assertEquals(1, state.incomes.size)
        assertEquals(2323.0, state.totalIncome, 0.0)
    }

    @Test
    fun test_filter_incomes_by_selected_period_updates_list_and_totals() = runTest {
        repository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-04-01 10:00", category = "Employment", subcategory = "Salary", periodId = 1L)
        )
        repository.insertIncome(
            Income(amount = 500.0, dateTime = "2026-04-15 11:00", category = "Employment", subcategory = "Bonus", periodId = 1L)
        )
        repository.insertIncome(
            Income(amount = 2323.0, dateTime = "2026-05-15 12:00", category = "Employment", subcategory = "Salary", periodId = 2L)
        )

        // Select Period 1
        viewModel.onPeriodSelected(1L)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(2, state.incomes.size)
        assertEquals(1500.0, state.totalIncome, 0.0)
        assertTrue(state.incomes.all { it.periodId == 1L })

        // Switch to Period 2
        viewModel.onPeriodSelected(2L)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.incomes.size)
        assertEquals(2323.0, state.totalIncome, 0.0)
        assertEquals(2L, state.incomes.first().periodId)

        // Switch to Period with no incomes (3)
        viewModel.onPeriodSelected(3L)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0, state.incomes.size)
        assertEquals(0.0, state.totalIncome, 0.0)
    }

    @Test
    fun test_delete_income_category_rejected_when_incomes_associated_shows_error() = runTest {
        repository.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList()))
        repository.insertIncome(Income(amount = 2323.0, dateTime = "2026-05-15 10:00", category = "Employment", subcategory = "Salary", periodId = 1L))

        viewModel.deleteCategory("Employment")
        advanceUntilIdle()

        assertEquals("No se puede eliminar la categoría porque tiene ingresos asociados", viewModel.uiState.value.categoryError)
        assertNull(viewModel.uiState.value.errorMessage)
        val categories = repository.getAllCategories().first()
        assertTrue(categories.any { it.name == "Employment" })
    }

    @Test
    fun test_delete_income_category_succeeds_when_no_incomes_associated() = runTest {
        repository.insertCategory(Category(name = "Investments", icon = "📈", color = "#1CC88A", subcategories = emptyList()))

        viewModel.deleteCategory("Investments")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals("Categoría eliminada exitosamente", viewModel.uiState.value.toastMessage)
        val categories = repository.getAllCategories().first()
        assertTrue(categories.none { it.name == "Investments" })
    }

    @Test
    fun test_viewmodel_delete_subcategory_updates_state_and_handles_error() = runTest {
        repository.insertCategory(
            Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salary"), Subcategory("Bonus")))
        )
        advanceUntilIdle()

        // 1. Rejected when incomes are associated
        repository.insertIncome(
            Income(amount = 2323.0, dateTime = "2026-05-15 10:00", category = "Employment", subcategory = "Salary", periodId = 1L)
        )

        viewModel.deleteSubcategory("Employment", "Salary")
        advanceUntilIdle()

        assertEquals("No se puede eliminar la subcategoría porque tiene ingresos asociados", viewModel.uiState.value.subcategoryError)
        var categories = repository.getAllCategories().first()
        var employment = categories.first { it.name == "Employment" }
        assertTrue(employment.subcategories.any { it.name == "Salary" })

        // 2. Succeeds when no incomes are associated
        viewModel.deleteSubcategory("Employment", "Bonus")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.subcategoryError)
        assertEquals("Subcategoría eliminada exitosamente", viewModel.uiState.value.toastMessage)
        categories = repository.getAllCategories().first()
        employment = categories.first { it.name == "Employment" }
        assertTrue(employment.subcategories.none { it.name == "Bonus" })
    }

    @Test
    fun test_viewmodel_create_and_edit_budget_updates_budgeted_total_and_reflects_in_income_state() = runTest {
        val fakeBudgetRepo = FakeBudgetRepository()
        val incomeUseCase = IncomeUseCase(repository, fakeBudgetRepo)
        val vm = IncomeViewModel(incomeUseCase)

        val periodId = 1L
        repository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-05-15 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )
        vm.onPeriodSelected(periodId)
        advanceUntilIdle()

        assertEquals(1000.0, vm.uiState.value.totalIncome, 0.001)
        assertEquals(0.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(1000.0, vm.uiState.value.availableIncome, 0.001)

        // Switch to Budget top tab
        vm.onTopTabSelected(FinanceTopTab.BUDGET)
        assertEquals(FinanceTopTab.BUDGET, vm.uiState.value.selectedTopTab)

        // Try to save budget that exceeds available income (1200 > 1000)
        vm.onBudgetAmountChange("1200")
        vm.onBudgetCategorySelected("Food & Dining", "Supermarket")
        vm.saveBudget()
        advanceUntilIdle()

        assertEquals("El monto supera el ingreso disponible", vm.uiState.value.budgetErrorMessage)
        assertEquals(0, vm.uiState.value.budgets.size)

        // Save valid budget (400)
        vm.onBudgetAmountChange("400")
        vm.saveBudget()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.budgetErrorMessage)
        assertEquals("Presupuesto guardado exitosamente", vm.uiState.value.budgetToastMessage)
        assertEquals(1, vm.uiState.value.budgets.size)
        assertEquals(400.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(600.0, vm.uiState.value.availableIncome, 0.001)

        // Switch back to Incomes tab and verify totals reflect
        vm.onTopTabSelected(FinanceTopTab.INCOMES)
        assertEquals(400.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(600.0, vm.uiState.value.availableIncome, 0.001)

        // Edit budget: change 400 to 700
        val savedBudget = vm.uiState.value.budgets.first()
        vm.startEditingBudget(savedBudget)
        assertNotNull(vm.uiState.value.editingBudget)
        assertEquals(savedBudget.id, vm.uiState.value.editingBudget?.budgetId)

        vm.onEditBudgetAmountChange("700")
        vm.saveEditedBudget()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.editingBudget)
        assertEquals("Presupuesto actualizado exitosamente", vm.uiState.value.budgetToastMessage)
        assertEquals(1, vm.uiState.value.budgets.size)
        assertEquals(700.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(300.0, vm.uiState.value.availableIncome, 0.001)
    }

    @Test
    fun test_budget_category_delete_flow_with_confirmation() = runTest {
        val fakeBudgetRepo = FakeBudgetRepository()
        fakeBudgetRepo.insertCategory(
            Category(name = "Entertainment", icon = "🎬", color = "#F6C23E", subcategories = emptyList())
        )
        val incomeUseCase = IncomeUseCase(
            repository = repository,
            budgetRepository = fakeBudgetRepo
        )
        val vm = IncomeViewModel(incomeUseCase)
        advanceUntilIdle()

        // 1. Initially budgetCategoryToDelete is null
        assertNull(vm.uiState.value.budgetCategoryToDelete)

        // 2. Request delete sets budgetCategoryToDelete
        vm.requestDeleteBudgetCategory("Entertainment")
        assertNotNull(vm.uiState.value.budgetCategoryToDelete)
        assertEquals("Entertainment", vm.uiState.value.budgetCategoryToDelete?.name)

        // 3. Dismiss clears budgetCategoryToDelete without deleting
        vm.dismissDeleteBudgetCategoryConfirmation()
        assertNull(vm.uiState.value.budgetCategoryToDelete)
        assertEquals(1, fakeBudgetRepo.getAllCategories().first().size)

        // 4. Request again and confirm deletion
        vm.requestDeleteBudgetCategory("Entertainment")
        vm.confirmDeleteBudgetCategory()
        advanceUntilIdle()

        assertNull(vm.uiState.value.budgetCategoryToDelete)
        assertEquals(0, fakeBudgetRepo.getAllCategories().first().size)
        assertEquals("Categoría eliminada exitosamente", vm.uiState.value.budgetToastMessage)
    }

    @Test
    fun test_budget_delete_flow_with_confirmation_updates_totals_and_clears_state() = runTest {
        val fakeBudgetRepo = FakeBudgetRepository()
        val incomeUseCase = IncomeUseCase(
            repository = repository,
            budgetRepository = fakeBudgetRepo
        )
        val vm = IncomeViewModel(incomeUseCase)
        vm.onPeriodSelected(1L)

        repository.insertIncome(
            Income(
                amount = 1000.0,
                dateTime = "2026-05-01 09:00",
                category = "Salary",
                subcategory = "Main",
                description = "",
                periodId = 1L
            )
        )
        advanceUntilIdle()

        vm.onBudgetAmountChange("400")
        vm.onBudgetCategorySelected("Food", "Supermarket")
        vm.saveBudget()
        advanceUntilIdle()

        val savedBudget = vm.uiState.value.budgets.first()
        assertEquals(400.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(600.0, vm.uiState.value.availableIncome, 0.001)
        assertNull(vm.uiState.value.budgetToDelete)

        // 1. Request delete sets budgetToDelete
        vm.requestDeleteBudget(savedBudget)
        assertEquals(savedBudget, vm.uiState.value.budgetToDelete)

        // 2. Dismiss clears budgetToDelete without deleting
        vm.dismissDeleteBudgetConfirmation()
        assertNull(vm.uiState.value.budgetToDelete)
        assertEquals(1, vm.uiState.value.budgets.size)

        // 3. Request again and confirm deletion
        vm.requestDeleteBudget(savedBudget)
        vm.confirmDeleteBudget()
        advanceUntilIdle()

        assertNull(vm.uiState.value.budgetToDelete)
        assertTrue(vm.uiState.value.budgets.isEmpty())
        assertEquals(0.0, vm.uiState.value.totalBudgeted, 0.001)
        assertEquals(1000.0, vm.uiState.value.availableIncome, 0.001)
        assertEquals(IncomeViewModel.MSG_BUDGET_DELETED, vm.uiState.value.budgetToastMessage)
    }

    @Test
    fun test_income_viewmodel_collects_expenses_and_exposes_in_ui_state() = runTest {
        val fakeExpenseRepo = FakeExpenseRepository()
        val expense = Expense(1L, 120.0, "2026-05-10", "Groceries", "Food & Dining", "Supermarket")
        fakeExpenseRepo.insertExpense(expense)
        val incomeUseCase = IncomeUseCase(repository = repository, expenseRepository = fakeExpenseRepo)
        val vm = IncomeViewModel(incomeUseCase)
        advanceUntilIdle()
        assertEquals(listOf(expense), vm.uiState.value.expenses)
    }

    @Test
    fun test_income_category_edit_and_delete_confirmation_reconciles_state() = runTest {
        val cat = Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList())
        repository.insertCategory(cat)
        advanceUntilIdle()

        viewModel.onCategorySelected("Employment", "")
        viewModel.startEditing(
            Income(id = 1L, amount = 500.0, dateTime = "2026-05-01 09:00", category = "Employment", periodId = 1L)
        )

        // Edit income category -> empty name shows error inside editingCategory
        viewModel.startEditingCategory(cat)
        viewModel.onEditCategoryNameChange("  ")
        viewModel.confirmEditCategory()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.editingCategory?.errorMessage)

        // Valid rename + color change reconciles form and editingIncome
        viewModel.onEditCategoryNameChange("Trabajo")
        viewModel.onEditCategoryColorChange("#1CC88A")
        viewModel.confirmEditCategory()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingCategory)
        assertEquals("Trabajo", viewModel.uiState.value.categoryInput)
        assertEquals("Trabajo", viewModel.uiState.value.editingIncome?.category)
        assertEquals(IncomeViewModel.MSG_CATEGORY_UPDATED, viewModel.uiState.value.toastMessage)

        // Delete confirmation flow
        viewModel.requestDeleteCategory("Trabajo")
        assertEquals("Trabajo", viewModel.uiState.value.categoryToDelete?.name)
        viewModel.dismissDeleteCategoryConfirmation()
        assertNull(viewModel.uiState.value.categoryToDelete)

        viewModel.requestDeleteCategory("Trabajo")
        viewModel.confirmDeleteCategory()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.categoryToDelete)
        assertTrue(repository.getAllCategories().first().none { it.name == "Trabajo" })
    }

    @Test
    fun test_budget_category_edit_reconciles_form_and_editing_budget() = runTest {
        val fakeBudgetRepo = FakeBudgetRepository()
        val cat = Category(name = "Food & Dining", icon = "🍔", color = "#4E73DF", subcategories = emptyList())
        fakeBudgetRepo.insertCategory(cat)
        val vm = IncomeViewModel(IncomeUseCase(repository = repository, budgetRepository = fakeBudgetRepo))
        advanceUntilIdle()

        vm.onBudgetCategorySelected("Food & Dining", "")
        vm.startEditingBudget(Budget(id = 1L, amount = 250.0, category = "Food & Dining", periodId = 1L))

        vm.startEditingBudgetCategory(cat)
        vm.onEditBudgetCategoryNameChange("Comida")
        vm.onEditBudgetCategoryColorChange("#F6C23E")
        vm.confirmEditBudgetCategory()
        advanceUntilIdle()

        assertNull(vm.uiState.value.editingBudgetCategory)
        assertEquals("Comida", vm.uiState.value.budgetCategoryInput)
        assertEquals("Comida", vm.uiState.value.editingBudget?.category)
        assertEquals(IncomeViewModel.MSG_CATEGORY_UPDATED, vm.uiState.value.budgetToastMessage)
    }
}
