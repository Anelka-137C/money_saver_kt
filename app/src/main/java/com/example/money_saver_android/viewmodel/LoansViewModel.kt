package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
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

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val totalReceivable: Double = 0.0,
    val totalPaid: Double = 0.0,
    val savingsBalances: List<SavingsCategoryBalance> = emptyList(),
    val personNameInput: String = "",
    val amountLentInput: String = "",
    val amountToReceiveInput: String = "",
    val isAmountToReceiveManuallyEdited: Boolean = false,
    val noteInput: String = "",
    val selectedSavingCategory: String = "",
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class LoansViewModel(
    private val loanUseCase: LoanUseCase
) : ViewModel() {

    companion object {
        const val MSG_LOAN_SAVED = "loans.messages.saved"
    }

    private val _uiState = MutableStateFlow(LoansUiState())
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

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                personNameInput = "",
                amountLentInput = "",
                amountToReceiveInput = "",
                isAmountToReceiveManuallyEdited = false,
                noteInput = "",
                selectedSavingCategory = "",
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
                    savingCategoryInput = state.selectedSavingCategory
                )) {
                    is LoanResult.Success -> {
                        _uiState.update {
                            it.copy(
                                personNameInput = "",
                                amountLentInput = "",
                                amountToReceiveInput = "",
                                isAmountToReceiveManuallyEdited = false,
                                noteInput = "",
                                selectedSavingCategory = "",
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
}
