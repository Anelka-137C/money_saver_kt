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
import org.junit.Before
import org.junit.Test

class FakeExpenseRepository : ExpenseRepository {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override fun getAllExpenses(): Flow<List<Expense>> = _expenses

    override suspend fun insertExpense(expense: Expense) {
        _expenses.value = _expenses.value + expense
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
}
