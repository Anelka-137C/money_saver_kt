package com.example.money_saver_android.domain.model

data class Income(
    val id: Long = 0,
    val amount: Double,
    val dateTime: String,
    val category: String,
    val subcategory: String,
    val description: String = "",
    val periodId: Long
)
