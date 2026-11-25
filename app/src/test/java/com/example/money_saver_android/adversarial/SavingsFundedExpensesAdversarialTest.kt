package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.ExpenseEntity
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.buildSavingsNetSummary
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.ExpensesTabContent
import com.example.money_saver_android.ui.SavingsTabContent
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Savings-Funded Expenses & Cumulative Net Savings:
 * - Domain & Mathematical Invariants (`calculateSavingsNetBalances`, `buildSavingsNetSummary`)
 * - Cross-UseCase Conservation & Referential Integrity (`ExpenseUseCase`, `SavingsUseCase`, `IncomeUseCase`)
 * - Coroutine Concurrency, Mutex Contention & ViewModel Idempotency (`ExpenseViewModel`, `SavingsViewModel`)
 * - Room SQLite Storage Roundtrips, Corrupt Columns & Extreme Unicode (`AppDatabase` v6, `SavingsDatabase` v2)
 * - Compose UI Edge Cases & Lifecycle Synchronization (`ExpensesTabContent`, `AddEditExpenseDialog`, `SavingsTabContent`, `ExpenseCardItem`)
 *
 * Rules:
 * 1. Strictly test-only file; production source code is untouched.
 * 2. Bugs discovered in production code are exposed via assertions expecting the valid domain/UI contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsFundedExpensesAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var i18nManager: I18nManager
    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$", formatPattern = "#,##0.00")

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        i18nManager = I18nManager().apply {
            initFromContext(context)
            setLanguage("es")
        }
    }

    // =========================================================================
    // Adversarial In-Memory Repositories
    // =========================================================================

    private class AdversarialExpenseRepo(
        private val yieldOnMutation: Boolean = false
    ) : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private var nextId = 1L
        var insertCallCount = 0
            private set
        var updateCallCount = 0
            private set

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow

        override fun getExpensesByPeriod(periodId: Long): Flow<List<Expense>> =
            expensesFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun insertExpense(expense: Expense) {
            insertCallCount++
            if (yieldOnMutation) yield()
            val id = if (expense.id > 0L) expense.id else nextId++
            expensesFlow.value = expensesFlow.value + expense.copy(id = id)
        }

        override suspend fun updateExpense(expense: Expense) {
            updateCallCount++
            if (yieldOnMutation) yield()
            expensesFlow.value = expensesFlow.value.map { if (it.id == expense.id) expense else it }
        }

        override suspend fun deleteExpense(id: Long) {
            if (yieldOnMutation) yield()
            expensesFlow.value = expensesFlow.value.filterNot { it.id == id }
        }
    }

    private class AdversarialSavingsRepo(
        private val yieldOnMutation: Boolean = false
    ) : SavingsRepository {
        val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow

        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            savingsFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getSavingById(id: Long): Saving? =
            savingsFlow.value.find { it.id == id }

        override suspend fun insertSaving(saving: Saving): Long {
            if (yieldOnMutation) yield()
            val id = if (saving.id > 0L) saving.id else nextId++
            savingsFlow.value = savingsFlow.value + saving.copy(id = id)
            return id
        }

        override suspend fun updateSaving(saving: Saving) {
            if (yieldOnMutation) yield()
            savingsFlow.value = savingsFlow.value.map { if (it.id == saving.id) saving else it }
        }

        override suspend fun deleteSaving(id: Long) {
            if (yieldOnMutation) yield()
            savingsFlow.value = savingsFlow.value.filterNot { it.id == id }
        }

        override suspend fun countSavingsForCategory(categoryName: String): Int =
            savingsFlow.value.count { it.category.trim().equals(categoryName.trim(), ignoreCase = true) }

        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int =
            savingsFlow.value.count {
                it.category.trim().equals(categoryName.trim(), ignoreCase = true) &&
                    it.subcategory.trim().equals(subcategoryName.trim(), ignoreCase = true)
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
            categoriesFlow.value = categoriesFlow.value.filterNot {
                it.name.trim().equals(categoryName.trim(), ignoreCase = true)
            }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.trim().equals(categoryName.trim(), ignoreCase = true)) {
                    it.copy(subcategories = it.subcategories.filterNot { s ->
                        s.name.trim().equals(subcategoryName.trim(), ignoreCase = true)
                    })
                } else it
            }
        }

        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialBudgetRepo(
        initial: List<Budget> = emptyList()
    ) : BudgetRepository {
        val budgetsFlow = MutableStateFlow(initial)
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

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
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) = Unit
        override suspend fun deleteCategory(categoryName: String) = Unit
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) = Unit
        override suspend fun countBudgetsForCategory(categoryName: String): Int = 0
        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialIncomeRepo(
        initial: List<Income> = emptyList()
    ) : IncomeRepository {
        val incomesFlow = MutableStateFlow(initial)
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            incomesFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getIncomeById(id: Long): Income? =
            incomesFlow.value.find { it.id == id }

        override suspend fun insertIncome(income: Income): Long {
            val id = if (income.id > 0L) income.id else nextId++
            incomesFlow.value = incomesFlow.value + income.copy(id = id)
            return id
        }

        override suspend fun updateIncome(income: Income) {
            incomesFlow.value = incomesFlow.value.map { if (it.id == income.id) income else it }
        }

        override suspend fun deleteIncome(id: Long) {
            incomesFlow.value = incomesFlow.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) = Unit
        override suspend fun deleteCategory(categoryName: String) = Unit
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) = Unit
        override suspend fun countIncomesForCategory(categoryName: String): Int = 0
        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialCategoryRepo(
        initial: List<Category> = emptyList()
    ) : CategoryRepository {
        val categoriesFlow = MutableStateFlow(initial)
        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value + category
        }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) = Unit
        override suspend fun deleteCategory(categoryName: String) = Unit
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) = Unit
    }

    // =========================================================================
    // Custom Property-Based Generators (Arb-style deterministic shrinkable pool)
    // =========================================================================

    private object DomainArb {
        private val categoryPool = listOf(
            "Emergencias",
            "Viajes",
            "Educación",
            "Inversión",
            "🛡️ Fondo Árabe طوارئ",
            "Hogar & Auto"
        )

        fun noisyName(base: String, random: Random): String = when (random.nextInt(5)) {
            0 -> "  $base  "
            1 -> base.lowercase()
            2 -> base.uppercase()
            3 -> "\t$base\n"
            else -> base
        }

        fun adversarialAmount(random: Random): Double = when (random.nextInt(12)) {
            0 -> 0.0
            1 -> -0.0
            2 -> -random.nextDouble(0.01, 10_000.0)
            3 -> Double.NaN
            4 -> Double.POSITIVE_INFINITY
            5 -> Double.NEGATIVE_INFINITY
            6 -> 0.01
            7 -> 0.10 + 0.20 // Classic IEEE-754 drift value
            else -> (random.nextInt(1, 500_000) / 100.0)
        }

        fun randomCatalog(random: Random): List<Category> =
            categoryPool.mapIndexed { idx, name ->
                val color = when (random.nextInt(5)) {
                    0 -> ""
                    1 -> "   "
                    else -> listOf("#3B82F6", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899")[idx % 5]
                }
                Category(
                    name = if (random.nextBoolean()) " $name " else name,
                    icon = "💰",
                    color = color,
                    subcategories = emptyList()
                )
            }

        fun randomSaving(id: Long, random: Random): Saving {
            val cat = if (random.nextInt(10) == 0) "   " else noisyName(categoryPool.random(random), random)
            return Saving(
                id = id,
                amount = adversarialAmount(random),
                category = cat,
                subcategory = if (random.nextBoolean()) "Sub_${random.nextInt(3)}" else "",
                periodId = random.nextLong(1L, 8L),
                incomeId = random.nextLong(1L, 4L),
                dateTime = "2026-11-24 10:00"
            )
        }

        fun randomExpense(id: Long, random: Random): Expense {
            val source = if (random.nextBoolean()) ExpenseFundingSource.SAVINGS else ExpenseFundingSource.BUDGET
            val expCat = if (random.nextInt(12) == 0) "  " else noisyName(categoryPool.random(random), random)
            val savCat = if (source == ExpenseFundingSource.SAVINGS) {
                if (random.nextInt(10) == 0) "   " else noisyName(categoryPool.random(random), random)
            } else {
                // Deliberately pollute savingCategory on some BUDGET expenses to ensure it is ignored
                if (random.nextInt(4) == 0) categoryPool.random(random) else ""
            }
            return Expense(
                id = id,
                amount = adversarialAmount(random),
                date = "2026-11-24 12:00",
                description = "Prop #$id",
                category = expCat,
                subcategory = if (random.nextBoolean()) "General" else "",
                periodId = random.nextLong(1L, 8L),
                budgetId = if (source == ExpenseFundingSource.BUDGET) random.nextLong(1L, 4L) else 0L,
                budgetCategory = if (source == ExpenseFundingSource.BUDGET) expCat.trim() else "",
                fundingSource = source,
                savingCategory = savCat
            )
        }
    }

    // =========================================================================
    // SECTION 1: Property-Based Testing (Strict Mathematical & Domain Invariants)
    // =========================================================================

    /**
     * Property-Based Test (500 randomized multi-period histories):
     * Verifies 7 invariants on [calculateSavingsNetBalances] and [buildSavingsNetSummary]:
     * 1. Non-negativity & Finiteness: totalSaved >= 0, totalSpent >= 0, netBalance >= 0, never NaN/Infinity.
     * 2. Exact Net Formula: netBalance == max(0, totalSaved - totalSpent) (within 1e-6).
     * 3. Depleted Filter: categories with netBalance == 0.0 never appear in netSummary.items.
     * 4. Total Conservation: sum(netSummary.items.amount) == netSummary.totalAmount.
     * 5. Proportion & Percentage Bounds: proportion in [0, 1], percentage in [0, 100], sum(proportion) ~ 1.0f.
     * 6. Deterministic Ordering: balances sorted by netBalance DESC, then categoryName.lowercase() ASC.
     * 7. Case/Whitespace Consolidation & Default Color Fallback: unique normalized keys, non-blank colors.
     */
    @Test
    fun property_based_invariants_savings_net_balances_and_net_summary_500_iterations() {
        val random = Random(20261125L)
        val defaultHex = "#3B82F6"

        repeat(500) { iter ->
            val catalog = DomainArb.randomCatalog(random)
            val savings = (1..random.nextInt(0, 25)).map { id -> DomainArb.randomSaving(id.toLong(), random) }
            val expenses = (1..random.nextInt(0, 30)).map { id -> DomainArb.randomExpense(id.toLong(), random) }

            val balances = calculateSavingsNetBalances(
                savings = savings,
                expenses = expenses,
                categories = catalog,
                defaultColorHex = defaultHex
            )
            val netSummary = buildSavingsNetSummary(
                savings = savings,
                expenses = expenses,
                categories = catalog,
                defaultColorHex = defaultHex
            )

            // Invariant 1 & 2: Non-negativity, finiteness, and exact BigDecimal clamping
            balances.forEach { bal ->
                assertTrue("Iter $iter: totalSaved must be finite >= 0", bal.totalSaved.isFinite() && bal.totalSaved >= 0.0)
                assertTrue("Iter $iter: totalSpent must be finite >= 0", bal.totalSpent.isFinite() && bal.totalSpent >= 0.0)
                assertTrue("Iter $iter: netBalance must be finite >= 0", bal.netBalance.isFinite() && bal.netBalance >= 0.0)
                assertTrue("Iter $iter: colorHex must never be blank", bal.colorHex.isNotBlank())

                val expectedNet = BigDecimal.valueOf(bal.totalSaved)
                    .subtract(BigDecimal.valueOf(bal.totalSpent))
                    .max(BigDecimal.ZERO)
                    .toDouble()
                assertEquals("Iter $iter: netBalance exact conservation", expectedNet, bal.netBalance, 1e-6)
            }

            // Invariant 6: Deterministic ordering (netBalance DESC, categoryName.lowercase() ASC)
            for (i in 0 until balances.size - 1) {
                val curr = balances[i]
                val next = balances[i + 1]
                assertTrue(
                    "Iter $iter: balances must be sorted by netBalance DESC then name ASC",
                    curr.netBalance > next.netBalance ||
                        (curr.netBalance == next.netBalance && curr.categoryName.lowercase() <= next.categoryName.lowercase())
                )
            }

            // Invariant 7: Unique consolidated category keys
            val normalizedNames = balances.map { it.categoryName.trim().lowercase() }
            assertEquals("Iter $iter: duplicate category keys in balances", normalizedNames.distinct().size, normalizedNames.size)

            // Invariants 3, 4, 5 on netSummary
            val positiveBalances = balances.filter { it.netBalance > 0.0 }
            if (positiveBalances.isEmpty()) {
                assertTrue("Iter $iter: netSummary must be empty when all netBalances == 0", netSummary.isEmpty)
                assertEquals(0.0, netSummary.totalAmount, 0.0)
                assertTrue(netSummary.items.isEmpty())
            } else {
                assertFalse("Iter $iter: netSummary must not be empty", netSummary.isEmpty)
                assertEquals(positiveBalances.size, netSummary.items.size)
                assertEquals(
                    "Iter $iter: netSummary total must match sum of positive netBalances",
                    positiveBalances.sumOf { it.netBalance },
                    netSummary.totalAmount,
                    0.01
                )
                assertEquals(
                    "Iter $iter: netSummary items sum must match totalAmount",
                    netSummary.totalAmount,
                    netSummary.items.sumOf { it.amount },
                    0.01
                )
                netSummary.items.forEach { item ->
                    assertTrue("Iter $iter: item amount must be > 0", item.amount > 0.0)
                    assertTrue("Iter $iter: proportion out of bounds", item.proportion in 0.0f..1.0f)
                    assertTrue("Iter $iter: percentage out of bounds", item.percentage in 0..100)
                }
                val propSum = netSummary.items.fold(0f) { acc, item -> acc + item.proportion }
                assertTrue("Iter $iter: proportions must sum near 1.0f, got $propSum", propSum in 0.98f..1.02f)
            }
        }
    }

    /**
     * Property-Based Invariant (300 randomized multi-period scenarios):
     * Injecting arbitrary SAVINGS-funded expenses—even when their expense category matches active
     * current-period budgets—MUST NEVER alter budget spent totals, budget remaining percentages,
     * period expense donut summaries, or category expense breakdowns.
     */
    @Test
    fun property_based_invariant_budget_and_period_expense_isolation_from_savings_expenses() {
        val random = Random(998877L)
        val expenseUseCase = ExpenseUseCase(AdversarialExpenseRepo())
        val incomeUseCase = IncomeUseCase(AdversarialIncomeRepo())

        val categories = listOf(
            Category("Salud", "💊", "#EF4444", emptyList()),
            Category("Viajes", "✈️", "#10B981", emptyList()),
            Category("Hogar", "🏠", "#3B82F6", emptyList())
        )
        val budgets = listOf(
            Budget(id = 1L, amount = 500.0, category = "Salud", subcategory = "", periodId = 2L),
            Budget(id = 2L, amount = 800.0, category = "Viajes", subcategory = "", periodId = 2L),
            Budget(id = 3L, amount = 400.0, category = "Hogar", subcategory = "", periodId = 2L)
        )

        repeat(300) { iter ->
            val budgetExpenses = (1..random.nextInt(0, 12)).map { idx ->
                val cat = categories.random(random).name
                val matchedBudget = budgets.first { it.category == cat }
                Expense(
                    id = idx.toLong(),
                    amount = (random.nextInt(1, 30_000) / 100.0),
                    date = "2026-11-15 10:00",
                    description = "Budget exp $idx",
                    category = cat,
                    periodId = 2L,
                    budgetId = matchedBudget.id,
                    budgetCategory = matchedBudget.category,
                    fundingSource = ExpenseFundingSource.BUDGET,
                    savingCategory = ""
                )
            }
            val savingsExpenses = (100 until 100 + random.nextInt(1, 15)).map { idx ->
                val cat = categories.random(random).name // Intentionally collides with budget categories!
                Expense(
                    id = idx.toLong(),
                    amount = (random.nextInt(1, 90_000) / 100.0),
                    date = "2026-11-16 14:00",
                    description = "Savings exp $idx",
                    category = cat,
                    periodId = 2L,
                    budgetId = 0L,
                    budgetCategory = "",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            }
            val combined = (budgetExpenses + savingsExpenses).shuffled(random)

            assertEquals(
                "Iter $iter: expenseSummary must ignore SAVINGS expenses",
                expenseUseCase.calculateExpenseCategorySummary(budgetExpenses, categories),
                expenseUseCase.calculateExpenseCategorySummary(combined, categories)
            )
            assertEquals(
                "Iter $iter: calculateCategoryExpenses must ignore SAVINGS expenses",
                expenseUseCase.calculateCategoryExpenses(budgetExpenses, categories),
                expenseUseCase.calculateCategoryExpenses(combined, categories)
            )
            assertEquals(
                "Iter $iter: calculateTotalBudgetSpent must ignore SAVINGS expenses",
                incomeUseCase.calculateTotalBudgetSpent(budgets, budgetExpenses, "2026-11-01", "2026-11-30", 2L),
                incomeUseCase.calculateTotalBudgetSpent(budgets, combined, "2026-11-01", "2026-11-30", 2L),
                1e-6
            )
            assertEquals(
                "Iter $iter: calculateCategoryBudgets must ignore SAVINGS expenses",
                incomeUseCase.calculateCategoryBudgets(budgets, categories, budgetExpenses, "2026-11-01", "2026-11-30", 2L),
                incomeUseCase.calculateCategoryBudgets(budgets, categories, combined, "2026-11-01", "2026-11-30", 2L)
            )
        }
    }

    /**
     * Stateful Property-Based Test (200 command steps across multiple periods):
     * Executes randomized sequences of deposits, savings-funded expense creations, updates (including
     * category switches and amount increases), and deletions via [ExpenseUseCase].
     * Invariant: At EVERY step, cumulative spent in every savings category never exceeds cumulative saved.
     */
    @Test
    fun property_based_stateful_ledger_invariant_random_save_and_update_operations_never_overdraw() = runTest {
        val random = Random(424242L)
        val expenseRepo = AdversarialExpenseRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)
        val savingCats = listOf("Emergencias", "Viajes", "Educación")

        // Initial seed deposits across periods 1..3
        savingCats.forEachIndexed { idx, cat ->
            savingsRepo.insertSaving(Saving(amount = 250.0 * (idx + 1), category = cat, periodId = (idx + 1).toLong()))
        }

        repeat(200) { step ->
            val periodId = random.nextLong(1L, 5L)
            when (random.nextInt(4)) {
                0 -> {
                    // Deposit new saving
                    val deposit = (random.nextInt(100, 20_000) / 100.0)
                    savingsRepo.insertSaving(
                        Saving(amount = deposit, category = savingCats.random(random), periodId = periodId)
                    )
                }
                1 -> {
                    // Attempt savings-funded expense (often exceeding balance to test rejection)
                    val amount = (random.nextInt(1, 80_000) / 100.0)
                    val savCat = DomainArb.noisyName(savingCats.random(random), random)
                    expenseUseCase.saveExpense(
                        amountInput = amount.toString(),
                        dateInput = "2026-11-24 10:00",
                        descriptionInput = "Step $step",
                        categoryInput = "Gasto General",
                        periodId = periodId,
                        fundingSource = ExpenseFundingSource.SAVINGS,
                        savingCategoryInput = savCat
                    )
                }
                2 -> {
                    // Attempt updating a random existing savings expense
                    val existing = expenseRepo.expensesFlow.value.randomOrNull(random)
                    if (existing != null) {
                        val newAmount = (random.nextInt(1, 100_000) / 100.0)
                        val newSavCat = DomainArb.noisyName(savingCats.random(random), random)
                        expenseUseCase.updateExpense(
                            id = existing.id,
                            amountInput = newAmount.toString(),
                            dateInput = "2026-11-24 11:00",
                            descriptionInput = "Updated $step",
                            categoryInput = "Gasto General",
                            periodId = existing.periodId,
                            fundingSource = ExpenseFundingSource.SAVINGS,
                            savingCategoryInput = newSavCat
                        )
                    }
                }
                3 -> {
                    // Delete an existing expense (frees balance)
                    val existing = expenseRepo.expensesFlow.value.randomOrNull(random)
                    if (existing != null) {
                        expenseUseCase.deleteExpense(existing.id)
                    }
                }
            }

            // Ledger Invariant Check after every operation
            val allSavings = savingsRepo.savingsFlow.value
            val allExpenses = expenseRepo.expensesFlow.value
            savingCats.forEach { cat ->
                val saved = allSavings
                    .filter { it.category.trim().equals(cat, ignoreCase = true) }
                    .fold(BigDecimal.ZERO) { acc, s -> acc.add(BigDecimal.valueOf(s.amount)) }
                val spent = allExpenses
                    .filter {
                        it.fundingSource == ExpenseFundingSource.SAVINGS &&
                            it.savingCategory.trim().equals(cat, ignoreCase = true)
                    }
                    .fold(BigDecimal.ZERO) { acc, e -> acc.add(BigDecimal.valueOf(e.amount)) }
                assertTrue(
                    "Step $step [$cat]: overdraft detected! spent=$spent > saved=$saved",
                    spent <= saved
                )
            }
        }
    }

    // =========================================================================
    // SECTION 2: Domain Integrity & Cross-UseCase Adversarial Attacks
    // =========================================================================

    /**
     * EXPOSES BUG #1A (Cross-UseCase Referential & Non-Overdraft Invariant):
     * Deleting a historical Saving record when cumulative SAVINGS-funded expenses depend on its funds
     * MUST be blocked by [SavingsUseCase.deleteSaving]. Otherwise, totalSpent > totalSaved (negative real balance).
     */
    @Test
    fun test_adversarial_delete_saving_backed_by_savings_expense_must_be_blocked() = runTest {
        val incomeRepo = AdversarialIncomeRepo(
            listOf(Income(id = 1L, amount = 1000.0, dateTime = "2026-10-01 09:00", category = "Salario", periodId = 1L))
        )
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()

        val savingsUseCase = SavingsUseCase(
            savingsRepository = savingsRepo,
            incomeRepository = incomeRepo,
            budgetRepository = budgetRepo,
            expenseRepository = expenseRepo
        )
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)

        assertTrue(
            savingsUseCase.saveSaving(
                amountInput = "500",
                categoryInput = "Emergencias",
                periodId = 1L,
                incomeId = 1L,
                dateTimeInput = "2026-10-05 10:00"
            ) is SavingsResult.Success
        )
        val savingId = savingsRepo.savingsFlow.value.single().id

        // Spend $400 from "Emergencias" in Period 2
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "400",
                dateInput = "2026-11-10 14:00",
                descriptionInput = "Cirugía dental",
                categoryInput = "Salud",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            ) is ExpenseResult.Success
        )

        // Adversarial action: attempt to delete the $500 saving backing the $400 expense
        val deleteResult = savingsUseCase.deleteSaving(savingId)
        assertTrue(
            "Deleting a Saving whose balance backs existing savings-funded expenses must return SavingsResult.Error to prevent negative net balance",
            deleteResult is SavingsResult.Error
        )
    }

    /**
     * EXPOSES BUG #1B & #1C (Cross-UseCase Non-Overdraft Invariant on Saving Edit):
     * Updating a Saving record to either:
     * (a) reduce its amount below cumulative savings-funded expenses in that category ($500 -> $200 when $400 is spent), or
     * (b) reassign its category ("Emergencias" -> "Viajes") leaving $400 spent in "Emergencias" with $0 saved
     * MUST be rejected by [SavingsUseCase.updateSaving].
     */
    @Test
    fun test_adversarial_update_saving_reducing_below_spent_or_changing_category_must_be_blocked() = runTest {
        val incomeRepo = AdversarialIncomeRepo(
            listOf(Income(id = 1L, amount = 1000.0, dateTime = "2026-10-01 09:00", category = "Salario", periodId = 1L))
        )
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()

        val savingsUseCase = SavingsUseCase(
            savingsRepository = savingsRepo,
            incomeRepository = incomeRepo,
            budgetRepository = budgetRepo,
            expenseRepository = expenseRepo
        )
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)

        savingsUseCase.saveSaving(
            amountInput = "500",
            categoryInput = "Emergencias",
            periodId = 1L,
            incomeId = 1L,
            dateTimeInput = "2026-10-05 10:00"
        )
        val savingId = savingsRepo.savingsFlow.value.single().id

        expenseUseCase.saveExpense(
            amountInput = "400",
            dateInput = "2026-11-10 14:00",
            descriptionInput = "Urgencia",
            categoryInput = "Salud",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Emergencias"
        )

        // Attack A: Reduce saving from $500 to $200 while $400 is already spent
        val reduceResult = savingsUseCase.updateSaving(
            id = savingId,
            amountInput = "200",
            categoryInput = "Emergencias",
            periodId = 1L,
            incomeId = 1L,
            dateTimeInput = "2026-10-05 10:00"
        )
        assertTrue(
            "Reducing Saving amount ($200) below cumulative spent ($400) must return SavingsResult.Error",
            reduceResult is SavingsResult.Error
        )

        // Attack B: Reassign saving category from "Emergencias" to "Viajes" while $400 is spent in "Emergencias"
        val reassignResult = savingsUseCase.updateSaving(
            id = savingId,
            amountInput = "500",
            categoryInput = "Viajes",
            periodId = 1L,
            incomeId = 1L,
            dateTimeInput = "2026-10-05 10:00"
        )
        assertTrue(
            "Changing Saving category out of 'Emergencias' when $400 is already spent against it must return SavingsResult.Error",
            reassignResult is SavingsResult.Error
        )
    }

    /**
     * IEEE-754 Floating-Point Drift & Localized Decimal Formats:
     * In binary64, 0.10 + 0.20 == 0.30000000000000004 and 0.30 - 0.10 - 0.10 == 0.09999999999999998.
     * Verifies BigDecimal exact arithmetic allows spending the exact final cent ("0,10") and blocks 1 extra cent,
     * and handles European ("1.234,56") and US ("1,234.56") thousands/decimal separators accurately.
     */
    @Test
    fun test_adversarial_ieee754_cent_drift_and_localized_decimal_formats_on_savings_expenses() = runTest {
        val expenseRepo = AdversarialExpenseRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo, AdversarialBudgetRepo(), savingsRepo)

        savingsRepo.insertSaving(Saving(amount = 0.10, category = "Centavos", periodId = 1L))
        savingsRepo.insertSaving(Saving(amount = 0.20, category = "Centavos", periodId = 2L))

        // Spend 0.10 + 0.10 + 0,10 == exact 0.30
        repeat(3) { idx ->
            val res = expenseUseCase.saveExpense(
                amountInput = if (idx % 2 == 0) "0,10" else "0.10",
                dateInput = "2026-11-24 10:00",
                descriptionInput = "Cent #$idx",
                categoryInput = "Otros",
                periodId = 3L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "  centavos  "
            )
            assertTrue("Step $idx exact 0.10 must succeed without IEEE-754 drift error", res is ExpenseResult.Success)
        }

        val extraCent = expenseUseCase.saveExpense(
            amountInput = "0.01",
            dateInput = "2026-11-24 10:05",
            descriptionInput = "Overflow cent",
            categoryInput = "Otros",
            periodId = 3L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Centavos"
        )
        assertTrue(extraCent is ExpenseResult.Error)

        // Localized thousands + decimal formats: 1,234.56 + 1.234,56 == 2,469.12
        savingsRepo.insertSaving(Saving(amount = 2469.12, category = "Global", periodId = 1L))
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "1.234,56",
                dateInput = "2026-11-24 11:00",
                descriptionInput = "EU format",
                categoryInput = "Viajes",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Global"
            ) is ExpenseResult.Success
        )
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "1,234.56",
                dateInput = "2026-11-24 11:05",
                descriptionInput = "US format",
                categoryInput = "Viajes",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Global"
            ) is ExpenseResult.Success
        )
        val remainingGlobal = calculateSavingsNetBalances(
            savingsRepo.savingsFlow.value,
            expenseRepo.expensesFlow.value
        ).first { it.categoryName == "Global" }
        assertEquals(0.0, remainingGlobal.netBalance, 1e-6)
    }

    /**
     * Extreme Boundary Values, Corrupt Inputs, Strict Calendar Validation & Double Overflow Containment.
     */
    @Test
    fun test_adversarial_malformed_inputs_calendar_boundaries_overflow_and_missing_dependencies() = runTest {
        val expenseRepo = AdversarialExpenseRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val budgetRepo = AdversarialBudgetRepo()

        // 1. Missing SavingsRepository must fail gracefully
        val unconfiguredUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepository = null)
        val unconfiguredRes = unconfiguredUseCase.saveExpense(
            amountInput = "50",
            dateInput = "2026-11-24 10:00",
            descriptionInput = "",
            categoryInput = "Salud",
            periodId = 1L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Emergencias"
        )
        assertEquals(ExpenseResult.Error("Módulo de ahorros no configurado"), unconfiguredRes)

        val useCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)
        savingsRepo.insertSaving(Saving(amount = 1000.0, category = "Emergencias", periodId = 1L))

        // 2. Invalid periodId values when budgetRepository != null
        listOf(null, 0L, -1L, Long.MIN_VALUE).forEach { invalidPeriod ->
            val res = useCase.saveExpense(
                amountInput = "10",
                dateInput = "2026-11-24 10:00",
                descriptionInput = "",
                categoryInput = "Salud",
                periodId = invalidPeriod,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            )
            assertEquals("Period $invalidPeriod must be rejected", ExpenseResult.Error("Período no seleccionado"), res)
        }

        // 3. Corrupt / boundary amounts
        listOf("", "   ", "0", "0.00", "-0.0", "-0.01", "-999999", "NaN", "Infinity", "-Infinity", "1,2,3", "abc").forEach { badAmount ->
            val res = useCase.saveExpense(
                amountInput = badAmount,
                dateInput = "2026-11-24 10:00",
                descriptionInput = "",
                categoryInput = "Salud",
                periodId = 1L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            )
            assertEquals("Amount '$badAmount' must be rejected", ExpenseResult.Error("El monto debe ser mayor a cero"), res)
        }

        // 4. Blank / whitespace savingCategoryInput and categoryInput
        assertEquals(
            ExpenseResult.Error("Debes seleccionar una categoría de ahorro"),
            useCase.saveExpense(
                amountInput = "10",
                dateInput = "2026-11-24 10:00",
                descriptionInput = "",
                categoryInput = "Salud",
                periodId = 1L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "  \t\n "
            )
        )
        assertEquals(
            ExpenseResult.Error("Debes seleccionar una categoría"),
            useCase.saveExpense(
                amountInput = "10",
                dateInput = "2026-11-24 10:00",
                descriptionInput = "",
                categoryInput = "  \t\n ",
                periodId = 1L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            )
        )

        // 5. Impossible calendar dates (non-leap Feb 29, Apr 31, hour 24, minute 60, injection)
        listOf("2026-02-29", "2026-04-31 10:00", "2026-11-24 24:00", "2026-11-24 12:60", "", "2026/11/24").forEach { badDate ->
            val res = useCase.saveExpense(
                amountInput = "10",
                dateInput = badDate,
                descriptionInput = "",
                categoryInput = "Salud",
                periodId = 1L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            )
            assertEquals("Date '$badDate' must be rejected", ExpenseResult.Error("Fecha inválida"), res)
        }

        // 6. Double.MAX_VALUE overflow in calculateSavingsNetBalances & buildSavingsNetSummary
        val overflowSavings = listOf(
            Saving(id = 1L, amount = Double.MAX_VALUE * 0.75, category = "Colosal", periodId = 1L),
            Saving(id = 2L, amount = Double.MAX_VALUE * 0.75, category = "Colosal", periodId = 2L)
        )
        val overflowBalances = calculateSavingsNetBalances(overflowSavings, emptyList())
        assertTrue(overflowBalances.single().netBalance.isFinite())
        val overflowSummary = buildSavingsNetSummary(overflowSavings, emptyList(), emptyList())
        assertTrue("Overflow must never produce Infinite totalAmount", overflowSummary.totalAmount.isFinite())
    }

    /**
     * Bidirectional Funding Source Transition on Update (BUDGET -> SAVINGS -> BUDGET):
     * Verifies metadata cleanup (`budgetId`, `budgetCategory`, `savingCategory`) and exact restoration
     * of both current-period budget consumption and cumulative savings net balance.
     */
    @Test
    fun test_adversarial_update_expense_switching_funding_source_bidirectional_state_and_balance_integrity() = runTest {
        val budget = Budget(id = 10L, amount = 500.0, category = "Salud", subcategory = "Consultas", periodId = 2L)
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo(listOf(budget))
        val savingsRepo = AdversarialSavingsRepo()
        savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))

        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)
        val incomeUseCase = IncomeUseCase(AdversarialIncomeRepo(), budgetRepo, expenseRepository = expenseRepo)

        // 1. Save as BUDGET ($200)
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "200",
                dateInput = "2026-11-24 09:00",
                descriptionInput = "Consulta",
                categoryInput = "Salud",
                periodId = 2L,
                budgetId = 10L,
                budgetCategoryInput = "Salud",
                fundingSource = ExpenseFundingSource.BUDGET
            ) is ExpenseResult.Success
        )
        val expId = expenseRepo.expensesFlow.value.single().id
        assertEquals(200.0, incomeUseCase.calculateTotalBudgetSpent(listOf(budget), expenseRepo.expensesFlow.value, null, null, 2L), 0.001)

        // 2. Update same expense to SAVINGS ($300 from "Emergencias")
        assertTrue(
            expenseUseCase.updateExpense(
                id = expId,
                amountInput = "300",
                dateInput = "2026-11-24 09:00",
                descriptionInput = "Consulta + exámenes",
                categoryInput = "Salud",
                periodId = 2L,
                budgetId = 10L, // Stale budgetId passed by caller must be wiped!
                budgetCategoryInput = "Salud",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            ) is ExpenseResult.Success
        )
        val afterToSavings = expenseRepo.expensesFlow.value.single()
        assertEquals(ExpenseFundingSource.SAVINGS, afterToSavings.fundingSource)
        assertEquals(0L, afterToSavings.budgetId)
        assertEquals("", afterToSavings.budgetCategory)
        assertEquals("Emergencias", afterToSavings.savingCategory)
        assertEquals(0.0, incomeUseCase.calculateTotalBudgetSpent(listOf(budget), expenseRepo.expensesFlow.value, null, null, 2L), 0.001)
        assertEquals(200.0, calculateSavingsNetBalances(savingsRepo.savingsFlow.value, expenseRepo.expensesFlow.value).single().netBalance, 0.001)

        // 3. Update same expense back to BUDGET ($250 from Budget #10)
        assertTrue(
            expenseUseCase.updateExpense(
                id = expId,
                amountInput = "250",
                dateInput = "2026-11-24 09:00",
                descriptionInput = "Reembolsado a presupuesto",
                categoryInput = "Salud",
                periodId = 2L,
                budgetId = 10L,
                budgetCategoryInput = "Salud",
                fundingSource = ExpenseFundingSource.BUDGET,
                savingCategoryInput = "Emergencias" // Stale savingCategory must be wiped!
            ) is ExpenseResult.Success
        )
        val afterToBudget = expenseRepo.expensesFlow.value.single()
        assertEquals(ExpenseFundingSource.BUDGET, afterToBudget.fundingSource)
        assertEquals(10L, afterToBudget.budgetId)
        assertEquals("", afterToBudget.savingCategory)
        assertEquals(250.0, incomeUseCase.calculateTotalBudgetSpent(listOf(budget), expenseRepo.expensesFlow.value, null, null, 2L), 0.001)
        assertEquals(500.0, calculateSavingsNetBalances(savingsRepo.savingsFlow.value, expenseRepo.expensesFlow.value).single().netBalance, 0.001)
    }

    /**
     * EXPOSES BUG #6 (NaN Contamination & Rounding Mismatch in [ExpenseUseCase.calculateCategoryExpenses]):
     * 1. A single corrupted NaN record in BUDGET expenses causes `totalSpent = NaN` in `calculateCategoryExpenses`,
     *    collapsing every valid category's `percent` and `progress` to 0% while `calculateExpenseCategorySummary`
     *    cleanly ignores corrupt records.
     * 2. Even with clean records ($200 Food vs $100 Transport), `calculateExpenseCategorySummary` computes 67%
     *    (`roundToInt()`), whereas `calculateCategoryExpenses` truncates via `(progress * 100).toInt()` to 66%
     *    on the exact same screen.
     */
    @Test
    fun test_adversarial_corrupted_nan_budget_expense_and_percentage_consistency_in_calculateCategoryExpenses() {
        val useCase = ExpenseUseCase(AdversarialExpenseRepo())
        val categories = listOf(
            Category("Comida", "🍔", "#EF4444", emptyList()),
            Category("Transporte", "🚗", "#3B82F6", emptyList())
        )
        val expenses = listOf(
            Expense(id = 1L, amount = 200.0, date = "2026-11-24", description = "", category = "Comida", periodId = 1L, fundingSource = ExpenseFundingSource.BUDGET),
            Expense(id = 2L, amount = 100.0, date = "2026-11-24", description = "", category = "Transporte", periodId = 1L, fundingSource = ExpenseFundingSource.BUDGET),
            Expense(id = 3L, amount = 900.0, date = "2026-11-24", description = "", category = "Comida", periodId = 1L, fundingSource = ExpenseFundingSource.SAVINGS, savingCategory = "Emergencias"),
            Expense(id = 4L, amount = Double.NaN, date = "2026-11-24", description = "Corrupt", category = "Otros", periodId = 1L, fundingSource = ExpenseFundingSource.BUDGET)
        )

        val donutSummary = useCase.calculateExpenseCategorySummary(expenses, categories)
        val breakdownItems = useCase.calculateCategoryExpenses(expenses, categories)

        val donutFood = donutSummary.items.first { it.categoryName == "Comida" }
        val breakdownFood = breakdownItems.first { it.categoryName == "Comida" }

        assertEquals(67, donutFood.percentage)
        assertEquals(
            "calculateCategoryExpenses must ignore corrupt NaN entries and match PeriodSummaryCard rounding (67%)",
            donutFood.percentage,
            breakdownFood.percent
        )
        assertTrue("Valid category progress must remain > 0f despite corrupt sibling entry", breakdownFood.progress > 0.6f)
    }

    // =========================================================================
    // SECTION 3: Concurrency, Race Conditions & ViewModel State Transitions
    // =========================================================================

    /**
     * Concurrency Stress Test (20 parallel coroutines with yielding repository):
     * 20 concurrent `saveExpense` calls each requesting $100 against a $500 cumulative savings balance
     * ($300 in Period 1 + $200 in Period 2). Strictly 5 must succeed and 15 must fail with zero overdraft.
     */
    @Test
    fun test_adversarial_concurrent_20_coroutines_spending_shared_savings_never_overdraws() = runTest {
        val expenseRepo = AdversarialExpenseRepo(yieldOnMutation = true)
        val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)
        val budgetRepo = AdversarialBudgetRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)

        savingsRepo.insertSaving(Saving(amount = 300.0, category = "Emergencias", periodId = 1L))
        savingsRepo.insertSaving(Saving(amount = 200.0, category = " emergencias ", periodId = 2L))

        val results = (1..20).map { idx ->
            async {
                expenseUseCase.saveExpense(
                    amountInput = "100",
                    dateInput = "2026-11-24 10:00",
                    descriptionInput = "Concurrent #$idx",
                    categoryInput = "Salud",
                    periodId = 3L,
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategoryInput = "EMERGENCIAS"
                )
            }
        }.awaitAll()

        val successes = results.count { it is ExpenseResult.Success }
        val errors = results.count { it is ExpenseResult.Error }
        assertEquals("Strictly 5 concurrent expenses of $100 may succeed against $500 balance", 5, successes)
        assertEquals(15, errors)
        assertEquals(5, expenseRepo.expensesFlow.value.size)

        val finalBalance = calculateSavingsNetBalances(
            savingsRepo.savingsFlow.value,
            expenseRepo.expensesFlow.value
        ).single()
        assertEquals(500.0, finalBalance.totalSaved, 0.001)
        assertEquals(500.0, finalBalance.totalSpent, 0.001)
        assertEquals(0.0, finalBalance.netBalance, 0.001)
    }

    /**
     * EXPOSES BUG #4 (Missing Concurrency / Double-Click Guard in [ExpenseViewModel.confirmEdit]):
     * While `saveExpense()` guards with `if (isSavingExpense) return`, `confirmEdit()` has no in-flight guard.
     * Rapid double invocation of `confirmEdit()` fires two concurrent repository updates.
     */
    @Test
    fun test_adversarial_viewmodel_double_confirmEdit_must_be_idempotent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val expenseRepo = AdversarialExpenseRepo(yieldOnMutation = true)
            val savingsRepo = AdversarialSavingsRepo()
            val budgetRepo = AdversarialBudgetRepo()
            savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))

            val existing = Expense(
                id = 1L,
                amount = 100.0,
                date = "2026-11-24 10:00",
                description = "Inicial",
                category = "Salud",
                periodId = 1L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategory = "Emergencias"
            )
            expenseRepo.insertExpense(existing)

            val viewModel = ExpenseViewModel(ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo))
            viewModel.onPeriodSelected(1L)
            advanceUntilIdle()

            viewModel.startEditing(existing)
            viewModel.onEditAmountChange("250")

            // Simulate rapid double-tap on Save button in Edit Expense Dialog before coroutine completes
            viewModel.confirmEdit()
            viewModel.confirmEdit()
            advanceUntilIdle()

            assertEquals(
                "Double invocation of confirmEdit() must execute updateExpense at most once",
                1,
                expenseRepo.updateCallCount
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG #5 (Stale `fundingSourceInput` State in [ExpenseViewModel] on Savings Depletion & Period Switch):
     * 1. When active budgets exist (`hasActiveBudget = true`) and user selected `SAVINGS` ("Emergencias"),
     *    if all savings are subsequently depleted (`hasAvailableSavings` becomes `false`), `fundingSourceInput`
     *    must revert to `BUDGET` and clear `savingCategoryInput`.
     * 2. When `budgetRepository == null` (standalone mode) and `hasAvailableSavings == true`, calling
     *    `onPeriodSelected(2L)` must keep `fundingSourceInput == ExpenseFundingSource.SAVINGS` instead of
     *    getting stuck on `BUDGET` because `getBudgetsByPeriod` emits `emptyFlow()`.
     */
    @Test
    fun test_adversarial_viewmodel_funding_source_recovers_when_savings_deplete_or_period_switches_without_budget_repo() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            // Part A: Standalone ExpenseUseCase (budgetRepository = null) + available savings + period switch
            val standaloneExpRepo = AdversarialExpenseRepo()
            val standaloneSavRepo = AdversarialSavingsRepo()
            standaloneSavRepo.insertSaving(Saving(id = 1L, amount = 300.0, category = "Viajes", periodId = 1L))

            val standaloneVm = ExpenseViewModel(
                ExpenseUseCase(standaloneExpRepo, budgetRepository = null, savingsRepository = standaloneSavRepo)
            )
            advanceUntilIdle()
            assertEquals(ExpenseFundingSource.SAVINGS, standaloneVm.uiState.value.fundingSourceInput)

            standaloneVm.onPeriodSelected(2L)
            advanceUntilIdle()
            assertEquals(
                "With no active budget and available savings, onPeriodSelected must select SAVINGS",
                ExpenseFundingSource.SAVINGS,
                standaloneVm.uiState.value.fundingSourceInput
            )

            // Part B: Period with active budget + savings; user selects SAVINGS, then savings become depleted
            val expRepo = AdversarialExpenseRepo()
            val savRepo = AdversarialSavingsRepo()
            val budRepo = AdversarialBudgetRepo(
                listOf(Budget(id = 1L, amount = 400.0, category = "Comida", periodId = 1L))
            )
            savRepo.insertSaving(Saving(id = 10L, amount = 200.0, category = "Emergencias", periodId = 1L))

            val vm = ExpenseViewModel(ExpenseUseCase(expRepo, budRepo, savRepo))
            vm.onPeriodSelected(1L)
            advanceUntilIdle()

            vm.onSavingCategorySelected("Emergencias")
            assertEquals(ExpenseFundingSource.SAVINGS, vm.uiState.value.fundingSourceInput)

            // Deplete all savings externally
            expRepo.insertExpense(
                Expense(
                    id = 99L,
                    amount = 200.0,
                    date = "2026-11-24 12:00",
                    description = "Agota ahorro",
                    category = "Salud",
                    periodId = 1L,
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            )
            advanceUntilIdle()

            assertFalse(vm.uiState.value.hasAvailableSavings)
            assertTrue(vm.uiState.value.hasActiveBudget)
            assertEquals(
                "When savings become depleted while an active budget exists, fundingSourceInput must revert to BUDGET",
                ExpenseFundingSource.BUDGET,
                vm.uiState.value.fundingSourceInput
            )
            assertEquals("", vm.uiState.value.savingCategoryInput)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // SECTION 4: Room SQLite Persistence, Legacy Nulls & Extreme Payloads
    // =========================================================================

    /**
     * Real Room In-Memory SQLite Persistence Test (`AppDatabase` v6 + `SavingsDatabase` v2):
     * Injects raw `ExpenseEntity` rows with null/corrupt/whitespace `fundingSource`, null `savingCategory`,
     * null `periodId`, and 300+ char Unicode/RTL/Emoji/SQL-quote category names.
     */
    @Test
    fun test_adversarial_room_persistence_legacy_nulls_corrupt_funding_source_and_extreme_unicode_roundtrip() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val savingsDb = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val roomExpenseRepo = RoomExpenseRepository(appDb.expenseDao())
            val roomSavingsRepo = RoomSavingsRepository(savingsDb.savingDao(), savingsDb.savingCategoryDao())

            val extremeCategory = "🛡️ طوارئ O'Reilly -- ; DROP TABLE expenses; 🎉 " + "K".repeat(260)
            roomSavingsRepo.insertCategory(Category(extremeCategory, "🛡️", "#10B981", emptyList()))
            roomSavingsRepo.insertSaving(Saving(amount = 1000.0, category = extremeCategory, periodId = 1L))

            // Raw SQLite entity insertions simulating legacy pre-v6 rows & corrupted storage values
            appDb.expenseDao().insert(
                ExpenseEntity(
                    amount = 50.0,
                    date = "2026-11-20",
                    description = "Legacy null source",
                    category = "Comida",
                    periodId = null,
                    fundingSource = null,
                    savingCategory = null
                )
            )
            appDb.expenseDao().insert(
                ExpenseEntity(
                    amount = 75.0,
                    date = "2026-11-21",
                    description = "Corrupted enum string",
                    category = "Comida",
                    periodId = 1L,
                    fundingSource = "UNKNOWN_CORRUPT_SOURCE",
                    savingCategory = extremeCategory
                )
            )
            appDb.expenseDao().insert(
                ExpenseEntity(
                    amount = 300.0,
                    date = "2026-11-22",
                    description = "Mixed-case padded savings source",
                    category = "Salud",
                    periodId = 2L,
                    fundingSource = "  sAvInGs  ",
                    savingCategory = extremeCategory
                )
            )

            val loaded = roomExpenseRepo.getAllExpenses().first()
            assertEquals(3, loaded.size)

            val legacyRow = loaded.first { it.description == "Legacy null source" }
            assertEquals(ExpenseFundingSource.BUDGET, legacyRow.fundingSource)
            assertEquals("", legacyRow.savingCategory)
            assertEquals(0L, legacyRow.periodId)

            val corruptRow = loaded.first { it.description == "Corrupted enum string" }
            assertEquals(ExpenseFundingSource.BUDGET, corruptRow.fundingSource)

            val savingsRow = loaded.first { it.description == "Mixed-case padded savings source" }
            assertEquals(ExpenseFundingSource.SAVINGS, savingsRow.fundingSource)
            assertEquals(extremeCategory, savingsRow.savingCategory)

            // Only the 300.0 valid SAVINGS row must deduct from the 1000.0 saving (corrupt enum fell back to BUDGET)
            val netSummary = buildSavingsNetSummary(
                savings = roomSavingsRepo.getAllSavings().first(),
                expenses = loaded,
                categories = roomSavingsRepo.getAllCategories().first()
            )
            assertEquals(700.0, netSummary.totalAmount, 0.001)
            assertEquals(extremeCategory, netSummary.items.single().categoryName)
            assertEquals("#10B981", netSummary.items.single().colorHex)
        } finally {
            appDb.close()
            savingsDb.close()
        }
    }

    // =========================================================================
    // SECTION 5: Compose UI Adversarial & Lifecycle Edge Cases
    // =========================================================================

    /**
     * EXPOSES BUG #2 (Double Deduction of Edited Expense in Edit Dialog's Available Balance):
     * When "Viajes" has $500 saved and an existing savings-funded expense of $350, opening Edit mode
     * on that $350 expense MUST display "Viajes • $ 500.00" as available (using `excludeExpenseId = expense.id`),
     * NOT "Viajes • $ 150.00" (which falsely deducts the expense being edited from its own available limit).
     */
    @Test
    fun test_adversarial_ui_edit_expense_dialog_shows_undiscounted_available_balance_for_edited_expense() {
        val expenseRepo = AdversarialExpenseRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val categoryRepo = AdversarialCategoryRepo(listOf(Category("Viajes", "✈️", "#10B981", emptyList())))

        runBlocking {
            savingsRepo.insertCategory(Category("Viajes", "✈️", "#10B981", emptyList()))
            savingsRepo.insertSaving(Saving(id = 1L, amount = 500.0, category = "Viajes", periodId = 1L))
            expenseRepo.insertExpense(
                Expense(
                    id = 42L,
                    amount = 350.0,
                    date = "2026-11-24 10:00",
                    description = "Tiquetes",
                    category = "Viajes",
                    periodId = 2L,
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Viajes"
                )
            )
        }

        val viewModel = ExpenseViewModel(
            ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo),
            CategoryUseCase(categoryRepo)
        )
        viewModel.onPeriodSelected(2L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ExpensesTabContent(viewModel = viewModel, currency = usd)
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.uiState.value.expenses.size == 1 && viewModel.uiState.value.savingsBalances.isNotEmpty()
        }

        val expenseToEdit = viewModel.uiState.value.expenses.single()
        composeTestRule.runOnIdle {
            viewModel.startEditing(expenseToEdit)
        }
        composeTestRule.waitForIdle()

        // Inside Edit Dialog, the selected saving category must display the full $500.00 available to this expense
        composeTestRule.onNodeWithTag("selector_expense_saving_category").assertIsDisplayed()
        composeTestRule.onNodeWithText("Viajes • $ 500.00").assertIsDisplayed()
    }

    /**
     * EXPOSES BUG #3 (Depleted $0.00 Savings Categories Displayed as Selectable Options in Add Dialog):
     * 1. Verifies `ExpensesTabContent` enables `btn_open_add_expense` (and hides `banner_no_active_budget`)
     *    when only savings are available (`hasActiveBudget = false, hasAvailableSavings = true`), and dynamically
     *    disables `btn_open_add_expense` + shows `banner_no_active_budget` as soon as savings hit $0.00.
     * 2. When a budget is then added in Period 2 while "Emergencias" remains depleted at $0.00 ($200 saved - $200 spent),
     *    opening Add Expense Dialog -> switching to "Ahorros" -> opening `selector_expense_saving_category`
     *    MUST display "No hay ahorros disponibles" and MUST NOT offer "Emergencias • $ 0.00" as a selectable source.
     */
    @Test
    fun test_adversarial_ui_add_dialog_depleted_savings_shows_empty_message_and_dynamic_button_state() {
        val expenseRepo = AdversarialExpenseRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo, savingsRepo)

        runBlocking {
            savingsRepo.insertSaving(Saving(id = 1L, amount = 200.0, category = "Emergencias", periodId = 1L))
        }

        val viewModel = ExpenseViewModel(expenseUseCase)
        viewModel.onPeriodSelected(2L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ExpensesTabContent(viewModel = viewModel, currency = usd)
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.uiState.value.hasAvailableSavings
        }
        composeTestRule.waitForIdle()

        // 1. No budget, $200 savings -> Add button enabled, no warning banner
        composeTestRule.onNodeWithTag("banner_no_active_budget").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsEnabled()

        // 2. Deplete the $200 savings -> Add button must disable and warning banner must appear
        runBlocking {
            expenseUseCase.saveExpense(
                amountInput = "200",
                dateInput = "2026-11-24 10:00",
                descriptionInput = "Agotar fondo",
                categoryInput = "Salud",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            !viewModel.uiState.value.hasAvailableSavings
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("banner_no_active_budget").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsNotEnabled()

        // 3. Add a budget in Period 2 so Add Expense Dialog can be opened, then switch to Ahorros
        runBlocking {
            budgetRepo.insertBudget(Budget(id = 5L, amount = 300.0, category = "Comida", periodId = 2L))
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.uiState.value.hasActiveBudget
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("source_option_savings").performClick()
        composeTestRule.onNodeWithTag("selector_expense_saving_category").performClick()

        // Because all savings categories have $0.00 available, dropdown must show empty message, not "$ 0.00" option
        composeTestRule.onNodeWithText("No hay ahorros disponibles").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saving_source_option_Emergencias").assertDoesNotExist()
    }

    /**
     * Compose UI Edge Case: `ExpenseCardItem` with `fundingSource = SAVINGS` and blank/whitespace `savingCategory`
     * must render fallback badge `"Ahorro"` cleanly without a dangling colon (`"Ahorro:   "`).
     */
    @Test
    fun test_adversarial_ui_expense_card_blank_saving_category_renders_clean_prefix_badge() {
        val orphanSavingsExpense = Expense(
            id = 99L,
            amount = 125.50,
            date = "2026-11-24 18:30",
            description = "Gasto sin nombre de ahorro",
            category = "Imprevistos",
            periodId = 1L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategory = "   "
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                ExpenseCardItem(expense = orphanSavingsExpense, currency = usd)
            }
        }

        composeTestRule.onNodeWithTag("badge_expense_savings_source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ahorro").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ahorro:", substring = true).assertDoesNotExist()
    }

    /**
     * Compose UI & Lifecycle Period-Switch Invariant on `SavingsTabContent`:
     * When switching from Period 1 ($600 saved) to an empty Period 99 ($0 saved in period),
     * `card_savings_overview` (period card) must reset to `$ 0.00`, while `card_savings_net_overview`
     * (cumulative global net card) must continue displaying `$ 450.00` ($600 saved - $150 spent).
     */
    @Test
    fun test_adversarial_ui_savings_tab_preserves_cumulative_net_card_when_switching_to_empty_period() {
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()

        runBlocking {
            savingsRepo.insertCategory(Category("Emergencias", "🛡️", "INVALID_COLOR_HEX", emptyList()))
            savingsRepo.insertSaving(Saving(id = 1L, amount = 600.0, category = "Emergencias", periodId = 1L))
            expenseRepo.insertExpense(
                Expense(
                    id = 10L,
                    amount = 150.0,
                    date = "2026-11-24 15:00",
                    description = "Reparación",
                    category = "Hogar",
                    periodId = 2L,
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            )
        }

        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo, expenseRepo)
        val savingsViewModel = SavingsViewModel(savingsUseCase)
        savingsViewModel.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SavingsTabContent(viewModel = savingsViewModel, currency = usd)
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            abs(savingsViewModel.uiState.value.netSavingsSummary.totalAmount - 450.0) < 0.001 &&
                abs(savingsViewModel.uiState.value.savingsSummary.totalAmount - 600.0) < 0.001
        }
        composeTestRule.waitForIdle()

        // Switch to empty Period 99 -> Period card resets to $0.00, Net cumulative card stays $450.00!
        composeTestRule.runOnIdle {
            savingsViewModel.onPeriodSelected(99L)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            savingsViewModel.uiState.value.selectedPeriodId == 99L &&
                savingsViewModel.uiState.value.savingsSummary.isEmpty
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_total_saved").assertTextEquals("$ 0.00")
        composeTestRule.onNodeWithTag("card_savings_net_overview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_total_net_savings").assertTextEquals("$ 450.00")
        composeTestRule.onNode(
            hasTestTag("legend_amount_Emergencias") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("$ 450.00")
        composeTestRule.onNode(
            hasTestTag("legend_percent_Emergencias") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("100%")
    }
}
