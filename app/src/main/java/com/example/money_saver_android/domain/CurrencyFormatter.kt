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

    fun formatValue(value: String, currency: Currency): String {
        val pattern = currency.formatPattern
        val allowsDecimals = pattern.contains(".")
        val maxDecimals = if (allowsDecimals) pattern.substringAfter(".").length else 0

        val clean = value.replace(",", "").trim()
        if (clean.isEmpty()) return ""

        val parts = clean.split(".", limit = 2)
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else null
        val endsWithDot = clean.endsWith(".")

        val formattedInt = StringBuilder()
        val len = intPart.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % 3 == 0) {
                formattedInt.append(',')
            }
            formattedInt.append(intPart[i])
        }

        if (allowsDecimals) {
            if (decPart != null) {
                formattedInt.append('.').append(decPart.take(maxDecimals))
            } else if (endsWithDot) {
                formattedInt.append('.')
            }
        }

        return formattedInt.toString()
    }
}
