package com.example.money_saver_android.domain

import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeRepositoryForUseCase : ExpenseRepository {
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

class ExpenseUseCaseTest {

    private lateinit var repository: FakeRepositoryForUseCase
    private lateinit var useCase: ExpenseUseCase

    @Before
    fun setup() {
        repository = FakeRepositoryForUseCase()
        useCase = ExpenseUseCase(repository)
    }

    @Test
    fun saveExpense_withValidData_returnsSuccessAndInserts() = runBlocking {
        val result = useCase.saveExpense(
            amountInput = "100.0",
            dateInput = "2026-11-06",
            descriptionInput = "Almuerzo",
            categoryInput = "Alimentación",
            subcategoryInput = "Restaurante"
        )

        assertTrue(result is ExpenseResult.Success)
        val list = useCase.getExpenses().first()
        assertEquals(1, list.size)
        assertEquals(100.0, list.first().amount, 0.0)
    }

    @Test
    fun saveExpense_withInvalidAmount_returnsError() = runBlocking {
        val result = useCase.saveExpense(
            amountInput = "0",
            dateInput = "2026-11-06",
            descriptionInput = "Almuerzo",
            categoryInput = "Alimentación",
            subcategoryInput = "Restaurante"
        )

        assertTrue(result is ExpenseResult.Error)
        assertEquals("El monto debe ser mayor a cero", (result as ExpenseResult.Error).message)
        val list = useCase.getExpenses().first()
        assertEquals(0, list.size)
    }

    @Test
    fun saveExpense_withoutCategory_returnsError() = runBlocking {
        val result = useCase.saveExpense(
            amountInput = "50.0",
            dateInput = "2026-11-06",
            descriptionInput = "Snack",
            categoryInput = "",
            subcategoryInput = ""
        )

        assertTrue(result is ExpenseResult.Error)
        assertEquals("Debes seleccionar una categoría", (result as ExpenseResult.Error).message)
        val list = useCase.getExpenses().first()
        assertEquals(0, list.size)
    }

    @Test
    fun updateExpense_withValidData_returnsSuccessAndUpdates() = runBlocking {
        val initial = Expense(id = 5L, amount = 40.0, date = "2026-11-01", description = "Uber", category = "Transporte", subcategory = "Taxi")
        repository.insertExpense(initial)

        val result = useCase.updateExpense(
            id = 5L,
            amountInput = "55.0",
            dateInput = "2026-11-02",
            descriptionInput = "Uber XL",
            categoryInput = "Transporte",
            subcategoryInput = "Taxi"
        )

        assertTrue(result is ExpenseResult.Success)
        val list = useCase.getExpenses().first()
        assertEquals(1, list.size)
        assertEquals(55.0, list.first().amount, 0.0)
        assertEquals("Uber XL", list.first().description)
    }

    @Test
    fun updateExpense_withInvalidAmount_returnsError() = runBlocking {
        val initial = Expense(id = 5L, amount = 40.0, date = "2026-11-01", description = "Uber", category = "Transporte", subcategory = "Taxi")
        repository.insertExpense(initial)

        val result = useCase.updateExpense(
            id = 5L,
            amountInput = "-10",
            dateInput = "2026-11-02",
            descriptionInput = "Uber XL",
            categoryInput = "Transporte",
            subcategoryInput = "Taxi"
        )

        assertTrue(result is ExpenseResult.Error)
        assertEquals("El monto debe ser mayor a cero", (result as ExpenseResult.Error).message)
    }

    @Test
    fun updateExpense_withoutCategory_returnsError() = runBlocking {
        val initial = Expense(id = 5L, amount = 40.0, date = "2026-11-01", description = "Uber", category = "Transporte", subcategory = "Taxi")
        repository.insertExpense(initial)

        val result = useCase.updateExpense(
            id = 5L,
            amountInput = "55.0",
            dateInput = "2026-11-02",
            descriptionInput = "Uber XL",
            categoryInput = "",
            subcategoryInput = ""
        )

        assertTrue(result is ExpenseResult.Error)
        assertEquals("Debes seleccionar una categoría", (result as ExpenseResult.Error).message)
    }

    @Test
    fun deleteExpense_callsRepositoryAndReturnsSuccess() = runBlocking {
        val initial = Expense(id = 7L, amount = 20.0, date = "2026-11-06", description = "Snack", category = "Alimentación", subcategory = "Snack")
        repository.insertExpense(initial)

        val result = useCase.deleteExpense(7L)

        assertTrue(result is ExpenseResult.Success)
        val list = useCase.getExpenses().first()
        assertEquals(0, list.size)
    }
}
