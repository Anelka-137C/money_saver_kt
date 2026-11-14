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
        subcategoryInput: String = ""
    ): ExpenseResult = withValidatedExpense(
        amountInput = amountInput,
        dateInput = dateInput,
        descriptionInput = descriptionInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput
    ) { expense ->
        repository.insertExpense(expense)
    }

    suspend fun updateExpense(
        id: Long,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String = ""
    ): ExpenseResult = withValidatedExpense(
        id = id,
        amountInput = amountInput,
        dateInput = dateInput,
        descriptionInput = descriptionInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput
    ) { expense ->
        repository.updateExpense(expense)
    }

    private suspend fun withValidatedExpense(
        id: Long = 0,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String,
        onValid: suspend (Expense) -> Unit
    ): ExpenseResult {
        val amount = amountInput.toDoubleOrNull() ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return ExpenseResult.Error("El monto debe ser mayor a cero")
        }
        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return ExpenseResult.Error("Debes seleccionar una categoría")
        }

        onValid(
            Expense(
                id = id,
                amount = amount,
                date = dateInput,
                description = descriptionInput,
                category = trimmedCategory,
                subcategory = subcategoryInput.trim()
            )
        )
        return ExpenseResult.Success
    }

    suspend fun deleteExpense(id: Long): ExpenseResult {
        repository.deleteExpense(id)
        return ExpenseResult.Success
    }
}
