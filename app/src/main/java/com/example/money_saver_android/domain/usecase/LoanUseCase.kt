package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanDueBadgeInfo
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class LoanResult {
    object Success : LoanResult()
    data class Error(val message: String) : LoanResult()
}

data class LoanInstallmentInput(
    val dueDate: String,
    val expectedAmountInput: String,
    val persistedId: Long = 0L
)

sealed class LoanInstallmentValidationResult {
    data class Valid(val installment: LoanInstallment) : LoanInstallmentValidationResult()
    data class Error(val message: String) : LoanInstallmentValidationResult()
}

private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

fun parseLoanAmount(rawInput: String): Double? = parseAmountInput(rawInput)

internal fun isValidLoanIsoDate(dateStr: String): Boolean {
    val trimmed = dateStr.trim { it.isWhitespace() }
    if (!ISO_DATE_REGEX.matches(trimmed)) return false
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val pos = ParsePosition(0)
    val parsed = parser.parse(trimmed, pos)
    return parsed != null && pos.index == trimmed.length && pos.errorIndex == -1
}

fun calculateLoanDueBadgeInfo(
    installments: List<LoanInstallment>,
    todayIso: String
): LoanDueBadgeInfo? {
    val validInstallments = installments
        .filter { isValidLoanIsoDate(it.dueDate) }
        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
    val earliest = validInstallments.firstOrNull() ?: return null
    val nextDate = earliest.dueDate.trim { it.isWhitespace() }
    val isOverdue = isValidLoanIsoDate(todayIso) && nextDate < todayIso.trim { it.isWhitespace() }
    return LoanDueBadgeInfo(
        nextDueDate = nextDate,
        additionalCount = validInstallments.size - 1,
        isOverdue = isOverdue
    )
}

fun formatLoanShortDate(isoDate: String, languageTag: String): String {
    val trimmed = isoDate.trim { it.isWhitespace() }
    if (!isValidLoanIsoDate(trimmed)) return trimmed
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val parsed = parser.parse(trimmed) ?: return trimmed
        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val locale = Locale.forLanguageTag(languageTag)
        val monthLabel = SimpleDateFormat("MMM", locale).format(parsed)
            .replace(".", "")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        "$day $monthLabel $year"
    } catch (_: Exception) {
        trimmed
    }
}

