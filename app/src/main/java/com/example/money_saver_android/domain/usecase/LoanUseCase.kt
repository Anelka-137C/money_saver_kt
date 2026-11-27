package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

sealed class LoanResult {
    object Success : LoanResult()
    data class Error(val message: String) : LoanResult()
}

class LoanUseCase(
    private val loanRepository: LoanRepository,
    private val savingsRepository: SavingsRepository? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val loanMutex: Mutex = FinancialAllocationLock.mutex
) {
    fun getAllLoans(): Flow<List<Loan>> = loanRepository.getAllLoans()

    fun getAllSavings(): Flow<List<Saving>> = savingsRepository?.getAllSavings() ?: emptyFlow()

    fun getAllExpenses(): Flow<List<Expense>> = expenseRepository?.getAllExpenses() ?: emptyFlow()

    fun getSavingCategories(): Flow<List<Category>> = savingsRepository?.getAllCategories() ?: emptyFlow()

    fun calculateTotalReceivable(loans: List<Loan>): Double {
        val total = loans
            .filter { it.amountToReceive.isFinite() && it.amountToReceive > 0.0 }
            .map { it.amountToReceive }
            .sumBigDecimal()
            .toDouble()
        return if (total.isFinite() && total > 0.0) total else 0.0
    }

    suspend fun saveLoan(
        personNameInput: String,
        amountLentInput: String,
        amountToReceiveInput: String,
        noteInput: String = "",
        savingCategoryInput: String = ""
    ): LoanResult {
        val trimmedPerson = personNameInput.trim()
        if (trimmedPerson.isEmpty()) {
            return LoanResult.Error("Debes ingresar el nombre de la persona")
        }

        val amountLent = parseAmountInput(amountLentInput) ?: 0.0
        if (!amountLent.isFinite() || amountLent <= 0.0) {
            return LoanResult.Error("El monto prestado debe ser mayor a cero")
        }

        val amountToReceive = parseAmountInput(amountToReceiveInput)
        if (amountToReceive == null || !amountToReceive.isFinite() ||
            BigDecimal.valueOf(amountToReceive) < BigDecimal.valueOf(amountLent)
        ) {
            return LoanResult.Error("El monto a recibir debe ser mayor o igual al monto prestado")
        }

        val trimmedSavingCategory = savingCategoryInput.trim()
        if (trimmedSavingCategory.isEmpty()) {
            loanRepository.insertLoan(
                Loan(
                    personName = trimmedPerson,
                    amountLent = amountLent,
                    amountToReceive = amountToReceive,
                    note = noteInput.trim(),
                    savingCategory = ""
                )
            )
            return LoanResult.Success
        }

        val savingsRepo = savingsRepository
            ?: return LoanResult.Error("Módulo de ahorros no configurado")

        return loanMutex.withLock {
            val allSavings = savingsRepo.getAllSavings().first()
            val allExpenses = expenseRepository?.getAllExpenses()?.first() ?: emptyList()
            val allLoans = loanRepository.getAllLoans().first()
            val matchingSavings = allSavings.filter {
                it.amount.isFinite() && it.amount > 0.0 &&
                    it.category.trim().equals(trimmedSavingCategory, ignoreCase = true)
            }
            val available = calculateCategoryAvailableSavings(
                categoryName = trimmedSavingCategory,
                savings = allSavings,
                expenses = allExpenses,
                loans = allLoans
            )
            if (matchingSavings.isEmpty() || BigDecimal.valueOf(amountLent) > available) {
                return@withLock LoanResult.Error("Saldo insuficiente en la categoría de ahorro")
            }

            loanRepository.insertLoan(
                Loan(
                    personName = trimmedPerson,
                    amountLent = amountLent,
                    amountToReceive = amountToReceive,
                    note = noteInput.trim(),
                    savingCategory = matchingSavings.first().category.trim()
                )
            )
            LoanResult.Success
        }
    }
}
