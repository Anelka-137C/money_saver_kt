package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanPayment
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanInstallmentValidationResult
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.PartialPaymentScheduleOption
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.domain.usecase.parseLoanAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class LoanInstallmentDraft(
    val draftId: String = UUID.randomUUID().toString(),
    val dueDate: String,
    val expectedAmountInput: String,
    val persistedId: Long = 0L,
    val paymentAmount: Double = 0.0
) {
    val isPaid: Boolean
        get() {
            val expected = parseLoanAmount(expectedAmountInput) ?: return false
            if (!expected.isFinite() || expected <= 0.0) return false
            val paid = if (paymentAmount.isFinite() && paymentAmount > 0.0) {
                BigDecimal.valueOf(paymentAmount)
            } else {
                BigDecimal.ZERO
            }
            return BigDecimal.valueOf(expected).subtract(paid) <= BigDecimal.ZERO
        }
}

enum class PartialScheduleAction {
    KEEP_CURRENT,
    ADD_INSTALLMENT,
    ADJUST_NEXT_INSTALLMENT
}

enum class LoanCreationPaymentMode {
    NO_DATE,
    SINGLE_DATE,
    INSTALLMENTS
}

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val totalReceivable: Double = 0.0,
    val totalPaid: Double = 0.0,
    val savingsBalances: List<SavingsCategoryBalance> = emptyList(),
    val editSavingsBalances: List<SavingsCategoryBalance> = emptyList(),
    val savingCategories: List<Category> = emptyList(),
    val todayIso: String = "",
    val showLoanFormDialog: Boolean = false,
    val editingLoanId: Long? = null,
    val personNameInput: String = "",
    val amountLentInput: String = "",
    val amountToReceiveInput: String = "",
    val isAmountToReceiveManuallyEdited: Boolean = false,
    val noteInput: String = "",
    val selectedSavingCategory: String = "",
    val creationPaymentMode: LoanCreationPaymentMode = LoanCreationPaymentMode.NO_DATE,
    val creationSingleDueDateInput: String = "",
    val draftInstallments: List<LoanInstallmentDraft> = emptyList(),
    val draftInstallmentDateInput: String = "",
    val draftInstallmentAmountInput: String = "",
    val errorMessage: String? = null,
    val showDiscardEditLoanDialog: Boolean = false,
    val editingScheduleLoan: Loan? = null,
    val scheduleInstallments: List<LoanInstallmentDraft> = emptyList(),
    val editingScheduleInstallmentDraftId: String? = null,
    val scheduleDateInput: String = "",
    val scheduleAmountInput: String = "",
    val scheduleErrorMessage: String? = null,
    val showDiscardScheduleChangesDialog: Boolean = false,
    val payingLoan: Loan? = null,
    val editingPaymentId: Long? = null,
    val paymentAmountInput: String = "",
    val paymentDateTimeInput: String = "",
    val paymentSavingCategoryInput: String = "",
    val paymentTargetInstallmentId: Long? = null,
    val paymentDefineNewDateOnNoSchedule: Boolean = false,
    val paymentNewDueDateInput: String = "",
    val partialScheduleAction: PartialScheduleAction = PartialScheduleAction.KEEP_CURRENT,
    val paymentExtraInstallmentDateInput: String = "",
    val paymentExtraInstallmentAmountInput: String = "",
    val paymentAdjustedNextAmountInput: String = "",
    val isAdjustedNextAmountManuallyEdited: Boolean = false,
    val paymentAdjustedRemainderDateInput: String = "",
    val paymentErrorMessage: String? = null,
    val historyLoanId: Long? = null,
    val paymentToDelete: Pair<Loan, LoanPayment>? = null,
    val loanToDelete: Loan? = null,
    val toastMessage: String? = null
)

