package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

sealed class IncomeResult {
    object Success : IncomeResult()
    data class Error(val message: String) : IncomeResult()
}

data class CategoryBudgetItem(
    val categoryName: String,
    val icon: String,
    val colorHex: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val progress: Float,
    val percent: Int
)

class IncomeUseCase(
    private val repository: IncomeRepository,
    private val budgetRepository: BudgetRepository? = null,
    budgetUseCase: BudgetUseCase? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val savingsRepository: SavingsRepository? = null,
    private val allocationMutex: Mutex = FinancialAllocationLock.mutex
) {
    private val budgetUseCase: BudgetUseCase? = budgetUseCase ?: budgetRepository?.let {
        BudgetUseCase(it, repository, savingsRepository)
    }
    private val deletionMutex = Mutex()
    fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> {
        return repository.getIncomesByPeriod(periodId)
    }

    fun calculateTotalIncome(incomes: List<Income>): Double {
        return incomes.sumOf { it.amount }
    }

    fun calculateIncomeCategorySummary(
        incomes: List<Income>,
        categories: List<Category>
    ): PeriodCategorySummary = buildPeriodCategorySummary(
        entries = incomes,
        categories = categories,
        categorySelector = { it.category },
        amountSelector = { it.amount },
        defaultColorHex = "#10B981"
    )

    fun calculateBudgetCategorySummary(
        budgets: List<Budget>,
        categories: List<Category>
    ): PeriodCategorySummary = buildPeriodCategorySummary(
        entries = budgets,
        categories = categories,
        categorySelector = { it.category },
        amountSelector = { it.amount },
        defaultColorHex = "#10B981"
    )

    fun getCategories(): Flow<List<Category>> {
        return repository.getAllCategories()
    }

    suspend fun preloadDefaultCategoriesIfNeeded() {
        repository.preloadDefaultCategoriesIfNeeded()
    }

    suspend fun insertCategory(category: Category) {
        repository.insertCategory(category)
    }

    suspend fun createCategory(
        nameInput: String,
        colorInput: String,
        icon: String = "💰"
    ): IncomeResult {
        val trimmedName = nameInput.trim()
        if (trimmedName.isEmpty()) {
            return IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }

        val existingCategories = repository.getAllCategories().first()
        val isDuplicate = existingCategories.any { it.name.equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            return IncomeResult.Error("Ya existe una categoría con este nombre")
        }

        val category = Category(
            name = trimmedName,
            icon = icon,
            color = if (colorInput.isBlank()) "#4E73DF" else colorInput,
            subcategories = emptyList()
        )
        repository.insertCategory(category)
        return IncomeResult.Success
    }

    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        repository.insertSubcategory(categoryName, subcategory)
    }

    suspend fun createSubcategory(
        categoryName: String,
        subcategoryNameInput: String
    ): IncomeResult {
        val trimmedSubcategory = subcategoryNameInput.trim()
        if (trimmedSubcategory.isEmpty()) {
            return IncomeResult.Error("El nombre de la subcategoría no puede estar vacío")
        }

        val categories = repository.getAllCategories().first()
        val targetCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
        val isDuplicate = targetCategory?.subcategories?.any {
            it.name.equals(trimmedSubcategory, ignoreCase = true)
        } == true

        if (isDuplicate) {
            return IncomeResult.Error("Ya existe una subcategoría con este nombre")
        }

        repository.insertSubcategory(
            categoryName = categoryName,
            subcategory = Subcategory(name = trimmedSubcategory)
        )
        return IncomeResult.Success
    }

    suspend fun deleteCategory(categoryName: String): IncomeResult = deletionMutex.withLock {
        val trimmedCategory = categoryName.trim()
        if (trimmedCategory.isEmpty()) {
            return@withLock IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val count = repository.countIncomesForCategory(trimmedCategory)
        if (count > 0) {
            return@withLock IncomeResult.Error("No se puede eliminar la categoría porque tiene ingresos asociados")
        }
        repository.deleteCategory(trimmedCategory)
        IncomeResult.Success
    }

    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String): IncomeResult = deletionMutex.withLock {
        val trimmedCategory = categoryName.trim()
        val trimmedSubcategory = subcategoryName.trim()
        if (trimmedCategory.isEmpty() || trimmedSubcategory.isEmpty()) {
            return@withLock IncomeResult.Error("El nombre no puede estar vacío")
        }
        val count = repository.countIncomesForSubcategory(trimmedCategory, trimmedSubcategory)
        if (count > 0) {
            return@withLock IncomeResult.Error("No se puede eliminar la subcategoría porque tiene ingresos asociados")
        }
        repository.deleteSubcategory(trimmedCategory, trimmedSubcategory)
        IncomeResult.Success
    }

    suspend fun saveIncome(
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        descriptionInput: String,
        periodId: Long?
    ): IncomeResult = withValidatedIncome(
        amountInput = amountInput,
        dateTimeInput = dateTimeInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput,
        descriptionInput = descriptionInput,
        periodId = periodId
    ) { income ->
        repository.insertIncome(income)
        IncomeResult.Success
    }

    suspend fun updateIncome(
        id: Long,
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        descriptionInput: String,
        periodId: Long?
    ): IncomeResult = withValidatedIncome(
        id = id,
        amountInput = amountInput,
        dateTimeInput = dateTimeInput,
        categoryInput = categoryInput,
        subcategoryInput = subcategoryInput,
        descriptionInput = descriptionInput,
        periodId = periodId
    ) { income ->
        allocationMutex.withLock {
            val periodIncomes = repository.getIncomesByPeriod(income.periodId).first()
            val periodSavings = savingsRepository?.getSavingsByPeriod(income.periodId)?.first().orEmpty()
            val newAmount = BigDecimal.valueOf(income.amount)
            val savedFromIncome = periodSavings
                .filter { it.incomeId == id }
                .map { it.amount }
                .sumBigDecimal()
            if (newAmount < savedFromIncome) {
                return@withLock IncomeResult.Error("El monto no puede ser menor a los ahorros asociados a este ingreso")
            }

            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(income.periodId)?.first()
                ?.map { it.amount }?.sumBigDecimal() ?: BigDecimal.ZERO
            val totalSaved = periodSavings.map { it.amount }.sumBigDecimal()
            val otherIncomesTotal = periodIncomes
                .filter { it.id != id }
                .map { it.amount }
                .sumBigDecimal()
            if (otherIncomesTotal.add(newAmount) < totalBudgeted.add(totalSaved)) {
                return@withLock IncomeResult.Error("El monto no puede ser menor al total asignado en presupuestos y ahorros")
            }

            repository.updateIncome(income)
            IncomeResult.Success
        }
    }

    private suspend fun withValidatedIncome(
        id: Long = 0,
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String,
        descriptionInput: String,
        periodId: Long?,
        onValid: suspend (Income) -> IncomeResult
    ): IncomeResult {
        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return IncomeResult.Error("El monto debe ser mayor a cero")
        }

        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return IncomeResult.Error("Debes seleccionar una categoría")
        }

        if (periodId == null) {
            return IncomeResult.Error("Período no seleccionado")
        }

        return onValid(
            Income(
                id = id,
                amount = amount,
                dateTime = dateTimeInput,
                category = trimmedCategory,
                subcategory = subcategoryInput.trim(),
                description = descriptionInput,
                periodId = periodId
            )
        )
    }

    suspend fun deleteIncome(id: Long): IncomeResult = allocationMutex.withLock {
        val income = repository.getIncomeById(id)
        if (income != null) {
            val periodId = income.periodId
            val periodSavings = savingsRepository?.getSavingsByPeriod(periodId)?.first().orEmpty()
            if (periodSavings.any { it.incomeId == id }) {
                return@withLock IncomeResult.Error("No se puede eliminar el ingreso porque tiene ahorros asociados")
            }
            val periodIncomes = repository.getIncomesByPeriod(periodId).first()
            val remainingIncome = periodIncomes.filter { it.id != id }.map { it.amount }.sumBigDecimal()
            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(periodId)?.first()
                ?.map { it.amount }?.sumBigDecimal() ?: BigDecimal.ZERO
            val totalSaved = periodSavings.map { it.amount }.sumBigDecimal()
            if (remainingIncome < totalBudgeted.add(totalSaved)) {
                return@withLock IncomeResult.Error("No se puede eliminar el ingreso porque compromete presupuestos o ahorros existentes")
            }
        }
        repository.deleteIncome(id)
        IncomeResult.Success
    }

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> {
        return budgetUseCase?.getBudgetsByPeriod(periodId) ?: emptyFlow()
    }

    fun calculateTotalBudgeted(budgets: List<Budget>): Double {
        return budgetUseCase?.calculateTotalBudgeted(budgets) ?: budgets.sumOf { it.amount }
    }

    fun calculateAvailableIncome(
        totalIncome: Double,
        totalBudgeted: Double,
        totalSaved: Double = 0.0
    ): Double {
        return budgetUseCase?.calculateAvailableIncome(totalIncome, totalBudgeted, totalSaved)
            ?: calculateExactAvailableIncome(totalIncome, totalBudgeted, totalSaved)
    }

    fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> {
        return savingsRepository?.getSavingsByPeriod(periodId) ?: emptyFlow()
    }

    fun calculateTotalSaved(savings: List<Saving>): Double {
        return savings.sumOf { it.amount }
    }

    suspend fun saveBudget(
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?
    ): BudgetResult {
        val useCase = budgetUseCase ?: return BudgetResult.Error("Módulo de presupuestos no configurado")
        return useCase.saveBudget(
            amountInput = amountInput,
            categoryInput = categoryInput,
            subcategoryInput = subcategoryInput,
            periodId = periodId
        )
    }

    suspend fun updateBudget(
        id: Long,
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?
    ): BudgetResult {
        val useCase = budgetUseCase ?: return BudgetResult.Error("Módulo de presupuestos no configurado")
        return useCase.updateBudget(
            id = id,
            amountInput = amountInput,
            categoryInput = categoryInput,
            subcategoryInput = subcategoryInput,
            periodId = periodId
        )
    }

    suspend fun deleteBudget(id: Long): BudgetResult {
        val useCase = budgetUseCase ?: return BudgetResult.Error("Módulo de presupuestos no configurado")
        return useCase.deleteBudget(id)
    }

    fun getBudgetCategories(): Flow<List<Category>> {
        return budgetRepository?.getAllCategories() ?: emptyFlow()
    }

    suspend fun preloadDefaultBudgetCategoriesIfNeeded() {
        budgetRepository?.preloadDefaultCategoriesIfNeeded()
    }

    suspend fun createBudgetCategory(
        nameInput: String,
        colorInput: String,
        icon: String = "💰"
    ): IncomeResult {
        val repo = budgetRepository ?: return IncomeResult.Error("Módulo de presupuestos no configurado")
        val trimmedName = nameInput.trim()
        if (trimmedName.isEmpty()) {
            return IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val existing = repo.getAllCategories().first()
        if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return IncomeResult.Error("Ya existe una categoría con este nombre")
        }
        repo.insertCategory(
            Category(
                name = trimmedName,
                icon = icon,
                color = if (colorInput.isBlank()) "#4E73DF" else colorInput,
                subcategories = emptyList()
            )
        )
        return IncomeResult.Success
    }

    suspend fun createBudgetSubcategory(
        categoryName: String,
        subcategoryNameInput: String
    ): IncomeResult {
        val repo = budgetRepository ?: return IncomeResult.Error("Módulo de presupuestos no configurado")
        val trimmed = subcategoryNameInput.trim()
        if (trimmed.isEmpty()) {
            return IncomeResult.Error("El nombre de la subcategoría no puede estar vacío")
        }
        val existing = repo.getAllCategories().first()
        val target = existing.find { it.name.equals(categoryName, ignoreCase = true) }
        if (target?.subcategories?.any { it.name.equals(trimmed, ignoreCase = true) } == true) {
            return IncomeResult.Error("Ya existe una subcategoría con este nombre")
        }
        repo.insertSubcategory(categoryName, Subcategory(name = trimmed))
        return IncomeResult.Success
    }

    suspend fun deleteBudgetCategory(categoryName: String): IncomeResult {
        val repo = budgetRepository ?: return IncomeResult.Error("Módulo de presupuestos no configurado")
        val trimmed = categoryName.trim()
        if (trimmed.isEmpty()) {
            return IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val count = repo.countBudgetsForCategory(trimmed)
        if (count > 0) {
            return IncomeResult.Error("No se puede eliminar la categoría porque tiene presupuestos asociados")
        }
        repo.deleteCategory(trimmed)
        return IncomeResult.Success
    }

    suspend fun deleteBudgetSubcategory(categoryName: String, subcategoryName: String): IncomeResult {
        val repo = budgetRepository ?: return IncomeResult.Error("Módulo de presupuestos no configurado")
        val trimmedCat = categoryName.trim()
        val trimmedSub = subcategoryName.trim()
        if (trimmedCat.isEmpty() || trimmedSub.isEmpty()) {
            return IncomeResult.Error("El nombre no puede estar vacío")
        }
        val count = repo.countBudgetsForSubcategory(trimmedCat, trimmedSub)
        if (count > 0) {
            return IncomeResult.Error("No se puede eliminar la subcategoría porque tiene presupuestos asociados")
        }
        repo.deleteSubcategory(trimmedCat, trimmedSub)
        return IncomeResult.Success
    }

    fun getExpenses(): Flow<List<Expense>> {
        return expenseRepository?.getAllExpenses() ?: emptyFlow()
    }

    fun calculateCategoryBudgets(
        budgets: List<Budget>,
        categories: List<Category>,
        expenses: List<Expense>,
        periodStartDate: String?,
        periodEndDate: String?,
        periodId: Long? = null
    ): List<CategoryBudgetItem> {
        if (budgets.isEmpty()) return emptyList()
        return budgets.groupBy { it.category.trim().lowercase() }
            .map { (_, catBudgets) ->
                val rawName = catBudgets.first().category.trim()
                val category = categories.find { it.name.equals(rawName, ignoreCase = true) }
                val totalBudget = catBudgets.sumOf { it.amount }
                val spent = expenses.filter { expense ->
                    expense.matchesPeriod(periodStartDate, periodEndDate, periodId) &&
                        expense.matchesBudgetGroup(catBudgets, rawName)
                }.sumOf { it.amount }
                val rawRemaining = totalBudget - spent
                val remaining = if (rawRemaining.isNaN()) 0.0 else maxOf(0.0, rawRemaining)
                val ratio = when {
                    totalBudget.isInfinite() -> if (remaining.isInfinite()) 1.0 else 0.0
                    totalBudget > 0.0 -> remaining / totalBudget
                    else -> 0.0
                }
                val progress = if (ratio.isFinite()) ratio.toFloat().coerceIn(0f, 1f) else 0f
                CategoryBudgetItem(
                    categoryName = category?.name ?: rawName,
                    icon = category?.icon?.ifBlank { "📋" } ?: "📋",
                    colorHex = category?.color?.ifBlank { "#10B981" } ?: "#10B981",
                    budgetAmount = totalBudget,
                    spentAmount = spent,
                    remainingAmount = remaining,
                    progress = progress,
                    percent = (progress * 100).toInt()
                )
            }
    }

    fun calculateTotalBudgetSpent(
        budgets: List<Budget>,
        expenses: List<Expense>,
        periodStartDate: String?,
        periodEndDate: String?,
        periodId: Long? = null
    ): Double {
        if (budgets.isEmpty()) return 0.0
        val budgetsByCat = budgets.groupBy { it.category.trim().lowercase() }
        return expenses.filter { expense ->
            expense.matchesPeriod(periodStartDate, periodEndDate, periodId) &&
                budgetsByCat.any { (_, catBudgets) ->
                    expense.matchesBudgetGroup(catBudgets, catBudgets.first().category.trim())
                }
        }.sumOf { it.amount }
    }

    fun calculateRemainingBudget(totalBudgeted: Double, totalSpent: Double): Double {
        val raw = totalBudgeted - totalSpent
        return if (raw.isNaN()) 0.0 else maxOf(0.0, raw)
    }

    private fun Expense.matchesPeriod(
        periodStartDate: String?,
        periodEndDate: String?,
        periodId: Long?
    ): Boolean {
        if (periodId != null) {
            return this.periodId == periodId
        }
        if (periodStartDate == null || periodEndDate == null) {
            return false
        }
        val dateOnly = date.take(10)
        return dateOnly >= periodStartDate && dateOnly <= periodEndDate
    }

    private fun Expense.matchesBudgetGroup(catBudgets: List<Budget>, categoryName: String): Boolean {
        if (budgetId > 0L) {
            return catBudgets.any { it.id == budgetId }
        }
        if (budgetCategory.isNotBlank()) {
            return budgetCategory.trim().equals(categoryName, ignoreCase = true)
        }
        return category.trim().equals(categoryName, ignoreCase = true)
    }
}
