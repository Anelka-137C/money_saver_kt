package com.example.money_saver_android.domain.model

enum class ExpenseFundingSource {
    BUDGET,
    SAVINGS;

    companion object {
        fun fromStorage(value: String?): ExpenseFundingSource =
            entries.find { it.name.equals(value?.trim(), ignoreCase = true) } ?: BUDGET
    }
}

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val subcategory: String = "",
    val periodId: Long = 0L,
    val budgetId: Long = 0L,
    val budgetCategory: String = "",
    val fundingSource: ExpenseFundingSource = ExpenseFundingSource.BUDGET,
    val savingCategory: String = ""
)
