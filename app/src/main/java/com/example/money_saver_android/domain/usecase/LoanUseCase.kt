package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanDueBadgeInfo
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
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
    val persistedId: Long = 0L,
    val paymentAmount: Double = 0.0
)

sealed class LoanInstallmentValidationResult {
    data class Valid(val installment: LoanInstallment) : LoanInstallmentValidationResult()
    data class Error(val message: String) : LoanInstallmentValidationResult()
}

sealed class PartialPaymentScheduleOption {
    object KeepCurrent : PartialPaymentScheduleOption()
    data class SetNewDueDate(val dueDate: String) : PartialPaymentScheduleOption()
    data class AddInstallment(val dueDate: String, val amountInput: String) : PartialPaymentScheduleOption()
    data class AdjustNextInstallment(
        val amountInput: String,
        val remainderDueDate: String = ""
    ) : PartialPaymentScheduleOption()
}

private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val ISO_DATE_TIME_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")

fun parseLoanAmount(rawInput: String): Double? = parseAmountInput(rawInput)

internal fun isValidLoanIsoDate(dateStr: String): Boolean {
    val trimmed = dateStr.trim { it.isWhitespace() }
    if (!ISO_DATE_REGEX.matches(trimmed)) return false
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val pos = ParsePosition(0)
    val parsed = parser.parse(trimmed, pos)
    return parsed != null && pos.index == trimmed.length && pos.errorIndex == -1
}

internal fun isValidLoanPaymentDateTime(dateTimeStr: String): Boolean {
    val trimmed = dateTimeStr.trim { it.isWhitespace() }
    val pattern = when {
        ISO_DATE_TIME_REGEX.matches(trimmed) -> "yyyy-MM-dd HH:mm"
        ISO_DATE_REGEX.matches(trimmed) -> "yyyy-MM-dd"
        else -> return false
    }
    return try {
        val parser = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
        val pos = ParsePosition(0)
        val parsed = parser.parse(trimmed, pos)
        parsed != null && pos.index == trimmed.length && pos.errorIndex == -1
    } catch (_: Exception) {
        false
    }
}

