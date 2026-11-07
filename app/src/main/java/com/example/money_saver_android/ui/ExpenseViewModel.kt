package com.example.money_saver_android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Expense
import com.example.money_saver_android.data.ExpenseRepository
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
    val editingExpense: EditExpenseState? = null
)

class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllExpenses().collect { list ->
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
        val amount = editState.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _uiState.update { state ->
                state.copy(
                    editingExpense = state.editingExpense?.copy(
                        errorMessage = "El monto debe ser mayor a cero"
                    )
                )
            }
            return
        }
        if (editState.category.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    editingExpense = state.editingExpense?.copy(
                        errorMessage = "Debes seleccionar una categoría"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            repository.updateExpense(
                Expense(
                    id = editState.expenseId,
                    amount = amount,
                    date = editState.date,
                    description = editState.description,
                    category = editState.category,
                    subcategory = editState.subcategory
                )
            )
            _uiState.update { it.copy(editingExpense = null, toastMessage = "Gasto guardado") }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun saveExpense() {
        val current = _uiState.value
        val amount = current.amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _uiState.update { it.copy(errorMessage = "El monto debe ser mayor a cero") }
            return
        }
        if (current.categoryInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Debes seleccionar una categoría") }
            return
        }
        _uiState.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            repository.insertExpense(
                Expense(
                    amount = amount,
                    date = current.dateInput,
                    description = current.descriptionInput,
                    category = current.categoryInput,
                    subcategory = current.subcategoryInput
                )
            )
            _uiState.update {
                it.copy(
                    amountInput = "",
                    descriptionInput = "",
                    categoryInput = "",
                    subcategoryInput = "",
                    toastMessage = "Gasto guardado"
                )
            }
        }
    }
}
