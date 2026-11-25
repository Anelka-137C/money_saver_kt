package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

sealed class ExpenseResult {
    object Success : ExpenseResult()
    data class Error(val message: String) : ExpenseResult()
}

data class CategoryExpenseItem(
    val categoryName: String,
    val icon: String,
    val colorHex: String,
    val spentAmount: Double,
    val progress: Float,
    val percent: Int
)

class ExpenseUseCase(
    private val repository: ExpenseRepository,
    private val budgetRepository: BudgetRepository? = null,
    private val savingsRepository: SavingsRepository? = null,
    private val expenseMutex: Mutex = FinancialAllocationLock.mutex
) {
    val requiresBudgetConditioning: Boolean
        get() = budgetRepository != null

    fun getExpenses(): Flow<List<Expense>> {
        return repository.getAllExpenses()
    }

    fun getExpensesByPeriod(periodId: Long): Flow<List<Expense>> {
        return repository.getExpensesByPeriod(periodId)
    }

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> {
        return budgetRepository?.getBudgetsByPeriod(periodId) ?: emptyFlow()
    }

    fun getAllSavings(): Flow<List<Saving>> {
        return savingsRepository?.getAllSavings() ?: emptyFlow()
    }

    fun getSavingCategories(): Flow<List<Category>> {
        return savingsRepository?.getAllCategories() ?: emptyFlow()
    }

    suspend fun saveExpense(
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long? = null,
        budgetId: Long? = null,
        budgetCategoryInput: String = "",
        fundingSource: ExpenseFundingSource = ExpenseFundingSource.BUDGET,
        savingCategoryInput: String = ""
    ): ExpenseResult = withValidatedExpense(
        amountInput = amountInput,
        dateInput = dateInput,
        descriptionInput = descriptionInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput,
        periodId = periodId,
        budgetId = budgetId,
        budgetCategoryInput = budgetCategoryInput,
        fundingSource = fundingSource,
        savingCategoryInput = savingCategoryInput
    ) { expense ->
        repository.insertExpense(expense)
        ExpenseResult.Success
    }

    suspend fun updateExpense(
        id: Long,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long? = null,
        budgetId: Long? = null,
        budgetCategoryInput: String = "",
        fundingSource: ExpenseFundingSource = ExpenseFundingSource.BUDGET,
        savingCategoryInput: String = ""
    ): ExpenseResult = withValidatedExpense(
        id = id,
        amountInput = amountInput,
        dateInput = dateInput,
        descriptionInput = descriptionInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput,
        periodId = periodId,
        budgetId = budgetId,
        budgetCategoryInput = budgetCategoryInput,
        fundingSource = fundingSource,
        savingCategoryInput = savingCategoryInput
    ) { expense ->
        if (id <= 0L || repository.getAllExpenses().first().none { it.id == id }) {
            return@withValidatedExpense ExpenseResult.Error("Gasto no encontrado")
        }
        repository.updateExpense(expense)
        ExpenseResult.Success
    }

    private suspend fun withValidatedExpense(
        id: Long = 0,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String,
        periodId: Long?,
        budgetId: Long?,
        budgetCategoryInput: String,
        fundingSource: ExpenseFundingSource,
        savingCategoryInput: String,
        onValid: suspend (Expense) -> ExpenseResult
    ): ExpenseResult {
        if (fundingSource == ExpenseFundingSource.SAVINGS) {
            return withValidatedSavingsExpense(
                id = id,
                amountInput = amountInput,
                dateInput = dateInput,
                descriptionInput = descriptionInput,
                categoryInput = categoryInput,
                subcategoryInput = subcategoryInput,
                periodId = periodId,
                savingCategoryInput = savingCategoryInput,
                onValid = onValid
            )
        }

        val periodBudgets = if (budgetRepository != null) {
            if (periodId == null || periodId <= 0L) {
                return ExpenseResult.Error("Período no seleccionado")
            }
            budgetRepository.getBudgetsByPeriod(periodId).first().also {
                if (it.isEmpty()) return ExpenseResult.Error("Se requiere definir un presupuesto activo en el período")
            }
        } else null

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return ExpenseResult.Error("El monto debe ser mayor a cero")
        }

        val matchedBudget = if (periodBudgets != null) {
            val trimmedBudgetCategory = budgetCategoryInput.trim()
            if (budgetId == null || budgetId <= 0L || trimmedBudgetCategory.isEmpty()) {
                return ExpenseResult.Error("Debes seleccionar un presupuesto")
            }
            periodBudgets.find {
                it.id == budgetId && it.category.trim().equals(trimmedBudgetCategory, ignoreCase = true)
            } ?: return ExpenseResult.Error("El presupuesto seleccionado no corresponde al período activo")
        } else null

        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return ExpenseResult.Error("Debes seleccionar una categoría")
        }

        val trimmedDate = dateInput.trim()
        if (!isValidExpenseDate(trimmedDate)) {
            return ExpenseResult.Error("Fecha inválida")
        }

        return onValid(
            Expense(
                id = id,
                amount = amount,
                date = trimmedDate,
                description = descriptionInput,
                category = trimmedCategory,
                subcategory = subcategoryInput.trim(),
                periodId = periodId ?: 0L,
                budgetId = matchedBudget?.id ?: (budgetId ?: 0L),
                budgetCategory = matchedBudget?.category ?: budgetCategoryInput.trim(),
                fundingSource = ExpenseFundingSource.BUDGET,
                savingCategory = ""
            )
        )
    }

    private suspend fun withValidatedSavingsExpense(
        id: Long,
        amountInput: String,
        dateInput: String,
        descriptionInput: String,
        categoryInput: String,
        subcategoryInput: String,
        periodId: Long?,
        savingCategoryInput: String,
        onValid: suspend (Expense) -> ExpenseResult
    ): ExpenseResult {
        val savingsRepo = savingsRepository
            ?: return ExpenseResult.Error("Módulo de ahorros no configurado")

        if (budgetRepository != null && (periodId == null || periodId <= 0L)) {
            return ExpenseResult.Error("Período no seleccionado")
        }

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return ExpenseResult.Error("El monto debe ser mayor a cero")
        }

        val trimmedSavingCategory = savingCategoryInput.trim()
        if (trimmedSavingCategory.isEmpty()) {
            return ExpenseResult.Error("Debes seleccionar una categoría de ahorro")
        }

        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return ExpenseResult.Error("Debes seleccionar una categoría")
        }

        val trimmedDate = dateInput.trim()
        if (!isValidExpenseDate(trimmedDate)) {
            return ExpenseResult.Error("Fecha inválida")
        }

        return expenseMutex.withLock {
            val allSavings = savingsRepo.getAllSavings().first()
            val allExpenses = repository.getAllExpenses().first()
            val matchingSavings = allSavings.filter {
                it.amount.isFinite() && it.amount > 0.0 &&
                    it.category.trim().equals(trimmedSavingCategory, ignoreCase = true)
            }
            val totalSaved = matchingSavings.map { it.amount }.sumBigDecimal()
            val totalSpent = allExpenses
                .filter {
                    it.fundingSource == ExpenseFundingSource.SAVINGS &&
                        (id <= 0L || it.id != id) &&
                        it.amount.isFinite() && it.amount > 0.0 &&
                        it.savingCategory.trim().equals(trimmedSavingCategory, ignoreCase = true)
                }
                .map { it.amount }
                .sumBigDecimal()
            val available = totalSaved.subtract(totalSpent).max(BigDecimal.ZERO)
            if (matchingSavings.isEmpty() || BigDecimal.valueOf(amount) > available) {
                return@withLock ExpenseResult.Error("Saldo insuficiente en la categoría de ahorro")
            }

            val canonicalSavingCategory = matchingSavings.first().category.trim()
            onValid(
                Expense(
                    id = id,
                    amount = amount,
                    date = trimmedDate,
                    description = descriptionInput,
                    category = trimmedCategory,
                    subcategory = subcategoryInput.trim(),
                    periodId = periodId ?: 0L,
                    budgetId = 0L,
                    budgetCategory = "",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = canonicalSavingCategory
                )
            )
        }
    }

    private fun parseAmountInput(rawInput: String): Double? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val normalized = when {
            trimmed.contains(',') && trimmed.contains('.') -> {
                if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                    trimmed.replace(".", "").replace(",", ".")
                } else {
                    trimmed.replace(",", "")
                }
            }
            trimmed.contains(',') -> {
                if (Regex("^\\d{1,3}(,\\d{3})+$").matches(trimmed)) {
                    trimmed.replace(",", "")
                } else if (trimmed.count { it == ',' } == 1) {
                    trimmed.replace(",", ".")
                } else {
                    return null
                }
            }
            else -> trimmed
        }
        return normalized.toDoubleOrNull()
    }

    private fun isValidExpenseDate(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return false
        val pattern = when {
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateStr) -> "yyyy-MM-dd"
            Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$").matches(dateStr) -> "yyyy-MM-dd HH:mm"
            else -> return false
        }
        return try {
            val parser = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
            val pos = ParsePosition(0)
            val parsed = parser.parse(dateStr, pos)
            parsed != null && pos.index == dateStr.length && pos.errorIndex == -1
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteExpense(id: Long): ExpenseResult {
        repository.deleteExpense(id)
        return ExpenseResult.Success
    }

    fun calculateExpenseCategorySummary(
        expenses: List<Expense>,
        categories: List<Category>
    ): PeriodCategorySummary = buildPeriodCategorySummary(
        entries = expenses.filter { it.fundingSource != ExpenseFundingSource.SAVINGS },
        categories = categories,
        categorySelector = { it.category },
        amountSelector = { it.amount },
        defaultColorHex = "#EF4444"
    )

    /**
     * Groups budget-funded expenses strictly by expense category (subcategories consolidated).
     */
    fun calculateCategoryExpenses(
        expenses: List<Expense>,
        categories: List<Category>
    ): List<CategoryExpenseItem> {
        val budgetExpenses = expenses.filter { it.fundingSource != ExpenseFundingSource.SAVINGS }
        if (budgetExpenses.isEmpty()) return emptyList()
        val totalSpent = budgetExpenses.sumOf { it.amount }
        return budgetExpenses.groupBy { it.category.trim().lowercase() }
            .map { (_, catExpenses) ->
                val rawName = catExpenses.first().category.trim()
                val category = categories.find { it.name.trim().equals(rawName, ignoreCase = true) }
                val spent = catExpenses.sumOf { it.amount }
                val ratio = when {
                    totalSpent.isInfinite() -> if (spent.isInfinite()) 1.0 else 0.0
                    totalSpent > 0.0 -> spent / totalSpent
                    else -> 0.0
                }
                val progress = if (ratio.isFinite()) ratio.toFloat().coerceIn(0f, 1f) else 0f
                CategoryExpenseItem(
                    categoryName = category?.name?.trim()?.ifEmpty { rawName } ?: rawName,
                    icon = category?.icon?.ifBlank { "💸" } ?: "💸",
                    colorHex = category?.color?.trim()?.ifEmpty { "#EF4444" } ?: "#EF4444",
                    spentAmount = spent,
                    progress = progress,
                    percent = (progress * 100).toInt()
                )
            }
            .sortedByDescending { it.spentAmount }
    }
}