fun calculateLoanDueBadgeInfo(
    installments: List<LoanInstallment>,
    todayIso: String
): LoanDueBadgeInfo? {
    val validInstallments = installments
        .filter { !it.isPaid && isValidLoanIsoDate(it.dueDate) }
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

fun previewLoanInstallmentsAfterPayment(
    loan: Loan,
    paymentAmount: Double,
    targetInstallmentId: Long? = null
): List<LoanInstallment> {
    val sortedAll = loan.installments
        .filter { it.expectedAmount.isFinite() && it.expectedAmount > 0.0 }
        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
    if (sortedAll.isEmpty() || !paymentAmount.isFinite() || paymentAmount <= 0.0) {
        return sortedAll
    }

    val paidOnes = sortedAll.filter { it.isPaid }
    val unpaid = sortedAll.filter { !it.isPaid }
    if (unpaid.isEmpty()) return sortedAll

    val target = targetInstallmentId?.let { id -> unpaid.find { it.id == id } } ?: unpaid.first()
    var remainingToApply = BigDecimal.valueOf(paymentAmount)
    val orderedToApply = listOf(target) + unpaid.filterNot { it === target }
    val updatedByIdentity = mutableMapOf<LoanInstallment, LoanInstallment>()

    for (inst in orderedToApply) {
        if (remainingToApply <= BigDecimal.ZERO) {
            updatedByIdentity[inst] = inst
            continue
        }
        val instRemaining = BigDecimal.valueOf(inst.remainingAmount)
        val applied = remainingToApply.min(instRemaining)
        remainingToApply = remainingToApply.subtract(applied)
        val existingPaid = if (inst.paymentAmount.isFinite() && inst.paymentAmount > 0.0) {
            BigDecimal.valueOf(inst.paymentAmount)
        } else {
            BigDecimal.ZERO
        }
        updatedByIdentity[inst] = inst.copy(paymentAmount = existingPaid.add(applied).toDouble())
    }

    return (paidOnes + unpaid.map { updatedByIdentity[it] ?: it })
        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
}

fun hasFutureLoanInstallments(installments: List<LoanInstallment>, todayIso: String): Boolean {
    val normalizedToday = todayIso.trim { it.isWhitespace() }
    if (!isValidLoanIsoDate(normalizedToday)) return false
    return installments.any {
        !it.isPaid &&
            isValidLoanIsoDate(it.dueDate) &&
            it.dueDate.trim { ch -> ch.isWhitespace() } >= normalizedToday
    }
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

fun formatLoanPaymentDateTime(paidAt: String, languageTag: String): String {
    val trimmed = paidAt.trim { it.isWhitespace() }
    if (trimmed.isEmpty()) return trimmed
    val parts = trimmed.split(" ")
    val formattedDate = formatLoanShortDate(parts[0], languageTag)
    return if (parts.size >= 2 && parts[1].isNotBlank()) {
        "$formattedDate ${parts[1]}"
    } else {
        formattedDate
    }
}

class LoanUseCase(
    private val loanRepository: LoanRepository,
    private val savingsRepository: SavingsRepository? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val loanMutex: Mutex = FinancialAllocationLock.mutex,
    private val currentDateProvider: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    },
    private val currentDateTimeProvider: (() -> String)? = null
) {
    fun todayIso(): String = currentDateProvider().trim { it.isWhitespace() }

    fun nowDateTimeIso(): String {
        val custom = currentDateTimeProvider?.invoke()?.trim { it.isWhitespace() }
        if (!custom.isNullOrEmpty()) return custom
        val today = todayIso()
        val time = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
        return if (isValidLoanIsoDate(today)) "$today $time" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
    }

    fun getAllLoans(): Flow<List<Loan>> = loanRepository.getAllLoans()

    fun getAllSavings(): Flow<List<Saving>> = savingsRepository?.getAllSavings() ?: emptyFlow()

    fun getAllExpenses(): Flow<List<Expense>> = expenseRepository?.getAllExpenses() ?: emptyFlow()

    fun getSavingCategories(): Flow<List<Category>> = savingsRepository?.getAllCategories() ?: emptyFlow()

    fun calculateTotalReceivable(loans: List<Loan>): Double {
        val totalDecimal = loans
            .filter { it.pendingAmount.isFinite() && it.pendingAmount > 0.0 }
            .map { it.pendingAmount }
            .sumBigDecimal()
        if (totalDecimal <= BigDecimal.ZERO) return 0.0
        val total = totalDecimal.toDouble()
        return if (total.isFinite()) total else Double.MAX_VALUE
    }

    fun calculateTotalPaid(loans: List<Loan>): Double {
        val totalDecimal = loans
            .filter { it.paidAmount.isFinite() && it.paidAmount > 0.0 }
            .map { it.paidAmount }
            .sumBigDecimal()
        if (totalDecimal <= BigDecimal.ZERO) return 0.0
        val total = totalDecimal.toDouble()
        return if (total.isFinite()) total else Double.MAX_VALUE
    }

    fun previewInstallmentsAfterPayment(
        loan: Loan,
        paymentAmount: Double,
        targetInstallmentId: Long? = null
    ): List<LoanInstallment> = previewLoanInstallmentsAfterPayment(loan, paymentAmount, targetInstallmentId)

    fun hasFutureInstallments(installments: List<LoanInstallment>, todayIso: String = this.todayIso()): Boolean =
        hasFutureLoanInstallments(installments, todayIso)

    fun validateInstallmentDraft(
        dueDateInput: String,
        expectedAmountInput: String,
        persistedDueDate: String? = null,
        existingPaymentAmount: Double = 0.0
    ): LoanInstallmentValidationResult {
        val allowPast = persistedDueDate != null &&
            persistedDueDate.trim { it.isWhitespace() } == dueDateInput.trim { it.isWhitespace() }
        val (installment, error) = parseAndValidateInstallment(
            input = LoanInstallmentInput(
                dueDate = dueDateInput,
                expectedAmountInput = expectedAmountInput,
                paymentAmount = existingPaymentAmount
            ),
            today = todayIso(),
            allowPersistedPastDate = allowPast
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
    ): LoanResult = saveLoanReturningCreated(
        personNameInput = personNameInput,
        amountLentInput = amountLentInput,
        amountToReceiveInput = amountToReceiveInput,
        noteInput = noteInput,
        savingCategoryInput = savingCategoryInput,
        installmentInputs = installmentInputs
    ).first

    suspend fun saveLoanReturningCreated(
        personNameInput: String,
        amountLentInput: String,
        amountToReceiveInput: String,
        noteInput: String = "",
        savingCategoryInput: String = "",
        installmentInputs: List<LoanInstallmentInput> = emptyList()
    ): Pair<LoanResult, Loan?> {
        val trimmedPerson = personNameInput.trim { it.isWhitespace() }
        if (trimmedPerson.isEmpty()) {
            return LoanResult.Error("Debes ingresar el nombre de la persona") to null
        }

        val amountLent = parseAmountInput(amountLentInput) ?: 0.0
        if (!amountLent.isFinite() || amountLent <= 0.0) {
            return LoanResult.Error("El monto prestado debe ser mayor a cero") to null
        }

        val amountToReceive = parseAmountInput(amountToReceiveInput)
        if (amountToReceive == null || !amountToReceive.isFinite() ||
            BigDecimal.valueOf(amountToReceive) < BigDecimal.valueOf(amountLent)
        ) {
            return LoanResult.Error("El monto a recibir debe ser mayor o igual al monto prestado") to null
        }

        val (validatedInstallments, scheduleError) = validateScheduleForLoan(
            inputs = installmentInputs,
            expectedTargetAmount = amountToReceive,
            existingById = emptyMap()
        )
        if (scheduleError != null) {
            return LoanResult.Error(scheduleError) to null
        }

        val trimmedNote = noteInput.trim { it.isWhitespace() }
        val trimmedSavingCategory = savingCategoryInput.trim { it.isWhitespace() }
        if (trimmedSavingCategory.isEmpty()) {
            val draftLoan = Loan(
                personName = trimmedPerson,
                amountLent = amountLent,
                amountToReceive = amountToReceive,
                note = trimmedNote,
                savingCategory = "",
                installments = validatedInstallments
            )
            val newId = loanRepository.insertLoan(draftLoan)
            return LoanResult.Success to draftLoan.copy(id = newId)
        }

        val savingsRepo = savingsRepository
            ?: return LoanResult.Error("Módulo de ahorros no configurado") to null

        return loanMutex.withLock {
            val allSavings = savingsRepo.getAllSavings().first()
            val allExpenses = expenseRepository?.getAllExpenses()?.first() ?: emptyList()
            val allLoans = loanRepository.getAllLoans().first()
            val matchingSavings = allSavings.filter {
                it.amount.isFinite() && it.amount > 0.0 &&
                    it.category.trim { ch -> ch.isWhitespace() }.equals(trimmedSavingCategory, ignoreCase = true)
            }
            val matchingRepaymentCategory = allLoans
                .flatMap { it.payments }
                .firstOrNull {
                    it.amount.isFinite() && it.amount > 0.0 &&
                        it.savingCategory.trim { ch -> ch.isWhitespace() }.equals(trimmedSavingCategory, ignoreCase = true)
                }?.savingCategory?.trim { it.isWhitespace() }
            val available = calculateCategoryAvailableSavings(
                categoryName = trimmedSavingCategory,
                savings = allSavings,
                expenses = allExpenses,
                loans = allLoans
            )
            val canonicalCategory = matchingSavings.firstOrNull()?.category?.trim { it.isWhitespace() }
                ?: matchingRepaymentCategory
            if (canonicalCategory == null || BigDecimal.valueOf(amountLent) > available) {
                return@withLock LoanResult.Error("Saldo insuficiente en la categoría de ahorro") to null
            }

            val draftLoan = Loan(
                personName = trimmedPerson,
                amountLent = amountLent,
                amountToReceive = amountToReceive,
                note = trimmedNote,
                savingCategory = canonicalCategory,
                installments = validatedInstallments
            )
            val newId = loanRepository.insertLoan(draftLoan)
            LoanResult.Success to draftLoan.copy(id = newId)
        }
    }

    suspend fun recordLoanPayment(
        loanId: Long,
        amountInput: String,
        paidAtInput: String,
        savingCategoryInput: String = "",
        targetInstallmentId: Long? = null,
        scheduleOption: PartialPaymentScheduleOption = PartialPaymentScheduleOption.KeepCurrent
    ): LoanResult {
        val amount = parseAmountInput(amountInput)
        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            return LoanResult.Error("El monto del pago debe ser mayor a cero")
        }

        val trimmedPaidAt = paidAtInput.trim { it.isWhitespace() }
        if (!isValidLoanPaymentDateTime(trimmedPaidAt)) {
            return LoanResult.Error("La fecha y hora del pago no son válidas")
        }

        val savingsRepo = savingsRepository
            ?: return LoanResult.Error("Módulo de ahorros no configurado")

        return loanMutex.withLock {
            val allLoans = loanRepository.getAllLoans().first()
            val loan = allLoans.find { it.id == loanId }
                ?: return@withLock LoanResult.Error("Préstamo no encontrado")

            val pendingBefore = BigDecimal.valueOf(loan.pendingAmount)
            if (pendingBefore <= BigDecimal.ZERO) {
                return@withLock LoanResult.Error("El préstamo ya está saldado")
            }

            val paymentDec = BigDecimal.valueOf(amount)
            if (paymentDec > pendingBefore) {
                return@withLock LoanResult.Error("El monto del pago supera el saldo pendiente")
            }

            val unpaidBefore = loan.installments
                .filter { !it.isPaid }
                .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
            val resolvedTargetInstallment = if (unpaidBefore.isNotEmpty()) {
                if (targetInstallmentId != null) {
                    unpaidBefore.find { it.id == targetInstallmentId }
                        ?: return@withLock LoanResult.Error("Debes seleccionar una cuota pendiente válida")
                } else {
                    unpaidBefore.first()
                }
            } else {
                null
            }

            val categories = savingsRepo.getAllCategories().first()
            val destinationCategory = if (loan.savingCategory.trim { it.isWhitespace() }.isNotEmpty()) {
                val origin = loan.savingCategory.trim { it.isWhitespace() }
                categories.find { it.name.trim { ch -> ch.isWhitespace() }.equals(origin, ignoreCase = true) }
                    ?.name?.trim { it.isWhitespace() } ?: origin
            } else {
                val chosen = savingCategoryInput.trim { it.isWhitespace() }
                if (chosen.isEmpty()) {
                    return@withLock LoanResult.Error("Debes seleccionar una categoría de ahorro destino")
                }
                val matched = categories.find {
                    it.name.trim { ch -> ch.isWhitespace() }.equals(chosen, ignoreCase = true)
                } ?: return@withLock LoanResult.Error("La categoría de ahorro seleccionada no existe")
                matched.name.trim { it.isWhitespace() }
            }

            val remainingAfter = pendingBefore.subtract(paymentDec).max(BigDecimal.ZERO)
            val (updatedInstallments, scheduleError) = resolveUpdatedInstallmentsForPayment(
                loan = loan,
                paymentAmount = amount,
                targetInstallmentId = resolvedTargetInstallment?.id,
                remainingAfter = remainingAfter,
                scheduleOption = scheduleOption
            )
            if (scheduleError != null) {
                return@withLock LoanResult.Error(scheduleError)
            }

            loanRepository.recordPayment(
                payment = LoanPayment(
                    loanId = loan.id,
                    amount = amount,
                    paidAt = trimmedPaidAt,
                    savingCategory = destinationCategory,
                    installmentId = resolvedTargetInstallment?.id
                ),
                updatedInstallments = updatedInstallments.map { it.copy(loanId = loan.id) }
            )
            LoanResult.Success
        }
    }

    fun recomputeInstallmentsWithoutPayment(loan: Loan, excludedPaymentId: Long): List<LoanInstallment> {
        val baseInstallments = loan.installments
            .map { it.copy(paymentAmount = 0.0) }
            .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
        if (baseInstallments.isEmpty()) return emptyList()
        val remainingPayments = loan.payments
            .filterNot { it.id == excludedPaymentId }
            .sortedWith(compareBy<LoanPayment> { it.paidAt }.thenBy { it.id })
        var runningLoan = loan.copy(installments = baseInstallments, payments = emptyList())
        for (payment in remainingPayments) {
            if (!payment.amount.isFinite() || payment.amount <= 0.0) continue
            val nextInstallments = previewLoanInstallmentsAfterPayment(
                loan = runningLoan,
                paymentAmount = payment.amount,
                targetInstallmentId = payment.installmentId
            )
            runningLoan = runningLoan.copy(
                installments = nextInstallments,
                payments = runningLoan.payments + payment
            )
        }
        return runningLoan.installments
    }

    suspend fun updateLoanPayment(
        loanId: Long,
        paymentId: Long,
        amountInput: String,
        paidAtInput: String,
        savingCategoryInput: String = "",
        targetInstallmentId: Long? = null,
        scheduleOption: PartialPaymentScheduleOption = PartialPaymentScheduleOption.KeepCurrent
    ): LoanResult {
        val amount = parseAmountInput(amountInput)
        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            return LoanResult.Error("El monto del pago debe ser mayor a cero")
        }

        val trimmedPaidAt = paidAtInput.trim { it.isWhitespace() }
        if (!isValidLoanPaymentDateTime(trimmedPaidAt)) {
            return LoanResult.Error("La fecha y hora del pago no son válidas")
        }

        val savingsRepo = savingsRepository
            ?: return LoanResult.Error("Módulo de ahorros no configurado")

        return loanMutex.withLock {
            val allLoans = loanRepository.getAllLoans().first()
            val loan = allLoans.find { it.id == loanId }
                ?: return@withLock LoanResult.Error("Préstamo no encontrado")
            val existingPayment = loan.payments.find { it.id == paymentId }
                ?: return@withLock LoanResult.Error("Pago no encontrado")

            val otherPayments = loan.payments.filterNot { it.id == paymentId }
            val otherPaidDec = otherPayments
                .filter { it.amount.isFinite() && it.amount > 0.0 }
                .map { it.amount }
                .sumBigDecimal()
            val maxAllowed = BigDecimal.valueOf(loan.amountToReceive)
                .subtract(otherPaidDec)
                .max(BigDecimal.ZERO)
            val paymentDec = BigDecimal.valueOf(amount)
            if (paymentDec > maxAllowed) {
                return@withLock LoanResult.Error("El monto del pago supera el saldo pendiente")
            }

            val categories = savingsRepo.getAllCategories().first()
            val destinationCategory = if (loan.savingCategory.trim { it.isWhitespace() }.isNotEmpty()) {
                val origin = loan.savingCategory.trim { it.isWhitespace() }
                categories.find { it.name.trim { ch -> ch.isWhitespace() }.equals(origin, ignoreCase = true) }
                    ?.name?.trim { it.isWhitespace() } ?: origin
            } else {
                val chosen = savingCategoryInput.trim { it.isWhitespace() }
                if (chosen.isEmpty()) {
                    return@withLock LoanResult.Error("Debes seleccionar una categoría de ahorro destino")
                }
                val matched = categories.find {
                    it.name.trim { ch -> ch.isWhitespace() }.equals(chosen, ignoreCase = true)
                } ?: return@withLock LoanResult.Error("La categoría de ahorro seleccionada no existe")
                matched.name.trim { it.isWhitespace() }
            }

            val updatedPayment = existingPayment.copy(
                amount = amount,
                paidAt = trimmedPaidAt,
                savingCategory = destinationCategory
            )
            val allSavings = savingsRepo.getAllSavings().first()
            val allExpenses = expenseRepository?.getAllExpenses()?.first() ?: emptyList()
            val simulatedLoans = allLoans.map {
                if (it.id == loanId) it.copy(payments = otherPayments + updatedPayment) else it
            }
            val oldCat = existingPayment.savingCategory.trim { it.isWhitespace() }
            if (oldCat.isNotEmpty()) {
                val inflowsBefore = calculateCategorySavingsInflows(oldCat, allSavings, allLoans)
                val inflowsAfter = calculateCategorySavingsInflows(oldCat, allSavings, simulatedLoans)
                val outflows = calculateCategorySavingsOutflows(oldCat, allExpenses, allLoans)
                if (inflowsAfter < inflowsBefore && outflows > inflowsAfter) {
                    return@withLock LoanResult.Error("No se puede modificar el pago porque el ahorro acreditado respalda gastos o préstamos registrados")
                }
            }

            val installmentsAfterOthers = recomputeInstallmentsWithoutPayment(loan, paymentId)
            val unpaidAfterOthers = installmentsAfterOthers
                .filter { !it.isPaid }
                .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
            val resolvedTargetInstallment = if (unpaidAfterOthers.isNotEmpty()) {
                if (targetInstallmentId != null) {
                    unpaidAfterOthers.find { it.id == targetInstallmentId }
                        ?: return@withLock LoanResult.Error("Debes seleccionar una cuota pendiente válida")
                } else {
                    unpaidAfterOthers.first()
                }
            } else {
                null
            }

            val loanForApply = loan.copy(payments = otherPayments, installments = installmentsAfterOthers)
            val remainingAfter = maxAllowed.subtract(paymentDec).max(BigDecimal.ZERO)
            val (updatedInstallments, scheduleError) = resolveUpdatedInstallmentsForPayment(
                loan = loanForApply,
                paymentAmount = amount,
                targetInstallmentId = resolvedTargetInstallment?.id,
                remainingAfter = remainingAfter,
                scheduleOption = scheduleOption
            )
            if (scheduleError != null) {
                return@withLock LoanResult.Error(scheduleError)
            }

            loanRepository.updatePayment(
                payment = updatedPayment.copy(installmentId = resolvedTargetInstallment?.id),
                updatedInstallments = updatedInstallments.map { it.copy(loanId = loan.id) }
            )
            LoanResult.Success
        }
    }

    suspend fun deleteLoanPayment(loanId: Long, paymentId: Long): LoanResult {
        return loanMutex.withLock {
            val allLoans = loanRepository.getAllLoans().first()
            val loan = allLoans.find { it.id == loanId }
                ?: return@withLock LoanResult.Error("Préstamo no encontrado")
            val existingPayment = loan.payments.find { it.id == paymentId }
                ?: return@withLock LoanResult.Error("Pago no encontrado")

            val remainingPayments = loan.payments.filterNot { it.id == paymentId }
            val savingsRepo = savingsRepository
            if (savingsRepo != null && existingPayment.savingCategory.trim { it.isWhitespace() }.isNotEmpty()) {
                val allSavings = savingsRepo.getAllSavings().first()
                val allExpenses = expenseRepository?.getAllExpenses()?.first() ?: emptyList()
                val simulatedLoans = allLoans.map {
                    if (it.id == loanId) it.copy(payments = remainingPayments) else it
                }
                val cat = existingPayment.savingCategory.trim { it.isWhitespace() }
                val outflows = calculateCategorySavingsOutflows(cat, allExpenses, allLoans)
                val inflowsAfter = calculateCategorySavingsInflows(cat, allSavings, simulatedLoans)
                if (outflows > inflowsAfter) {
                    return@withLock LoanResult.Error("No se puede eliminar el pago porque el ahorro acreditado respalda gastos o préstamos registrados")
                }
            }

            val updatedInstallments = recomputeInstallmentsWithoutPayment(loan, paymentId)
            loanRepository.deletePayment(
                loanId = loanId,
                paymentId = paymentId,
                updatedInstallments = updatedInstallments.map { it.copy(loanId = loanId) }
            )
            LoanResult.Success
        }
    }

    private fun resolveUpdatedInstallmentsForPayment(
        loan: Loan,
        paymentAmount: Double,
        targetInstallmentId: Long?,
        remainingAfter: BigDecimal,
        scheduleOption: PartialPaymentScheduleOption
    ): Pair<List<LoanInstallment>, String?> {
        val afterApplying = previewLoanInstallmentsAfterPayment(loan, paymentAmount, targetInstallmentId)
        if (remainingAfter <= BigDecimal.ZERO) {
            return afterApplying to null
        }

        val today = todayIso()
        val settledOnes = afterApplying.filter { it.isPaid }
        val unpaidAfter = afterApplying.filter { !it.isPaid }

        return when (scheduleOption) {
            is PartialPaymentScheduleOption.KeepCurrent -> {
                afterApplying to null
            }
            is PartialPaymentScheduleOption.SetNewDueDate -> {
                val trimmedDate = scheduleOption.dueDate.trim { it.isWhitespace() }
                val dateError = validateFutureDate(trimmedDate, today)
                if (dateError != null) {
                    emptyList<LoanInstallment>() to dateError
                } else {
                    val finalizedPaidFromUnpaid = unpaidAfter.mapNotNull { inst ->
                        val paidDec = BigDecimal.valueOf(inst.paymentAmount)
                        if (paidDec > BigDecimal.ZERO) {
                            inst.copy(expectedAmount = paidDec.toDouble())
                        } else {
                            null
                        }
                    }
                    val nextInst = LoanInstallment(
                        loanId = loan.id,
                        dueDate = trimmedDate,
                        expectedAmount = remainingAfter.toDouble(),
                        paymentAmount = 0.0
                    )
                    (settledOnes + finalizedPaidFromUnpaid + nextInst)
                        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id }) to null
                }
            }
            is PartialPaymentScheduleOption.AddInstallment -> {
                val trimmedDate = scheduleOption.dueDate.trim { it.isWhitespace() }
                val dateError = validateFutureDate(trimmedDate, today)
                if (dateError != null) {
                    return emptyList<LoanInstallment>() to dateError
                }
                val extraAmount = parseAmountInput(scheduleOption.amountInput)
                if (extraAmount == null || !extraAmount.isFinite() || extraAmount <= 0.0) {
                    return emptyList<LoanInstallment>() to "El monto de la nueva cuota debe ser mayor a cero"
                }
                val extraDec = BigDecimal.valueOf(extraAmount)
                if (extraDec > remainingAfter) {
                    return emptyList<LoanInstallment>() to "El monto de la nueva cuota supera el saldo pendiente"
                }

                var toDeduct = extraDec
                val adjustedUnpaid = mutableListOf<LoanInstallment>()
                for (inst in unpaidAfter) {
                    val paidDec = BigDecimal.valueOf(inst.paymentAmount)
                    val remDec = BigDecimal.valueOf(inst.remainingAmount)
                    if (toDeduct <= BigDecimal.ZERO) {
                        adjustedUnpaid.add(inst)
                    } else if (remDec <= toDeduct) {
                        toDeduct = toDeduct.subtract(remDec)
                        if (paidDec > BigDecimal.ZERO) {
                            adjustedUnpaid.add(inst.copy(expectedAmount = paidDec.toDouble()))
                        }
                    } else {
                        val newRem = remDec.subtract(toDeduct)
                        toDeduct = BigDecimal.ZERO
                        adjustedUnpaid.add(inst.copy(expectedAmount = paidDec.add(newRem).toDouble()))
                    }
                }
                val added = LoanInstallment(
                    loanId = loan.id,
                    dueDate = trimmedDate,
                    expectedAmount = extraDec.toDouble(),
                    paymentAmount = 0.0
                )
                (settledOnes + adjustedUnpaid + added)
                    .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id }) to null
            }
            is PartialPaymentScheduleOption.AdjustNextInstallment -> {
                if (unpaidAfter.isEmpty()) {
                    return emptyList<LoanInstallment>() to "No hay una siguiente cuota para ajustar"
                }
                val adjustedRemaining = parseAmountInput(scheduleOption.amountInput)
                if (adjustedRemaining == null || !adjustedRemaining.isFinite() || adjustedRemaining <= 0.0) {
                    return emptyList<LoanInstallment>() to "El monto de la siguiente cuota debe ser mayor a cero"
                }
                val adjustedDec = BigDecimal.valueOf(adjustedRemaining)
                if (adjustedDec > remainingAfter) {
                    return emptyList<LoanInstallment>() to "El monto de la siguiente cuota supera el saldo pendiente"
                }
                val first = unpaidAfter.first()
                val firstPaid = BigDecimal.valueOf(first.paymentAmount)
                val updatedFirst = first.copy(expectedAmount = firstPaid.add(adjustedDec).toDouble())

                if (adjustedDec.compareTo(remainingAfter) == 0) {
                    val finalizedTail = unpaidAfter.drop(1).mapNotNull { inst ->
                        val paidDec = BigDecimal.valueOf(inst.paymentAmount)
                        if (paidDec > BigDecimal.ZERO) inst.copy(expectedAmount = paidDec.toDouble()) else null
                    }
                    return (settledOnes + updatedFirst + finalizedTail)
                        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id }) to null
                }

                val restTarget = remainingAfter.subtract(adjustedDec)
                if (unpaidAfter.size == 1) {
                    val trimmedRemDate = scheduleOption.remainderDueDate.trim { it.isWhitespace() }
                    val dateError = validateFutureDate(trimmedRemDate, today)
                    if (dateError != null) {
                        return emptyList<LoanInstallment>() to dateError
                    }
                    val remInst = LoanInstallment(
                        loanId = loan.id,
                        dueDate = trimmedRemDate,
                        expectedAmount = restTarget.toDouble(),
                        paymentAmount = 0.0
                    )
                    (settledOnes + updatedFirst + remInst)
                        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id }) to null
                } else {
                    val tail = unpaidAfter.drop(1)
                    val tailRemSum = tail.map { it.remainingAmount }.sumBigDecimal()
                    val rebalancedTail = if (restTarget >= tailRemSum) {
                        val extra = restTarget.subtract(tailRemSum)
                        tail.mapIndexed { idx, inst ->
                            if (idx == tail.lastIndex) {
                                inst.copy(expectedAmount = BigDecimal.valueOf(inst.expectedAmount).add(extra).toDouble())
                            } else {
                                inst
                            }
                        }
                    } else {
                        var budget = restTarget
                        val kept = mutableListOf<LoanInstallment>()
                        for (inst in tail) {
                            val paidDec = BigDecimal.valueOf(inst.paymentAmount)
                            val remDec = BigDecimal.valueOf(inst.remainingAmount)
                            if (budget <= BigDecimal.ZERO) {
                                if (paidDec > BigDecimal.ZERO) {
                                    kept.add(inst.copy(expectedAmount = paidDec.toDouble()))
                                }
                            } else if (remDec <= budget) {
                                kept.add(inst)
                                budget = budget.subtract(remDec)
                            } else {
                                kept.add(inst.copy(expectedAmount = paidDec.add(budget).toDouble()))
                                budget = BigDecimal.ZERO
                            }
                        }
                        kept
                    }
                    (settledOnes + updatedFirst + rebalancedTail)
                        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id }) to null
                }
            }
        }
    }

    private fun validateFutureDate(trimmedDate: String, today: String): String? {
        if (!isValidLoanIsoDate(trimmedDate)) {
            return "La fecha de la cuota no es válida"
        }
        if (!isValidLoanIsoDate(today) || trimmedDate < today) {
            return "No se pueden programar cuotas con fecha anterior a hoy"
        }
        return null
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
            expectedTargetAmount = existingLoan.pendingAmount,
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

    suspend fun updateLoan(
        loanId: Long,
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

        val trimmedNote = noteInput.trim { it.isWhitespace() }
        val trimmedSavingCategory = savingCategoryInput.trim { it.isWhitespace() }

        return loanMutex.withLock {
            val allLoans = loanRepository.getAllLoans().first()
            val existingLoan = allLoans.find { it.id == loanId }
                ?: return@withLock LoanResult.Error("Préstamo no encontrado")

            val existingById = existingLoan.installments
                .filter { it.id > 0L }
                .associateBy { it.id }

            val pendingTarget = BigDecimal.valueOf(amountToReceive)
                .subtract(BigDecimal.valueOf(existingLoan.paidAmount))
                .max(BigDecimal.ZERO)
                .toDouble()

            val (validatedInstallments, scheduleError) = validateScheduleForLoan(
                inputs = installmentInputs,
                expectedTargetAmount = pendingTarget,
                existingById = existingById
            )
            if (scheduleError != null) {
                return@withLock LoanResult.Error(scheduleError)
            }

            val resolvedCategory = if (trimmedSavingCategory.isEmpty()) {
                ""
            } else {
                val savingsRepo = savingsRepository
                    ?: return@withLock LoanResult.Error("Módulo de ahorros no configurado")
                val allSavings = savingsRepo.getAllSavings().first()
                val allExpenses = expenseRepository?.getAllExpenses()?.first() ?: emptyList()
                val matchingSavings = allSavings.filter {
                    it.amount.isFinite() && it.amount > 0.0 &&
                        it.category.trim { ch -> ch.isWhitespace() }.equals(trimmedSavingCategory, ignoreCase = true)
                }
                val matchingRepaymentCategory = allLoans
                    .flatMap { it.payments }
                    .firstOrNull {
                        it.amount.isFinite() && it.amount > 0.0 &&
                            it.savingCategory.trim { ch -> ch.isWhitespace() }.equals(trimmedSavingCategory, ignoreCase = true)
                    }?.savingCategory?.trim { it.isWhitespace() }
                val available = calculateCategoryAvailableSavings(
                    categoryName = trimmedSavingCategory,
                    savings = allSavings,
                    expenses = allExpenses,
                    loans = allLoans,
                    excludeLoanId = loanId
                )
                val canonicalCategory = matchingSavings.firstOrNull()?.category?.trim { it.isWhitespace() }
                    ?: matchingRepaymentCategory
                if (canonicalCategory == null || BigDecimal.valueOf(amountLent) > available) {
                    return@withLock LoanResult.Error("Saldo insuficiente en la categoría de ahorro")
                }
                canonicalCategory
            }

            loanRepository.updateLoan(
                Loan(
                    id = loanId,
                    personName = trimmedPerson,
                    amountLent = amountLent,
                    amountToReceive = amountToReceive,
                    note = trimmedNote,
                    savingCategory = resolvedCategory,
                    installments = validatedInstallments.map { it.copy(loanId = loanId) },
                    payments = existingLoan.payments
                )
            )
            LoanResult.Success
        }
    }

    suspend fun deleteLoan(loanId: Long): LoanResult {
        return loanMutex.withLock {
            val exists = loanRepository.getAllLoans().first().any { it.id == loanId }
            if (!exists) {
                return@withLock LoanResult.Error("Préstamo no encontrado")
            }
            loanRepository.deleteLoan(loanId)
            LoanResult.Success
        }
    }

    private fun validateScheduleForLoan(
        inputs: List<LoanInstallmentInput>,
        expectedTargetAmount: Double,
        existingById: Map<Long, LoanInstallment>
    ): Pair<List<LoanInstallment>, String?> {
        if (inputs.isEmpty()) return emptyList<LoanInstallment>() to null

        val today = todayIso()
        val parsed = mutableListOf<LoanInstallment>()
        for (input in inputs) {
            val existing = existingById[input.persistedId]
            val allowPast = existing != null &&
                existing.dueDate.trim { it.isWhitespace() } == input.dueDate.trim { it.isWhitespace() }
            val resolvedPaymentAmount = existing?.paymentAmount ?: input.paymentAmount
            val (item, error) = parseAndValidateInstallment(
                input = input.copy(paymentAmount = resolvedPaymentAmount),
                today = today,
                allowPersistedPastDate = allowPast
            )
            if (error != null || item == null) {
                return emptyList<LoanInstallment>() to (error ?: "Datos de cuota inválidos")
            }
            parsed.add(item)
        }

        val totalRemainingScheduled = parsed.map { it.remainingAmount }.sumBigDecimal()
        val expectedTotal = BigDecimal.valueOf(expectedTargetAmount)
        if (totalRemainingScheduled.compareTo(expectedTotal) != 0) {
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
        val paidAmount = if (input.paymentAmount.isFinite() && input.paymentAmount > 0.0) {
            input.paymentAmount
        } else {
            0.0
        }
        if (BigDecimal.valueOf(expectedAmount) < BigDecimal.valueOf(paidAmount)) {
            return null to "El monto esperado no puede ser menor al monto ya pagado de la cuota"
        }
        return LoanInstallment(
            id = input.persistedId,
            dueDate = trimmedDate,
            expectedAmount = expectedAmount,
            paymentAmount = paidAmount
        ) to null
    }
}
