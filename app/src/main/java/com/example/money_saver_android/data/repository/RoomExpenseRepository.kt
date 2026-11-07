package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.local.ExpenseDao
import com.example.money_saver_android.data.local.ExpenseEntity
import com.example.money_saver_android.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> {
        return dao.getAll().map { list ->
            list.map { entity ->
                Expense(
                    id = entity.id,
                    amount = entity.amount,
                    date = entity.date,
                    description = entity.description,
                    category = entity.category,
                    subcategory = entity.subcategory
                )
            }
        }
    }

    override suspend fun insertExpense(expense: Expense) {
        dao.insert(
            ExpenseEntity(
                id = expense.id,
                amount = expense.amount,
                date = expense.date,
                description = expense.description,
                category = expense.category,
                subcategory = expense.subcategory
            )
        )
    }

    override suspend fun updateExpense(expense: Expense) {
        dao.update(
            ExpenseEntity(
                id = expense.id,
                amount = expense.amount,
                date = expense.date,
                description = expense.description,
                category = expense.category,
                subcategory = expense.subcategory
            )
        )
    }

    override suspend fun deleteExpense(id: Long) {
        dao.deleteById(id)
    }
}
