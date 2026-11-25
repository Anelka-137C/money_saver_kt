package com.example.money_saver_android.data.repository

import com.example.money_saver_android.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getExpensesByPeriod(periodId: Long): Flow<List<Expense>> =
        getAllExpenses().map { list -> list.filter { it.periodId == periodId } }
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(id: Long)
    suspend fun reassignBudgetCategory(oldName: String, newName: String) {}
    suspend fun reassignSavingCategory(oldName: String, newName: String) {}
}
