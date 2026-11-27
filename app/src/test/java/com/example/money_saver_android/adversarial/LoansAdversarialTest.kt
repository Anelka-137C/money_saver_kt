package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.local.LoansDatabase
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.RoomLoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.buildSavingsNetSummary
import com.example.money_saver_android.domain.usecase.calculateCategoryAvailableSavings
import com.example.money_saver_android.domain.usecase.calculateCategorySavingsOutflows
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.AppTab
import com.example.money_saver_android.ui.LoansView
import com.example.money_saver_android.ui.MainContainer
import com.example.money_saver_android.ui.components.loans.buildPersonInitials
import com.example.money_saver_android.ui.components.loans.filterDecimalInput
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeLanguagePreferenceRepository
import com.example.money_saver_android.viewmodel.LoansViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.Locale
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Loans Module (#15) and Centralized Net Savings:
 * - Domain & Mathematical Invariants (`calculateSavingsNetBalances`, `calculateCategoryAvailableSavings`,
 *   `calculateCategorySavingsOutflows`, `buildSavingsNetSummary`, `LoanUseCase.calculateTotalReceivable`)
 * - Input Validation, Unicode Whitespace, Numeric Parsing & Overflow Boundaries (`LoanUseCase`)
 * - Cross-Module Conservation, Mutex Contention & Coroutine Cancellation (`LoanUseCase`, `ExpenseUseCase`, `SavingsUseCase`)
 * - Room SQLite Persistence, SQL Injection, Unicode & Reassignment Edge Cases (`LoansDatabase`, `RoomLoanRepository`)
 * - ViewModel Idempotency, Error Recovery & Cross-Screen Reactivity (`LoansViewModel`, `ExpenseViewModel`, `SavingsViewModel`)
 * - UI Helpers, Localization Parity & Compose Navigation/Rendering (`filterDecimalInput`, `buildPersonInitials`, `LoansView`, `MainContainer`)
 *
 * Rules:
 * 1. Strictly test-only file; production source code is untouched.
 * 2. Bugs discovered in production code are exposed via assertions expecting the valid domain/UI contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LoansAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var i18nManager: I18nManager
    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$", formatPattern = "#,##0.00")
    private val jpy = Currency(code = "JPY", name = "Japanese Yen", symbol = "¥", formatPattern = "#,##0")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        i18nManager = I18nManager().apply {
            initFromContext(context)
            setLanguage("es")
        }
    }

    // =========================================================================
    // Adversarial In-Memory Repositories
    // =========================================================================

    private class AdversarialLoanRepo(
        private val yieldOnMutation: Boolean = false
    ) : LoanRepository {
        val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
        private var nextId = 1L
        var insertCallCount = 0
            private set
        var insertGate: CompletableDeferred<Unit>? = null
        var onInsertStarted: CompletableDeferred<Unit>? = null
        var throwOnInsert: Throwable? = null
        var throwOnRead: Throwable? = null

        override fun getAllLoans(): Flow<List<Loan>> {
            val err = throwOnRead
            if (err != null) {
                return flow { throw err }
            }
            return loansFlow
        }

        override suspend fun insertLoan(loan: Loan): Long {
            insertCallCount++
            onInsertStarted?.complete(Unit)
            insertGate?.await()
            throwOnInsert?.let { throw it }
            if (yieldOnMutation) yield()
            val id = if (loan.id > 0L) loan.id else nextId++
            loansFlow.value = listOf(loan.copy(id = id)) + loansFlow.value
            return id
        }

        override suspend fun reassignSavingCategory(oldName: String, newName: String) {
            if (yieldOnMutation) yield()
            val trimmedOld = oldName.trim()
            if (trimmedOld.isEmpty()) return
            loansFlow.value = loansFlow.value.map {
                if (it.savingCategory.trim().equals(trimmedOld, ignoreCase = true)) {
                    it.copy(savingCategory = newName)
                } else {
                    it
                }
            }
        }
    }

    private class AdversarialSavingsRepo(
        private val yieldOnMutation: Boolean = false,
        initialCategories: List<Category> = listOf(
            Category("Emergencias", "🚨", "#3B82F6", emptyList()),
            Category("Viajes", "✈️", "#10B981", emptyList())
        )
    ) : SavingsRepository {
        val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
        val categoriesFlow = MutableStateFlow(initialCategories)
        private var nextId = 1L

        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow
        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            savingsFlow.map { list -> list.filter { it.periodId == periodId } }
        override suspend fun getSavingById(id: Long): Saving? = savingsFlow.value.find { it.id == id }

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
            savingsFlow.value = savingsFlow.value.filter { it.id != id }
        }

        override suspend fun countSavingsForCategory(categoryName: String): Int =
            savingsFlow.value.count { it.category.trim().equals(categoryName.trim(), ignoreCase = true) }

        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int =
            savingsFlow.value.count {
                it.category.trim().equals(categoryName.trim(), ignoreCase = true) &&
                    it.subcategory.trim().equals(subcategoryName.trim(), ignoreCase = true)
            }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun insertCategory(category: Category) { categoriesFlow.value += category }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}

        override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
            if (yieldOnMutation) yield()
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(oldName, ignoreCase = true)) it.copy(name = newName, color = color, icon = icon) else it
            }
            savingsFlow.value = savingsFlow.value.map {
                if (it.category.trim().equals(oldName.trim(), ignoreCase = true)) it.copy(category = newName) else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class AdversarialExpenseRepo(
        private val yieldOnMutation: Boolean = false
    ) : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        private var nextId = 1L

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
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
            expensesFlow.value = expensesFlow.value.filter { it.id != id }
        }

        override suspend fun reassignSavingCategory(oldName: String, newName: String) {
            expensesFlow.value = expensesFlow.value.map {
                if (it.fundingSource == ExpenseFundingSource.SAVINGS &&
                    it.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)
                ) {
                    it.copy(savingCategory = newName)
                } else {
                    it
                }
            }
        }
    }

    private class AdversarialIncomeRepo(
        initialIncomes: List<Income> = listOf(
            Income(id = 1L, amount = 10_000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = 1L)
        )
    ) : IncomeRepository {
        val incomesFlow = MutableStateFlow(initialIncomes)
        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            incomesFlow.map { list -> list.filter { it.periodId == periodId } }
        override suspend fun getIncomeById(id: Long): Income? = incomesFlow.value.find { it.id == id }
        override suspend fun insertIncome(income: Income): Long = 1L
        override suspend fun updateIncome(income: Income) {}
        override suspend fun deleteIncome(id: Long) {}
        override suspend fun countIncomesForCategory(categoryName: String): Int = 0
        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private fun hasUnpairedUtf16Surrogate(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= value.length || !Character.isLowSurrogate(value[i + 1])) {
                    return true
                }
                i += 2
            } else if (Character.isLowSurrogate(ch)) {
                return true
            } else {
                i++
            }
        }
        return false
    }

    // =========================================================================
    // 1. Property-Based & Mathematical Invariants
    // =========================================================================

    /**
     * Property: Over 200 randomized datasets mixing valid/corrupt savings, budget & savings expenses,
     * and personal & savings-backed loans:
     * - Every balance field is finite and >= 0.
     * - `netBalance == max(0, totalSaved - totalSpent - totalLoaned)` (exact BigDecimal).
     * - `calculateCategoryAvailableSavings` strictly agrees with `calculateSavingsNetBalances`.
     * - `calculateCategorySavingsOutflows` strictly equals `totalSpent + totalLoaned` and ignores
     *   personal loans and budget-funded expenses.
     * - `buildSavingsNetSummary` excludes zero-net categories and matches exact sum of positive nets.
     * - Output order is strictly descending by `netBalance`, tie-broken ascending by lowercase name.
     */
    @Test
    fun property_savingsNetBalances_conserveExactBigDecimalArithmeticAcrossSavingsExpensesAndLoans() {
        val rng = Random(20261126L)
        val rawNames = listOf(
            "Emergencias", "  emergencias ", "EMERGENCIAS",
            "Viajes", " viajes", "Fondo Casa", "fondo casa",
            "", "   "
        )
        val categories = listOf(
            Category("Emergencias", "🚨", "#3B82F6", emptyList()),
            Category("Viajes", "✈️", "#10B981", emptyList()),
            Category("Fondo Casa", "🏠", "#F59E0B", emptyList())
        )
        val corruptAmounts = listOf(0.0, -0.01, -500.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        repeat(200) { iteration ->
            val savings = (1..rng.nextInt(0, 14)).map { idx ->
                val amount = if (rng.nextInt(6) == 0) {
                    corruptAmounts[rng.nextInt(corruptAmounts.size)]
                } else {
                    rng.nextInt(1, 50_000) / 100.0
                }
                Saving(
                    id = idx.toLong(),
                    amount = amount,
                    category = rawNames[rng.nextInt(rawNames.size)],
                    periodId = 1L
                )
            }
            val expenses = (1..rng.nextInt(0, 14)).map { idx ->
                val amount = if (rng.nextInt(6) == 0) {
                    corruptAmounts[rng.nextInt(corruptAmounts.size)]
                } else {
                    rng.nextInt(1, 20_000) / 100.0
                }
                val isSavings = rng.nextBoolean()
                Expense(
                    id = idx.toLong(),
                    amount = amount,
                    date = "2026-11-15",
                    description = "E$idx",
                    category = "Gasto",
                    fundingSource = if (isSavings) ExpenseFundingSource.SAVINGS else ExpenseFundingSource.BUDGET,
                    savingCategory = rawNames[rng.nextInt(rawNames.size)]
                )
            }
            val loans = (1..rng.nextInt(0, 14)).map { idx ->
                val amountLent = if (rng.nextInt(6) == 0) {
                    corruptAmounts[rng.nextInt(corruptAmounts.size)]
                } else {
                    rng.nextInt(1, 20_000) / 100.0
                }
                val amountToReceive = if (amountLent.isFinite() && amountLent > 0.0) {
                    amountLent + rng.nextInt(0, 5_000) / 100.0
                } else {
                    amountLent
                }
                Loan(
                    id = idx.toLong(),
                    personName = "Persona $idx",
                    amountLent = amountLent,
                    amountToReceive = amountToReceive,
                    savingCategory = if (rng.nextInt(3) == 0) "" else rawNames[rng.nextInt(rawNames.size)]
                )
            }

            val balances = calculateSavingsNetBalances(
                savings = savings,
                expenses = expenses,
                categories = categories,
                loans = loans
            )

            // Ordering invariant
            for (i in 0 until balances.size - 1) {
                val a = balances[i]
                val b = balances[i + 1]
                assertTrue(
                    "Iteration $iteration violated sort order: $a before $b",
                    a.netBalance > b.netBalance ||
                        (a.netBalance == b.netBalance && a.categoryName.lowercase() <= b.categoryName.lowercase())
                )
            }

            // Per-category conservation & equivalence with single-category helpers
            balances.forEach { balance ->
                assertTrue("totalSaved finite & >= 0", balance.totalSaved.isFinite() && balance.totalSaved >= 0.0)
                assertTrue("totalSpent finite & >= 0", balance.totalSpent.isFinite() && balance.totalSpent >= 0.0)
                assertTrue("totalLoaned finite & >= 0", balance.totalLoaned.isFinite() && balance.totalLoaned >= 0.0)
                assertTrue("netBalance finite & >= 0", balance.netBalance.isFinite() && balance.netBalance >= 0.0)

                val expectedNet = BigDecimal.valueOf(balance.totalSaved)
                    .subtract(BigDecimal.valueOf(balance.totalSpent))
                    .subtract(BigDecimal.valueOf(balance.totalLoaned))
                    .max(BigDecimal.ZERO)
                    .toDouble()
                assertEquals("Iteration $iteration net conservation for ${balance.categoryName}", expectedNet, balance.netBalance, 1e-6)

                val singleAvailable = calculateCategoryAvailableSavings(
                    categoryName = balance.categoryName,
                    savings = savings,
                    expenses = expenses,
                    loans = loans
                ).toDouble()
                assertEquals("Iteration $iteration single vs batch available", balance.netBalance, singleAvailable, 1e-6)

                val singleOutflows = calculateCategorySavingsOutflows(
                    categoryName = balance.categoryName,
                    expenses = expenses,
                    loans = loans
                )
                val expectedOutflows = BigDecimal.valueOf(balance.totalSpent)
                    .add(BigDecimal.valueOf(balance.totalLoaned))
                assertEquals(
                    "Iteration $iteration outflows match spent + loaned",
                    0,
                    singleOutflows.compareTo(expectedOutflows)
                )
            }

            // Net summary invariant
            val summary = buildSavingsNetSummary(savings, expenses, categories, loans = loans)
            val positiveBalances = balances.filter { it.netBalance > 0.0 }
            assertEquals("Iteration $iteration summary item count", positiveBalances.size, summary.items.size)
            val expectedSummaryTotal = positiveBalances.fold(BigDecimal.ZERO) { acc, b ->
                acc.add(BigDecimal.valueOf(b.netBalance))
            }.toDouble()
            assertEquals("Iteration $iteration summary total", expectedSummaryTotal, summary.totalAmount, 1e-6)
        }
    }

    /**
     * Property: `LoanUseCase.calculateTotalReceivable` ignores negative, zero, NaN, and Infinite
     * entries and matches the exact BigDecimal sum of valid positive `amountToReceive` values.
     */
    @Test
    fun property_calculateTotalReceivable_ignoresCorruptLoansAndMatchesExactSum() {
        val rng = Random(778899L)
        val useCase = LoanUseCase(AdversarialLoanRepo())
        val corrupt = listOf(0.0, -0.001, -9999.99, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        repeat(200) { iteration ->
            var expected = BigDecimal.ZERO
            val loans = (1..rng.nextInt(0, 20)).map { idx ->
                val isCorrupt = rng.nextInt(4) == 0
                val amountToReceive = if (isCorrupt) {
                    corrupt[rng.nextInt(corrupt.size)]
                } else {
                    val v = rng.nextInt(1, 100_000) / 100.0
                    expected = expected.add(BigDecimal.valueOf(v))
                    v
                }
                Loan(
                    id = idx.toLong(),
                    personName = "P$idx",
                    amountLent = 10.0,
                    amountToReceive = amountToReceive
                )
            }
            val actual = useCase.calculateTotalReceivable(loans)
            assertTrue("Iteration $iteration total must be finite and >= 0", actual.isFinite() && actual >= 0.0)
            assertEquals("Iteration $iteration exact sum", expected.toDouble(), actual, 1e-6)
        }
    }

    /**
     * EXPOSES BUG (Locale-sensitive `.lowercase()` in `CategorySummary.kt`):
     * Under Turkish locale (`tr_TR`), `"INVERSIÓN".lowercase()` produces `"ınversión"` (dotless i),
     * whereas `"inversión".lowercase()` produces `"inversión"`.
     * `calculateCategoryAvailableSavings` uses Kotlin `equals(..., ignoreCase = true)` (matches both!),
     * while `calculateSavingsNetBalances` groups by `lowercase()` with default locale (splits them!).
     * Invariant: Both functions MUST deduct the loan identically regardless of JVM default Locale.
     */
    @Test
    fun property_calculateSavingsNetBalances_and_calculateCategoryAvailableSavings_agreeUnderTurkishLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val savings = listOf(Saving(id = 1L, amount = 500.0, category = "INVERSIÓN", periodId = 1L))
            val loans = listOf(
                Loan(id = 1L, personName = "Carlos", amountLent = 200.0, amountToReceive = 200.0, savingCategory = "inversión")
            )
            val categories = listOf(Category("INVERSIÓN", "📈", "#3B82F6", emptyList()))

            val availableSingle = calculateCategoryAvailableSavings("inversión", savings, emptyList(), loans).toDouble()
            val balances = calculateSavingsNetBalances(savings, emptyList(), categories, loans = loans)

            assertEquals(300.0, availableSingle, 1e-6)
            assertEquals(1, balances.size)
            assertEquals("Loan must be deducted even under Turkish locale", 200.0, balances.single().totalLoaned, 1e-6)
            assertEquals("Net balance must match available savings under Turkish locale", 300.0, balances.single().netBalance, 1e-6)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    /**
     * EXPOSES BUG (Double overflow silent collapse to 0.0 in `LoanUseCase.calculateTotalReceivable`):
     * Two finite loans of `1.0e308` are both accepted by `saveLoan`, yet summing them overflows
     * `Double.MAX_VALUE` to `Double.POSITIVE_INFINITY` and `calculateTotalReceivable` silently
     * returns `0.0` (telling the user they have $0 receivable!).
     */
    @Test
    fun calculateTotalReceivable_hugeFiniteLoansExceedingDoubleMax_doesNotSilentlyCollapseToZero() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val useCase = LoanUseCase(loanRepo)

        val r1 = useCase.saveLoan("A", "1e308", "1e308")
        val r2 = useCase.saveLoan("B", "1e308", "1e308")

        if (r1 is LoanResult.Success && r2 is LoanResult.Success) {
            val total = useCase.calculateTotalReceivable(loanRepo.loansFlow.value)
            assertTrue(
                "Total receivable must never silently collapse to 0.0 when valid positive loans exist; got $total",
                total > 0.0 && total.isFinite()
            )
        } else {
            // Alternatively, if saveLoan rejects absurd astronomical values above a safe monetary cap, that also upholds the invariant.
            assertTrue("At least one overflow loan must be rejected if not saturated", r1 is LoanResult.Error || r2 is LoanResult.Error)
        }
    }

    // =========================================================================
    // 2. LoanUseCase Input Validation, Numeric Parsing & Boundary Attacks
    // =========================================================================

    /**
     * Invariant: Every malformed, negative, zero, non-finite, under-receivable, or overdrawn input
     * MUST return `LoanResult.Error` and leave `LoanRepository` completely untouched.
     */
    @Test
    fun saveLoan_rejectsExtremeInvalidAndMalformedInputsWithoutPersisting() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        savingsRepo.insertSaving(Saving(amount = 200.0, category = "Emergencias", periodId = 1L))
        expenseRepo.insertExpense(
            Expense(
                amount = 50.0,
                date = "2026-11-10",
                description = "Farmacia",
                category = "Salud",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategory = "Emergencias"
            )
        )
        val useCase = LoanUseCase(loanRepo, savingsRepo, expenseRepo)

        val invalidCases = listOf(
            // Blank / whitespace person
            Triple("", "100", "100") to "",
            Triple("   \t\n  ", "100", "100") to "",
            // Invalid amountLent
            Triple("Ana", "", "100") to "",
            Triple("Ana", "0", "100") to "",
            Triple("Ana", "0.00", "100") to "",
            Triple("Ana", "-0.01", "100") to "",
            Triple("Ana", "-500", "500") to "",
            Triple("Ana", "NaN", "100") to "",
            Triple("Ana", "Infinity", "Infinity") to "",
            Triple("Ana", "-Infinity", "100") to "",
            Triple("Ana", "abc", "100") to "",
            Triple("Ana", "$100", "$100") to "",
            Triple("Ana", "1,2,3", "100") to "",
            Triple("Ana", "1.2.3", "100") to "",
            // Invalid amountToReceive or amountToReceive < amountLent
            Triple("Ana", "100", "") to "",
            Triple("Ana", "100", "0") to "",
            Triple("Ana", "100", "-100") to "",
            Triple("Ana", "100", "NaN") to "",
            Triple("Ana", "100", "Infinity") to "",
            Triple("Ana", "100", "99.99") to "",
            Triple("Ana", "100.0001", "100.0000") to "",
            // Savings-source validation failures (available = 200 - 50 = 150)
            Triple("Ana", "150.01", "150.01") to "Emergencias",
            Triple("Ana", "10", "10") to "CategoriaInexistente",
            Triple("Ana", "10", "10") to "Viajes"
        )

        invalidCases.forEach { (inputs, savingCat) ->
            val (person, lent, receive) = inputs
            val result = useCase.saveLoan(
                personNameInput = person,
                amountLentInput = lent,
                amountToReceiveInput = receive,
                savingCategoryInput = savingCat
            )
            assertTrue(
                "Expected Error for person='$person', lent='$lent', receive='$receive', savingCat='$savingCat' but got $result",
                result is LoanResult.Error
            )
        }

        // Null savingsRepository when savingCategory is requested
        val noSavingsUseCase = LoanUseCase(loanRepo, savingsRepository = null, expenseRepository = null)
        val noSavingsRes = noSavingsUseCase.saveLoan("Ana", "50", "50", savingCategoryInput = "Emergencias")
        assertTrue(noSavingsRes is LoanResult.Error)

        assertTrue("No invalid loan may ever be persisted", loanRepo.loansFlow.value.isEmpty())
    }

    /**
     * EXPOSES BUG (`LoanUseCase.saveLoan` uses `trim().isEmpty()` instead of `isBlank()`):
     * Java/Kotlin `String.trim()` only strips chars `<= '\u0020'`. Unicode whitespace such as
     * NBSP (`\u00A0`), Em-Space (`\u2003`), or Ideographic Space (`\u3000`) bypasses `trim().isEmpty()`
     * and persists a visually empty borrower name!
     */
    @Test
    fun saveLoan_unicodeWhitespacePersonName_isRejected() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val useCase = LoanUseCase(loanRepo)

        val unicodeBlanks = listOf("\u00A0", "\u00A0\u00A0\u00A0", "\u2003\u2002", "\u3000")
        unicodeBlanks.forEach { blankName ->
            val result = useCase.saveLoan(blankName, "100", "100")
            assertTrue(
                "Unicode-blank person name (${blankName.map { it.code }}) must be rejected",
                result is LoanResult.Error
            )
        }
        assertTrue(loanRepo.loansFlow.value.isEmpty())
    }

    /**
     * EXPOSES BUG (`parseAmountInput` delegates directly to `String.toDoubleOrNull()`):
     * JVM `Double.parseDouble` accepts Java literal suffixes (`"100d"`, `"100f"`) and hexadecimal
     * floating-point literals (`"0x1.0p10"` = 1024.0), which are NOT valid user currency amounts.
     */
    @Test
    fun saveLoan_rejectsJavaFloatSuffixesAndHexFloats() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val useCase = LoanUseCase(loanRepo)

        listOf("100d", "100f", "100D", "100F", "0x1.0p10").forEach { malformed ->
            val result = useCase.saveLoan("Carlos", malformed, malformed)
            assertTrue("Malformed numeric literal '$malformed' must be rejected", result is LoanResult.Error)
        }
        assertTrue(loanRepo.loansFlow.value.isEmpty())
    }

    /**
     * Invariant (IEEE-754 Penny Precision):
     * Lending 0.10 + 0.20 from 0.30 MUST succeed exactly (no `0.30000000000000004 > 0.30` false rejection),
     * and lending 0.01 afterwards MUST fail.
     */
    @Test
    fun saveLoan_exactBoundaryPennyPrecision_allowsExactBalanceAndRejectsOneCentOver() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        savingsRepo.insertSaving(Saving(amount = 0.30, category = "Emergencias", periodId = 1L))
        val useCase = LoanUseCase(loanRepo, savingsRepo)

        assertTrue(useCase.saveLoan("Ana", "0.10", "0.10", savingCategoryInput = "Emergencias") is LoanResult.Success)
        assertTrue(useCase.saveLoan("Luis", "0.20", "0.25", savingCategoryInput = "Emergencias") is LoanResult.Success)
        assertTrue(useCase.saveLoan("Eva", "0.01", "0.01", savingCategoryInput = "Emergencias") is LoanResult.Error)

        val remaining = calculateCategoryAvailableSavings("Emergencias", savingsRepo.savingsFlow.value, emptyList(), loanRepo.loansFlow.value)
        assertEquals(0, remaining.compareTo(BigDecimal.ZERO))
    }

    /**
     * EXPOSES BUG (`parseAmountInput` regex `^\d{1,3}(,\d{3})+$` misinterprets `"0,500"` as `500.0`!):
     * Leading zero `"0,500"` in European format represents `0.5`, never `500.0`.
     * Also verifies trimming/canonicalization of personName, note, and savingCategory.
     */
    @Test
    fun saveLoan_europeanCommaDecimalInputs_andCanonicalCategoryTrimming() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        savingsRepo.insertSaving(Saving(amount = 2000.0, category = "Emergencias", periodId = 1L))
        val useCase = LoanUseCase(loanRepo, savingsRepo)

        assertTrue(
            useCase.saveLoan(
                personNameInput = "  María  ",
                amountLentInput = "1.250,75",
                amountToReceiveInput = "1,300.25",
                noteInput = "  Boleto  ",
                savingCategoryInput = "  eMeRgEnCiAs  "
            ) is LoanResult.Success
        )
        val first = loanRepo.loansFlow.value.first()
        assertEquals("María", first.personName)
        assertEquals("Boleto", first.note)
        assertEquals("Emergencias", first.savingCategory)
        assertEquals(1250.75, first.amountLent, 1e-6)
        assertEquals(1300.25, first.amountToReceive, 1e-6)

        // Leading-zero comma decimal "0,500" must parse as 0.5 (not 500.0!)
        assertTrue(useCase.saveLoan("Pedro", "0,500", "1,00") is LoanResult.Success)
        val pedroLoan = loanRepo.loansFlow.value.first { it.personName == "Pedro" }
        assertEquals("Leading-zero '0,500' must parse as 0.5, not 500.0", 0.5, pedroLoan.amountLent, 1e-6)
        assertEquals(1.0, pedroLoan.amountToReceive, 1e-6)
    }

    // =========================================================================
    // 3. Cross-Module Integrity, Concurrency, Cancellation & Race Conditions
    // =========================================================================

    /**
     * Concurrency Invariant:
     * 12 parallel `LoanUseCase.saveLoan` calls of $150 each against $500 savings MUST serialize via
     * `FinancialAllocationLock.mutex`: exactly 3 succeed ($450), 9 fail, remaining == $50, never negative.
     */
    @Test
    fun concurrency_parallelLoansFromSameSaving_neverOverdrawAvailableBalance() = runBlocking {
        val loanRepo = AdversarialLoanRepo(yieldOnMutation = true)
        val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)
        savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))
        val sharedMutex = Mutex()
        val useCase = LoanUseCase(loanRepo, savingsRepo, loanMutex = sharedMutex)

        val results = (1..12).map { i ->
            async(Dispatchers.Default) {
                useCase.saveLoan("Deudor $i", "150", "160", savingCategoryInput = "Emergencias")
            }
        }.awaitAll()

        assertEquals(3, results.count { it is LoanResult.Success })
        assertEquals(9, results.count { it is LoanResult.Error })
        assertEquals(3, loanRepo.loansFlow.value.size)

        val remaining = calculateCategoryAvailableSavings(
            "Emergencias",
            savingsRepo.savingsFlow.value,
            emptyList(),
            loanRepo.loansFlow.value
        ).toDouble()
        assertEquals(50.0, remaining, 1e-6)
    }

    /**
     * Cross-Module Concurrency Invariant:
     * Parallel `LoanUseCase.saveLoan` ($100 x 6) and `ExpenseUseCase.saveExpense(SAVINGS)` ($100 x 6)
     * competing for the same $500 saving MUST share `FinancialAllocationLock.mutex` so combined
     * outflows never exceed $500.
     */
    @Test
    fun concurrency_parallelLoansAndSavingsExpenses_shareFinancialAllocationLockWithoutOverdraw() = runBlocking {
        val loanRepo = AdversarialLoanRepo(yieldOnMutation = true)
        val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)
        val expenseRepo = AdversarialExpenseRepo(yieldOnMutation = true)
        savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))

        val sharedMutex = Mutex()
        val loanUseCase = LoanUseCase(loanRepo, savingsRepo, expenseRepo, loanMutex = sharedMutex)
        val expenseUseCase = ExpenseUseCase(expenseRepo, null, savingsRepo, loanRepo, expenseMutex = sharedMutex)

        val loanJobs = (1..6).map { i ->
            async(Dispatchers.Default) {
                loanUseCase.saveLoan("L$i", "100", "110", savingCategoryInput = "Emergencias")
            }
        }
        val expenseJobs = (1..6).map { i ->
            async(Dispatchers.Default) {
                expenseUseCase.saveExpense(
                    amountInput = "100",
                    dateInput = "2026-11-15",
                    descriptionInput = "E$i",
                    categoryInput = "Hogar",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategoryInput = "Emergencias"
                )
            }
        }

        val loanSuccesses = loanJobs.awaitAll().count { it is LoanResult.Success }
        val expenseSuccesses = expenseJobs.awaitAll().count { it is ExpenseResult.Success }

        assertEquals("Combined loans + savings expenses must equal exactly 5 ($500)", 5, loanSuccesses + expenseSuccesses)
        val outflows = calculateCategorySavingsOutflows("Emergencias", expenseRepo.expensesFlow.value, loanRepo.loansFlow.value)
        assertEquals(0, outflows.compareTo(BigDecimal("500.0")))
        val remaining = calculateCategoryAvailableSavings(
            "Emergencias",
            savingsRepo.savingsFlow.value,
            expenseRepo.expensesFlow.value,
            loanRepo.loansFlow.value
        )
        assertEquals(0, remaining.compareTo(BigDecimal.ZERO))
    }

    /**
     * Cross-Module Race Invariant:
     * Concurrent `LoanUseCase.saveLoan` ($400) racing against `SavingsUseCase.deleteSaving` and
     * `SavingsUseCase.updateSaving` ($200) on a $500 saving MUST preserve `totalCommitted <= totalSaved`.
     */
    @Test
    fun concurrency_parallelLoanCreationAndSavingDeletionOrReduction_preservesAllocationInvariant() = runBlocking {
        val loanRepo = AdversarialLoanRepo(yieldOnMutation = true)
        val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)
        val expenseRepo = AdversarialExpenseRepo(yieldOnMutation = true)
        val incomeRepo = AdversarialIncomeRepo()
        val savingId = savingsRepo.insertSaving(
            Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )

        val sharedMutex = Mutex()
        val loanUseCase = LoanUseCase(loanRepo, savingsRepo, expenseRepo, loanMutex = sharedMutex)
        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, loanRepo, savingMutex = sharedMutex)

        val j1 = async(Dispatchers.Default) {
            loanUseCase.saveLoan("Carlos", "400", "450", savingCategoryInput = "Emergencias")
        }
        val j2 = async(Dispatchers.Default) {
            savingsUseCase.deleteSaving(savingId)
        }
        val j3 = async(Dispatchers.Default) {
            savingsUseCase.updateSaving(
                id = savingId,
                amountInput = "200",
                categoryInput = "Emergencias",
                periodId = 1L,
                incomeId = 1L,
                dateTimeInput = "2026-11-05 10:00"
            )
        }
        j1.await(); j2.await(); j3.await()

        val totalSaved = savingsRepo.savingsFlow.value
            .filter { it.category.equals("Emergencias", ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, s -> acc.add(BigDecimal.valueOf(s.amount)) }
        val totalCommitted = calculateCategorySavingsOutflows("Emergencias", expenseRepo.expensesFlow.value, loanRepo.loansFlow.value)

        assertTrue(
            "Conservation invariant violated: committed=$totalCommitted > saved=$totalSaved",
            totalCommitted <= totalSaved
        )
    }

    /**
     * Coroutine Cancellation Invariant:
     * Cancelling a coroutine while it holds `loanMutex` inside `LoanUseCase.saveLoan` MUST release
     * the mutex cleanly (no deadlock) so subsequent calls succeed.
     */
    @Test
    fun concurrency_cancelledLoanCoroutine_releasesMutexWithoutDeadlock() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))
        val localMutex = Mutex()
        val useCase = LoanUseCase(loanRepo, savingsRepo, loanMutex = localMutex)

        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        loanRepo.onInsertStarted = started
        loanRepo.insertGate = gate

        var caughtCancellation = false
        val job = launch(Dispatchers.Default) {
            try {
                useCase.saveLoan("Cancelado", "100", "100", savingCategoryInput = "Emergencias")
            } catch (_: CancellationException) {
                caughtCancellation = true
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertTrue("CancellationException must propagate cleanly", caughtCancellation)
        assertFalse("Mutex must be released after cancellation", localMutex.isLocked)

        // Subsequent saveLoan must proceed without deadlocking
        loanRepo.onInsertStarted = null
        loanRepo.insertGate = null
        val followUp = useCase.saveLoan("Siguiente", "100", "100", savingCategoryInput = "Emergencias")
        assertTrue(followUp is LoanResult.Success)
        assertEquals(1, loanRepo.loansFlow.value.size)
    }

    /**
     * Cross-Module Lifecycle Invariant:
     * Combined commitments (Expense + Loan = $400 out of $500) protect the saving against
     * under-sizing, category change, and deletion; editing/deleting the expense updates available
     * balance accurately for future loans.
     */
    @Test
    fun crossModule_loanPlusExpenseCombinedCommitment_blocksSavingUpdateAndDeletionAndExpenseEdit() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val savingId = savingsRepo.insertSaving(
            Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )

        val sharedMutex = Mutex()
        val loanUseCase = LoanUseCase(loanRepo, savingsRepo, expenseRepo, loanMutex = sharedMutex)
        val expenseUseCase = ExpenseUseCase(expenseRepo, null, savingsRepo, loanRepo, expenseMutex = sharedMutex)
        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, loanRepo, savingMutex = sharedMutex)

        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "200",
                dateInput = "2026-11-10",
                descriptionInput = "Doctor",
                categoryInput = "Salud",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            ) is ExpenseResult.Success
        )
        val expenseId = expenseRepo.expensesFlow.value.single().id

        assertTrue(loanUseCase.saveLoan("Ana", "200", "220", savingCategoryInput = "Emergencias") is LoanResult.Success)

        // Saving cannot be reduced below 400 or moved to another category or deleted
        assertTrue(
            savingsUseCase.updateSaving(savingId, "399.99", "Emergencias", periodId = 1L, incomeId = 1L, dateTimeInput = "2026-11-05 10:00")
                is SavingsResult.Error
        )
        assertTrue(
            savingsUseCase.updateSaving(savingId, "500", "Viajes", periodId = 1L, incomeId = 1L, dateTimeInput = "2026-11-05 10:00")
                is SavingsResult.Error
        )
        assertTrue(savingsUseCase.deleteSaving(savingId) is SavingsResult.Error)

        // Exact floor (400) IS allowed
        assertTrue(
            savingsUseCase.updateSaving(savingId, "400", "Emergencias", periodId = 1L, incomeId = 1L, dateTimeInput = "2026-11-05 10:00")
                is SavingsResult.Success
        )

        // Updating expense from 200 -> 201 exceeds remaining (400 - 200 loan = 200)
        assertTrue(
            expenseUseCase.updateExpense(
                id = expenseId,
                amountInput = "201",
                dateInput = "2026-11-10",
                descriptionInput = "Doctor",
                categoryInput = "Salud",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            ) is ExpenseResult.Error
        )

        // Deleting the $200 expense frees $200 so another $200 loan succeeds
        expenseUseCase.deleteExpense(expenseId)
        assertTrue(loanUseCase.saveLoan("Luis", "200", "200", savingCategoryInput = "Emergencias") is LoanResult.Success)
        assertTrue(loanUseCase.saveLoan("Eva", "0.01", "0.01", savingCategoryInput = "Emergencias") is LoanResult.Error)
    }

    // =========================================================================
    // 4. Room SQLite Persistence, SQL Injection, Unicode & Reassignment Attacks
    // =========================================================================

    /**
     * Room Persistence Invariant:
     * `LoansDatabase` + `RoomLoanRepository` preserves exact ordering (`ORDER BY id DESC`),
     * `NULL` <-> `""` roundtrip for personal loans, SQL injection payloads, RTL Arabic,
     * emojis, and 2,000-character strings without truncation or SQL error.
     */
    @Test
    fun room_roundTrip_preservesOrderUnicodeSqlInjectionAndNullSavingCategory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomLoanRepository(db.loanDao())
            val longNote = "Nota larga ".repeat(200)
            val sqlInjectionName = "Robert'); DROP TABLE loans;--"

            val id1 = repo.insertLoan(
                Loan(personName = sqlInjectionName, amountLent = 100.5, amountToReceive = 120.75, note = longNote, savingCategory = "")
            )
            val id2 = repo.insertLoan(
                Loan(personName = "أحمد 🚀", amountLent = 500.0, amountToReceive = 550.0, note = "日本語テスト", savingCategory = "Emergencias")
            )

            val stored = repo.getAllLoans().first()
            assertEquals(2, stored.size)
            // ORDER BY id DESC
            assertEquals(id2, stored[0].id)
            assertEquals("أحمد 🚀", stored[0].personName)
            assertEquals("日本語テスト", stored[0].note)
            assertEquals("Emergencias", stored[0].savingCategory)

            assertEquals(id1, stored[1].id)
            assertEquals(sqlInjectionName, stored[1].personName)
            assertEquals(longNote, stored[1].note)
            assertEquals("", stored[1].savingCategory)
        } finally {
            db.close()
        }
    }

    /**
     * EXPOSES BUG (`LoanDao.reassignSavingCategory` SQL uses `COALESCE(savingCategory, '')`):
     * When `oldCategoryName` is blank (`""` or `"   "`), `LOWER(TRIM(COALESCE(NULL, ''))) = ''`
     * matches EVERY personal loan (`savingCategory IS NULL`) and hijacks them into `newCategoryName`!
     */
    @Test
    fun room_reassignSavingCategory_blankOldCategory_mustNotOverwritePersonalLoans() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomLoanRepository(db.loanDao())
            repo.insertLoan(Loan(personName = "Personal", amountLent = 300.0, amountToReceive = 300.0, savingCategory = ""))
            repo.insertLoan(Loan(personName = "Ahorro", amountLent = 200.0, amountToReceive = 200.0, savingCategory = "Viajes"))

            repo.reassignSavingCategory(oldName = "   ", newName = "Secuestrado")

            val loans = repo.getAllLoans().first()
            val personal = loans.single { it.personName == "Personal" }
            assertEquals(
                "Blank oldCategoryName must NEVER overwrite personal loans (NULL savingCategory)",
                "",
                personal.savingCategory
            )
            assertEquals("Viajes", loans.single { it.personName == "Ahorro" }.savingCategory)
        } finally {
            db.close()
        }
    }

    /**
     * EXPOSES BUG (`LoanDao.reassignSavingCategory` relies on SQLite ASCII-only `LOWER()`):
     * SQLite's built-in `LOWER()` does NOT fold non-ASCII Unicode letters like `Ó`/`ó`, `Á`/`á`, `Ñ`/`ñ`.
     * A loan stored with `savingCategory = "EDUCACIÓN"` is NOT updated when renaming `"Educación"` -> `"Universidad"`,
     * detaching the loan from its renamed saving category and silently inflating available savings!
     */
    @Test
    fun room_reassignSavingCategory_accentedCaseInsensitiveCategory_updatesLinkedLoans() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomLoanRepository(db.loanDao())
            repo.insertLoan(
                Loan(personName = "Sofía", amountLent = 400.0, amountToReceive = 450.0, savingCategory = "EDUCACIÓN")
            )

            repo.reassignSavingCategory(oldName = "Educación", newName = "Universidad")

            val updated = repo.getAllLoans().first().single()
            assertEquals(
                "Case-insensitive rename must match accented Unicode category names in SQLite",
                "Universidad",
                updated.savingCategory
            )
        } finally {
            db.close()
        }
    }

    // =========================================================================
    // 5. ViewModel State Machine, Error Recovery, Idempotency & Reactivity
    // =========================================================================

    /**
     * Idempotency Invariant:
     * Rapid triple-tap on `LoansViewModel.saveLoan()` while the first save is suspended MUST
     * execute only ONE repository insertion and reset `isSaving` so subsequent saves work.
     */
    @Test
    fun loansViewModel_doubleTapSaveLoan_isIdempotentAndInsertsOnlyOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val gate = CompletableDeferred<Unit>()
            loanRepo.insertGate = gate
            val viewModel = LoansViewModel(LoanUseCase(loanRepo))
            advanceUntilIdle()

            viewModel.onPersonNameChange("Pedro")
            viewModel.onAmountLentChange("250")

            // Rapid triple tap while first insert is suspended
            viewModel.saveLoan()
            viewModel.saveLoan()
            viewModel.saveLoan()
            testScheduler.runCurrent()

            assertEquals("Only 1 insert call may be in flight", 1, loanRepo.insertCallCount)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, loanRepo.loansFlow.value.size)
            assertEquals(LoansViewModel.MSG_LOAN_SAVED, viewModel.uiState.value.toastMessage)

            // Subsequent distinct loan succeeds after isSaving resets
            loanRepo.insertGate = null
            viewModel.onPersonNameChange("Lucía")
            viewModel.onAmountLentChange("100")
            viewModel.saveLoan()
            advanceUntilIdle()

            assertEquals(2, loanRepo.loansFlow.value.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.saveLoan()` lacks `catch` around `loanUseCase.saveLoan`):
     * If Room/storage throws an unexpected exception (e.g., disk full / SQLite failure), the
     * uncaught exception propagates out of `viewModelScope.launch` and crashes the app instead of
     * surfacing `errorMessage`, preserving user inputs, and allowing retry!
     */
    @Test
    fun loansViewModel_repositoryThrowsException_setsErrorAndRecoversWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            loanRepo.throwOnInsert = IllegalStateException("SQLite disk full")
            val viewModel = LoansViewModel(LoanUseCase(loanRepo))
            advanceUntilIdle()

            viewModel.onPersonNameChange("Elena")
            viewModel.onAmountLentChange("300")
            viewModel.saveLoan()
            advanceUntilIdle()

            assertNotNull("Storage failure must surface an errorMessage instead of crashing", viewModel.uiState.value.errorMessage)
            assertEquals("User inputs must be preserved on storage failure", "Elena", viewModel.uiState.value.personNameInput)
            assertTrue(loanRepo.loansFlow.value.isEmpty())

            // Recovery on retry once DB recovers
            loanRepo.throwOnInsert = null
            viewModel.saveLoan()
            advanceUntilIdle()

            assertEquals(1, loanRepo.loansFlow.value.size)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * State Machine Invariant:
     * `amountToReceiveInput` tracks `amountLentInput` until manually edited; `resetForm()` and
     * successful `saveLoan()` both re-enable auto-sync.
     */
    @Test
    fun loansViewModel_amountToReceiveAutoSync_andResetFormReEnablesAutoSync() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = LoansViewModel(LoanUseCase(AdversarialLoanRepo()))
            advanceUntilIdle()

            viewModel.onAmountLentChange("100")
            assertEquals("100", viewModel.uiState.value.amountToReceiveInput)

            viewModel.onAmountLentChange("250.50")
            assertEquals("250.50", viewModel.uiState.value.amountToReceiveInput)

            // Manual edit decouples auto-sync (even if user clears it)
            viewModel.onAmountToReceiveChange("")
            viewModel.onAmountLentChange("300")
            assertEquals("", viewModel.uiState.value.amountToReceiveInput)

            // resetForm re-enables auto-sync
            viewModel.resetForm()
            viewModel.onAmountLentChange("420")
            assertEquals("420", viewModel.uiState.value.amountToReceiveInput)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.init` silently clears `selectedSavingCategory` to `""`):
     * When the user selects `"Emergencias"` as the funding source and that category is exhausted
     * (or renamed) before the user taps Save, `LoansViewModel` silently resets `selectedSavingCategory`
     * to `""`. Because `""` means "personal loan", tapping Save then silently creates an UNINTENDED
     * personal loan instead of rejecting with an insufficient-balance error!
     */
    @Test
    fun loansViewModel_selectedSavingCategoryExhausted_doesNotSilentlyConvertToPersonalLoan() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val expenseRepo = AdversarialExpenseRepo()
            savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))

            val viewModel = LoansViewModel(LoanUseCase(loanRepo, savingsRepo, expenseRepo))
            advanceUntilIdle()

            // User fills form choosing Emergencias as funding source
            viewModel.onPersonNameChange("Carlos")
            viewModel.onAmountLentChange("300")
            viewModel.onSavingCategorySelected("Emergencias")
            assertEquals("Emergencias", viewModel.uiState.value.selectedSavingCategory)

            // Meanwhile, an expense drains Emergencias completely
            expenseRepo.insertExpense(
                Expense(
                    amount = 500.0,
                    date = "2026-11-15",
                    description = "Cirugía",
                    category = "Salud",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            )
            advanceUntilIdle()

            // User taps Save -> MUST NOT silently save a personal loan!
            viewModel.saveLoan()
            advanceUntilIdle()

            assertTrue(
                "Drained saving category must NOT silently convert user's savings loan into a personal loan",
                loanRepo.loansFlow.value.isEmpty()
            )
            assertNotNull(
                "User must receive an error when selected saving category no longer has balance",
                viewModel.uiState.value.errorMessage
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`SavingsViewModel.confirmDeleteSaving()` swallows `SavingsResult.Error`):
     * When deleting a saving is blocked because it backs an active loan, `SavingsUseCase.deleteSaving`
     * returns `SavingsResult.Error(...)`, but `SavingsViewModel` discards the error message and leaves
     * `savingErrorMessage == null`, giving the user zero explanation!
     */
    @Test
    fun savingsViewModel_confirmDeleteSavingBlockedByLoan_surfacesErrorMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val expenseRepo = AdversarialExpenseRepo()
            val incomeRepo = AdversarialIncomeRepo()

            val savingId = savingsRepo.insertSaving(
                Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
            )
            loanRepo.insertLoan(
                Loan(personName = "Ana", amountLent = 300.0, amountToReceive = 300.0, savingCategory = "Emergencias")
            )

            val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, loanRepo)
            val savingsViewModel = SavingsViewModel(savingsUseCase)
            advanceUntilIdle()

            val saving = savingsRepo.getSavingById(savingId)!!
            savingsViewModel.requestDeleteSaving(saving)
            savingsViewModel.confirmDeleteSaving()
            advanceUntilIdle()

            assertEquals("Protected saving must remain persisted", 1, savingsRepo.savingsFlow.value.size)
            assertNull(savingsViewModel.uiState.value.savingToDelete)
            assertNotNull(
                "Blocked saving deletion must expose error message in SavingsUiState.savingErrorMessage",
                savingsViewModel.uiState.value.savingErrorMessage
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Cross-ViewModel Reactivity & Stream Error Resilience Invariant:
     * - Inserting a loan updates `ExpenseViewModel.uiState` (`savingsBalances`, `editSavingsBalances`,
     *   `hasAvailableSavings`, `fundingSourceInput`) and `SavingsViewModel.uiState` (`netSavingsSummary`).
     * - Upstream errors from `LoanRepository.getAllLoans()` are caught cleanly without crashing any ViewModel.
     */
    @Test
    fun expenseAndSavingsViewModels_reactToLoanInsertionAndRepositoryErrors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val expenseRepo = AdversarialExpenseRepo()
            val incomeRepo = AdversarialIncomeRepo()
            savingsRepo.insertSaving(Saving(amount = 500.0, category = "Emergencias", periodId = 1L))
            expenseRepo.insertExpense(
                Expense(
                    amount = 100.0,
                    date = "2026-11-10",
                    description = "Test",
                    category = "Salud",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            )

            val expenseVm = ExpenseViewModel(ExpenseUseCase(expenseRepo, null, savingsRepo, loanRepo))
            val savingsVm = SavingsViewModel(SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, loanRepo))
            advanceUntilIdle()

            assertTrue(expenseVm.uiState.value.hasAvailableSavings)
            assertEquals(400.0, expenseVm.uiState.value.savingsBalances.single().netBalance, 1e-6)
            assertEquals(400.0, savingsVm.uiState.value.netSavingsSummary.totalAmount, 1e-6)

            // Begin editing the $100 expense: editSavingsBalances excludes its own $100 -> $500 available
            expenseVm.startEditing(expenseRepo.expensesFlow.value.single())
            assertEquals(500.0, expenseVm.uiState.value.editSavingsBalances.single().netBalance, 1e-6)

            // Now a $400 loan drains remaining savings completely
            loanRepo.insertLoan(
                Loan(personName = "Jorge", amountLent = 400.0, amountToReceive = 450.0, savingCategory = "Emergencias")
            )
            advanceUntilIdle()

            assertFalse("No available savings remain after 100 expense + 400 loan", expenseVm.uiState.value.hasAvailableSavings)
            assertEquals(0.0, expenseVm.uiState.value.savingsBalances.single().netBalance, 1e-6)
            assertEquals(ExpenseFundingSource.BUDGET, expenseVm.uiState.value.fundingSourceInput)
            // While editing the $100 expense, edit balance must reflect 500 - 400 loan = 100
            assertEquals(100.0, expenseVm.uiState.value.editSavingsBalances.single().netBalance, 1e-6)
            // Net savings summary drops exhausted category -> empty
            assertTrue(savingsVm.uiState.value.netSavingsSummary.isEmpty)

            // Stream error resilience across all 3 ViewModels
            val failingLoanRepo = AdversarialLoanRepo().apply { throwOnRead = RuntimeException("Room I/O error") }
            val resilientLoansVm = LoansViewModel(LoanUseCase(failingLoanRepo, savingsRepo, expenseRepo))
            val resilientExpenseVm = ExpenseViewModel(ExpenseUseCase(expenseRepo, null, savingsRepo, failingLoanRepo))
            val resilientSavingsVm = SavingsViewModel(SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, failingLoanRepo))
            advanceUntilIdle()

            assertTrue(resilientLoansVm.uiState.value.loans.isEmpty())
            assertEquals(0.0, resilientLoansVm.uiState.value.totalReceivable, 1e-6)
            assertEquals(400.0, resilientExpenseVm.uiState.value.savingsBalances.single().netBalance, 1e-6)
            assertEquals(400.0, resilientSavingsVm.uiState.value.netSavingsSummary.totalAmount, 1e-6)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // 6. UI Pure Helpers, Localization Parity & Compose Edge Cases
    // =========================================================================

    /**
     * EXPOSES BUG (`AddLoanDialog.kt` `filterDecimalInput()`):
     * Line `for (char in raw.replace(",", ""))` strips ALL commas before the loop, making
     * `else if ((char == '.' || char == ',') && allowsDecimals && !hasDot)` dead code!
     * Consequently, Spanish/German/Portuguese users typing `"12,50"` silently get `"1250"` (100x inflation!).
     * Also tests property invariants across 200 fuzzed strings including non-ASCII digits.
     */
    @Test
    fun addLoanDialog_filterDecimalInput_preservesCommaAsDecimalSeparatorAndEnforcesFormatProperty() {
        // Explicit comma-decimal regressions (Spanish/German/Portuguese decimal keyboards)
        assertEquals("Comma decimal '12,50' must normalize to '12.50', not '1250'", "12.50", filterDecimalInput("12,50", allowsDecimals = true, maxDecimals = 2))
        assertEquals("Comma decimal '0,75' must normalize to '0.75', not '075'", "0.75", filterDecimalInput("0,75", allowsDecimals = true, maxDecimals = 2))
        assertEquals("Trailing comma '100,' must normalize to '100.'", "100.", filterDecimalInput("100,", allowsDecimals = true, maxDecimals = 2))
        assertEquals("Extra decimals truncated", "12.34", filterDecimalInput("12,3456", allowsDecimals = true, maxDecimals = 2))
        assertEquals("Multiple dots collapse to first", "12.34", filterDecimalInput("12.3.4.5", allowsDecimals = true, maxDecimals = 2))
        // Non-ASCII digits (e.g., Arabic-Indic '١٢٣') must NOT pass through as unparseable chars
        assertEquals("Non-ASCII digits must be filtered out", "", filterDecimalInput("١٢٣", allowsDecimals = true, maxDecimals = 2))

        // Property-based check over 200 fuzzed inputs
        val rng = Random(445566L)
        val alphabet = "0123456789.,-+abc$€ \t\n١٢٣"
        repeat(200) {
            val raw = buildString {
                repeat(rng.nextInt(0, 25)) { append(alphabet[rng.nextInt(alphabet.length)]) }
            }
            val allowsDecimals = rng.nextBoolean()
            val maxDecimals = if (allowsDecimals) rng.nextInt(1, 5) else 0
            val out = filterDecimalInput(raw, allowsDecimals, maxDecimals)

            assertTrue("Output '$out' must contain only ASCII digits and optional '.'", out.all { it in '0'..'9' || it == '.' })
            assertTrue("Output '$out' must have at most one '.'", out.count { it == '.' } <= 1)
            if (!allowsDecimals) {
                assertFalse("Output '$out' must not contain '.' when decimals disallowed", out.contains('.'))
            } else if (out.contains('.')) {
                assertTrue(
                    "Decimal digits in '$out' must not exceed $maxDecimals",
                    out.substringAfter('.').length <= maxDecimals
                )
            }
        }
    }

    /**
     * EXPOSES BUG (`LoanCardItem.kt` `buildPersonInitials()` splits UTF-16 surrogate pairs!):
     * `"😀 Carlos"` -> `parts[0].first()` takes only the high surrogate `'\uD83D'`, returning
     * corrupted UTF-16 `"\uD83DC"`. Same for `"😀 😎"` (`"\uD83D\uD83D"`) and `"A😀"` (`"A\uD83D"`).
     * Property: For ANY Unicode input, `buildPersonInitials` must return a non-empty string with
     * ZERO unpaired UTF-16 surrogates.
     */
    @Test
    fun property_buildPersonInitials_neverProducesUnpairedUtf16SurrogatesOrCrashes() {
        val edgeCases = listOf(
            "", "   ", "\t\n", "a", "María", "María Castillo", "   Juan   Carlos   Pérez  ",
            "😀 Carlos", "😀 😎", "A😀", "🚀", "👨‍👩‍👧‍👦 Familia", "أحمد محمد", "東京 太郎",
            "polygenelubricants"
        )
        edgeCases.forEach { name ->
            val initials = buildPersonInitials(name)
            assertTrue("Initials must not be empty for '$name'", initials.isNotEmpty())
            assertFalse(
                "Initials for '$name' contain unpaired UTF-16 surrogate: ${initials.map { it.code }}",
                hasUnpairedUtf16Surrogate(initials)
            )
        }

        val rng = Random(998877L)
        val tokens = listOf("Ana", "José", "😀", "🚀", "A😀", "Ü", "中", " ", "   ", "")
        repeat(150) {
            val name = (1..rng.nextInt(1, 6)).joinToString("") { tokens[rng.nextInt(tokens.size)] }
            val initials = buildPersonInitials(name)
            assertTrue(initials.isNotEmpty())
            assertFalse(
                "Fuzzed name '$name' produced unpaired UTF-16 surrogate",
                hasUnpairedUtf16Surrogate(initials)
            )
        }
    }

    /**
     * Localization Parity Invariant:
     * Every new translation key in `navigation.tab_loans` and `loans.*` MUST resolve to a non-blank
     * translated string (never falling back to the raw key) in all 4 supported languages (`es`, `en`, `de`, `pt`).
     */
    @Test
    fun i18n_allLoanAndNavigationKeys_existAndAreTranslatedInAllFourLanguages() {
        val requiredKeys = listOf(
            "navigation.tab_loans",
            "loans.header.title",
            "loans.summary.total_receivable",
            "loans.summary.paid",
            "loans.section.pending_title",
            "loans.section.count_singular",
            "loans.section.count_plural",
            "loans.empty.message",
            "loans.actions.register_loan",
            "loans.card.personal_loan",
            "loans.card.amount_lent",
            "loans.card.paid",
            "loans.card.pending",
            "loans.add_dialog.title",
            "loans.add_dialog.person_label",
            "loans.add_dialog.person_placeholder",
            "loans.add_dialog.amount_lent_label",
            "loans.add_dialog.amount_to_receive_label",
            "loans.add_dialog.saving_source_label",
            "loans.add_dialog.saving_none",
            "loans.add_dialog.note_label",
            "loans.add_dialog.note_placeholder",
            "loans.add_dialog.save_button",
            "loans.messages.saved"
        )

        listOf("es", "en", "de", "pt").forEach { lang ->
            i18nManager.setLanguage(lang)
            requiredKeys.forEach { key ->
                val translated = i18nManager.t(key)
                assertTrue("[$lang] Key '$key' must not be blank", translated.isNotBlank())
                assertNotEquals("[$lang] Key '$key' fell back to raw key string", key, translated)
            }
        }
    }

    /**
     * Compose UI Edge Cases:
     * - Zero-decimal currency formatting (`JPY`: `¥ 1,500`) across summary and cards.
     * - Subtitle fallback precedence: `note` > `savingCategory` > `"Préstamo personal"`.
     * - Extreme borrower names: 500-char string and `"polygenelubricants"` (`hashCode() == Int.MIN_VALUE`)
     *   render cleanly without `ArrayIndexOutOfBoundsException` or layout crash.
     * - Closing dialog resets typed inputs and errors.
     */
    @Test
    fun compose_loansView_extremeInputsZeroDecimalCurrencyAndSubtitleFallbackHierarchy() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        savingsRepo.insertSaving(Saving(amount = 5000.0, category = "Viajes", periodId = 1L))

        // 1. Note overrides savingCategory
        loanRepo.insertLoan(
            Loan(personName = "polygenelubricants", amountLent = 1000.0, amountToReceive = 1200.0, note = "Nota prioritaria", savingCategory = "Viajes")
        )
        // 2. Blank note falls back to savingCategory
        loanRepo.insertLoan(
            Loan(personName = "María " + "X".repeat(300), amountLent = 500.0, amountToReceive = 500.0, note = "   ", savingCategory = "Viajes")
        )
        // 3. Blank note & blank savingCategory falls back to personal_loan
        loanRepo.insertLoan(
            Loan(personName = "Luis", amountLent = 300.0, amountToReceive = 300.0, note = "", savingCategory = "")
        )

        val viewModel = LoansViewModel(LoanUseCase(loanRepo, savingsRepo))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = jpy)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_loans_total_receivable").assertTextContains("¥ 2,000")
        composeTestRule.onNodeWithTag("text_loans_total_paid").assertTextContains("¥ 0")
        composeTestRule.onNodeWithTag("text_loans_count").assertTextContains("3 préstamos")

        composeTestRule.onNodeWithText("Nota prioritaria").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Viajes").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Préstamo personal").performScrollTo().assertIsDisplayed()

        // Open dialog, type invalid data, trigger error, close dialog -> reopen must be clean
        composeTestRule.onNodeWithTag("btn_add_loan_top").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Temporal")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_loan_dialog_error").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_close_loan_dialog").performClick()
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()

        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("text_loan_dialog_error").assertDoesNotExist()
        assertEquals("", viewModel.uiState.value.personNameInput)
    }

    /**
     * Navigation & Period-Lock Invariant (`MainContainer` + `FloatingBottomNavigationBar`):
     * - `requestedTab = AppTab.LOANS` lands directly on Loans even when `isPeriodLocked == true`
     *   (expired period) and invokes `onRequestedTabConsumed()`.
     * - Clicking locked `tab_incomes` while on `LOANS` keeps user on `LOANS` (does NOT unlock or switch).
     * - Switching `LOANS` -> `SETTINGS` -> `PERIOD` -> `LOANS` remains fully responsive.
     */
    @Test
    fun compose_mainContainer_loansTabAccessibleWhenPeriodExpiredAndRequestedTabConsumed() {
        val expiredPeriod = FinancialPeriod(id = 1L, startDate = "2026-10-01", endDate = "2026-10-31")
        val periodViewModel = PeriodViewModel(
            PeriodUseCase(FakeFinancialPeriodRepository(initialPeriod = expiredPeriod), currentDateProvider = { "2026-11-26" })
        )
        val settingsViewModel = SettingsViewModel(FakeLanguagePreferenceRepository("es"), i18nManager)
        var consumedCount = 0

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsViewModel,
                periodViewModel = periodViewModel,
                i18nManager = i18nManager,
                requestedTab = AppTab.LOANS,
                onRequestedTabConsumed = { consumedCount++ },
                incomesContent = { Text("Contabilidad Protegida") },
                loansContent = { Text("Pantalla Préstamos Activa") }
            )
        }
        composeTestRule.waitForIdle()

        assertEquals("requestedTab = LOANS must be consumed once", 1, consumedCount)
        composeTestRule.onNodeWithText("Pantalla Préstamos Activa").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_loans").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_incomes").assertIsNotSelected()

        // Clicking locked Contabilidad keeps Loans active
        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.onNodeWithText("Pantalla Préstamos Activa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contabilidad Protegida").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tab_loans").assertIsSelected()

        // Navigate across all unlocked tabs and return to Loans
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_period").performClick()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_loans").performClick()
        composeTestRule.onNodeWithText("Pantalla Préstamos Activa").assertIsDisplayed()
    }
}
