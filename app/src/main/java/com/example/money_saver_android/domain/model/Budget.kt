package com.example.money_saver_android.domain.model

data class Budget(
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val subcategory: String = "",
    val periodId: Long
)
