package com.example.money_saver_android.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class LoanInstallment(
    val id: Long = 0,
    val loanId: Long = 0,
    val dueDate: String,
    val expectedAmount: Double,
    val paymentAmount: Double = 0.0
) {
    private val validExpectedDecimal: BigDecimal
        get() = if (expectedAmount.isFinite() && expectedAmount > 0.0) {
            BigDecimal.valueOf(expectedAmount)
        } else {
            BigDecimal.ZERO
        }

    private val validPaidDecimal: BigDecimal
        get() = if (paymentAmount.isFinite() && paymentAmount > 0.0) {
            BigDecimal.valueOf(paymentAmount)
        } else {
            BigDecimal.ZERO
        }

    val remainingAmount: Double
        get() = validExpectedDecimal
            .subtract(validPaidDecimal)
            .max(BigDecimal.ZERO)
            .toDouble()
            .takeIf { it.isFinite() } ?: 0.0

    val isPaid: Boolean
        get() = validExpectedDecimal > BigDecimal.ZERO &&
            validExpectedDecimal.subtract(validPaidDecimal) <= BigDecimal.ZERO
}

data class LoanPayment(
    val id: Long = 0,
    val loanId: Long = 0,
    val amount: Double,
    val paidAt: String,
    val savingCategory: String,
    val installmentId: Long? = null
)

data class LoanDueBadgeInfo(
    val nextDueDate: String,
    val additionalCount: Int,
    val isOverdue: Boolean
)

data class Loan(
    val id: Long = 0,
    val personName: String,
    val amountLent: Double,
    val amountToReceive: Double,
    val note: String = "",
    val savingCategory: String = "",
    val installments: List<LoanInstallment> = emptyList(),
    val payments: List<LoanPayment> = emptyList()
) {
    private val paidDecimal: BigDecimal
        get() = payments
            .filter { it.amount.isFinite() && it.amount > 0.0 }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(BigDecimal.valueOf(item.amount)) }

    val paidAmount: Double
        get() = paidDecimal.toDouble().takeIf { it.isFinite() } ?: 0.0

    val pendingAmount: Double
        get() {
            if (!amountToReceive.isFinite() || amountToReceive <= 0.0) return 0.0
            return BigDecimal.valueOf(amountToReceive)
                .subtract(paidDecimal)
                .max(BigDecimal.ZERO)
                .toDouble()
                .takeIf { it.isFinite() } ?: 0.0
        }

    val isSettled: Boolean
        get() {
            if (!amountToReceive.isFinite() || amountToReceive <= 0.0) return false
            return BigDecimal.valueOf(amountToReceive).subtract(paidDecimal) <= BigDecimal.ZERO
        }

    val progressFraction: Float
        get() {
            if (!amountToReceive.isFinite() || amountToReceive <= 0.0) return 0f
            val target = BigDecimal.valueOf(amountToReceive)
            if (target <= BigDecimal.ZERO) return 0f
            return paidDecimal.divide(target, 6, RoundingMode.HALF_UP)
                .toFloat()
                .coerceIn(0f, 1f)
        }
}