class LoanUseCase(
    private val loanRepository: LoanRepository,
    private val savingsRepository: SavingsRepository? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val loanMutex: Mutex = FinancialAllocationLock.mutex,
    private val currentDateProvider: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    }
) {
    fun todayIso(): String = currentDateProvider().trim { it.isWhitespace() }

    fun getAllLoans(): Flow<List<Loan>> = loanRepository.getAllLoans()

    fun getAllSavings(): Flow<List<Saving>> = savingsRepository?.getAllSavings() ?: emptyFlow()

    fun getAllExpenses(): Flow<List<Expense>> = expenseRepository?.getAllExpenses() ?: emptyFlow()

    fun getSavingCategories(): Flow<List<Category>> = savingsRepository?.getAllCategories() ?: emptyFlow()

    fun calculateTotalReceivable(loans: List<Loan>): Double {
        val totalDecimal = loans
            .filter { it.amountToReceive.isFinite() && it.amountToReceive > 0.0 }
            .map { it.amountToReceive }
            .sumBigDecimal()
        if (totalDecimal <= BigDecimal.ZERO) return 0.0
        val total = totalDecimal.toDouble()
        return if (total.isFinite()) total else Double.MAX_VALUE
    }

    fun validateInstallmentDraft(
        dueDateInput: String,
        expectedAmountInput: String
    ): LoanInstallmentValidationResult {
        val (installment, error) = parseAndValidateInstallment(
            input = LoanInstallmentInput(dueDate = dueDateInput, expectedAmountInput = expectedAmountInput),
            today = todayIso(),
            allowPersistedPastDate = false
        )
        return if (installment != null) {
            LoanInstallmentValidationResult.Valid(installment)
        } else {
            LoanInstallmentValidationResult.Error(error ?: "Datos de cuota inválidos")
        }
    }

    suspend fun saveLoan(
        personNameInput: String,
        amountLentInput: String,
        amountToReceiveInput: String,
        noteInput: String = "",
        savingCategoryInput: String = "",
        installmentInputs: List<LoanInstallmentInput> = emptyList()
    ): LoanResult {
        val trimmedPerson = personNameInput.trim { it.isWhitespace() }
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

        val (validatedInstallments, scheduleError) = validateScheduleForLoan(
            inputs = installmentInputs,
            amountToReceive = amountToReceive,
            existingById = emptyMap()
        )
        if (scheduleError != null) {
            return LoanResult.Error(scheduleError)
        }

        val trimmedNote = noteInput.trim { it.isWhitespace() }
        val trimmedSavingCategory = savingCategoryInput.trim { it.isWhitespace() }
        if (trimmedSavingCategory.isEmpty()) {
            loanRepository.insertLoan(
                Loan(
                    personName = trimmedPerson,
                    amountLent = amountLent,
                    amountToReceive = amountToReceive,
                    note = trimmedNote,
                    savingCategory = "",
                    installments = validatedInstallments
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
                    it.category.trim { ch -> ch.isWhitespace() }.equals(trimmedSavingCategory, ignoreCase = true)
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
                    note = trimmedNote,
                    savingCategory = matchingSavings.first().category.trim { it.isWhitespace() },
                    installments = validatedInstallments
                )
            )
            LoanResult.Success
        }
    }

    suspend fun updateLoanInstallments(
        loanId: Long,
        installmentInputs: List<LoanInstallmentInput>
    ): LoanResult {
        val existingLoan = loanRepository.getAllLoans().first().find { it.id == loanId }
            ?: return LoanResult.Error("Préstamo no encontrado")

        val existingById = existingLoan.installments
            .filter { it.id > 0L }
            .associateBy { it.id }

        val (validatedInstallments, scheduleError) = validateScheduleForLoan(
            inputs = installmentInputs,
            amountToReceive = existingLoan.amountToReceive,
            existingById = existingById
        )
        if (scheduleError != null) {
            return LoanResult.Error(scheduleError)
        }

        loanRepository.replaceInstallments(
            loanId = loanId,
            installments = validatedInstallments.map { it.copy(loanId = loanId) }
        )
        return LoanResult.Success
    }

    private fun validateScheduleForLoan(
        inputs: List<LoanInstallmentInput>,
        amountToReceive: Double,
        existingById: Map<Long, LoanInstallment>
    ): Pair<List<LoanInstallment>, String?> {
        if (inputs.isEmpty()) return emptyList<LoanInstallment>() to null

        val today = todayIso()
        val parsed = mutableListOf<LoanInstallment>()
        for (input in inputs) {
            val existing = existingById[input.persistedId]
            val allowPast = existing != null &&
                existing.dueDate.trim { it.isWhitespace() } == input.dueDate.trim { it.isWhitespace() }
            val (item, error) = parseAndValidateInstallment(
                input = input,
                today = today,
                allowPersistedPastDate = allowPast
            )
            if (error != null || item == null) {
                return emptyList<LoanInstallment>() to (error ?: "Datos de cuota inválidos")
            }
            parsed.add(item)
        }

        val totalInstallments = parsed.map { it.expectedAmount }.sumBigDecimal()
        val expectedTotal = BigDecimal.valueOf(amountToReceive)
        if (totalInstallments.compareTo(expectedTotal) != 0) {
            return emptyList<LoanInstallment>() to
                "La suma de los montos esperados debe ser exactamente igual al monto a recibir"
        }

        return parsed.sortedBy { it.dueDate } to null
    }

    private fun parseAndValidateInstallment(
        input: LoanInstallmentInput,
        today: String,
        allowPersistedPastDate: Boolean
    ): Pair<LoanInstallment?, String?> {
        val trimmedDate = input.dueDate.trim { it.isWhitespace() }
        if (!isValidLoanIsoDate(trimmedDate)) {
            return null to "La fecha de la cuota no es válida"
        }
        if (!allowPersistedPastDate && isValidLoanIsoDate(today) && trimmedDate < today) {
            return null to "No se pueden programar cuotas con fecha anterior a hoy"
        }
        val expectedAmount = parseAmountInput(input.expectedAmountInput)
        if (expectedAmount == null || !expectedAmount.isFinite() || expectedAmount <= 0.0) {
            return null to "El monto esperado de cada cuota debe ser mayor a cero"
        }
        return LoanInstallment(
            id = input.persistedId,
            dueDate = trimmedDate,
            expectedAmount = expectedAmount
        ) to null
    }
}
