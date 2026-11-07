package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditExpenseState(
    val expenseId: Long = 0,
    val amount: String = "",
    val date: String = "",
    val description: String = "",
    val category: String = "",
    val subcategory: String = "",
    val errorMessage: String? = null
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val amountInput: String = "",
    val dateInput: String = "",
    val descriptionInput: String = "",
    val categoryInput: String = "",
    val subcategoryInput: String = "",
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val editingExpense: EditExpenseState? = null,
    val expenseToDelete: Expense? = null
)

class ExpenseViewModel(
    private val expenseUseCase: ExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseUseCase.getExpenses().collect { list ->
                _uiState.update { it.copy(expenses = list) }
            }
        }
    }

    fun onAmountChange(amount: String) {
        _uiState.update { it.copy(amountInput = amount) }
    }

    fun onDateChange(date: String) {
        _uiState.update { it.copy(dateInput = date) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(descriptionInput = description) }
    }

    fun onCategoryChange(category: String, subcategory: String) {
        _uiState.update { it.copy(categoryInput = category, subcategoryInput = subcategory) }
    }

    fun startEditing(expense: Expense) {
        val amountStr = if (expense.amount % 1.0 == 0.0) {
            expense.amount.toLong().toString()
        } else {
            expense.amount.toString()
        }
        _uiState.update {
            it.copy(
                editingExpense = EditExpenseState(
                    expenseId = expense.id,
                    amount = amountStr,
                    date = expense.date,
                    description = expense.description,
                    category = expense.category,
                    subcategory = expense.subcategory
                )
            )
        }
    }

    fun onEditAmountChange(amount: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(amount = amount))
        }
    }

    fun onEditDateChange(date: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(date = date))
        }
    }

    fun onEditDescriptionChange(description: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(description = description))
        }
    }

    fun onEditCategoryChange(category: String, subcategory: String) {
        _uiState.update { state ->
            state.copy(
                editingExpense = state.editingExpense?.copy(
                    category = category,
                    subcategory = subcategory
                )
            )
        }
    }

    fun confirmEdit() {
        val editState = _uiState.value.editingExpense ?: return
        viewModelScope.launch {
            when (val result = expenseUseCase.updateExpense(
                id = editState.expenseId,
                amountInput = editState.amount,
                dateInput = editState.date,
                descriptionInput = editState.description,
                categoryInput = editState.category,
                subcategoryInput = editState.subcategory
            )) {
                is ExpenseResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            editingExpense = state.editingExpense?.copy(
                                errorMessage = result.message
                            )
                        )
                    }
                }
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            editingExpense = null,
                            toastMessage = "Gasto guardado"
                        )
                    }
                }
            }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun requestDeleteExpense(expense: Expense) {
        _uiState.update { it.copy(expenseToDelete = expense) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(expenseToDelete = null) }
    }

    fun confirmDeleteExpense() {
        val expense = _uiState.value.expenseToDelete ?: return
        viewModelScope.launch {
            when (expenseUseCase.deleteExpense(expense.id)) {
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            expenseToDelete = null,
                            toastMessage = "Gasto eliminado"
                        )
                    }
                }
                is ExpenseResult.Error -> {
                    // No-op for now
                }
            }
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun saveExpense() {
        val current = _uiState.value
        viewModelScope.launch {
            when (val result = expenseUseCase.saveExpense(
                amountInput = current.amountInput,
                dateInput = current.dateInput,
                descriptionInput = current.descriptionInput,
                categoryInput = current.categoryInput,
                subcategoryInput = current.subcategoryInput
            )) {
                is ExpenseResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            amountInput = "",
                            descriptionInput = "",
                            categoryInput = "",
                            subcategoryInput = "",
                            errorMessage = null,
                            toastMessage = "Gasto guardado"
                        )
                    }
                }
            }
        }
    }
}
