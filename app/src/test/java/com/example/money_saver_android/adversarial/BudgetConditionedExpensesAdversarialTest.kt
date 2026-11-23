package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.ExpensesTabContent
import com.example.money_saver_android.ui.IncomesView
import com.example.money_saver_android.ui.MainContainer
import com.example.money_saver_android.ui.components.expenses.ExpenseAllocationCard
import com.example.money_saver_android.ui.components.expenses.ExpenseOverviewCard
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

private class AdversarialExpenseRepo(
    private val yieldOnInsert: Boolean = false
) : ExpenseRepository {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    override fun getAllExpenses(): Flow<List<Expense>> = _expenses

    override suspend fun insertExpense(expense: Expense) {
        if (yieldOnInsert) yield()
        val assignedId = if (expense.id > 0L) expense.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1L
        _expenses.value = listOf(expense.copy(id = assignedId)) + _expenses.value
    }

    override suspend fun updateExpense(expense: Expense) {
        _expenses.value = _expenses.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun deleteExpense(id: Long) {
        _expenses.value = _expenses.value.filter { it.id != id }
    }
}

private class AdversarialBudgetRepo : BudgetRepository {
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private var nextId = 1L

    override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
        _budgets.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertBudget(budget: Budget): Long {
        val assignedId = if (budget.id > 0L) budget.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1L
        _budgets.value = _budgets.value + budget.copy(id = assignedId)
        return assignedId
    }

    override suspend fun updateBudget(budget: Budget) {
        _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun getBudgetById(id: Long): Budget? = _budgets.value.find { it.id == id }

    override suspend fun deleteBudget(id: Long) {
        _budgets.value = _budgets.value.filter { it.id != id }
    }

    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map {
            if (it.name.equals(categoryName, ignoreCase = true)) {
                it.copy(subcategories = it.subcategories + subcategory)
            } else it
        }
    }

    override suspend fun deleteCategory(categoryName: String) {
        _categories.value = _categories.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name.equals(categoryName, ignoreCase = true)) {
                cat.copy(subcategories = cat.subcategories.filterNot { it.name.equals(subcategoryName, ignoreCase = true) })
            } else cat
        }
    }

    override suspend fun countBudgetsForCategory(categoryName: String): Int =
        _budgets.value.count { it.category.equals(categoryName, ignoreCase = true) }

    override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int =
        _budgets.value.count {
            it.category.equals(categoryName, ignoreCase = true) &&
                it.subcategory.equals(subcategoryName, ignoreCase = true)
        }

    override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
}

private class AdversarialIncomeRepo : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private var nextId = 1L

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        _incomes.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertIncome(income: Income): Long {
        val assignedId = if (income.id > 0L) income.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1L
        _incomes.value = _incomes.value + income.copy(id = assignedId)
        return assignedId
    }

    override suspend fun updateIncome(income: Income) {
        _incomes.value = _incomes.value.map { if (it.id == income.id) income else it }
    }

    override suspend fun deleteIncome(id: Long) {
        _incomes.value = _incomes.value.filter { it.id != id }
    }

    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map {
            if (it.name.equals(categoryName, ignoreCase = true)) {
                it.copy(subcategories = it.subcategories + subcategory)
            } else it
        }
    }

    override suspend fun deleteCategory(categoryName: String) {
        _categories.value = _categories.value.filterNot { it.name.equals(categoryName, ignoreCase = true) }
    }

    override suspend fun countIncomesForCategory(categoryName: String): Int =
        _incomes.value.count { it.category.equals(categoryName, ignoreCase = true) }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name.equals(categoryName, ignoreCase = true)) {
                cat.copy(subcategories = cat.subcategories.filterNot { it.name.equals(subcategoryName, ignoreCase = true) })
            } else cat
        }
    }

    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int =
        _incomes.value.count {
            it.category.equals(categoryName, ignoreCase = true) &&
                it.subcategory.equals(subcategoryName, ignoreCase = true)
        }

    override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
}

private class AdversarialCategoryRepo : CategoryRepository {
    private val _categories = MutableStateFlow(
        listOf(
            Category(
                name = "Alimentación",
                icon = "🍔",
                color = "#1CC88A",
                subcategories = listOf(Subcategory("Restaurante"), Subcategory("Supermercado"))
            ),
            Category(
                name = "Transporte",
                icon = "🚗",
                color = "#4E73DF",
                subcategories = listOf(Subcategory("Bus"))
            )
        )
    )

    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun preloadDefaultCategoriesIfNeeded() = Unit

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map {
            if (it.name.equals(categoryName, ignoreCase = true)) {
                it.copy(subcategories = it.subcategories + subcategory)
            } else it
        }
    }
}

private class AdversarialPeriodRepo(initialPeriod: FinancialPeriod? = null) : FinancialPeriodRepository {
    private val _periods = MutableStateFlow(if (initialPeriod != null) listOf(initialPeriod) else emptyList())
    private var nextId = (initialPeriod?.id ?: 0L) + 1L

    override suspend fun savePeriod(period: FinancialPeriod): Long {
        val assignedId = if (period.id > 0L) period.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1L
        val saved = period.copy(id = assignedId)
        _periods.value = _periods.value.filterNot { it.id == assignedId } + saved
        return assignedId
    }

    override suspend fun getPeriodForDate(date: String): FinancialPeriod? =
        _periods.value.find { date >= it.startDate && date <= it.endDate }

    override fun getLatestPeriod(): Flow<FinancialPeriod?> =
        _periods.map { it.lastOrNull() }

    override fun getAllPeriods(): Flow<List<FinancialPeriod>> = _periods

    override suspend fun deletePeriod(id: Long) {
        _periods.value = _periods.value.filterNot { it.id == id }
    }
}

private class AdversarialLanguageRepo(initialLang: String = "es") : LanguagePreferenceRepository {
    private var lang = initialLang
    override fun getLanguage(): String = lang
    override fun saveLanguage(languageCode: String) {
        lang = languageCode
    }
}

