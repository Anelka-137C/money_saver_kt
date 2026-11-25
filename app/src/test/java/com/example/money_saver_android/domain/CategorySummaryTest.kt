package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.buildPeriodCategorySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySummaryTest {

    private val categories = listOf(
        Category("Employment", "💼", "#1CC88A", listOf(Subcategory("Salary"), Subcategory("Bonus"))),
        Category("Investments", "📈", "#3B82F6", listOf(Subcategory("Dividends")))
    )

    @Test
    fun `consolidates subcategories into main category with color and percentages`() {
        val entries = listOf(
            Income(1, 600.0, "2026-05-01 09:00", "Employment", "Salary", "", 1L),
            Income(2, 150.0, "2026-05-02 09:00", " employment ", "Bonus", "", 1L),
            Income(3, 250.0, "2026-05-03 09:00", "Investments", "Dividends", "", 1L)
        )

        val summary = buildPeriodCategorySummary(entries, categories, { it.category }, { it.amount })

        assertFalse(summary.isEmpty)
        assertEquals(1000.0, summary.totalAmount, 0.001)
        assertEquals(2, summary.items.size)
        assertEquals("Employment", summary.items[0].categoryName)
        assertEquals("#1CC88A", summary.items[0].colorHex)
        assertEquals(750.0, summary.items[0].amount, 0.001)
        assertEquals(75, summary.items[0].percentage)
        assertEquals("Investments", summary.items[1].categoryName)
        assertEquals("#3B82F6", summary.items[1].colorHex)
        assertEquals(25, summary.items[1].percentage)
    }

    @Test
    fun `returns empty summary when there are no valid positive entries`() {
        val summary = buildPeriodCategorySummary(emptyList<Income>(), categories, { it.category }, { it.amount })

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.totalAmount, 0.001)
        assertTrue(summary.items.isEmpty())
    }

    @Test
    fun `uses fallback color when category is missing from catalog`() {
        val entries = listOf(Income(1, 100.0, "2026-05-01 09:00", "Custom", "", "", 1L))

        val summary = buildPeriodCategorySummary(
            entries = entries,
            categories = categories,
            categorySelector = { it.category },
            amountSelector = { it.amount },
            defaultColorHex = "#EF4444"
        )

        assertEquals(1, summary.items.size)
        assertEquals("#EF4444", summary.items[0].colorHex)
        assertEquals(100, summary.items[0].percentage)
    }

    @Test
    fun `matches catalog categories with leading or trailing whitespace and returns trimmed name and color`() {
        val messyCatalog = listOf(
            Category(" Employment ", "💼", " #1CC88A ", emptyList())
        )
        val entries = listOf(
            Income(1, 500.0, "2026-05-01 09:00", "employment", "Salary", "", 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = entries,
            categories = messyCatalog,
            categorySelector = { it.category },
            amountSelector = { it.amount },
            defaultColorHex = "#4E73DF"
        )

        assertEquals(1, summary.items.size)
        assertEquals("Employment", summary.items[0].categoryName)
        assertEquals("#1CC88A", summary.items[0].colorHex)
    }
}
