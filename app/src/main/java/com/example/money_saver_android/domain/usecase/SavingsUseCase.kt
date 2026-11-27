package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

sealed class SavingsResult {
    object Success : SavingsResult()
    data class Error(val message: String) : SavingsResult()
}

class SavingsUseCase(
    private val savingsRepository: SavingsRepository,
    private val incomeRepository: IncomeRepository,
    private val budgetRepository: BudgetRepository? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val loanRepository: LoanRepository? = null,
    private val savingMutex: Mutex = FinancialAllocationLock.mutex
) {

    fun getAllSavings(): Flow<List<Saving>> =
        savingsRepository.getAllSavings()

    fun getAllExpenses(): Flow<List<Expense>> =
        expenseRepository?.getAllExpenses() ?: emptyFlow()

    fun getAllLoans(): Flow<List<Loan>> =
        loanRepository?.getAllLoans() ?: emptyFlow()

    fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
        savingsRepository.getSavingsByPeriod(periodId)

    fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        incomeRepository.getIncomesByPeriod(periodId)

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
        budgetRepository?.getBudgetsByPeriod(periodId) ?: emptyFlow()

    fun calculateTotalSaved(savings: List<Saving>): Double =
        savings.sumOf { it.amount }

    fun calculateSavingsCategorySummary(
        savings: List<Saving>,
        categories: List<Category>
    ): PeriodCategorySummary = buildPeriodCategorySummary(
        entries = savings,
        categories = categories,
        categorySelector = { it.category },
        amountSelector = { it.amount },
        defaultColorHex = "#3B82F6"
    )

    fun calculateNetSavingsSummary(
        allSavings: List<Saving>,
        allExpenses: List<Expense>,
        categories: List<Category>,
        allLoans: List<Loan> = emptyList()
    ): PeriodCategorySummary = buildSavingsNetSummary(
        savings = allSavings,
        expenses = allExpenses,
        categories = categories,
        defaultColorHex = "#3B82F6",
        loans = allLoans
    )

    fun calculateAvailableIncome(
        totalIncome: Double,
        totalBudgeted: Double,
        totalSaved: Double
    ): Double = calculateExactAvailableIncome(totalIncome, totalBudgeted, totalSaved)

    suspend fun saveSaving(
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?,
        incomeId: Long? = null,
        dateTimeInput: String = "",
        noteInput: String = ""
    ): SavingsResult {
        if (periodId == null) {
            return SavingsResult.Error("Período no seleccionado")
        }

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return SavingsResult.Error("El monto debe ser mayor a cero")
        }

        if (incomeId == null || incomeId <= 0L) {
            return SavingsResult.Error("Debes seleccionar un ingreso origen")
        }

        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return SavingsResult.Error("Debes seleccionar una categoría")
        }

        val trimmedDateTime = dateTimeInput.trim()
        if (!isValidSavingDateTime(trimmedDateTime)) {
            return SavingsResult.Error("Fecha y hora inválidas")
        }

        return savingMutex.withLock {
            val periodIncomes = incomeRepository.getIncomesByPeriod(periodId).first()
            val matchedIncome = periodIncomes.find { it.id == incomeId }
                ?: return@withLock SavingsResult.Error("El ingreso seleccionado no corresponde al período activo")

            val totalIncome = periodIncomes.map { it.amount }.sumBigDecimal()
            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(periodId)?.first()?.map { it.amount }?.sumBigDecimal()
                ?: BigDecimal.ZERO
            val periodSavings = savingsRepository.getSavingsByPeriod(periodId).first()
            val totalSaved = periodSavings.map { it.amount }.sumBigDecimal()
            val amountDecimal = BigDecimal.valueOf(amount)

            val savedFromIncome = periodSavings.filter { it.incomeId == matchedIncome.id }.map { it.amount }.sumBigDecimal()
            val incomeAmount = if (matchedIncome.amount.isFinite()) BigDecimal.valueOf(matchedIncome.amount) else BigDecimal.ZERO
            val incomeRemaining = incomeAmount.subtract(savedFromIncome)
            if (amountDecimal > incomeRemaining) {
                return@withLock SavingsResult.Error("El monto supera el disponible del ingreso seleccionado")
            }

            val available = totalIncome.subtract(totalBudgeted).subtract(totalSaved)
            if (amountDecimal > available) {
                return@withLock SavingsResult.Error("El monto supera el ingreso disponible")
            }

            val incomeLabel = if (matchedIncome.subcategory.isNotBlank()) {
                "${matchedIncome.category} • ${matchedIncome.subcategory}"
            } else {
                matchedIncome.category
            }

            savingsRepository.insertSaving(
                Saving(
                    amount = amount,
                    category = trimmedCategory,
                    subcategory = subcategoryInput.trim(),
                    periodId = periodId,
                    incomeId = matchedIncome.id,
                    incomeCategory = incomeLabel,
                    dateTime = trimmedDateTime,
                    note = noteInput.trim()
                )
            )
            SavingsResult.Success
        }
    }

    suspend fun updateSaving(
        id: Long,
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?,
        incomeId: Long?,
        dateTimeInput: String,
        noteInput: String = ""
    ): SavingsResult {
        if (periodId == null) {
            return SavingsResult.Error("Período no seleccionado")
        }

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return SavingsResult.Error("El monto debe ser mayor a cero")
        }

        if (incomeId == null || incomeId <= 0L) {
            return SavingsResult.Error("Debes seleccionar un ingreso origen")
        }

        val trimmedCategory = categoryInput.trim()
        if (trimmedCategory.isEmpty()) {
            return SavingsResult.Error("Debes seleccionar una categoría")
        }

        val trimmedDateTime = dateTimeInput.trim()
        if (!isValidSavingDateTime(trimmedDateTime)) {
            return SavingsResult.Error("Fecha y hora inválidas")
        }

        return savingMutex.withLock {
            val periodSavings = savingsRepository.getSavingsByPeriod(periodId).first()
            val existingSaving = if (id > 0L) periodSavings.find { it.id == id } else null
            if (existingSaving == null) {
                return@withLock SavingsResult.Error("Ahorro no encontrado")
            }

            val periodIncomes = incomeRepository.getIncomesByPeriod(periodId).first()
            val matchedIncome = periodIncomes.find { it.id == incomeId }
                ?: return@withLock SavingsResult.Error("El ingreso seleccionado no corresponde al período activo")

            val otherSavings = periodSavings.filter { it.id != id }
            val totalIncome = periodIncomes.map { it.amount }.sumBigDecimal()
            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(periodId)?.first()?.map { it.amount }?.sumBigDecimal()
                ?: BigDecimal.ZERO
            val totalSavedWithoutCurrent = otherSavings.map { it.amount }.sumBigDecimal()
            val amountDecimal = BigDecimal.valueOf(amount)

            val savedFromIncomeWithoutCurrent = otherSavings
                .filter { it.incomeId == matchedIncome.id }
                .map { it.amount }
                .sumBigDecimal()
            val incomeAmount = if (matchedIncome.amount.isFinite()) BigDecimal.valueOf(matchedIncome.amount) else BigDecimal.ZERO
            val incomeRemaining = incomeAmount.subtract(savedFromIncomeWithoutCurrent)
            if (amountDecimal > incomeRemaining) {
                return@withLock SavingsResult.Error("El monto supera el disponible del ingreso seleccionado")
            }

            val availableForUpdate = totalIncome.subtract(totalBudgeted).subtract(totalSavedWithoutCurrent)
            if (amountDecimal > availableForUpdate) {
                return@withLock SavingsResult.Error("El monto supera el ingreso disponible")
            }

            if (wouldOverdrawSavingsAllocations(
                    categoryToCheck = existingSaving.category,
                    savingIdToReplace = id,
                    replacementAmount = amountDecimal,
                    replacementCategory = trimmedCategory
                )
            ) {
                return@withLock SavingsResult.Error("El monto o categoría compromete gastos o préstamos financiados con este ahorro")
            }

            val incomeLabel = if (matchedIncome.subcategory.isNotBlank()) {
                "${matchedIncome.category} • ${matchedIncome.subcategory}"
            } else {
                matchedIncome.category
            }

            savingsRepository.updateSaving(
                Saving(
                    id = id,
                    amount = amount,
                    category = trimmedCategory,
                    subcategory = subcategoryInput.trim(),
                    periodId = periodId,
                    incomeId = matchedIncome.id,
                    incomeCategory = incomeLabel,
                    dateTime = trimmedDateTime,
                    note = noteInput.trim()
                )
            )
            SavingsResult.Success
        }
    }

    suspend fun deleteSaving(id: Long): SavingsResult = savingMutex.withLock {
        val existingSaving = if ((expenseRepository != null || loanRepository != null) && id > 0L) {
            savingsRepository.getSavingById(id)
        } else {
            null
        }
        if (existingSaving != null && wouldOverdrawSavingsAllocations(
                categoryToCheck = existingSaving.category,
                savingIdToReplace = existingSaving.id,
                replacementAmount = BigDecimal.ZERO,
                replacementCategory = null
            )
        ) {
            return@withLock SavingsResult.Error("No se puede eliminar el ahorro porque respalda gastos o préstamos registrados")
        }
        savingsRepository.deleteSaving(id)
        SavingsResult.Success
    }

    private suspend fun wouldOverdrawSavingsAllocations(
        categoryToCheck: String,
        savingIdToReplace: Long,
        replacementAmount: BigDecimal,
        replacementCategory: String?
    ): Boolean {
        if (expenseRepository == null && loanRepository == null) return false
        val normalizedCheck = categoryToCheck.trim()
        if (normalizedCheck.isEmpty()) return false
        val allExpenses = expenseRepository?.getAllExpenses()?.firstOrNull() ?: emptyList()
        val allLoans = loanRepository?.getAllLoans()?.firstOrNull() ?: emptyList()
        val totalCommitted = calculateCategorySavingsOutflows(
            categoryName = normalizedCheck,
            expenses = allExpenses,
            loans = allLoans
        )
        if (totalCommitted <= BigDecimal.ZERO) return false

        val allSavings = savingsRepository.getAllSavings().firstOrNull() ?: emptyList()
        val baseSaved = allSavings
            .filter {
                it.id != savingIdToReplace &&
                    it.amount.isFinite() &&
                    it.amount > 0.0 &&
                    it.category.trim().equals(normalizedCheck, ignoreCase = true)
            }
            .map { it.amount }
            .sumBigDecimal()
        val replacementMatches = replacementCategory != null &&
            replacementCategory.trim().equals(normalizedCheck, ignoreCase = true)
        val totalSavedAfter = if (replacementMatches) baseSaved.add(replacementAmount) else baseSaved
        return totalCommitted > totalSavedAfter
    }

    private fun isValidSavingDateTime(dateStr: String): Boolean {
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

    fun getCategories(): Flow<List<Category>> = savingsRepository.getAllCategories()

    suspend fun preloadDefaultCategoriesIfNeeded() {
        savingsRepository.preloadDefaultCategoriesIfNeeded()
    }

    suspend fun createCategory(
        nameInput: String,
        colorInput: String,
        icon: String = "💰"
    ): SavingsResult {
        val trimmedName = nameInput.trim()
        if (trimmedName.isEmpty()) {
            return SavingsResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val existing = savingsRepository.getAllCategories().first()
        if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return SavingsResult.Error("Ya existe una categoría con este nombre")
        }
        savingsRepository.insertCategory(
            Category(
                name = trimmedName,
                icon = icon,
                color = if (colorInput.isBlank()) "#4E73DF" else colorInput,
                subcategories = emptyList()
            )
        )
        return SavingsResult.Success
    }

    suspend fun createSubcategory(
        categoryName: String,
        subcategoryNameInput: String
    ): SavingsResult {
        val trimmedCategory = categoryName.trim()
        val trimmed = subcategoryNameInput.trim()
        if (trimmedCategory.isEmpty() || trimmed.isEmpty()) {
            return SavingsResult.Error("El nombre de la subcategoría no puede estar vacío")
        }
        val existing = savingsRepository.getAllCategories().first()
        val target = existing.find { it.name.equals(trimmedCategory, ignoreCase = true) }
            ?: return SavingsResult.Error("La categoría no existe")
        if (target.subcategories.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return SavingsResult.Error("Ya existe una subcategoría con este nombre")
        }
        savingsRepository.insertSubcategory(target.name, Subcategory(name = trimmed))
        return SavingsResult.Success
    }

    suspend fun updateCategory(
        oldName: String,
        newNameInput: String,
        colorInput: String,
        iconInput: String? = null
    ): SavingsResult = savingMutex.withLock {
        val trimmedOldName = oldName.trim()
        val trimmedNewName = newNameInput.trim()
        if (trimmedNewName.isEmpty()) {
            return@withLock SavingsResult.Error("El nombre de la categoría no puede estar vacío")
        }

        val existingCategories = savingsRepository.getAllCategories().first()
        val targetCategory = existingCategories.find { it.name.equals(trimmedOldName, ignoreCase = true) }
            ?: return@withLock SavingsResult.Error("Categoría no encontrada")

        val isDuplicate = existingCategories.any {
            !it.name.equals(targetCategory.name, ignoreCase = true) &&
                it.name.equals(trimmedNewName, ignoreCase = true)
        }
        if (isDuplicate) {
            return@withLock SavingsResult.Error("Ya existe una categoría con este nombre")
        }

        val resolvedColor = if (colorInput.isBlank()) targetCategory.color else colorInput
        val resolvedIcon = iconInput?.takeIf { it.isNotBlank() } ?: targetCategory.icon

        savingsRepository.updateCategory(
            oldName = targetCategory.name,
            newName = trimmedNewName,
            color = resolvedColor,
            icon = resolvedIcon
        )
        if (targetCategory.name != trimmedNewName) {
            expenseRepository?.reassignSavingCategory(targetCategory.name, trimmedNewName)
            loanRepository?.reassignSavingCategory(targetCategory.name, trimmedNewName)
        }
        SavingsResult.Success
    }

    suspend fun deleteCategory(categoryName: String): SavingsResult = savingMutex.withLock {
        val trimmed = categoryName.trim()
        if (trimmed.isEmpty()) {
            return@withLock SavingsResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val canonicalName = savingsRepository.getAllCategories().first()
            .find { it.name.equals(trimmed, ignoreCase = true) }
            ?.name ?: trimmed
        if (savingsRepository.countSavingsForCategory(canonicalName) > 0) {
            return@withLock SavingsResult.Error("No se puede eliminar la categoría porque tiene ahorros asociados")
        }
        savingsRepository.deleteCategory(canonicalName)
        SavingsResult.Success
    }

    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String): SavingsResult = savingMutex.withLock {
        val trimmedCat = categoryName.trim()
        val trimmedSub = subcategoryName.trim()
        if (trimmedCat.isEmpty() || trimmedSub.isEmpty()) {
            return@withLock SavingsResult.Error("El nombre no puede estar vacío")
        }
        val matchedCategory = savingsRepository.getAllCategories().first()
            .find { it.name.equals(trimmedCat, ignoreCase = true) }
        val canonicalCat = matchedCategory?.name ?: trimmedCat
        val canonicalSub = matchedCategory?.subcategories
            ?.find { it.name.equals(trimmedSub, ignoreCase = true) }
            ?.name ?: trimmedSub
        if (savingsRepository.countSavingsForSubcategory(canonicalCat, canonicalSub) > 0) {
            return@withLock SavingsResult.Error("No se puede eliminar la subcategoría porque tiene ahorros asociados")
        }
        savingsRepository.deleteSubcategory(canonicalCat, canonicalSub)
        SavingsResult.Success
    }
}
