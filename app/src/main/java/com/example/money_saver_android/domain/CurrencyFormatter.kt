package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Currency
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: BigDecimal, currency: Currency): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val decimalFormat = DecimalFormat(currency.formatPattern, symbols)
        return "${currency.symbol} ${decimalFormat.format(amount)}"
    }

    fun format(amount: Double, currency: Currency): String {
        return format(BigDecimal.valueOf(amount), currency)
    }
}
