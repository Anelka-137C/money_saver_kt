package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToInt

data class SavingsCategoryBalance(
    val categoryName: String,
    val colorHex: String,
    val totalSaved: Double,
    val totalSpent: Double,
    val netBalance: Double,
    val totalLoaned: Double = 0.0,
    val totalLoanRepaid: Double = 0.0
)

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
        get() = items.isEmpty() || !totalAmount.isFinite() || totalAmount <= 0.0
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
        .groupBy { categorySelector(it).trim().lowercase(Locale.ROOT) }
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

fun calculateCategorySavingsOutflows(
    categoryName: String,
    expenses: List<Expense>,
    loans: List<Loan> = emptyList(),
    excludeExpenseId: Long? = null,
    excludeLoanId: Long? = null
): BigDecimal {
    val normalized = categoryName.trim()
    if (normalized.isEmpty()) return BigDecimal.ZERO
    val spent = expenses
        .filter {
            it.fundingSource == ExpenseFundingSource.SAVINGS &&
                (excludeExpenseId == null || it.id != excludeExpenseId) &&
                it.amount.isFinite() &&
                it.amount > 0.0 &&
                it.savingCategory.trim().equals(normalized, ignoreCase = true)
        }
        .map { it.amount }
        .sumBigDecimal()
    val loaned = loans
        .filter {
            (excludeLoanId == null || it.id != excludeLoanId) &&
                it.amountLent.isFinite() &&
                it.amountLent > 0.0 &&
                it.savingCategory.trim().equals(normalized, ignoreCase = true)
        }
        .map { it.amountLent }
        .sumBigDecimal()
    return spent.add(loaned)
}

fun calculateCategorySavingsInflows(
    categoryName: String,
    savings: List<Saving>,
    loans: List<Loan> = emptyList()
): BigDecimal {
    val normalized = categoryName.trim()
    if (normalized.isEmpty()) return BigDecimal.ZERO
    val totalSaved = savings
        .filter {
            it.amount.isFinite() &&
                it.amount > 0.0 &&
                it.category.trim().equals(normalized, ignoreCase = true)
        }
        .map { it.amount }
        .sumBigDecimal()
    val totalRepaid = loans
        .flatMap { it.payments }
        .filter {
            it.amount.isFinite() &&
                it.amount > 0.0 &&
                it.savingCategory.trim().equals(normalized, ignoreCase = true)
        }
        .map { it.amount }
        .sumBigDecimal()
    return totalSaved.add(totalRepaid)
}

fun calculateCategoryAvailableSavings(
    categoryName: String,
    savings: List<Saving>,
    expenses: List<Expense>,
    loans: List<Loan> = emptyList(),
    excludeExpenseId: Long? = null,
    excludeLoanId: Long? = null
): BigDecimal {
    val normalized = categoryName.trim()
    if (normalized.isEmpty()) return BigDecimal.ZERO
    val totalInflows = calculateCategorySavingsInflows(
        categoryName = normalized,
        savings = savings,
        loans = loans
    )
    val totalOutflows = calculateCategorySavingsOutflows(
        categoryName = normalized,
        expenses = expenses,
        loans = loans,
        excludeExpenseId = excludeExpenseId,
        excludeLoanId = excludeLoanId
    )
    return totalInflows.subtract(totalOutflows).max(BigDecimal.ZERO)
}

