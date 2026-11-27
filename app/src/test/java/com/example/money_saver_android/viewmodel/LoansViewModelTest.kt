package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.FakeLoanRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
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
                currentDateProvider = { "2026-11-26" },
                currentDateTimeProvider = { "2026-11-26 15:45" }
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

    @Test
    fun recordPayment_updatesSummaryTotalsSavingsBalanceAndClearsDialogOnSuccess() = runTest(testDispatcher) {
        savingsRepo.savingsFlow.value = listOf(Saving(id = 1L, amount = 1000.0, category = "Viajes", periodId = 1L))
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Elena",
                amountLent = 400.0,
                amountToReceive = 450.0,
                savingCategory = "Viajes"
            )
        )
        advanceUntilIdle()
        assertEquals(600.0, viewModel.uiState.value.savingsBalances.single().netBalance, 0.001)

        val loan = viewModel.uiState.value.loans.single { it.id == loanId }
        viewModel.openPaymentDialog(loan)
        assertEquals("2026-11-26 15:45", viewModel.uiState.value.paymentDateTimeInput)
        assertEquals("Viajes", viewModel.uiState.value.paymentSavingCategoryInput)

        // Invalid payment amount (> pending) shows error and preserves dialog
        viewModel.onPaymentAmountChange("500")
        viewModel.saveLoanPayment()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.paymentErrorMessage)
        assertNotNull(viewModel.uiState.value.payingLoan)

        // Valid partial payment of 200 with optional new due date 2026-12-15
        viewModel.onPaymentAmountChange("200")
        viewModel.onPaymentDefineNewDateToggle(true)
        viewModel.onPaymentNewDueDateChange("2026-12-15")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val afterPartial = viewModel.uiState.value
        assertNull(afterPartial.payingLoan)
        assertNull(afterPartial.paymentErrorMessage)
        assertEquals(LoansViewModel.MSG_PAYMENT_SAVED, afterPartial.toastMessage)
        assertEquals(200.0, afterPartial.totalPaid, 0.001)
        assertEquals(250.0, afterPartial.totalReceivable, 0.001)
        assertEquals(800.0, afterPartial.savingsBalances.single().netBalance, 0.001)
        assertEquals("2026-12-15", afterPartial.loans.single().installments.single().dueDate)

        // Pay remaining full amount via quick action
        viewModel.openPaymentDialog(afterPartial.loans.single())
        viewModel.onPaymentFullAmountClick()
        assertEquals("250", viewModel.uiState.value.paymentAmountInput)
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val afterFull = viewModel.uiState.value
        assertNull(afterFull.payingLoan)
        assertEquals(450.0, afterFull.totalPaid, 0.001)
        assertEquals(0.0, afterFull.totalReceivable, 0.001)
        assertEquals(1050.0, afterFull.savingsBalances.single().netBalance, 0.001)
        assertTrue(afterFull.loans.single().isSettled)
    }

    @Test
    fun recordPayment_withFutureInstallments_supportsOptionalAddAndAdjustActions() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Mateo",
                amountLent = 1000.0,
                amountToReceive = 1000.0,
                savingCategory = "Viajes",
                installments = listOf(
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-01",
                        expectedAmount = 400.0
                    ),
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-20",
                        expectedAmount = 600.0
                    )
                )
            )
        )
        advanceUntilIdle()

        // Partial payment of 250 + add installment 150 on 2027-01-10
        viewModel.openPaymentDialog(viewModel.uiState.value.loans.single { it.id == loanId })
        viewModel.onPaymentAmountChange("250")
        viewModel.onPartialScheduleActionSelected(PartialScheduleAction.ADD_INSTALLMENT)
        viewModel.onPaymentExtraInstallmentDateChange("2027-01-10")
        viewModel.onPaymentExtraInstallmentAmountChange("150")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val afterAdd = viewModel.uiState.value.loans.single()
        assertEquals(3, afterAdd.installments.size)
        assertEquals(250.0, afterAdd.installments[0].paymentAmount, 0.001)
        assertTrue(afterAdd.installments[0].isPaid)
        assertEquals("2026-12-20", afterAdd.installments[1].dueDate)
        assertEquals(600.0, afterAdd.installments[1].expectedAmount, 0.001)
        assertEquals("2027-01-10", afterAdd.installments[2].dueDate)
        assertEquals(150.0, afterAdd.installments[2].expectedAmount, 0.001)

        // Partial payment of 100 toward Dec 20 installment + adjust remaining from 500 to 350
        viewModel.openPaymentDialog(afterAdd)
        assertEquals(afterAdd.installments[1].id, viewModel.uiState.value.paymentTargetInstallmentId)
        viewModel.onPaymentAmountChange("100")
        viewModel.onPartialScheduleActionSelected(PartialScheduleAction.ADJUST_NEXT_INSTALLMENT)
        assertEquals("500", viewModel.uiState.value.paymentAdjustedNextAmountInput)
        viewModel.onPaymentAdjustedNextAmountChange("350")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val afterAdjust = viewModel.uiState.value.loans.single()
        assertEquals(100.0, afterAdjust.installments[1].paymentAmount, 0.001)
        assertEquals(350.0, afterAdjust.installments[1].remainingAmount, 0.001)
        assertEquals(300.0, afterAdjust.installments[2].expectedAmount, 0.001)
    }

    @Test
    fun recordPayment_selectingSecondInstallment_recordsPaymentAmountOnChosenInstallment() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Andrés",
                amountLent = 1000.0,
                amountToReceive = 1000.0,
                savingCategory = "Viajes",
                installments = listOf(
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-01",
                        expectedAmount = 400.0
                    ),
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-20",
                        expectedAmount = 600.0
                    )
                )
            )
        )
        advanceUntilIdle()

        val loan = viewModel.uiState.value.loans.single { it.id == loanId }
        val secondInstId = loan.installments[1].id
        viewModel.openPaymentDialog(loan)
        assertEquals(loan.installments[0].id, viewModel.uiState.value.paymentTargetInstallmentId)

        viewModel.onPaymentTargetInstallmentSelected(secondInstId)
        viewModel.onPaymentAmountChange("200")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val updated = viewModel.uiState.value.loans.single()
        assertEquals(0.0, updated.installments[0].paymentAmount, 0.001)
        assertEquals(200.0, updated.installments[1].paymentAmount, 0.001)
    }

    @Test
    fun createLoan_singleDateAndInstallmentsMode_createInstallmentOrOpenScheduleDialog() = runTest(testDispatcher) {
        // Mode 2: single payment date creates 1 installment for full amountToReceive
        viewModel.onPersonNameChange("Laura")
        viewModel.onAmountLentChange("300")
        viewModel.onAmountToReceiveChange("360")
        viewModel.onCreationPaymentModeSelected(LoanCreationPaymentMode.SINGLE_DATE)
        viewModel.onCreationSingleDueDateChange("2026-12-28")
        viewModel.saveLoan()
        advanceUntilIdle()

        val firstLoan = viewModel.uiState.value.loans.single { it.personName == "Laura" }
        assertEquals(1, firstLoan.installments.size)
        assertEquals("2026-12-28", firstLoan.installments.single().dueDate)
        assertEquals(360.0, firstLoan.installments.single().expectedAmount, 0.001)
        assertNull(viewModel.uiState.value.editingScheduleLoan)

        // Mode 3: agreed installments saves loan and automatically opens schedule dialog for it
        viewModel.onPersonNameChange("Diego")
        viewModel.onAmountLentChange("500")
        viewModel.onCreationPaymentModeSelected(LoanCreationPaymentMode.INSTALLMENTS)
        viewModel.saveLoan()
        advanceUntilIdle()

        val createdDiego = viewModel.uiState.value.loans.single { it.personName == "Diego" }
        assertTrue(createdDiego.installments.isEmpty())
        assertEquals(createdDiego.id, viewModel.uiState.value.editingScheduleLoan?.id)
        assertEquals(LoanCreationPaymentMode.NO_DATE, viewModel.uiState.value.creationPaymentMode)
    }

    @Test
    fun scheduleInstallment_editAndDiscardConfirmationFlow() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Camila",
                amountLent = 400.0,
                amountToReceive = 400.0,
                installments = listOf(
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-01",
                        expectedAmount = 150.0
                    ),
                    com.example.money_saver_android.domain.model.LoanInstallment(
                        dueDate = "2026-12-20",
                        expectedAmount = 250.0
                    )
                )
            )
        )
        advanceUntilIdle()

        viewModel.openScheduleDialog(viewModel.uiState.value.loans.single { it.id == loanId })
        val firstDraft = viewModel.uiState.value.scheduleInstallments.first()
        viewModel.startEditingScheduleInstallment(firstDraft.draftId)
        assertEquals(firstDraft.draftId, viewModel.uiState.value.editingScheduleInstallmentDraftId)
        assertEquals("2026-12-01", viewModel.uiState.value.scheduleDateInput)
        assertEquals("150", viewModel.uiState.value.scheduleAmountInput)

        // Update first installment date & amount
        viewModel.onScheduleDateChange("2026-12-05")
        viewModel.onScheduleAmountChange("150")
        viewModel.addScheduleInstallment()
        assertNull(viewModel.uiState.value.editingScheduleInstallmentDraftId)
        assertEquals("2026-12-05", viewModel.uiState.value.scheduleInstallments.first().dueDate)

        // Request close -> discard confirmation shown; dismiss keeps dialog open; confirm closes without saving
        viewModel.requestCloseScheduleDialog()
        assertTrue(viewModel.uiState.value.showDiscardScheduleChangesDialog)
        viewModel.dismissDiscardScheduleChangesDialog()
        assertTrue(!viewModel.uiState.value.showDiscardScheduleChangesDialog)
        assertEquals(loanId, viewModel.uiState.value.editingScheduleLoan?.id)

        viewModel.requestCloseScheduleDialog()
        viewModel.closeScheduleDialog()
        assertNull(viewModel.uiState.value.editingScheduleLoan)
        assertEquals("2026-12-01", viewModel.uiState.value.loans.single().installments.first().dueDate)
    }

    @Test
    fun editLoan_discardConfirmationFlow_requestsDismissesAndResetsOnConfirm() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            Loan(personName = "Lucas", amountLent = 300.0, amountToReceive = 300.0)
        )
        advanceUntilIdle()

        viewModel.startEditingLoan(viewModel.uiState.value.loans.single { it.id == loanId })
        viewModel.onPersonNameChange("Lucas Modificado")
        viewModel.requestCloseEditLoanDialog()
        assertTrue(viewModel.uiState.value.showDiscardEditLoanDialog)

        viewModel.dismissDiscardEditLoanDialog()
        assertTrue(!viewModel.uiState.value.showDiscardEditLoanDialog)
        assertEquals(loanId, viewModel.uiState.value.editingLoanId)
        assertEquals("Lucas Modificado", viewModel.uiState.value.personNameInput)

        viewModel.requestCloseEditLoanDialog()
        viewModel.resetForm()
        assertTrue(!viewModel.uiState.value.showDiscardEditLoanDialog)
        assertNull(viewModel.uiState.value.editingLoanId)
        assertEquals("Lucas", viewModel.uiState.value.loans.single().personName)
    }

    @Test
    fun paymentHistory_openAndClose_tracksLoanIdAndClearsOnDelete() = runTest(testDispatcher) {
        val loanId = loanRepo.insertLoan(
            Loan(personName = "Paula", amountLent = 200.0, amountToReceive = 200.0)
        )
        advanceUntilIdle()
        val loan = viewModel.uiState.value.loans.single { it.id == loanId }

        viewModel.openPaymentHistory(loan)
        assertEquals(loanId, viewModel.uiState.value.historyLoanId)

        viewModel.closePaymentHistory()
        assertNull(viewModel.uiState.value.historyLoanId)

        viewModel.openPaymentHistory(loan)
        viewModel.requestDeleteLoan(loan)
        viewModel.confirmDeleteLoan()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.historyLoanId)
    }

    @Test
    fun editAndDeletePaymentFromHistory_updatesBalancesAndTotals() = runTest(testDispatcher) {
        savingsRepo.savingsFlow.value = listOf(Saving(id = 1L, amount = 500.0, category = "Viajes", periodId = 1L))
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Sofía",
                amountLent = 400.0,
                amountToReceive = 400.0,
                savingCategory = "Viajes"
            )
        )
        advanceUntilIdle()

        viewModel.openPaymentDialog(viewModel.uiState.value.loans.single { it.id == loanId })
        viewModel.onPaymentAmountChange("150")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        val afterPay = viewModel.uiState.value.loans.single()
        val payment = afterPay.payments.single()
        assertEquals(150.0, viewModel.uiState.value.totalPaid, 0.001)
        assertEquals(250.0, viewModel.uiState.value.totalReceivable, 0.001)

        // Edit payment from 150 -> 200
        viewModel.openEditPaymentDialog(afterPay, payment)
        assertEquals(payment.id, viewModel.uiState.value.editingPaymentId)
        assertEquals("150", viewModel.uiState.value.paymentAmountInput)
        viewModel.onPaymentAmountChange("200")
        viewModel.saveLoanPayment()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingPaymentId)
        assertEquals(LoansViewModel.MSG_PAYMENT_UPDATED, viewModel.uiState.value.toastMessage)
        assertEquals(200.0, viewModel.uiState.value.totalPaid, 0.001)
        assertEquals(200.0, viewModel.uiState.value.totalReceivable, 0.001)
        assertEquals(300.0, viewModel.uiState.value.savingsBalances.single().netBalance, 0.001)

        // Request delete payment and confirm
        val updatedLoan = viewModel.uiState.value.loans.single()
        val updatedPayment = updatedLoan.payments.single()
        viewModel.requestDeleteLoanPayment(updatedLoan, updatedPayment)
        assertEquals(updatedPayment.id, viewModel.uiState.value.paymentToDelete?.second?.id)
        viewModel.confirmDeleteLoanPayment()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.paymentToDelete)
        assertEquals(LoansViewModel.MSG_PAYMENT_DELETED, viewModel.uiState.value.toastMessage)
        assertEquals(0.0, viewModel.uiState.value.totalPaid, 0.001)
        assertEquals(400.0, viewModel.uiState.value.totalReceivable, 0.001)
        assertEquals(100.0, viewModel.uiState.value.savingsBalances.single().netBalance, 0.001)
    }
}
