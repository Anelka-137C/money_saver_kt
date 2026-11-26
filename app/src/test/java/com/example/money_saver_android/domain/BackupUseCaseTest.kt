package com.example.money_saver_android.domain

import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.BackupResult
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.domain.usecase.RestoreParseResult
import com.example.money_saver_android.domain.usecase.RestoreResult
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeIncomeRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupUseCaseTest {

    private lateinit var periodRepo: FakeFinancialPeriodRepository
    private lateinit var incomeRepo: FakeIncomeRepository
    private lateinit var budgetRepo: FakeBudgetRepository
    private lateinit var expenseRepo: FakeExpenseRepository
    private lateinit var expenseCatRepo: FakeCategoryRepoForVm
    private lateinit var savingsRepo: FakeSavingsRepository
    private lateinit var backupPrefs: FakeBackupPreferenceRepository
    private var currentNow = 1_764_100_000_000L

    @Before
    fun setUp() {
        periodRepo = FakeFinancialPeriodRepository(
            initialPeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        )
        incomeRepo = FakeIncomeRepository()
        budgetRepo = FakeBudgetRepository()
        expenseRepo = FakeExpenseRepository()
        expenseCatRepo = FakeCategoryRepoForVm()
        savingsRepo = FakeSavingsRepository()
        backupPrefs = FakeBackupPreferenceRepository()
    }

    private fun createUseCase(): BackupUseCase = BackupUseCase(
        periodRepository = periodRepo,
        incomeRepository = incomeRepo,
        budgetRepository = budgetRepo,
        expenseRepository = expenseRepo,
        expenseCategoryRepository = expenseCatRepo,
        savingsRepository = savingsRepo,
        languagePreferenceRepository = object : LanguagePreferenceRepository {
            override fun getLanguage(): String = "es"
            override fun saveLanguage(languageCode: String) {}
        },
        currencyPreferenceRepository = object : CurrencyPreferenceRepository {
            override fun getCurrency(): String = "USD"
            override fun saveCurrency(currencyCode: String) {}
        },
        backupPreferenceRepository = backupPrefs,
        nowProvider = { currentNow }
    )

    @Test
    fun exportBackup_packagesAllDataAndPreferences_andUpdatesTimestampOnSuccess() = runBlocking {
        incomeRepo.insertCategory(Category("Salario", "💼", "#4E73DF", listOf(Subcategory("Nómina"))))
        incomeRepo.insertIncome(Income(1L, 3000.0, "2026-11-01 09:00", "Salario", "Nómina", "Pago", 1L))
        budgetRepo.insertCategory(Category("Hogar", "🏠", "#1CC88A", emptyList()))
        budgetRepo.insertBudget(Budget(1L, 800.0, "Hogar", "", 1L))
        savingsRepo.insertCategory(Category("Viajes", "✈️", "#36B9CC", listOf(Subcategory("Vuelos"))))
        savingsRepo.insertSaving(Saving(1L, 500.0, "Viajes", "Vuelos", 1L, 1L, "Salario", "2026-11-05 10:00", "Meta"))
        expenseCatRepo.insertCategory(Category("Alimentación", "🍔", "#E74A3B", emptyList()))
        expenseRepo.insertExpense(
            Expense(1L, 120.0, "2026-11-10", "Cena", "Alimentación", "", 1L, 1L, "Hogar", ExpenseFundingSource.BUDGET, "")
        )

        var writtenJson = ""
        val result = createUseCase().exportBackup { writtenJson = it }

        assertEquals(BackupResult.Success(currentNow), result)
        assertEquals(currentNow, backupPrefs.getLastBackupTimestamp())

        val root = JSONObject(writtenJson)
        assertEquals(BackupUseCase.SCHEMA_VERSION, root.getInt("schemaVersion"))
        assertEquals(currentNow, root.getLong("exportedAtMillis"))
        assertEquals("es", root.getJSONObject("preferences").getString("language"))
        assertEquals("USD", root.getJSONObject("preferences").getString("currency"))
        assertEquals(1, root.getJSONArray("periods").length())
        assertEquals(1, root.getJSONArray("incomes").length())
        assertEquals(1, root.getJSONArray("budgets").length())
        assertEquals(1, root.getJSONArray("expenses").length())
        assertEquals(1, root.getJSONArray("savings").length())
        assertEquals(1, root.getJSONObject("categories").getJSONArray("incomes").length())
        assertEquals(1, root.getJSONObject("categories").getJSONArray("budget").length())
        assertEquals(1, root.getJSONObject("categories").getJSONArray("savings").length())
        assertTrue(root.getJSONObject("categories").getJSONArray("expenses").length() > 0)
    }

    @Test
    fun exportBackup_whenWriteFails_returnsErrorAndDoesNotUpdateTimestamp() = runBlocking {
        val result = createUseCase().exportBackup {
            throw IllegalStateException("Disk full")
        }

        assertTrue(result is BackupResult.Error)
        assertNull(backupPrefs.getLastBackupTimestamp())
    }

    @Test
    fun parseAndRestoreBackup_roundTripsAllDataAndPreservesLastBackupTimestamp() = runBlocking {
        var savedLanguage = "es"
        var savedCurrency = "USD"
        val existingUploadTimestamp = 1_764_000_000_000L
        backupPrefs.saveLastBackupTimestamp(existingUploadTimestamp)

        incomeRepo.insertCategory(Category("Salario", "💼", "#4E73DF", listOf(Subcategory("Nómina"))))
        incomeRepo.insertIncome(Income(1L, 3000.0, "2026-11-01 09:00", "Salario", "Nómina", "Pago", 1L))
        budgetRepo.insertCategory(Category("Hogar", "🏠", "#1CC88A", emptyList()))
        budgetRepo.insertBudget(Budget(1L, 800.0, "Hogar", "", 1L))
        savingsRepo.insertCategory(Category("Viajes", "✈️", "#36B9CC", listOf(Subcategory("Vuelos"))))
        savingsRepo.insertSaving(Saving(1L, 500.0, "Viajes", "Vuelos", 1L, 1L, "Salario", "2026-11-05 10:00", "Meta"))
        expenseCatRepo.insertCategory(Category("Alimentación", "🍔", "#E74A3B", emptyList()))
        expenseRepo.insertExpense(
            Expense(1L, 120.0, "2026-11-10", "Cena", "Alimentación", "", 1L, 1L, "Hogar", ExpenseFundingSource.BUDGET, "")
        )

        val json = createUseCase().buildBackupJson(exportedAtMillis = 1_764_200_000_000L)

        var restoredPeriods = 0
        var restoredIncomes = 0
        var restoredBudgets = 0
        var restoredExpenses = 0
        var restoredSavings = 0
        val targetUseCase = BackupUseCase(
            periodRepository = object : com.example.money_saver_android.data.repository.FinancialPeriodRepository by periodRepo {
                override suspend fun replaceAllPeriods(periods: List<FinancialPeriod>) { restoredPeriods = periods.size }
            },
            incomeRepository = object : com.example.money_saver_android.data.repository.IncomeRepository by incomeRepo {
                override suspend fun replaceAllIncomes(incomes: List<Income>) { restoredIncomes = incomes.size }
                override suspend fun replaceAllCategories(categories: List<Category>) {}
            },
            budgetRepository = object : com.example.money_saver_android.data.repository.BudgetRepository by budgetRepo {
                override suspend fun replaceAllBudgets(budgets: List<Budget>) { restoredBudgets = budgets.size }
                override suspend fun replaceAllCategories(categories: List<Category>) {}
            },
            expenseRepository = object : com.example.money_saver_android.data.repository.ExpenseRepository by expenseRepo {
                override suspend fun replaceAllExpenses(expenses: List<Expense>) { restoredExpenses = expenses.size }
            },
            expenseCategoryRepository = expenseCatRepo,
            savingsRepository = object : com.example.money_saver_android.data.repository.SavingsRepository by savingsRepo {
                override suspend fun replaceAllSavings(savings: List<Saving>) { restoredSavings = savings.size }
                override suspend fun replaceAllCategories(categories: List<Category>) {}
            },
            languagePreferenceRepository = object : LanguagePreferenceRepository {
                override fun getLanguage(): String = savedLanguage
                override fun saveLanguage(languageCode: String) { savedLanguage = languageCode }
            },
            currencyPreferenceRepository = object : CurrencyPreferenceRepository {
                override fun getCurrency(): String = savedCurrency
                override fun saveCurrency(currencyCode: String) { savedCurrency = currencyCode }
            },
            backupPreferenceRepository = backupPrefs
        )

        val parsed = targetUseCase.parseBackupJson(json)
        assertTrue(parsed is RestoreParseResult.Valid)
        val snapshot = (parsed as RestoreParseResult.Valid).snapshot
        assertEquals(1_764_200_000_000L, snapshot.exportedAtMillis)

        savedLanguage = "en"
        savedCurrency = "COP"
        val restoreResult = targetUseCase.restoreBackup(snapshot)
        assertTrue(restoreResult is RestoreResult.Success)
        assertEquals(1, restoredPeriods)
        assertEquals(1, restoredIncomes)
        assertEquals(1, restoredBudgets)
        assertEquals(1, restoredExpenses)
        assertEquals(1, restoredSavings)
        assertEquals("es", savedLanguage)
        assertEquals("USD", savedCurrency)
        // lastBackupTimestamp MUST NOT change on restore
        assertEquals(existingUploadTimestamp, backupPrefs.getLastBackupTimestamp())
    }

    @Test
    fun parseBackupJson_rejectsMalformedOrUnsupportedSchema() {
        val uc = createUseCase()
        assertTrue(uc.parseBackupJson("not-json") is RestoreParseResult.Invalid)
        assertTrue(uc.parseBackupJson("""{"schemaVersion":99,"exportedAtMillis":1}""") is RestoreParseResult.Invalid)
        assertTrue(uc.parseBackupJson("""{"schemaVersion":1,"exportedAtMillis":1}""") is RestoreParseResult.Invalid)
    }

    private class FakeBackupPreferenceRepository : BackupPreferenceRepository {
        private var timestamp: Long? = null
        override fun getLastBackupTimestamp(): Long? = timestamp
        override fun saveLastBackupTimestamp(timestampMillis: Long) {
            timestamp = timestampMillis
        }
    }
}