class LoansViewModel(
    private val loanUseCase: LoanUseCase
) : ViewModel() {

    companion object {
        const val MSG_LOAN_SAVED = "loans.messages.saved"
        const val MSG_LOAN_UPDATED = "loans.messages.updated"
        const val MSG_LOAN_DELETED = "loans.messages.deleted"
        const val MSG_SCHEDULE_UPDATED = "loans.messages.schedule_updated"
        const val MSG_PAYMENT_SAVED = "loans.messages.payment_saved"
        const val MSG_PAYMENT_UPDATED = "loans.messages.payment_updated"
        const val MSG_PAYMENT_DELETED = "loans.messages.payment_deleted"
    }

    private val initialToday = loanUseCase.todayIso()
    private val _uiState = MutableStateFlow(
        LoansUiState(
            todayIso = initialToday,
            creationSingleDueDateInput = initialToday,
            draftInstallmentDateInput = initialToday,
            scheduleDateInput = initialToday,
            paymentNewDueDateInput = initialToday,
            paymentExtraInstallmentDateInput = initialToday,
            paymentAdjustedRemainderDateInput = initialToday
        )
    )
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    private var isSaving: Boolean = false
    private var latestSavingsSnapshot: SavingsBalanceSnapshot? = null

    private data class SavingsBalanceSnapshot(
        val savings: List<Saving>,
        val expenses: List<Expense>,
        val loans: List<Loan>,
        val categories: List<Category>
    )

    init {
        viewModelScope.launch {
            loanUseCase.getAllLoans()
                .catch { emit(emptyList()) }
                .collect { loanList ->
                    _uiState.update { current ->
                        current.copy(
                            loans = loanList,
                            totalReceivable = loanUseCase.calculateTotalReceivable(loanList),
                            totalPaid = loanUseCase.calculateTotalPaid(loanList)
                        )
                    }
                }
        }
        viewModelScope.launch {
            combine(
                loanUseCase.getAllSavings().catch { emit(emptyList()) }.onStart { emit(emptyList()) },
                loanUseCase.getAllExpenses().catch { emit(emptyList()) }.onStart { emit(emptyList()) },
                loanUseCase.getAllLoans().catch { emit(emptyList()) }.onStart { emit(emptyList()) },
                loanUseCase.getSavingCategories().catch { emit(emptyList()) }.onStart { emit(emptyList()) }
            ) { allSavings, allExpenses, allLoans, savingCategories ->
                SavingsBalanceSnapshot(allSavings, allExpenses, allLoans, savingCategories)
            }.collect { snapshot ->
                latestSavingsSnapshot = snapshot
                val balances = calculateSavingsNetBalances(
                    savings = snapshot.savings,
                    expenses = snapshot.expenses,
                    categories = snapshot.categories,
                    loans = snapshot.loans
                )
                _uiState.update { current ->
                    val editBalances = current.editingLoanId?.let { editId ->
                        calculateSavingsNetBalances(
                            savings = snapshot.savings,
                            expenses = snapshot.expenses,
                            categories = snapshot.categories,
                            loans = snapshot.loans,
                            excludeLoanId = editId
                        )
                    } ?: balances
                    current.copy(
                        savingsBalances = balances,
                        editSavingsBalances = editBalances,
                        savingCategories = snapshot.categories
                    )
                }
            }
        }
    }

    fun onPersonNameChange(value: String) {
        _uiState.update { it.copy(personNameInput = value, errorMessage = null) }
    }

    fun onAmountLentChange(value: String) {
        _uiState.update { current ->
            current.copy(
                amountLentInput = value,
                amountToReceiveInput = if (current.isAmountToReceiveManuallyEdited) current.amountToReceiveInput else value,
                errorMessage = null
            )
        }
    }

    fun onAmountToReceiveChange(value: String) {
        _uiState.update {
            it.copy(
                amountToReceiveInput = value,
                isAmountToReceiveManuallyEdited = true,
                errorMessage = null
            )
        }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(noteInput = value, errorMessage = null) }
    }

    fun onSavingCategorySelected(category: String) {
        _uiState.update { it.copy(selectedSavingCategory = category, errorMessage = null) }
    }

    fun onCreationPaymentModeSelected(mode: LoanCreationPaymentMode) {
        _uiState.update {
            it.copy(
                creationPaymentMode = mode,
                draftInstallments = emptyList(),
                errorMessage = null
            )
        }
    }

    fun onCreationSingleDueDateChange(value: String) {
        _uiState.update { it.copy(creationSingleDueDateInput = value, errorMessage = null) }
    }

    fun onDraftInstallmentDateChange(value: String) {
        _uiState.update { it.copy(draftInstallmentDateInput = value, errorMessage = null) }
    }

    fun onDraftInstallmentAmountChange(value: String) {
        _uiState.update { it.copy(draftInstallmentAmountInput = value, errorMessage = null) }
    }

    fun addDraftInstallment() {
        val state = _uiState.value
        when (val validation = loanUseCase.validateInstallmentDraft(
            dueDateInput = state.draftInstallmentDateInput,
            expectedAmountInput = state.draftInstallmentAmountInput
        )) {
            is LoanInstallmentValidationResult.Error -> {
                _uiState.update { it.copy(errorMessage = validation.message) }
            }
            is LoanInstallmentValidationResult.Valid -> {
                val nextDraft = LoanInstallmentDraft(
                    dueDate = validation.installment.dueDate,
                    expectedAmountInput = formatDraftAmount(validation.installment.expectedAmount)
                )
                _uiState.update { current ->
                    current.copy(
                        draftInstallments = (current.draftInstallments + nextDraft).sortedBy { it.dueDate },
                        draftInstallmentAmountInput = "",
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun removeDraftInstallment(draftId: String) {
        _uiState.update { current ->
            current.copy(
                draftInstallments = current.draftInstallments.filterNot { it.draftId == draftId },
                errorMessage = null
            )
        }
    }

    fun openScheduleDialog(loan: Loan) {
        val today = loanUseCase.todayIso()
        val existingDrafts = loan.installments
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
            .map { inst ->
                LoanInstallmentDraft(
                    dueDate = inst.dueDate,
                    expectedAmountInput = formatDraftAmount(inst.expectedAmount),
                    persistedId = inst.id,
                    paymentAmount = inst.paymentAmount
                )
            }
        _uiState.update {
            it.copy(
                todayIso = today,
                editingScheduleLoan = loan,
                scheduleInstallments = existingDrafts,
                editingScheduleInstallmentDraftId = null,
                scheduleDateInput = today,
                scheduleAmountInput = "",
                scheduleErrorMessage = null,
                showDiscardScheduleChangesDialog = false,
                errorMessage = null
            )
        }
    }

    fun requestCloseScheduleDialog() {
        _uiState.update { it.copy(showDiscardScheduleChangesDialog = true) }
    }

    fun dismissDiscardScheduleChangesDialog() {
        _uiState.update { it.copy(showDiscardScheduleChangesDialog = false) }
    }

    fun closeScheduleDialog() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                editingScheduleLoan = null,
                scheduleInstallments = emptyList(),
                editingScheduleInstallmentDraftId = null,
                scheduleDateInput = today,
                scheduleAmountInput = "",
                scheduleErrorMessage = null,
                showDiscardScheduleChangesDialog = false
            )
        }
    }

    fun onScheduleDateChange(value: String) {
        _uiState.update { it.copy(scheduleDateInput = value, scheduleErrorMessage = null) }
    }

    fun onScheduleAmountChange(value: String) {
        _uiState.update { it.copy(scheduleAmountInput = value, scheduleErrorMessage = null) }
    }

    fun startEditingScheduleInstallment(draftId: String) {
        val target = _uiState.value.scheduleInstallments.find { it.draftId == draftId } ?: return
        _uiState.update {
            it.copy(
                editingScheduleInstallmentDraftId = target.draftId,
                scheduleDateInput = target.dueDate,
                scheduleAmountInput = target.expectedAmountInput,
                scheduleErrorMessage = null
            )
        }
    }

    fun cancelEditingScheduleInstallment() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                editingScheduleInstallmentDraftId = null,
                scheduleDateInput = today,
                scheduleAmountInput = "",
                scheduleErrorMessage = null
            )
        }
    }

    fun addScheduleInstallment() {
        val state = _uiState.value
        val editingDraftId = state.editingScheduleInstallmentDraftId
        val editingTarget = editingDraftId?.let { id ->
            state.scheduleInstallments.find { it.draftId == id }
        }
        val persistedDate = editingTarget?.takeIf { it.persistedId > 0L }?.dueDate
        val existingPaid = editingTarget?.paymentAmount ?: 0.0
        when (val validation = loanUseCase.validateInstallmentDraft(
            dueDateInput = state.scheduleDateInput,
            expectedAmountInput = state.scheduleAmountInput,
            persistedDueDate = persistedDate,
            existingPaymentAmount = existingPaid
        )) {
            is LoanInstallmentValidationResult.Error -> {
                _uiState.update { it.copy(scheduleErrorMessage = validation.message) }
            }
            is LoanInstallmentValidationResult.Valid -> {
                val today = loanUseCase.todayIso()
                val updatedList = if (editingTarget != null) {
                    val keptPersistedId = editingTarget.persistedId
                    val updatedDraft = editingTarget.copy(
                        dueDate = validation.installment.dueDate,
                        expectedAmountInput = formatDraftAmount(validation.installment.expectedAmount),
                        persistedId = keptPersistedId
                    )
                    state.scheduleInstallments
                        .map { if (it.draftId == editingTarget.draftId) updatedDraft else it }
                        .sortedBy { it.dueDate }
                } else {
                    val nextDraft = LoanInstallmentDraft(
                        dueDate = validation.installment.dueDate,
                        expectedAmountInput = formatDraftAmount(validation.installment.expectedAmount),
                        persistedId = 0L
                    )
                    (state.scheduleInstallments + nextDraft).sortedBy { it.dueDate }
                }
                _uiState.update { current ->
                    current.copy(
                        scheduleInstallments = updatedList,
                        editingScheduleInstallmentDraftId = null,
                        scheduleDateInput = if (editingTarget != null) today else current.scheduleDateInput,
                        scheduleAmountInput = "",
                        scheduleErrorMessage = null
                    )
                }
            }
        }
    }

    fun removeScheduleInstallment(draftId: String) {
        val today = loanUseCase.todayIso()
        _uiState.update { current ->
            val wasEditingRemoved = current.editingScheduleInstallmentDraftId == draftId
            current.copy(
                scheduleInstallments = current.scheduleInstallments.filterNot { it.draftId == draftId },
                editingScheduleInstallmentDraftId = if (wasEditingRemoved) null else current.editingScheduleInstallmentDraftId,
                scheduleDateInput = if (wasEditingRemoved) today else current.scheduleDateInput,
                scheduleAmountInput = if (wasEditingRemoved) "" else current.scheduleAmountInput,
                scheduleErrorMessage = null
            )
        }
    }

    fun openPaymentDialog(loan: Loan) {
        val today = loanUseCase.todayIso()
        val nowDateTime = loanUseCase.nowDateTimeIso()
        val firstUnpaidId = loan.installments
            .filter { !it.isPaid }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
            .firstOrNull()
            ?.id
        _uiState.update {
            it.copy(
                todayIso = today,
                payingLoan = loan,
                editingPaymentId = null,
                paymentAmountInput = "",
                paymentDateTimeInput = nowDateTime,
                paymentSavingCategoryInput = loan.savingCategory.trim(),
                paymentTargetInstallmentId = firstUnpaidId,
                paymentDefineNewDateOnNoSchedule = false,
                paymentNewDueDateInput = today,
                partialScheduleAction = PartialScheduleAction.KEEP_CURRENT,
                paymentExtraInstallmentDateInput = today,
                paymentExtraInstallmentAmountInput = "",
                paymentAdjustedNextAmountInput = "",
                isAdjustedNextAmountManuallyEdited = false,
                paymentAdjustedRemainderDateInput = today,
                paymentErrorMessage = null,
                errorMessage = null
            )
        }
    }

    fun openEditPaymentDialog(loan: Loan, payment: LoanPayment) {
        val today = loanUseCase.todayIso()
        val installmentsWithoutThis = loanUseCase.recomputeInstallmentsWithoutPayment(loan, payment.id)
        val loanForEdit = loan.copy(
            payments = loan.payments.filterNot { it.id == payment.id },
            installments = installmentsWithoutThis
        )
        val unpaidInstallments = loanForEdit.installments
            .filter { !it.isPaid }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        val preferredTargetId = payment.installmentId
            ?.takeIf { targetId -> unpaidInstallments.any { it.id == targetId } }
            ?: unpaidInstallments.firstOrNull()?.id
        _uiState.update {
            it.copy(
                todayIso = today,
                payingLoan = loanForEdit,
                editingPaymentId = payment.id,
                paymentAmountInput = formatDraftAmount(payment.amount),
                paymentDateTimeInput = payment.paidAt,
                paymentSavingCategoryInput = payment.savingCategory,
                paymentTargetInstallmentId = preferredTargetId,
                paymentDefineNewDateOnNoSchedule = false,
                paymentNewDueDateInput = today,
                partialScheduleAction = PartialScheduleAction.KEEP_CURRENT,
                paymentExtraInstallmentDateInput = today,
                paymentExtraInstallmentAmountInput = "",
                paymentAdjustedNextAmountInput = "",
                isAdjustedNextAmountManuallyEdited = false,
                paymentAdjustedRemainderDateInput = today,
                paymentErrorMessage = null,
                errorMessage = null
            )
        }
    }

    fun closePaymentDialog() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                payingLoan = null,
                editingPaymentId = null,
                paymentAmountInput = "",
                paymentDateTimeInput = "",
                paymentSavingCategoryInput = "",
                paymentTargetInstallmentId = null,
                paymentDefineNewDateOnNoSchedule = false,
                paymentNewDueDateInput = today,
                partialScheduleAction = PartialScheduleAction.KEEP_CURRENT,
                paymentExtraInstallmentDateInput = today,
                paymentExtraInstallmentAmountInput = "",
                paymentAdjustedNextAmountInput = "",
                isAdjustedNextAmountManuallyEdited = false,
                paymentAdjustedRemainderDateInput = today,
                paymentErrorMessage = null
            )
        }
    }

    fun openPaymentHistory(loan: Loan) {
        _uiState.update {
            it.copy(
                historyLoanId = loan.id,
                paymentErrorMessage = null,
                errorMessage = null
            )
        }
    }

    fun closePaymentHistory() {
        _uiState.update {
            it.copy(
                historyLoanId = null,
                paymentErrorMessage = null,
                errorMessage = null
            )
        }
    }

    fun requestDeleteLoanPayment(loan: Loan, payment: LoanPayment) {
        _uiState.update { it.copy(paymentToDelete = loan to payment, paymentErrorMessage = null) }
    }

    fun dismissDeleteLoanPayment() {
        _uiState.update { it.copy(paymentToDelete = null) }
    }

    fun confirmDeleteLoanPayment() {
        if (isSaving) return
        val (loan, payment) = _uiState.value.paymentToDelete ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = loanUseCase.deleteLoanPayment(loan.id, payment.id)) {
                    is LoanResult.Success -> {
                        _uiState.update {
                            it.copy(
                                paymentToDelete = null,
                                paymentErrorMessage = null,
                                errorMessage = null,
                                toastMessage = MSG_PAYMENT_DELETED
                            )
                        }
                    }
                    is LoanResult.Error -> {
                        _uiState.update {
                            it.copy(
                                paymentToDelete = null,
                                paymentErrorMessage = result.message,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message?.takeIf { msg -> msg.isNotBlank() }
                    ?: "No se pudo eliminar el pago"
                _uiState.update {
                    it.copy(
                        paymentToDelete = null,
                        paymentErrorMessage = message,
                        errorMessage = message
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    private fun computePrefilledAdjustedNextAmount(
        loan: Loan?,
        paymentAmountInput: String,
        targetInstallmentId: Long?
    ): String {
        if (loan == null) return ""
        val parsed = parseLoanAmount(paymentAmountInput) ?: 0.0
        val nextInst = loanUseCase
            .previewInstallmentsAfterPayment(loan, parsed, targetInstallmentId)
            .firstOrNull { !it.isPaid }
        return nextInst?.let { formatDraftAmount(it.remainingAmount) } ?: ""
    }

    fun onPaymentAmountChange(value: String) {
        _uiState.update { current ->
            val refreshedAdjusted = if (current.partialScheduleAction == PartialScheduleAction.ADJUST_NEXT_INSTALLMENT &&
                !current.isAdjustedNextAmountManuallyEdited
            ) {
                computePrefilledAdjustedNextAmount(
                    loan = current.payingLoan,
                    paymentAmountInput = value,
                    targetInstallmentId = current.paymentTargetInstallmentId
                )
            } else {
                current.paymentAdjustedNextAmountInput
            }
            current.copy(
                paymentAmountInput = value,
                paymentAdjustedNextAmountInput = refreshedAdjusted,
                paymentErrorMessage = null
            )
        }
    }

    fun onPaymentTargetInstallmentSelected(installmentId: Long) {
        _uiState.update { current ->
            val refreshedAdjusted = if (current.partialScheduleAction == PartialScheduleAction.ADJUST_NEXT_INSTALLMENT &&
                !current.isAdjustedNextAmountManuallyEdited
            ) {
                computePrefilledAdjustedNextAmount(
                    loan = current.payingLoan,
                    paymentAmountInput = current.paymentAmountInput,
                    targetInstallmentId = installmentId
                )
            } else {
                current.paymentAdjustedNextAmountInput
            }
            current.copy(
                paymentTargetInstallmentId = installmentId,
                paymentAdjustedNextAmountInput = refreshedAdjusted,
                paymentErrorMessage = null
            )
        }
    }

    fun onPaymentDefineNewDateToggle(enabled: Boolean) {
        _uiState.update { it.copy(paymentDefineNewDateOnNoSchedule = enabled, paymentErrorMessage = null) }
    }

    fun onPaymentFullAmountClick() {
        val loan = _uiState.value.payingLoan ?: return
        val fullAmountStr = formatDraftAmount(loan.pendingAmount)
        _uiState.update { current ->
            val refreshedAdjusted = if (current.partialScheduleAction == PartialScheduleAction.ADJUST_NEXT_INSTALLMENT &&
                !current.isAdjustedNextAmountManuallyEdited
            ) {
                computePrefilledAdjustedNextAmount(
                    loan = loan,
                    paymentAmountInput = fullAmountStr,
                    targetInstallmentId = current.paymentTargetInstallmentId
                )
            } else {
                current.paymentAdjustedNextAmountInput
            }
            current.copy(
                paymentAmountInput = fullAmountStr,
                paymentAdjustedNextAmountInput = refreshedAdjusted,
                paymentErrorMessage = null
            )
        }
    }

    fun onPaymentDateTimeChange(value: String) {
        _uiState.update { it.copy(paymentDateTimeInput = value, paymentErrorMessage = null) }
    }

    fun onPaymentSavingCategorySelected(category: String) {
        _uiState.update { it.copy(paymentSavingCategoryInput = category, paymentErrorMessage = null) }
    }

    fun onPaymentNewDueDateChange(value: String) {
        _uiState.update { it.copy(paymentNewDueDateInput = value, paymentErrorMessage = null) }
    }

    fun onPartialScheduleActionSelected(action: PartialScheduleAction) {
        _uiState.update { current ->
            val shouldRefreshPrefill = action == PartialScheduleAction.ADJUST_NEXT_INSTALLMENT &&
                (!current.isAdjustedNextAmountManuallyEdited || current.paymentAdjustedNextAmountInput.isBlank())
            val prefilledAdjusted = if (shouldRefreshPrefill) {
                computePrefilledAdjustedNextAmount(
                    loan = current.payingLoan,
                    paymentAmountInput = current.paymentAmountInput,
                    targetInstallmentId = current.paymentTargetInstallmentId
                )
            } else {
                current.paymentAdjustedNextAmountInput
            }
            current.copy(
                partialScheduleAction = action,
                paymentAdjustedNextAmountInput = prefilledAdjusted,
                isAdjustedNextAmountManuallyEdited = if (shouldRefreshPrefill) false else current.isAdjustedNextAmountManuallyEdited,
                paymentErrorMessage = null
            )
        }
    }

    fun onPaymentExtraInstallmentDateChange(value: String) {
        _uiState.update { it.copy(paymentExtraInstallmentDateInput = value, paymentErrorMessage = null) }
    }

    fun onPaymentExtraInstallmentAmountChange(value: String) {
        _uiState.update { it.copy(paymentExtraInstallmentAmountInput = value, paymentErrorMessage = null) }
    }

    fun onPaymentAdjustedNextAmountChange(value: String) {
        _uiState.update {
            it.copy(
                paymentAdjustedNextAmountInput = value,
                isAdjustedNextAmountManuallyEdited = value.isNotBlank(),
                paymentErrorMessage = null
            )
        }
    }

    fun onPaymentAdjustedRemainderDateChange(value: String) {
        _uiState.update { it.copy(paymentAdjustedRemainderDateInput = value, paymentErrorMessage = null) }
    }

    fun saveLoanPayment() {
        if (isSaving) return
        val state = _uiState.value
        val loan = state.payingLoan ?: return
        val parsedAmount = parseLoanAmount(state.paymentAmountInput)
        val scheduleOption = if (parsedAmount != null && parsedAmount.isFinite() && parsedAmount > 0.0) {
            val remainingAfter = BigDecimal.valueOf(loan.pendingAmount)
                .subtract(BigDecimal.valueOf(parsedAmount))
            if (remainingAfter > BigDecimal.ZERO) {
                val afterApplying = loanUseCase.previewInstallmentsAfterPayment(
                    loan = loan,
                    paymentAmount = parsedAmount,
                    targetInstallmentId = state.paymentTargetInstallmentId
                )
                val hasFuture = loanUseCase.hasFutureInstallments(afterApplying, state.todayIso)
                if (!hasFuture) {
                    if (state.paymentDefineNewDateOnNoSchedule) {
                        PartialPaymentScheduleOption.SetNewDueDate(state.paymentNewDueDateInput)
                    } else {
                        PartialPaymentScheduleOption.KeepCurrent
                    }
                } else {
                    when (state.partialScheduleAction) {
                        PartialScheduleAction.KEEP_CURRENT -> PartialPaymentScheduleOption.KeepCurrent
                        PartialScheduleAction.ADD_INSTALLMENT -> PartialPaymentScheduleOption.AddInstallment(
                            dueDate = state.paymentExtraInstallmentDateInput,
                            amountInput = state.paymentExtraInstallmentAmountInput
                        )
                        PartialScheduleAction.ADJUST_NEXT_INSTALLMENT -> PartialPaymentScheduleOption.AdjustNextInstallment(
                            amountInput = state.paymentAdjustedNextAmountInput,
                            remainderDueDate = state.paymentAdjustedRemainderDateInput
                        )
                    }
                }
            } else {
                PartialPaymentScheduleOption.KeepCurrent
            }
        } else {
            PartialPaymentScheduleOption.KeepCurrent
        }

        isSaving = true
        val editingPayId = state.editingPaymentId
        viewModelScope.launch {
            try {
                val result = if (editingPayId == null) {
                    loanUseCase.recordLoanPayment(
                        loanId = loan.id,
                        amountInput = state.paymentAmountInput,
                        paidAtInput = state.paymentDateTimeInput,
                        savingCategoryInput = state.paymentSavingCategoryInput,
                        targetInstallmentId = state.paymentTargetInstallmentId,
                        scheduleOption = scheduleOption
                    )
                } else {
                    loanUseCase.updateLoanPayment(
                        loanId = loan.id,
                        paymentId = editingPayId,
                        amountInput = state.paymentAmountInput,
                        paidAtInput = state.paymentDateTimeInput,
                        savingCategoryInput = state.paymentSavingCategoryInput,
                        targetInstallmentId = state.paymentTargetInstallmentId,
                        scheduleOption = scheduleOption
                    )
                }
                when (result) {
                    is LoanResult.Success -> {
                        val today = loanUseCase.todayIso()
                        _uiState.update {
                            it.copy(
                                todayIso = today,
                                payingLoan = null,
                                editingPaymentId = null,
                                paymentAmountInput = "",
                                paymentDateTimeInput = "",
                                paymentSavingCategoryInput = "",
                                paymentTargetInstallmentId = null,
                                paymentDefineNewDateOnNoSchedule = false,
                                paymentNewDueDateInput = today,
                                partialScheduleAction = PartialScheduleAction.KEEP_CURRENT,
                                paymentExtraInstallmentDateInput = today,
                                paymentExtraInstallmentAmountInput = "",
                                paymentAdjustedNextAmountInput = "",
                                isAdjustedNextAmountManuallyEdited = false,
                                paymentAdjustedRemainderDateInput = today,
                                paymentErrorMessage = null,
                                toastMessage = if (editingPayId == null) MSG_PAYMENT_SAVED else MSG_PAYMENT_UPDATED
                            )
                        }
                    }
                    is LoanResult.Error -> {
                        _uiState.update { it.copy(paymentErrorMessage = result.message) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        paymentErrorMessage = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "No se pudo registrar el pago"
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun requestCloseEditLoanDialog() {
        _uiState.update { it.copy(showDiscardEditLoanDialog = true) }
    }

    fun dismissDiscardEditLoanDialog() {
        _uiState.update { it.copy(showDiscardEditLoanDialog = false) }
    }

    fun openCreateLoanDialog() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                todayIso = today,
                showLoanFormDialog = true,
                editingLoanId = null,
                personNameInput = "",
                amountLentInput = "",
                amountToReceiveInput = "",
                isAmountToReceiveManuallyEdited = false,
                noteInput = "",
                selectedSavingCategory = "",
                creationPaymentMode = LoanCreationPaymentMode.NO_DATE,
                creationSingleDueDateInput = today,
                draftInstallments = emptyList(),
                draftInstallmentDateInput = today,
                draftInstallmentAmountInput = "",
                errorMessage = null,
                showDiscardEditLoanDialog = false
            )
        }
    }

    fun startEditingLoan(loan: Loan) {
        val today = loanUseCase.todayIso()
        val existingDrafts = loan.installments
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
            .map { inst ->
                LoanInstallmentDraft(
                    dueDate = inst.dueDate,
                    expectedAmountInput = formatDraftAmount(inst.expectedAmount),
                    persistedId = inst.id,
                    paymentAmount = inst.paymentAmount
                )
            }
        val snapshot = latestSavingsSnapshot
        val editBalances = if (snapshot != null) {
            calculateSavingsNetBalances(
                savings = snapshot.savings,
                expenses = snapshot.expenses,
                categories = snapshot.categories,
                loans = snapshot.loans,
                excludeLoanId = loan.id
            )
        } else {
            _uiState.value.savingsBalances
        }
        _uiState.update {
            it.copy(
                todayIso = today,
                showLoanFormDialog = true,
                editingLoanId = loan.id,
                editSavingsBalances = editBalances,
                personNameInput = loan.personName,
                amountLentInput = formatDraftAmount(loan.amountLent),
                amountToReceiveInput = formatDraftAmount(loan.amountToReceive),
                isAmountToReceiveManuallyEdited = true,
                noteInput = loan.note,
                selectedSavingCategory = loan.savingCategory,
                draftInstallments = existingDrafts,
                draftInstallmentDateInput = today,
                draftInstallmentAmountInput = "",
                errorMessage = null,
                showDiscardEditLoanDialog = false
            )
        }
    }

    fun requestDeleteLoan(loan: Loan) {
        _uiState.update { it.copy(loanToDelete = loan, errorMessage = null) }
    }

    fun dismissDeleteLoan() {
        _uiState.update { it.copy(loanToDelete = null) }
    }

    fun confirmDeleteLoan() {
        if (isSaving) return
        val loan = _uiState.value.loanToDelete ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = loanUseCase.deleteLoan(loan.id)) {
                    is LoanResult.Success -> {
                        val today = loanUseCase.todayIso()
                        _uiState.update { current ->
                            val deletedEditingLoan = current.editingLoanId == loan.id
                            current.copy(
                                loanToDelete = null,
                                showLoanFormDialog = if (deletedEditingLoan) false else current.showLoanFormDialog,
                                editingLoanId = if (deletedEditingLoan) null else current.editingLoanId,
                                personNameInput = if (deletedEditingLoan) "" else current.personNameInput,
                                amountLentInput = if (deletedEditingLoan) "" else current.amountLentInput,
                                amountToReceiveInput = if (deletedEditingLoan) "" else current.amountToReceiveInput,
                                isAmountToReceiveManuallyEdited = if (deletedEditingLoan) false else current.isAmountToReceiveManuallyEdited,
                                noteInput = if (deletedEditingLoan) "" else current.noteInput,
                                selectedSavingCategory = if (deletedEditingLoan) "" else current.selectedSavingCategory,
                                creationPaymentMode = if (deletedEditingLoan) LoanCreationPaymentMode.NO_DATE else current.creationPaymentMode,
                                creationSingleDueDateInput = if (deletedEditingLoan) today else current.creationSingleDueDateInput,
                                draftInstallments = if (deletedEditingLoan) emptyList() else current.draftInstallments,
                                draftInstallmentDateInput = if (deletedEditingLoan) today else current.draftInstallmentDateInput,
                                draftInstallmentAmountInput = if (deletedEditingLoan) "" else current.draftInstallmentAmountInput,
                                showDiscardEditLoanDialog = if (deletedEditingLoan) false else current.showDiscardEditLoanDialog,
                                editingScheduleLoan = if (current.editingScheduleLoan?.id == loan.id) null else current.editingScheduleLoan,
                                payingLoan = if (current.payingLoan?.id == loan.id) null else current.payingLoan,
                                editingPaymentId = if (current.payingLoan?.id == loan.id) null else current.editingPaymentId,
                                historyLoanId = if (current.historyLoanId == loan.id) null else current.historyLoanId,
                                paymentToDelete = if (current.paymentToDelete?.first?.id == loan.id) null else current.paymentToDelete,
                                errorMessage = null,
                                toastMessage = MSG_LOAN_DELETED
                            )
                        }
                    }
                    is LoanResult.Error -> {
                        _uiState.update {
                            it.copy(
                                loanToDelete = null,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loanToDelete = null,
                        errorMessage = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "No se pudo eliminar el préstamo"
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun resetForm() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                todayIso = today,
                showLoanFormDialog = false,
                editingLoanId = null,
                personNameInput = "",
                amountLentInput = "",
                amountToReceiveInput = "",
                isAmountToReceiveManuallyEdited = false,
                noteInput = "",
                selectedSavingCategory = "",
                creationPaymentMode = LoanCreationPaymentMode.NO_DATE,
                creationSingleDueDateInput = today,
                draftInstallments = emptyList(),
                draftInstallmentDateInput = today,
                draftInstallmentAmountInput = "",
                errorMessage = null,
                showDiscardEditLoanDialog = false
            )
        }
    }

    private fun rebalanceEditDraftInstallments(
        drafts: List<LoanInstallmentDraft>,
        amountToReceiveInput: String,
        existingLoan: Loan?
    ): List<LoanInstallmentInput> {
        val rawInputs = drafts.map {
            LoanInstallmentInput(
                dueDate = it.dueDate,
                expectedAmountInput = it.expectedAmountInput,
                persistedId = it.persistedId,
                paymentAmount = it.paymentAmount
            )
        }
        if (drafts.isEmpty()) return rawInputs
        val newAmountToReceive = parseLoanAmount(amountToReceiveInput) ?: return rawInputs
        if (!newAmountToReceive.isFinite() || newAmountToReceive <= 0.0) return rawInputs

        val existingPaidDec = existingLoan?.payments
            ?.filter { it.amount.isFinite() && it.amount > 0.0 }
            ?.fold(BigDecimal.ZERO) { acc, p -> acc.add(BigDecimal.valueOf(p.amount)) }
            ?: drafts
                .filter { it.paymentAmount.isFinite() && it.paymentAmount > 0.0 }
                .fold(BigDecimal.ZERO) { acc, d -> acc.add(BigDecimal.valueOf(d.paymentAmount)) }

        val newPendingTarget = BigDecimal.valueOf(newAmountToReceive).subtract(existingPaidDec)
        if (newPendingTarget < BigDecimal.ZERO) return rawInputs

        data class MutableDraft(
            val dueDate: String,
            var expected: BigDecimal,
            val persistedId: Long,
            val paid: BigDecimal
        ) {
            val remaining: BigDecimal
                get() = expected.subtract(paid).max(BigDecimal.ZERO)
        }

        val items = mutableListOf<MutableDraft>()
        for (d in drafts) {
            val exp = parseLoanAmount(d.expectedAmountInput) ?: return rawInputs
            if (!exp.isFinite() || exp <= 0.0) return rawInputs
            val paidDec = if (d.paymentAmount.isFinite() && d.paymentAmount > 0.0) {
                BigDecimal.valueOf(d.paymentAmount)
            } else {
                BigDecimal.ZERO
            }
            items.add(
                MutableDraft(
                    dueDate = d.dueDate,
                    expected = BigDecimal.valueOf(exp),
                    persistedId = d.persistedId,
                    paid = paidDec
                )
            )
        }

        val currentRemaining = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.remaining) }
        val cmp = newPendingTarget.compareTo(currentRemaining)
        if (cmp == 0) return rawInputs

        if (cmp > 0) {
            val delta = newPendingTarget.subtract(currentRemaining)
            val targetIdx = items.indexOfLast { it.remaining > BigDecimal.ZERO }.takeIf { it >= 0 } ?: items.lastIndex
            items[targetIdx].expected = items[targetIdx].expected.add(delta)
        } else {
            var toReduce = currentRemaining.subtract(newPendingTarget)
            val keptReversed = mutableListOf<MutableDraft>()
            for (item in items.asReversed()) {
                val rem = item.remaining
                if (toReduce <= BigDecimal.ZERO || rem <= BigDecimal.ZERO) {
                    keptReversed.add(item)
                } else if (rem > toReduce) {
                    item.expected = item.expected.subtract(toReduce)
                    toReduce = BigDecimal.ZERO
                    keptReversed.add(item)
                } else {
                    toReduce = toReduce.subtract(rem)
                    if (item.paid > BigDecimal.ZERO) {
                        item.expected = item.paid
                        keptReversed.add(item)
                    }
                }
            }
            items.clear()
            items.addAll(keptReversed.asReversed())
        }

        return items.map { item ->
            LoanInstallmentInput(
                dueDate = item.dueDate,
                expectedAmountInput = formatDraftDecimal(item.expected),
                persistedId = item.persistedId,
                paymentAmount = item.paid.toDouble()
            )
        }
    }

    fun saveLoan() {
        if (isSaving) return
        val state = _uiState.value
        val editingId = state.editingLoanId
        val selectedCreationMode = state.creationPaymentMode
        isSaving = true
        viewModelScope.launch {
            try {
                val installmentInputs = when {
                    editingId != null -> rebalanceEditDraftInstallments(
                        drafts = state.draftInstallments,
                        amountToReceiveInput = state.amountToReceiveInput,
                        existingLoan = state.loans.find { it.id == editingId }
                    )
                    selectedCreationMode == LoanCreationPaymentMode.SINGLE_DATE -> listOf(
                        LoanInstallmentInput(
                            dueDate = state.creationSingleDueDateInput,
                            expectedAmountInput = state.amountToReceiveInput
                        )
                    )
                    selectedCreationMode == LoanCreationPaymentMode.INSTALLMENTS -> emptyList()
                    else -> state.draftInstallments.map {
                        LoanInstallmentInput(
                            dueDate = it.dueDate,
                            expectedAmountInput = it.expectedAmountInput,
                            persistedId = it.persistedId,
                            paymentAmount = it.paymentAmount
                        )
                    }
                }
                val (result, createdLoan) = if (editingId == null) {
                    loanUseCase.saveLoanReturningCreated(
                        personNameInput = state.personNameInput,
                        amountLentInput = state.amountLentInput,
                        amountToReceiveInput = state.amountToReceiveInput,
                        noteInput = state.noteInput,
                        savingCategoryInput = state.selectedSavingCategory,
                        installmentInputs = installmentInputs
                    )
                } else {
                    loanUseCase.updateLoan(
                        loanId = editingId,
                        personNameInput = state.personNameInput,
                        amountLentInput = state.amountLentInput,
                        amountToReceiveInput = state.amountToReceiveInput,
                        noteInput = state.noteInput,
                        savingCategoryInput = state.selectedSavingCategory,
                        installmentInputs = installmentInputs
                    ) to null
                }
                when (result) {
                    is LoanResult.Success -> {
                        val today = loanUseCase.todayIso()
                        val shouldOpenSchedule = editingId == null &&
                            selectedCreationMode == LoanCreationPaymentMode.INSTALLMENTS &&
                            createdLoan != null
                        _uiState.update {
                            it.copy(
                                todayIso = today,
                                showLoanFormDialog = false,
                                editingLoanId = null,
                                personNameInput = "",
                                amountLentInput = "",
                                amountToReceiveInput = "",
                                isAmountToReceiveManuallyEdited = false,
                                noteInput = "",
                                selectedSavingCategory = "",
                                creationPaymentMode = LoanCreationPaymentMode.NO_DATE,
                                creationSingleDueDateInput = today,
                                draftInstallments = emptyList(),
                                draftInstallmentDateInput = today,
                                draftInstallmentAmountInput = "",
                                errorMessage = null,
                                showDiscardEditLoanDialog = false,
                                editingScheduleLoan = if (shouldOpenSchedule) createdLoan else it.editingScheduleLoan,
                                scheduleInstallments = if (shouldOpenSchedule) emptyList() else it.scheduleInstallments,
                                scheduleDateInput = if (shouldOpenSchedule) today else it.scheduleDateInput,
                                scheduleAmountInput = if (shouldOpenSchedule) "" else it.scheduleAmountInput,
                                scheduleErrorMessage = if (shouldOpenSchedule) null else it.scheduleErrorMessage,
                                toastMessage = if (editingId == null) MSG_LOAN_SAVED else MSG_LOAN_UPDATED
                            )
                        }
                    }
                    is LoanResult.Error -> {
                        _uiState.update { it.copy(errorMessage = result.message) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "No se pudo guardar el préstamo"
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun saveLoanSchedule() {
        if (isSaving) return
        val state = _uiState.value
        val loan = state.editingScheduleLoan ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = loanUseCase.updateLoanInstallments(
                    loanId = loan.id,
                    installmentInputs = state.scheduleInstallments.map {
                        LoanInstallmentInput(
                            dueDate = it.dueDate,
                            expectedAmountInput = it.expectedAmountInput,
                            persistedId = it.persistedId,
                            paymentAmount = it.paymentAmount
                        )
                    }
                )) {
                    is LoanResult.Success -> {
                        val today = loanUseCase.todayIso()
                        _uiState.update {
                            it.copy(
                                todayIso = today,
                                editingScheduleLoan = null,
                                scheduleInstallments = emptyList(),
                                editingScheduleInstallmentDraftId = null,
                                scheduleDateInput = today,
                                scheduleAmountInput = "",
                                scheduleErrorMessage = null,
                                showDiscardScheduleChangesDialog = false,
                                toastMessage = MSG_SCHEDULE_UPDATED
                            )
                        }
                    }
                    is LoanResult.Error -> {
                        _uiState.update { it.copy(scheduleErrorMessage = result.message) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scheduleErrorMessage = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "No se pudo guardar el calendario de pagos"
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    private fun formatDraftAmount(amount: Double): String {
        if (!amount.isFinite()) return "0"
        return formatDraftDecimal(BigDecimal.valueOf(amount))
    }

    private fun formatDraftDecimal(decimal: BigDecimal): String {
        val bd = decimal.stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }
}
