package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

internal object FinancialAllocationLock {
    val mutex = Mutex()
}

internal fun Iterable<Double>.sumBigDecimal(): BigDecimal =
    fold(BigDecimal.ZERO) { acc, value ->
        if (value.isFinite()) acc.add(BigDecimal.valueOf(value)) else acc
    }

internal fun calculateExactAvailableIncome(
    totalIncome: Double,
    totalBudgeted: Double,
    totalSaved: Double
): Double {
    if (totalIncome.isFinite() && totalBudgeted.isFinite() && totalSaved.isFinite()) {
        val exact = BigDecimal.valueOf(totalIncome)
            .subtract(BigDecimal.valueOf(totalBudgeted))
            .subtract(BigDecimal.valueOf(totalSaved))
            .toDouble()
        return if (exact.isNaN()) 0.0 else maxOf(0.0, exact)
    }
    val raw = totalIncome - totalBudgeted - totalSaved
    return if (raw.isNaN()) 0.0 else maxOf(0.0, raw)
}

internal fun parseAmountInput(rawInput: String): Double? {
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

sealed class BudgetResult {
    object Success : BudgetResult()
    data class Error(val message: String) : BudgetResult()
}

class BudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val incomeRepository: IncomeRepository,
    private val savingsRepository: SavingsRepository? = null,
    private val budgetMutex: Mutex = FinancialAllocationLock.mutex
) {

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> {
        return budgetRepository.getBudgetsByPeriod(periodId)
    }

    fun calculateTotalBudgeted(budgets: List<Budget>): Double {
        return budgets.sumOf { it.amount }
    }

    fun calculateAvailableIncome(
        totalIncome: Double,
        totalBudgeted: Double,
        totalSaved: Double = 0.0
    ): Double = calculateExactAvailableIncome(totalIncome, totalBudgeted, totalSaved)

    suspend fun saveBudget(
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?
    ): BudgetResult {
        if (periodId == null) {
            return BudgetResult.Error("Período no seleccionado")
        }

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return BudgetResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank()) {
            return BudgetResult.Error("Debes seleccionar una categoría")
        }

        return budgetMutex.withLock {
            val totalIncome = incomeRepository.getIncomesByPeriod(periodId).first().map { it.amount }.sumBigDecimal()
            val currentBudgets = budgetRepository.getBudgetsByPeriod(periodId).first()
            val totalBudgeted = currentBudgets.map { it.amount }.sumBigDecimal()
            val totalSaved = savingsRepository?.getSavingsByPeriod(periodId)?.first()?.map { it.amount }?.sumBigDecimal()
                ?: BigDecimal.ZERO
            val available = totalIncome.subtract(totalBudgeted).subtract(totalSaved)

            if (BigDecimal.valueOf(amount) > available) {
                return@withLock BudgetResult.Error("El monto supera el ingreso disponible")
            }

            budgetRepository.insertBudget(
                Budget(
                    amount = amount,
                    category = categoryInput,
                    subcategory = subcategoryInput,
                    periodId = periodId
                )
            )
            BudgetResult.Success
        }
    }

    suspend fun updateBudget(
        id: Long,
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?
    ): BudgetResult {
        if (periodId == null) {
            return BudgetResult.Error("Período no seleccionado")
        }

        val amount = parseAmountInput(amountInput) ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return BudgetResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank()) {
            return BudgetResult.Error("Debes seleccionar una categoría")
        }

        return budgetMutex.withLock {
            val currentBudgets = budgetRepository.getBudgetsByPeriod(periodId).first()
            if (currentBudgets.none { it.id == id }) {
                return@withLock BudgetResult.Error("Presupuesto no encontrado")
            }

            val totalIncome = incomeRepository.getIncomesByPeriod(periodId).first().map { it.amount }.sumBigDecimal()
            val totalBudgetedWithoutCurrent = currentBudgets.filter { it.id != id }.map { it.amount }.sumBigDecimal()
            val totalSaved = savingsRepository?.getSavingsByPeriod(periodId)?.first()?.map { it.amount }?.sumBigDecimal()
                ?: BigDecimal.ZERO
            val availableForUpdate = totalIncome.subtract(totalBudgetedWithoutCurrent).subtract(totalSaved)

            if (BigDecimal.valueOf(amount) > availableForUpdate) {
                return@withLock BudgetResult.Error("El monto supera el ingreso disponible")
            }

            budgetRepository.updateBudget(
                Budget(
                    id = id,
                    amount = amount,
                    category = categoryInput,
                    subcategory = subcategoryInput,
                    periodId = periodId
                )
            )
            BudgetResult.Success
        }
    }

    suspend fun deleteBudget(id: Long): BudgetResult {
        return budgetMutex.withLock {
            budgetRepository.deleteBudget(id)
            BudgetResult.Success
        }
    }
}
