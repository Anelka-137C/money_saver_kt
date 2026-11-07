package com.example.money_saver_android.data

import kotlinx.coroutines.flow.Flow

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val subcategory: String
)

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
}
