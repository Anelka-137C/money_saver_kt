package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanInstallmentValidationResult
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
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
    val persistedId: Long = 0L
)

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val totalReceivable: Double = 0.0,
    val totalPaid: Double = 0.0,
    val savingsBalances: List<SavingsCategoryBalance> = emptyList(),
    val todayIso: String = "",
    val personNameInput: String = "",
    val amountLentInput: String = "",
    val amountToReceiveInput: String = "",
    val isAmountToReceiveManuallyEdited: Boolean = false,
    val noteInput: String = "",
    val selectedSavingCategory: String = "",
    val draftInstallments: List<LoanInstallmentDraft> = emptyList(),
    val draftInstallmentDateInput: String = "",
    val draftInstallmentAmountInput: String = "",
    val errorMessage: String? = null,
    val editingScheduleLoan: Loan? = null,
    val scheduleInstallments: List<LoanInstallmentDraft> = emptyList(),
    val scheduleDateInput: String = "",
    val scheduleAmountInput: String = "",
    val scheduleErrorMessage: String? = null,
    val toastMessage: String? = null
)

class LoansViewModel(
    private val loanUseCase: LoanUseCase
) : ViewModel() {

    companion object {
        const val MSG_LOAN_SAVED = "loans.messages.saved"
        const val MSG_SCHEDULE_UPDATED = "loans.messages.schedule_updated"
    }

    private val initialToday = loanUseCase.todayIso()
    private val _uiState = MutableStateFlow(
        LoansUiState(
            todayIso = initialToday,
            draftInstallmentDateInput = initialToday,
            scheduleDateInput = initialToday
        )
    )
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    private var isSaving: Boolean = false

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
                            totalReceivable = loanUseCase.calculateTotalReceivable(loanList)
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
            }.collect { (allSavings, allExpenses, allLoans, savingCategories) ->
                val balances = calculateSavingsNetBalances(
                    savings = allSavings,
                    expenses = allExpenses,
                    categories = savingCategories,
                    loans = allLoans
                )
                _uiState.update { current ->
                    current.copy(savingsBalances = balances)
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
                    persistedId = inst.id
                )
            }
        _uiState.update {
            it.copy(
                todayIso = today,
                editingScheduleLoan = loan,
                scheduleInstallments = existingDrafts,
                scheduleDateInput = today,
                scheduleAmountInput = "",
                scheduleErrorMessage = null
            )
        }
    }

    fun closeScheduleDialog() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                editingScheduleLoan = null,
                scheduleInstallments = emptyList(),
                scheduleDateInput = today,
                scheduleAmountInput = "",
                scheduleErrorMessage = null
            )
        }
    }

    fun onScheduleDateChange(value: String) {
        _uiState.update { it.copy(scheduleDateInput = value, scheduleErrorMessage = null) }
    }

    fun onScheduleAmountChange(value: String) {
        _uiState.update { it.copy(scheduleAmountInput = value, scheduleErrorMessage = null) }
    }

    fun addScheduleInstallment() {
        val state = _uiState.value
        when (val validation = loanUseCase.validateInstallmentDraft(
            dueDateInput = state.scheduleDateInput,
            expectedAmountInput = state.scheduleAmountInput
        )) {
            is LoanInstallmentValidationResult.Error -> {
                _uiState.update { it.copy(scheduleErrorMessage = validation.message) }
            }
            is LoanInstallmentValidationResult.Valid -> {
                val nextDraft = LoanInstallmentDraft(
                    dueDate = validation.installment.dueDate,
                    expectedAmountInput = formatDraftAmount(validation.installment.expectedAmount),
                    persistedId = 0L
                )
                _uiState.update { current ->
                    current.copy(
                        scheduleInstallments = (current.scheduleInstallments + nextDraft).sortedBy { it.dueDate },
                        scheduleAmountInput = "",
                        scheduleErrorMessage = null
                    )
                }
            }
        }
    }

    fun removeScheduleInstallment(draftId: String) {
        _uiState.update { current ->
            current.copy(
                scheduleInstallments = current.scheduleInstallments.filterNot { it.draftId == draftId },
                scheduleErrorMessage = null
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun resetForm() {
        val today = loanUseCase.todayIso()
        _uiState.update {
            it.copy(
                todayIso = today,
                personNameInput = "",
                amountLentInput = "",
                amountToReceiveInput = "",
                isAmountToReceiveManuallyEdited = false,
                noteInput = "",
                selectedSavingCategory = "",
                draftInstallments = emptyList(),
                draftInstallmentDateInput = today,
                draftInstallmentAmountInput = "",
                errorMessage = null
            )
        }
    }

    fun saveLoan() {
        if (isSaving) return
        val state = _uiState.value
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = loanUseCase.saveLoan(
                    personNameInput = state.personNameInput,
                    amountLentInput = state.amountLentInput,
                    amountToReceiveInput = state.amountToReceiveInput,
                    noteInput = state.noteInput,
                    savingCategoryInput = state.selectedSavingCategory,
                    installmentInputs = state.draftInstallments.map {
                        LoanInstallmentInput(
                            dueDate = it.dueDate,
                            expectedAmountInput = it.expectedAmountInput,
                            persistedId = it.persistedId
                        )
                    }
                )) {
                    is LoanResult.Success -> {
                        val today = loanUseCase.todayIso()
                        _uiState.update {
                            it.copy(
                                todayIso = today,
                                personNameInput = "",
                                amountLentInput = "",
                                amountToReceiveInput = "",
                                isAmountToReceiveManuallyEdited = false,
                                noteInput = "",
                                selectedSavingCategory = "",
                                draftInstallments = emptyList(),
                                draftInstallmentDateInput = today,
                                draftInstallmentAmountInput = "",
                                errorMessage = null,
                                toastMessage = MSG_LOAN_SAVED
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
                            persistedId = it.persistedId
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
                                scheduleDateInput = today,
                                scheduleAmountInput = "",
                                scheduleErrorMessage = null,
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
        val bd = BigDecimal.valueOf(amount).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }
}
