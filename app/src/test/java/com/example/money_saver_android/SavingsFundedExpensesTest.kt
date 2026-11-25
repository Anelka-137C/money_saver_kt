package com.example.money_saver_android

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.buildSavingsNetSummary
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.SavingsTabContent
import com.example.money_saver_android.ui.components.expenses.AddEditExpenseDialog
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import com.example.money_saver_android.viewmodel.SavingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsFundedExpensesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var appDb: AppDatabase
    private lateinit var savingsDb: SavingsDatabase
    private lateinit var expenseRepo: RoomExpenseRepository
    private lateinit var savingsRepo: RoomSavingsRepository
    private lateinit var i18nManager: I18nManager
    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$", formatPattern = "#,##0.00")

    private class StubBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
        private val flow = MutableStateFlow(initial)
        override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
            flow.map { list -> list.filter { it.periodId == periodId } }
        override suspend fun getBudgetById(id: Long): Budget? = flow.value.find { it.id == id }
        override suspend fun insertBudget(budget: Budget): Long = 0L
        override suspend fun updateBudget(budget: Budget) {}
        override suspend fun deleteBudget(id: Long) {}
        override fun getAllCategories(): Flow<List<Category>> = emptyFlow()
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun countBudgetsForCategory(categoryName: String): Int = 0
        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class StubIncomeRepository : IncomeRepository {
        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> = MutableStateFlow(emptyList())
        override suspend fun getIncomeById(id: Long): Income? = null
        override suspend fun insertIncome(income: Income): Long = 0L
        override suspend fun updateIncome(income: Income) {}
        override suspend fun deleteIncome(id: Long) {}
        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun countIncomesForCategory(categoryName: String): Int = 0
        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savingsDb = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expenseRepo = RoomExpenseRepository(appDb.expenseDao())
        savingsRepo = RoomSavingsRepository(savingsDb.savingDao(), savingsDb.savingCategoryDao())
        i18nManager = I18nManager().apply {
            initFromContext(context)
            setLanguage("es")
        }
    }

    @After
    fun tearDown() {
        appDb.close()
        savingsDb.close()
    }

    @Test
    fun roomPersistence_preservesFundingSourceAndSavingCategoryAndGlobalSavings() = runBlocking {
        savingsRepo.insertSaving(Saving(amount = 300.0, category = "Emergencias", periodId = 1L))
        savingsRepo.insertSaving(Saving(amount = 200.0, category = "Emergencias", periodId = 2L))
        expenseRepo.insertExpense(
            Expense(
                amount = 150.0,
                date = "2026-11-24 10:00",
                description = "Reparación auto",
                category = "Transporte",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategory = "Emergencias"
            )
        )

        val allSavings = savingsRepo.getAllSavings().first()
        assertEquals(2, allSavings.size)
        val savedExpense = expenseRepo.getAllExpenses().first().single()
        assertEquals(ExpenseFundingSource.SAVINGS, savedExpense.fundingSource)
        assertEquals("Emergencias", savedExpense.savingCategory)
    }

    @Test
    fun ac1_savingsExpenseWorksWithoutActiveBudgetAndExcludesBudgetConsumption() = runBlocking {
        // Savings accumulated across period 1 and period 2
        savingsRepo.insertSaving(Saving(amount = 400.0, category = "Emergencias", periodId = 1L))
        savingsRepo.insertSaving(Saving(amount = 100.0, category = "Emergencias", periodId = 2L))

        val emptyBudgetRepo = StubBudgetRepository(emptyList())
        val expenseUseCase = ExpenseUseCase(expenseRepo, emptyBudgetRepo, savingsRepo)

        val result = expenseUseCase.saveExpense(
            amountInput = "250",
            dateInput = "2026-11-24 12:00",
            descriptionInput = "Urgencia médica",
            categoryInput = "Salud",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Emergencias"
        )
        assertTrue(result is ExpenseResult.Success)

        val stored = expenseRepo.getAllExpenses().first()
        assertEquals(1, stored.size)
        assertEquals(ExpenseFundingSource.SAVINGS, stored.first().fundingSource)

        // Excluded from current budget consumption even if category name matches a budget
        val budget = Budget(id = 10L, amount = 500.0, category = "Salud", periodId = 2L)
        val incomeUseCase = IncomeUseCase(StubIncomeRepository(), StubBudgetRepository(listOf(budget)), expenseRepository = expenseRepo)
        val totalBudgetSpent = incomeUseCase.calculateTotalBudgetSpent(
            budgets = listOf(budget),
            expenses = stored,
            periodStartDate = "2026-11-01",
            periodEndDate = "2026-11-30",
            periodId = 2L
        )
        val categoryBudgets = incomeUseCase.calculateCategoryBudgets(
            budgets = listOf(budget),
            categories = emptyList(),
            expenses = stored,
            periodStartDate = "2026-11-01",
            periodEndDate = "2026-11-30",
            periodId = 2L
        )
        assertEquals(0.0, totalBudgetSpent, 0.001)
        assertEquals(500.0, categoryBudgets.single().remainingAmount, 0.001)
        assertEquals(100, categoryBudgets.single().percent)

        // Excluded from current period expense summary as well
        val periodSummary = expenseUseCase.calculateExpenseCategorySummary(stored, emptyList())
        assertTrue(periodSummary.isEmpty)
        assertEquals(0.0, periodSummary.totalAmount, 0.001)
    }

    @Test
    fun ac2_blocksSavingsExpenseExceedingCumulativeNetBalanceAndAllowsExactBalanceEdit() = runBlocking {
        savingsRepo.insertSaving(Saving(amount = 300.0, category = "Viajes", periodId = 1L))
        savingsRepo.insertSaving(Saving(amount = 200.0, category = "Viajes", periodId = 2L))

        val expenseUseCase = ExpenseUseCase(expenseRepo, StubBudgetRepository(), savingsRepo)

        // First expense uses 350 -> 150 remaining
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "350",
                dateInput = "2026-11-20 09:00",
                descriptionInput = "Vuelos",
                categoryInput = "Viajes",
                periodId = 2L,
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Viajes"
            ) is ExpenseResult.Success
        )

        // Second expense exceeding remaining 150 must be blocked
        val overResult = expenseUseCase.saveExpense(
            amountInput = "150.01",
            dateInput = "2026-11-24 09:00",
            descriptionInput = "Hotel",
            categoryInput = "Viajes",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Viajes"
        )
        assertTrue(overResult is ExpenseResult.Error)
        assertEquals("Saldo insuficiente en la categoría de ahorro", (overResult as ExpenseResult.Error).message)

        // Editing the first expense from 350 to 500 (exact total available) must succeed
        val firstExpense = expenseRepo.getAllExpenses().first().single()
        val editResult = expenseUseCase.updateExpense(
            id = firstExpense.id,
            amountInput = "500",
            dateInput = "2026-11-20 09:00",
            descriptionInput = "Vuelos + hotel",
            categoryInput = "Viajes",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Viajes"
        )
        assertTrue(editResult is ExpenseResult.Success)

        // Balance is now 0 -> any new expense is blocked (no negative balance)
        val afterDepleted = expenseUseCase.saveExpense(
            amountInput = "1",
            dateInput = "2026-11-25 09:00",
            descriptionInput = "Extra",
            categoryInput = "Viajes",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Viajes"
        )
        assertTrue(afterDepleted is ExpenseResult.Error)

        val balances = calculateSavingsNetBalances(
            savingsRepo.getAllSavings().first(),
            expenseRepo.getAllExpenses().first()
        )
        assertEquals(0.0, balances.single().netBalance, 0.001)
    }

    @Test
    fun ac1_dialogDisplaysSavingsCategoriesWithCumulativeAvailableBalance() {
        val balances = listOf(
            SavingsCategoryBalance("Emergencias", "#3B82F6", 1000.0, 250.0, 750.0),
            SavingsCategoryBalance("Viajes", "#10B981", 500.0, 0.0, 500.0)
        )
        var selectedCategory = ""

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                AddEditExpenseDialog(
                    isEditing = false,
                    amount = "100",
                    date = "2026-11-24 10:00",
                    description = "",
                    budgetCategory = "",
                    budgetSubcategory = "",
                    category = "",
                    subcategory = "",
                    budgets = emptyList(),
                    errorMessage = null,
                    currency = usd,
                    fundingSource = ExpenseFundingSource.SAVINGS,
                    savingCategory = selectedCategory,
                    savingsBalances = balances,
                    onFundingSourceSelected = {},
                    onSavingCategorySelected = { selectedCategory = it },
                    onAmountChange = {},
                    onDateChange = {},
                    onDescriptionChange = {},
                    onBudgetSelected = {},
                    onSelectCategoryClick = {},
                    onSaveClick = {},
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("selector_expense_saving_category").performClick()
        composeTestRule.onNodeWithText("Emergencias • $ 750.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Viajes • $ 500.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saving_source_option_Emergencias").performClick()
        assertEquals("Emergencias", selectedCategory)
    }

    @Test
    fun ac3_expenseCardShowsSavingsSourceBadge() {
        val savingsExpense = Expense(
            id = 7L,
            amount = 200.0,
            date = "2026-11-24 14:00",
            description = "Laptop",
            category = "Tecnología",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategory = "Emergencias"
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                ExpenseCardItem(expense = savingsExpense, currency = usd)
            }
        }

        composeTestRule.onNodeWithTag("badge_expense_savings_source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ahorro: Emergencias").assertIsDisplayed()
    }

    @Test
    fun ac3_savingsViewShowsCumulativeNetCard() {
        val savingsExpense = Expense(
            id = 7L,
            amount = 200.0,
            date = "2026-11-24 14:00",
            description = "Laptop",
            category = "Tecnología",
            periodId = 2L,
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategory = "Emergencias"
        )

        val netSummary = runBlocking {
            savingsRepo.insertCategory(Category("Emergencias", "🛡️", "#3B82F6", emptyList()))
            savingsRepo.insertCategory(Category("Viajes", "✈️", "#10B981", emptyList()))
            savingsRepo.insertSaving(Saving(amount = 600.0, category = "Emergencias", periodId = 1L))
            savingsRepo.insertSaving(Saving(amount = 200.0, category = "Emergencias", periodId = 2L))
            savingsRepo.insertSaving(Saving(amount = 200.0, category = "Viajes", periodId = 1L))
            expenseRepo.insertExpense(savingsExpense)

            buildSavingsNetSummary(
                savings = savingsRepo.getAllSavings().first(),
                expenses = expenseRepo.getAllExpenses().first(),
                categories = savingsRepo.getAllCategories().first()
            )
        }
        // Emergencias: 800 - 200 = 600 (75%), Viajes: 200 (25%), Total = 800
        assertEquals(800.0, netSummary.totalAmount, 0.001)
        assertEquals(2, netSummary.items.size)
        assertEquals(75, netSummary.items[0].percentage)
        assertEquals(25, netSummary.items[1].percentage)

        val savingsUseCase = SavingsUseCase(savingsRepo, StubIncomeRepository(), StubBudgetRepository(), expenseRepo)
        val savingsViewModel = SavingsViewModel(savingsUseCase)
        savingsViewModel.onPeriodSelected(2L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    SavingsTabContent(viewModel = savingsViewModel, currency = usd)
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            savingsViewModel.uiState.value.netSavingsSummary.totalAmount == 800.0
        }
        composeTestRule.waitForIdle()

        // Cumulative net savings card in SavingsTabContent
        composeTestRule.onNodeWithTag("card_savings_net_overview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_total_net_savings").assertTextEquals("$ 800.00")
        composeTestRule.onNode(
            hasTestTag("legend_amount_Emergencias") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("$ 600.00")
        composeTestRule.onNode(
            hasTestTag("legend_percent_Emergencias") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("75%")
        composeTestRule.onNode(
            hasTestTag("legend_amount_Viajes") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("$ 200.00")
        composeTestRule.onNode(
            hasTestTag("legend_percent_Viajes") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertTextEquals("25%")
        composeTestRule.onNode(
            hasTestTag("chart_period_summary_donut") and hasAnyAncestor(hasTestTag("card_savings_net_overview"))
        ).assertIsDisplayed()
    }
}
