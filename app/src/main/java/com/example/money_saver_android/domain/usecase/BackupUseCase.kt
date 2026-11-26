package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class BackupResult {
    data class Success(val timestampMillis: Long) : BackupResult()
    data class Error(val cause: Throwable? = null) : BackupResult()
}

data class BackupSnapshot(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val language: String,
    val currency: String,
    val periods: List<FinancialPeriod>,
    val incomes: List<Income>,
    val budgets: List<Budget>,
    val expenses: List<Expense>,
    val savings: List<Saving>,
    val incomeCategories: List<Category>,
    val budgetCategories: List<Category>,
    val expenseCategories: List<Category>,
    val savingCategories: List<Category>
)

sealed class RestoreParseResult {
    data class Valid(val snapshot: BackupSnapshot) : RestoreParseResult()
    data class Invalid(val cause: Throwable? = null) : RestoreParseResult()
}

sealed class RestoreResult {
    data class Success(val snapshot: BackupSnapshot) : RestoreResult()
    data class Error(val cause: Throwable? = null) : RestoreResult()
}

/**
 * Collects a consistent snapshot of all financial records, categories, and user preferences
 * into a portable JSON payload named `money_saver_backup`, updating the last backup timestamp
 * only when the caller confirms the write succeeded.
 */
class BackupUseCase(
    private val periodRepository: FinancialPeriodRepository,
    private val incomeRepository: IncomeRepository,
    private val budgetRepository: BudgetRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseCategoryRepository: CategoryRepository,
    private val savingsRepository: SavingsRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository,
    private val backupPreferenceRepository: BackupPreferenceRepository,
    private val allocationMutex: Mutex = FinancialAllocationLock.mutex,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val BACKUP_FILE_NAME = "money_saver_backup"
        const val SCHEMA_VERSION = 1
        private val SUPPORTED_CURRENCIES: Set<String> = defaultCurrencies.map { it.code }.toSet()
    }

    fun getLastBackupTimestamp(): Long? = backupPreferenceRepository.getLastBackupTimestamp()

    suspend fun exportBackup(writeContent: suspend (String) -> Unit): BackupResult {
        return try {
            val exportedAt = nowProvider()
            val jsonPayload = buildBackupJson(exportedAt)
            writeContent(jsonPayload)
            backupPreferenceRepository.saveLastBackupTimestamp(exportedAt)
            BackupResult.Success(exportedAt)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            BackupResult.Error(e)
        }
    }

    fun parseBackupJson(rawJson: String): RestoreParseResult {
        return try {
            val root = JSONObject(rawJson)
            val schemaVersion = root.getInt("schemaVersion")
            if (schemaVersion !in 1..SCHEMA_VERSION) {
                return RestoreParseResult.Invalid(IllegalArgumentException("Unsupported schemaVersion: $schemaVersion"))
            }
            val exportedAtMillis = root.getLong("exportedAtMillis")
            val prefsObj = root.getJSONObject("preferences")
            val language = prefsObj.optStringOrEmpty("language", "")
            val currency = prefsObj.optStringOrEmpty("currency", "")

            val periodsArray = root.getJSONArray("periods")
            val periods = (0 until periodsArray.length()).map { idx ->
                val obj = periodsArray.getJSONObject(idx)
                val startDate = obj.requireNonBlankString("startDate")
                val endDate = obj.requireNonBlankString("endDate")
                require(startDate <= endDate) { "Invalid period date range: $startDate > $endDate" }
                FinancialPeriod(
                    id = obj.getLong("id"),
                    startDate = startDate,
                    endDate = endDate
                )
            }

            val incomesArray = root.getJSONArray("incomes")
            val incomes = (0 until incomesArray.length()).map { idx ->
                val obj = incomesArray.getJSONObject(idx)
                val amount = obj.requirePositiveAmount("amount")
                Income(
                    id = obj.getLong("id"),
                    amount = amount,
                    dateTime = obj.requireNonBlankString("dateTime"),
                    category = obj.requireNonBlankString("category"),
                    subcategory = obj.optStringOrEmpty("subcategory", ""),
                    description = obj.optStringOrEmpty("description", ""),
                    periodId = obj.getLong("periodId")
                )
            }

            val budgetsArray = root.getJSONArray("budgets")
            val budgets = (0 until budgetsArray.length()).map { idx ->
                val obj = budgetsArray.getJSONObject(idx)
                val amount = obj.requirePositiveAmount("amount")
                Budget(
                    id = obj.getLong("id"),
                    amount = amount,
                    category = obj.requireNonBlankString("category"),
                    subcategory = obj.optStringOrEmpty("subcategory", ""),
                    periodId = obj.getLong("periodId")
                )
            }

            val expensesArray = root.getJSONArray("expenses")
            val expenses = (0 until expensesArray.length()).map { idx ->
                val obj = expensesArray.getJSONObject(idx)
                val amount = obj.requirePositiveAmount("amount")
                Expense(
                    id = obj.getLong("id"),
                    amount = amount,
                    date = obj.requireNonBlankString("date"),
                    description = obj.optStringOrEmpty("description", ""),
                    category = obj.requireNonBlankString("category"),
                    subcategory = obj.optStringOrEmpty("subcategory", ""),
                    periodId = obj.optLong("periodId", 0L),
                    budgetId = obj.optLong("budgetId", 0L),
                    budgetCategory = obj.optStringOrEmpty("budgetCategory", ""),
                    fundingSource = ExpenseFundingSource.fromStorage(obj.optStringOrEmpty("fundingSource", "BUDGET")),
                    savingCategory = obj.optStringOrEmpty("savingCategory", "")
                )
            }

            val savingsArray = root.getJSONArray("savings")
            val savings = (0 until savingsArray.length()).map { idx ->
                val obj = savingsArray.getJSONObject(idx)
                val amount = obj.requirePositiveAmount("amount")
                Saving(
                    id = obj.getLong("id"),
                    amount = amount,
                    category = obj.requireNonBlankString("category"),
                    subcategory = obj.optStringOrEmpty("subcategory", ""),
                    periodId = obj.getLong("periodId"),
                    incomeId = obj.optLong("incomeId", 0L),
                    incomeCategory = obj.optStringOrEmpty("incomeCategory", ""),
                    dateTime = obj.optStringOrEmpty("dateTime", ""),
                    note = obj.optStringOrEmpty("note", "")
                )
            }

            val categoriesObj = root.getJSONObject("categories")
            val incomeCategories = parseCategoriesArray(categoriesObj.getJSONArray("incomes"))
            val budgetCategories = parseCategoriesArray(categoriesObj.getJSONArray("budget"))
            val expenseCategories = parseCategoriesArray(categoriesObj.getJSONArray("expenses"))
            val savingCategories = parseCategoriesArray(categoriesObj.getJSONArray("savings"))

            RestoreParseResult.Valid(
                BackupSnapshot(
                    schemaVersion = schemaVersion,
                    exportedAtMillis = exportedAtMillis,
                    language = language,
                    currency = currency,
                    periods = periods,
                    incomes = incomes,
                    budgets = budgets,
                    expenses = expenses,
                    savings = savings,
                    incomeCategories = incomeCategories,
                    budgetCategories = budgetCategories,
                    expenseCategories = expenseCategories,
                    savingCategories = savingCategories
                )
            )
        } catch (e: Exception) {
            RestoreParseResult.Invalid(e)
        }
    }

    /**
     * Replaces all local financial data, categories, and language/currency preferences
     * from a validated [BackupSnapshot]. Intentionally does NOT alter `lastBackupTimestamp`,
     * which tracks only the user's latest upload to Google Drive.
     *
     * Captures a pre-restore rollback snapshot so that any mid-flight failure across the
     * separate Room databases restores the prior consistent local state.
     */
    suspend fun restoreBackup(snapshot: BackupSnapshot): RestoreResult {
        return try {
            allocationMutex.withLock {
                val prevPeriods = periodRepository.getAllPeriods().firstOrNull().orEmpty()
                val prevPeriodIds = prevPeriods.map { it.id }.distinct()
                val prevIncomeCats = incomeRepository.getAllCategories().firstOrNull().orEmpty()
                val prevBudgetCats = budgetRepository.getAllCategories().firstOrNull().orEmpty()
                val prevExpenseCats = expenseCategoryRepository.getAllCategories().firstOrNull().orEmpty()
                val prevSavingCats = savingsRepository.getAllCategories().firstOrNull().orEmpty()
                val prevIncomes = prevPeriodIds.flatMap { periodId ->
                    incomeRepository.getIncomesByPeriod(periodId).firstOrNull().orEmpty()
                }.distinctBy { it.id }
                val prevBudgets = prevPeriodIds.flatMap { periodId ->
                    budgetRepository.getBudgetsByPeriod(periodId).firstOrNull().orEmpty()
                }.distinctBy { it.id }
                val prevExpenses = expenseRepository.getAllExpenses().firstOrNull().orEmpty()
                val prevAllSavings = savingsRepository.getAllSavings().firstOrNull().orEmpty()
                val prevSavings = prevAllSavings.ifEmpty {
                    prevPeriodIds.flatMap { periodId ->
                        savingsRepository.getSavingsByPeriod(periodId).firstOrNull().orEmpty()
                    }.distinctBy { it.id }
                }
                val prevLanguage = languagePreferenceRepository.getLanguage()
                val prevCurrency = currencyPreferenceRepository.getCurrency()

                val rollbackSteps = mutableListOf<suspend () -> Unit>()
                try {
                    rollbackSteps.add { periodRepository.replaceAllPeriods(prevPeriods) }
                    periodRepository.replaceAllPeriods(snapshot.periods)

                    rollbackSteps.add { incomeRepository.replaceAllCategories(prevIncomeCats) }
                    incomeRepository.replaceAllCategories(snapshot.incomeCategories)

                    rollbackSteps.add { budgetRepository.replaceAllCategories(prevBudgetCats) }
                    budgetRepository.replaceAllCategories(snapshot.budgetCategories)

                    rollbackSteps.add { expenseCategoryRepository.replaceAllCategories(prevExpenseCats) }
                    expenseCategoryRepository.replaceAllCategories(snapshot.expenseCategories)

                    rollbackSteps.add { savingsRepository.replaceAllCategories(prevSavingCats) }
                    savingsRepository.replaceAllCategories(snapshot.savingCategories)

                    rollbackSteps.add { incomeRepository.replaceAllIncomes(prevIncomes) }
                    incomeRepository.replaceAllIncomes(snapshot.incomes)

                    rollbackSteps.add { budgetRepository.replaceAllBudgets(prevBudgets) }
                    budgetRepository.replaceAllBudgets(snapshot.budgets)

                    rollbackSteps.add { expenseRepository.replaceAllExpenses(prevExpenses) }
                    expenseRepository.replaceAllExpenses(snapshot.expenses)

                    rollbackSteps.add { savingsRepository.replaceAllSavings(prevSavings) }
                    savingsRepository.replaceAllSavings(snapshot.savings)

                    if (snapshot.language in SharedPrefsLanguageRepository.SUPPORTED_LANGUAGES) {
                        rollbackSteps.add { languagePreferenceRepository.saveLanguage(prevLanguage) }
                        languagePreferenceRepository.saveLanguage(snapshot.language)
                    }
                    if (snapshot.currency in SUPPORTED_CURRENCIES) {
                        rollbackSteps.add { currencyPreferenceRepository.saveCurrency(prevCurrency) }
                        currencyPreferenceRepository.saveCurrency(snapshot.currency)
                    }
                } catch (t: Throwable) {
                    withContext(NonCancellable) {
                        for (rollback in rollbackSteps.asReversed()) {
                            runCatching { rollback() }
                        }
                    }
                    throw t
                }
            }
            RestoreResult.Success(snapshot)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            RestoreResult.Error(e)
        }
    }

    private fun parseCategoriesArray(array: JSONArray): List<Category> {
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            val name = obj.requireNonBlankString("name").trim()
            val subsArray = if (obj.isNull("subcategories")) null else obj.optJSONArray("subcategories")
            val subs = if (subsArray != null) {
                (0 until subsArray.length()).mapNotNull { sIdx ->
                    if (subsArray.isNull(sIdx)) {
                        null
                    } else {
                        subsArray.getString(sIdx).trim().takeIf { it.isNotEmpty() }?.let { Subcategory(it) }
                    }
                }
            } else {
                emptyList()
            }
            Category(
                name = name,
                icon = obj.optStringOrEmpty("icon", "💰").ifBlank { "💰" },
                color = obj.optStringOrEmpty("color", "#4E73DF").ifBlank { "#4E73DF" },
                subcategories = subs
            )
        }
    }

    private fun JSONObject.optStringOrEmpty(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        return optString(key, fallback)
    }

    private fun JSONObject.requireNonBlankString(key: String): String {
        require(!isNull(key)) { "Missing or null required field: $key" }
        val value = getString(key)
        require(value.isNotBlank()) { "Blank required field: $key" }
        return value
    }

    private fun JSONObject.requirePositiveAmount(key: String): Double {
        val amount = getDouble(key)
        require(amount.isFinite() && amount > 0.0) { "Invalid $key amount: $amount" }
        return amount
    }

    suspend fun buildBackupJson(exportedAtMillis: Long = nowProvider()): String = allocationMutex.withLock {
        val periods = periodRepository.getAllPeriods().firstOrNull().orEmpty()
        val uniquePeriodIds = periods.map { it.id }.distinct()
        val incomes = uniquePeriodIds.flatMap { periodId ->
            incomeRepository.getIncomesByPeriod(periodId).firstOrNull().orEmpty()
        }.distinctBy { it.id }
        val budgets = uniquePeriodIds.flatMap { periodId ->
            budgetRepository.getBudgetsByPeriod(periodId).firstOrNull().orEmpty()
        }.distinctBy { it.id }
        val expenses = expenseRepository.getAllExpenses().firstOrNull().orEmpty()
        val allSavings = savingsRepository.getAllSavings().firstOrNull().orEmpty()
        val savings = if (allSavings.isNotEmpty()) {
            allSavings
        } else {
            uniquePeriodIds.flatMap { periodId ->
                savingsRepository.getSavingsByPeriod(periodId).firstOrNull().orEmpty()
            }.distinctBy { it.id }
        }

        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAtMillis", exportedAtMillis)
            put("preferences", JSONObject().apply {
                put("language", languagePreferenceRepository.getLanguage())
                put("currency", currencyPreferenceRepository.getCurrency())
            })
            put("periods", JSONArray().apply {
                periods.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("startDate", p.startDate)
                        put("endDate", p.endDate)
                    })
                }
            })
            put("incomes", JSONArray().apply {
                incomes.forEach { i ->
                    put(JSONObject().apply {
                        put("id", i.id)
                        put("amount", i.amount)
                        put("dateTime", i.dateTime)
                        put("category", i.category)
                        put("subcategory", i.subcategory)
                        put("description", i.description)
                        put("periodId", i.periodId)
                    })
                }
            })
            put("budgets", JSONArray().apply {
                budgets.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("amount", b.amount)
                        put("category", b.category)
                        put("subcategory", b.subcategory)
                        put("periodId", b.periodId)
                    })
                }
            })
            put("expenses", JSONArray().apply {
                expenses.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("amount", e.amount)
                        put("date", e.date)
                        put("description", e.description)
                        put("category", e.category)
                        put("subcategory", e.subcategory)
                        put("periodId", e.periodId)
                        put("budgetId", e.budgetId)
                        put("budgetCategory", e.budgetCategory)
                        put("fundingSource", e.fundingSource.name)
                        put("savingCategory", e.savingCategory)
                    })
                }
            })
            put("savings", JSONArray().apply {
                savings.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("amount", s.amount)
                        put("category", s.category)
                        put("subcategory", s.subcategory)
                        put("periodId", s.periodId)
                        put("incomeId", s.incomeId)
                        put("incomeCategory", s.incomeCategory)
                        put("dateTime", s.dateTime)
                        put("note", s.note)
                    })
                }
            })
            put("categories", JSONObject().apply {
                put("incomes", categoriesToJsonArray(incomeRepository.getAllCategories().firstOrNull().orEmpty()))
                put("budget", categoriesToJsonArray(budgetRepository.getAllCategories().firstOrNull().orEmpty()))
                put("expenses", categoriesToJsonArray(expenseCategoryRepository.getAllCategories().firstOrNull().orEmpty()))
                put("savings", categoriesToJsonArray(savingsRepository.getAllCategories().firstOrNull().orEmpty()))
            })
        }
        root.toString(2)
    }

    private fun categoriesToJsonArray(categories: List<Category>): JSONArray = JSONArray().apply {
        categories.forEach { cat ->
            put(JSONObject().apply {
                put("name", cat.name)
                put("icon", cat.icon)
                put("color", cat.color)
                put("subcategories", JSONArray().apply {
                    cat.subcategories.forEach { sub -> put(sub.name) }
                })
            })
        }
    }
}
