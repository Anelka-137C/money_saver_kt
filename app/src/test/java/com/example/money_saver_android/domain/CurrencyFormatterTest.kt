package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CurrencyFormatterTest {

    @Test
    fun test_currency_formatter_formats_amount_according_to_iso_4217_rules() {
        val cop = Currency(code = "COP", name = "Peso colombiano", symbol = "$", formatPattern = "#,##0")
        val usd = Currency(code = "USD", name = "Dólar", symbol = "$", formatPattern = "#,##0.00")
        val eur = Currency(code = "EUR", name = "Euro", symbol = "€", formatPattern = "#,##0.00")
        val brl = Currency(code = "BRL", name = "Real brasileño", symbol = "R$", formatPattern = "#,##0.00")

        // COP format with 0 decimals
        val copFormatted = CurrencyFormatter.format(BigDecimal("15000"), cop)
        assertEquals("$ 15,000", copFormatted)

        // USD format with 2 decimals
        val usdFormatted = CurrencyFormatter.format(BigDecimal("15.5"), usd)
        assertEquals("$ 15.50", usdFormatted)

        // EUR format with 2 decimals
        val eurFormatted = CurrencyFormatter.format(BigDecimal("1234.56"), eur)
        assertEquals("€ 1,234.56", eurFormatted)

        // BRL format with 2 decimals
        val brlFormatted = CurrencyFormatter.format(BigDecimal("99.9"), brl)
        assertEquals("R$ 99.90", brlFormatted)

        // Double overload works consistently
        val doubleFormatted = CurrencyFormatter.format(15000.0, cop)
        assertEquals("$ 15,000", doubleFormatted)
    }
}