fun calculateSavingsNetBalances(
    savings: List<Saving>,
    expenses: List<Expense>,
    categories: List<Category> = emptyList(),
    excludeExpenseId: Long? = null,
    defaultColorHex: String = "#3B82F6",
    loans: List<Loan> = emptyList(),
    excludeLoanId: Long? = null
): List<SavingsCategoryBalance> {
    val validSavings = savings.filter { it.amount.isFinite() && it.amount > 0.0 && it.category.trim().isNotEmpty() }
    val validLoanPayments = loans
        .flatMap { it.payments }
        .filter { it.amount.isFinite() && it.amount > 0.0 && it.savingCategory.trim().isNotEmpty() }
    if (validSavings.isEmpty() && validLoanPayments.isEmpty()) return emptyList()

    val savedByKey = validSavings.groupBy { it.category.trim().lowercase(Locale.ROOT) }
    val repaidByKey = validLoanPayments.groupBy { it.savingCategory.trim().lowercase(Locale.ROOT) }

    val spentByKey = expenses
        .filter {
            it.fundingSource == ExpenseFundingSource.SAVINGS &&
                (excludeExpenseId == null || it.id != excludeExpenseId) &&
                it.amount.isFinite() &&
                it.amount > 0.0 &&
                it.savingCategory.trim().isNotEmpty()
        }
        .groupBy { it.savingCategory.trim().lowercase(Locale.ROOT) }
        .mapValues { (_, list) -> list.map { it.amount }.sumBigDecimal() }

    val loanedByKey = loans
        .filter {
            (excludeLoanId == null || it.id != excludeLoanId) &&
                it.amountLent.isFinite() &&
                it.amountLent > 0.0 &&
                it.savingCategory.trim().isNotEmpty()
        }
        .groupBy { it.savingCategory.trim().lowercase(Locale.ROOT) }
        .mapValues { (_, list) -> list.map { it.amountLent }.sumBigDecimal() }

    val allKeys = (savedByKey.keys + repaidByKey.keys).distinct()
    return allKeys
        .map { key ->
            val savedGroup = savedByKey[key].orEmpty()
            val repaidGroup = repaidByKey[key].orEmpty()
            val rawName = savedGroup.firstOrNull()?.category?.trim()
                ?: repaidGroup.first().savingCategory.trim()
            val matched = categories.find { it.name.trim().equals(rawName, ignoreCase = true) }
            val resolvedName = matched?.name?.trim()?.ifEmpty { rawName } ?: rawName
            val resolvedColor = matched?.color?.trim()?.ifEmpty { defaultColorHex } ?: defaultColorHex
            val savedDec = savedGroup.map { it.amount }.sumBigDecimal()
            val repaidDec = repaidGroup.map { it.amount }.sumBigDecimal()
            val spentDec = spentByKey[key] ?: BigDecimal.ZERO
            val loanedDec = loanedByKey[key] ?: BigDecimal.ZERO
            val netDec = savedDec.add(repaidDec).subtract(spentDec).subtract(loanedDec).max(BigDecimal.ZERO)
            SavingsCategoryBalance(
                categoryName = resolvedName,
                colorHex = resolvedColor,
                totalSaved = savedDec.toDouble().takeIf { it.isFinite() } ?: 0.0,
                totalSpent = spentDec.toDouble().takeIf { it.isFinite() } ?: 0.0,
                netBalance = netDec.toDouble().takeIf { it.isFinite() } ?: 0.0,
                totalLoaned = loanedDec.toDouble().takeIf { it.isFinite() } ?: 0.0,
                totalLoanRepaid = repaidDec.toDouble().takeIf { it.isFinite() } ?: 0.0
            )
        }
        .sortedWith(compareByDescending<SavingsCategoryBalance> { it.netBalance }.thenBy { it.categoryName.lowercase(Locale.ROOT) })
}

fun buildSavingsNetSummary(
    savings: List<Saving>,
    expenses: List<Expense>,
    categories: List<Category>,
    defaultColorHex: String = "#3B82F6",
    loans: List<Loan> = emptyList()
): PeriodCategorySummary {
    val balances = calculateSavingsNetBalances(savings, expenses, categories, defaultColorHex = defaultColorHex, loans = loans)
        .filter { it.netBalance > 0.0 }
    return buildPeriodCategorySummary(
        entries = balances,
        categories = categories,
        categorySelector = { it.categoryName },
        amountSelector = { it.netBalance },
        defaultColorHex = defaultColorHex
    )
}
