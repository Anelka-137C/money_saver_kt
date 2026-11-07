package com.example.money_saver_android.data

data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val formatPattern: String
)

val defaultCurrencies = listOf(
    Currency(code = "COP", name = "Peso colombiano", symbol = "$", formatPattern = "#,##0"),
    Currency(code = "BRL", name = "Real brasileño", symbol = "R$", formatPattern = "#,##0.00"),
    Currency(code = "EUR", name = "Euro", symbol = "€", formatPattern = "#,##0.00"),
    Currency(code = "USD", name = "Dólar", symbol = "$", formatPattern = "#,##0.00")
)
