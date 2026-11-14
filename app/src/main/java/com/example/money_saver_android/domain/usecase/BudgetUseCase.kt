package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class BudgetResult {
    object Success : BudgetResult()
    data class Error(val message: String) : BudgetResult()
}

class BudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val incomeRepository: IncomeRepository
) {
    private val budgetMutex = Mutex()

    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> {
        return budgetRepository.getBudgetsByPeriod(periodId)
    }

    fun calculateTotalBudgeted(budgets: List<Budget>): Double {
        return budgets.sumOf { it.amount }
    }

    fun calculateAvailableIncome(totalIncome: Double, totalBudgeted: Double): Double {
        return maxOf(0.0, totalIncome - totalBudgeted)
    }

    suspend fun saveBudget(
        amountInput: String,
        categoryInput: String,
        subcategoryInput: String = "",
        periodId: Long?
    ): BudgetResult {
        if (periodId == null) {
            return BudgetResult.Error("Período no seleccionado")
        }

        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return BudgetResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank()) {
            return BudgetResult.Error("Debes seleccionar una categoría")
        }

        return budgetMutex.withLock {
            val totalIncome = incomeRepository.getIncomesByPeriod(periodId).first().sumOf { it.amount }
            val currentBudgets = budgetRepository.getBudgetsByPeriod(periodId).first()
            val totalBudgeted = currentBudgets.sumOf { it.amount }
            val available = totalIncome - totalBudgeted

            if (amount > available) {
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

        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            return BudgetResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank()) {
            return BudgetResult.Error("Debes seleccionar una categoría")
        }

        return budgetMutex.withLock {
            val currentBudgets = budgetRepository.getBudgetsByPeriod(periodId).first()
            val currentBudget = currentBudgets.find { it.id == id }
                ?: return@withLock BudgetResult.Error("Presupuesto no encontrado")
            val currentBudgetAmount = currentBudget.amount

            val totalIncome = incomeRepository.getIncomesByPeriod(periodId).first().sumOf { it.amount }
            val totalBudgetedWithoutCurrent = currentBudgets.sumOf { it.amount } - currentBudgetAmount
            val availableForUpdate = totalIncome - totalBudgetedWithoutCurrent

            if (amount > availableForUpdate) {
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
}
