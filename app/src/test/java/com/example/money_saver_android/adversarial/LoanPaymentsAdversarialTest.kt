package com.example.money_saver_android.adversarial

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.PartialPaymentScheduleOption
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.buildSavingsNetSummary
import com.example.money_saver_android.domain.usecase.calculateCategoryAvailableSavings
import com.example.money_saver_android.domain.usecase.calculateCategorySavingsInflows
import com.example.money_saver_android.domain.usecase.calculateCategorySavingsOutflows
import com.example.money_saver_android.domain.usecase.calculateLoanDueBadgeInfo
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.domain.usecase.formatLoanPaymentDateTime
import com.example.money_saver_android.domain.usecase.previewLoanInstallmentsAfterPayment
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.LoansView
import com.example.money_saver_android.ui.components.loans.LoanPaymentHistorySheet
import com.example.money_saver_android.viewmodel.LoanCreationPaymentMode
import com.example.money_saver_android.viewmodel.LoansViewModel
import com.example.money_saver_android.viewmodel.PartialScheduleAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.io.File
import java.math.BigDecimal
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Loan Payments, Payment History,
 * Partial Schedule Rebalancing, Loan/Payment Edit & Delete, Room Migrations 1->2->3->4,
 * Cross-Module Savings Integrity, ViewModel Concurrency, Compose UI & i18n Parity.
 *
 * Strictly test-only file: production code is NEVER modified.
 * Several tests intentionally assert strict domain/UI/migration invariants that expose
 * confirmed defects in the current implementation (clearly marked with `EXPOSES BUG`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LoanPaymentsAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var i18nManager: I18nManager
    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$", formatPattern = "#,##0.00")

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

    private class AdversarialLoanRepo : LoanRepository {
        val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
        private var nextLoanId = 1L
        private var nextInstallmentId = 1L
        private var nextPaymentId = 1L

        var recordPaymentCallCount = 0
            private set
        var deletePaymentCallCount = 0
            private set
        var replaceInstallmentsCallCount = 0
            private set

        var recordPaymentStarted: CompletableDeferred<Unit>? = null
        var recordPaymentGate: CompletableDeferred<Unit>? = null
        var deletePaymentGate: CompletableDeferred<Unit>? = null
        var throwOnRecordPayment: Throwable? = null
        var throwOnDeleteLoan: Throwable? = null

        override fun getAllLoans(): Flow<List<Loan>> = loansFlow

        override suspend fun insertLoan(loan: Loan): Long {
            val id = if (loan.id > 0L) loan.id else nextLoanId++
            val savedInstallments = loan.installments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = id)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            val savedPayments = loan.payments
                .map { p ->
                    val pId = if (p.id > 0L) p.id else nextPaymentId++
                    p.copy(id = pId, loanId = id)
                }
                .sortedWith(compareBy({ it.paidAt }, { it.id }))
            loansFlow.value = listOf(loan.copy(id = id, installments = savedInstallments, payments = savedPayments)) + loansFlow.value
            return id
        }

        override suspend fun updateLoan(loan: Loan) {
            val savedInstallments = loan.installments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = loan.id)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = loansFlow.value.map { existing ->
                if (existing.id == loan.id) {
                    loan.copy(installments = savedInstallments, payments = existing.payments)
                } else {
                    existing
                }
            }
        }

        override suspend fun deleteLoan(loanId: Long) {
            throwOnDeleteLoan?.let { throw it }
            loansFlow.value = loansFlow.value.filterNot { it.id == loanId }
        }

        override suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {
            replaceInstallmentsCallCount++
            val savedInstallments = installments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = loanId)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = loansFlow.value.map { existing ->
                if (existing.id == loanId) existing.copy(installments = savedInstallments) else existing
            }
        }

        override suspend fun recordPayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
            recordPaymentCallCount++
            recordPaymentStarted?.complete(Unit)
            recordPaymentGate?.await()
            throwOnRecordPayment?.let { throw it }
            val savedInstallments = updatedInstallments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = payment.loanId)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            val savedPayment = payment.copy(id = nextPaymentId++)
            loansFlow.value = loansFlow.value.map { existing ->
                if (existing.id == payment.loanId) {
                    existing.copy(
                        installments = savedInstallments,
                        payments = (existing.payments + savedPayment).sortedWith(compareBy({ it.paidAt }, { it.id }))
                    )
                } else {
                    existing
                }
            }
        }

        override suspend fun updatePayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
            val savedInstallments = updatedInstallments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = payment.loanId)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = loansFlow.value.map { existing ->
                if (existing.id == payment.loanId) {
                    existing.copy(
                        installments = savedInstallments,
                        payments = existing.payments
                            .map { if (it.id == payment.id) payment else it }
                            .sortedWith(compareBy({ it.paidAt }, { it.id }))
                    )
                } else {
                    existing
                }
            }
        }

        override suspend fun deletePayment(loanId: Long, paymentId: Long, updatedInstallments: List<LoanInstallment>) {
            deletePaymentCallCount++
            deletePaymentGate?.await()
            val savedInstallments = updatedInstallments
                .map { inst ->
                    val instId = if (inst.id > 0L) inst.id else nextInstallmentId++
                    inst.copy(id = instId, loanId = loanId)
                }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = loansFlow.value.map { existing ->
                if (existing.id == loanId) {
                    existing.copy(
                        installments = savedInstallments,
                        payments = existing.payments.filterNot { it.id == paymentId }
                    )
                } else {
                    existing
                }
            }
        }

        override suspend fun reassignSavingCategory(oldName: String, newName: String) {
            loansFlow.value = loansFlow.value.map { loan ->
                val nextLoanCat = if (loan.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)) {
                    newName
                } else {
                    loan.savingCategory
                }
                val nextPayments = loan.payments.map { p ->
                    if (p.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)) {
                        p.copy(savingCategory = newName)
                    } else {
                        p
                    }
                }
                loan.copy(savingCategory = nextLoanCat, payments = nextPayments)
            }
        }
    }

    private class AdversarialSavingsRepo(
        initialCategories: List<Category> = listOf(
            Category("Emergencias", "🚨", "#3B82F6", emptyList()),
            Category("Viajes", "✈️", "#10B981", emptyList())
        )
    ) : SavingsRepository {
        val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
        val categoriesFlow = MutableStateFlow(initialCategories)

        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow
        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            savingsFlow.map { list -> list.filter { it.periodId == periodId } }
        override suspend fun getSavingById(id: Long): Saving? = savingsFlow.value.find { it.id == id }
        override suspend fun insertSaving(saving: Saving): Long {
            val id = (savingsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
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
            savingsFlow.value.count { it.category.trim().equals(categoryName.trim(), ignoreCase = true) }
        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun insertCategory(category: Category) { categoriesFlow.value += category }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name.equals(oldName, ignoreCase = true)) it.copy(name = newName, color = color, icon = icon) else it
            }
            savingsFlow.value = savingsFlow.value.map {
                if (it.category.equals(oldName, ignoreCase = true)) it.copy(category = newName) else it
            }
        }
        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
        }
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class AdversarialExpenseRepo : ExpenseRepository {
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
        override suspend fun insertExpense(expense: Expense) {
            val id = (expensesFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            expensesFlow.value = expensesFlow.value + expense.copy(id = id)
        }
        override suspend fun updateExpense(expense: Expense) {
            expensesFlow.value = expensesFlow.value.map { if (it.id == expense.id) expense else it }
        }
        override suspend fun deleteExpense(id: Long) {
            expensesFlow.value = expensesFlow.value.filterNot { it.id == id }
        }
    }

    private class AdversarialIncomeRepo : IncomeRepository {
        val incomesFlow = MutableStateFlow(
            listOf(Income(id = 1L, amount = 5000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = 1L))
        )
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

    // =========================================================================
    // 1. Property-Based Domain & Accounting Invariants
    // =========================================================================

    /**
     * Property: Over 200 randomized loans mixing valid penny payments, zero/negative payments,
     * and non-finite corrupt values (`NaN`, `Infinity`, `-Infinity`):
     * - `paidAmount`, `pendingAmount`, and `progressFraction` are always finite and non-negative.
     * - `progressFraction` is strictly in `[0f, 1f]`.
     * - For valid `amountToReceive > 0`: `paid + pending == max(amountToReceive, paid)` (BigDecimal exact).
     * - `isSettled == (pendingAmount == 0.0)`; when settled, `progressFraction == 1f`.
     */
    @Test
    fun property_loanModel_paidPendingSettledAndProgressInvariantsAcrossExtremePayments() {
        val rng = Random(17001L)
        val corruptPool = doubleArrayOf(0.0, -0.01, -100.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        repeat(200) { iter ->
            val targetCents = rng.nextInt(1, 500_000)
            val amountToReceive = BigDecimal(targetCents).movePointLeft(2).toDouble()
            val paymentCount = rng.nextInt(0, 12)
            var validPaidCents = 0L

            val payments = (1..paymentCount).map { idx ->
                val amount = if (rng.nextInt(5) == 0) {
                    corruptPool[rng.nextInt(corruptPool.size)]
                } else {
                    val cents = rng.nextInt(1, 120_000)
                    validPaidCents += cents
                    BigDecimal(cents).movePointLeft(2).toDouble()
                }
                LoanPayment(
                    id = idx.toLong(),
                    loanId = 1L,
                    amount = amount,
                    paidAt = "2026-11-26 10:00",
                    savingCategory = "Emergencias"
                )
            }

            val loan = Loan(
                id = 1L,
                personName = "Persona $iter",
                amountLent = amountToReceive,
                amountToReceive = amountToReceive,
                payments = payments
            )

            assertTrue("Iter $iter paidAmount finite & >= 0", loan.paidAmount.isFinite() && loan.paidAmount >= 0.0)
            assertTrue("Iter $iter pendingAmount finite & >= 0", loan.pendingAmount.isFinite() && loan.pendingAmount >= 0.0)
            assertTrue("Iter $iter progress in [0,1]", loan.progressFraction.isFinite() && loan.progressFraction in 0f..1f)

            val expectedPaid = BigDecimal(validPaidCents).movePointLeft(2)
            val expectedTarget = BigDecimal(targetCents).movePointLeft(2)
            val expectedPending = expectedTarget.subtract(expectedPaid).max(BigDecimal.ZERO)

            assertEquals("Iter $iter exact paidAmount", 0, BigDecimal.valueOf(loan.paidAmount).compareTo(expectedPaid))
            assertEquals("Iter $iter exact pendingAmount", 0, BigDecimal.valueOf(loan.pendingAmount).compareTo(expectedPending))
            assertEquals("Iter $iter isSettled", expectedPending.compareTo(BigDecimal.ZERO) == 0, loan.isSettled)
            if (loan.isSettled) {
                assertEquals("Iter $iter settled progress must be 1f", 1f, loan.progressFraction)
            }
        }
    }

    /**
     * Property: Over 200 randomized installment schedules with distinct persisted IDs, partial payments,
     * already-paid installments, unsorted due dates, and random target installment IDs:
     * - Total `paymentAmount` increases by EXACTLY `min(paymentAmount, totalRemainingBefore)`.
     * - No installment ever exceeds `paymentAmount > expectedAmount`.
     * - Previously paid installments keep identical `paymentAmount`.
     * - Output is sorted strictly by `(dueDate.trim(), id)`.
     * - Selected unpaid `targetInstallmentId` absorbs `min(paymentAmount, target.remainingAmount)` first.
     */
    @Test
    fun property_previewLoanInstallmentsAfterPayment_conservationOrderingAndTargetPriority() {
        val rng = Random(17002L)
        val datePool = listOf(
            "2026-11-20", "2026-11-26", "2026-12-01", "2026-12-15",
            "2027-01-05", "2027-02-28", "2028-02-29"
        )

        repeat(200) { iter ->
            val count = rng.nextInt(1, 10)
            val installments = (1..count).map { idx ->
                val expectedCents = rng.nextInt(100, 30_000)
                val paidCents = when (rng.nextInt(4)) {
                    0 -> expectedCents // already paid
                    1 -> rng.nextInt(1, expectedCents) // partially paid
                    else -> 0
                }
                LoanInstallment(
                    id = idx.toLong(),
                    loanId = 1L,
                    dueDate = datePool[rng.nextInt(datePool.size)],
                    expectedAmount = BigDecimal(expectedCents).movePointLeft(2).toDouble(),
                    paymentAmount = BigDecimal(paidCents).movePointLeft(2).toDouble()
                )
            }
            val totalExpected = installments.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.expectedAmount)) }
            val totalPaidBefore = installments.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.paymentAmount)) }
            val totalRemBefore = installments.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.remainingAmount)) }

            val unpaidBefore = installments
                .filter { !it.isPaid }
                .sortedWith(compareBy<LoanInstallment> { it.dueDate }.thenBy { it.id })
            val chosenTarget = unpaidBefore.takeIf { it.isNotEmpty() }?.let {
                if (rng.nextBoolean()) it[rng.nextInt(it.size)] else null
            }

            val paymentCents = rng.nextInt(1, 80_000)
            val paymentAmount = BigDecimal(paymentCents).movePointLeft(2).toDouble()
            val loan = Loan(
                id = 1L,
                personName = "Prop $iter",
                amountLent = totalExpected.toDouble(),
                amountToReceive = totalExpected.toDouble(),
                installments = installments
            )

            val after = previewLoanInstallmentsAfterPayment(loan, paymentAmount, chosenTarget?.id)

            // 1. Exact count & chronological order
            assertEquals("Iter $iter count preserved", installments.size, after.size)
            for (i in 0 until after.size - 1) {
                val cmp = compareValuesBy(after[i], after[i + 1], { it.dueDate.trim() }, { it.id })
                assertTrue("Iter $iter sorted invariant broken at index $i", cmp <= 0)
            }

            // 2. Exact conservation of applied funds
            val totalPaidAfter = after.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.paymentAmount)) }
            val expectedDelta = BigDecimal.valueOf(paymentAmount).min(totalRemBefore)
            assertEquals(
                "Iter $iter exact payment conservation",
                0,
                totalPaidAfter.subtract(totalPaidBefore).compareTo(expectedDelta)
            )

            // 3. No installment overpaid & previously paid untouched
            after.forEach { inst ->
                assertTrue(
                    "Iter $iter installment ${inst.id} overpaid: ${inst.paymentAmount} > ${inst.expectedAmount}",
                    BigDecimal.valueOf(inst.paymentAmount) <= BigDecimal.valueOf(inst.expectedAmount)
                )
            }
            val beforeById = installments.associateBy { it.id }
            after.forEach { inst ->
                val orig = beforeById.getValue(inst.id)
                if (orig.isPaid) {
                    assertEquals(
                        "Iter $iter already paid installment ${inst.id} mutated",
                        0,
                        BigDecimal.valueOf(orig.paymentAmount).compareTo(BigDecimal.valueOf(inst.paymentAmount))
                    )
                }
            }

            // 4. Target installment priority
            if (chosenTarget != null) {
                val updatedTarget = after.first { it.id == chosenTarget.id }
                val expectedTargetDelta = BigDecimal.valueOf(paymentAmount).min(BigDecimal.valueOf(chosenTarget.remainingAmount))
                val actualTargetDelta = BigDecimal.valueOf(updatedTarget.paymentAmount)
                    .subtract(BigDecimal.valueOf(chosenTarget.paymentAmount))
                assertEquals("Iter $iter target installment must absorb payment first", 0, actualTargetDelta.compareTo(expectedTargetDelta))
            }
        }
    }

    /**
     * Property: Over 200 randomized cross-module portfolios `(savings, expenses, loans, loanPayments)`
     * including Unicode/case/whitespace category variants and corrupt values (`NaN`, `Infinity`, negatives):
     * - `calculateSavingsNetBalances` matches `calculateCategoryAvailableSavings` exactly for every category.
     * - `calculateCategoryAvailableSavings == max(0, inflows - outflows)`.
     * - Case/whitespace variants collapse into a single canonical balance row.
     * - `buildSavingsNetSummary` total equals exact sum of positive net balances.
     */
    @Test
    fun property_savingsNetBalances_algebraicConsistencyWithCategoryAvailableSavings() {
        val rng = Random(17003L)
        val catVariants = listOf(
            listOf("Educación", "EDUCACIÓN", "  educación  "),
            listOf("Emergencias", "emergencias", " EMERGENCIAS "),
            listOf("Viajes", "VIAJES", "viajes")
        )
        val categories = listOf(
            Category("Educación", "🎓", "#3B82F6", emptyList()),
            Category("Emergencias", "🚨", "#EF4444", emptyList()),
            Category("Viajes", "✈️", "#10B981", emptyList())
        )
        val corruptAmounts = doubleArrayOf(0.0, -50.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        fun pickAmount(): Double = if (rng.nextInt(6) == 0) {
            corruptAmounts[rng.nextInt(corruptAmounts.size)]
        } else {
            BigDecimal(rng.nextInt(1, 50_000)).movePointLeft(2).toDouble()
        }

        fun pickCat(): String = if (rng.nextInt(8) == 0) {
            "   "
        } else {
            val group = catVariants[rng.nextInt(catVariants.size)]
            group[rng.nextInt(group.size)]
        }

        repeat(200) { iter ->
            val savings = (1..rng.nextInt(0, 8)).map { i ->
                Saving(id = i.toLong(), amount = pickAmount(), category = pickCat(), periodId = 1L)
            }
            val expenses = (1..rng.nextInt(0, 8)).map { i ->
                Expense(
                    id = i.toLong(),
                    amount = pickAmount(),
                    date = "2026-11-26",
                    description = "Exp $i",
                    category = "General",
                    fundingSource = if (rng.nextBoolean()) ExpenseFundingSource.SAVINGS else ExpenseFundingSource.BUDGET,
                    savingCategory = pickCat()
                )
            }
            val loans = (1..rng.nextInt(0, 6)).map { i ->
                val payments = (1..rng.nextInt(0, 4)).map { p ->
                    LoanPayment(
                        id = (i * 10 + p).toLong(),
                        loanId = i.toLong(),
                        amount = pickAmount(),
                        paidAt = "2026-11-26 12:00",
                        savingCategory = pickCat()
                    )
                }
                Loan(
                    id = i.toLong(),
                    personName = "L$i",
                    amountLent = pickAmount(),
                    amountToReceive = 1000.0,
                    savingCategory = pickCat(),
                    payments = payments
                )
            }

            val balances = calculateSavingsNetBalances(savings, expenses, categories, loans = loans)
            // No duplicate case-insensitive category keys
            val normalizedKeys = balances.map { it.categoryName.trim().lowercase(Locale.ROOT) }
            assertEquals("Iter $iter no duplicate category rows", normalizedKeys.size, normalizedKeys.distinct().size)

            // Cross-function algebraic consistency
            balances.forEach { bal ->
                val inflows = calculateCategorySavingsInflows(bal.categoryName, savings, loans)
                val outflows = calculateCategorySavingsOutflows(bal.categoryName, expenses, loans)
                val available = calculateCategoryAvailableSavings(bal.categoryName, savings, expenses, loans)
                assertEquals(
                    "Iter $iter available == max(0, inflows - outflows)",
                    0,
                    available.compareTo(inflows.subtract(outflows).max(BigDecimal.ZERO))
                )
                assertEquals(
                    "Iter $iter netBalance matches calculateCategoryAvailableSavings for ${bal.categoryName}",
                    0,
                    BigDecimal.valueOf(bal.netBalance).compareTo(available)
                )
            }

            // Summary consistency
            val summary = buildSavingsNetSummary(savings, expenses, categories, loans = loans)
            val expectedSummaryTotal = balances
                .filter { it.netBalance > 0.0 }
                .fold(BigDecimal.ZERO) { acc, b -> acc.add(BigDecimal.valueOf(b.netBalance)) }
            if (expectedSummaryTotal <= BigDecimal.ZERO) {
                assertTrue("Iter $iter empty summary expected", summary.isEmpty)
            } else {
                assertEquals(
                    "Iter $iter summary total conservation",
                    0,
                    BigDecimal.valueOf(summary.totalAmount).compareTo(expectedSummaryTotal)
                )
                summary.items.forEach { item ->
                    assertTrue("Iter $iter proportion in [0,1]", item.proportion in 0f..1f)
                    assertTrue("Iter $iter percentage in [0,100]", item.percentage in 0..100)
                }
            }
        }
    }

    /**
     * Property: Over 150 randomized partial payments across `KeepCurrent`, `SetNewDueDate`,
     * `AddInstallment`, and `AdjustNextInstallment` (both increasing and decreasing the next installment,
     * with 1 or multiple remaining installments):
     * - Every accepted payment MUST preserve `sum(installments.remainingAmount) == loan.pendingAmount`.
     * - Every installment MUST satisfy `0 <= paymentAmount <= expectedAmount`.
     * - Schedule MUST remain chronologically sorted.
     */
    @Test
    fun property_partialPaymentScheduleOptions_preserveExactPendingBalanceInvariant() = runBlocking {
        val rng = Random(17004L)
        val today = "2026-11-26"
        val futureDates = listOf("2026-11-26", "2026-12-01", "2026-12-15", "2027-01-10", "2027-03-01", "2028-02-29")

        repeat(150) { iter ->
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val useCase = LoanUseCase(
                loanRepository = loanRepo,
                savingsRepository = savingsRepo,
                loanMutex = Mutex(),
                currentDateProvider = { today },
                currentDateTimeProvider = { "$today 10:00" }
            )

            val parts = rng.nextInt(1, 5)
            val partCents = (1..parts).map { rng.nextInt(500, 10_000) }
            val totalCents = partCents.sum()
            val installments = partCents.mapIndexed { idx, cents ->
                LoanInstallment(
                    dueDate = futureDates[idx % futureDates.size],
                    expectedAmount = BigDecimal(cents).movePointLeft(2).toDouble()
                )
            }
            val loanId = loanRepo.insertLoan(
                Loan(
                    personName = "Partial $iter",
                    amountLent = BigDecimal(totalCents).movePointLeft(2).toDouble(),
                    amountToReceive = BigDecimal(totalCents).movePointLeft(2).toDouble(),
                    installments = installments
                )
            )

            // Strict partial payment in [1 cent, totalCents - 1 cent]
            val payCents = rng.nextInt(1, totalCents)
            val remainingCents = totalCents - payCents
            val payStr = BigDecimal(payCents).movePointLeft(2).toPlainString()

            val option = when (iter % 4) {
                0 -> PartialPaymentScheduleOption.KeepCurrent
                1 -> PartialPaymentScheduleOption.SetNewDueDate(futureDates[rng.nextInt(futureDates.size)])
                2 -> {
                    val extraCents = rng.nextInt(1, remainingCents + 1)
                    PartialPaymentScheduleOption.AddInstallment(
                        dueDate = futureDates[rng.nextInt(futureDates.size)],
                        amountInput = BigDecimal(extraCents).movePointLeft(2).toPlainString()
                    )
                }
                else -> {
                    val adjCents = rng.nextInt(1, remainingCents + 1)
                    PartialPaymentScheduleOption.AdjustNextInstallment(
                        amountInput = BigDecimal(adjCents).movePointLeft(2).toPlainString(),
                        remainderDueDate = futureDates[rng.nextInt(futureDates.size)]
                    )
                }
            }

            val result = useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = payStr,
                paidAtInput = "$today 14:30",
                savingCategoryInput = "Emergencias",
                scheduleOption = option
            )
            assertTrue("Iter $iter option=$option must succeed; got $result", result is LoanResult.Success)

            val updated = loanRepo.loansFlow.value.single()
            val expectedRemaining = BigDecimal(remainingCents).movePointLeft(2)
            val actualScheduledRemaining = updated.installments
                .fold(BigDecimal.ZERO) { acc, inst -> acc.add(BigDecimal.valueOf(inst.remainingAmount)) }

            assertEquals("Iter $iter loan.pendingAmount", 0, BigDecimal.valueOf(updated.pendingAmount).compareTo(expectedRemaining))
            assertEquals(
                "Iter $iter option=$option sum(installments.remainingAmount) must equal loan.pendingAmount",
                0,
                actualScheduledRemaining.compareTo(expectedRemaining)
            )
            updated.installments.forEach { inst ->
                assertTrue("Iter $iter paymentAmount >= 0", inst.paymentAmount >= 0.0)
                assertTrue(
                    "Iter $iter paymentAmount <= expectedAmount",
                    BigDecimal.valueOf(inst.paymentAmount) <= BigDecimal.valueOf(inst.expectedAmount)
                )
            }
            for (i in 0 until updated.installments.size - 1) {
                assertTrue(
                    "Iter $iter sorted due dates",
                    updated.installments[i].dueDate <= updated.installments[i + 1].dueDate
                )
            }
        }
    }

    // =========================================================================
    // 2. Exposed Domain Bugs & Adversarial Exploits
    // =========================================================================

    /**
     * EXPOSES BUG (`LoanUseCase.kt` line 119 — `previewLoanInstallmentsAfterPayment` uses
     * `mutableMapOf<LoanInstallment, LoanInstallment>()` instead of `IdentityHashMap` or index tracking):
     * `LoanInstallment` is a Kotlin `data class`, so two unpersisted installments with identical
     * `(id = 0, dueDate, expectedAmount, paymentAmount = 0.0)` are `equals()`-equal despite being
     * distinct list elements. Writing `updatedByIdentity[instB] = instB` overwrites `updatedByIdentity[instA]`,
     * silently dropping the entire `$50` payment (`sum(paymentAmount) == 0.0`)!
     */
    @Test
    fun previewLoanInstallmentsAfterPayment_identicalUnpersistedInstallments_mustNotDropOrDoubleApplyPayment() {
        val instA = LoanInstallment(id = 0L, loanId = 1L, dueDate = "2026-12-01", expectedAmount = 100.0)
        val instB = LoanInstallment(id = 0L, loanId = 1L, dueDate = "2026-12-01", expectedAmount = 100.0)
        val loan = Loan(
            id = 1L,
            personName = "Clon",
            amountLent = 200.0,
            amountToReceive = 200.0,
            installments = listOf(instA, instB)
        )

        val preview = previewLoanInstallmentsAfterPayment(loan, paymentAmount = 50.0)
        val totalApplied = preview.fold(BigDecimal.ZERO) { acc, i -> acc.add(BigDecimal.valueOf(i.paymentAmount)) }
        assertEquals(
            "Applying \$50 across two equal-value unpersisted installments must conserve exactly \$50, not drop/duplicate it",
            0,
            totalApplied.compareTo(BigDecimal("50.00"))
        )
        assertEquals(listOf(50.0, 0.0), preview.map { it.paymentAmount }.sortedDescending())
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 462–484 — `recomputeInstallmentsWithoutPayment` double-counts
     * pre-schedule payments created with `SetNewDueDate` when a later payment is deleted or edited):
     * 1. Loan of `$300` starts with NO installments (`installments = []`).
     * 2. Payment #1 of `$100` uses `SetNewDueDate("2026-12-20")` -> creates 1 installment of `$200` (already net of P1!).
     * 3. Payment #2 of `$50` applies to that `$200` installment -> `paymentAmount = 50, remaining = 150`.
     * 4. User deletes Payment #2 (`deleteLoanPayment`): `recomputeInstallmentsWithoutPayment` resets
     *    `paymentAmount = 0` and replays Payment #1 (`$100`) ONTO the `$200` installment, leaving
     *    `installments.remainingAmount == $100` while `loan.pendingAmount == $200`!
     */
    @Test
    fun deleteLoanPayment_afterSetNewDueDateOnUnscheduledLoan_mustNotDoubleCountInitialPayment() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" },
            currentDateTimeProvider = { "2026-11-26 10:00" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(personName = "Camila", amountLent = 300.0, amountToReceive = 300.0, installments = emptyList())
        )

        // Payment #1 ($100) defines a due date for the $200 remaining balance
        val r1 = useCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "100",
            paidAtInput = "2026-11-26 10:00",
            savingCategoryInput = "Emergencias",
            scheduleOption = PartialPaymentScheduleOption.SetNewDueDate("2026-12-20")
        )
        assertTrue(r1 is LoanResult.Success)

        // Payment #2 ($50) applied against the $200 installment
        val r2 = useCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "50",
            paidAtInput = "2026-11-26 11:00",
            savingCategoryInput = "Emergencias",
            scheduleOption = PartialPaymentScheduleOption.KeepCurrent
        )
        assertTrue(r2 is LoanResult.Success)
        val payment2Id = loanRepo.loansFlow.value.single().payments.last().id

        // Delete Payment #2 -> remaining loan balance is $200, so remaining scheduled installments MUST equal $200
        val delRes = useCase.deleteLoanPayment(loanId, payment2Id)
        assertTrue(delRes is LoanResult.Success)

        val afterDelete = loanRepo.loansFlow.value.single()
        val scheduledRemaining = afterDelete.installments
            .fold(BigDecimal.ZERO) { acc, inst -> acc.add(BigDecimal.valueOf(inst.remainingAmount)) }
        assertEquals(200.0, afterDelete.pendingAmount, 0.001)
        assertEquals(
            "Deleting Payment #2 must NOT replay Payment #1 onto an installment that already excluded Payment #1",
            0,
            scheduledRemaining.compareTo(BigDecimal("200.00"))
        )
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 936–945 — `deleteLoan` omits savings outflow integrity check):
     * Unlike `deleteLoanPayment`, `deleteLoan` deletes the loan and cascades all its `loan_payments`
     * without verifying whether those credited repayments back existing savings-funded expenses or
     * other loans, leaving the savings category with outflows > inflows (hidden negative balance).
     */
    @Test
    fun deleteLoan_whoseRepaymentsBackRegisteredExpense_mustBeRejected() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            expenseRepository = expenseRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(personName = "Andrés", amountLent = 500.0, amountToReceive = 500.0)
        )
        val payRes = useCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "500",
            paidAtInput = "2026-11-26 10:00",
            savingCategoryInput = "Emergencias"
        )
        assertTrue(payRes is LoanResult.Success)

        // Spend $400 from the $500 repayment credited to "Emergencias"
        expenseRepo.insertExpense(
            Expense(
                amount = 400.0,
                date = "2026-11-26",
                description = "Reparación",
                category = "Hogar",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategory = "Emergencias"
            )
        )

        // Deleting the loan would cascade-delete the $500 repayment backing the $400 expense!
        val deleteResult = useCase.deleteLoan(loanId)
        assertTrue(
            "deleteLoan must reject deleting a loan whose repayments back registered savings outflows; got $deleteResult",
            deleteResult is LoanResult.Error
        )
        assertEquals("Loan must remain persisted when deletion is rejected", 1, loanRepo.loansFlow.value.size)
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 855–886 — `updateLoan` permits `amountToReceive < existingLoan.paidAmount`):
     * If a loan already has `$150` paid on an initial `$200` receivable, editing `amountToReceive` down
     * to `$120` succeeds because only `amountToReceive >= amountLent` is checked, producing an invalid
     * overpaid state (`paidAmount = 150 > amountToReceive = 120`).
     */
    @Test
    fun updateLoan_amountToReceiveBelowAlreadyPaidAmount_mustBeRejected() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(personName = "Paula", amountLent = 100.0, amountToReceive = 200.0)
        )
        assertTrue(
            useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "150",
                paidAtInput = "2026-11-26 09:00",
                savingCategoryInput = "Emergencias"
            ) is LoanResult.Success
        )

        val updateRes = useCase.updateLoan(
            loanId = loanId,
            personNameInput = "Paula",
            amountLentInput = "100",
            amountToReceiveInput = "120"
        )
        assertTrue(
            "updateLoan must reject setting amountToReceive (\$120) below already paidAmount (\$150); got $updateRes",
            updateRes is LoanResult.Error
        )
        assertEquals(200.0, loanRepo.loansFlow.value.single().amountToReceive, 0.001)
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 952–965 — `validateScheduleForLoan` trusts caller-supplied
     * `paymentAmount` on brand-new installments and permits deleting already-paid installments):
     * 1. Submitting a new installment (`persistedId = 0L`) with a forged `paymentAmount = 500.0` is accepted
     *    verbatim as a "paid" installment with zero `LoanPayment` records!
     * 2. Omitting an already-paid persisted installment (`paymentAmount = 100.0`) passes because only
     *    `sum(remainingAmount) == pendingAmount` is checked, silently wiping paid installment history
     *    and orphaning `LoanPayment.installmentId`.
     */
    @Test
    fun updateLoanInstallments_forgedPaymentAmountAndDroppingPaidInstallments_mustBeRejected() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        // Part A: Forged paymentAmount on brand-new installment of an unpaid $200 loan
        val unpaidLoanId = loanRepo.insertLoan(
            Loan(personName = "Esteban", amountLent = 200.0, amountToReceive = 200.0)
        )
        val forgeResult = useCase.updateLoanInstallments(
            loanId = unpaidLoanId,
            installmentInputs = listOf(
                LoanInstallmentInput(dueDate = "2026-12-01", expectedAmountInput = "500", persistedId = 0L, paymentAmount = 500.0),
                LoanInstallmentInput(dueDate = "2026-12-15", expectedAmountInput = "200", persistedId = 0L, paymentAmount = 0.0)
            )
        )
        assertTrue(
            "Forging paymentAmount > 0 on a brand-new installment must be rejected; got $forgeResult",
            forgeResult is LoanResult.Error
        )

        // Part B: Dropping an already-paid installment on a partially-paid loan
        val paidLoanId = loanRepo.insertLoan(
            Loan(
                personName = "Lucía",
                amountLent = 300.0,
                amountToReceive = 300.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 100.0),
                    LoanInstallment(dueDate = "2026-12-15", expectedAmount = 200.0)
                )
            )
        )
        assertTrue(
            useCase.recordLoanPayment(
                loanId = paidLoanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Emergencias"
            ) is LoanResult.Success
        )
        val secondInstallment = loanRepo.loansFlow.value.first { it.id == paidLoanId }.installments.last()

        val dropPaidResult = useCase.updateLoanInstallments(
            loanId = paidLoanId,
            installmentInputs = listOf(
                LoanInstallmentInput(
                    dueDate = secondInstallment.dueDate,
                    expectedAmountInput = "200",
                    persistedId = secondInstallment.id
                )
            )
        )
        assertTrue(
            "Dropping an already-paid persisted installment must be rejected to preserve payment links; got $dropPaidResult",
            dropPaidResult is LoanResult.Error
        )
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 678–719 — `AddInstallment` on a loan with no existing
     * unpaid installments creates an underfunded schedule whose sum != `remainingAfter`):
     * Loan of `$300` with `installments = []` pays `$100` (`remaining = $200`) with
     * `AddInstallment("2026-12-20", "50")`. Because `unpaidAfter` is empty, nothing is deducted and
     * a single `$50` installment is saved for a `$200` pending debt, violating schedule conservation.
     */
    @Test
    fun recordLoanPayment_addInstallmentWithoutExistingSchedule_mustNotCreateUnderfundedSchedule() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(personName = "Jorge", amountLent = 300.0, amountToReceive = 300.0, installments = emptyList())
        )
        val result = useCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "100",
            paidAtInput = "2026-11-26 10:00",
            savingCategoryInput = "Emergencias",
            scheduleOption = PartialPaymentScheduleOption.AddInstallment("2026-12-20", "50")
        )

        if (result is LoanResult.Success) {
            val saved = loanRepo.loansFlow.value.single()
            val scheduledRem = saved.installments.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.remainingAmount)) }
            assertEquals(
                "Schedule remaining sum must equal loan pendingAmount (\$200), not \$50",
                0,
                scheduledRem.compareTo(BigDecimal.valueOf(saved.pendingAmount))
            )
        } else {
            assertTrue(result is LoanResult.Error)
        }
    }

    /**
     * EXPOSES BUG (`Loan.kt` lines 69–70 — `Loan.paidAmount` collapses to `0.0` on `Double` overflow):
     * When summed payments exceed `Double.MAX_VALUE`, `paidDecimal.toDouble()` is `Infinity` and
     * `.takeIf { it.isFinite() } ?: 0.0` returns `0.0` while `isSettled == true` and `progressFraction == 1f`.
     */
    @Test
    fun loanModel_hugePaymentsExceedingDoubleMaxValue_mustCapNotCollapseToZero() {
        val loan = Loan(
            id = 1L,
            personName = "Overflow",
            amountLent = 1e308,
            amountToReceive = 1e308,
            payments = listOf(
                LoanPayment(id = 1L, loanId = 1L, amount = 1e308, paidAt = "2026-11-26 10:00", savingCategory = "Emergencias"),
                LoanPayment(id = 2L, loanId = 1L, amount = 1e308, paidAt = "2026-11-26 11:00", savingCategory = "Emergencias")
            )
        )
        assertTrue("Loan should be settled", loan.isSettled)
        assertTrue(
            "paidAmount must NOT collapse to 0.0 on Double overflow when loan is settled; got ${loan.paidAmount}",
            loan.paidAmount > 0.0 && loan.paidAmount.isFinite()
        )
    }

    /**
     * EXPOSES BUG (`SavingsUseCase.kt` lines 440–453 — `deleteCategory` ignores loan repayment balances):
     * If a category has `0` entries in `savings` table but `$300` of active net savings balance credited
     * from `loan_payments`, `deleteCategory` deletes the category anyway!
     */
    @Test
    fun savingsUseCase_deleteCategoryWithActiveLoanRepaymentBalance_mustBeRejected() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val sharedMutex = Mutex()

        val loanUseCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            expenseRepository = expenseRepo,
            loanMutex = sharedMutex,
            currentDateProvider = { "2026-11-26" }
        )
        val savingsUseCase = SavingsUseCase(
            savingsRepository = savingsRepo,
            incomeRepository = incomeRepo,
            expenseRepository = expenseRepo,
            loanRepository = loanRepo,
            savingMutex = sharedMutex
        )

        val loanId = loanRepo.insertLoan(Loan(personName = "Diana", amountLent = 300.0, amountToReceive = 300.0))
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "300",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Viajes"
            ) is LoanResult.Success
        )

        // Verify "Viajes" holds $300 net savings balance from repayment
        val netBalances = calculateSavingsNetBalances(
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            categories = savingsRepo.categoriesFlow.value,
            loans = loanRepo.loansFlow.value
        )
        assertEquals(300.0, netBalances.single { it.categoryName == "Viajes" }.netBalance, 0.001)

        val deleteCatRes = savingsUseCase.deleteCategory("Viajes")
        assertTrue(
            "Deleting a saving category holding \$300 from loan repayments must be rejected; got $deleteCatRes",
            deleteCatRes is SavingsResult.Error
        )
        assertTrue(
            "Category 'Viajes' must still exist in catalog",
            savingsRepo.categoriesFlow.value.any { it.name == "Viajes" }
        )
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 66–81 — `isValidLoanPaymentDateTime` uses local default TimeZone):
     * On DST spring-forward-at-midnight dates (`America/Santiago` `2026-09-06`), a valid date-only
     * payment timestamp `"2026-09-06"` is falsely rejected by strict `SimpleDateFormat` parsing.
     */
    @Test
    fun recordLoanPayment_dstMidnightDateOnlyInLocalTimezone_mustBeAccepted() = runBlocking {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Santiago"))
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val useCase = LoanUseCase(
                loanRepository = loanRepo,
                savingsRepository = savingsRepo,
                loanMutex = Mutex(),
                currentDateProvider = { "2026-09-01" }
            )
            val loanId = loanRepo.insertLoan(Loan(personName = "Tomás", amountLent = 100.0, amountToReceive = 100.0))

            val res = useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-09-06",
                savingCategoryInput = "Emergencias"
            )
            assertTrue(
                "Valid calendar date '2026-09-06' must NOT be rejected in America/Santiago DST midnight; got $res",
                res is LoanResult.Success
            )
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 809–834 — `updateLoanInstallments` does NOT acquire `loanMutex`):
     * While `recordLoanPayment` holds `loanMutex` mid-transaction, a concurrent `updateLoanInstallments`
     * bypasses the mutex completely, reading stale `pendingAmount`/`paymentAmount` and racing
     * `replaceInstallments` against the in-flight payment!
     */
    @Test
    fun concurrency_recordPaymentAndUpdateLoanInstallments_mustSerializeViaMutex() = runTest {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val mutex = Mutex()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = mutex,
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Race",
                amountLent = 300.0,
                amountToReceive = 300.0,
                installments = listOf(LoanInstallment(dueDate = "2026-12-10", expectedAmount = 300.0))
            )
        )

        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        loanRepo.recordPaymentStarted = started
        loanRepo.recordPaymentGate = gate

        val paymentJob = async {
            useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Emergencias"
            )
        }
        started.await()

        // While recordLoanPayment holds loanMutex, updateLoanInstallments MUST block until payment finishes
        val scheduleJob = launch {
            useCase.updateLoanInstallments(
                loanId = loanId,
                installmentInputs = listOf(LoanInstallmentInput("2026-12-20", "300"))
            )
        }
        testScheduler.runCurrent()

        assertEquals(
            "updateLoanInstallments MUST wait on loanMutex while recordLoanPayment is in flight (must not bypass mutex)",
            0,
            loanRepo.replaceInstallmentsCallCount
        )

        gate.complete(Unit)
        paymentJob.await()
        scheduleJob.join()
    }

    @Test
    fun formatLoanPaymentDateTime_multipleSpacesAndCorruptInputs_neverThrow() {
        assertEquals("", formatLoanPaymentDateTime("   ", "es"))
        assertEquals("not-a-date", formatLoanPaymentDateTime("not-a-date", "es"))
        // Multiple spaces between date and time should still preserve time rather than silently dropping it
        val formatted = formatLoanPaymentDateTime("2026-11-26 14:30", "es")
        assertTrue("Expected formatted time 14:30 in '$formatted'", formatted.endsWith("14:30"))
        assertNotEquals("2026-11-26 14:30", formatted)
    }

    // =========================================================================
    // 3. Room SQLite Migrations (1->2->3->4 & 3->4), CASCADE & Sync Integrity
    // =========================================================================

    @Test
    fun room_fullMigrationChain1To4_categoryRenameUpdatesLoansAndPayments_andDeleteCascades() = runBlocking {
        val dbFile = File(context.cacheDir, "adv_loans_mig1to4_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personName` TEXT NOT NULL,
                `amountLent` REAL NOT NULL,
                `amountToReceive` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `savingCategory` TEXT
            )
            """.trimIndent()
        )
        v1.execSQL(
            "INSERT INTO `loans` (`id`, `personName`, `amountLent`, `amountToReceive`, `note`, `savingCategory`) " +
                "VALUES (1, 'Sofía ''OR 1=1--', 500.0, 500.0, 'Estudios', 'EDUCACIÓN')"
        )
        v1.version = 1
        v1.close()

        val roomDb = Room.databaseBuilder(context, LoansDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LoansDatabase.MIGRATION_1_2, LoansDatabase.MIGRATION_2_3, LoansDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            repo.replaceInstallments(
                1L,
                listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 200.0),
                    LoanInstallment(dueDate = "2026-12-15", expectedAmount = 300.0)
                )
            )
            val inst1 = repo.getAllLoans().first().single().installments.first()

            repo.recordPayment(
                payment = LoanPayment(
                    loanId = 1L,
                    amount = 200.0,
                    paidAt = "2026-11-26 15:00",
                    savingCategory = "EDUCACIÓN",
                    installmentId = inst1.id
                ),
                updatedInstallments = listOf(
                    inst1.copy(paymentAmount = 200.0),
                    repo.getAllLoans().first().single().installments.last()
                )
            )

            // Rename saving category using different Unicode casing -> must update BOTH loans and loan_payments
            repo.reassignSavingCategory("educación", "Universidad")
            val afterRename = repo.getAllLoans().first().single()
            assertEquals("Universidad", afterRename.savingCategory)
            assertEquals("Universidad", afterRename.payments.single().savingCategory)
            assertEquals(inst1.id, afterRename.payments.single().installmentId)
            assertEquals(200.0, afterRename.installments.first().paymentAmount, 0.001)

            // Deleting parent loan MUST trigger ON DELETE CASCADE for BOTH loan_installments AND loan_payments
            repo.deleteLoan(1L)
            roomDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM loan_installments").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ON DELETE CASCADE must remove loan_installments", 0, c.getInt(0))
            }
            roomDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM loan_payments").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ON DELETE CASCADE must remove loan_payments", 0, c.getInt(0))
            }
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }

    /**
     * EXPOSES BUG (`LoansDatabase.kt` lines 288–297 — `MIGRATION_3_4` leaves `paymentAmount = 0.0`
     * on existing installments when upgrading from DB v3 with already-recorded payments):
     * A v3 database has a `$300` loan with installments `($100, $200)` and a recorded `$100` payment.
     * After `MIGRATION_3_4`, `loan.pendingAmount` is `$200`, yet `loan_installments.paymentAmount`
     * remains `0.0` on every row (`sum(remainingAmount) == $300 != $200`), showing paid installments
     * as unpaid/overdue and breaking schedule editing!
     */
    @Test
    fun room_migration3To4_preExistingPayments_mustBackfillInstallmentPaymentAmounts() = runBlocking {
        val dbFile = File(context.cacheDir, "adv_loans_mig3to4_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personName` TEXT NOT NULL,
                `amountLent` REAL NOT NULL,
                `amountToReceive` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `savingCategory` TEXT
            )
            """.trimIndent()
        )
        v3.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loan_installments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `loanId` INTEGER NOT NULL,
                `dueDate` TEXT NOT NULL,
                `expectedAmount` REAL NOT NULL,
                FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        v3.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_installments_loanId` ON `loan_installments` (`loanId`)")
        v3.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loan_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `loanId` INTEGER NOT NULL,
                `amount` REAL NOT NULL,
                `paidAt` TEXT NOT NULL,
                `savingCategory` TEXT NOT NULL,
                FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        v3.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")

        v3.execSQL("INSERT INTO `loans` VALUES (1, 'Carlos', 300.0, 300.0, 'v3 loan', 'Emergencias')")
        v3.execSQL("INSERT INTO `loan_installments` VALUES (1, 1, '2026-12-01', 100.0)")
        v3.execSQL("INSERT INTO `loan_installments` VALUES (2, 1, '2026-12-15', 200.0)")
        v3.execSQL("INSERT INTO `loan_payments` VALUES (1, 1, 100.0, '2026-11-25 09:00', 'Emergencias')")
        v3.version = 3
        v3.close()

        val roomDb = Room.databaseBuilder(context, LoansDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LoansDatabase.MIGRATION_1_2, LoansDatabase.MIGRATION_2_3, LoansDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val migrated = repo.getAllLoans().first().single()
            assertEquals(100.0, migrated.paidAmount, 0.001)
            assertEquals(200.0, migrated.pendingAmount, 0.001)

            val scheduledRemaining = migrated.installments
                .fold(BigDecimal.ZERO) { acc, i -> acc.add(BigDecimal.valueOf(i.remainingAmount)) }
            assertEquals(
                "MIGRATION_3_4 must backfill paymentAmount so sum(installments.remainingAmount) == loan.pendingAmount (\$200)",
                0,
                scheduledRemaining.compareTo(BigDecimal("200.00"))
            )
            assertTrue("First installment (\$100) must be marked paid after migrating v3 \$100 payment", migrated.installments.first().isPaid)
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }

    /**
     * EXPOSES BUG (`LoansDatabase.kt` lines 155–170 — `LoanDao.syncInstallments` silently overwrites
     * rows when two entries share an existing `id`):
     * Calling `updateLoanInstallments` with two installments carrying the same `persistedId` passes
     * validation AND executes two `UPDATE ... WHERE id = :id` statements in `syncInstallments`,
     * silently dropping one installment and leaving `sum(expectedAmount) != amountToReceive` in Room!
     */
    @Test
    fun room_syncInstallments_duplicatePersistedId_mustNotSilentlyDropInstallment() = runBlocking {
        val roomDb = Room.inMemoryDatabaseBuilder(context, LoansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val useCase = LoanUseCase(repo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" })
            val loanId = repo.insertLoan(
                Loan(
                    personName = "DupID",
                    amountLent = 300.0,
                    amountToReceive = 300.0,
                    installments = listOf(LoanInstallment(dueDate = "2026-12-01", expectedAmount = 300.0))
                )
            )
            val existingInstId = repo.getAllLoans().first().single().installments.single().id

            val res = useCase.updateLoanInstallments(
                loanId = loanId,
                installmentInputs = listOf(
                    LoanInstallmentInput(dueDate = "2026-12-01", expectedAmountInput = "100", persistedId = existingInstId),
                    LoanInstallmentInput(dueDate = "2026-12-15", expectedAmountInput = "200", persistedId = existingInstId)
                )
            )

            // Either rejected with Error, OR persisted without silent data loss
            if (res is LoanResult.Success) {
                val persisted = repo.getAllLoans().first().single().installments
                val sum = persisted.fold(BigDecimal.ZERO) { a, i -> a.add(BigDecimal.valueOf(i.expectedAmount)) }
                assertEquals("Duplicate persistedId caused silent row loss in Room!", 2, persisted.size)
                assertEquals(0, sum.compareTo(BigDecimal("300.00")))
            } else {
                assertTrue(res is LoanResult.Error)
            }
        } finally {
            roomDb.close()
        }
    }

    // =========================================================================
    // 4. LoansViewModel Concurrency, Idempotency & State Traps
    // =========================================================================

    @Test
    fun loansViewModel_saveLoanPayment_tripleTapIdempotencyAndStorageErrorRecovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val loanId = loanRepo.insertLoan(Loan(personName = "Mateo", amountLent = 400.0, amountToReceive = 400.0))
            val viewModel = LoansViewModel(
                LoanUseCase(
                    loanRepository = loanRepo,
                    savingsRepository = savingsRepo,
                    loanMutex = Mutex(),
                    currentDateProvider = { "2026-11-26" },
                    currentDateTimeProvider = { "2026-11-26 12:00" }
                )
            )
            advanceUntilIdle()

            viewModel.openPaymentDialog(viewModel.uiState.value.loans.single { it.id == loanId })
            viewModel.onPaymentAmountChange("150")
            viewModel.onPaymentSavingCategorySelected("Emergencias")

            val gate = CompletableDeferred<Unit>()
            loanRepo.recordPaymentGate = gate

            // Rapid triple tap while first payment is suspended
            viewModel.saveLoanPayment()
            viewModel.saveLoanPayment()
            viewModel.saveLoanPayment()
            testScheduler.runCurrent()

            assertEquals("Only 1 recordPayment call may be in flight", 1, loanRepo.recordPaymentCallCount)
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull("Dialog must close on success", viewModel.uiState.value.payingLoan)
            assertEquals(LoansViewModel.MSG_PAYMENT_SAVED, viewModel.uiState.value.toastMessage)
            assertEquals(150.0, viewModel.uiState.value.totalPaid, 0.001)
            assertEquals(250.0, viewModel.uiState.value.totalReceivable, 0.001)

            // Storage failure must keep dialog & user inputs intact and allow retry
            loanRepo.recordPaymentGate = null
            loanRepo.throwOnRecordPayment = IllegalStateException("SQLite disk full")
            viewModel.openPaymentDialog(viewModel.uiState.value.loans.single())
            viewModel.onPaymentAmountChange("100")
            viewModel.onPaymentSavingCategorySelected("Emergencias")
            viewModel.saveLoanPayment()
            advanceUntilIdle()

            assertNotNull("Storage failure must surface paymentErrorMessage", viewModel.uiState.value.paymentErrorMessage)
            assertNotNull("Payment dialog must stay open on failure", viewModel.uiState.value.payingLoan)
            assertEquals("100", viewModel.uiState.value.paymentAmountInput)

            // Recover storage & retry
            loanRepo.throwOnRecordPayment = null
            viewModel.saveLoanPayment()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.payingLoan)
            assertNull(viewModel.uiState.value.paymentErrorMessage)
            assertEquals(250.0, viewModel.uiState.value.totalPaid, 0.001)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loansViewModel_deletePaymentIdempotencyAndDeleteLoanClearsAllOpenModals() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val loanId = loanRepo.insertLoan(
                Loan(
                    personName = "Valeria",
                    amountLent = 200.0,
                    amountToReceive = 200.0,
                    payments = listOf(
                        LoanPayment(amount = 50.0, paidAt = "2026-11-25 10:00", savingCategory = "Emergencias"),
                        LoanPayment(amount = 50.0, paidAt = "2026-11-26 10:00", savingCategory = "Emergencias")
                    )
                )
            )
            val viewModel = LoansViewModel(
                LoanUseCase(
                    loanRepository = loanRepo,
                    savingsRepository = savingsRepo,
                    loanMutex = Mutex(),
                    currentDateProvider = { "2026-11-26" }
                )
            )
            advanceUntilIdle()

            val loan = viewModel.uiState.value.loans.single { it.id == loanId }
            val firstPayment = loan.payments.first()

            // Rapid double-tap on confirmDeleteLoanPayment
            val delGate = CompletableDeferred<Unit>()
            loanRepo.deletePaymentGate = delGate
            viewModel.requestDeleteLoanPayment(loan, firstPayment)
            viewModel.confirmDeleteLoanPayment()
            viewModel.confirmDeleteLoanPayment()
            testScheduler.runCurrent()

            assertEquals("Double-tap confirmDeleteLoanPayment must execute once", 1, loanRepo.deletePaymentCallCount)
            delGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(LoansViewModel.MSG_PAYMENT_DELETED, viewModel.uiState.value.toastMessage)

            // Open every modal referencing this loan, then confirmDeleteLoan -> all modals MUST close atomically
            val refreshedLoan = viewModel.uiState.value.loans.single()
            viewModel.openScheduleDialog(refreshedLoan)
            viewModel.openPaymentDialog(refreshedLoan)
            viewModel.openPaymentHistory(refreshedLoan)
            viewModel.requestDeleteLoanPayment(refreshedLoan, refreshedLoan.payments.single())
            viewModel.requestDeleteLoan(refreshedLoan)
            viewModel.confirmDeleteLoan()
            advanceUntilIdle()

            val finalState = viewModel.uiState.value
            assertNull(finalState.loanToDelete)
            assertNull(finalState.editingScheduleLoan)
            assertNull(finalState.payingLoan)
            assertNull(finalState.editingPaymentId)
            assertNull(finalState.historyLoanId)
            assertNull(finalState.paymentToDelete)
            assertTrue(finalState.loans.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.kt` lines 382–393 — `addScheduleInstallment` resets `persistedId`
     * to `0L` whenever the user changes the `dueDate` of an existing installment):
     * Editing only the due date of a partially-paid installment (`id = 10`, `paymentAmount = 40.0`)
     * drops `persistedId` to `0L`, causing Room's `syncInstallments` to DELETE row `10` and INSERT
     * a new ID, permanently orphaning all `LoanPayment.installmentId = 10` references!
     */
    @Test
    fun loansViewModel_editScheduleInstallmentDueDate_mustPreservePersistedIdForPartiallyPaidInstallment() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val loanId = loanRepo.insertLoan(
                Loan(
                    personName = "Elena",
                    amountLent = 100.0,
                    amountToReceive = 100.0,
                    installments = listOf(
                        LoanInstallment(id = 10L, dueDate = "2026-12-01", expectedAmount = 100.0, paymentAmount = 40.0)
                    ),
                    payments = listOf(
                        LoanPayment(id = 1L, amount = 40.0, paidAt = "2026-11-26 10:00", savingCategory = "Emergencias", installmentId = 10L)
                    )
                )
            )
            val viewModel = LoansViewModel(
                LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" })
            )
            advanceUntilIdle()

            val loan = viewModel.uiState.value.loans.single { it.id == loanId }
            viewModel.openScheduleDialog(loan)
            val draftId = viewModel.uiState.value.scheduleInstallments.single().draftId
            viewModel.startEditingScheduleInstallment(draftId)
            viewModel.onScheduleDateChange("2026-12-20")
            viewModel.addScheduleInstallment()

            val editedDraft = viewModel.uiState.value.scheduleInstallments.single()
            assertEquals(
                "Changing dueDate of a persisted partially-paid installment must preserve persistedId=10L so LoanPayment.installmentId is not orphaned",
                10L,
                editedDraft.persistedId
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.kt` lines 902–926 — `saveLoan` in `LoanCreationPaymentMode.NO_DATE`
     * falls through to `else -> state.draftInstallments` instead of `emptyList()`):
     * If `draftInstallments` was populated before selecting `NO_DATE`, saving the loan still persists
     * those stale installments despite the user explicitly selecting "Sin fecha de pago prevista"!
     */
    @Test
    fun loansViewModel_saveLoanInNoDateMode_mustIgnoreStaleDraftInstallments() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val viewModel = LoansViewModel(
                LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" })
            )
            advanceUntilIdle()

            viewModel.onPersonNameChange("Sebastián")
            viewModel.onAmountLentChange("200")
            viewModel.onDraftInstallmentDateChange("2026-12-10")
            viewModel.onDraftInstallmentAmountChange("200")
            viewModel.addDraftInstallment()

            // User explicitly chooses NO_DATE mode before saving
            viewModel.onCreationPaymentModeSelected(LoanCreationPaymentMode.NO_DATE)
            viewModel.saveLoan()
            advanceUntilIdle()

            val created = loanRepo.loansFlow.value.single()
            assertTrue(
                "Saving in NO_DATE mode must persist zero installments, not stale draftInstallments",
                created.installments.isEmpty()
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.kt` lines 774–815 & 902–910 + `AddLoanDialog.kt` line 219):
     * When editing a loan that has a scheduled installment, `AddLoanDialog(isEditing = true)` hides
     * schedule editing controls, yet `LoansViewModel.saveLoan()` still submits the unchanged
     * `draftInstallments` (summing to the OLD `amountToReceive`) to `updateLoan`. Changing
     * `amountToReceive` from `$200` to `$250` therefore ALWAYS fails with a schedule sum error
     * that the user cannot fix from the edit dialog!
     */
    @Test
    fun loansViewModel_editScheduledLoanAmountToReceive_mustNotTrapUserWithHiddenScheduleMismatch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val loanId = loanRepo.insertLoan(
                Loan(
                    personName = "Natalia",
                    amountLent = 200.0,
                    amountToReceive = 200.0,
                    installments = listOf(LoanInstallment(dueDate = "2026-12-15", expectedAmount = 200.0))
                )
            )
            val viewModel = LoansViewModel(
                LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" })
            )
            advanceUntilIdle()

            viewModel.startEditingLoan(viewModel.uiState.value.loans.single { it.id == loanId })
            viewModel.onAmountLentChange("250")
            viewModel.onAmountToReceiveChange("250")
            viewModel.saveLoan()
            advanceUntilIdle()

            assertNull(
                "Editing amountToReceive on a single-date/scheduled loan must succeed or rebalance without trapping user; error=${viewModel.uiState.value.errorMessage}",
                viewModel.uiState.value.errorMessage
            )
            assertEquals(250.0, loanRepo.loansFlow.value.single().amountToReceive, 0.001)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // 5. Compose UI Adversarial Tests & i18n Parity
    // =========================================================================

    /**
     * EXPOSES BUG (`LoanPaymentHistorySheet.kt` lines 463–467 — unguarded `BigDecimal.valueOf(Double)`):
     * `Loan.kt` guards every `Double` with `isFinite()`, but `PaymentHistoryRow` calls
     * `BigDecimal.valueOf(payment.amount)` and `BigDecimal.valueOf(loan.amountToReceive)` directly,
     * throwing `NumberFormatException` and crashing the Activity if any payment row has `NaN`/`Infinity`.
     */
    @Test
    fun compose_paymentHistorySheet_nonFiniteCorruptPaymentAmount_mustRenderWithoutCrashing() {
        val corruptLoan = Loan(
            id = 7L,
            personName = "Corrupt Data",
            amountLent = 100.0,
            amountToReceive = 100.0,
            payments = listOf(
                LoanPayment(
                    id = 1L,
                    loanId = 7L,
                    amount = Double.NaN,
                    paidAt = "2026-11-26 10:00",
                    savingCategory = "Emergencias"
                )
            )
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoanPaymentHistorySheet(
                    loan = corruptLoan,
                    currency = usd,
                    todayIso = "2026-11-26",
                    onRegisterNewPaymentClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()
    }

    /**
     * EXPOSES BUG (`LoansView.kt` lines 358–387 + `LoansViewModel.kt` lines 548–556):
     * When deleting a payment from `LoanPaymentHistorySheet` fails because its credited savings back
     * an existing expense, `LoansViewModel` puts the error into `errorMessage`/`paymentErrorMessage`,
     * which neither `LoansView` nor `LoanPaymentHistorySheet` renders — silently swallowing the failure!
     */
    @Test
    fun compose_loansView_deleteProtectedPaymentFromHistory_mustDisplayErrorToUser() {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val loanUseCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            expenseRepository = expenseRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val (loanId, paymentId) = runBlocking {
            val id = loanRepo.insertLoan(Loan(personName = "Protegido", amountLent = 300.0, amountToReceive = 300.0))
            loanUseCase.recordLoanPayment(id, "300", "2026-11-26 10:00", "Emergencias")
            expenseRepo.insertExpense(
                Expense(
                    amount = 200.0,
                    date = "2026-11-26",
                    description = "Compra",
                    category = "Hogar",
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = "Emergencias"
                )
            )
            id to loanRepo.loansFlow.value.single().payments.single().id
        }
        val viewModel = LoansViewModel(loanUseCase)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // Open payment history sheet and attempt to delete the protected payment
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_payment_history_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_options_payment_history_$paymentId")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("btn_delete_payment_history_$paymentId")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("dialog_delete_loan_payment").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_delete_loan_payment").performClick()
        composeTestRule.waitForIdle()

        // The rejection reason MUST be visible to the user, not silently discarded in hidden state
        composeTestRule.onNodeWithText(
            "No se puede eliminar el pago porque el ahorro acreditado respalda gastos o préstamos registrados",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun compose_recordLoanPaymentDialog_overflowHintPayFullAndSettledCardTransition() {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val loanUseCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" },
            currentDateTimeProvider = { "2026-11-26 15:00" }
        )
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(
                    personName = "Gabriel",
                    amountLent = 300.0,
                    amountToReceive = 300.0,
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-12-01", expectedAmount = 100.0),
                        LoanInstallment(dueDate = "2026-12-15", expectedAmount = 200.0)
                    )
                )
            )
        }
        val viewModel = LoansViewModel(loanUseCase)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // Open payment dialog -> type $150 (> first installment $100, < total $300) -> overflow hint appears
        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_payment_amount").performScrollTo().performTextInput("150")
        composeTestRule.onNodeWithTag("text_payment_overflow_hint").performScrollTo().assertIsDisplayed()

        // Tap "Pagar total" -> fills $300 and displays will-settle banner
        composeTestRule.onNodeWithTag("btn_payment_full_amount").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_payment_will_settle").performScrollTo().assertIsDisplayed()

        // Pick destination saving category and save -> card transitions to settled & hides register payment button
        composeTestRule.onNodeWithTag("selector_payment_saving_category").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("payment_saving_option_Emergencias").performClick()
        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_settled_badge_$loanId", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").assertDoesNotExist()
    }

    @Test
    fun compose_paymentHistorySheet_stress30PaymentsAndSettledHidesRegisterButton() {
        val manyPayments = (1..30).map { idx ->
            LoanPayment(
                id = idx.toLong(),
                loanId = 9L,
                amount = 10.0,
                paidAt = String.format(Locale.ROOT, "2026-11-%02d 10:00", ((idx - 1) % 28) + 1),
                savingCategory = "Fondo de Emergencias Extendido"
            )
        }
        val settledLoan = Loan(
            id = 9L,
            personName = "Alejandro " + "Z".repeat(120),
            amountLent = 300.0,
            amountToReceive = 300.0,
            payments = manyPayments
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoanPaymentHistorySheet(
                    loan = settledLoan,
                    currency = usd,
                    todayIso = "2026-11-26",
                    onRegisterNewPaymentClick = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_payment_history_count", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_history_register_payment").assertDoesNotExist()
    }

    @Test
    fun i18n_newPaymentHistoryAndDialogKeys_areCompleteAndFormatSafeInAllFourLanguages() {
        val plainKeys = listOf(
            "common.delete_record_confirmation",
            "common.delete_action",
            "common.cancel_action",
            "loans.actions.register_payment",
            "loans.card.settled_badge",
            "loans.schedule.options_content_desc",
            "loans.schedule.edit_installment",
            "loans.schedule.update_installment_button",
            "loans.schedule.discard_dialog_title",
            "loans.schedule.discard_dialog_message",
            "loans.schedule.discard_cancel",
            "loans.schedule.discard_confirm",
            "loans.schedule.paid_label",
            "loans.add_dialog.payment_plan_label",
            "loans.add_dialog.payment_mode_none",
            "loans.add_dialog.payment_mode_single",
            "loans.add_dialog.payment_mode_installments",
            "loans.add_dialog.single_due_date_label",
            "loans.options.payment_history",
            "loans.history_sheet.payments_section_title",
            "loans.history_sheet.payment_singular",
            "loans.history_sheet.payment_plural",
            "loans.history_sheet.empty_message",
            "loans.history_sheet.register_new_payment",
            "loans.history_sheet.debt_payment_tag",
            "loans.history_sheet.full_payment_badge",
            "loans.history_sheet.partial_payment_badge",
            "loans.history_sheet.edit_payment",
            "loans.history_sheet.delete_payment",
            "loans.edit_dialog.discard_dialog_title",
            "loans.edit_dialog.discard_dialog_message",
            "loans.payment_dialog.title",
            "loans.payment_dialog.edit_title",
            "loans.payment_dialog.target_installment_label",
            "loans.payment_dialog.single_scheduled_prefix",
            "loans.payment_dialog.overflow_installment_hint",
            "loans.payment_dialog.optional_new_due_date_toggle",
            "loans.payment_dialog.amount_label",
            "loans.payment_dialog.pay_full_button",
            "loans.payment_dialog.datetime_label",
            "loans.payment_dialog.saving_destination_label",
            "loans.payment_dialog.saving_destination_placeholder",
            "loans.payment_dialog.auto_credited_tag",
            "loans.payment_dialog.will_settle_hint",
            "loans.payment_dialog.new_due_date_label",
            "loans.payment_dialog.partial_schedule_title",
            "loans.payment_dialog.schedule_keep",
            "loans.payment_dialog.schedule_add",
            "loans.payment_dialog.schedule_adjust",
            "loans.payment_dialog.remainder_due_date_label",
            "loans.payment_dialog.save_button",
            "loans.messages.payment_saved",
            "loans.messages.payment_updated",
            "loans.messages.payment_deleted"
        )

        val singleStringFormats = listOf(
            "loans.payment_dialog.remaining_balance_hint",
            "loans.payment_dialog.next_installment_label"
        )
        val singleIntFormats = listOf(
            "loans.history_sheet.paid_percent_label",
            "loans.payment_dialog.installment_number_prefix"
        )
        val doubleIntFormats = listOf(
            "loans.history_sheet.installments_progress",
            "loans.history_sheet.installment_of_total"
        )
        val tripleStringFormats = listOf(
            "loans.payment_dialog.installment_detail"
        )

        listOf("es", "en", "de", "pt").forEach { lang ->
            i18nManager.setLanguage(lang)
            plainKeys.forEach { key ->
                val value = i18nManager.t(key)
                assertTrue("[$lang] Missing or blank key '$key'", value.isNotBlank() && value != key)
            }
            singleStringFormats.forEach { key ->
                val tpl = i18nManager.t(key)
                assertTrue("[$lang] Missing template '$key'", tpl.isNotBlank() && tpl != key)
                val out = String.format(tpl, "$100.00")
                assertTrue("[$lang] Template '$key' must interpolate argument; got '$out'", out.contains("$100.00"))
            }
            singleIntFormats.forEach { key ->
                val tpl = i18nManager.t(key)
                assertTrue("[$lang] Missing template '$key'", tpl.isNotBlank() && tpl != key)
                val out = String.format(tpl, 75)
                assertTrue("[$lang] Template '$key' must interpolate int; got '$out'", out.contains("75"))
            }
            doubleIntFormats.forEach { key ->
                val tpl = i18nManager.t(key)
                assertTrue("[$lang] Missing template '$key'", tpl.isNotBlank() && tpl != key)
                val out = String.format(tpl, 2, 5)
                assertTrue("[$lang] Template '$key' must interpolate both ints; got '$out'", out.contains("2") && out.contains("5"))
            }
            tripleStringFormats.forEach { key ->
                val tpl = i18nManager.t(key)
                assertTrue("[$lang] Missing template '$key'", tpl.isNotBlank() && tpl != key)
                val out = String.format(tpl, "$50", "$100", "$150")
                assertTrue(
                    "[$lang] Template '$key' must interpolate all 3 args; got '$out'",
                    out.contains("$50") && out.contains("$100") && out.contains("$150")
                )
            }
        }
    }

    // =========================================================================
    // 6. Second-Pass Deep Adversarial Analysis (Edit/Update Desync, Lifecycle & Cross-Module Races)
    // =========================================================================

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 564–595 — `updateLoanPayment` after `SetNewDueDate`
     * desynchronizes schedule remaining balance and hides due badge on unpaid loans):
     * 1. Loan of `$300` has no initial installments.
     * 2. Payment #1 (`$100`) with `SetNewDueDate("2026-12-20")` creates 1 installment of `$200`.
     * 3. User edits Payment #1 from `$100` to `$200` (`KeepCurrent`): `updateLoanPayment` applies `$200`
     *    against the `$200` installment, marking the installment 100% paid (`isPaid == true`) and
     *    hiding the due badge (`calculateLoanDueBadgeInfo == null`) EVEN THOUGH `$100` IS STILL OWED!
     */
    @Test
    fun updateLoanPayment_afterSetNewDueDateOnUnscheduledLoan_mustKeepScheduleAlignedWithPendingBalance() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(personName = "Renata", amountLent = 300.0, amountToReceive = 300.0, installments = emptyList())
        )
        assertTrue(
            useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Emergencias",
                scheduleOption = PartialPaymentScheduleOption.SetNewDueDate("2026-12-20")
            ) is LoanResult.Success
        )
        val paymentId = loanRepo.loansFlow.value.single().payments.single().id

        // Edit Payment #1 from $100 -> $200 (still $100 pending on the $300 loan!)
        val updateRes = useCase.updateLoanPayment(
            loanId = loanId,
            paymentId = paymentId,
            amountInput = "200",
            paidAtInput = "2026-11-26 11:00",
            savingCategoryInput = "Emergencias",
            scheduleOption = PartialPaymentScheduleOption.KeepCurrent
        )
        assertTrue(updateRes is LoanResult.Success)

        val updated = loanRepo.loansFlow.value.single()
        assertEquals(100.0, updated.pendingAmount, 0.001)

        val scheduledRemaining = updated.installments
            .fold(BigDecimal.ZERO) { acc, i -> acc.add(BigDecimal.valueOf(i.remainingAmount)) }
        assertEquals(
            "After editing Payment #1 to \$200 on a \$300 loan, scheduled remaining must be \$100, not \$0",
            0,
            scheduledRemaining.compareTo(BigDecimal("100.00"))
        )
        assertNotNull(
            "Unpaid loan with scheduled due date '2026-12-20' must still display due badge",
            calculateLoanDueBadgeInfo(updated.installments, "2026-11-26")
        )
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` lines 879–931 & 952 — `updateLoan` with `installmentInputs = emptyList()`
     * wipes already-paid installments and orphans `LoanPayment.installmentId`):
     * Calling `updateLoan` with an empty schedule on a loan that already has a paid installment (`id = 1`)
     * passes `validateScheduleForLoan` (line 952 returns `emptyList() to null`) and deletes every
     * installment row in Room, breaking `LoanPayment.installmentId` history tags!
     */
    @Test
    fun updateLoan_withEmptyInstallmentsOnPartiallyPaidLoan_mustNotWipePaidInstallments() = runBlocking {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val useCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            loanMutex = Mutex(),
            currentDateProvider = { "2026-11-26" }
        )

        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Bruno",
                amountLent = 300.0,
                amountToReceive = 300.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 100.0),
                    LoanInstallment(dueDate = "2026-12-15", expectedAmount = 200.0)
                )
            )
        )
        assertTrue(
            useCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Emergencias"
            ) is LoanResult.Success
        )
        val paidInstallmentId = loanRepo.loansFlow.value.single().payments.single().installmentId
        assertNotNull(paidInstallmentId)

        val res = useCase.updateLoan(
            loanId = loanId,
            personNameInput = "Bruno Editado",
            amountLentInput = "300",
            amountToReceiveInput = "300",
            installmentInputs = emptyList()
        )
        if (res is LoanResult.Success) {
            val after = loanRepo.loansFlow.value.single()
            assertTrue(
                "updateLoan must NOT delete paid installment referenced by LoanPayment.installmentId=$paidInstallmentId",
                after.installments.any { it.id == paidInstallmentId }
            )
        } else {
            assertTrue(res is LoanResult.Error)
        }
    }

    /**
     * Cross-module concurrency stress test (`ExpenseUseCase` vs `LoanUseCase.saveLoan` vs `LoanUseCase.deleteLoanPayment`
     * sharing `FinancialAllocationLock`-style `Mutex`):
     * Starts with `$500` net savings in `"Viajes"` coming EXCLUSIVELY from a loan repayment (`0` rows in `savings`).
     * Fires 30 concurrent coroutines attempting to spend `$200`, lend `$200`, or delete the `$500` payment.
     * Invariant: `inflows >= outflows` (`available >= 0`) MUST hold at the end — total outflows can NEVER exceed inflows!
     */
    @Test
    fun concurrency_crossModuleRace_expenseLoanAndDeletePaymentOnRepaymentBackedSavings_neverOverdraws() = runTest {
        val loanRepo = AdversarialLoanRepo()
        val savingsRepo = AdversarialSavingsRepo()
        val expenseRepo = AdversarialExpenseRepo()
        val sharedMutex = Mutex()

        val loanUseCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            expenseRepository = expenseRepo,
            loanMutex = sharedMutex,
            currentDateProvider = { "2026-11-26" }
        )
        val expenseUseCase = ExpenseUseCase(
            repository = expenseRepo,
            budgetRepository = null,
            savingsRepository = savingsRepo,
            loanRepository = loanRepo,
            expenseMutex = sharedMutex
        )

        val seedLoanId = loanRepo.insertLoan(Loan(personName = "Origen", amountLent = 500.0, amountToReceive = 500.0))
        assertTrue(loanUseCase.recordLoanPayment(seedLoanId, "500", "2026-11-26 09:00", "Viajes") is LoanResult.Success)
        val seedPaymentId = loanRepo.loansFlow.value.single().payments.single().id

        val jobs = (1..30).map { idx ->
            async {
                when (idx % 3) {
                    0 -> expenseUseCase.saveExpense(
                        amountInput = "200",
                        dateInput = "2026-11-26",
                        descriptionInput = "Race Exp $idx",
                        categoryInput = "General",
                        fundingSource = ExpenseFundingSource.SAVINGS,
                        savingCategoryInput = "Viajes"
                    )
                    1 -> loanUseCase.saveLoan(
                        personNameInput = "Race Loan $idx",
                        amountLentInput = "200",
                        amountToReceiveInput = "200",
                        savingCategoryInput = "Viajes"
                    )
                    else -> loanUseCase.deleteLoanPayment(seedLoanId, seedPaymentId)
                }
            }
        }
        jobs.forEach { it.await() }

        val finalInflows = calculateCategorySavingsInflows("Viajes", savingsRepo.savingsFlow.value, loanRepo.loansFlow.value)
        val finalOutflows = calculateCategorySavingsOutflows("Viajes", expenseRepo.expensesFlow.value, loanRepo.loansFlow.value)
        assertTrue(
            "Cross-module race overdrew 'Viajes'! inflows=$finalInflows < outflows=$finalOutflows",
            finalInflows >= finalOutflows
        )
    }

    /**
     * EXPOSES BUG (`LoansDatabase.kt` lines 219–237 — `reassignSavingCategory` asymmetric nulling on blank `newName`):
     * Calling `reassignSavingCategory("Emergencias", "")` sets `loans.savingCategory = null` (turning
     * savings-backed loans into personal loans) while leaving `loan_payments.savingCategory = "Emergencias"`!
     */
    @Test
    fun room_reassignSavingCategoryWithBlankNewName_mustNotLeaveLoansAndPaymentsOutOfSync() = runBlocking {
        val roomDb = Room.inMemoryDatabaseBuilder(context, LoansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val loanId = repo.insertLoan(
                Loan(personName = "Asimétrico", amountLent = 200.0, amountToReceive = 200.0, savingCategory = "Emergencias")
            )
            repo.recordPayment(
                payment = LoanPayment(loanId = loanId, amount = 200.0, paidAt = "2026-11-26 10:00", savingCategory = "Emergencias"),
                updatedInstallments = emptyList()
            )

            repo.reassignSavingCategory("Emergencias", "   ")
            val loaded = repo.getAllLoans().first().single()
            assertEquals(
                "Blank newCategoryName must be a no-op or update symmetrically; loan='${loaded.savingCategory}' vs payment='${loaded.payments.single().savingCategory}'",
                loaded.savingCategory,
                loaded.payments.single().savingCategory
            )
        } finally {
            roomDb.close()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.kt` lines 574–576 & 608–632 — `paymentAdjustedNextAmountInput` goes stale):
     * Selecting `ADJUST_NEXT_INSTALLMENT` when `paymentAmountInput = "50"` prefills `paymentAdjustedNextAmountInput`
     * with `"50"`. Increasing `paymentAmountInput` to `"260"` on a `$300` loan reduces `remainingAfter` to `$40`,
     * yet `paymentAdjustedNextAmountInput` stays `"50"`, causing `saveLoanPayment()` to fail with
     * `"El monto de la siguiente cuota supera el saldo pendiente"`!
     */
    @Test
    fun loansViewModel_changingPaymentAmountAfterSelectingAdjustNextInstallment_mustRefreshStaleAdjustedPrefill() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val savingsRepo = AdversarialSavingsRepo()
            val loanId = loanRepo.insertLoan(
                Loan(
                    personName = "Iván",
                    amountLent = 300.0,
                    amountToReceive = 300.0,
                    savingCategory = "Emergencias",
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-12-01", expectedAmount = 100.0),
                        LoanInstallment(dueDate = "2026-12-20", expectedAmount = 200.0)
                    )
                )
            )
            val viewModel = LoansViewModel(
                LoanUseCase(loanRepo, savingsRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" }, currentDateTimeProvider = { "2026-11-26 10:00" })
            )
            advanceUntilIdle()

            viewModel.openPaymentDialog(viewModel.uiState.value.loans.single { it.id == loanId })
            viewModel.onPaymentAmountChange("50")
            viewModel.onPartialScheduleActionSelected(PartialScheduleAction.ADJUST_NEXT_INSTALLMENT)
            assertEquals("50", viewModel.uiState.value.paymentAdjustedNextAmountInput)

            // User increases payment amount to $260 -> only $40 remains on the entire loan!
            viewModel.onPaymentAmountChange("260")
            viewModel.saveLoanPayment()
            advanceUntilIdle()

            assertNull(
                "Auto-prefilled paymentAdjustedNextAmountInput went stale ('50' > remaining \$40) and broke saveLoanPayment: ${viewModel.uiState.value.paymentErrorMessage}",
                viewModel.uiState.value.paymentErrorMessage
            )
            assertEquals(40.0, viewModel.uiState.value.loans.single().pendingAmount, 0.001)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansViewModel.kt` lines 834–844 — `confirmDeleteLoan` forgets to clear `editingLoanId`):
     * `confirmDeleteLoan` clears `editingScheduleLoan`, `payingLoan`, `editingPaymentId`, `historyLoanId`,
     * and `paymentToDelete`, but leaves `editingLoanId` pointing to the deleted loan ID!
     */
    @Test
    fun loansViewModel_confirmDeleteLoanWhileEditingSameLoan_mustClearEditingLoanId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loanRepo = AdversarialLoanRepo()
            val loanId = loanRepo.insertLoan(Loan(personName = "fantasma", amountLent = 100.0, amountToReceive = 100.0))
            val viewModel = LoansViewModel(LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" }))
            advanceUntilIdle()

            val loan = viewModel.uiState.value.loans.single { it.id == loanId }
            viewModel.startEditingLoan(loan)
            assertEquals(loanId, viewModel.uiState.value.editingLoanId)

            viewModel.requestDeleteLoan(loan)
            viewModel.confirmDeleteLoan()
            advanceUntilIdle()

            assertNull(
                "Deleting the loan currently in edit mode must clear editingLoanId, not leave dangling ID=$loanId",
                viewModel.uiState.value.editingLoanId
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * EXPOSES BUG (`LoansView.kt` line 82 vs `LoansViewModel.kt` — missing `SavedStateHandle` split-brain on process recreation):
     * `showAddDialog` is stored in `rememberSaveable`, whereas `editingLoanId` lives only in in-memory
     * `LoansViewModel` state without `SavedStateHandle`. When the process/ViewModel is recreated while
     * editing an existing loan, `showAddDialog` remains `true` while `editingLoanId` resets to `null`,
     * silently morphing "Editar préstamo" into "Registrar préstamo" and creating a DUPLICATE loan!
     */
    @Test
    fun compose_loansView_recreatingViewModelWhileEditingLoan_mustNotMorphEditDialogIntoCreateDuplicateLoan() {
        val loanRepo = AdversarialLoanRepo()
        val loanId = runBlocking {
            loanRepo.insertLoan(Loan(personName = "Original", amountLent = 250.0, amountToReceive = 250.0))
        }
        val vm1 = LoansViewModel(LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" }))
        var activeVm by mutableStateOf(vm1)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = activeVm, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // Open Edit Loan dialog -> title is "Editar préstamo"
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_edit_loan_$loanId").performClick()
        composeTestRule.onNodeWithText("Editar préstamo").assertIsDisplayed()

        // Simulate ViewModel recreation after process death (Compose rememberSaveable keeps showAddDialog = true)
        composeTestRule.runOnUiThread {
            activeVm = LoansViewModel(LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" }))
        }
        composeTestRule.waitForIdle()

        // Either the dialog closes cleanly when edit target was lost, OR it restores "Editar préstamo" —
        // it must NEVER stay open as a blank "Registrar préstamo" dialog!
        val addCards = composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("card_add_loan_dialog")).fetchSemanticsNodes()
        if (addCards.isNotEmpty()) {
            composeTestRule.onNodeWithText("Editar préstamo").assertIsDisplayed()
        }
    }

    /**
     * EXPOSES BUG (`LoansView.kt` lines 279–286 + `LoansViewModel.kt` lines 858–865):
     * When `confirmDeleteLoan()` encounters an error, `LoansViewModel` closes `DeleteLoanConfirmationDialog`
     * (`loanToDelete = null`) and writes the message to `state.errorMessage`, which `LoansView` never
     * renders anywhere outside `AddLoanDialog` — leaving the user with zero visual feedback!
     */
    @Test
    fun compose_loansView_deleteLoanFailure_mustSurfaceErrorOnMainScreen() {
        val loanRepo = AdversarialLoanRepo().apply {
            throwOnDeleteLoan = IllegalStateException("Error crítico de almacenamiento al borrar préstamo")
        }
        val loanId = runBlocking {
            loanRepo.insertLoan(Loan(personName = "Inborrable", amountLent = 150.0, amountToReceive = 150.0))
        }
        val viewModel = LoansViewModel(LoanUseCase(loanRepo, loanMutex = Mutex(), currentDateProvider = { "2026-11-26" }))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            viewModel.requestDeleteLoan(viewModel.uiState.value.loans.single { it.id == loanId })
            viewModel.confirmDeleteLoan()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Error crítico de almacenamiento al borrar préstamo",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }
}
