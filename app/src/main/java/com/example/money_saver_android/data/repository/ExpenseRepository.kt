package com.example.money_saver_android.data.repository

import com.example.money_saver_android.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
}
