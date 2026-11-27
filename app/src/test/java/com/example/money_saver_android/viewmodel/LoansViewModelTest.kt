package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.FakeLoanRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.LoanUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoansViewModelTest {

    private class StubSavingsRepository : SavingsRepository {
        val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
        val categoriesFlow = MutableStateFlow(listOf(Category("Viajes", "✈️", "#10B981", emptyList())))
        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow
        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> = savingsFlow
        override suspend fun getSavingById(id: Long): Saving? = null
        override suspend fun insertSaving(saving: Saving): Long = 0L
        override suspend fun updateSaving(saving: Saving) {}
        override suspend fun deleteSaving(id: Long) {}
        override suspend fun countSavingsForCategory(categoryName: String): Int = 0
        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class StubExpenseRepository : ExpenseRepository {
        override fun getAllExpenses(): Flow<List<Expense>> = MutableStateFlow(emptyList())
        override suspend fun insertExpense(expense: Expense) {}
        override suspend fun updateExpense(expense: Expense) {}
        override suspend fun deleteExpense(id: Long) {}
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var loanRepo: FakeLoanRepository
    private lateinit var savingsRepo: StubSavingsRepository
    private lateinit var viewModel: LoansViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        loanRepo = FakeLoanRepository()
        savingsRepo = StubSavingsRepository()
        viewModel = LoansViewModel(LoanUseCase(loanRepo, savingsRepo, StubExpenseRepository()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun amountLentChange_prefillsAmountToReceiveUntilManuallyEdited() = runTest(testDispatcher) {
        viewModel.onAmountLentChange("500")
        assertEquals("500", viewModel.uiState.value.amountToReceiveInput)

        viewModel.onAmountToReceiveChange("600")
        viewModel.onAmountLentChange("450")
        assertEquals("600", viewModel.uiState.value.amountToReceiveInput)
    }

    @Test
    fun saveLoan_validWithSavings_updatesStateAndDeductsAvailableBalance() = runTest(testDispatcher) {
        savingsRepo.savingsFlow.value = listOf(Saving(id = 1L, amount = 1000.0, category = "Viajes", periodId = 1L))
        advanceUntilIdle()
        assertEquals(1000.0, viewModel.uiState.value.savingsBalances.single().netBalance, 0.001)

        viewModel.onPersonNameChange("Luis Gómez")
        viewModel.onAmountLentChange("400")
        viewModel.onSavingCategorySelected("Viajes")
        viewModel.saveLoan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.loans.size)
        assertEquals(400.0, state.totalReceivable, 0.001)
        assertEquals(600.0, state.savingsBalances.single().netBalance, 0.001)
        assertEquals("", state.personNameInput)
        assertEquals("", state.amountLentInput)
        assertEquals("", state.amountToReceiveInput)
        assertEquals("", state.selectedSavingCategory)
        assertEquals(LoansViewModel.MSG_LOAN_SAVED, state.toastMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun saveLoan_invalidAmountToReceive_setsErrorAndPreservesInputs() = runTest(testDispatcher) {
        viewModel.onPersonNameChange("Ana")
        viewModel.onAmountLentChange("300")
        viewModel.onAmountToReceiveChange("200")
        viewModel.saveLoan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loans.isEmpty())
        assertNotNull(state.errorMessage)
        assertEquals("Ana", state.personNameInput)
        assertEquals("300", state.amountLentInput)
        assertEquals("200", state.amountToReceiveInput)
    }
}