/**
 * Suite de Pruebas Adversariales y Property-Based Testing para:
 * Story #9 — Condicionamiento del registro de gastos a presupuestos activos en el período,
 * integración en Contabilidad (`FinanceTopTabs`), selectores independientes y saldo sobrante.
 *
 * Restricción: Este archivo únicamente diseña e implementa pruebas sobre los cambios detectados
 * mediante Git y sus dependencias directas, sin modificar el código fuente productivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BudgetConditionedExpensesAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            runTest(testDispatcher) {
                block()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun buildI18nManager(): I18nManager {
        val i18n = I18nManager()
        val esJson = """
            {
              "navigation": {
                "tab_expenses": "Gastos",
                "tab_incomes": "Contabilidad",
                "tab_settings": "Configuración",
                "tab_period": "Período"
              },
              "incomes": {
                "tabs": { "incomes": "Ingresos", "budget": "Presupuesto", "expenses": "Gastos" },
                "header": { "title": "Ingresos", "add_button": "Registrar Ingreso", "select_period": "Seleccionar período" },
                "overview": { "title": "Resumen de Ingresos", "total_income": "Ingreso total", "trend": "↑ +12% vs mes ant.", "used": "Usado" },
                "allocation": { "title": "Asignación de Ingresos", "details": "Detalles >", "budgeted": "Presupuestado", "savings": "Ahorros", "spent": "Gastado", "available": "Disponible" },
                "breakdown": { "title": "Desglose de Ingresos" },
                "history": { "empty_message": "No hay ingresos registrados en este período" }
              },
              "expenses": {
                "overview": { "title": "Resumen de Gastos", "total_spent": "Total gastado", "budget_label": "Presupuesto" },
                "allocation": { "title": "Distribución de Gastos", "details": "Detalles >", "spent_label": "Gastado", "empty_message": "No hay gastos registrados en este período" },
                "no_budget_banner": { "title": "Sin presupuesto activo", "subtitle": "Define al menos un presupuesto en este período para poder registrar gastos." },
                "add_card": { "title": "Registrar Gasto", "amount_label": "Monto", "amount_placeholder": "0.00", "date_label": "Fecha", "budget_label": "Presupuesto origen", "budget_placeholder": "Seleccionar presupuesto", "category_label": "Categoría", "category_placeholder": "Seleccionar categoría", "description_label": "Nota (opcional)", "description_placeholder": "Agregar una descripción...", "save_button": "Guardar Gasto" },
                "breakdown": { "title": "Desglose de gastos" },
                "history": { "period_empty_message": "No hay gastos registrados en este período" },
                "edit_dialog": { "title": "Modificar Gasto", "save_button": "Guardar" }
              },
              "budget": {
                "header": { "title": "Presupuesto", "add_button": "Registrar Presupuesto" },
                "overview": { "title": "Resumen de Presupuesto", "total_budgeted": "Total presupuestado", "spent_label": "Gastado", "remaining_label": "Restante global", "available_banner_title": "Tienes %s disponible", "available_banner_subtitle": "%s disponible para presupuestar." },
                "allocation": { "title": "Asignación de Presupuesto", "details": "Detalles >", "budget_label": "Presupuesto", "remaining_label": "Sobrante", "empty_message": "No hay presupuestos asignados para este período" },
                "add_dialog": { "title": "Registrar Presupuesto", "edit_title": "Modificar Presupuesto", "amount_label": "Monto", "amount_placeholder": "0.00", "category_label": "Categoría", "select_category": "Seleccionar categoría", "save_button": "✓ Guardar Presupuesto", "edit_save_button": "✓ Guardar Cambios" },
                "breakdown": { "title": "Desglose de presupuestos" },
                "history": { "empty_message": "No hay presupuestos registrados en este período" }
              },
              "settings": {
                "header": { "title": "Configuración" },
                "language_section": { "title": "Idioma", "description": "Selecciona el idioma" }
              },
              "period": {
                "title": "Período Financiero",
                "active_period_title": "Período activo",
                "start_date": "Fecha de inicio",
                "end_date": "Fecha de fin",
                "save_button": "Guardar período",
                "edit_button": "Modificar período"
              }
            }
        """.trimIndent()
        val enJson = """
            {
              "navigation": {
                "tab_expenses": "Expenses",
                "tab_incomes": "Accounting",
                "tab_settings": "Settings",
                "tab_period": "Period"
              },
              "incomes": {
                "tabs": { "incomes": "Incomes", "budget": "Budget", "expenses": "Expenses" },
                "header": { "title": "Incomes", "add_button": "Add Income", "select_period": "Select period" }
              },
              "expenses": {
                "overview": { "title": "Expense Overview", "total_spent": "Total spent", "budget_label": "Budget" },
                "allocation": { "title": "Expense Allocation", "details": "Details >", "spent_label": "Spent", "empty_message": "No expenses recorded in this period" },
                "no_budget_banner": { "title": "No active budget", "subtitle": "Define at least one budget in this period before recording expenses." },
                "add_card": { "title": "Add Expense", "amount_label": "Amount", "amount_placeholder": "0.00", "date_label": "Date", "budget_label": "Source budget", "budget_placeholder": "Select budget", "category_label": "Category", "category_placeholder": "Select category", "description_label": "Note (optional)", "description_placeholder": "Add a description...", "save_button": "Save Expense" },
                "breakdown": { "title": "Expense breakdown" },
                "history": { "period_empty_message": "No expenses recorded in this period" },
                "edit_dialog": { "title": "Edit Expense", "save_button": "Save" }
              },
              "settings": {
                "header": { "title": "Settings" },
                "language_section": { "title": "Language", "description": "Select language" }
              }
            }
        """.trimIndent()
        i18n.loadTranslation("es", esJson)
        i18n.loadTranslation("en", enJson)
        i18n.setLanguage("es")
        return i18n
    }

    // =========================================================================
    // 1. BUG CRÍTICO EN UI/INTEGRACIÓN: CAMBIO DE PERÍODO DESINCRONIZA PRESUPUESTO VS GASTOS
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN MainActivity.kt (líneas 164-168), IncomesView.kt (línea 174)
     * E IncomeViewModel.kt (líneas 693-719):
     * `MainActivity` pasa `selectedPeriodId = periodState.activePeriod?.id` (que no cambia cuando
     * el usuario elige otro período en el dropdown de `IncomesView`) y `onPeriodSelected` solo
     * actualiza `incomeViewModel` y `expenseViewModel`.
     * Como `IncomesView` calcula `activePeriod` usando el parámetro `selectedPeriodId` (en lugar
     * de `state.selectedPeriodId`), al cambiar al Período 2:
     *   - `IncomeViewModel.uiState.budgets` carga los presupuestos del Período 2 (`id = 2L`).
     *   - `ExpenseViewModel` carga los gastos del Período 2 (`$250.00` gastados).
     *   - PERO `activePeriod` sigue siendo el Período 1 (`id = 1L`), y `IncomeViewModel` usa
     *     `periodId = activePeriod?.id ?: current.selectedPeriodId` (= `1L`).
     * Resultado: En la pestaña Presupuesto, los gastos del Período 2 son ignorados por completo
     * (`Gastado = $0.00`, `Restante = $800.00`), contradiciendo los `$250.00` mostrados en Gastos.
     */
    @Test
    fun test_adversarial_incomes_view_period_switch_desyncs_budget_spent_and_remaining_from_expenses_tab() {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val categoryRepo = AdversarialCategoryRepo()
        val i18n = buildI18nManager()
        val currency = defaultCurrencies.first()

        val period1 = FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31")
        val period2 = FinancialPeriod(id = 2L, startDate = "2026-06-01", endDate = "2026-06-30")

        runBlocking {
            budgetRepo.insertCategory(Category("Food & Dining", "🍔", "#1CC88A", emptyList()))
            budgetRepo.insertCategory(Category("Transport", "🚗", "#4E73DF", emptyList()))

            incomeRepo.insertIncome(Income(amount = 2000.0, dateTime = "2026-05-01 09:00", category = "Employment", periodId = 1L))
            incomeRepo.insertIncome(Income(amount = 3000.0, dateTime = "2026-06-01 09:00", category = "Employment", periodId = 2L))

            val b1 = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Food & Dining", periodId = 1L))
            val b2 = budgetRepo.insertBudget(Budget(amount = 800.0, category = "Transport", periodId = 2L))

            expenseRepo.insertExpense(
                Expense(
                    amount = 100.0,
                    date = "2026-05-10",
                    description = "Almuerzo",
                    category = "Alimentación",
                    periodId = 1L,
                    budgetId = b1,
                    budgetCategory = "Food & Dining"
                )
            )
            expenseRepo.insertExpense(
                Expense(
                    amount = 250.0,
                    date = "2026-06-10",
                    description = "Pasajes",
                    category = "Transporte",
                    periodId = 2L,
                    budgetId = b2,
                    budgetCategory = "Transport"
                )
            )
        }

        val incomeVm = IncomeViewModel(IncomeUseCase(incomeRepo, budgetRepo, expenseRepository = expenseRepo))
        val expenseVm = ExpenseViewModel(ExpenseUseCase(expenseRepo, budgetRepo), CategoryUseCase(categoryRepo))
        incomeVm.onPeriodSelected(1L)
        expenseVm.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                // Cableado idéntico al de MainActivity.kt: selectedPeriodId fijo al período activo inicial
                IncomesView(
                    incomeViewModel = incomeVm,
                    expenseViewModel = expenseVm,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = period1.id,
                    onPeriodSelected = { periodId ->
                        incomeVm.onPeriodSelected(periodId)
                        expenseVm.onPeriodSelected(periodId)
                    },
                    i18nManager = i18n,
                    currency = currency
                )
            }
        }
        composeTestRule.waitForIdle()

        // El usuario cambia al Período 2 desde el selector superior
        composeTestRule.onNodeWithTag("selector_period").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("period_option_2").performClick()
        composeTestRule.waitForIdle()

        // 1. Verificar que la pestaña Gastos muestra $250 gastados en Período 2
        composeTestRule.onNodeWithTag("tab_top_expenses").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_expense_total_spent")
            .performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(250.0, currency))

        // 2. INVARIANTE: La pestaña Presupuesto del Período 2 DEBE mostrar $800 presupuestados y $550 sobrantes
        composeTestRule.onNodeWithTag("tab_top_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_total_budgeted")
            .performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(800.0, currency))
        composeTestRule.onNodeWithTag("text_budget_remaining_Transport")
            .performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(550.0, currency))
    }

    // =========================================================================
    // 2. BUG CRÍTICO EN DOMINIO: RENOMBRAR CATEGORÍA DE PRESUPUESTO ROMPE DEDUCCIÓN Y EDICIÓN
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomeUseCase.kt (líneas 418-425) Y ExpenseUseCase.kt (líneas 119-121):
     * Cada `Expense` almacena `budgetId` y una copia desnormalizada `budgetCategory`.
     * Si el usuario edita un presupuesto existente (mediante `BudgetUseCase.updateBudget`) y cambia su
     * categoría de `"Food & Dining"` a `"Groceries"` manteniendo el mismo `id`:
     *   1. `IncomeUseCase.matchesBudgetGroup` exige coincidencia simultánea de `budgetId` Y `budgetCategory`,
     *      por lo que el gasto desaparece por completo de `calculateTotalBudgetSpent` y `calculateCategoryBudgets`.
     *   2. `ExpenseUseCase.updateExpense` y `ExpenseViewModel.startEditing` exigen que `budget.category`
     *      coincida con la copia obsoleta `expense.budgetCategory`, bloqueando ediciones futuras del gasto.
     */
    @Test
    fun test_adversarial_budget_category_rename_breaks_expense_deduction_and_subsequent_edits() = runViewModelTest {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val budgetUseCase = BudgetUseCase(budgetRepo, incomeRepo)
        val incomeUseCase = IncomeUseCase(incomeRepo, budgetRepo, budgetUseCase, expenseRepo)
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo)
        val expenseVm = ExpenseViewModel(expenseUseCase, CategoryUseCase(AdversarialCategoryRepo()))

        incomeRepo.insertIncome(Income(amount = 1000.0, dateTime = "2026-05-01 09:00", category = "Salary", periodId = 1L))
        assertTrue(budgetUseCase.saveBudget("500", "Food & Dining", "", 1L) is BudgetResult.Success)
        val budgetId = budgetRepo.getBudgetsByPeriod(1L).first().single().id

        val saveExpenseResult = expenseUseCase.saveExpense(
            amountInput = "150",
            dateInput = "2026-05-10",
            descriptionInput = "Mercado",
            categoryInput = "Alimentación",
            subcategoryInput = "Supermercado",
            periodId = 1L,
            budgetId = budgetId,
            budgetCategoryInput = "Food & Dining"
        )
        assertTrue(saveExpenseResult is ExpenseResult.Success)

        // El usuario cambia la categoría del presupuesto existente a "Groceries"
        val renameResult = budgetUseCase.updateBudget(
            id = budgetId,
            amountInput = "500",
            categoryInput = "Groceries",
            subcategoryInput = "",
            periodId = 1L
        )
        assertTrue(renameResult is BudgetResult.Success)

        val updatedBudgets = budgetRepo.getBudgetsByPeriod(1L).first()
        val expenses = expenseRepo.getExpensesByPeriod(1L).first()
        val categories = listOf(Category("Groceries", "🛒", "#1CC88A", emptyList()))

        // INVARIANTE 1: El gasto vinculado por budgetId debe seguir descontándose del presupuesto renombrado
        val totalSpent = incomeUseCase.calculateTotalBudgetSpent(
            budgets = updatedBudgets,
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = 1L
        )
        assertEquals(
            "VULNERABILIDAD EXPUESTA: Renombrar la categoría del presupuesto dejó invisible el gasto vinculado a budgetId=$budgetId",
            150.0,
            totalSpent,
            0.001
        )
        val categoryItems = incomeUseCase.calculateCategoryBudgets(
            budgets = updatedBudgets,
            categories = categories,
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = 1L
        )
        assertEquals(350.0, categoryItems.single().remainingAmount, 0.001)

        // INVARIANTE 2: El usuario debe poder editar el monto de ese gasto sin que el enlace obsoleto bloquee el guardado
        expenseVm.onPeriodSelected(1L)
        advanceUntilIdle()
        expenseVm.startEditing(expenses.single())
        expenseVm.onEditAmountChange("175")
        expenseVm.confirmEdit()
        advanceUntilIdle()

        assertNull(
            "VULNERABILIDAD EXPUESTA: confirmEdit falló tras renombrar el presupuesto origen (${expenseVm.uiState.value.editingExpense?.errorMessage})",
            expenseVm.uiState.value.editingExpense
        )
        assertEquals(175.0, expenseRepo.getExpensesByPeriod(1L).first().single().amount, 0.001)
    }

    // =========================================================================
    // 3. BUG ALTO EN DOMINIO/VIEWMODELS: GASTOS LEGACY (periodId = 0L) Y FECHAS NULAS
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomeUseCase.kt (líneas 406-416) E IncomeViewModel.kt (líneas 125-129):
     * 1. `IncomeUseCase.matchesPeriod`: cuando `expense.periodId == 0L` y `periodStartDate == null`,
     *    retorna `true` para CUALQUIER `periodId` (contaminando otros períodos).
     * 2. `IncomeViewModel.init` recolecta `getAllExpenses()`, de modo que un gasto legacy (`periodId = 0L`)
     *    con fecha en mayo SÍ se descuenta en `BudgetOverviewCard` (Presupuesto), pero NO aparece ni se suma
     *    en `ExpenseViewModel` / `ExpenseOverviewCard` (que consulta `getExpensesByPeriod(1L)`).
     */
    @Test
    fun test_adversarial_legacy_zero_period_expenses_diverge_between_budget_and_expenses_tabs_and_leak_across_null_dates() = runViewModelTest {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val incomeUseCase = IncomeUseCase(incomeRepo, budgetRepo, expenseRepository = expenseRepo)
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo)

        val period1 = FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31")
        val b1 = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Food & Dining", periodId = 1L))
        val b2 = budgetRepo.insertBudget(Budget(amount = 400.0, category = "Food & Dining", periodId = 2L))

        // Gasto legacy sin periodId (0L) ni budgetId (0L), pero con fecha dentro de mayo
        val legacyExpense = Expense(
            id = 1L,
            amount = 120.0,
            date = "2026-05-15",
            description = "Gasto previo a Story 9",
            category = "Food & Dining",
            periodId = 0L,
            budgetId = 0L,
            budgetCategory = ""
        )
        expenseRepo.insertExpense(legacyExpense)

        // Caso A: Período 2 sin rango de fechas proporcionado NO debe heredar gastos legacy con periodId=0
        val period2Budgets = incomeUseCase.calculateCategoryBudgets(
            budgets = listOf(Budget(id = b2, amount = 400.0, category = "Food & Dining", periodId = 2L)),
            categories = emptyList(),
            expenses = listOf(legacyExpense),
            periodStartDate = null,
            periodEndDate = null,
            periodId = 2L
        )
        assertEquals(
            "VULNERABILIDAD EXPUESTA: Un gasto legacy sin periodId contaminó el cálculo del Período 2 cuando periodStartDate es null",
            0.0,
            period2Budgets.single().spentAmount,
            0.001
        )

        // Caso B: Consistencia estricta entre Presupuesto y Gastos para el mismo Período 1
        val incomeVm = IncomeViewModel(incomeUseCase)
        val expenseVm = ExpenseViewModel(expenseUseCase, CategoryUseCase(AdversarialCategoryRepo()))
        incomeVm.onPeriodSelected(1L)
        expenseVm.onPeriodSelected(1L)
        advanceUntilIdle()

        val budgetTabSpent = incomeVm.calculateTotalBudgetSpent(period1)
        val expensesTabSpent = expenseVm.uiState.value.totalSpent
        assertEquals(
            "VULNERABILIDAD EXPUESTA: Presupuesto reporta gastado=$budgetTabSpent pero Gastos reporta totalSpent=$expensesTabSpent para el Período 1 (b1=$b1)",
            expensesTabSpent,
            budgetTabSpent,
            0.001
        )
    }

    // =========================================================================
    // 4. BUG ALTO EN ExpenseViewModel: FUGA DE ESTADO AL CAMBIAR DE PERÍODO E INIT SIN PERÍODO
    // =========================================================================
    /**
     * VULNERABILIDADES DETECTADAS EN ExpenseViewModel.kt (líneas 73-78 y 89-135):
     * 1. En `init`, `ExpenseViewModel` recolecta `getExpenses()` (todos los períodos) mientras `selectedPeriodId == null`.
     * 2. Al cambiar de período en `onPeriodSelected`, NO limpia `errorMessage`, `editingExpense`, `expenseToDelete`,
     *    `amountInput`, `categoryInput`, `subcategoryInput` ni `descriptionInput`.
     */
    @Test
    fun test_adversarial_expense_viewmodel_period_switch_leaks_error_and_editing_state_and_init_exposes_all_periods() = runViewModelTest {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val b1 = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Fijos", periodId = 1L))
        val p1Expense = Expense(
            id = 10L,
            amount = 90.0,
            date = "2026-05-10",
            description = "Internet",
            category = "Servicios",
            periodId = 1L,
            budgetId = b1,
            budgetCategory = "Fijos"
        )
        expenseRepo.insertExpense(p1Expense)

        val vm = ExpenseViewModel(ExpenseUseCase(expenseRepo, budgetRepo), CategoryUseCase(AdversarialCategoryRepo()))
        advanceUntilIdle()

        // INVARIANTE 1: Sin período seleccionado y con budgetRepository configurado, no debe exponer gastos de otros períodos
        assertNull(vm.uiState.value.selectedPeriodId)
        assertTrue(
            "VULNERABILIDAD EXPUESTA: ExpenseViewModel expuso gastos de todos los períodos antes de seleccionar período",
            vm.uiState.value.expenses.isEmpty()
        )

        // Seleccionar Período 2 (sin presupuestos) e intentar guardar -> dispara errorMessage
        vm.onPeriodSelected(2L)
        advanceUntilIdle()
        vm.onAmountChange("50")
        vm.onDateChange("2026-06-05")
        vm.onCategoryChange("Alimentación", "")
        vm.saveExpense()
        advanceUntilIdle()
        assertEquals("Se requiere definir un presupuesto activo en el período", vm.uiState.value.errorMessage)

        // INVARIANTE 2: Al cambiar al Período 1 (con presupuesto activo), el error del Período 2 debe limpiarse
        vm.onPeriodSelected(1L)
        advanceUntilIdle()
        assertNull(
            "VULNERABILIDAD EXPUESTA: errorMessage del Período 2 persistió al cambiar al Período 1",
            vm.uiState.value.errorMessage
        )

        // Abrir edición de un gasto del Período 1 y cambiar al Período 2
        vm.startEditing(p1Expense)
        assertTrue(vm.uiState.value.editingExpense != null)
        vm.onPeriodSelected(2L)
        advanceUntilIdle()

        // INVARIANTE 3: editingExpense del Período 1 debe cerrarse/limpiarse al cambiar de período
        assertNull(
            "VULNERABILIDAD EXPUESTA: editingExpense del Período 1 permaneció abierto tras cambiar al Período 2",
            vm.uiState.value.editingExpense
        )
    }

    // =========================================================================
    // 5. CONCURRENCIA / IDEMPOTENCIA: DOBLE TAP EN saveExpense DUPLICA GASTOS
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpenseViewModel.kt (líneas 305-338):
     * `saveExpense()` lee una instantánea de `_uiState.value` y lanza una corrutina sin bloqueo ni
     * bandera `isSaving`; los campos de entrada solo se limpian al terminar la inserción.
     * Dos invocaciones concurrentes antes de completarse la primera insertan dos gastos duplicados.
     */
    @Test
    fun test_adversarial_expense_viewmodel_concurrent_double_submit_inserts_duplicate_expenses() = runViewModelTest {
        val expenseRepo = AdversarialExpenseRepo(yieldOnInsert = true)
        val budgetRepo = AdversarialBudgetRepo()
        budgetRepo.insertBudget(Budget(amount = 500.0, category = "Fijos", periodId = 1L))

        val vm = ExpenseViewModel(ExpenseUseCase(expenseRepo, budgetRepo), CategoryUseCase(AdversarialCategoryRepo()))
        vm.onPeriodSelected(1L)
        advanceUntilIdle()

        val budget = vm.uiState.value.budgets.single()
        vm.onBudgetSelected(budget)
        vm.onAmountChange("75.0")
        vm.onDateChange("2026-05-12")
        vm.onDescriptionChange("Café")
        vm.onCategoryChange("Alimentación", "Restaurante")

        // Simular doble pulsación concurrente sobre el botón Guardar antes de que termine el IO
        vm.saveExpense()
        vm.saveExpense()
        advanceUntilIdle()

        val savedCount = expenseRepo.getExpensesByPeriod(1L).first().size
        assertEquals(
            "VULNERABILIDAD EXPUESTA: Doble invocación concurrente de saveExpense duplicó el gasto ($savedCount registros)",
            1,
            savedCount
        )
    }

    // =========================================================================
    // 6. BUG UI EN ExpensesTabContent: ERROR PERSISTENTE TRAS CERRAR DIÁLOGO DE GASTO
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpensesView.kt (línea 276):
     * `onCloseClick = { showAddDialog = false }` cierra el diálogo sin limpiar `state.errorMessage`
     * (y `ExpenseViewModel` carece de `clearErrorMessage()`). Al reabrir el diálogo, el error anterior
     * aparece inmediatamente sin haber intentado guardar de nuevo.
     */
    @Test
    fun test_adversarial_expenses_tab_closing_add_dialog_clears_stale_error_message() {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val i18n = buildI18nManager()

        runBlocking {
            budgetRepo.insertBudget(Budget(amount = 300.0, category = "Food & Dining", periodId = 1L))
        }

        val expenseVm = ExpenseViewModel(
            ExpenseUseCase(expenseRepo, budgetRepo),
            CategoryUseCase(AdversarialCategoryRepo())
        )
        expenseVm.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpensesTabContent(viewModel = expenseVm)
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Abrir diálogo, ingresar monto, intentar guardar sin presupuesto -> error visible
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_expense_amount").performTextInput("50")
        composeTestRule.onNodeWithTag("btn_save_expense_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_expense_dialog_error").assertIsDisplayed()

        // 2. Cerrar el diálogo con X y volver a abrirlo
        composeTestRule.onNodeWithTag("btn_close_expense_dialog").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // INVARIANTE UX: El diálogo recién abierto NO debe mostrar errores residuales del intento descartado
        composeTestRule.onNodeWithTag("text_expense_dialog_error").assertDoesNotExist()
    }

    /**
     * VULNERABILIDAD DETECTADA EN ExpensesView.kt (líneas 335-360):
     * `ExpenseCategorySheet` no pasa `onClearSubcategoryError = viewModel::clearSubcategoryError`
     * a `CategorySelectionSheet`. Un error al crear subcategoría dentro de una categoría persiste
     * al volver atrás y abrir otra categoría diferente.
     */
    @Test
    fun test_adversarial_expenses_tab_category_sheet_leaks_subcategory_error_across_categories() {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val i18n = buildI18nManager()

        runBlocking {
            budgetRepo.insertBudget(Budget(amount = 300.0, category = "Food & Dining", periodId = 1L))
        }

        val expenseVm = ExpenseViewModel(
            ExpenseUseCase(expenseRepo, budgetRepo),
            CategoryUseCase(AdversarialCategoryRepo())
        )
        expenseVm.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpensesTabContent(viewModel = expenseVm)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selector_expense_category").performClick()
        composeTestRule.waitForIdle()

        // Entrar a "Alimentación" y disparar error de nombre de subcategoría vacío
        composeTestRule.onNodeWithText("Alimentación").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Crear").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("El nombre de la subcategoría no puede estar vacío").assertIsDisplayed()

        // Volver a la lista de categorías y entrar a "Transporte"
        composeTestRule.onNodeWithContentDescription("Volver").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Transporte").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // INVARIANTE UX: El error de subcategoría de "Alimentación" NO debe fugarse hacia "Transporte"
        composeTestRule.onNodeWithText("El nombre de la subcategoría no puede estar vacío").assertDoesNotExist()
    }

    // =========================================================================
    // 7. VALORES FRONTERA, TIPOS Y VALIDACIÓN EN ExpenseUseCase
    // =========================================================================
    /**
     * Evalúa entradas malformadas y límites en `ExpenseUseCase`:
     *   - Decimales con coma (`"10,50"` -> `replace(",", "")` lo convierte silenciosamente en `1050.0`!).
     *   - Valores no finitos (`"NaN"`, `"Infinity"`, `"-Infinity"`, `"1e309"`), ceros y negativos.
     *   - Fechas vacías o malformadas (`""`, `"not-a-date"`).
     *   - `updateExpense` sobre un `id` inexistente (`99999L`).
     *   - Inyección cruzada de `budgetId` perteneciente al Período 2 mientras `periodId = 1L`.
     */
    @Test
    fun test_adversarial_expense_usecase_numeric_comma_locale_nonfinite_update_id_and_date_validation() = runBlocking {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val p1BudgetId = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Fijos", periodId = 1L))
        val p2BudgetId = budgetRepo.insertBudget(Budget(amount = 500.0, category = "Ocio", periodId = 2L))
        val useCase = ExpenseUseCase(expenseRepo, budgetRepo)

        // 1. Valores numéricos inválidos / no finitos / cero / negativos
        val invalidAmounts = listOf("NaN", "Infinity", "-Infinity", "+Infinity", "1e309", "0", "0.00", "-10.5", "", "   ", "abc")
        for (badAmount in invalidAmounts) {
            val res = useCase.saveExpense(
                amountInput = badAmount,
                dateInput = "2026-05-10",
                descriptionInput = "Test",
                categoryInput = "Alimentación",
                periodId = 1L,
                budgetId = p1BudgetId,
                budgetCategoryInput = "Fijos"
            )
            assertTrue("Debe rechazar monto inválido '$badAmount'", res is ExpenseResult.Error)
        }

        // 2. Coma decimal regional ("10,50") NO debe interpretarse silenciosamente como 1050.0
        val commaRes = useCase.saveExpense(
            amountInput = "10,50",
            dateInput = "2026-05-10",
            descriptionInput = "Coma decimal",
            categoryInput = "Alimentación",
            periodId = 1L,
            budgetId = p1BudgetId,
            budgetCategoryInput = "Fijos"
        )
        if (commaRes is ExpenseResult.Success) {
            val storedAmount = expenseRepo.getExpensesByPeriod(1L).first().first().amount
            assertEquals(
                "VULNERABILIDAD EXPUESTA: '10,50' se corrompió a $storedAmount por replace(',', '')",
                10.50,
                storedAmount,
                0.001
            )
        }

        // 3. Inyección de presupuesto de otro período (p2BudgetId con periodId=1L)
        val crossPeriodRes = useCase.saveExpense(
            amountInput = "25.0",
            dateInput = "2026-05-10",
            descriptionInput = "Cross period",
            categoryInput = "Alimentación",
            periodId = 1L,
            budgetId = p2BudgetId,
            budgetCategoryInput = "Ocio"
        )
        assertTrue("Debe bloquear presupuesto de otro período", crossPeriodRes is ExpenseResult.Error)

        // 4. Período nulo, cero o negativo
        for (badPeriod in listOf(null, 0L, -1L)) {
            val res = useCase.saveExpense(
                amountInput = "25.0",
                dateInput = "2026-05-10",
                descriptionInput = "Bad period",
                categoryInput = "Alimentación",
                periodId = badPeriod,
                budgetId = p1BudgetId,
                budgetCategoryInput = "Fijos"
            )
            assertTrue("Debe rechazar periodId=$badPeriod", res is ExpenseResult.Error)
        }

        // 5. Fechas vacías o malformadas
        for (badDate in listOf("", "   ", "not-a-date", "2026-13-45")) {
            val res = useCase.saveExpense(
                amountInput = "25.0",
                dateInput = badDate,
                descriptionInput = "Bad date",
                categoryInput = "Alimentación",
                periodId = 1L,
                budgetId = p1BudgetId,
                budgetCategoryInput = "Fijos"
            )
            assertTrue("VULNERABILIDAD EXPUESTA: Aceptó fecha inválida '$badDate'", res is ExpenseResult.Error)
        }

        // 6. Actualizar un gasto con ID inexistente debe retornar Error
        val missingUpdate = useCase.updateExpense(
            id = 99999L,
            amountInput = "30.0",
            dateInput = "2026-05-10",
            descriptionInput = "Fantasma",
            categoryInput = "Alimentación",
            periodId = 1L,
            budgetId = p1BudgetId,
            budgetCategoryInput = "Fijos"
        )
        assertTrue(
            "VULNERABILIDAD EXPUESTA: updateExpense con id inexistente retornó Success",
            missingUpdate is ExpenseResult.Error
        )
    }

    // =========================================================================
    // 8. DESBORDAMIENTO DE DOUBLE -> Float.NaN Y PÉRDIDA DE PRECISIÓN EN startEditing
    // =========================================================================
    /**
     * VULNERABILIDADES DETECTADAS:
     * 1. En `ExpenseUseCase.calculateCategoryExpenses` y `IncomeUseCase.calculateCategoryBudgets`:
     *    cuando dos montos finitos grandes (`1e308 + 1e308`) suman `Double.POSITIVE_INFINITY`,
     *    `spent / totalSpent = Infinity / Infinity = Double.NaN`. En Kotlin, `Float.NaN.coerceIn(0f, 1f)`
     *    retorna `Float.NaN` (no está contenido en `[0f, 1f]`), rompiendo `LinearProgressIndicator`.
     * 2. En `ExpenseViewModel.startEditing`:
     *    - `1e20 % 1.0 == 0.0` evalúa `true` y `1e20.toLong()` satura a `Long.MAX_VALUE`.
     *    - `0.0001.toString()` genera `"1.0E-4"` (notación científica incompatible con el filtro de `AddEditExpenseDialog`).
     */
    @Test
    fun test_adversarial_overflow_double_infinity_and_start_editing_scientific_notation_and_precision_loss() = runViewModelTest {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val expenseUseCase = ExpenseUseCase(expenseRepo, budgetRepo)
        val incomeUseCase = IncomeUseCase(incomeRepo, budgetRepo, expenseRepository = expenseRepo)

        val overflowExpenses = listOf(
            Expense(id = 1L, amount = 1e308, date = "2026-05-10", description = "Huge1", category = "Alimentación", periodId = 1L, budgetId = 1L, budgetCategory = "Fijos"),
            Expense(id = 2L, amount = 1e308, date = "2026-05-11", description = "Huge2", category = "Alimentación", periodId = 1L, budgetId = 1L, budgetCategory = "Fijos")
        )
        val categoryItems = expenseUseCase.calculateCategoryExpenses(overflowExpenses, emptyList())
        assertEquals(1, categoryItems.size)
        assertFalse(
            "VULNERABILIDAD EXPUESTA: progress resultó NaN tras suma desbordada a Infinity",
            categoryItems.single().progress.isNaN()
        )
        assertTrue(categoryItems.single().progress in 0.0f..1.0f)

        val overflowBudgets = listOf(
            Budget(id = 1L, amount = 1e308, category = "Fijos", periodId = 1L),
            Budget(id = 2L, amount = 1e308, category = "Fijos", periodId = 1L)
        )
        val budgetItems = incomeUseCase.calculateCategoryBudgets(
            budgets = overflowBudgets,
            categories = emptyList(),
            expenses = emptyList(),
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = 1L
        )
        assertFalse(
            "VULNERABILIDAD EXPUESTA: CategoryBudgetItem.progress resultó NaN tras suma desbordada de presupuestos",
            budgetItems.single().progress.isNaN()
        )

        val vm = ExpenseViewModel(expenseUseCase, CategoryUseCase(AdversarialCategoryRepo()))
        vm.startEditing(
            Expense(id = 3L, amount = 1e20, date = "2026-05-10", description = "Max", category = "Alimentación")
        )
        val hugeAmountStr = vm.uiState.value.editingExpense?.amount.orEmpty()
        assertFalse(
            "VULNERABILIDAD EXPUESTA: startEditing truncó 1e20 a Long.MAX_VALUE ($hugeAmountStr)",
            hugeAmountStr == Long.MAX_VALUE.toString()
        )

        vm.startEditing(
            Expense(id = 4L, amount = 0.0001, date = "2026-05-10", description = "Micro", category = "Alimentación")
        )
        val microAmountStr = vm.uiState.value.editingExpense?.amount.orEmpty()
        assertFalse(
            "VULNERABILIDAD EXPUESTA: startEditing generó notación científica '$microAmountStr' que el input numérico mutila",
            microAmountStr.contains('E', ignoreCase = true)
        )
    }

    // =========================================================================
    // 9. UI EDGE CASES: SOBREGIRO, PORCENTAJE IEEE 754, COLOR HEX INVÁLIDO Y UNICODE
    // =========================================================================
    /**
     * Verifica en Compose UI:
     *   1. `ExpenseOverviewCard` con sobregiro (`250 / 100` -> `250%`, restante `$0.00`).
     *   2. `ExpenseAllocationCard` ante color hex corrupto (`"NOT_A_HEX"`), icono vacío y caracteres Unicode extremos.
     */
    @Test
    fun test_adversarial_expense_overview_over_budget_and_allocation_malformed_hex_unicode() {
        val i18n = buildI18nManager()
        val currency = defaultCurrencies.first()
        val expenseUseCase = ExpenseUseCase(AdversarialExpenseRepo())
        val weirdCat = Category(name = "Café & Тест 🚀🔥", icon = "", color = "NOT_A_HEX", subcategories = emptyList())
        val weirdItems = expenseUseCase.calculateCategoryExpenses(
            expenses = listOf(
                Expense(id = 1L, amount = 250.0, date = "2026-05-10", description = "Ex", category = "  Café & Тест 🚀🔥  ", subcategory = "Sub")
            ),
            categories = listOf(weirdCat)
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column {
                    ExpenseOverviewCard(
                        totalSpent = 250.0,
                        totalBudgeted = 100.0,
                        currency = currency
                    )
                    ExpenseAllocationCard(
                        categoryExpenses = weirdItems,
                        currency = currency
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mini_card_expense_Café & Тест 🚀🔥").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_expense_budget_remaining")
            .assertTextEquals(CurrencyFormatter.format(0.0, currency))
        composeTestRule.onNodeWithTag("text_expense_percent_badge")
            .assertTextEquals("250%")
    }

    /**
     * VULNERABILIDAD DETECTADA EN ExpenseOverviewCard.kt (línea 55):
     * `((totalSpent / totalBudgeted) * 100).toInt()` trunca por imprecisión IEEE 754:
     * `(29.0 / 100.0) * 100.0 == 28.999999999999996`, por lo que `.toInt()` muestra `"28%"`
     * en lugar de `"29%"`.
     */
    @Test
    fun test_adversarial_expense_overview_ieee754_percent_truncation() {
        val i18n = buildI18nManager()
        val currency = defaultCurrencies.first()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                ExpenseOverviewCard(
                    totalSpent = 29.0,
                    totalBudgeted = 100.0,
                    currency = currency
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_expense_budget_remaining")
            .assertTextEquals(CurrencyFormatter.format(71.0, currency))
        composeTestRule.onNodeWithTag("text_expense_percent_badge")
            .assertTextEquals("29%")
    }

    // =========================================================================
    // 10. PERSISTENCIA REAL EN ROOM (AppDatabase v5): ROUNDTRIP, AISLAMIENTO Y periodId=0L
    // =========================================================================
    /**
     * Prueba directamente en SQLite (Room v5) los nuevos campos `periodId`, `budgetId`, `budgetCategory`:
     *   - Mapeo bidireccional `0L`/`""` <-> `NULL` <-> `0L`/`""` para gastos legacy/sin asignar.
     *   - Aislamiento estricto por `periodId` en `ExpenseDao.getByPeriod`.
     *   - Actualización de `periodId`, `budgetId` y `budgetCategory` mediante `updateExpense`.
     *   - Divergencia entre `RoomExpenseRepository.getExpensesByPeriod(0L)` (SQL `WHERE periodId = 0` sobre `NULL`)
     *     y la implementación por defecto de `ExpenseRepository.getExpensesByPeriod(0L)`.
     */
    @Test
    fun test_adversarial_room_v5_expense_period_and_budget_roundtrip_isolation_and_zero_period_query() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val roomRepo = RoomExpenseRepository(db.expenseDao())

            roomRepo.insertExpense(
                Expense(
                    amount = 110.5,
                    date = "2026-05-10",
                    description = "Con vínculo completo 🚀",
                    category = "Alimentación",
                    subcategory = "",
                    periodId = 1L,
                    budgetId = 7L,
                    budgetCategory = "Food & Dining"
                )
            )
            roomRepo.insertExpense(
                Expense(
                    amount = 40.0,
                    date = "2026-06-10",
                    description = "Otro período",
                    category = "Transporte",
                    subcategory = "Metro",
                    periodId = 2L,
                    budgetId = 8L,
                    budgetCategory = "Transport"
                )
            )
            roomRepo.insertExpense(
                Expense(
                    amount = 25.0,
                    date = "2026-05-01",
                    description = "Sin período",
                    category = "Otros",
                    subcategory = "",
                    periodId = 0L,
                    budgetId = 0L,
                    budgetCategory = ""
                )
            )

            val p1 = roomRepo.getExpensesByPeriod(1L).first()
            assertEquals(1, p1.size)
            assertEquals(7L, p1.single().budgetId)
            assertEquals("Food & Dining", p1.single().budgetCategory)
            assertEquals("", p1.single().subcategory)

            // Actualizar presupuesto origen y categoría en Room v5
            roomRepo.updateExpense(
                p1.single().copy(
                    amount = 145.0,
                    periodId = 2L,
                    budgetId = 8L,
                    budgetCategory = "Transport"
                )
            )
            assertEquals(0, roomRepo.getExpensesByPeriod(1L).first().size)
            val p2 = roomRepo.getExpensesByPeriod(2L).first()
            assertEquals(2, p2.size)
            assertTrue(p2.any { it.amount == 145.0 && it.budgetId == 8L && it.budgetCategory == "Transport" })

            // Contrato de ExpenseRepository.getExpensesByPeriod(0L) vs Room SQL WHERE periodId = 0 sobre NULL
            val defaultInterfaceZero = object : ExpenseRepository {
                override fun getAllExpenses() = roomRepo.getAllExpenses()
                override suspend fun insertExpense(expense: Expense) = roomRepo.insertExpense(expense)
                override suspend fun updateExpense(expense: Expense) = roomRepo.updateExpense(expense)
                override suspend fun deleteExpense(id: Long) = roomRepo.deleteExpense(id)
            }
            val expectedZeroPeriodCount = defaultInterfaceZero.getExpensesByPeriod(0L).first().size
            val roomZeroPeriodCount = roomRepo.getExpensesByPeriod(0L).first().size
            assertEquals(
                "VULNERABILIDAD EXPUESTA: RoomExpenseRepository.getExpensesByPeriod(0L) diverge de ExpenseRepository por NULL vs 0",
                expectedZeroPeriodCount,
                roomZeroPeriodCount
            )
        } finally {
            db.close()
        }
    }

    // =========================================================================
    // 11. NAVEGACIÓN CONTABILIDAD, SINCRONIZACIÓN DE PESTAÑAS Y CICLO DE VIDA
    // =========================================================================
    /**
     * Verifica la reestructuración de navegación de Story #9:
     *   - La barra inferior ya no incluye `tab_expenses`; `tab_incomes` muestra "Contabilidad" / "Accounting".
     *   - Bloqueo en Período cuando no existe período configurado y desbloqueo al crear uno.
     *   - `FinanceTopTabs` conserva la pestaña superior seleccionada (`EXPENSES`) al ir a Configuración,
     *     cambiar el idioma en tiempo real y volver a Contabilidad.
     *   - Si `selectedPeriodId` pasa a `null` en `IncomeViewModel`, `ExpenseViewModel` también debe sincronizarse
     *     limpiando sus datos en lugar de quedar reteniendo el período anterior.
     */
    @Test
    fun test_adversarial_navigation_and_accounting_tabs_state_preservation_and_null_period_sync() {
        val i18n = buildI18nManager()
        val settingsVm = SettingsViewModel(AdversarialLanguageRepo("es"), i18n)
        val periodRepo = AdversarialPeriodRepo(FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31"))
        val periodVm = PeriodViewModel(PeriodUseCase(periodRepo) { "2026-05-15" })

        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val incomeRepo = AdversarialIncomeRepo()
        runBlocking {
            budgetRepo.insertBudget(Budget(amount = 400.0, category = "Fijos", periodId = 1L))
        }
        val incomeVm = IncomeViewModel(IncomeUseCase(incomeRepo, budgetRepo, expenseRepository = expenseRepo))
        val expenseVm = ExpenseViewModel(ExpenseUseCase(expenseRepo, budgetRepo), CategoryUseCase(AdversarialCategoryRepo()))
        incomeVm.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                val periodState by periodVm.uiState.collectAsState()
                MainContainer(
                    settingsViewModel = settingsVm,
                    periodViewModel = periodVm,
                    i18nManager = i18n,
                    incomesContent = {
                        IncomesView(
                            incomeViewModel = incomeVm,
                            expenseViewModel = expenseVm,
                            allPeriods = periodState.allPeriods,
                            selectedPeriodId = periodState.activePeriod?.id,
                            onPeriodSelected = { id ->
                                incomeVm.onPeriodSelected(id)
                                expenseVm.onPeriodSelected(id)
                            },
                            i18nManager = i18n
                        )
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Barra inferior sin Gastos y con Contabilidad
        composeTestRule.onNodeWithTag("tab_expenses").assertDoesNotExist()
        composeTestRule.onNodeWithText("Contabilidad").assertIsDisplayed()

        // 2. Ir a pestaña superior Gastos y verificar estado sincronizado del período 1
        composeTestRule.onNodeWithTag("tab_top_expenses").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("container_expenses_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsEnabled()

        // 3. Ir a Configuración, cambiar idioma a Inglés, volver a Contabilidad -> debe mantener tab Gastos traducida
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lang_option_en").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_incomes").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Accounting").assertIsDisplayedCountAtLeast(1)
        composeTestRule.onNodeWithTag("container_expenses_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expense Overview").assertIsDisplayed()

        // 4. Deseleccionar período en IncomeViewModel -> ExpenseViewModel debe quedar también sin período ni presupuestos activos
        incomeVm.onPeriodSelected(null)
        composeTestRule.waitForIdle()
        assertFalse(
            "VULNERABILIDAD EXPUESTA: Al limpiar selectedPeriodId a null en IncomeViewModel, ExpenseViewModel retuvo el período anterior",
            expenseVm.uiState.value.hasActiveBudget
        )
    }

    /**
     * Edge case reactivo/concurrencia en UI:
     * Si el único presupuesto activo del período se elimina mientras el usuario tiene abierto
     * el diálogo de creación de gasto, el guardado debe bloquearse con error de dominio y la UI
     * debe mostrar el banner sin presupuesto y deshabilitar `btn_open_add_expense`.
     */
    @Test
    fun test_adversarial_expenses_tab_reactively_blocks_save_and_disables_button_when_last_budget_deleted() {
        val expenseRepo = AdversarialExpenseRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val i18n = buildI18nManager()
        val budgetId = runBlocking {
            budgetRepo.insertBudget(Budget(amount = 300.0, category = "Food & Dining", periodId = 1L))
        }
        val expenseVm = ExpenseViewModel(
            ExpenseUseCase(expenseRepo, budgetRepo),
            CategoryUseCase(AdversarialCategoryRepo())
        )
        expenseVm.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpensesTabContent(viewModel = expenseVm)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Abrir el diálogo mientras el presupuesto aún existe
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        // Eliminar el último presupuesto del período mientras el diálogo está abierto
        runBlocking { budgetRepo.deleteBudget(budgetId) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_expense_amount").performTextInput("40")
        expenseVm.onCategoryChange("Alimentación", "")
        composeTestRule.onNodeWithTag("btn_save_expense_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_expense_dialog_error")
            .assertIsDisplayed()
            .assertTextEquals("Se requiere definir un presupuesto activo en el período")
        composeTestRule.onNodeWithTag("btn_close_expense_dialog").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("banner_no_active_budget").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsNotEnabled()
    }

    // =========================================================================
    // 12. PROPERTY-BASED TESTING (500 ITERACIONES): INVARIANTES MATEMÁTICAS Y DE AGRUPACIÓN
    // =========================================================================
    /**
     * PRUEBA BASADA EN PROPIEDADES (500 iteraciones aleatorias):
     * Para cualquier conjunto aleatorio de presupuestos en Período 1 y Período 2 y cualquier conjunto
     * de gastos vinculados por `budgetId` + `budgetCategory` y clasificados independientemente en
     * categorías de gasto (con o sin subcategoría, incluyendo sobregiros):
     *   1. `ExpenseUseCase.calculateCategoryExpenses` agrupa estrictamente por categoría principal
     *      (sin duplicados case/trim, subcategorías consolidadas, ordenado descendente por monto).
     *   2. La suma de `spentAmount` por categoría de gasto es exactamente igual al total gastado del período.
     *   3. Todo `progress` pertenece a `[0.0f, 1.0f]` (sin `NaN`) y `percent` pertenece a `0..100`.
     *   4. `IncomeUseCase.calculateTotalBudgetSpent` descuenta exclusivamente gastos del Período 1
     *      (ignorando gastos del Período 2 incluso si sus fechas caen dentro del Período 1).
     *   5. `IncomeUseCase.calculateRemainingBudget` siempre es `maxOf(0.0, totalBudgeted - totalSpent) >= 0.0`.
     *   6. `IncomeUseCase.calculateCategoryBudgets` deduce por `budgetId`/`budgetCategory` sin contaminarse
     *      por la categoría de gasto, con `remainingAmount == maxOf(0.0, budgetAmount - spentAmount) >= 0.0`.
     */
    @Test
    fun test_property_based_invariant_budget_conditioned_expenses_and_remaining_balances() {
        val random = Random(202611149)
        val expenseUseCase = ExpenseUseCase(AdversarialExpenseRepo())
        val incomeUseCase = IncomeUseCase(AdversarialIncomeRepo(), AdversarialBudgetRepo())

        val budgetCatNames = listOf("Food & Dining", "Housing", "Transport")
        val expenseCatNames = listOf("Alimentación", "  alimentación ", "Vivienda", "Ocio")
        val subcategories = listOf("", "   ", "Restaurante", "Mercado", "Café")
        val categories = listOf(
            Category("Alimentación", "🍔", "#1CC88A", emptyList()),
            Category("Vivienda", "🏠", "#4E73DF", emptyList()),
            Category("Ocio", "🎮", "#F6C23E", emptyList()),
            Category("Food & Dining", "🍔", "#1CC88A", emptyList()),
            Category("Housing", "🏠", "#4E73DF", emptyList()),
            Category("Transport", "🚗", "#36B9CC", emptyList())
        )

        for (iteration in 1..500) {
            val p1Budgets = mutableListOf<Budget>()
            val expectedBudgetByCat = mutableMapOf<String, Double>()
            val expectedSpentByBudgetCat = mutableMapOf<String, Double>()
            val expectedSpentByExpenseCat = mutableMapOf<String, Double>()

            val p1BudgetCount = random.nextInt(1, 7)
            var nextBudgetId = 1L
            for (i in 1..p1BudgetCount) {
                val cat = budgetCatNames[random.nextInt(budgetCatNames.size)]
                val amt = random.nextDouble(20.0, 600.0)
                val b = Budget(
                    id = nextBudgetId++,
                    amount = amt,
                    category = cat,
                    subcategory = if (random.nextBoolean()) "" else "Sub$i",
                    periodId = 1L
                )
                p1Budgets.add(b)
                val key = cat.trim().lowercase()
                expectedBudgetByCat[key] = (expectedBudgetByCat[key] ?: 0.0) + amt
            }

            val p2Budgets = mutableListOf<Budget>()
            val p2BudgetCount = random.nextInt(0, 4)
            for (i in 1..p2BudgetCount) {
                val cat = budgetCatNames[random.nextInt(budgetCatNames.size)]
                p2Budgets.add(Budget(id = nextBudgetId++, amount = random.nextDouble(50.0, 400.0), category = cat, periodId = 2L))
            }

            val p1Expenses = mutableListOf<Expense>()
            val allExpenses = mutableListOf<Expense>()
            var expectedTotalSpentP1 = 0.0

            val p1ExpenseCount = random.nextInt(0, 12)
            var nextExpenseId = 1L
            for (e in 1..p1ExpenseCount) {
                val linkedBudget = p1Budgets[random.nextInt(p1Budgets.size)]
                val expCat = expenseCatNames[random.nextInt(expenseCatNames.size)]
                val expSub = subcategories[random.nextInt(subcategories.size)]
                val amt = random.nextDouble(1.0, 500.0)
                val expense = Expense(
                    id = nextExpenseId++,
                    amount = amt,
                    date = "2026-05-${random.nextInt(10, 28)}",
                    description = "Desc $e",
                    category = expCat,
                    subcategory = expSub,
                    periodId = 1L,
                    budgetId = linkedBudget.id,
                    budgetCategory = linkedBudget.category
                )
                p1Expenses.add(expense)
                allExpenses.add(expense)

                expectedTotalSpentP1 += amt
                val bKey = linkedBudget.category.trim().lowercase()
                expectedSpentByBudgetCat[bKey] = (expectedSpentByBudgetCat[bKey] ?: 0.0) + amt
                val eKey = expCat.trim().lowercase()
                expectedSpentByExpenseCat[eKey] = (expectedSpentByExpenseCat[eKey] ?: 0.0) + amt
            }

            // Gastos del Período 2 con fechas de mayo que NO deben contaminar al Período 1
            if (p2Budgets.isNotEmpty()) {
                val p2ExpenseCount = random.nextInt(0, 6)
                for (e in 1..p2ExpenseCount) {
                    val linkedBudget = p2Budgets[random.nextInt(p2Budgets.size)]
                    allExpenses.add(
                        Expense(
                            id = nextExpenseId++,
                            amount = random.nextDouble(10.0, 300.0),
                            date = "2026-05-15",
                            description = "Other period overlapping date",
                            category = expenseCatNames[random.nextInt(expenseCatNames.size)],
                            periodId = 2L,
                            budgetId = linkedBudget.id,
                            budgetCategory = linkedBudget.category
                        )
                    )
                }
            }

            // 1. Invariantes de ExpenseUseCase.calculateCategoryExpenses
            val catExpenses = expenseUseCase.calculateCategoryExpenses(p1Expenses, categories)
            assertEquals("Iter $iteration: claves únicas por categoría", expectedSpentByExpenseCat.size, catExpenses.size)
            assertEquals("Iter $iteration: suma por categoría == total gastado", expectedTotalSpentP1, catExpenses.sumOf { it.spentAmount }, 0.001)
            for (idx in 0 until catExpenses.size - 1) {
                assertTrue("Iter $iteration: ordenado descendente", catExpenses[idx].spentAmount >= catExpenses[idx + 1].spentAmount)
            }
            catExpenses.forEach { item ->
                val expected = expectedSpentByExpenseCat[item.categoryName.trim().lowercase()] ?: 0.0
                assertEquals("Iter $iteration: spentAmount exacto para ${item.categoryName}", expected, item.spentAmount, 0.001)
                assertTrue("Iter $iteration: progress en [0,1]", !item.progress.isNaN() && item.progress in 0.0f..1.0f)
                assertTrue("Iter $iteration: percent en 0..100", item.percent in 0..100)
            }

            // 2. Invariantes de IncomeUseCase para saldo global y por categoría de presupuesto
            val totalBudgeted = incomeUseCase.calculateTotalBudgeted(p1Budgets)
            val totalSpent = incomeUseCase.calculateTotalBudgetSpent(
                budgets = p1Budgets,
                expenses = allExpenses,
                periodStartDate = "2026-05-01",
                periodEndDate = "2026-05-31",
                periodId = 1L
            )
            assertEquals("Iter $iteration: totalSpent global debe aislar Período 1", expectedTotalSpentP1, totalSpent, 0.001)

            val globalRemaining = incomeUseCase.calculateRemainingBudget(totalBudgeted, totalSpent)
            assertTrue("Iter $iteration: globalRemaining >= 0", globalRemaining >= 0.0)
            assertEquals("Iter $iteration: globalRemaining == max(0, budgeted - spent)", maxOf(0.0, totalBudgeted - totalSpent), globalRemaining, 0.001)

            val catBudgets = incomeUseCase.calculateCategoryBudgets(
                budgets = p1Budgets,
                categories = categories,
                expenses = allExpenses,
                periodStartDate = "2026-05-01",
                periodEndDate = "2026-05-31",
                periodId = 1L
            )
            assertEquals("Iter $iteration: consolida categorías presupuestadas", expectedBudgetByCat.size, catBudgets.size)
            catBudgets.forEach { item ->
                val key = item.categoryName.trim().lowercase()
                val expBudget = expectedBudgetByCat[key] ?: 0.0
                val expSpent = expectedSpentByBudgetCat[key] ?: 0.0
                assertEquals("Iter $iteration: budgetAmount para $key", expBudget, item.budgetAmount, 0.001)
                assertEquals("Iter $iteration: spentAmount para $key", expSpent, item.spentAmount, 0.001)
                assertEquals("Iter $iteration: remainingAmount >= 0 para $key", maxOf(0.0, expBudget - expSpent), item.remainingAmount, 0.001)
                assertTrue("Iter $iteration: remainingAmount nunca negativo", item.remainingAmount >= 0.0)
                assertTrue("Iter $iteration: budget progress en [0,1]", !item.progress.isNaN() && item.progress in 0.0f..1.0f)
                assertTrue("Iter $iteration: budget percent en 0..100", item.percent in 0..100)
            }
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertIsDisplayedCountAtLeast(minCount: Int) {
    val count = fetchSemanticsNodes().size
    assertTrue("Expected at least $minCount matching displayed nodes, found $count", count >= minCount)
}
