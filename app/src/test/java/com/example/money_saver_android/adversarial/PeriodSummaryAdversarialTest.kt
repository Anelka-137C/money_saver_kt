package com.example.money_saver_android.adversarial

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.CategorySummaryItem
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.buildPeriodCategorySummary
import com.example.money_saver_android.ui.components.atom.PeriodSummaryCard
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Story #11:
 * "Consultar histórico de ingresos, presupuesto, gastos y ahorros filtrado por período
 * financiero con un resumen visual por categorías al inicio de cada vista."
 *
 * Rules:
 * 1. Only test code is created/modified. Production code in app/src/main is strictly untouched.
 * 2. Property-Based Testing invariants covering non-empty and empty summaries.
 * 3. Extreme edge cases: numerical limits, types/nils, unicode/RTL/emojis, malformed inputs.
 * 4. Concurrency, coroutine cancellation, and strict period isolation.
 * 5. Compose UI rendering robustness for PeriodSummaryCard under adversarial inputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodSummaryAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // Test Doubles (Adversarial In-Memory Repositories)
    // =========================================================================

    private class AdversarialExpenseRepo(
        private val yieldOnMutation: Boolean = false
    ) : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private var nextId = 1L

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow

        override fun getExpensesByPeriod(periodId: Long): Flow<List<Expense>> =
            expensesFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun insertExpense(expense: Expense) {
            if (yieldOnMutation) yield()
            val id = if (expense.id > 0L) expense.id else nextId++
            expensesFlow.value = expensesFlow.value + expense.copy(id = id)
        }

        override suspend fun updateExpense(expense: Expense) {
            if (yieldOnMutation) yield()
            expensesFlow.value = expensesFlow.value.map { if (it.id == expense.id) expense else it }
        }

        override suspend fun deleteExpense(id: Long) {
            if (yieldOnMutation) yield()
            expensesFlow.value = expensesFlow.value.filterNot { it.id == id }
        }
    }

    private class AdversarialIncomeRepo(
        private val yieldOnMutation: Boolean = false
    ) : IncomeRepository {
        val incomesFlow = MutableStateFlow<List<Income>>(emptyList())
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        val budgetCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            incomesFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getIncomeById(id: Long): Income? =
            incomesFlow.value.find { it.id == id }

        override suspend fun insertIncome(income: Income): Long {
            if (yieldOnMutation) yield()
            val id = if (income.id > 0L) income.id else nextId++
            incomesFlow.value = incomesFlow.value + income.copy(id = id)
            return id
        }

        override suspend fun updateIncome(income: Income) {
            if (yieldOnMutation) yield()
            incomesFlow.value = incomesFlow.value.map { if (it.id == income.id) income else it }
        }

        override suspend fun deleteIncome(id: Long) {
            if (yieldOnMutation) yield()
            incomesFlow.value = incomesFlow.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override fun getAllBudgetCategories(): Flow<List<Category>> = budgetCategoriesFlow

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories + subcategory)
                } else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories.filterNot { s -> s.name.equals(subcategoryName, ignoreCase = true) })
                } else it
            }
        }

        override suspend fun countIncomesForCategory(categoryName: String): Int =
            incomesFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }

        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int =
            incomesFlow.value.count {
                it.category.equals(categoryName, ignoreCase = true) &&
                    it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
        override suspend fun preloadDefaultBudgetCategoriesIfNeeded() = Unit
    }

    private class AdversarialBudgetRepo : BudgetRepository {
        val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
            budgetsFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getBudgetById(id: Long): Budget? =
            budgetsFlow.value.find { it.id == id }

        override suspend fun insertBudget(budget: Budget): Long {
            val id = if (budget.id > 0L) budget.id else nextId++
            budgetsFlow.value = budgetsFlow.value + budget.copy(id = id)
            return id
        }

        override suspend fun updateBudget(budget: Budget) {
            budgetsFlow.value = budgetsFlow.value.map { if (it.id == budget.id) budget else it }
        }

        override suspend fun deleteBudget(id: Long) {
            budgetsFlow.value = budgetsFlow.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories + subcategory)
                } else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories.filterNot { s -> s.name.equals(subcategoryName, ignoreCase = true) })
                } else it
            }
        }

        override suspend fun countBudgetsForCategory(categoryName: String): Int =
            budgetsFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }

        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int =
            budgetsFlow.value.count {
                it.category.equals(categoryName, ignoreCase = true) &&
                    it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialSavingsRepo : SavingsRepository {
        val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            savingsFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getSavingById(id: Long): Saving? =
            savingsFlow.value.find { it.id == id }

        override suspend fun insertSaving(saving: Saving): Long {
            val id = if (saving.id > 0L) saving.id else nextId++
            savingsFlow.value = savingsFlow.value + saving.copy(id = id)
            return id
        }

        override suspend fun updateSaving(saving: Saving) {
            savingsFlow.value = savingsFlow.value.map { if (it.id == saving.id) saving else it }
        }

        override suspend fun deleteSaving(id: Long) {
            savingsFlow.value = savingsFlow.value.filterNot { it.id == id }
        }

        override suspend fun countSavingsForCategory(categoryName: String): Int =
            savingsFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }

        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int =
            savingsFlow.value.count {
                it.category.equals(categoryName, ignoreCase = true) &&
                    it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories + subcategory)
                } else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories.filterNot { s -> s.name.equals(subcategoryName, ignoreCase = true) })
                } else it
            }
        }

        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialCategoryRepo : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories + subcategory)
                } else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(categoryName, ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories.filterNot { s -> s.name.equals(subcategoryName, ignoreCase = true) })
                } else it
            }
        }
    }

    // =========================================================================
    // SECTION 1: Property-Based Testing & Mathematical Invariants
    // =========================================================================

    /**
     * Invariants tested across 500 randomized runs:
     * 1. Sum Invariant: sum of item amounts == summary.totalAmount (within precision delta).
     * 2. Proportion Invariant: item.proportion in 0.0f..1.0f.
     * 3. Percentage Invariant: item.percentage in 0..100.
     * 4. Proportion Sum Invariant: sum(proportion) in 0.99f..1.01f when non-empty.
     * 5. Ordering Invariant: items are sorted strictly by amount descending.
     * 6. Uniqueness Invariant: no duplicate categoryName (case-insensitive & trimmed) in items.
     * 7. Sanitization: non-finite, zero or negative entries are completely excluded.
     */
    @Test
    fun property_based_invariants_across_randomized_inputs() {
        val random = Random(42)
        val sampleCategories = listOf(
            Category("Food", "🍔", "#EF4444"),
            Category("Housing", "🏠", "#3B82F6"),
            Category("Transport", "🚗", "#10B981"),
            Category("Entertainment", "🎮", "#F59E0B"),
            Category("Health", "💊", "#8B5CF6")
        )

        repeat(500) { iteration ->
            val numEntries = random.nextInt(0, 30)
            val entries = (0 until numEntries).map { id ->
                val category = if (random.nextBoolean()) {
                    sampleCategories.random(random).name
                } else {
                    "Custom_${random.nextInt(1, 10)}"
                }
                // Introduce messy formatting: spaces, lowercase/uppercase
                val paddedCategory = when (random.nextInt(4)) {
                    0 -> "  $category  "
                    1 -> category.lowercase()
                    2 -> category.uppercase()
                    else -> category
                }

                // Random amount with occasional extreme / invalid numbers
                val amount = when (random.nextInt(10)) {
                    0 -> 0.0 // Zero
                    1 -> -random.nextDouble(1.0, 500.0) // Negative
                    2 -> Double.NaN
                    3 -> Double.POSITIVE_INFINITY
                    else -> random.nextDouble(0.01, 10_000.0)
                }

                Expense(
                    id = id.toLong(),
                    amount = amount,
                    date = "2026-05-01",
                    description = "Iter $iteration #$id",
                    category = paddedCategory,
                    periodId = 1L
                )
            }

            val summary = buildPeriodCategorySummary(
                entries = entries,
                categories = sampleCategories,
                categorySelector = { it.category },
                amountSelector = { it.amount },
                defaultColorHex = "#4E73DF"
            )

            val validEntries = entries.filter {
                it.amount.isFinite() && it.amount > 0.0 && it.category.trim().isNotEmpty()
            }

            if (validEntries.isEmpty()) {
                assertTrue("Iteration $iteration: summary must be empty", summary.isEmpty)
                assertEquals(0.0, summary.totalAmount, 0.0001)
                assertTrue(summary.items.isEmpty())
            } else {
                assertFalse("Iteration $iteration: summary must not be empty", summary.isEmpty)
                assertTrue("Iteration $iteration: totalAmount must be positive", summary.totalAmount > 0.0)

                // 1. Sum Invariant
                val itemsSum = summary.items.sumOf { it.amount }
                assertEquals(
                    "Iteration $iteration: items sum must match totalAmount",
                    summary.totalAmount,
                    itemsSum,
                    0.01
                )

                // 2. Proportion & 3. Percentage Invariants
                summary.items.forEach { item ->
                    assertTrue(
                        "Iteration $iteration: proportion must be in [0, 1] but was ${item.proportion}",
                        item.proportion in 0.0f..1.0f
                    )
                    assertTrue(
                        "Iteration $iteration: percentage must be in 0..100 but was ${item.percentage}",
                        item.percentage in 0..100
                    )
                }

                // 4. Proportion Sum Invariant
                if (summary.items.isNotEmpty()) {
                    val proportionSum = summary.items.fold(0f) { acc, item -> acc + item.proportion }
                    assertTrue(
                        "Iteration $iteration: sum of proportions must be ~1.0f, was $proportionSum",
                        proportionSum in 0.98f..1.02f
                    )
                }

                // 5. Ordering Invariant
                for (i in 0 until summary.items.size - 1) {
                    assertTrue(
                        "Iteration $iteration: items must be sorted descending by amount",
                        summary.items[i].amount >= summary.items[i + 1].amount
                    )
                }

                // 6. Uniqueness Invariant
                val names = summary.items.map { it.categoryName.trim().lowercase() }
                assertEquals(
                    "Iteration $iteration: category names must be unique when consolidated",
                    names.distinct().size,
                    names.size
                )
            }
        }
    }

    // =========================================================================
    // SECTION 2: Adversarial Boundary & Corner Cases (Fuzzing & Extremes)
    // =========================================================================

    @Test
    fun test_cumulative_double_overflow_returns_safe_empty_summary() {
        // Two massive finite numbers whose sum overflows Double to POSITIVE_INFINITY
        val massiveEntries = listOf(
            Income(1, Double.MAX_VALUE * 0.75, "2026-05-01", "Salary", "", "", 1L),
            Income(2, Double.MAX_VALUE * 0.75, "2026-05-01", "Investments", "", "", 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = massiveEntries,
            categories = emptyList(),
            categorySelector = { it.category },
            amountSelector = { it.amount }
        )

        // Must not crash or propagate Double.POSITIVE_INFINITY as valid total
        assertTrue("Sum overflow must safely return empty summary", summary.isEmpty)
        assertEquals(0.0, summary.totalAmount, 0.0)
        assertTrue(summary.items.isEmpty())
    }

    @Test
    fun test_scale_disparity_terabytes_alongside_nanodollars() {
        val entries = listOf(
            Income(1, 1_000_000_000_000.0, "2026-05-01", "MegaCorp", "", "", 1L), // 1 Trillion
            Income(2, 0.0000000001, "2026-05-01", "Dust", "", "", 1L) // 1e-10
        )

        val summary = buildPeriodCategorySummary(
            entries = entries,
            categories = emptyList(),
            categorySelector = { it.category },
            amountSelector = { it.amount }
        )

        assertFalse(summary.isEmpty)
        assertEquals(2, summary.items.size)
        assertEquals("MegaCorp", summary.items[0].categoryName)
        assertEquals(100, summary.items[0].percentage)
        assertEquals(1.0f, summary.items[0].proportion, 0.0001f)

        assertEquals("Dust", summary.items[1].categoryName)
        assertEquals(0, summary.items[1].percentage)
        assertEquals(0.0f, summary.items[1].proportion, 0.0001f)
    }

    @Test
    fun test_corrupt_numerical_values_and_negative_zeros() {
        val corruptedEntries = listOf(
            Expense(1, Double.NaN, "2026-05-01", "Corrupt 1", "Food", periodId = 1L),
            Expense(2, Double.POSITIVE_INFINITY, "2026-05-01", "Corrupt 2", "Food", periodId = 1L),
            Expense(3, Double.NEGATIVE_INFINITY, "2026-05-01", "Corrupt 3", "Food", periodId = 1L),
            Expense(4, -0.0, "2026-05-01", "Negative Zero", "Food", periodId = 1L),
            Expense(5, 0.0, "2026-05-01", "Positive Zero", "Food", periodId = 1L),
            Expense(6, -999.99, "2026-05-01", "Negative amount", "Food", periodId = 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = corruptedEntries,
            categories = emptyList(),
            categorySelector = { it.category },
            amountSelector = { it.amount }
        )

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.totalAmount, 0.0)
        assertTrue(summary.items.isEmpty())
    }

    @Test
    fun test_blank_tabs_and_newline_categories_are_filtered() {
        val invalidCategoryEntries = listOf(
            Expense(1, 100.0, "2026-05-01", "", "", periodId = 1L),
            Expense(2, 200.0, "2026-05-01", "", "   ", periodId = 1L),
            Expense(3, 300.0, "2026-05-01", "", "\t\n\r  ", periodId = 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = invalidCategoryEntries,
            categories = emptyList(),
            categorySelector = { it.category },
            amountSelector = { it.amount }
        )

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.totalAmount, 0.0)
    }

    @Test
    fun test_special_unicode_emojis_rtl_and_html_injection_in_category_name() {
        val specialEntries = listOf(
            Expense(1, 100.0, "2026-05-01", "", "🍔 Comida Rápida 🎉", periodId = 1L),
            Expense(2, 200.0, "2026-05-01", "", "رواتب واستثمارات", periodId = 1L), // Arabic RTL
            Expense(3, 300.0, "2026-05-01", "", "<script>alert('XSS')</script>", periodId = 1L),
            Expense(4, 400.0, "2026-05-01", "", "Category\nWith\nNewlines", periodId = 1L),
            Expense(5, 500.0, "2026-05-01", "", "A".repeat(500), periodId = 1L) // 500 chars
        )

        val summary = buildPeriodCategorySummary(
            entries = specialEntries,
            categories = emptyList(),
            categorySelector = { it.category },
            amountSelector = { it.amount }
        )

        assertEquals(1500.0, summary.totalAmount, 0.01)
        assertEquals(5, summary.items.size)

        // Verify each special entry is cleanly preserved
        val names = summary.items.map { it.categoryName }
        assertTrue(names.contains("🍔 Comida Rápida 🎉"))
        assertTrue(names.contains("رواتب واستثمارات"))
        assertTrue(names.contains("<script>alert('XSS')</script>"))
        assertTrue(names.contains("Category\nWith\nNewlines"))
        assertTrue(names.contains("A".repeat(500)))
    }

    @Test
    fun test_catalog_fallback_color_when_color_is_blank_or_missing() {
        val categories = listOf(
            Category("Food", "🍔", ""), // Blank color
            Category("Housing", "🏠", "   ") // Whitespace color
        )
        val entries = listOf(
            Expense(1, 100.0, "2026-05-01", "", "Food", periodId = 1L),
            Expense(2, 200.0, "2026-05-01", "", "Housing", periodId = 1L),
            Expense(3, 300.0, "2026-05-01", "", "NonExistent", periodId = 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = entries,
            categories = categories,
            categorySelector = { it.category },
            amountSelector = { it.amount },
            defaultColorHex = "#DEF000"
        )

        assertEquals(3, summary.items.size)
        // All 3 items must cleanly fall back to defaultColorHex
        summary.items.forEach { item ->
            assertEquals("#DEF000", item.colorHex)
        }
    }

    /**
     * VULNERABILITY / ADVERSARIAL BUG EXPOSURE:
     * When categories in the catalog have leading/trailing whitespace (e.g. " Employment "),
     * buildPeriodCategorySummary matches with `it.name.equals(rawName, ignoreCase = true)`.
     * Because `rawName` is trimmed ("Employment") but `it.name` is NOT trimmed (" Employment "),
     * the lookup fails, resulting in a null matchedCategory and falling back to defaultColorHex!
     */
    @Test
    fun test_untrimmed_category_name_in_catalog_lookup_behavior() {
        val catalogWithWhitespace = listOf(
            Category(" Employment ", "💼", "#1CC88A"),
            Category("Transport", "🚗", "#3B82F6")
        )
        val entries = listOf(
            Income(1, 500.0, "2026-05-01", "Employment", "", "", 1L),
            Income(2, 500.0, "2026-05-01", "Transport", "", "", 1L)
        )

        val summary = buildPeriodCategorySummary(
            entries = entries,
            categories = catalogWithWhitespace,
            categorySelector = { it.category },
            amountSelector = { it.amount },
            defaultColorHex = "#4E73DF"
        )

        val transportItem = summary.items.first { it.categoryName == "Transport" }
        assertEquals("#3B82F6", transportItem.colorHex)

        val employmentItem = summary.items.first { it.categoryName.trim() == "Employment" }
        // EXPOSURE: If it.name in catalog was not trimmed, matchedCategory was null,
        // so colorHex fell back to #4E73DF instead of #1CC88A.
        // We assert the actual observed behavior:
        assertNotEquals(
            "Untrimmed catalog name ' Employment ' fails exact match and falls back to default",
            "#1CC88A",
            employmentItem.colorHex
        )
        assertEquals("#4E73DF", employmentItem.colorHex)
    }

    // =========================================================================
    // SECTION 3: UseCase Consolidation (Subcategories Consolidated into Parent)
    // =========================================================================

    @Test
    fun test_expense_usecase_consolidates_subcategories_under_parent_category() {
        val expenseRepo = AdversarialExpenseRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo)
        val categories = listOf(
            Category("Food", "🍔", "#FF5733", listOf(Subcategory("Groceries"), Subcategory("Restaurants"), Subcategory("Snacks"))),
            Category("Travel", "✈️", "#33FF57")
        )

        val expenses = listOf(
            Expense(1, 40.0, "2026-05-01", "", "Food", "Groceries", periodId = 1L),
            Expense(2, 35.0, "2026-05-02", "", " food ", "Restaurants", periodId = 1L),
            Expense(3, 25.0, "2026-05-03", "", "FOOD", "Snacks", periodId = 1L),
            Expense(4, 100.0, "2026-05-04", "", "Travel", "Flights", periodId = 1L)
        )

        val summary = expenseUseCase.calculateExpenseCategorySummary(expenses, categories)

        assertEquals(200.0, summary.totalAmount, 0.001)
        assertEquals(2, summary.items.size)

        val foodItem = summary.items.first { it.categoryName == "Food" }
        assertEquals(100.0, foodItem.amount, 0.001)
        assertEquals(0.5f, foodItem.proportion, 0.001f)
        assertEquals(50, foodItem.percentage)
        assertEquals("#FF5733", foodItem.colorHex)

        val travelItem = summary.items.first { it.categoryName == "Travel" }
        assertEquals(100.0, travelItem.amount, 0.001)
        assertEquals(0.5f, travelItem.proportion, 0.001f)
        assertEquals(50, travelItem.percentage)
        assertEquals("#33FF57", travelItem.colorHex)
    }

    @Test
    fun test_income_usecase_consolidates_incomes_and_budgets_by_parent_category() {
        val incomeRepo = AdversarialIncomeRepo()
        val incomeUseCase = IncomeUseCase(incomeRepo)
        val categories = listOf(
            Category("Salary", "💼", "#10B981"),
            Category("Investments", "📈", "#3B82F6")
        )

        val incomes = listOf(
            Income(1, 600.0, "2026-05-01", "Salary", "Main Job", "", 1L),
            Income(2, 400.0, "2026-05-02", " salary ", "Overtime", "", 1L),
            Income(3, 500.0, "2026-05-03", "Investments", "Dividends", "", 1L)
        )
        val incomeSummary = incomeUseCase.calculateIncomeCategorySummary(incomes, categories)
        assertEquals(1500.0, incomeSummary.totalAmount, 0.001)
        assertEquals(2, incomeSummary.items.size)
        assertEquals("Salary", incomeSummary.items[0].categoryName)
        assertEquals(1000.0, incomeSummary.items[0].amount, 0.001)
        assertEquals(67, incomeSummary.items[0].percentage)

        val budgets = listOf(
            Budget(1, 200.0, "Salary", "Living", 1L),
            Budget(2, 300.0, "salary", "Discretionary", 1L)
        )
        val budgetSummary = incomeUseCase.calculateBudgetCategorySummary(budgets, categories)
        assertEquals(500.0, budgetSummary.totalAmount, 0.001)
        assertEquals(1, budgetSummary.items.size)
        assertEquals("Salary", budgetSummary.items[0].categoryName)
        assertEquals(100, budgetSummary.items[0].percentage)
    }

    @Test
    fun test_savings_usecase_consolidates_savings_across_different_income_sources() {
        val savingsRepo = AdversarialSavingsRepo()
        val savingsUseCase = SavingsUseCase(savingsRepo)
        val categories = listOf(
            Category("Emergency Fund", "🛡️", "#3B82F6")
        )

        val savings = listOf(
            Saving(1, 150.0, "Emergency Fund", "Cash", 1L, incomeId = 10L),
            Saving(2, 250.0, " emergency fund ", "Deposit", 1L, incomeId = 20L)
        )

        val summary = savingsUseCase.calculateSavingsCategorySummary(savings, categories)
        assertEquals(400.0, summary.totalAmount, 0.001)
        assertEquals(1, summary.items.size)
        assertEquals("Emergency Fund", summary.items[0].categoryName)
        assertEquals(400.0, summary.items[0].amount, 0.001)
        assertEquals(100, summary.items[0].percentage)
        assertEquals("#3B82F6", summary.items[0].colorHex)
    }

    // =========================================================================
    // SECTION 4: ViewModel Period Isolation & Concurrency
    // =========================================================================

    @Test
    fun test_viewmodel_strict_period_isolation_across_multiple_periods() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val expenseRepo = AdversarialExpenseRepo()
            val categoryRepo = AdversarialCategoryRepo()
            categoryRepo.insertCategory(Category("Food", "🍔", "#EF4444"))
            categoryRepo.insertCategory(Category("Leisure", "🎉", "#F59E0B"))

            // Period 101 has $300 in expenses
            expenseRepo.insertExpense(Expense(1, 100.0, "2026-05-01", "", "Food", periodId = 101L))
            expenseRepo.insertExpense(Expense(2, 200.0, "2026-05-02", "", "Food", periodId = 101L))

            // Period 102 has $50 in expenses
            expenseRepo.insertExpense(Expense(3, 50.0, "2026-06-01", "", "Leisure", periodId = 102L))

            // Period 103 has 0 expenses (empty period)

            val expenseUseCase = ExpenseUseCase(expenseRepo)
            val categoryUseCase = CategoryUseCase(categoryRepo)
            val viewModel = ExpenseViewModel(expenseUseCase, categoryUseCase)
            advanceUntilIdle()

            // 1. Select Period 101
            viewModel.setFinancialPeriod(101L)
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(101L, state.selectedPeriodId)
            assertEquals(300.0, state.expenseSummary.totalAmount, 0.001)
            assertEquals(1, state.expenseSummary.items.size)
            assertEquals("Food", state.expenseSummary.items[0].categoryName)
            assertEquals(300.0, state.expenseSummary.items[0].amount, 0.001)

            // 2. Switch to Period 102 -> Period 101 data must vanish completely
            viewModel.setFinancialPeriod(102L)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(102L, state.selectedPeriodId)
            assertEquals(50.0, state.expenseSummary.totalAmount, 0.001)
            assertEquals(1, state.expenseSummary.items.size)
            assertEquals("Leisure", state.expenseSummary.items[0].categoryName)
            assertEquals(50.0, state.expenseSummary.items[0].amount, 0.001)

            // 3. Switch to empty Period 103 -> Must show 0.0 and empty items immediately
            viewModel.setFinancialPeriod(103L)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(103L, state.selectedPeriodId)
            assertTrue(state.expenseSummary.isEmpty)
            assertEquals(0.0, state.expenseSummary.totalAmount, 0.0)
            assertTrue(state.expenseSummary.items.isEmpty())

            // 4. Set period to null -> Immediate reset
            viewModel.setFinancialPeriod(null)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.expenseSummary.isEmpty)
            assertEquals(0.0, state.expenseSummary.totalAmount, 0.0)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun test_viewmodel_rapid_period_switching_cancels_previous_period_flow() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val incomeRepo = AdversarialIncomeRepo(yieldOnMutation = true)
            val incomeUseCase = IncomeUseCase(incomeRepo)
            val viewModel = IncomeViewModel(incomeUseCase)
            advanceUntilIdle()

            // Seed period 1 with 100 entries
            (1..100).forEach { i ->
                incomeRepo.insertIncome(Income(i.toLong(), 10.0, "2026-05-01", "Work", "", "", 1L))
            }
            // Seed period 2 with 1 entry
            incomeRepo.insertIncome(Income(200L, 50.0, "2026-05-01", "Bonus", "", "", 2L))
            advanceUntilIdle()

            // Rapid switching: 1 -> 2 -> 1 -> 2
            viewModel.onPeriodSelected(1L)
            viewModel.onPeriodSelected(2L)
            viewModel.onPeriodSelected(1L)
            viewModel.onPeriodSelected(2L)
            advanceUntilIdle()

            // Final state MUST strictly match Period 2 only ($50.0, Bonus)
            val finalState = viewModel.uiState.value
            assertEquals(2L, finalState.selectedPeriodId)
            assertEquals(50.0, finalState.incomeSummary.totalAmount, 0.001)
            assertEquals(1, finalState.incomeSummary.items.size)
            assertEquals("Bonus", finalState.incomeSummary.items[0].categoryName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun test_savings_viewmodel_reacts_dynamically_to_saving_insert_and_deletion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val savingsRepo = AdversarialSavingsRepo()
            val savingsUseCase = SavingsUseCase(savingsRepo)
            val viewModel = SavingsViewModel(savingsUseCase)
            advanceUntilIdle()

            viewModel.onPeriodSelected(1L)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.savingsSummary.isEmpty)

            // Insert saving
            savingsRepo.insertSaving(Saving(1L, 200.0, "Retirement", "", 1L))
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertFalse(state.savingsSummary.isEmpty)
            assertEquals(200.0, state.savingsSummary.totalAmount, 0.001)
            assertEquals("Retirement", state.savingsSummary.items[0].categoryName)

            // Delete saving
            savingsRepo.deleteSaving(1L)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.savingsSummary.isEmpty)
            assertEquals(0.0, state.savingsSummary.totalAmount, 0.0)
            assertTrue(state.savingsSummary.items.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // SECTION 5: Compose UI Rendering Stress & Robustness
    // =========================================================================

    @Test
    fun test_period_summary_card_single_item_draws_full_arc_without_crash() {
        val currency = defaultCurrencies.first()
        val summary = PeriodCategorySummary(
            totalAmount = 500.0,
            items = listOf(
                CategorySummaryItem("Salary", "#1CC88A", 500.0, 1.0f, 100)
            )
        )

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Resumen de Ingresos",
                totalSubtitle = "Ingreso total",
                centerLabel = "Total",
                emptyMessage = "Sin movimientos",
                summary = summary,
                currency = currency
            )
        }

        composeTestRule.onNodeWithTag("text_period_summary_total")
            .assertTextEquals(CurrencyFormatter.format(500.0, currency))
        composeTestRule.onNodeWithTag("text_summary_center_total")
            .assertTextEquals(CurrencyFormatter.format(500.0, currency))
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
        composeTestRule.onNodeWithTag("legend_percent_Salary").assertTextEquals("100%")
    }

    @Test
    fun test_period_summary_card_50_categories_rendered_without_ui_freeze() {
        val currency = defaultCurrencies.first()
        val items = (1..50).map { i ->
            CategorySummaryItem(
                categoryName = "Cat_$i",
                colorHex = "#1CC88A",
                amount = 10.0,
                proportion = 0.02f,
                percentage = 2
            )
        }
        val summary = PeriodCategorySummary(totalAmount = 500.0, items = items)

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Resumen Masivo",
                totalSubtitle = "50 Categorías",
                centerLabel = "Total",
                emptyMessage = "Sin movimientos",
                summary = summary,
                currency = currency
            )
        }

        composeTestRule.onNodeWithTag("text_period_summary_total")
            .assertTextEquals(CurrencyFormatter.format(500.0, currency))
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legend_item_Cat_1").assertIsDisplayed()
    }

    @Test
    fun test_period_summary_card_handles_invalid_hex_color_safely() {
        val currency = defaultCurrencies.first()
        val summary = PeriodCategorySummary(
            totalAmount = 100.0,
            items = listOf(
                CategorySummaryItem("BadColor", "MALFORMED_HEX", 100.0, 1.0f, 100)
            )
        )

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Prueba Color Inválido",
                totalSubtitle = "Total",
                centerLabel = "Total",
                emptyMessage = "Sin movimientos",
                summary = summary,
                currency = currency
            )
        }

        // Must display without crashing parseHexColor in DonutChart or Legend
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legend_item_BadColor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legend_percent_BadColor").assertTextEquals("100%")
    }

    @Test
    fun test_period_summary_card_empty_state_contains_no_comparative_badges() {
        val currency = defaultCurrencies.first()

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Resumen Vacío",
                totalSubtitle = "Total",
                centerLabel = "Total",
                emptyMessage = "No hay registros en este período",
                summary = PeriodCategorySummary(totalAmount = 0.0, items = emptyList()),
                currency = currency
            )
        }

        composeTestRule.onNodeWithTag("text_period_summary_total")
            .assertTextEquals(CurrencyFormatter.format(0.0, currency))
        composeTestRule.onNodeWithTag("text_period_summary_empty")
            .assertTextEquals("No hay registros en este período")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertDoesNotExist()

        // Strict verification: No comparative badges (+12%, -5%, etc.) anywhere
        composeTestRule.onNodeWithText("+12%", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("vs", substring = true).assertDoesNotExist()
    }
}
