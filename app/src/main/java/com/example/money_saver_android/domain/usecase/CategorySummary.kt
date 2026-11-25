package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import kotlin.math.roundToInt

data class CategorySummaryItem(
    val categoryName: String,
    val colorHex: String,
    val amount: Double,
    val proportion: Float,
    val percentage: Int
)

data class PeriodCategorySummary(
    val totalAmount: Double = 0.0,
    val items: List<CategorySummaryItem> = emptyList()
) {
    val isEmpty: Boolean
        get() = items.isEmpty() || totalAmount <= 0.0
}

fun <T> calculateValidPeriodTotal(
    entries: List<T>,
    categorySelector: (T) -> String,
    amountSelector: (T) -> Double
): Double {
    val validEntries = entries.filter { entry ->
        val amount = amountSelector(entry)
        amount.isFinite() && amount > 0.0 && categorySelector(entry).trim().isNotEmpty()
    }
    if (validEntries.isEmpty()) return 0.0

    val total = validEntries.map(amountSelector).sumBigDecimal().toDouble()
    return if (total.isFinite() && total > 0.0) total else 0.0
}

fun <T> buildPeriodCategorySummary(
    entries: List<T>,
    categories: List<Category>,
    categorySelector: (T) -> String,
    amountSelector: (T) -> Double,
    defaultColorHex: String = "#4E73DF"
): PeriodCategorySummary {
    val validEntries = entries.filter { entry ->
        val amount = amountSelector(entry)
        amount.isFinite() && amount > 0.0 && categorySelector(entry).trim().isNotEmpty()
    }
    if (validEntries.isEmpty()) return PeriodCategorySummary()

    val total = validEntries.map(amountSelector).sumBigDecimal().toDouble()
    if (!total.isFinite() || total <= 0.0) return PeriodCategorySummary()

    val items = validEntries
        .groupBy { categorySelector(it).trim().lowercase() }
        .map { (_, group) ->
            val rawName = categorySelector(group.first()).trim()
            val matchedCategory = categories.find { it.name.trim().equals(rawName, ignoreCase = true) }
            val resolvedName = matchedCategory?.name?.trim()?.ifEmpty { rawName } ?: rawName
            val resolvedColor = matchedCategory?.color?.trim()?.ifEmpty { defaultColorHex } ?: defaultColorHex
            val amount = group.map(amountSelector).sumBigDecimal().toDouble()
            val ratio = (amount / total).coerceIn(0.0, 1.0)
            CategorySummaryItem(
                categoryName = resolvedName,
                colorHex = resolvedColor,
                amount = amount,
                proportion = ratio.toFloat(),
                percentage = (ratio * 100.0).roundToInt()
            )
        }
        .sortedByDescending { it.amount }

    return PeriodCategorySummary(totalAmount = total, items = items)
}
