package com.example.money_saver_android.domain.model

data class Loan(
    val id: Long = 0,
    val personName: String,
    val amountLent: Double,
    val amountToReceive: Double,
    val note: String = "",
    val savingCategory: String = ""
)
