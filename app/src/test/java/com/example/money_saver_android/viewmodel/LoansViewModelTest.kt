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
        viewModel = LoansViewModel(
            LoanUseCase(
                loanRepository = loanRepo,
                savingsRepository = savingsRepo,
                expenseRepository = StubExpenseRepository(),
                currentDateProvider = { "2026-11-26" }
            )
        )
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

    @Test
    fun draftInstallments_addRemoveValidateAndSaveWithLoan() = runTest(testDispatcher) {
        viewModel.onPersonNameChange("María")
        viewModel.onAmountLentChange("500")
        viewModel.onAmountToReceiveChange("600")

        // Reject past date when adding draft
        viewModel.onDraftInstallmentDateChange("2026-11-25")
        viewModel.onDraftInstallmentAmountChange("300")
        viewModel.addDraftInstallment()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.draftInstallments.isEmpty())

        // Add two valid installments summing 600
        viewModel.onDraftInstallmentDateChange("2026-12-10")
        viewModel.onDraftInstallmentAmountChange("350")
        viewModel.addDraftInstallment()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.draftInstallments.size)

        viewModel.onDraftInstallmentDateChange("2026-12-01")
        viewModel.onDraftInstallmentAmountChange("250")
        viewModel.addDraftInstallment()
        assertEquals(2, viewModel.uiState.value.draftInstallments.size)
        assertEquals("2026-12-01", viewModel.uiState.value.draftInstallments[0].dueDate)

        viewModel.saveLoan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.loans.size)
        assertEquals(2, state.loans.single().installments.size)
        assertTrue(state.draftInstallments.isEmpty())
        assertEquals(LoansViewModel.MSG_LOAN_SAVED, state.toastMessage)
    }

    @Test
    fun existingLoanSchedule_openEditValidateAndSave() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            com.example.money_saver_android.domain.model.Loan(
                personName = "Pedro",
                amountLent = 400.0,
                amountToReceive = 450.0
            )
        )
        advanceUntilIdle()
        val loan = viewModel.uiState.value.loans.single { it.id == loanId }

        viewModel.openScheduleDialog(loan)
        assertNotNull(viewModel.uiState.value.editingScheduleLoan)

        // Add installment with partial sum 200 -> saving must fail with error
        viewModel.onScheduleDateChange("2026-12-05")
        viewModel.onScheduleAmountChange("200")
        viewModel.addScheduleInstallment()
        viewModel.saveLoanSchedule()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.scheduleErrorMessage)
        assertNotNull(viewModel.uiState.value.editingScheduleLoan)
        assertTrue(loanRepo.loansFlow.value.single().installments.isEmpty())

        // Complete sum to 450 -> saves and closes dialog
        viewModel.onScheduleDateChange("2026-12-20")
        viewModel.onScheduleAmountChange("250")
        viewModel.addScheduleInstallment()
        viewModel.saveLoanSchedule()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingScheduleLoan)
        assertNull(viewModel.uiState.value.scheduleErrorMessage)
        assertEquals(LoansViewModel.MSG_SCHEDULE_UPDATED, viewModel.uiState.value.toastMessage)
        assertEquals(2, viewModel.uiState.value.loans.single().installments.size)

        // Reopen, remove all installments, and save empty schedule
        viewModel.openScheduleDialog(viewModel.uiState.value.loans.single())
        val ids = viewModel.uiState.value.scheduleInstallments.map { it.draftId }
        ids.forEach { viewModel.removeScheduleInstallment(it) }
        viewModel.saveLoanSchedule()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingScheduleLoan)
        assertTrue(viewModel.uiState.value.loans.single().installments.isEmpty())
    }

    @Test
    fun editLoan_andDeleteLoan_updateStateAndEmitMessages() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            com.example.money_saver_android.domain.model.Loan(
                personName = "Pedro",
                amountLent = 400.0,
                amountToReceive = 450.0,
                note = "Inicial"
            )
        )
        advanceUntilIdle()

        val initialLoan = viewModel.uiState.value.loans.single { it.id == loanId }
        viewModel.startEditingLoan(initialLoan)
        assertEquals(loanId, viewModel.uiState.value.editingLoanId)
        assertEquals("Pedro", viewModel.uiState.value.personNameInput)

        viewModel.onPersonNameChange("Pedro Gómez")
        viewModel.onAmountToReceiveChange("500")
        viewModel.saveLoan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingLoanId)
        assertEquals(LoansViewModel.MSG_LOAN_UPDATED, viewModel.uiState.value.toastMessage)
        assertEquals("Pedro Gómez", viewModel.uiState.value.loans.single().personName)
        assertEquals(500.0, viewModel.uiState.value.loans.single().amountToReceive, 0.001)

        // Request delete and cancel
        viewModel.requestDeleteLoan(viewModel.uiState.value.loans.single())
        assertNotNull(viewModel.uiState.value.loanToDelete)
        viewModel.dismissDeleteLoan()
        assertNull(viewModel.uiState.value.loanToDelete)
        assertEquals(1, viewModel.uiState.value.loans.size)

        // Request delete and confirm
        viewModel.requestDeleteLoan(viewModel.uiState.value.loans.single())
        viewModel.confirmDeleteLoan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.loanToDelete)
        assertTrue(viewModel.uiState.value.loans.isEmpty())
        assertEquals(LoansViewModel.MSG_LOAN_DELETED, viewModel.uiState.value.toastMessage)
    }
}
