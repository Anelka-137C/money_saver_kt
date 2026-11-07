package com.example.money_saver_android.domain.model

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val subcategory: String
)
