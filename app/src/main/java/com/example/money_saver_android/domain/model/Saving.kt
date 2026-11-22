package com.example.money_saver_android.domain.model

data class Saving(
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val subcategory: String = "",
    val periodId: Long,
    val incomeId: Long = 0L,
    val incomeCategory: String = "",
    val dateTime: String = "",
    val note: String = ""
)
