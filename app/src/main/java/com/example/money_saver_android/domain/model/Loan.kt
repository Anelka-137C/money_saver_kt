package com.example.money_saver_android.domain.model

data class LoanInstallment(
    val id: Long = 0,
    val loanId: Long = 0,
    val dueDate: String,
    val expectedAmount: Double
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
    val installments: List<LoanInstallment> = emptyList()
)
