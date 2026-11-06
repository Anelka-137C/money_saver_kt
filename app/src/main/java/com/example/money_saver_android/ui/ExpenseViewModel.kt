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

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val amountInput: String = "",
    val dateInput: String = "",
    val descriptionInput: String = "",
    val categoryInput: String = "",
    val subcategoryInput: String = "",
    val errorMessage: String? = null
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

    fun saveExpense() {
        val current = _uiState.value
        val amount = current.amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _uiState.update { it.copy(errorMessage = "El monto debe ser mayor a cero") }
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
        }
    }
}
