package com.example.money_saver_android.adversarial

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomFinancialPeriodRepository
import com.example.money_saver_android.data.repository.RoomIncomeRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.data.repository.SharedPrefsBackupPreferenceRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.BackupReminderScheduleCalculator
import com.example.money_saver_android.domain.usecase.BackupResult
import com.example.money_saver_android.domain.usecase.BackupSnapshot
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.domain.usecase.RestoreParseResult
import com.example.money_saver_android.domain.usecase.RestoreResult
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.notifications.AndroidBackupReminderScheduler
import com.example.money_saver_android.notifications.BackupReminderReceiver
import com.example.money_saver_android.notifications.BackupReminderScheduler
import com.example.money_saver_android.ui.AppTab
import com.example.money_saver_android.ui.MainContainer
import com.example.money_saver_android.ui.SettingsView
import com.example.money_saver_android.ui.components.settings.BackupSectionCard
import com.example.money_saver_android.viewmodel.BackupStatus
import com.example.money_saver_android.viewmodel.FakeCurrencyRepository
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.RestoreStatus
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Google Drive Backup, Restore,
 * and 7-Day Backup Reminder Notifications.
 *
 * Scope strictly derived from `git status` / `git diff`:
 * - [BackupUseCase] (export, JSON parsing/validation, cross-DB restore, mutex serialization)
 * - [BackupReminderScheduleCalculator] (7-day activation, 8-hour daytime recurrence, DST & overflow boundaries)
 * - [SharedPrefsBackupPreferenceRepository] (boundary timestamps & corrupted SharedPreferences types)
 * - [SettingsViewModel] (backup/restore state machine, mutual exclusion, cancellation, scheduler faults)
 * - Room `replaceAll*` transactions across [ConfigDatabase], [AppDatabase], [BudgetDatabase], [SavingsDatabase]
 * - [BackupReminderReceiver] & [AndroidBackupReminderScheduler] (boot reschedule, permission gating, intent validation)
 * - Compose UI: [BackupSectionCard], [RestoreBackupConfirmationDialog], [SettingsView], [MainContainer]
 *
 * Rules:
 * - Zero production code edits. Failing tests intentionally document real defects discovered in the diff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndRestoreAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // Self-Contained Adversarial Test Doubles
    // =========================================================================

    private class AdversarialPeriodRepo(
        initial: List<FinancialPeriod> = emptyList()
    ) : FinancialPeriodRepository {
        val periodsFlow = MutableStateFlow(initial)
        var failOnReplace: Throwable? = null
        var onReplaceHook: (suspend () -> Unit)? = null

        override suspend fun savePeriod(period: FinancialPeriod): Long {
            val id = if (period.id == 0L) (periodsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L else period.id
            periodsFlow.value = periodsFlow.value.filterNot { it.id == id } + period.copy(id = id)
            return id
        }

        override suspend fun getPeriodForDate(date: String): FinancialPeriod? =
            periodsFlow.value.firstOrNull { it.startDate <= date && date <= it.endDate }

        override fun getLatestPeriod(): Flow<FinancialPeriod?> =
            periodsFlow.map { it.maxByOrNull { p -> p.endDate } }

        override fun getAllPeriods(): Flow<List<FinancialPeriod>> = periodsFlow

        override suspend fun deletePeriod(id: Long) {
            periodsFlow.value = periodsFlow.value.filterNot { it.id == id }
        }

        override suspend fun replaceAllPeriods(periods: List<FinancialPeriod>) {
            onReplaceHook?.invoke()
            failOnReplace?.let { throw it }
            periodsFlow.value = periods
        }
    }

    private class AdversarialIncomeRepo(
        initialIncomes: List<Income> = emptyList(),
        initialCategories: List<Category> = emptyList()
    ) : IncomeRepository {
        val incomesFlow = MutableStateFlow(initialIncomes)
        val categoriesFlow = MutableStateFlow(initialCategories)
        var failOnReplaceIncomes: Throwable? = null
        var failOnReplaceCategories: Throwable? = null

        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            incomesFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getIncomeById(id: Long): Income? =
            incomesFlow.value.firstOrNull { it.id == id }

        override suspend fun insertIncome(income: Income): Long {
            val id = if (income.id == 0L) (incomesFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L else income.id
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
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == category.name } + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == categoryName }
        }

        override suspend fun countIncomesForCategory(categoryName: String): Int =
            incomesFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories.filterNot { s -> s.name == subcategoryName }) else it
            }
        }

        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int =
            incomesFlow.value.count {
                it.category.equals(categoryName, ignoreCase = true) && it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override suspend fun preloadDefaultCategoriesIfNeeded() {}

        override suspend fun replaceAllIncomes(incomes: List<Income>) {
            failOnReplaceIncomes?.let { throw it }
            incomesFlow.value = incomes
        }

        override suspend fun replaceAllCategories(categories: List<Category>) {
            failOnReplaceCategories?.let { throw it }
            categoriesFlow.value = categories
        }
    }

    private class AdversarialBudgetRepo(
        initialBudgets: List<Budget> = emptyList(),
        initialCategories: List<Category> = emptyList()
    ) : BudgetRepository {
        val budgetsFlow = MutableStateFlow(initialBudgets)
        val categoriesFlow = MutableStateFlow(initialCategories)
        var failOnReplaceBudgets: Throwable? = null

        override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
            budgetsFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun insertBudget(budget: Budget): Long {
            val id = if (budget.id == 0L) (budgetsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L else budget.id
            budgetsFlow.value = budgetsFlow.value + budget.copy(id = id)
            return id
        }

        override suspend fun updateBudget(budget: Budget) {
            budgetsFlow.value = budgetsFlow.value.map { if (it.id == budget.id) budget else it }
        }

        override suspend fun getBudgetById(id: Long): Budget? =
            budgetsFlow.value.firstOrNull { it.id == id }

        override suspend fun deleteBudget(id: Long) {
            budgetsFlow.value = budgetsFlow.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == category.name } + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == categoryName }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories.filterNot { s -> s.name == subcategoryName }) else it
            }
        }

        override suspend fun countBudgetsForCategory(categoryName: String): Int =
            budgetsFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }

        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int =
            budgetsFlow.value.count {
                it.category.equals(categoryName, ignoreCase = true) && it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override suspend fun preloadDefaultCategoriesIfNeeded() {}

        override suspend fun replaceAllBudgets(budgets: List<Budget>) {
            failOnReplaceBudgets?.let { throw it }
            budgetsFlow.value = budgets
        }

        override suspend fun replaceAllCategories(categories: List<Category>) {
            categoriesFlow.value = categories
        }
    }

    private class AdversarialExpenseRepo(
        initialExpenses: List<Expense> = emptyList()
    ) : ExpenseRepository {
        val expensesFlow = MutableStateFlow(initialExpenses)
        var failOnReplace: Throwable? = null
        var onReplaceHook: (suspend () -> Unit)? = null
        val replaceCallCount = AtomicInteger(0)

        override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow

        override suspend fun insertExpense(expense: Expense) {
            expensesFlow.value = expensesFlow.value + expense
        }

        override suspend fun updateExpense(expense: Expense) {
            expensesFlow.value = expensesFlow.value.map { if (it.id == expense.id) expense else it }
        }

        override suspend fun deleteExpense(id: Long) {
            expensesFlow.value = expensesFlow.value.filterNot { it.id == id }
        }

        override suspend fun replaceAllExpenses(expenses: List<Expense>) {
            replaceCallCount.incrementAndGet()
            onReplaceHook?.invoke()
            failOnReplace?.let { throw it }
            expensesFlow.value = expenses
        }
    }

    private class AdversarialExpenseCategoryRepo(
        initialCategories: List<Category> = emptyList()
    ) : CategoryRepository {
        val categoriesFlow = MutableStateFlow(initialCategories)

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == category.name } + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
            }
        }

        override suspend fun replaceAllCategories(categories: List<Category>) {
            categoriesFlow.value = categories
        }
    }

    private class AdversarialSavingsRepo(
        initialSavings: List<Saving> = emptyList(),
        initialCategories: List<Category> = emptyList()
    ) : SavingsRepository {
        val savingsFlow = MutableStateFlow(initialSavings)
        val categoriesFlow = MutableStateFlow(initialCategories)
        var failOnReplaceSavings: Throwable? = null

        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow

        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            savingsFlow.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getSavingById(id: Long): Saving? =
            savingsFlow.value.firstOrNull { it.id == id }

        override suspend fun insertSaving(saving: Saving): Long {
            val id = if (saving.id == 0L) (savingsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L else saving.id
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
                it.category.equals(categoryName, ignoreCase = true) && it.subcategory.equals(subcategoryName, ignoreCase = true)
            }

        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow

        override suspend fun insertCategory(category: Category) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == category.name } + category
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            categoriesFlow.value = categoriesFlow.value.filterNot { it.name == categoryName }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            categoriesFlow.value = categoriesFlow.value.map {
                if (it.name == categoryName) it.copy(subcategories = it.subcategories.filterNot { s -> s.name == subcategoryName }) else it
            }
        }

        override suspend fun preloadDefaultCategoriesIfNeeded() {}

        override suspend fun replaceAllSavings(savings: List<Saving>) {
            failOnReplaceSavings?.let { throw it }
            savingsFlow.value = savings
        }

        override suspend fun replaceAllCategories(categories: List<Category>) {
            categoriesFlow.value = categories
        }
    }

    private class AdversarialLangPrefRepo(var lang: String = "es") : LanguagePreferenceRepository {
        override fun getLanguage(): String = lang
        override fun saveLanguage(languageCode: String) { lang = languageCode }
    }

    private class AdversarialCurrencyPrefRepo(var currency: String = "COP") : CurrencyPreferenceRepository {
        override fun getCurrency(): String = currency
        override fun saveCurrency(currencyCode: String) { currency = currencyCode }
    }

    private class AdversarialBackupPrefRepo(var timestamp: Long? = null) : BackupPreferenceRepository {
        override fun getLastBackupTimestamp(): Long? = timestamp
        override fun saveLastBackupTimestamp(timestampMillis: Long) { timestamp = timestampMillis }
    }

    private class AdversarialReminderScheduler(
        private val throwOnSchedule: Throwable? = null
    ) : BackupReminderScheduler {
        val scheduledTimestamps = mutableListOf<Long?>()
        var cancelCount = 0

        override fun scheduleNextReminder(lastBackupTimestamp: Long?) {
            throwOnSchedule?.let { throw it }
            scheduledTimestamps.add(lastBackupTimestamp)
        }

        override fun cancelReminders() {
            cancelCount++
        }
    }

    private data class BackupHarness(
        val periodRepo: AdversarialPeriodRepo = AdversarialPeriodRepo(),
        val incomeRepo: AdversarialIncomeRepo = AdversarialIncomeRepo(),
        val budgetRepo: AdversarialBudgetRepo = AdversarialBudgetRepo(),
        val expenseRepo: AdversarialExpenseRepo = AdversarialExpenseRepo(),
        val expenseCatRepo: AdversarialExpenseCategoryRepo = AdversarialExpenseCategoryRepo(),
        val savingsRepo: AdversarialSavingsRepo = AdversarialSavingsRepo(),
        val langRepo: AdversarialLangPrefRepo = AdversarialLangPrefRepo(),
        val currencyRepo: AdversarialCurrencyPrefRepo = AdversarialCurrencyPrefRepo(),
        val backupPrefs: AdversarialBackupPrefRepo = AdversarialBackupPrefRepo(),
        val mutex: Mutex = Mutex(),
        var nowMillis: Long = 1_764_100_000_000L
    ) {
        fun buildUseCase(): BackupUseCase = BackupUseCase(
            periodRepository = periodRepo,
            incomeRepository = incomeRepo,
            budgetRepository = budgetRepo,
            expenseRepository = expenseRepo,
            expenseCategoryRepository = expenseCatRepo,
            savingsRepository = savingsRepo,
            languagePreferenceRepository = langRepo,
            currencyPreferenceRepository = currencyRepo,
            backupPreferenceRepository = backupPrefs,
            allocationMutex = mutex,
            nowProvider = { nowMillis }
        )
    }

    private fun calMillis(tz: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long {
        return Calendar.getInstance(tz).apply {
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun buildMinimalValidBackupJson(
        schemaVersion: Any = 1,
        exportedAtMillis: Any = 1_764_200_000_000L,
        language: Any? = "es",
        currency: Any? = "COP",
        periods: JSONArray = JSONArray(),
        incomes: JSONArray = JSONArray(),
        budgets: JSONArray = JSONArray(),
        expenses: JSONArray = JSONArray(),
        savings: JSONArray = JSONArray(),
        incomeCategories: JSONArray = JSONArray(),
        budgetCategories: JSONArray = JSONArray(),
        expenseCategories: JSONArray = JSONArray(),
        savingCategories: JSONArray = JSONArray()
    ): String {
        val root = JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("exportedAtMillis", exportedAtMillis)
            put("preferences", JSONObject().apply {
                put("language", language ?: JSONObject.NULL)
                put("currency", currency ?: JSONObject.NULL)
            })
            put("periods", periods)
            put("incomes", incomes)
            put("budgets", budgets)
            put("expenses", expenses)
            put("savings", savings)
            put("categories", JSONObject().apply {
                put("incomes", incomeCategories)
                put("budget", budgetCategories)
                put("expenses", expenseCategories)
                put("savings", savingCategories)
            })
        }
        return root.toString()
    }

    // =========================================================================
    // SECTION 1: Property-Based & Adversarial Tests for BackupReminderScheduleCalculator
    // =========================================================================

    /**
     * Property-Based Test (500 deterministic trials across 9 diverse TimeZones including
     * half-hour, 45-minute, and DST-observing zones).
     *
     * Invariants protected:
     * 1. For any realistic positive `lastBackupTimestamp` and `nowMillis`, `nextReminderTimeMillis`
     *    NEVER returns null.
     * 2. The scheduled reminder ALWAYS falls strictly inside daytime hours [08:00, 22:00) in `timeZone`.
     * 3. The scheduled reminder is ALWAYS >= `lastBackupTimestamp + 7 days`.
     * 4. The scheduled reminder is ALWAYS strictly greater than `nowMillis`.
     */
    @Test
    fun pbt_nextReminderTimeMillis_alwaysDaytime_strictlyAfterNow_andAtLeastSevenDaysAfterBackup() {
        val rng = Random(seed = 20261126L)
        val timeZones = listOf(
            "UTC",
            "America/Bogota",
            "America/New_York",
            "America/Los_Angeles",
            "Europe/Berlin",
            "Asia/Tokyo",
            "Asia/Kolkata",      // UTC+05:30
            "Asia/Kathmandu",    // UTC+05:45
            "Pacific/Kiritimati" // UTC+14:00
        ).map { TimeZone.getTimeZone(it) }

        val baseEpoch = 1_700_000_000_000L // ~Nov 2023
        val tenYearsMillis = 10L * 365 * 24 * 60 * 60 * 1000

        repeat(500) { trial ->
            val tz = timeZones[rng.nextInt(timeZones.size)]
            val lastBackup = baseEpoch + rng.nextLong(0L, tenYearsMillis)
            // Cover before 7 days, around 7 days, and weeks after 7 days
            val offsetFromBackup = rng.nextLong(-2L * 24 * 3600 * 1000, 30L * 24 * 3600 * 1000)
            val now = lastBackup + offsetFromBackup

            val next = BackupReminderScheduleCalculator.nextReminderTimeMillis(
                lastBackupTimestamp = lastBackup,
                nowMillis = now,
                timeZone = tz
            )

            assertNotNull("Trial #$trial ($tz): next reminder must not be null", next)
            val scheduled = next!!
            assertTrue(
                "Trial #$trial ($tz): scheduled=$scheduled must be daytime (08:00-21:59)",
                BackupReminderScheduleCalculator.isDaytime(scheduled, tz)
            )
            assertTrue(
                "Trial #$trial ($tz): scheduled=$scheduled must be >= lastBackup+7d (${lastBackup + BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS})",
                scheduled >= lastBackup + BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS
            )
            assertTrue(
                "Trial #$trial ($tz): scheduled=$scheduled must be strictly > now=$now",
                scheduled > now
            )
        }
    }

    /**
     * Property-Based Test (500 trials).
     *
     * Invariant protected:
     * `shouldNotifyNow(lastBackup, now, tz)` is true if and only if
     * `lastBackup != null && now - lastBackup >= 7 days && isDaytime(now, tz)`.
     */
    @Test
    fun pbt_shouldNotifyNow_matchesSevenDayThresholdAndDaytimeWindow() {
        val rng = Random(seed = 987654321L)
        val zones = listOf("UTC", "America/New_York", "Europe/Berlin", "Asia/Kolkata")
            .map { TimeZone.getTimeZone(it) }
        val baseEpoch = 1_750_000_000_000L

        repeat(500) { trial ->
            val tz = zones[rng.nextInt(zones.size)]
            val lastBackup = baseEpoch + rng.nextLong(0L, 365L * 24 * 3600 * 1000)
            val delta = rng.nextLong(-BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS, 14L * 24 * 3600 * 1000)
            val now = lastBackup + delta

            assertFalse(
                "Trial #$trial: null lastBackup must never notify",
                BackupReminderScheduleCalculator.shouldNotifyNow(null, now, tz)
            )

            val expected = delta >= BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS &&
                BackupReminderScheduleCalculator.isDaytime(now, tz)
            assertEquals(
                "Trial #$trial ($tz, delta=$delta): shouldNotifyNow mismatch",
                expected,
                BackupReminderScheduleCalculator.shouldNotifyNow(lastBackup, now, tz)
            )
        }
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Arithmetic Overflow]:
     * Tests `Long.MAX_VALUE` boundary arithmetic in `shouldNotifyNow` and `nextReminderTimeMillis`.
     *
     * Bug exposed:
     * `lastBackupTimestamp + SEVEN_DAYS_MILLIS` overflows signed 64-bit `Long` into a negative value
     * when `lastBackupTimestamp` is near `Long.MAX_VALUE`, causing `shouldNotifyNow` to return `true`
     * even though `nowMillis < lastBackupTimestamp`.
     * Similarly, `nowMillis + REMINDER_INTERVAL_MILLIS` overflows to a negative timestamp when `nowMillis`
     * is near `Long.MAX_VALUE`.
     */
    @Test
    fun adversarial_scheduleCalculator_nearLongMaxValue_mustNotOverflow() {
        val utc = TimeZone.getTimeZone("UTC")
        val daytimeNow = calMillis(utc, 2026, 11, 26, 12, 0)
        val futureCorruptBackup = Long.MAX_VALUE - 1_000L

        assertFalse(
            "lastBackup near Long.MAX_VALUE is in the future; shouldNotifyNow must NOT overflow and return true",
            BackupReminderScheduleCalculator.shouldNotifyNow(futureCorruptBackup, daytimeNow, utc)
        )

        val validBackup = calMillis(utc, 2026, 11, 1, 10, 0)
        val nearMaxNow = Long.MAX_VALUE - 1_000L
        val next = BackupReminderScheduleCalculator.nextReminderTimeMillis(validBackup, nearMaxNow, utc)
        assertTrue(
            "nextReminderTimeMillis must not wrap around to a negative timestamp on Long overflow (got $next)",
            next == null || next >= nearMaxNow
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Non-Positive Timestamps]:
     * `SharedPrefsBackupPreferenceRepository` treats `<= 0L` as "never backed up" (`null`),
     * yet `BackupReminderScheduleCalculator` accepts `0L`, `-1L`, and `Long.MIN_VALUE` and treats
     * them as valid epoch timestamps from 1970 or BCE, triggering immediate notifications.
     */
    @Test
    fun adversarial_scheduleCalculator_nonPositiveLastBackupTimestamp_treatedAsUnbackedUp() {
        val utc = TimeZone.getTimeZone("UTC")
        val daytimeNow = calMillis(utc, 2026, 11, 26, 14, 0)

        for (invalidTimestamp in listOf(0L, -1L, -BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS, Long.MIN_VALUE)) {
            assertFalse(
                "Invalid lastBackupTimestamp=$invalidTimestamp must NOT trigger shouldNotifyNow",
                BackupReminderScheduleCalculator.shouldNotifyNow(invalidTimestamp, daytimeNow, utc)
            )
            assertNull(
                "Invalid lastBackupTimestamp=$invalidTimestamp must NOT schedule a reminder",
                BackupReminderScheduleCalculator.nextReminderTimeMillis(invalidTimestamp, daytimeNow, utc)
            )
        }
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Nighttime Boot / Wakeup Schedule Drift]:
     * Suppose a backup was taken at 02:00 UTC (`sevenDaysAfter` = 7 days later at 02:00 UTC).
     * When the device reboots (`ACTION_BOOT_COMPLETED`) at 07:55 UTC (nighttime, 5 minutes before
     * the 08:00 daytime window opens), no reminder has been delivered yet because 07:55 is nighttime.
     *
     * Expected: next reminder is scheduled for 08:00 UTC (the opening of the daytime window).
     * Actual bug: because `nowMillis >= sevenDaysAfter`, `nextReminderTimeMillis` computes
     * `07:55 + 8h = 15:55 UTC`, delaying the pending morning reminder by nearly 8 hours!
     */
    @Test
    fun adversarial_scheduleCalculator_nighttimeRescheduleAfterSevenDays_targets0800InsteadOfDelaying8Hours() {
        val utc = TimeZone.getTimeZone("UTC")
        val backupAt2am = calMillis(utc, 2026, 11, 25, 2, 0)
        val bootAt755am = calMillis(utc, 2026, 12, 2, 7, 55)
        val expected8am = calMillis(utc, 2026, 12, 2, 8, 0)

        assertFalse(BackupReminderScheduleCalculator.shouldNotifyNow(backupAt2am, bootAt755am, utc))

        val scheduled = BackupReminderScheduleCalculator.nextReminderTimeMillis(
            lastBackupTimestamp = backupAt2am,
            nowMillis = bootAt755am,
            timeZone = utc
        )
        assertEquals(
            "Nighttime reschedule after 7 days elapsed should fire at 08:00 opening of daytime window, not 15:55",
            expected8am,
            scheduled
        )
    }

    /**
     * Adversarial Edge Case — DST Spring-Forward (23-hour day) & Fall-Back (25-hour day):
     * Verifies `America/New_York` transitions on 2026-03-08 (02:00->03:00) and 2026-11-01 (02:00->01:00)
     * never produce nighttime alarms or non-monotonic timestamps.
     */
    @Test
    fun adversarial_scheduleCalculator_dstSpringForwardAndFallBack_preserveDaytimeAndMonotonicity() {
        val ny = TimeZone.getTimeZone("America/New_York")

        // Spring-forward: 2026-03-07 21:00 EST -> +8h crosses skipped 02:00 -> 06:00 EDT -> adjusts to 08:00 EDT
        val backupBeforeSpring = calMillis(ny, 2026, 2, 20, 12, 0)
        val springEve21 = calMillis(ny, 2026, 3, 7, 21, 0)
        val springNext = BackupReminderScheduleCalculator.nextReminderTimeMillis(backupBeforeSpring, springEve21, ny)!!
        assertTrue("Spring-forward scheduled time must be daytime", BackupReminderScheduleCalculator.isDaytime(springNext, ny))
        assertTrue("Spring-forward scheduled time must be > now", springNext > springEve21)
        val springCal = Calendar.getInstance(ny).apply { timeInMillis = springNext }
        assertEquals(8, springCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, springCal.get(Calendar.MINUTE))

        // Fall-back: 2026-10-31 21:30 EDT -> +8h crosses repeated 01:00 -> 04:30 EST -> adjusts to 08:00 EST
        val backupBeforeFall = calMillis(ny, 2026, 10, 15, 12, 0)
        val fallEve2130 = calMillis(ny, 2026, 10, 31, 21, 30)
        val fallNext = BackupReminderScheduleCalculator.nextReminderTimeMillis(backupBeforeFall, fallEve2130, ny)!!
        assertTrue("Fall-back scheduled time must be daytime", BackupReminderScheduleCalculator.isDaytime(fallNext, ny))
        assertTrue("Fall-back scheduled time must be > now", fallNext > fallEve2130)
        val fallCal = Calendar.getInstance(ny).apply { timeInMillis = fallNext }
        assertEquals(8, fallCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, fallCal.get(Calendar.MINUTE))
    }

    // =========================================================================
    // SECTION 2: Property-Based & Adversarial Tests for BackupUseCase
    // =========================================================================

    /**
     * Property-Based Test (80 deterministic trials).
     *
     * Invariant protected:
     * Round-trip fidelity: `parseBackupJson(buildBackupJson(ts))` MUST succeed and reproduce
     * every period, income, budget, expense (both BUDGET & SAVINGS), saving, category, subcategory,
     * and preference verbatim — even with emojis, RTL Arabic/Hebrew, escaped quotes, backslashes,
     * newlines, control chars, and SQL-like strings.
     */
    @Test
    fun pbt_buildAndParseBackupJson_roundTripPreservesAllRecordsAndAdversarialStrings() = runBlocking {
        val rng = Random(seed = 424242L)
        val adversarialStrings = listOf(
            "Café ☕ \"quoted\" \\backslash\\",
            "Line1\nLine2\tTabbed",
            "مرحبا بالعالم 💰",
            "שלום 🏠",
            "日本語テスト✈️",
            "'; DROP TABLE expenses; --",
            "A\u200DB\uFE0F👨‍👩‍👧‍👦",
            "Normal"
        )
        val languages = listOf("es", "en", "de", "pt")
        val currencies = listOf("COP", "USD", "EUR", "BRL")

        repeat(80) { trial ->
            val harness = BackupHarness()
            harness.langRepo.lang = languages[rng.nextInt(languages.size)]
            harness.currencyRepo.currency = currencies[rng.nextInt(currencies.size)]

            val periodCount = rng.nextInt(1, 4)
            val periods = (1..periodCount).map { idx ->
                FinancialPeriod(
                    id = idx.toLong(),
                    startDate = "2026-${String.format("%02d", idx)}-01",
                    endDate = "2026-${String.format("%02d", idx)}-28"
                )
            }
            harness.periodRepo.periodsFlow.value = periods

            val incomes = (1..rng.nextInt(0, 5)).map { idx ->
                Income(
                    id = idx.toLong(),
                    amount = rng.nextDouble(0.01, 1_000_000_000.0),
                    dateTime = "2026-01-0${(idx % 9) + 1} 10:00",
                    category = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    subcategory = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    description = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    periodId = periods[rng.nextInt(periods.size)].id
                )
            }
            harness.incomeRepo.incomesFlow.value = incomes

            val budgets = (1..rng.nextInt(0, 5)).map { idx ->
                Budget(
                    id = idx.toLong(),
                    amount = rng.nextDouble(0.01, 500_000_000.0),
                    category = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    subcategory = if (rng.nextBoolean()) "" else adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    periodId = periods[rng.nextInt(periods.size)].id
                )
            }
            harness.budgetRepo.budgetsFlow.value = budgets

            val expenses = (1..rng.nextInt(0, 6)).map { idx ->
                val isSavings = rng.nextBoolean()
                Expense(
                    id = idx.toLong(),
                    amount = rng.nextDouble(0.01, 100_000_000.0),
                    date = "2026-01-1${idx % 9}",
                    description = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    category = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    subcategory = if (rng.nextBoolean()) "" else adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    periodId = periods[rng.nextInt(periods.size)].id,
                    budgetId = if (isSavings) 0L else (budgets.firstOrNull()?.id ?: 0L),
                    budgetCategory = if (isSavings) "" else "Hogar",
                    fundingSource = if (isSavings) ExpenseFundingSource.SAVINGS else ExpenseFundingSource.BUDGET,
                    savingCategory = if (isSavings) "Viajes" else ""
                )
            }
            harness.expenseRepo.expensesFlow.value = expenses

            val savings = (1..rng.nextInt(0, 5)).map { idx ->
                Saving(
                    id = idx.toLong(),
                    amount = rng.nextDouble(0.01, 250_000_000.0),
                    category = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    subcategory = adversarialStrings[rng.nextInt(adversarialStrings.size)],
                    periodId = periods[rng.nextInt(periods.size)].id,
                    incomeId = incomes.firstOrNull()?.id ?: 0L,
                    incomeCategory = "Salario",
                    dateTime = "2026-01-05 08:00",
                    note = adversarialStrings[rng.nextInt(adversarialStrings.size)]
                )
            }
            harness.savingsRepo.savingsFlow.value = savings

            val categories = listOf(
                Category(
                    name = "Cat_$trial",
                    icon = "💰",
                    color = "#4E73DF",
                    subcategories = listOf(Subcategory("Sub_${trial}_A"), Subcategory("Sub_${trial}_B"))
                )
            )
            harness.incomeRepo.categoriesFlow.value = categories
            harness.budgetRepo.categoriesFlow.value = categories
            harness.expenseCatRepo.categoriesFlow.value = categories
            harness.savingsRepo.categoriesFlow.value = categories

            val exportedAt = 1_764_000_000_000L + trial * 1000L
            val useCase = harness.buildUseCase()
            val json = useCase.buildBackupJson(exportedAtMillis = exportedAt)
            val parsed = useCase.parseBackupJson(json)

            assertTrue("Trial #$trial: parsed backup must be Valid", parsed is RestoreParseResult.Valid)
            val snapshot = (parsed as RestoreParseResult.Valid).snapshot
            assertEquals(BackupUseCase.SCHEMA_VERSION, snapshot.schemaVersion)
            assertEquals(exportedAt, snapshot.exportedAtMillis)
            assertEquals(harness.langRepo.lang, snapshot.language)
            assertEquals(harness.currencyRepo.currency, snapshot.currency)
            assertEquals(periods, snapshot.periods)
            assertEquals(incomes.sortedBy { it.id }, snapshot.incomes.sortedBy { it.id })
            assertEquals(budgets.sortedBy { it.id }, snapshot.budgets.sortedBy { it.id })
            assertEquals(expenses, snapshot.expenses)
            assertEquals(savings, snapshot.savings)
            assertEquals(categories, snapshot.incomeCategories)
            assertEquals(categories, snapshot.budgetCategories)
            assertEquals(categories, snapshot.expenseCategories)
            assertEquals(categories, snapshot.savingCategories)
        }
    }

    /**
     * Property-Based Test (50 trials).
     *
     * Invariant protected:
     * Whenever `writeContent` throws ANY non-cancellation exception, `exportBackup` returns
     * `BackupResult.Error` and NEVER modifies `backupPreferenceRepository.getLastBackupTimestamp()`.
     */
    @Test
    fun pbt_exportBackup_neverMutatesLastBackupTimestampOnWriteFailure() = runBlocking {
        val rng = Random(seed = 1337L)
        val exceptions: List<() -> Exception> = listOf(
            { IOException("Broken pipe") },
            { IllegalStateException("OutputStream closed") },
            { SecurityException("Permission revoked") },
            { RuntimeException("Drive quota exceeded") }
        )

        repeat(50) { trial ->
            val previousTs = if (rng.nextBoolean()) null else rng.nextLong(1L, 1_900_000_000_000L)
            val harness = BackupHarness(
                backupPrefs = AdversarialBackupPrefRepo(previousTs),
                nowMillis = 1_950_000_000_000L + trial
            )
            val useCase = harness.buildUseCase()
            val result = useCase.exportBackup { throw exceptions[trial % exceptions.size]() }

            assertTrue("Trial #$trial: must return BackupResult.Error", result is BackupResult.Error)
            assertEquals(
                "Trial #$trial: previous lastBackupTimestamp must remain untouched on write failure",
                previousTs,
                harness.backupPrefs.getLastBackupTimestamp()
            )
        }
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Negative & Zero Monetary Amounts]:
     * `parseBackupJson` only validates `amount.isFinite()`, which returns `true` for negative
     * values (`-500.0`, `-0.01`) and zero (`0.0`).
     *
     * Expected: corrupted/tampered backup files with negative or zero financial amounts MUST be
     * rejected as `RestoreParseResult.Invalid` to prevent corrupting accounting totals & budgets.
     */
    @Test
    fun adversarial_parseBackupJson_rejectsNegativeAndZeroFinancialAmounts() {
        val useCase = BackupHarness().buildUseCase()

        val negativeIncomeJson = buildMinimalValidBackupJson(
            incomes = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("amount", -500.0); put("dateTime", "2026-11-01 10:00")
                put("category", "Salario"); put("periodId", 1L)
            })
        )
        assertTrue(
            "Negative income amount (-500.0) must be rejected as Invalid",
            useCase.parseBackupJson(negativeIncomeJson) is RestoreParseResult.Invalid
        )

        val zeroBudgetJson = buildMinimalValidBackupJson(
            budgets = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("amount", 0.0); put("category", "Hogar"); put("periodId", 1L)
            })
        )
        assertTrue(
            "Zero budget amount (0.0) must be rejected as Invalid",
            useCase.parseBackupJson(zeroBudgetJson) is RestoreParseResult.Invalid
        )

        val negativeExpenseJson = buildMinimalValidBackupJson(
            expenses = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("amount", -0.01); put("date", "2026-11-02"); put("category", "Comida")
            })
        )
        assertTrue(
            "Negative expense amount (-0.01) must be rejected as Invalid",
            useCase.parseBackupJson(negativeExpenseJson) is RestoreParseResult.Invalid
        )

        val negativeSavingJson = buildMinimalValidBackupJson(
            savings = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("amount", -100.0); put("category", "Viajes"); put("periodId", 1L)
            })
        )
        assertTrue(
            "Negative saving amount (-100.0) must be rejected as Invalid",
            useCase.parseBackupJson(negativeSavingJson) is RestoreParseResult.Invalid
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — org.json `optString` returns literal `"null"`]:
     * In Android's `org.json.JSONObject`, calling `optString("currency", "")` when the JSON contains
     * `"currency": null` returns the 4-character literal string `"null"` instead of `""`!
     * Because `"null".isNotBlank()` is `true`, `restoreBackup` then overwrites the user's currency
     * preference with the literal string `"null"`, and optional fields (subcategory, description,
     * note, icon, color) become `"null"`.
     */
    @Test
    fun adversarial_parseBackupJson_explicitJsonNullFields_mustNotBecomeLiteralNullString() = runBlocking {
        val harness = BackupHarness(currencyRepo = AdversarialCurrencyPrefRepo("USD"))
        val useCase = harness.buildUseCase()

        val jsonWithExplicitNulls = buildMinimalValidBackupJson(
            language = null,
            currency = null,
            expenses = JSONArray().put(JSONObject().apply {
                put("id", 1L)
                put("amount", 50.0)
                put("date", "2026-11-10")
                put("category", "Comida")
                put("subcategory", JSONObject.NULL)
                put("description", JSONObject.NULL)
                put("budgetCategory", JSONObject.NULL)
                put("savingCategory", JSONObject.NULL)
            }),
            expenseCategories = JSONArray().put(JSONObject().apply {
                put("name", "Comida")
                put("icon", JSONObject.NULL)
                put("color", JSONObject.NULL)
                put("subcategories", JSONObject.NULL)
            })
        )

        val parsed = useCase.parseBackupJson(jsonWithExplicitNulls)
        assertTrue("JSON with nullable optional fields should parse cleanly", parsed is RestoreParseResult.Valid)
        val snapshot = (parsed as RestoreParseResult.Valid).snapshot

        assertNotEquals("language must not be literal 'null'", "null", snapshot.language)
        assertNotEquals("currency must not be literal 'null'", "null", snapshot.currency)
        assertNotEquals("subcategory must not be literal 'null'", "null", snapshot.expenses.single().subcategory)
        assertNotEquals("description must not be literal 'null'", "null", snapshot.expenses.single().description)
        assertNotEquals("icon must not be literal 'null'", "null", snapshot.expenseCategories.single().icon)
        assertNotEquals("color must not be literal 'null'", "null", snapshot.expenseCategories.single().color)

        useCase.restoreBackup(snapshot)
        assertEquals(
            "Restoring a snapshot with currency:null must preserve existing USD preference, not save 'null'",
            "USD",
            harness.currencyRepo.getCurrency()
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Unvalidated Currency Code on Restore]:
     * Unlike `language` (validated against `SUPPORTED_LANGUAGES`), `restoreBackup` accepts ANY
     * arbitrary non-blank `currency` string (e.g., `"FAKE_COIN"`, `"<script>"`), overwriting a valid
     * local currency preference with an unsupported value.
     */
    @Test
    fun adversarial_restoreBackup_unsupportedCurrencyCode_mustNotOverwriteValidPreference() = runBlocking {
        val harness = BackupHarness(currencyRepo = AdversarialCurrencyPrefRepo("EUR"))
        val useCase = harness.buildUseCase()

        val tamperedSnapshot = BackupSnapshot(
            schemaVersion = 1,
            exportedAtMillis = 1_764_200_000_000L,
            language = "fr", // unsupported
            currency = "FAKE_COIN_999", // unsupported
            periods = emptyList(),
            incomes = emptyList(),
            budgets = emptyList(),
            expenses = emptyList(),
            savings = emptyList(),
            incomeCategories = emptyList(),
            budgetCategories = emptyList(),
            expenseCategories = emptyList(),
            savingCategories = emptyList()
        )

        val result = useCase.restoreBackup(tamperedSnapshot)
        assertTrue(result is RestoreResult.Success)
        assertEquals("Unsupported language must be ignored", "es", harness.langRepo.getLanguage())
        assertEquals(
            "Unsupported currency 'FAKE_COIN_999' must NOT overwrite existing 'EUR' preference",
            "EUR",
            harness.currencyRepo.getCurrency()
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Blank Required Fields & Inverted Period Dates]:
     * Tests malformed payloads, schema tampering, blank required fields, and invalid date ranges.
     */
    @Test
    fun adversarial_parseBackupJson_rejectsSchemaTamperingAndBlankRequiredFields() {
        val useCase = BackupHarness().buildUseCase()

        val alwaysInvalidPayloads = listOf(
            "",
            "   ",
            "[]",
            "{}",
            buildMinimalValidBackupJson(schemaVersion = 0),
            buildMinimalValidBackupJson(schemaVersion = -1),
            buildMinimalValidBackupJson(schemaVersion = BackupUseCase.SCHEMA_VERSION + 1),
            buildMinimalValidBackupJson(schemaVersion = Int.MAX_VALUE),
            buildMinimalValidBackupJson(schemaVersion = "one"),
            buildMinimalValidBackupJson(
                incomeCategories = JSONArray().put(JSONObject().apply {
                    put("name", "   ")
                })
            )
        )
        alwaysInvalidPayloads.forEachIndexed { idx, payload ->
            assertTrue(
                "Tampered payload #$idx must be Invalid",
                useCase.parseBackupJson(payload) is RestoreParseResult.Invalid
            )
        }

        // Blank required fields / inverted period dates (currently accepted as Valid!)
        val blankCategoryExpense = buildMinimalValidBackupJson(
            expenses = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("amount", 10.0); put("date", "2026-11-01"); put("category", "   ")
            })
        )
        assertTrue(
            "Expense with blank category must be rejected as Invalid",
            useCase.parseBackupJson(blankCategoryExpense) is RestoreParseResult.Invalid
        )

        val invertedPeriodDates = buildMinimalValidBackupJson(
            periods = JSONArray().put(JSONObject().apply {
                put("id", 1L); put("startDate", "2026-12-31"); put("endDate", "2026-01-01")
            })
        )
        assertTrue(
            "Period with startDate > endDate must be rejected as Invalid",
            useCase.parseBackupJson(invertedPeriodDates) is RestoreParseResult.Invalid
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Duplicate Period IDs Inflate Exported Incomes/Budgets]:
     * `buildBackupJson` computes `periods.flatMap { incomeRepository.getIncomesByPeriod(it.id) }`.
     * If `periods` contains duplicate period IDs, incomes and budgets for that period are duplicated
     * in the exported JSON!
     */
    @Test
    fun adversarial_buildBackupJson_duplicatePeriodIds_mustNotDuplicateIncomesOrBudgets() = runBlocking {
        val harness = BackupHarness()
        harness.periodRepo.periodsFlow.value = listOf(
            FinancialPeriod(1L, "2026-11-01", "2026-11-30"),
            FinancialPeriod(1L, "2026-11-01", "2026-11-30")
        )
        harness.incomeRepo.incomesFlow.value = listOf(
            Income(10L, 2500.0, "2026-11-02 09:00", "Salario", "", "", 1L)
        )
        harness.budgetRepo.budgetsFlow.value = listOf(
            Budget(20L, 500.0, "Hogar", "", 1L)
        )

        val json = harness.buildUseCase().buildBackupJson(1_764_200_000_000L)
        val root = JSONObject(json)

        assertEquals(
            "Export must deduplicate incomes rather than exporting duplicates for repeated period IDs",
            1,
            root.getJSONArray("incomes").length()
        )
        assertEquals(
            "Export must deduplicate budgets rather than exporting duplicates for repeated period IDs",
            1,
            root.getJSONArray("budgets").length()
        )
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Non-Atomic Cross-Database Restore / Split-Brain Data Loss]:
     * `restoreBackup` replaces 4 separate Room databases sequentially without rollback:
     * Periods (ConfigDb) -> Categories -> Incomes -> Budgets -> Expenses -> Savings -> Prefs.
     *
     * If `expenseRepository.replaceAllExpenses` fails mid-restore (e.g., disk full / SQLite error),
     * periods, categories, incomes, and budgets have ALREADY been permanently wiped and replaced with
     * new data, while expenses, savings, and preferences remain from the old data!
     *
     * Expected invariant: On failure, local state MUST be rolled back (or left consistent) so the user
     * does NOT lose existing periods, incomes, and budgets.
     */
    @Test
    fun adversarial_restoreBackup_midFlightFailure_mustRollbackAlreadyReplacedRepositories() = runBlocking {
        val originalPeriod = FinancialPeriod(1L, "2026-10-01", "2026-10-31")
        val originalIncome = Income(1L, 4000.0, "2026-10-01 08:00", "Salario", "", "Original", 1L)
        val originalBudget = Budget(1L, 1200.0, "Hogar", "", 1L)
        val originalExpense = Expense(1L, 300.0, "2026-10-05", "Renta", "Hogar", "", 1L, 1L, "Hogar", ExpenseFundingSource.BUDGET, "")
        val originalSaving = Saving(1L, 800.0, "Fondo", "", 1L, 1L, "Salario", "2026-10-02", "")

        val harness = BackupHarness(
            periodRepo = AdversarialPeriodRepo(listOf(originalPeriod)),
            incomeRepo = AdversarialIncomeRepo(listOf(originalIncome)),
            budgetRepo = AdversarialBudgetRepo(listOf(originalBudget)),
            expenseRepo = AdversarialExpenseRepo(listOf(originalExpense)),
            savingsRepo = AdversarialSavingsRepo(listOf(originalSaving)),
            langRepo = AdversarialLangPrefRepo("es"),
            currencyRepo = AdversarialCurrencyPrefRepo("COP")
        )
        // Simulate failure halfway through cross-DB restore
        harness.expenseRepo.failOnReplace = RuntimeException("SQLiteFullException: database or disk is full")

        val incomingSnapshot = BackupSnapshot(
            schemaVersion = 1,
            exportedAtMillis = 1_764_300_000_000L,
            language = "en",
            currency = "USD",
            periods = listOf(FinancialPeriod(99L, "2026-11-01", "2026-11-30")),
            incomes = listOf(Income(99L, 9999.0, "2026-11-01 10:00", "New", "", "", 99L)),
            budgets = listOf(Budget(99L, 5000.0, "NewBudget", "", 99L)),
            expenses = listOf(Expense(99L, 100.0, "2026-11-02", "NewExp", "Food", "", 99L, 99L, "NewBudget")),
            savings = listOf(Saving(99L, 2000.0, "NewSav", "", 99L)),
            incomeCategories = emptyList(),
            budgetCategories = emptyList(),
            expenseCategories = emptyList(),
            savingCategories = emptyList()
        )

        val result = harness.buildUseCase().restoreBackup(incomingSnapshot)
        assertTrue("Restore must report Error when expense replacement fails", result is RestoreResult.Error)

        // Atomicity invariant: NO partial wipe / split-brain state!
        assertEquals(
            "Periods must be rolled back to original state on mid-restore failure (no split-brain data loss)",
            listOf(originalPeriod),
            harness.periodRepo.periodsFlow.value
        )
        assertEquals(
            "Incomes must be rolled back to original state on mid-restore failure",
            listOf(originalIncome),
            harness.incomeRepo.incomesFlow.value
        )
        assertEquals(
            "Budgets must be rolled back to original state on mid-restore failure",
            listOf(originalBudget),
            harness.budgetRepo.budgetsFlow.value
        )
        assertEquals(listOf(originalExpense), harness.expenseRepo.expensesFlow.value)
        assertEquals(listOf(originalSaving), harness.savingsRepo.savingsFlow.value)
        assertEquals("es", harness.langRepo.getLanguage())
        assertEquals("COP", harness.currencyRepo.getCurrency())
    }

    /**
     * Adversarial Edge Case — Coroutine `CancellationException` Propagation:
     * Neither `exportBackup` nor `restoreBackup` may ever swallow `CancellationException`
     * into `BackupResult.Error` or `RestoreResult.Error`.
     */
    @Test
    fun adversarial_exportAndRestore_rethrowCancellationExceptionWithoutSwallowing() = runBlocking {
        val harness = BackupHarness(backupPrefs = AdversarialBackupPrefRepo(1_764_000_000_000L))
        val useCase = harness.buildUseCase()

        var exportCancelled = false
        try {
            useCase.exportBackup { throw CancellationException("User navigated away") }
            fail("exportBackup must rethrow CancellationException")
        } catch (_: CancellationException) {
            exportCancelled = true
        }
        assertTrue(exportCancelled)
        assertEquals(1_764_000_000_000L, harness.backupPrefs.getLastBackupTimestamp())

        harness.periodRepo.failOnReplace = CancellationException("Scope cancelled mid-restore")
        val emptySnapshot = BackupSnapshot(
            1, 1_764_200_000_000L, "es", "COP",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList()
        )
        var restoreCancelled = false
        try {
            useCase.restoreBackup(emptySnapshot)
            fail("restoreBackup must rethrow CancellationException")
        } catch (_: CancellationException) {
            restoreCancelled = true
        }
        assertTrue(restoreCancelled)
    }

    /**
     * Adversarial Edge Case — Non-Finite Double (`NaN` / `Infinity`) in Local Storage During Export:
     * `org.json.JSONObject.put(String, Double)` throws `JSONException` on `Double.NaN` or `Infinity`.
     * `exportBackup` must fail gracefully with `BackupResult.Error` and NOT update `lastBackupTimestamp`.
     */
    @Test
    fun adversarial_exportBackup_nonFiniteDoubleInRepo_returnsErrorAndPreservesTimestamp() = runBlocking {
        val harness = BackupHarness(
            expenseRepo = AdversarialExpenseRepo(
                listOf(Expense(1L, Double.NaN, "2026-11-10", "Corrupt", "Comida"))
            ),
            backupPrefs = AdversarialBackupPrefRepo(1_764_000_000_000L)
        )
        var writeCalled = false
        val result = harness.buildUseCase().exportBackup { writeCalled = true }

        assertTrue("Export with NaN amount must return BackupResult.Error", result is BackupResult.Error)
        assertFalse("writeContent must not be invoked when JSON serialization fails", writeCalled)
        assertEquals(1_764_000_000_000L, harness.backupPrefs.getLastBackupTimestamp())
    }

    /**
     * Adversarial Edge Case — Concurrency & Mutual Exclusion via `allocationMutex`:
     * Verifies concurrent `restoreBackup` and `buildBackupJson` sharing a `Mutex` never interleave
     * repository mutations and snapshot reads.
     */
    @Test
    fun adversarial_concurrentRestoreAndBuildBackup_strictlySerializedByMutex() = runBlocking {
        val harness = BackupHarness()
        val restoreInsideLock = CompletableDeferred<Unit>()
        val allowRestoreToFinish = CompletableDeferred<Unit>()
        val overlapDetected = AtomicBoolean(false)
        val restoreRunning = AtomicBoolean(false)

        harness.periodRepo.onReplaceHook = {
            restoreRunning.set(true)
            restoreInsideLock.complete(Unit)
            allowRestoreToFinish.await()
            restoreRunning.set(false)
        }

        val useCase = harness.buildUseCase()
        val snapshot = BackupSnapshot(
            1, 1_764_200_000_000L, "es", "COP",
            listOf(FinancialPeriod(5L, "2026-11-01", "2026-11-30")),
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList()
        )

        val restoreJob = async { useCase.restoreBackup(snapshot) }
        restoreInsideLock.await()

        val buildJob = async {
            val json = useCase.buildBackupJson(1_764_200_000_000L)
            if (restoreRunning.get()) overlapDetected.set(true)
            json
        }

        // Yield several times to ensure buildJob blocks on the mutex
        repeat(5) { yield() }
        assertFalse("buildBackupJson must still be waiting while restore holds mutex", buildJob.isCompleted)

        allowRestoreToFinish.complete(Unit)
        val results = awaitAll(restoreJob, buildJob)
        assertTrue(results[0] is RestoreResult.Success)
        assertFalse("No overlap allowed between restoreBackup and buildBackupJson", overlapDetected.get())
        val parsed = useCase.parseBackupJson(results[1] as String)
        assertTrue(parsed is RestoreParseResult.Valid)
        assertEquals(listOf(FinancialPeriod(5L, "2026-11-01", "2026-11-30")), (parsed as RestoreParseResult.Valid).snapshot.periods)
    }

    // =========================================================================
    // SECTION 3: Adversarial State Machine & Concurrency Tests for SettingsViewModel
    // =========================================================================

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Missing Cross-Operation Mutual Exclusion]:
     * `SettingsViewModel.performBackup` only checks `backupStatus == IN_PROGRESS` and ignores
     * `restoreStatus == READING || RESTORING`. Conversely, `prepareRestore` ignores
     * `backupStatus == IN_PROGRESS`.
     *
     * Expected invariant: Backup and Restore MUST be mutually exclusive in ViewModel state so that
     * `backupStatus == IN_PROGRESS` and `restoreStatus in {READING, RESTORING}` can NEVER coexist.
     */
    @Test
    fun adversarial_settingsViewModel_backupAndRestoreMustBeMutuallyExclusive() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val harness = BackupHarness()
            val backupGate = CompletableDeferred<Unit>()
            val viewModel = SettingsViewModel(
                languageRepository = harness.langRepo,
                i18nManager = I18nManager(),
                currencyPreferenceRepository = harness.currencyRepo,
                backupUseCase = harness.buildUseCase(),
                backupPreferenceRepository = harness.backupPrefs
            )

            // Start backup and suspend mid-write
            viewModel.performBackup { backupGate.await() }
            assertEquals(BackupStatus.IN_PROGRESS, viewModel.uiState.value.backupStatus)

            // Attempt to start restore while backup is active -> MUST be rejected!
            var readInvoked = false
            viewModel.prepareRestore {
                readInvoked = true
                buildMinimalValidBackupJson()
            }
            assertEquals(
                "prepareRestore must be ignored while backupStatus is IN_PROGRESS",
                RestoreStatus.IDLE,
                viewModel.uiState.value.restoreStatus
            )
            assertFalse("readContent must not execute while backup is in progress", readInvoked)

            backupGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(BackupStatus.SUCCESS, viewModel.uiState.value.backupStatus)

            // Now start restore (suspended during read) and attempt performBackup -> MUST be rejected!
            val restoreReadGate = CompletableDeferred<String>()
            viewModel.prepareRestore { restoreReadGate.await() }
            assertEquals(RestoreStatus.READING, viewModel.uiState.value.restoreStatus)

            var writeInvoked = false
            viewModel.performBackup { writeInvoked = true }
            assertNotEquals(
                "performBackup must not enter IN_PROGRESS while restoreStatus is READING",
                BackupStatus.IN_PROGRESS,
                viewModel.uiState.value.backupStatus
            )
            assertFalse("writeContent must not execute while restore is reading", writeInvoked)

            restoreReadGate.complete(buildMinimalValidBackupJson())
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — State Corruption via Cancel/Clear During Active Work]:
     * Calling `onBackupCancelled()` or `clearBackupStatus()` while `backupStatus == IN_PROGRESS`
     * resets `backupStatus` out of `IN_PROGRESS` while the background write coroutine is still running,
     * re-enabling the UI buttons and allowing duplicate concurrent backups!
     * Similarly, calling `cancelPendingRestore()` while `restoreStatus == READING` allows the
     * still-running `prepareRestore` coroutine to later pop open the confirmation dialog!
     */
    @Test
    fun adversarial_settingsViewModel_cancelOrClearWhileBusy_mustNotCorruptActiveOperation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val harness = BackupHarness()
            val viewModel = SettingsViewModel(
                languageRepository = harness.langRepo,
                i18nManager = I18nManager(),
                currencyPreferenceRepository = harness.currencyRepo,
                backupUseCase = harness.buildUseCase(),
                backupPreferenceRepository = harness.backupPrefs
            )

            // 1. Clear/cancel during active backup must NOT prematurely unlock IN_PROGRESS
            val writeGate = CompletableDeferred<Unit>()
            viewModel.performBackup { writeGate.await() }
            assertEquals(BackupStatus.IN_PROGRESS, viewModel.uiState.value.backupStatus)

            viewModel.clearBackupStatus()
            viewModel.onBackupCancelled()
            assertEquals(
                "Active IN_PROGRESS backup must not be overwritten by clearBackupStatus/onBackupCancelled",
                BackupStatus.IN_PROGRESS,
                viewModel.uiState.value.backupStatus
            )
            writeGate.complete(Unit)
            advanceUntilIdle()

            // 2. Cancel during slow restore read must NOT later resurrect pendingRestoreSnapshot
            val readGate = CompletableDeferred<String>()
            viewModel.prepareRestore { readGate.await() }
            assertEquals(RestoreStatus.READING, viewModel.uiState.value.restoreStatus)

            viewModel.cancelPendingRestore()
            readGate.complete(buildMinimalValidBackupJson())
            advanceUntilIdle()

            assertNull(
                "Cancelled restore read must NOT populate pendingRestoreSnapshot upon late completion",
                viewModel.uiState.value.pendingRestoreSnapshot
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Uncaught Scheduler Exception Permanently Locks UI]:
     * In `SettingsViewModel.performBackup`, `backupReminderScheduler?.scheduleNextReminder(result.timestampMillis)`
     * is invoked BEFORE updating `_uiState` to `BackupStatus.SUCCESS` and WITHOUT try/catch!
     * If `AlarmManager` throws `SecurityException` or `RuntimeException`, the coroutine crashes and
     * `backupStatus` stays permanently stuck in `BackupStatus.IN_PROGRESS`, locking both buttons forever!
     */
    @Test
    fun adversarial_settingsViewModel_schedulerThrowsOnBackupSuccess_mustNotLeaveUiStuckInProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val harness = BackupHarness()
            val crashingScheduler = AdversarialReminderScheduler(
                throwOnSchedule = SecurityException("Exact/idle alarm denied by OEM policy")
            )
            val viewModel = SettingsViewModel(
                languageRepository = harness.langRepo,
                i18nManager = I18nManager(),
                currencyPreferenceRepository = harness.currencyRepo,
                backupUseCase = harness.buildUseCase(),
                backupPreferenceRepository = harness.backupPrefs,
                backupReminderScheduler = crashingScheduler
            )

            viewModel.performBackup { /* file written successfully */ }
            advanceUntilIdle()

            assertNotEquals(
                "Scheduler failure must NEVER leave backupStatus permanently stuck in IN_PROGRESS",
                BackupStatus.IN_PROGRESS,
                viewModel.uiState.value.backupStatus
            )
            assertEquals(
                "Backup succeeded writing file and saving timestamp even if reminder scheduling failed",
                BackupStatus.SUCCESS,
                viewModel.uiState.value.backupStatus
            )
            assertEquals(harness.nowMillis, viewModel.uiState.value.lastBackupTimestamp)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Adversarial Edge Case — Rapid Double Confirmation of Restore (Idempotency):
     * Calling `confirmRestore` twice back-to-back must consume `pendingRestoreSnapshot` once,
     * execute `replaceAllExpenses` strictly once, and invoke `onRestored` strictly once.
     */
    @Test
    fun adversarial_settingsViewModel_doubleConfirmRestore_executesStrictlyOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val harness = BackupHarness()
            val restoreGate = CompletableDeferred<Unit>()
            harness.expenseRepo.onReplaceHook = { restoreGate.await() }

            val viewModel = SettingsViewModel(
                languageRepository = harness.langRepo,
                i18nManager = I18nManager(),
                currencyPreferenceRepository = harness.currencyRepo,
                backupUseCase = harness.buildUseCase(),
                backupPreferenceRepository = harness.backupPrefs
            )

            viewModel.prepareRestore { buildMinimalValidBackupJson() }
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.pendingRestoreSnapshot)

            val callbackCount = AtomicInteger(0)
            viewModel.confirmRestore { callbackCount.incrementAndGet() }
            viewModel.confirmRestore { callbackCount.incrementAndGet() }

            assertNull(viewModel.uiState.value.pendingRestoreSnapshot)
            assertEquals(RestoreStatus.RESTORING, viewModel.uiState.value.restoreStatus)

            restoreGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(RestoreStatus.SUCCESS, viewModel.uiState.value.restoreStatus)
            assertEquals(1, harness.expenseRepo.replaceCallCount.get())
            assertEquals(1, callbackCount.get())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Adversarial Edge Case — Null `backupUseCase` & Read Exceptions:
     * Ensures graceful `ERROR` transitions when `backupUseCase` is null or `readContent` fails.
     */
    @Test
    fun adversarial_settingsViewModel_nullUseCaseAndReadIoError_transitionToErrorSafely() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val vmWithoutUseCase = SettingsViewModel(
                languageRepository = AdversarialLangPrefRepo("es"),
                i18nManager = I18nManager(),
                backupUseCase = null
            )

            vmWithoutUseCase.performBackup { }
            assertEquals(BackupStatus.ERROR, vmWithoutUseCase.uiState.value.backupStatus)

            vmWithoutUseCase.prepareRestore { "{}" }
            assertEquals(RestoreStatus.ERROR, vmWithoutUseCase.uiState.value.restoreStatus)

            // Confirming without a pending snapshot must remain a safe no-op
            vmWithoutUseCase.clearRestoreStatus()
            vmWithoutUseCase.confirmRestore()
            assertEquals(RestoreStatus.IDLE, vmWithoutUseCase.uiState.value.restoreStatus)

            // With useCase, IO error during readContent must set ERROR (not INVALID_FILE)
            val harness = BackupHarness()
            val vmWithUseCase = SettingsViewModel(
                languageRepository = harness.langRepo,
                i18nManager = I18nManager(),
                backupUseCase = harness.buildUseCase()
            )
            vmWithUseCase.prepareRestore { throw IOException("Drive file unreachable") }
            advanceUntilIdle()
            assertEquals(RestoreStatus.ERROR, vmWithUseCase.uiState.value.restoreStatus)
            assertNull(vmWithUseCase.uiState.value.pendingRestoreSnapshot)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================
    // SECTION 4: Adversarial Tests for SharedPrefsBackupPreferenceRepository & Room DAOs
    // =========================================================================

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Invalid Timestamp Erasure & Corrupted Pref Type]:
     * 1. Saving a non-positive timestamp (`0L`, `-1L`, `Long.MIN_VALUE`) after a valid backup
     *    timestamp silently erases the stored valid timestamp because `getLastBackupTimestamp()`
     *    returns `null` for `<= 0L`.
     * 2. If SharedPreferences contains a corrupted String value under `"last_backup_timestamp"`,
     *    `getLastBackupTimestamp()` crashes with an uncaught `ClassCastException` instead of
     *    safely returning `null`.
     */
    @Test
    fun adversarial_backupPreferenceRepository_boundaryValuesAndCorruptedTypeHandling() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("adversarial_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val repo = SharedPrefsBackupPreferenceRepository(prefs)

        // Positive extremes must round-trip cleanly
        repo.saveLastBackupTimestamp(1L)
        assertEquals(1L, repo.getLastBackupTimestamp())
        repo.saveLastBackupTimestamp(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, repo.getLastBackupTimestamp())

        // Invalid non-positive timestamps must NOT silently wipe a previously valid backup timestamp
        val validTs = 1_764_100_000_000L
        repo.saveLastBackupTimestamp(validTs)
        repo.saveLastBackupTimestamp(0L)
        assertEquals(
            "Saving 0L must not silently erase a previously recorded valid backup timestamp",
            validTs,
            repo.getLastBackupTimestamp()
        )

        // Corrupted preference type (String stored under Long key) must NOT crash with ClassCastException
        prefs.edit().putString(SharedPrefsBackupPreferenceRepository.KEY_LAST_BACKUP_TIMESTAMP, "corrupted").commit()
        val recovered = try {
            repo.getLastBackupTimestamp()
        } catch (e: ClassCastException) {
            fail("getLastBackupTimestamp() crashed with ClassCastException on corrupted prefs entry: ${e.message}")
            null
        }
        assertNull(recovered)
    }

    /**
     * Adversarial Edge Case — Room `replaceAll*` Transactions:
     * 1. Replacing all tables with empty lists cleanly wipes every DB without SQLite errors.
     * 2. SQL injection payloads, Unicode emojis, RTL text, and 5000-char strings round-trip safely.
     * 3. [DEFECT EXPOSURE] Replacing categories when input contains duplicate category names
     *    duplicates subcategories under that category in Room!
     */
    @Test
    fun adversarial_roomReplaceAll_emptyWipe_sqlInjection_andDuplicateCategoryDeduplication() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val configDb = Room.inMemoryDatabaseBuilder(context, ConfigDatabase::class.java).allowMainThreadQueries().build()
        val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val budgetDb = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).allowMainThreadQueries().build()
        val savingsDb = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java).allowMainThreadQueries().build()

        try {
            val periodRepo = RoomFinancialPeriodRepository(configDb.financialPeriodDao())
            val incomeRepo = RoomIncomeRepository(appDb.incomeDao(), appDb.incomeCategoryDao())
            val expenseRepo = RoomExpenseRepository(appDb.expenseDao())
            val expenseCatRepo = RoomCategoryRepository(appDb.categoryDao())
            val budgetRepo = RoomBudgetRepository(budgetDb.budgetDao(), budgetDb.budgetCategoryDao())
            val savingsRepo = RoomSavingsRepository(savingsDb.savingDao(), savingsDb.savingCategoryDao())

            val sqli = "'; DROP TABLE expenses; DROP TABLE incomes; --"
            val hugeDescription = "🔥".repeat(2500)

            // 1. Insert adversarial SQLi + huge Unicode strings via replaceAll
            periodRepo.replaceAllPeriods(listOf(FinancialPeriod(42L, "2026-11-01", "2026-11-30")))
            expenseRepo.replaceAllExpenses(
                listOf(
                    Expense(
                        id = 100L,
                        amount = 999_999_999.99,
                        date = "2026-11-15",
                        description = hugeDescription,
                        category = sqli,
                        subcategory = "مرحبا",
                        periodId = 42L,
                        budgetId = 7L,
                        budgetCategory = sqli,
                        fundingSource = ExpenseFundingSource.SAVINGS,
                        savingCategory = sqli
                    )
                )
            )

            val savedExpenses = expenseRepo.getAllExpenses().first()
            assertEquals(1, savedExpenses.size)
            assertEquals(sqli, savedExpenses.single().category)
            assertEquals(hugeDescription, savedExpenses.single().description)
            assertEquals(ExpenseFundingSource.SAVINGS, savedExpenses.single().fundingSource)

            // 2. Empty-list replaceAll cleanly wipes every table
            periodRepo.replaceAllPeriods(emptyList())
            incomeRepo.replaceAllIncomes(emptyList())
            incomeRepo.replaceAllCategories(emptyList())
            budgetRepo.replaceAllBudgets(emptyList())
            budgetRepo.replaceAllCategories(emptyList())
            expenseRepo.replaceAllExpenses(emptyList())
            expenseCatRepo.replaceAllCategories(emptyList())
            savingsRepo.replaceAllSavings(emptyList())
            savingsRepo.replaceAllCategories(emptyList())

            assertTrue(periodRepo.getAllPeriods().first().isEmpty())
            assertTrue(expenseRepo.getAllExpenses().first().isEmpty())
            assertTrue(expenseCatRepo.getAllCategories().first().isEmpty())
            assertTrue(savingsRepo.getAllSavings().first().isEmpty())

            // 3. Duplicate category names with identical subcategories must not duplicate rows
            val duplicateCategories = listOf(
                Category("Comida", "🍔", "#E74A3B", listOf(Subcategory("Super"), Subcategory("Super"))),
                Category("Comida", "🍕", "#1CC88A", listOf(Subcategory("Super")))
            )
            expenseCatRepo.replaceAllCategories(duplicateCategories)
            val storedCats = expenseCatRepo.getAllCategories().first()
            assertEquals(1, storedCats.size)
            assertEquals(
                "Duplicate subcategories under the same category must be deduplicated on replaceAllCategories",
                1,
                storedCats.single().subcategories.size
            )
        } finally {
            configDb.close()
            appDb.close()
            budgetDb.close()
            savingsDb.close()
        }
    }

    // =========================================================================
    // SECTION 5: Adversarial Tests for BackupReminderReceiver & AndroidBackupReminderScheduler
    // =========================================================================

    /**
     * Helper that picks a deterministic UTC-offset TimeZone where `System.currentTimeMillis()` is
     * guaranteed to fall at 12:00 noon (strictly inside daytime 08:00–21:59) regardless of when
     * the test suite is executed.
     */
    private fun daytimeTimeZoneForNow(): TimeZone {
        val utcHour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY)
        var offsetHours = 12 - utcHour
        if (offsetHours > 12) offsetHours -= 24
        if (offsetHours < -12) offsetHours += 24
        val sign = if (offsetHours >= 0) "+" else "-"
        val absHours = kotlin.math.abs(offsetHours)
        return TimeZone.getTimeZone(String.format("GMT%s%02d:00", sign, absHours))
    }

    /**
     * Adversarial Edge Case [DEFECT EXPOSURE — Missing Intent Action Validation in Receiver]:
     * `BackupReminderReceiver.onReceive` only checks `intent?.action == Intent.ACTION_BOOT_COMPLETED`.
     * Any `null` intent or unexpected/spoofed action triggers `showReminderNotification` instead of
     * strictly matching `AndroidBackupReminderScheduler.ACTION_BACKUP_REMINDER`.
     */
    @Test
    fun adversarial_reminderReceiver_nullOrUnexpectedIntentAction_mustNotPostNotification() {
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(daytimeTimeZoneForNow())
        try {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            val prefs = app.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            val eightDaysAgo = System.currentTimeMillis() - BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS - 24L * 3600 * 1000
            prefs.edit().clear()
                .putLong(SharedPrefsBackupPreferenceRepository.KEY_LAST_BACKUP_TIMESTAMP, eightDaysAgo)
                .commit()

            val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shadowNotif = Shadows.shadowOf(notifManager)
            notifManager.cancelAll()

            val receiver = BackupReminderReceiver()
            receiver.onReceive(app, null)
            receiver.onReceive(app, Intent("com.example.UNRELATED_SPOOFED_ACTION"))

            assertTrue(
                "Null or unrelated Intent actions must NOT display backup reminder notifications",
                shadowNotif.allNotifications.isEmpty()
            )
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    /**
     * Adversarial Edge Case — `ACTION_BOOT_COMPLETED`, Missing Timestamp, & Missing Permission:
     * 1. When no backup timestamp exists, `onReceive` is a complete no-op (no notification, no alarm).
     * 2. On `ACTION_BOOT_COMPLETED` with an overdue backup, receiver MUST NOT show a notification
     *    but MUST reschedule the next alarm via `AlarmManager`.
     * 3. On API 34 without `POST_NOTIFICATIONS` permission, receiver MUST NOT crash or post a
     *    notification, yet MUST still reschedule the next alarm.
     */
    @Test
    fun adversarial_reminderReceiver_bootCompleted_noBackup_andDeniedPermission() {
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(daytimeTimeZoneForNow())
        try {
            val app = RuntimeEnvironment.getApplication()
            val prefs = app.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shadowAlarm = Shadows.shadowOf(alarmManager)
            val shadowNotif = Shadows.shadowOf(notifManager)
            val receiver = BackupReminderReceiver()

            // Case 1: No backup ever saved -> strict no-op
            prefs.edit().clear().commit()
            notifManager.cancelAll()
            receiver.onReceive(app, Intent(AndroidBackupReminderScheduler.ACTION_BACKUP_REMINDER))
            assertTrue("No notification when never backed up", shadowNotif.allNotifications.isEmpty())
            assertNull("No alarm scheduled when never backed up", shadowAlarm.nextScheduledAlarm)

            // Case 2: Overdue backup + ACTION_BOOT_COMPLETED -> reschedules alarm WITHOUT notifying
            val eightDaysAgo = System.currentTimeMillis() - BackupReminderScheduleCalculator.SEVEN_DAYS_MILLIS - 24L * 3600 * 1000
            prefs.edit()
                .putLong(SharedPrefsBackupPreferenceRepository.KEY_LAST_BACKUP_TIMESTAMP, eightDaysAgo)
                .commit()
            Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
            assertTrue("BOOT_COMPLETED must not immediately post notification", shadowNotif.allNotifications.isEmpty())
            assertNotNull("BOOT_COMPLETED must reschedule reminder alarm", shadowAlarm.nextScheduledAlarm)

            // Case 3: Overdue backup + POST_NOTIFICATIONS denied on API 34 -> no crash, no notif, alarm rescheduled
            Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            notifManager.cancelAll()
            receiver.onReceive(app, Intent(AndroidBackupReminderScheduler.ACTION_BACKUP_REMINDER))
            assertTrue("Missing POST_NOTIFICATIONS must suppress notification without crashing", shadowNotif.allNotifications.isEmpty())
            assertNotNull("Reminder alarm must still be rescheduled even when permission is denied", shadowAlarm.nextScheduledAlarm)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    /**
     * Adversarial Edge Case — `AndroidBackupReminderScheduler` Idempotency & Cancellation:
     * Calling `scheduleNextReminder` multiple times replaces the single pending alarm (no duplicates),
     * and calling `scheduleNextReminder(null)` cancels the existing alarm cleanly.
     */
    @Test
    fun adversarial_reminderScheduler_idempotentSchedulingAndNullCancellation() {
        val app = RuntimeEnvironment.getApplication()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        val fixedNow = 1_764_100_000_000L
        val scheduler = AndroidBackupReminderScheduler(app, nowProvider = { fixedNow })

        scheduler.scheduleNextReminder(fixedNow)
        scheduler.scheduleNextReminder(fixedNow + 60_000L)
        scheduler.scheduleNextReminder(fixedNow + 120_000L)
        assertEquals(
            "Repeated scheduleNextReminder calls must update the single alarm, not accumulate duplicates",
            1,
            shadowAlarm.scheduledAlarms.size
        )

        scheduler.scheduleNextReminder(null)
        assertTrue(
            "scheduleNextReminder(null) must cancel any scheduled reminder alarm",
            shadowAlarm.scheduledAlarms.isEmpty()
        )
    }

    // =========================================================================
    // SECTION 6: Adversarial Compose UI Tests
    // =========================================================================

    /**
     * Adversarial UI Edge Case — Busy State Button Locking:
     * Verifies BOTH `btn_create_backup` and `btn_restore_backup` are strictly disabled whenever
     * `backupStatus == IN_PROGRESS`, `restoreStatus == READING`, or `restoreStatus == RESTORING`.
     */
    @Test
    fun adversarial_backupSectionCard_disablesBothButtonsWhenAnyOperationIsBusy() {
        composeTestRule.setContent {
            BackupSectionCard(
                title = "Respaldo",
                description = "Desc",
                lastBackupLabel = "Último respaldo",
                neverBackedUpText = "Sin respaldos",
                actionButtonLabel = "Respaldar",
                inProgressLabel = "Generando...",
                successMessage = "OK",
                errorMessage = "ERR",
                cancelledMessage = "CANCEL",
                lastBackupTimestamp = null,
                backupStatus = BackupStatus.IN_PROGRESS,
                restoreStatus = RestoreStatus.IDLE,
                onCreateBackupClick = {},
                onRestoreBackupClick = {}
            )
        }

        // Backup IN_PROGRESS -> both buttons disabled
        composeTestRule.onNodeWithTag("btn_create_backup").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("btn_restore_backup").assertIsNotEnabled()
    }

    @Test
    fun adversarial_backupSectionCard_disablesBothButtonsWhenRestoreIsRestoring_andEnablesWhenIdle() {
        composeTestRule.setContent {
            BackupSectionCard(
                title = "Respaldo",
                description = "Desc",
                lastBackupLabel = "Último respaldo",
                neverBackedUpText = "Sin respaldos",
                actionButtonLabel = "Respaldar",
                inProgressLabel = "Generando...",
                successMessage = "OK",
                errorMessage = "ERR",
                cancelledMessage = "CANCEL",
                lastBackupTimestamp = 1_764_100_000_000L,
                backupStatus = BackupStatus.SUCCESS,
                restoreStatus = RestoreStatus.RESTORING,
                onCreateBackupClick = {},
                onRestoreBackupClick = {}
            )
        }

        composeTestRule.onNodeWithTag("btn_create_backup").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("btn_restore_backup").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("text_backup_feedback").assertTextEquals("OK")
    }

    /**
     * Adversarial UI Edge Case [DEFECT EXPOSURE — Epoch `1970-01-01` Rendered for `lastBackupTimestamp <= 0`]:
     * `RestoreBackupConfirmationDialog` checks `if (snapshot.exportedAtMillis > 0L)` and displays `"-"`,
     * whereas `BackupSectionCard` formats ANY non-null Long — rendering `"1970-01-01 ..."` (or `"1969-12-31 ..."`)
     * for `0L` or negative timestamps instead of `neverBackedUpText`!
     */
    @Test
    fun adversarial_backupSectionCard_zeroOrNegativeTimestamp_displaysNeverBackedUpInsteadOf1970() {
        composeTestRule.setContent {
            BackupSectionCard(
                title = "Respaldo",
                description = "Desc",
                lastBackupLabel = "Último respaldo",
                neverBackedUpText = "Sin respaldos",
                actionButtonLabel = "Respaldar",
                inProgressLabel = "Generando...",
                successMessage = "OK",
                errorMessage = "ERR",
                cancelledMessage = "CANCEL",
                lastBackupTimestamp = 0L,
                backupStatus = BackupStatus.IDLE,
                restoreStatus = RestoreStatus.IDLE,
                onCreateBackupClick = {},
                onRestoreBackupClick = {}
            )
        }

        composeTestRule.onNodeWithTag("btn_create_backup").assertIsEnabled()
        composeTestRule.onNodeWithTag("btn_restore_backup").assertIsEnabled()
        composeTestRule.onNodeWithTag("text_last_backup_status").assertTextEquals("Sin respaldos")
    }

    /**
     * Adversarial UI Edge Case — Broken `String.format` i18n Template & `exportedAtMillis = 0L`:
     * Verifies `SettingsView` + `RestoreBackupConfirmationDialog` never crash on malformed format
     * specifiers (`%d %99$z`) in translations and display `"-"` for `exportedAtMillis <= 0L`.
     */
    @Test
    fun adversarial_restoreConfirmationDialog_brokenI18nFormatAndZeroExportDate_rendersWithoutCrash() {
        val brokenI18n = I18nManager().apply {
            loadTranslation(
                "es",
                """
                {
                  "settings": {
                    "header": { "title": "Configuración" },
                    "language_section": { "title": "Idioma", "description": "Desc" },
                    "currency_section": { "title": "Moneda", "description": "Desc" },
                    "backup_section": {
                      "restore_dialog_summary": "Corrupt format %99${'$'}z %d",
                      "restore_dialog_date_label": "Fecha"
                    }
                  }
                }
                """.trimIndent()
            )
            setLanguage("es")
        }

        val harness = BackupHarness()
        val vm = SettingsViewModel(
            languageRepository = harness.langRepo,
            i18nManager = brokenI18n,
            currencyRepository = FakeCurrencyRepository(defaultCurrencies),
            currencyPreferenceRepository = harness.currencyRepo,
            backupUseCase = harness.buildUseCase(),
            backupPreferenceRepository = harness.backupPrefs
        )

        val zeroTimestampJson = buildMinimalValidBackupJson(exportedAtMillis = 0L)

        composeTestRule.setContent {
            SettingsView(
                settingsViewModel = vm,
                i18nManager = brokenI18n
            )
        }

        composeTestRule.runOnUiThread {
            vm.prepareRestore { zeroTimestampJson }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_restore_backup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_restore_dialog_date").assertTextContains("Fecha: -")
        composeTestRule.onNodeWithTag("text_restore_dialog_summary")
            .assertTextEquals("Corrupt format %99\$z %d")
    }

    /**
     * Adversarial UI Edge Case — Deep Link (`requestedTab`) Under Locked Period:
     * 1. When no active period exists (`isPeriodLocked == true`) and user taps a backup reminder
     *    notification (`requestedTab = AppTab.SETTINGS`), `MainContainer` MUST open Settings and
     *    invoke `onRequestedTabConsumed()`.
     * 2. Conversely, if `requestedTab = AppTab.INCOMES` while `isPeriodLocked == true`, `MainContainer`
     *    MUST block access to Incomes and keep the user on `period_screen`.
     */
    @Test
    fun adversarial_mainContainer_requestedTabSettingsAllowedWhenPeriodLocked() {
        val i18n = I18nManager().apply {
            loadTranslation(
                "es",
                """
                {
                  "navigation": { "tab_incomes": "Contabilidad", "tab_settings": "Configuración", "tab_period": "Período" },
                  "settings": {
                    "header": { "title": "Configuración" },
                    "language_section": { "title": "Idioma", "description": "Desc" },
                    "currency_section": { "title": "Moneda", "description": "Desc" }
                  },
                  "period": { "title": "Período", "start_date": "Inicio", "end_date": "Fin", "save_button": "Guardar" }
                }
                """.trimIndent()
            )
            setLanguage("es")
        }
        val settingsVm = SettingsViewModel(AdversarialLangPrefRepo("es"), i18n)
        val periodVm = PeriodViewModel(
            PeriodUseCase(AdversarialPeriodRepo(emptyList()), currentDateProvider = { "2026-11-26" })
        )
        var consumed = false

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsVm,
                periodViewModel = periodVm,
                i18nManager = i18n,
                requestedTab = AppTab.SETTINGS,
                onRequestedTabConsumed = { consumed = true },
                incomesContent = { Text("INCOMES_CONTENT") }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        assertTrue("requestedTab=SETTINGS must be consumed even when period is locked", consumed)
    }

    @Test
    fun adversarial_mainContainer_requestedTabIncomesBlockedWhenPeriodLocked() {
        val i18n = I18nManager().apply {
            loadTranslation(
                "es",
                """
                {
                  "navigation": { "tab_incomes": "Contabilidad", "tab_settings": "Configuración", "tab_period": "Período" },
                  "period": { "title": "Período", "start_date": "Inicio", "end_date": "Fin", "save_button": "Guardar" }
                }
                """.trimIndent()
            )
            setLanguage("es")
        }
        val settingsVm = SettingsViewModel(AdversarialLangPrefRepo("es"), i18n)
        val periodVm = PeriodViewModel(
            PeriodUseCase(AdversarialPeriodRepo(emptyList()), currentDateProvider = { "2026-11-26" })
        )
        var consumed = false

        composeTestRule.setContent {
            MainContainer(
                settingsViewModel = settingsVm,
                periodViewModel = periodVm,
                i18nManager = i18n,
                requestedTab = AppTab.INCOMES,
                onRequestedTabConsumed = { consumed = true },
                incomesContent = { Text("INCOMES_CONTENT") }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("period_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("INCOMES_CONTENT").assertDoesNotExist()
        assertFalse("Blocked requestedTab=INCOMES should not be marked consumed while period is locked", consumed)
    }
}
