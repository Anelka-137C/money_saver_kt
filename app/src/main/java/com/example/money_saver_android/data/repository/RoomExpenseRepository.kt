package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.local.ExpenseDao
import com.example.money_saver_android.data.local.ExpenseEntity
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override fun getExpensesByPeriod(periodId: Long): Flow<List<Expense>> {
        return dao.getByPeriod(periodId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertExpense(expense: Expense) {
        dao.insert(expense.toEntity())
    }

    override suspend fun updateExpense(expense: Expense) {
        dao.update(expense.toEntity())
    }

    override suspend fun deleteExpense(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun reassignBudgetCategory(oldName: String, newName: String) {
        dao.reassignBudgetCategory(oldCategoryName = oldName, newCategoryName = newName)
    }

    override suspend fun reassignSavingCategory(oldName: String, newName: String) {
        dao.reassignSavingCategory(oldCategoryName = oldName, newCategoryName = newName)
    }

    override suspend fun replaceAllExpenses(expenses: List<Expense>) {
        dao.replaceAll(expenses.map { it.toEntity() })
    }

    private fun ExpenseEntity.toDomain(): Expense = Expense(
        id = id,
        amount = amount,
        date = date,
        description = description,
        category = category,
        subcategory = subcategory.orEmpty(),
        periodId = periodId ?: 0L,
        budgetId = budgetId ?: 0L,
        budgetCategory = budgetCategory.orEmpty(),
        fundingSource = ExpenseFundingSource.fromStorage(fundingSource),
        savingCategory = savingCategory.orEmpty()
    )

    private fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
        id = id,
        amount = amount,
        date = date,
        description = description,
        category = category,
        subcategory = subcategory.takeIf { it.isNotBlank() },
        periodId = periodId.takeIf { it > 0L },
        budgetId = budgetId.takeIf { it > 0L },
        budgetCategory = budgetCategory.takeIf { it.isNotBlank() },
        fundingSource = fundingSource.name,
        savingCategory = savingCategory.takeIf { it.isNotBlank() }
    )
}
