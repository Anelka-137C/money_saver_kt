package com.example.money_saver_android.adversarial

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.local.LoansDatabase
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.RoomLoanRepository
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanInstallmentValidationResult
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.calculateLoanDueBadgeInfo
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.LoansView
import com.example.money_saver_android.ui.components.common.DatePickerField
import com.example.money_saver_android.viewmodel.LoansViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * Adversarial & Property-Based Test Suite for Story #16 (Loan Payment Schedule & Due Badges):
 * - Property-Based Domain Invariants (`calculateLoanDueBadgeInfo`, `saveLoan`, `formatLoanShortDate`)
 * - Calendar, Leap-Year, Unicode, Numeric Parsing & DST Midnight Timezone Edge Cases
 * - Persisted Overdue Installment Tampering & Duplicate `persistedId` Bypass Attacks
 * - Room Migration 1->2, SQLite ForeignKey CASCADE, Category Rename Preservation & 500-Item Stress Test
 * - ViewModel Idempotency, Error Recovery, State Consistency & Form Reactivity
 * - Compose UI Edge Cases (`DatePickerField` state sync, extreme names + badge layout, scrollable dialog, i18n parity)
 *
 * Strictly test-only file: production code is never modified.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LoanScheduleAdversarialTest {

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
    // Adversarial In-Memory Repository
    // =========================================================================

    private class AdversarialScheduleLoanRepo : LoanRepository {
        val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
        private var nextLoanId = 1L
        private var nextInstallmentId = 1L

        var replaceCallCount = 0
            private set
        var replaceGate: CompletableDeferred<Unit>? = null
        var throwOnReplace: Throwable? = null

        override fun getAllLoans(): Flow<List<Loan>> = loansFlow

        override suspend fun insertLoan(loan: Loan): Long {
            val id = if (loan.id > 0L) loan.id else nextLoanId++
            val savedInstallments = loan.installments
                .map { it.copy(id = nextInstallmentId++, loanId = id) }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = listOf(loan.copy(id = id, installments = savedInstallments)) + loansFlow.value
            return id
        }

        override suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {
            replaceCallCount++
            replaceGate?.await()
            throwOnReplace?.let { throw it }
            val savedInstallments = installments
                .map { it.copy(id = nextInstallmentId++, loanId = loanId) }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
            loansFlow.value = loansFlow.value.map {
                if (it.id == loanId) it.copy(installments = savedInstallments) else it
            }
        }
    }

    // =========================================================================
    // 1. Property-Based Domain Invariants
    // =========================================================================

    /**
     * Property: Over 200 randomized installment lists mixing valid past/today/future ISO dates,
     * malformed dates, blank strings, and unsorted order:
     * - Returns null iff there are zero valid ISO dates.
     * - `nextDueDate` is strictly the chronological minimum among valid ISO dates.
     * - `additionalCount == validCount - 1`.
     * - `isOverdue == (nextDueDate < todayIso)`.
     */
    @Test
    fun property_calculateLoanDueBadgeInfo_invariantsAcrossRandomizedInstallmentSets() {
        val rng = Random(16001L)
        val todayIso = "2026-11-26"
        val validPool = listOf(
            "2025-12-31", "2026-01-01", "2026-11-25", "2026-11-26",
            "2026-11-27", "2026-12-15", "2027-02-28", "2028-02-29"
        )
        val invalidPool = listOf(
            "", "   ", "2026-02-29", "2026-02-30", "2026-13-01",
            "2026-00-10", "26-11-2026", "not-a-date", "2026-11-26 10:00"
        )

        repeat(200) { iteration ->
            val count = rng.nextInt(0, 20)
            val expectedValid = mutableListOf<String>()
            val installments = (1..count).map { idx ->
                val rawDate = if (rng.nextInt(4) == 0) {
                    invalidPool[rng.nextInt(invalidPool.size)]
                } else {
                    val d = validPool[rng.nextInt(validPool.size)]
                    expectedValid.add(d)
                    if (rng.nextBoolean()) "  $d  " else d
                }
                LoanInstallment(
                    id = idx.toLong(),
                    loanId = 1L,
                    dueDate = rawDate,
                    expectedAmount = rng.nextInt(1, 10_000) / 100.0
                )
            }

            val badge = calculateLoanDueBadgeInfo(installments, todayIso)
            if (expectedValid.isEmpty()) {
                assertNull("Iteration $iteration expected null badge", badge)
            } else {
                assertNotNull("Iteration $iteration expected non-null badge", badge)
                val minDate = expectedValid.min()
                assertEquals("Iteration $iteration nextDueDate", minDate, badge!!.nextDueDate)
                assertEquals("Iteration $iteration additionalCount", expectedValid.size - 1, badge.additionalCount)
                assertEquals("Iteration $iteration isOverdue", minDate < todayIso, badge.isOverdue)
            }
        }
    }

    /**
     * Property: Over 150 randomized penny-exact partitions of `amountToReceive` into 1..8 installments:
     * - Any exact partition with dates >= today MUST succeed, persist sorted chronologically, and
     *   conserve exact BigDecimal sum == amountToReceive.
     * - Perturbing any installment by +0.01 or -0.01 MUST return `LoanResult.Error` and NOT persist.
     */
    @Test
    fun property_saveLoan_exactBigDecimalSumConservationAcrossRandomPartitions() = runBlocking {
        val rng = Random(16002L)
        val todayIso = "2026-11-26"
        val futureDates = listOf(
            "2026-11-26", "2026-11-30", "2026-12-01", "2026-12-15",
            "2027-01-10", "2027-03-01", "2028-02-29"
        )

        repeat(150) { iteration ->
            val repo = AdversarialScheduleLoanRepo()
            val useCase = LoanUseCase(repo, currentDateProvider = { todayIso })

            val parts = rng.nextInt(1, 9)
            val partCents = (1..parts).map { rng.nextInt(1, 50_000) }
            val totalCents = partCents.sum()
            val totalStr = BigDecimal(totalCents).movePointLeft(2).toPlainString()

            val inputs = partCents.map { cents ->
                LoanInstallmentInput(
                    dueDate = futureDates[rng.nextInt(futureDates.size)],
                    expectedAmountInput = BigDecimal(cents).movePointLeft(2).toPlainString()
                )
            }

            // Perturbed sum (+0.01) must fail and leave repo empty
            val perturbedInputs = inputs.toMutableList().apply {
                val bumped = BigDecimal(partCents[0] + 1).movePointLeft(2).toPlainString()
                this[0] = this[0].copy(expectedAmountInput = bumped)
            }
            val badResult = useCase.saveLoan(
                personNameInput = "Deudor $iteration",
                amountLentInput = totalStr,
                amountToReceiveInput = totalStr,
                installmentInputs = perturbedInputs
            )
            assertTrue("Iteration $iteration off-by-one-cent must fail", badResult is LoanResult.Error)
            assertTrue("Iteration $iteration repo must stay empty on error", repo.loansFlow.value.isEmpty())

            // Exact partition must succeed
            val okResult = useCase.saveLoan(
                personNameInput = "Deudor $iteration",
                amountLentInput = totalStr,
                amountToReceiveInput = totalStr,
                installmentInputs = inputs
            )
            assertTrue("Iteration $iteration exact partition must succeed: $okResult", okResult is LoanResult.Success)
            val persisted = repo.loansFlow.value.single().installments
            assertEquals(parts, persisted.size)
            for (i in 0 until persisted.size - 1) {
                assertTrue("Iteration $iteration installments must be sorted", persisted[i].dueDate <= persisted[i + 1].dueDate)
            }
            val actualSum = persisted.fold(BigDecimal.ZERO) { acc, inst -> acc.add(BigDecimal.valueOf(inst.expectedAmount)) }
            assertEquals(
                "Iteration $iteration sum conservation",
                0,
                actualSum.compareTo(BigDecimal(totalCents).movePointLeft(2))
            )
        }
    }

    /**
     * Property: `formatLoanShortDate` across all 4 supported languages (`es`, `en`, `de`, `pt`)
     * never throws on valid or corrupt inputs, and formats every valid ISO date into a short
     * localized date containing day and year (never leaving the raw `yyyy-MM-dd` ISO form).
     */
    @Test
    fun property_formatLoanShortDate_neverCrashesAndFormatsValidDatesAcrossAllSupportedLocales() {
        val rng = Random(16003L)
        val languages = listOf("es", "en", "de", "pt")
        val maxDayByMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        repeat(100) {
            val year = rng.nextInt(2020, 2035)
            val month = rng.nextInt(1, 13)
            val isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
            val maxDay = if (month == 2 && isLeap) 29 else maxDayByMonth[month - 1]
            val day = rng.nextInt(1, maxDay + 1)
            val iso = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

            languages.forEach { lang ->
                val formatted = formatLoanShortDate(iso, lang)
                assertTrue("[$lang] '$iso' formatted must contain year $year, got '$formatted'", formatted.contains(year.toString()))
                assertTrue("[$lang] '$iso' formatted must contain day $day, got '$formatted'", formatted.contains(day.toString()))
                assertNotEquals("[$lang] '$iso' must be formatted, not raw ISO", iso, formatted)
            }
        }

        // Corrupt inputs must never throw
        listOf("", "   ", "2026-02-30", "invalid", "2026-99-99", "\u0000").forEach { corrupt ->
            languages.forEach { lang ->
                val out = formatLoanShortDate(corrupt, lang)
                assertEquals(corrupt.trim { it.isWhitespace() }, out)
            }
        }
    }

    // =========================================================================
    // 2. Calendar Boundaries, Timezone DST Midnight & Numeric Parsing Attacks
    // =========================================================================

    @Test
    fun saveLoan_calendarBoundariesUnicodeDigitsAndEuropeanDecimals() = runBlocking {
        val repo = AdversarialScheduleLoanRepo()
        val useCase = LoanUseCase(repo, currentDateProvider = { "2026-11-26" })

        val invalidDates = listOf(
            "", "   ", "\u00A0", "2026-11-25", "2025-12-31",
            "2026-02-29", "2026-02-30", "2026-04-31", "2026-00-10", "2026-13-01",
            "2026-12-1", "2026-1-01", "2026/12/01", "01-12-2026",
            "2026-12-01 10:00", "\u200B2026-12-01", "٢٠٢٦-١٢-٠١"
        )
        invalidDates.forEach { badDate ->
            assertTrue(
                "validateInstallmentDraft must reject '$badDate'",
                useCase.validateInstallmentDraft(badDate, "100") is LoanInstallmentValidationResult.Error
            )
            assertTrue(
                "saveLoan must reject '$badDate'",
                useCase.saveLoan("Ana", "100", "100", installmentInputs = listOf(LoanInstallmentInput(badDate, "100")))
                    is LoanResult.Error
            )
        }

        val invalidAmounts = listOf("", "0", "0.00", "-1", "-0.01", "NaN", "Infinity", "100d", "100f", "0x1.0p10", "abc")
        invalidAmounts.forEach { badAmount ->
            assertTrue(
                "validateInstallmentDraft must reject amount '$badAmount'",
                useCase.validateInstallmentDraft("2026-12-01", badAmount) is LoanInstallmentValidationResult.Error
            )
        }
        assertTrue(repo.loansFlow.value.isEmpty())

        // Valid leap year date 2028-02-29 + European comma decimals summing exactly 250.00
        val ok = useCase.saveLoan(
            personNameInput = "Sofía",
            amountLentInput = "200",
            amountToReceiveInput = "250",
            installmentInputs = listOf(
                LoanInstallmentInput("2028-02-29", "125,50"),
                LoanInstallmentInput("2026-11-26", "124,50")
            )
        )
        assertTrue(ok is LoanResult.Success)
        assertEquals(2, repo.loansFlow.value.single().installments.size)
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` `isValidLoanIsoDate` & `formatLoanShortDate` omit UTC TimeZone):
     * `SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }` defaults to the
     * device's local `TimeZone`. In timezones where DST springs forward at `00:00:00` (e.g.
     * `America/Havana` on `2026-03-08`, `America/Santiago` on `2026-09-06`), midnight does not
     * exist on that calendar day, so strict parsing returns `null`!
     * Consequently:
     * - `validateInstallmentDraft` & `saveLoan` falsely reject a completely valid future calendar date.
     * - `calculateLoanDueBadgeInfo` drops the valid installment as corrupt.
     * - `formatLoanShortDate` fails to format the date and returns raw ISO text.
     */
    @Test
    fun dstMidnightTimezone_validCalendarDate_mustBeAcceptedCalculatedAndFormatted() = runBlocking {
        val originalTz = TimeZone.getDefault()
        try {
            // Chile (America/Santiago) springs forward at midnight 00:00 -> 01:00 on Sunday 2026-09-06
            // (and Cuba America/Havana on 2026-03-08)
            val dstCases = listOf(
                Triple("America/Havana", "2026-03-01", "2026-03-08"),
                Triple("America/Santiago", "2026-09-01", "2026-09-06"),
                Triple("America/Asuncion", "2024-10-01", "2024-10-06")
            )
            dstCases.forEach { (tzId, today, transitionDate) ->
                TimeZone.setDefault(TimeZone.getTimeZone(tzId))
                val repo = AdversarialScheduleLoanRepo()
                val useCase = LoanUseCase(repo, currentDateProvider = { today })

                val draftCheck = useCase.validateInstallmentDraft(transitionDate, "100")
                assertTrue(
                    "[$tzId] Valid calendar date $transitionDate must NOT be rejected under DST midnight; got $draftCheck",
                    draftCheck is LoanInstallmentValidationResult.Valid
                )

                val saveRes = useCase.saveLoan(
                    personNameInput = "Elena",
                    amountLentInput = "100",
                    amountToReceiveInput = "100",
                    installmentInputs = listOf(LoanInstallmentInput(transitionDate, "100"))
                )
                assertTrue("[$tzId] saveLoan must accept valid DST date $transitionDate; got $saveRes", saveRes is LoanResult.Success)

                val badge = calculateLoanDueBadgeInfo(
                    listOf(LoanInstallment(dueDate = transitionDate, expectedAmount = 100.0)),
                    todayIso = today
                )
                assertNotNull("[$tzId] Badge must not drop valid DST date $transitionDate", badge)
                assertEquals(transitionDate, badge?.nextDueDate)

                val formatted = formatLoanShortDate(transitionDate, "es")
                assertNotEquals("[$tzId] formatLoanShortDate must format $transitionDate", transitionDate, formatted)
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    /**
     * EXPOSES BUG (`LoanUseCase.kt` fail-open when `currentDateProvider` returns invalid/blank date):
     * `if (!allowPersistedPastDate && isValidLoanIsoDate(today) && trimmedDate < today)` skips the
     * past-date check completely if `today` is blank or malformed, silently allowing ancient dates
     * like `"1999-01-01"`!
     */
    @Test
    fun validateInstallmentDraft_corruptCurrentDateProvider_mustFailClosedNotAcceptAncientPastDate() {
        val useCase = LoanUseCase(AdversarialScheduleLoanRepo(), currentDateProvider = { "" })
        val result = useCase.validateInstallmentDraft("1999-01-01", "100")
        assertTrue(
            "Corrupt/blank currentDateProvider must fail closed rather than accepting 1999-01-01; got $result",
            result is LoanInstallmentValidationResult.Error
        )
    }

    // =========================================================================
    // 3. Schedule Update Tampering & Past-Date Bypass Attacks
    // =========================================================================

    /**
     * EXPOSES BUG (`LoanUseCase.kt` `validateScheduleForLoan` allows reusing the same `persistedId` multiple times):
     * A loan has ONE previously persisted overdue installment `(id = 1, dueDate = "2026-11-20", expectedAmount = 100.0)`.
     * `validateScheduleForLoan` looks up `existingById[input.persistedId]` without consuming/deduplicating IDs.
     * Submitting multiple installments carrying the same `persistedId = 1` bypasses the "no past dates"
     * invariant and persists brand-new installments with past due dates!
     */
    @Test
    fun updateLoanInstallments_duplicatePersistedId_mustNotBypassNoPastDatesRule() = runBlocking {
        val repo = AdversarialScheduleLoanRepo()
        val loanId = repo.insertLoan(
            Loan(
                personName = "Mateo",
                amountLent = 200.0,
                amountToReceive = 300.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-11-20", expectedAmount = 100.0),
                    LoanInstallment(dueDate = "2026-12-10", expectedAmount = 200.0)
                )
            )
        )
        val overdueId = repo.loansFlow.value.single().installments.first { it.dueDate == "2026-11-20" }.id
        val useCase = LoanUseCase(repo, currentDateProvider = { "2026-11-26" })

        // Attempt to clone the single persisted overdue installment twice to fill the full $300 in the past
        val bypassAttempt = useCase.updateLoanInstallments(
            loanId = loanId,
            installmentInputs = listOf(
                LoanInstallmentInput(dueDate = "2026-11-20", expectedAmountInput = "150", persistedId = overdueId),
                LoanInstallmentInput(dueDate = "2026-11-20", expectedAmountInput = "150", persistedId = overdueId)
            )
        )

        assertTrue(
            "Duplicating persistedId must NOT bypass the no-past-dates rule; got $bypassAttempt",
            bypassAttempt is LoanResult.Error
        )
        assertEquals(
            "Original installments must remain untouched on rejected bypass attempt",
            listOf("2026-11-20", "2026-12-10"),
            repo.loansFlow.value.single().installments.map { it.dueDate }
        )
    }

    @Test
    fun updateLoanInstallments_forgedPersistedIdAlteredPastDateAndUnknownLoan_areRejected() = runBlocking {
        val repo = AdversarialScheduleLoanRepo()
        val loanId = repo.insertLoan(
            Loan(
                personName = "Valeria",
                amountLent = 200.0,
                amountToReceive = 200.0,
                installments = listOf(LoanInstallment(dueDate = "2026-11-20", expectedAmount = 200.0))
            )
        )
        val overdueId = repo.loansFlow.value.single().installments.single().id
        val useCase = LoanUseCase(repo, currentDateProvider = { "2026-11-26" })

        // Non-existent loan
        assertTrue(useCase.updateLoanInstallments(999L, emptyList()) is LoanResult.Error)

        // Forged persistedId pointing to non-existent installment
        assertTrue(
            useCase.updateLoanInstallments(
                loanId,
                listOf(LoanInstallmentInput("2026-11-20", "200", persistedId = 9999L))
            ) is LoanResult.Error
        )

        // Altering a persisted overdue installment's date to a different past date must be rejected
        assertTrue(
            useCase.updateLoanInstallments(
                loanId,
                listOf(LoanInstallmentInput("2026-11-19", "200", persistedId = overdueId))
            ) is LoanResult.Error
        )
    }

    // =========================================================================
    // 4. Room SQLite Migration 1->2, ForeignKey CASCADE & Stress Persistence
    // =========================================================================

    @Test
    fun room_migration1To2_categoryRenamePreservesInstallments_andDeleteCascades_and500StressTest() = runBlocking {
        val dbFile = File(context.cacheDir, "adv_loans_mig_${System.nanoTime()}.db")
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

            // Stress test: persist 500 installments in reverse order on migrated loan
            val bulk = (500 downTo 1).map { idx ->
                val year = 2027 + (idx / 28)
                val day = (idx % 28) + 1
                LoanInstallment(
                    dueDate = String.format(Locale.ROOT, "%04d-01-%02d", year, day),
                    expectedAmount = 1.0
                )
            }
            repo.replaceInstallments(1L, bulk)

            val loaded = repo.getAllLoans().first().single()
            assertEquals(500, loaded.installments.size)
            for (i in 0 until loaded.installments.size - 1) {
                assertTrue(loaded.installments[i].dueDate <= loaded.installments[i + 1].dueDate)
            }

            // Renaming saving category via LoanDao.reassignSavingCategory MUST NOT wipe installments
            repo.reassignSavingCategory("Educación", "Universidad")
            val afterRename = repo.getAllLoans().first().single()
            assertEquals("Universidad", afterRename.savingCategory)
            assertEquals("Category rename must preserve all 500 installments", 500, afterRename.installments.size)

            // Deleting parent loan row MUST trigger ON DELETE CASCADE for loan_installments
            roomDb.openHelper.writableDatabase.execSQL("DELETE FROM loans WHERE id = 1")
            val cursor = roomDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM loan_installments")
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("ON DELETE CASCADE must remove orphaned installments", 0, it.getInt(0))
            }
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }

    // =========================================================================
    // 5. ViewModel Concurrency, Idempotency & Error Recovery
    // =========================================================================

    @Test
    fun loansViewModel_saveLoanSchedule_tripleTapIdempotencyAndStorageFailureRecovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = AdversarialScheduleLoanRepo()
            val loanId = repo.insertLoan(Loan(personName = "Diego", amountLent = 300.0, amountToReceive = 300.0))
            val viewModel = LoansViewModel(LoanUseCase(repo, currentDateProvider = { "2026-11-26" }))
            advanceUntilIdle()

            viewModel.openScheduleDialog(viewModel.uiState.value.loans.single { it.id == loanId })
            viewModel.onScheduleDateChange("2026-12-01")
            viewModel.onScheduleAmountChange("300")
            viewModel.addScheduleInstallment()

            // Rapid triple tap while replaceInstallments is suspended
            val gate = CompletableDeferred<Unit>()
            repo.replaceGate = gate
            viewModel.saveLoanSchedule()
            viewModel.saveLoanSchedule()
            viewModel.saveLoanSchedule()
            testScheduler.runCurrent()

            assertEquals("Only 1 replaceInstallments call may be in flight", 1, repo.replaceCallCount)
            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.editingScheduleLoan)
            assertEquals(1, repo.loansFlow.value.single().installments.size)

            // Storage failure must surface scheduleErrorMessage, keep dialog & drafts intact, and allow retry
            repo.replaceGate = null
            repo.throwOnReplace = IllegalStateException("SQLite I/O failure")
            viewModel.openScheduleDialog(viewModel.uiState.value.loans.single())
            val firstId = viewModel.uiState.value.scheduleInstallments.single().draftId
            viewModel.removeScheduleInstallment(firstId)
            viewModel.saveLoanSchedule()
            advanceUntilIdle()

            assertNotNull("Storage error must surface in scheduleErrorMessage", viewModel.uiState.value.scheduleErrorMessage)
            assertNotNull("Dialog must remain open on storage failure", viewModel.uiState.value.editingScheduleLoan)

            // Retry after storage recovers
            repo.throwOnReplace = null
            viewModel.saveLoanSchedule()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.editingScheduleLoan)
            assertNull(viewModel.uiState.value.scheduleErrorMessage)
            assertTrue(repo.loansFlow.value.single().installments.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loansViewModel_changingAmountToReceiveAfterAddingDrafts_rejectsMismatchedSaveAndPreservesDrafts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repo = AdversarialScheduleLoanRepo()
            val viewModel = LoansViewModel(LoanUseCase(repo, currentDateProvider = { "2026-11-26" }))
            advanceUntilIdle()

            viewModel.onPersonNameChange("Laura")
            viewModel.onAmountLentChange("500")
            viewModel.onDraftInstallmentDateChange("2026-12-01")
            viewModel.onDraftInstallmentAmountChange("500")
            viewModel.addDraftInstallment()

            // User increases amountToReceive after adding drafts
            viewModel.onAmountToReceiveChange("600")
            viewModel.saveLoan()
            advanceUntilIdle()

            assertTrue(repo.loansFlow.value.isEmpty())
            assertNotNull(viewModel.uiState.value.errorMessage)
            assertEquals("Draft installments must be preserved for user correction", 1, viewModel.uiState.value.draftInstallments.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // 6. Compose UI Edge Cases & i18n Parity
    // =========================================================================

    /**
     * EXPOSES BUG (`DatePickerField.kt` `rememberDatePickerState` stale state on external `value` change):
     * `rememberDatePickerState(initialSelectedDateMillis = ...)` only reads `initialSelectedDateMillis`
     * during initial composition. When `value` updates externally (e.g. `"2026-11-26"` -> `"2026-12-15"`),
     * opening the picker dialog and pressing OK without manually re-tapping the calendar emits the
     * stale initial date `"2026-11-26"` instead of `"2026-12-15"`!
     */
    @Test
    fun compose_datePickerField_externalValueUpdate_confirmEmitsUpdatedValueNotStaleInitialDate() {
        var currentDate by mutableStateOf("2026-11-26")
        var confirmedDate: String? = null

        composeTestRule.setContent {
            DatePickerField(
                value = currentDate,
                onValueChange = {
                    confirmedDate = it
                    currentDate = it
                },
                label = "Fecha de pago",
                minDateIso = "2026-11-26",
                fieldTestTag = "adv_date_picker_field"
            )
        }
        composeTestRule.waitForIdle()

        // External state change (e.g. ViewModel updates date or resets form)
        composeTestRule.runOnUiThread {
            currentDate = "2026-12-15"
        }
        composeTestRule.waitForIdle()

        // Open picker and immediately confirm OK -> must keep/emit "2026-12-15", NOT stale "2026-11-26"
        composeTestRule.onNodeWithTag("adv_date_picker_field").performClick()
        composeTestRule.onNodeWithTag("date_only_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_date_only_picker").performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Confirming DatePickerDialog after external value change must emit '2026-12-15', not stale initial '2026-11-26'",
            "2026-12-15",
            confirmedDate
        )
    }

    @Test
    fun compose_loanCard_extremePersonNameAndLargePlusNBadge_andScrollableAddDialog() {
        val repo = AdversarialScheduleLoanRepo()
        val loanId = runBlocking {
            val manyInstallments = (1..25).map { idx ->
                LoanInstallment(
                    dueDate = String.format(Locale.ROOT, "2026-12-%02d", idx),
                    expectedAmount = 40.0
                )
            }
            repo.insertLoan(
                Loan(
                    personName = "María " + "X".repeat(300),
                    amountLent = 1000.0,
                    amountToReceive = 1000.0,
                    installments = manyInstallments
                )
            )
        }
        val viewModel = LoansViewModel(LoanUseCase(repo, currentDateProvider = { "2026-11-26" }))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_due_badge_$loanId", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Vence: 1 Dic 2026 +24", useUnmergedTree = true).assertIsDisplayed()

        // Open AddLoanDialog, scroll all the way down to save button, then verify close button stays accessible
        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Test")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_close_loan_dialog").performClick()
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()
    }

    /**
     * Midnight rollover invariant on `AddLoanDialog`:
     * When the calendar date rolls over past midnight while `LoansViewModel` stays alive,
     * tapping "+ Registrar préstamo" and saving with `SINGLE_DATE` mode (default date untouched)
     * MUST prefill and persist the new `today` (`"2026-11-27"`), never yesterday (`"2026-11-26"`).
     */
    @Test
    fun compose_loansView_dayRolloverBeforeOpeningAddDialog_prefillsNewTodayNotYesterday() {
        var clockDate = "2026-11-26"
        val repo = AdversarialScheduleLoanRepo()
        val viewModel = LoansViewModel(LoanUseCase(repo, currentDateProvider = { clockDate }))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = viewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // Clock rolls past midnight before user opens AddLoanDialog
        clockDate = "2026-11-27"
        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Rollover")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("100")
        composeTestRule.onNodeWithTag("option_loan_payment_single").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Default single due date after midnight rollover must be updated to today '2026-11-27' so saving succeeds",
            1,
            repo.loansFlow.value.size
        )
        assertEquals("2026-11-27", repo.loansFlow.value.single().installments.single().dueDate)
    }

    @Test
    fun i18n_scheduleKeysAndFormatPlaceholders_areValidInAllFourLanguages() {
        val plainKeys = listOf(
            "loans.schedule.section_title",
            "loans.schedule.date_label",
            "loans.schedule.amount_label",
            "loans.schedule.add_button",
            "loans.schedule.empty_message",
            "loans.schedule.dialog_title",
            "loans.schedule.save_button",
            "loans.schedule.remove_content_desc",
            "loans.schedule.overdue_label",
            "loans.schedule.cancel_button",
            "loans.messages.schedule_updated"
        )

        listOf("es", "en", "de", "pt").forEach { lang ->
            i18nManager.setLanguage(lang)
            plainKeys.forEach { key ->
                val value = i18nManager.t(key)
                assertTrue("[$lang] '$key' must not be blank", value.isNotBlank())
                assertNotEquals("[$lang] '$key' fell back to raw key", key, value)
            }
            val duePrefix = i18nManager.t("loans.card.due_prefix")
            val formattedDue = String.format(duePrefix, "30 Jul 2026")
            assertTrue("[$lang] due_prefix must substitute date placeholder: '$formattedDue'", formattedDue.contains("30 Jul 2026"))

            val sumTemplate = i18nManager.t("loans.schedule.sum_label")
            val formattedSum = String.format(sumTemplate, "$ 100.00", "$ 200.00")
            assertTrue("[$lang] sum_label must substitute both placeholders: '$formattedSum'", formattedSum.contains("$ 100.00") && formattedSum.contains("$ 200.00"))
        }
    }
}
