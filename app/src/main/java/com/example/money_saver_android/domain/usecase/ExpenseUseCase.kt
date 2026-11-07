package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.domain.model.Expense
import kotlinx.coroutines.flow.Flow

sealed class ExpenseResult {
    object Success : ExpenseResult()
    data class Error(val message: String) : ExpenseResult()
}

class ExpenseUseCase(
    private val repository: ExpenseRepository
) {
    fun getExpenses(): Flow<List<Expense>> {
        return repository.getAllExpenses()
    }

    suspend fun saveExpense(
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String
    ): ExpenseResult {
        val amount = amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            return ExpenseResult.Error("El monto debe ser mayor a cero")
        }
        if (categoryInput.isBlank()) {
            return ExpenseResult.Error("Debes seleccionar una categoría")
        }

        repository.insertExpense(
            Expense(
                amount = amount,
                date = dateInput,
                description = descriptionInput,
                category = categoryInput,
                subcategory = subcategoryInput
            )
        )
        return ExpenseResult.Success
    }

    suspend fun updateExpense(
        id: Long,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String
    ): ExpenseResult {
        val amount = amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            return ExpenseResult.Error("El monto debe ser mayor a cero")
        }
        if (categoryInput.isBlank()) {
            return ExpenseResult.Error("Debes seleccionar una categoría")
        }

        repository.updateExpense(
            Expense(
                id = id,
                amount = amount,
                date = dateInput,
                description = descriptionInput,
                category = categoryInput,
                subcategory = subcategoryInput
            )
        )
        return ExpenseResult.Success
    }
}
