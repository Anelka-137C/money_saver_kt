package com.example.money_saver_android

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeExpenseRepository : ExpenseRepository {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override fun getAllExpenses(): Flow<List<Expense>> = _expenses

    override suspend fun insertExpense(expense: Expense) {
        _expenses.value = _expenses.value + expense
    }

    override suspend fun updateExpense(expense: Expense) {
        _expenses.value = _expenses.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun deleteExpense(id: Long) {
        _expenses.value = _expenses.value.filter { it.id != id }
    }
}

class FakeCategoryRepoForVm : CategoryRepository {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = listOf(
                Category(
                    name = "Alimentación",
                    icon = "🍔",
                    color = "#1CC88A",
                    subcategories = listOf(Subcategory("Restaurante"))
                )
            )
        }
    }

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name.equals(categoryName, ignoreCase = true)) {
                cat.copy(subcategories = cat.subcategories + subcategory)
            } else {
                cat
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeExpenseRepository
    private lateinit var categoryRepository: FakeCategoryRepoForVm
    private lateinit var viewModel: ExpenseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeExpenseRepository()
        categoryRepository = FakeCategoryRepoForVm()
        val expenseUseCase = ExpenseUseCase(repository)
        val categoryUseCase = CategoryUseCase(categoryRepository)
        viewModel = ExpenseViewModel(expenseUseCase, categoryUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveExpense_withValidData_persistsAndUpdatesList() = runTest(testDispatcher) {
        viewModel.onAmountChange("150.0")
        viewModel.onDateChange("2026-11-06")
        viewModel.onDescriptionChange("Supermercado")
        viewModel.onCategoryChange("Alimentación", "Supermercado")

        viewModel.saveExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.expenses.size)
        val saved = state.expenses.first()
        assertEquals(150.0, saved.amount, 0.0)
        assertEquals("2026-11-06", saved.date)
        assertEquals("Supermercado", saved.description)
        assertEquals("Alimentación", saved.category)
        assertEquals("Supermercado", saved.subcategory)
    }

    @Test
    fun saveExpense_withoutSubcategory_persistsAndUpdatesList() = runTest(testDispatcher) {
        viewModel.onAmountChange("95.0")
        viewModel.onDateChange("2026-11-06")
        viewModel.onDescriptionChange("Compra general")
        viewModel.onCategoryChange("Alimentación", "")

        viewModel.saveExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.expenses.size)
        val saved = state.expenses.first()
        assertEquals(95.0, saved.amount, 0.0)
        assertEquals("Alimentación", saved.category)
        assertEquals("", saved.subcategory)
    }

    @Test
    fun saveExpense_withValidData_emitsToastMessageAndClearsForm() = runTest(testDispatcher) {
        viewModel.onAmountChange("150.0")
        viewModel.onDateChange("2026-11-06")
        viewModel.onDescriptionChange("Supermercado")
        viewModel.onCategoryChange("Alimentación", "Supermercado")

        viewModel.saveExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Gasto guardado", state.toastMessage)
        assertEquals("", state.amountInput)
        assertEquals("", state.descriptionInput)

        viewModel.clearToastMessage()
        assertEquals(null, viewModel.uiState.value.toastMessage)
    }

    @Test
    fun saveExpense_withZeroOrEmptyAmount_showsValidationErrorAndDoesNotPersist() = runTest(testDispatcher) {
        viewModel.onAmountChange("0")
        viewModel.onDateChange("2026-11-06")
        viewModel.onDescriptionChange("Café")
        viewModel.onCategoryChange("Alimentación", "Café")

        viewModel.saveExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.expenses.size)
        assertEquals("El monto debe ser mayor a cero", state.errorMessage)
    }

    @Test
    fun saveExpense_withoutCategory_showsValidationErrorAndDoesNotPersist() = runTest(testDispatcher) {
        viewModel.onAmountChange("100.0")
        viewModel.onDateChange("2026-11-06")
        viewModel.onCategoryChange("", "")

        viewModel.saveExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.expenses.size)
        assertEquals("Debes seleccionar una categoría", state.errorMessage)
    }

    @Test
    fun startEditing_populatesEditStateWithExpenseDataAndOpensModal() = runTest(testDispatcher) {
        val expense = Expense(
            id = 42L,
            amount = 99.50,
            date = "2026-11-05",
            description = "Libro",
            category = "Educación",
            subcategory = "Libros"
        )

        viewModel.startEditing(expense)

        val editState = viewModel.uiState.value.editingExpense
        assertNotNull(editState)
        assertEquals(42L, editState?.expenseId)
        assertEquals("99.5", editState?.amount)
        assertEquals("2026-11-05", editState?.date)
        assertEquals("Libro", editState?.description)
        assertEquals("Educación", editState?.category)
        assertEquals("Libros", editState?.subcategory)
    }

    @Test
    fun confirmEdit_withValidData_updatesExpenseInRepositoryAndClosesModal() = runTest(testDispatcher) {
        val initial = Expense(
            id = 10L,
            amount = 50.0,
            date = "2026-11-01",
            description = "Taxi",
            category = "Transporte",
            subcategory = "Taxi"
        )
        repository.insertExpense(initial)
        advanceUntilIdle()

        viewModel.startEditing(initial)
        viewModel.onEditAmountChange("75.0")
        viewModel.onEditDescriptionChange("Taxi al aeropuerto")
        viewModel.onEditCategoryChange("Transporte", "Aeropuerto")
        viewModel.onEditDateChange("2026-11-02")

        viewModel.confirmEdit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.editingExpense)
        assertEquals(1, state.expenses.size)
        val updated = state.expenses.first()
        assertEquals(10L, updated.id)
        assertEquals(75.0, updated.amount, 0.0)
        assertEquals("Taxi al aeropuerto", updated.description)
        assertEquals("Transporte", updated.category)
        assertEquals("Aeropuerto", updated.subcategory)
        assertEquals("2026-11-02", updated.date)
    }

    @Test
    fun confirmEdit_withZeroOrEmptyAmount_showsValidationErrorAndDoesNotUpdate() = runTest(testDispatcher) {
        val initial = Expense(
            id = 20L,
            amount = 80.0,
            date = "2026-11-03",
            description = "Farmacia",
            category = "Salud",
            subcategory = "Medicamentos"
        )
        repository.insertExpense(initial)
        advanceUntilIdle()

        viewModel.startEditing(initial)
        viewModel.onEditAmountChange("0")

        viewModel.confirmEdit()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertNotNull(state.editingExpense)
        assertEquals("El monto debe ser mayor a cero", state.editingExpense?.errorMessage)

        viewModel.onEditAmountChange("")
        viewModel.confirmEdit()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNotNull(state.editingExpense)
        assertEquals("El monto debe ser mayor a cero", state.editingExpense?.errorMessage)

        val currentExpense = state.expenses.first { it.id == 20L }
        assertEquals(80.0, currentExpense.amount, 0.0)
    }

    @Test
    fun confirmEdit_withoutCategory_showsValidationErrorAndDoesNotUpdate() = runTest(testDispatcher) {
        val initial = Expense(
            id = 25L,
            amount = 60.0,
            date = "2026-11-03",
            description = "Gym",
            category = "Salud",
            subcategory = "Gimnasio"
        )
        repository.insertExpense(initial)
        advanceUntilIdle()

        viewModel.startEditing(initial)
        viewModel.onEditCategoryChange("", "")

        viewModel.confirmEdit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.editingExpense)
        assertEquals("Debes seleccionar una categoría", state.editingExpense?.errorMessage)

        val currentExpense = state.expenses.first { it.id == 25L }
        assertEquals("Salud", currentExpense.category)
    }

    @Test
    fun cancelEditing_dismissesModalWithoutChanges() = runTest(testDispatcher) {
        val initial = Expense(
            id = 30L,
            amount = 35.0,
            date = "2026-11-04",
            description = "Café",
            category = "Alimentación",
            subcategory = "Café"
        )
        repository.insertExpense(initial)
        advanceUntilIdle()

        viewModel.startEditing(initial)
        viewModel.onEditAmountChange("999.0")
        viewModel.cancelEditing()

        val state = viewModel.uiState.value
        assertNull(state.editingExpense)
        val expenseInList = state.expenses.first { it.id == 30L }
        assertEquals(35.0, expenseInList.amount, 0.0)
    }

    @Test
    fun requestDeleteExpense_setsExpenseToDeleteInUiState() = runTest(testDispatcher) {
        val expense = Expense(
            id = 50L,
            amount = 15.0,
            date = "2026-11-06",
            description = "Helado",
            category = "Alimentación",
            subcategory = "Postre"
        )

        viewModel.requestDeleteExpense(expense)

        val state = viewModel.uiState.value
        assertEquals(expense, state.expenseToDelete)
    }

    @Test
    fun dismissDeleteConfirmation_clearsExpenseToDeleteWithoutDeleting() = runTest(testDispatcher) {
        val expense = Expense(
            id = 55L,
            amount = 12.0,
            date = "2026-11-06",
            description = "Jugo",
            category = "Alimentación",
            subcategory = "Bebidas"
        )
        repository.insertExpense(expense)
        advanceUntilIdle()

        viewModel.requestDeleteExpense(expense)
        viewModel.dismissDeleteConfirmation()

        val state = viewModel.uiState.value
        assertNull(state.expenseToDelete)
        assertEquals(1, state.expenses.size)
        assertEquals(55L, state.expenses.first().id)
    }

    @Test
    fun confirmDeleteExpense_deletesFromRepositoryClearsDialogAndEmitsToast() = runTest(testDispatcher) {
        val expense = Expense(
            id = 60L,
            amount = 45.0,
            date = "2026-11-06",
            description = "Cena",
            category = "Alimentación",
            subcategory = "Restaurante"
        )
        repository.insertExpense(expense)
        advanceUntilIdle()

        viewModel.requestDeleteExpense(expense)
        viewModel.confirmDeleteExpense()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.expenseToDelete)
        assertEquals(0, state.expenses.size)
        assertEquals("Gasto eliminado", state.toastMessage)
    }

    @Test
    fun createCategory_withValidData_clearsInputAndRefreshesCategoryList() = runTest(testDispatcher) {
        viewModel.onNewCategoryNameChange("Transporte")
        viewModel.onNewCategoryColorChange("#E74A3B")

        viewModel.createCategory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.newCategoryNameInput)
        assertNull(state.categoryErrorMessage)
        assertEquals("Categoría creada", state.toastMessage)
        val created = state.categories.find { it.name == "Transporte" }
        assertNotNull(created)
        assertEquals("#E74A3B", created?.color)
    }

    @Test
    fun createCategory_withInvalidData_setsCategoryErrorMessage() = runTest(testDispatcher) {
        viewModel.onNewCategoryNameChange("   ")

        viewModel.createCategory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("El nombre de la categoría no puede estar vacío", state.categoryErrorMessage)
    }

    @Test
    fun createSubcategory_withValidData_clearsInputAndRefreshesCategoryList() = runTest(testDispatcher) {
        viewModel.onNewCategoryNameChange("Alimentación")
        viewModel.onNewCategoryColorChange("#1CC88A")
        viewModel.createCategory()
        advanceUntilIdle()

        viewModel.onNewSubcategoryNameChange("Supermercado")
        viewModel.createSubcategory("Alimentación")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.newSubcategoryNameInput)
        assertNull(state.subcategoryErrorMessage)
        assertEquals("Subcategoría creada", state.toastMessage)
        val alimentacion = state.categories.find { it.name == "Alimentación" }
        assertNotNull(alimentacion)
        assertTrue(alimentacion!!.subcategories.any { it.name == "Supermercado" })
    }

    @Test
    fun createSubcategory_withInvalidData_setsSubcategoryErrorMessage() = runTest(testDispatcher) {
        viewModel.onNewCategoryNameChange("Alimentación")
        viewModel.createCategory()
        advanceUntilIdle()

        viewModel.onNewSubcategoryNameChange("  ")
        viewModel.createSubcategory("Alimentación")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("El nombre de la subcategoría no puede estar vacío", state.subcategoryErrorMessage)
    }

    @Test
    fun onPeriodSelected_loadsBudgetsAndExpensesAndCancelsOldCollectors() = runTest(testDispatcher) {
        val budgetRepo = FakeBudgetRepository()
        val scopedVm = ExpenseViewModel(ExpenseUseCase(repository, budgetRepo), CategoryUseCase(categoryRepository))
        val b1Id = budgetRepo.insertBudget(Budget(amount = 300.0, category = "Fijos", subcategory = "Renta", periodId = 1L))
        val b2Id = budgetRepo.insertBudget(Budget(amount = 200.0, category = "Ocio", periodId = 2L))
        repository.insertExpense(Expense(id = 1L, amount = 80.0, date = "2026-11-01", description = "Luz", category = "Servicios", periodId = 1L, budgetId = b1Id, budgetCategory = "Fijos"))
        repository.insertExpense(Expense(id = 2L, amount = 50.0, date = "2026-12-01", description = "Cine", category = "Ocio", periodId = 2L, budgetId = b2Id, budgetCategory = "Ocio"))

        scopedVm.onPeriodSelected(1L)
        advanceUntilIdle()
        assertTrue(scopedVm.uiState.value.hasActiveBudget)
        assertEquals(300.0, scopedVm.uiState.value.totalBudgeted, 0.01)
        assertEquals(80.0, scopedVm.uiState.value.totalSpent, 0.01)

        scopedVm.onPeriodSelected(2L)
        advanceUntilIdle()
        assertEquals(200.0, scopedVm.uiState.value.totalBudgeted, 0.01)
        assertEquals(50.0, scopedVm.uiState.value.totalSpent, 0.01)

        // Old period 1 collector must stay cancelled when period 1 mutates
        budgetRepo.insertBudget(Budget(amount = 999.0, category = "Extra", periodId = 1L))
        repository.insertExpense(Expense(id = 3L, amount = 99.0, date = "2026-11-02", description = "Gas", category = "Servicios", periodId = 1L, budgetId = b1Id, budgetCategory = "Fijos"))
        advanceUntilIdle()
        assertEquals(200.0, scopedVm.uiState.value.totalBudgeted, 0.01)
        assertEquals(50.0, scopedVm.uiState.value.totalSpent, 0.01)

        scopedVm.onPeriodSelected(3L)
        advanceUntilIdle()
        assertFalse(scopedVm.uiState.value.hasActiveBudget)
        assertEquals(0.0, scopedVm.uiState.value.totalBudgeted, 0.01)
        assertEquals(0.0, scopedVm.uiState.value.totalSpent, 0.01)
    }

    @Test
    fun saveExpense_withBudgetRepo_requiresBudgetAndPersistsBothSelectors() = runTest(testDispatcher) {
        val budgetRepo = FakeBudgetRepository()
        val scopedVm = ExpenseViewModel(ExpenseUseCase(repository, budgetRepo), CategoryUseCase(categoryRepository))
        val bId = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Gastos Fijos", subcategory = "Comida", periodId = 1L))

        scopedVm.onPeriodSelected(2L)
        advanceUntilIdle()
        assertFalse(scopedVm.uiState.value.hasActiveBudget)
        scopedVm.onAmountChange("50.0")
        scopedVm.onDateChange("2026-11-06")
        scopedVm.onCategoryChange("Alimentación", "Restaurante")
        scopedVm.saveExpense()
        advanceUntilIdle()
        assertEquals("Se requiere definir un presupuesto activo en el período", scopedVm.uiState.value.errorMessage)

        scopedVm.onPeriodSelected(1L)
        advanceUntilIdle()
        val budget = scopedVm.uiState.value.budgets.first()
        scopedVm.onBudgetSelected(budget)
        scopedVm.onAmountChange("120.0")
        scopedVm.onDateChange("2026-11-06")
        scopedVm.onDescriptionChange("Cena")
        scopedVm.onCategoryChange("Alimentación", "Restaurante")
        scopedVm.saveExpense()
        advanceUntilIdle()

        val saved = scopedVm.uiState.value.expenses.single()
        assertEquals(1L, saved.periodId)
        assertEquals(bId, saved.budgetId)
        assertEquals("Gastos Fijos", saved.budgetCategory)
        assertEquals("Alimentación", saved.category)
        assertEquals("Restaurante", saved.subcategory)
        assertNull(scopedVm.uiState.value.budgetIdInput)
        assertEquals("", scopedVm.uiState.value.budgetCategoryInput)
    }

    @Test
    fun calculateCategoryExpenses_consolidatesSubcategoriesByCategory() = runTest(testDispatcher) {
        repository.insertExpense(Expense(id = 1L, amount = 60.0, date = "2026-11-01", description = "Almuerzo", category = "Alimentación", subcategory = "Restaurante"))
        repository.insertExpense(Expense(id = 2L, amount = 40.0, date = "2026-11-02", description = "Mercado", category = "Alimentación", subcategory = "Supermercado"))
        repository.insertExpense(Expense(id = 3L, amount = 100.0, date = "2026-11-03", description = "Bus", category = "Transporte", subcategory = ""))
        advanceUntilIdle()

        val grouped = viewModel.calculateCategoryExpenses()
        assertEquals(2, grouped.size)
        val food = grouped.first { it.categoryName == "Alimentación" }
        val transport = grouped.first { it.categoryName == "Transporte" }
        assertEquals(100.0, food.spentAmount, 0.01)
        assertEquals(50, food.percent)
        assertEquals(100.0, transport.spentAmount, 0.01)
        assertEquals(50, transport.percent)
    }
}
