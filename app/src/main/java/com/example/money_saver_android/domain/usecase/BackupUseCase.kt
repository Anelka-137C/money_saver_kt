package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            val language = prefsObj.optString("language", "")
            val currency = prefsObj.optString("currency", "")

            val periodsArray = root.getJSONArray("periods")
            val periods = (0 until periodsArray.length()).map { idx ->
                val obj = periodsArray.getJSONObject(idx)
                FinancialPeriod(
                    id = obj.getLong("id"),
                    startDate = obj.getString("startDate"),
                    endDate = obj.getString("endDate")
                )
            }

            val incomesArray = root.getJSONArray("incomes")
            val incomes = (0 until incomesArray.length()).map { idx ->
                val obj = incomesArray.getJSONObject(idx)
                val amount = obj.getDouble("amount")
                require(amount.isFinite()) { "Invalid income amount" }
                Income(
                    id = obj.getLong("id"),
                    amount = amount,
                    dateTime = obj.getString("dateTime"),
                    category = obj.getString("category"),
                    subcategory = obj.optString("subcategory", ""),
                    description = obj.optString("description", ""),
                    periodId = obj.getLong("periodId")
                )
            }

            val budgetsArray = root.getJSONArray("budgets")
            val budgets = (0 until budgetsArray.length()).map { idx ->
                val obj = budgetsArray.getJSONObject(idx)
                val amount = obj.getDouble("amount")
                require(amount.isFinite()) { "Invalid budget amount" }
                Budget(
                    id = obj.getLong("id"),
                    amount = amount,
                    category = obj.getString("category"),
                    subcategory = obj.optString("subcategory", ""),
                    periodId = obj.getLong("periodId")
                )
            }

            val expensesArray = root.getJSONArray("expenses")
            val expenses = (0 until expensesArray.length()).map { idx ->
                val obj = expensesArray.getJSONObject(idx)
                val amount = obj.getDouble("amount")
                require(amount.isFinite()) { "Invalid expense amount" }
                Expense(
                    id = obj.getLong("id"),
                    amount = amount,
                    date = obj.getString("date"),
                    description = obj.optString("description", ""),
                    category = obj.getString("category"),
                    subcategory = obj.optString("subcategory", ""),
                    periodId = obj.optLong("periodId", 0L),
                    budgetId = obj.optLong("budgetId", 0L),
                    budgetCategory = obj.optString("budgetCategory", ""),
                    fundingSource = ExpenseFundingSource.fromStorage(obj.optString("fundingSource", "BUDGET")),
                    savingCategory = obj.optString("savingCategory", "")
                )
            }

            val savingsArray = root.getJSONArray("savings")
            val savings = (0 until savingsArray.length()).map { idx ->
                val obj = savingsArray.getJSONObject(idx)
                val amount = obj.getDouble("amount")
                require(amount.isFinite()) { "Invalid saving amount" }
                Saving(
                    id = obj.getLong("id"),
                    amount = amount,
                    category = obj.getString("category"),
                    subcategory = obj.optString("subcategory", ""),
                    periodId = obj.getLong("periodId"),
                    incomeId = obj.optLong("incomeId", 0L),
                    incomeCategory = obj.optString("incomeCategory", ""),
                    dateTime = obj.optString("dateTime", ""),
                    note = obj.optString("note", "")
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
     */
    suspend fun restoreBackup(snapshot: BackupSnapshot): RestoreResult {
        return try {
            allocationMutex.withLock {
                periodRepository.replaceAllPeriods(snapshot.periods)
                incomeRepository.replaceAllCategories(snapshot.incomeCategories)
                budgetRepository.replaceAllCategories(snapshot.budgetCategories)
                expenseCategoryRepository.replaceAllCategories(snapshot.expenseCategories)
                savingsRepository.replaceAllCategories(snapshot.savingCategories)

                incomeRepository.replaceAllIncomes(snapshot.incomes)
                budgetRepository.replaceAllBudgets(snapshot.budgets)
                expenseRepository.replaceAllExpenses(snapshot.expenses)
                savingsRepository.replaceAllSavings(snapshot.savings)

                if (snapshot.language in SharedPrefsLanguageRepository.SUPPORTED_LANGUAGES) {
                    languagePreferenceRepository.saveLanguage(snapshot.language)
                }
                if (snapshot.currency.isNotBlank()) {
                    currencyPreferenceRepository.saveCurrency(snapshot.currency)
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
            val name = obj.getString("name").trim()
            require(name.isNotEmpty()) { "Category name cannot be blank" }
            val subsArray = obj.optJSONArray("subcategories")
            val subs = if (subsArray != null) {
                (0 until subsArray.length()).mapNotNull { sIdx ->
                    subsArray.getString(sIdx).trim().takeIf { it.isNotEmpty() }?.let { Subcategory(it) }
                }
            } else {
                emptyList()
            }
            Category(
                name = name,
                icon = obj.optString("icon", "💰").ifBlank { "💰" },
                color = obj.optString("color", "#4E73DF").ifBlank { "#4E73DF" },
                subcategories = subs
            )
        }
    }

    suspend fun buildBackupJson(exportedAtMillis: Long = nowProvider()): String = allocationMutex.withLock {
        val periods = periodRepository.getAllPeriods().firstOrNull().orEmpty()
        val incomes = periods.flatMap { period ->
            incomeRepository.getIncomesByPeriod(period.id).firstOrNull().orEmpty()
        }
        val budgets = periods.flatMap { period ->
            budgetRepository.getBudgetsByPeriod(period.id).firstOrNull().orEmpty()
        }
        val expenses = expenseRepository.getAllExpenses().firstOrNull().orEmpty()
        val allSavings = savingsRepository.getAllSavings().firstOrNull().orEmpty()
        val savings = if (allSavings.isNotEmpty()) {
            allSavings
        } else {
            periods.flatMap { period ->
                savingsRepository.getSavingsByPeriod(period.id).firstOrNull().orEmpty()
            }
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
