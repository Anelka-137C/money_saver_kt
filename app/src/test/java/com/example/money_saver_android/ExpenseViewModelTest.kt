package com.example.money_saver_android

import com.example.money_saver_android.data.Expense
import com.example.money_saver_android.data.ExpenseRepository
import com.example.money_saver_android.ui.ExpenseViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeExpenseRepository
    private lateinit var viewModel: ExpenseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeExpenseRepository()
        viewModel = ExpenseViewModel(repository)
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
}
