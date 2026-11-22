package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val budgetRepository: BudgetRepository? = null
) {
    private val savingMutex = Mutex()

    fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
        savingsRepository.getSavingsByPeriod(periodId)

    fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        incomeRepository.getIncomesByPeriod(periodId)

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
        budgetRepository?.getBudgetsByPeriod(periodId) ?: emptyFlow()

    fun calculateTotalSaved(savings: List<Saving>): Double =
        savings.sumOf { it.amount }

    fun calculateAvailableIncome(
        totalIncome: Double,
        totalBudgeted: Double,
        totalSaved: Double
    ): Double {
        val raw = totalIncome - totalBudgeted - totalSaved
        return if (raw.isNaN()) 0.0 else maxOf(0.0, raw)
    }

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

        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
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

            val totalIncome = periodIncomes.sumOf { it.amount }
            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(periodId)?.first()?.sumOf { it.amount } ?: 0.0
            val periodSavings = savingsRepository.getSavingsByPeriod(periodId).first()
            val totalSaved = periodSavings.sumOf { it.amount }

            val savedFromIncome = periodSavings.filter { it.incomeId == matchedIncome.id }.sumOf { it.amount }
            val incomeRemaining = matchedIncome.amount - savedFromIncome
            if (amount > incomeRemaining) {
                return@withLock SavingsResult.Error("El monto supera el disponible del ingreso seleccionado")
            }

            val available = totalIncome - totalBudgeted - totalSaved
            if (amount > available) {
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

        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
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
            if (id <= 0L || periodSavings.none { it.id == id }) {
                return@withLock SavingsResult.Error("Ahorro no encontrado")
            }

            val periodIncomes = incomeRepository.getIncomesByPeriod(periodId).first()
            val matchedIncome = periodIncomes.find { it.id == incomeId }
                ?: return@withLock SavingsResult.Error("El ingreso seleccionado no corresponde al período activo")

            val otherSavings = periodSavings.filter { it.id != id }
            val totalIncome = periodIncomes.sumOf { it.amount }
            val totalBudgeted = budgetRepository?.getBudgetsByPeriod(periodId)?.first()?.sumOf { it.amount } ?: 0.0
            val totalSavedWithoutCurrent = otherSavings.sumOf { it.amount }

            val savedFromIncomeWithoutCurrent = otherSavings
                .filter { it.incomeId == matchedIncome.id }
                .sumOf { it.amount }
            val incomeRemaining = matchedIncome.amount - savedFromIncomeWithoutCurrent
            if (amount > incomeRemaining) {
                return@withLock SavingsResult.Error("El monto supera el disponible del ingreso seleccionado")
            }

            val availableForUpdate = totalIncome - totalBudgeted - totalSavedWithoutCurrent
            if (amount > availableForUpdate) {
                return@withLock SavingsResult.Error("El monto supera el ingreso disponible")
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
        savingsRepository.deleteSaving(id)
        SavingsResult.Success
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
        val trimmed = subcategoryNameInput.trim()
        if (trimmed.isEmpty()) {
            return SavingsResult.Error("El nombre de la subcategoría no puede estar vacío")
        }
        val existing = savingsRepository.getAllCategories().first()
        val target = existing.find { it.name.equals(categoryName, ignoreCase = true) }
        if (target?.subcategories?.any { it.name.equals(trimmed, ignoreCase = true) } == true) {
            return SavingsResult.Error("Ya existe una subcategoría con este nombre")
        }
        savingsRepository.insertSubcategory(categoryName, Subcategory(name = trimmed))
        return SavingsResult.Success
    }

    suspend fun deleteCategory(categoryName: String): SavingsResult = savingMutex.withLock {
        val trimmed = categoryName.trim()
        if (trimmed.isEmpty()) {
            return@withLock SavingsResult.Error("El nombre de la categoría no puede estar vacío")
        }
        if (savingsRepository.countSavingsForCategory(trimmed) > 0) {
            return@withLock SavingsResult.Error("No se puede eliminar la categoría porque tiene ahorros asociados")
        }
        savingsRepository.deleteCategory(trimmed)
        SavingsResult.Success
    }

    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String): SavingsResult = savingMutex.withLock {
        val trimmedCat = categoryName.trim()
        val trimmedSub = subcategoryName.trim()
        if (trimmedCat.isEmpty() || trimmedSub.isEmpty()) {
            return@withLock SavingsResult.Error("El nombre no puede estar vacío")
        }
        if (savingsRepository.countSavingsForSubcategory(trimmedCat, trimmedSub) > 0) {
            return@withLock SavingsResult.Error("No se puede eliminar la subcategoría porque tiene ahorros asociados")
        }
        savingsRepository.deleteSubcategory(trimmedCat, trimmedSub)
        SavingsResult.Success
    }
}
