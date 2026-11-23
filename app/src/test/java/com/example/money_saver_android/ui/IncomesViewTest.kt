package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import androidx.compose.runtime.CompositionLocalProvider
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeIncomeRepository
import com.example.money_saver_android.viewmodel.FinanceTopTab
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomesViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var incomeRepository: FakeIncomeRepository
    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var expenseCategoryRepository: FakeCategoryRepoForVm
    private lateinit var periodRepository: FakeFinancialPeriodRepository
    private lateinit var incomeViewModel: IncomeViewModel
    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var i18nManager: I18nManager

    private val period1 = FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31")
    private val period2 = FinancialPeriod(id = 2L, startDate = "2026-06-01", endDate = "2026-06-30")

    @Before
    fun setup() {
        incomeRepository = FakeIncomeRepository()
        budgetRepository = FakeBudgetRepository()
        expenseRepository = FakeExpenseRepository()
        expenseCategoryRepository = FakeCategoryRepoForVm()
        periodRepository = FakeFinancialPeriodRepository(initialPeriod = period1)
        runBlocking {
            periodRepository.savePeriod(period2)
            incomeRepository.insertCategory(
                Category(
                    name = "Employment",
                    icon = "💼",
                    color = "#4E73DF",
                    subcategories = listOf(Subcategory("Salary"))
                )
            )
            budgetRepository.insertCategory(
                Category(
                    name = "Food & Dining",
                    icon = "🍔",
                    color = "#1CC88A",
                    subcategories = listOf(Subcategory("Supermarket"))
                )
            )
            incomeRepository.insertIncome(
                Income(
                    id = 1L,
                    amount = 2323.0,
                    dateTime = "2026-05-15 10:00",
                    category = "Employment",
                    subcategory = "Salary",
                    description = "Main salary",
                    periodId = 1L
                )
            )
        }
        val incomeUseCase = IncomeUseCase(incomeRepository, budgetRepository, expenseRepository = expenseRepository)
        incomeViewModel = IncomeViewModel(incomeUseCase)
        incomeViewModel.onPeriodSelected(1L)

        val expenseUseCase = ExpenseUseCase(expenseRepository, budgetRepository)
        expenseViewModel = ExpenseViewModel(expenseUseCase, CategoryUseCase(expenseCategoryRepository))
        expenseViewModel.onPeriodSelected(1L)

        i18nManager = I18nManager()
        val esJson = """
            {
              "incomes": {
                "tabs": { "incomes": "Ingresos", "budget": "Presupuesto", "expenses": "Gastos" },
                "header": { "title": "Ingresos", "add_button": "Registrar Ingreso", "select_period": "Seleccionar período" },
                "overview": { "title": "Resumen de Ingresos", "total_income": "Ingreso total", "trend": "↑ +12% vs mes ant.", "used": "Usado" },
                "allocation": { "title": "Asignación de Ingresos", "details": "Detalles >", "budgeted": "Presupuestado", "savings": "Ahorros", "spent": "Spent", "available": "Disponible" },
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
                "edit_dialog": { "title": "Modificar Gasto", "save_button": "Guardar" },
                "options": { "edit": "Modificar", "delete": "Eliminar" },
                "edit_category_dialog": { "title": "Modificar Categoría", "name_label": "Nombre de la categoría", "name_placeholder": "Ej. Alimentación", "color_label": "Color", "cancel_button": "Cancelar", "save_button": "Guardar" },
                "delete_category_dialog": { "title": "¿Eliminar esta categoría?", "warning_message": "Esta acción no se puede deshacer. Desliza el botón para confirmar.", "cancel_button": "Cancelar" }
              },
              "budget": {
                "header": { "title": "Presupuesto", "add_button": "Registrar Presupuesto" },
                "overview": { "title": "Resumen de Presupuesto", "total_budgeted": "Total presupuestado", "spent_label": "Gastado", "remaining_label": "Restante global", "available_banner_title": "Tienes %s disponible", "available_banner_subtitle": "%s disponible para presupuestar." },
                "allocation": { "title": "Asignación de Presupuesto", "details": "Detalles >", "budget_label": "Presupuesto", "remaining_label": "Sobrante", "empty_message": "No hay presupuestos asignados para este período" },
                "add_dialog": { "title": "Registrar Presupuesto", "edit_title": "Modificar Presupuesto", "amount_label": "Monto", "amount_placeholder": "0.00", "category_label": "Categoría", "select_category": "Seleccionar categoría", "save_button": "✓ Guardar Presupuesto", "edit_save_button": "✓ Guardar Cambios" },
                "breakdown": { "title": "Desglose de presupuestos" },
                "history": { "empty_message": "No hay presupuestos registrados en este período" },
                "options": { "edit": "Modificar", "delete": "Eliminar" },
                "delete_dialog": { "title": "¿Eliminar este presupuesto?", "warning_message": "Esta acción no se puede deshacer. Desliza el botón para confirmar.", "cancel_button": "Cancelar" },
                "delete_category_dialog": { "title": "¿Eliminar esta categoría?", "warning_message": "Esta acción no se puede deshacer. Desliza el botón para confirmar.", "cancel_button": "Cancelar" }
              }
            }
        """.trimIndent()
        val enJson = """
            {
              "incomes": {
                "header": { "title": "Incomes", "add_button": "Add Income", "select_period": "Select period" },
                "overview": { "title": "Income Overview", "total_income": "Total income", "trend": "↑ +12% vs Apr", "used": "Used" },
                "allocation": { "title": "Income Allocation", "details": "Details >", "budgeted": "Budgeted", "savings": "Savings", "spent": "Spent", "available": "Available" },
                "breakdown": { "title": "Income Breakdown" },
                "history": { "empty_message": "No income records in this period" }
              }
            }
        """.trimIndent()
        i18nManager.loadTranslation("es", esJson)
        i18nManager.loadTranslation("en", enJson)
        i18nManager.setLanguage("es")
    }

    @Test
    fun test_incomes_screen_displays_period_selector_form_and_incomes_list() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // 1. Period selector is displayed
        composeTestRule.onNodeWithTag("selector_period").assertIsDisplayed()

        // 2. Income Overview card is displayed
        composeTestRule.onNodeWithTag("card_income_overview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_total_income").assertIsDisplayed()

        // 3. Income Allocation card is displayed
        composeTestRule.onNodeWithTag("card_income_allocation").performScrollTo().assertIsDisplayed()

        // 4. Income Breakdown list is displayed
        composeTestRule.onNodeWithTag("list_income_breakdown").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("income_item_1").performScrollTo().assertIsDisplayed()

        // 5. Add income section / button is displayed
        composeTestRule.onNodeWithTag("btn_toggle_add_income").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_changing_period_in_selector_updates_displayed_incomes_and_overview() {
        runBlocking {
            incomeRepository.insertIncome(
                Income(
                    id = 2L,
                    amount = 500.0,
                    dateTime = "2026-06-10 14:00",
                    category = "Employment",
                    subcategory = "Bonus",
                    description = "June bonus",
                    periodId = 2L
                )
            )
        }

        var currentSelectedPeriodId by androidx.compose.runtime.mutableStateOf(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = currentSelectedPeriodId,
                    onPeriodSelected = {
                        currentSelectedPeriodId = it
                        incomeViewModel.onPeriodSelected(it)
                    },
                    i18nManager = i18nManager
                )
            }
        }

        // Click period selector to open dropdown
        composeTestRule.onNodeWithTag("selector_period").performScrollTo().performClick()

        // Select Period 2
        composeTestRule.onNodeWithTag("period_option_2").performClick()

        // Now period 2 is selected: item 2 is shown and item 1 is not in this period
        composeTestRule.onNodeWithTag("list_income_breakdown").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("income_item_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("income_item_1").assertDoesNotExist()
    }

    @Test
    fun test_incomes_screen_texts_update_with_i18n_language_change() {
        var lang by androidx.compose.runtime.mutableStateOf("es")
        i18nManager.setLanguage("es")

        composeTestRule.setContent {
            androidx.compose.runtime.key(lang) {
                CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                    IncomesView(
                        incomeViewModel = incomeViewModel,
                        allPeriods = listOf(period1, period2),
                        selectedPeriodId = 1L,
                        onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                        i18nManager = i18nManager
                    )
                }
            }
        }

        // Spanish texts
        composeTestRule.onNodeWithText("Resumen de Ingresos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingreso total").assertIsDisplayed()

        // Switch language to English
        composeTestRule.runOnUiThread {
            i18nManager.setLanguage("en")
            lang = "en"
        }

        // English texts
        composeTestRule.onNodeWithText("Income Overview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total income").assertIsDisplayed()
    }

    @Test
    fun test_income_allocation_card_displays_budgeted_and_savings_and_does_not_display_spent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Budgeted and Savings must be displayed in allocation card
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("card_income_allocation")) and hasText("Presupuestado"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("card_income_allocation")) and hasText("Ahorros"))
            .performScrollTo()
            .assertIsDisplayed()

        // "Gastado" / "Spent" section must NOT be displayed in allocation card
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("card_income_allocation")) and hasText("Gastado"))
            .assertDoesNotExist()
    }

    @Test
    fun test_delete_income_flow_from_incomes_view_opens_dialog_with_date_below_category() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Open options menu on income item 1
        composeTestRule.onNodeWithTag("list_income_breakdown").performScrollTo()
        composeTestRule.onNodeWithTag("btn_options_income_1").performScrollTo().performClick()

        // Click delete option
        composeTestRule.onNodeWithTag("btn_delete_income_1").performClick()

        // Confirmation dialog is displayed
        composeTestRule.onNodeWithTag("dialog_delete_income").assertIsDisplayed()

        // Category and Date are displayed separately, not combined with bullet
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("dialog_delete_income")) and hasText("Employment")).assertIsDisplayed()
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("dialog_delete_income")) and hasText("2026-05-15 10:00")).assertIsDisplayed()
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("dialog_delete_income")) and hasText("Employment • 2026-05-15 10:00")).assertDoesNotExist()
    }

    @Test
    fun test_incomes_history_displays_within_scrollable_container_with_multiple_items() {
        runBlocking {
            for (i in 2..6) {
                incomeRepository.insertIncome(
                    Income(
                        id = i.toLong(),
                        amount = 100.0 * i,
                        dateTime = "2026-05-1$i 10:00",
                        category = "Employment",
                        subcategory = "Bonus $i",
                        description = "Bonus number $i",
                        periodId = 1L
                    )
                )
            }
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Scroll to the income breakdown list
        composeTestRule.onNodeWithTag("list_income_breakdown").performScrollTo().assertIsDisplayed()

        // First item is displayed
        composeTestRule.onNodeWithTag("income_item_1").assertIsDisplayed()

        // Add income button remains readily accessible above the list
        composeTestRule.onNodeWithTag("btn_toggle_add_income").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_incomes_view_top_tabs_switch_between_income_and_budget_and_updates_allocation_card() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Top navigation tabs row and buttons are displayed
        composeTestRule.onNodeWithTag("row_finance_top_tabs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_top_incomes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_top_budget").assertIsDisplayed()

        // In Incomes tab: Allocation card is present
        composeTestRule.onNodeWithTag("card_income_allocation").assertExists()

        // Switch to Budget tab
        composeTestRule.onNodeWithTag("tab_top_budget").performClick()
        composeTestRule.waitForIdle()

        // Budget container and overview card exist
        composeTestRule.onNodeWithTag("container_budget_view").assertExists()
        composeTestRule.onNodeWithTag("card_budget_overview").assertExists()

        // Incomes breakdown is not displayed in budget tab
        composeTestRule.onNodeWithTag("list_income_breakdown").assertDoesNotExist()

        // Open Add Budget Dialog
        composeTestRule.onNodeWithTag("btn_toggle_add_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify Dialog is open
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertIsDisplayed()

        // Enter amount and select category
        composeTestRule.onNodeWithTag("input_budget_amount").performTextInput("500")
        incomeViewModel.onBudgetCategorySelected("Food & Dining", "Supermarket")

        // Save budget
        composeTestRule.onNodeWithTag("btn_save_budget").performClick()
        composeTestRule.waitForIdle()

        // Verify saved budget item is in the breakdown list
        composeTestRule.onNodeWithTag("budget_item_1").assertExists()
        composeTestRule.onNodeWithTag("text_budget_amount_1").assertExists()

        // Scroll to list, open three-dots menu, and edit the budget item
        composeTestRule.onNodeWithTag("list_budget_breakdown").performScrollTo()
        composeTestRule.onNodeWithTag("btn_options_budget_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_edit_budget_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertIsDisplayed()

        // Update amount to 750
        incomeViewModel.onEditBudgetAmountChange("750")
        composeTestRule.onNodeWithTag("btn_save_budget").performClick()
        composeTestRule.waitForIdle()

        // Switch back to Incomes tab
        composeTestRule.onNodeWithTag("tab_top_incomes").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_income_allocation").assertExists()

        // Switch to Budget tab and test error validation (exceeds available income)
        composeTestRule.onNodeWithTag("tab_top_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_toggle_add_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_budget_amount").performTextInput("9999")
        incomeViewModel.onBudgetCategorySelected("Food & Dining", "Supermarket")
        composeTestRule.onNodeWithTag("btn_save_budget").performClick()
        composeTestRule.waitForIdle()

        // Error message displayed
        composeTestRule.onNodeWithTag("text_budget_error").assertIsDisplayed()
    }

    @Test
    fun test_budget_category_deletion_triggers_slide_to_confirm_dialog() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        incomeViewModel.onTopTabSelected(FinanceTopTab.BUDGET)
        composeTestRule.waitForIdle()

        // 1. Request deletion of budget category triggers confirmation dialog
        incomeViewModel.requestDeleteBudgetCategory("Food & Dining")
        composeTestRule.waitForIdle()

        // Confirmation dialog with slide-to-confirm is displayed
        composeTestRule.onNodeWithTag("dialog_delete_budget_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_budget_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget_category").assertIsDisplayed()

        // 2. Cancel dismissal
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget_category").performClick()
        composeTestRule.waitForIdle()

        // Dialog is closed
        composeTestRule.onNodeWithTag("dialog_delete_budget_category").assertDoesNotExist()

        // 3. Request again and confirm deletion
        incomeViewModel.requestDeleteBudgetCategory("Food & Dining")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_budget_category").assertIsDisplayed()

        incomeViewModel.confirmDeleteBudgetCategory()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_delete_budget_category").assertDoesNotExist()
        runBlocking {
            val categories = budgetRepository.getAllCategories().first()
            assertTrue(categories.none { it.name == "Food & Dining" })
        }
    }

    @Test
    fun test_budget_tab_displays_budget_allocation_card_with_categories_and_colors() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }
        incomeViewModel.onTopTabSelected(FinanceTopTab.BUDGET)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_budget_allocation").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_budget_allocation_empty").performScrollTo().assertIsDisplayed()

        incomeViewModel.onBudgetAmountChange("500")
        incomeViewModel.onBudgetCategorySelected("Food & Dining", "Supermarket")
        incomeViewModel.saveBudget()
        composeTestRule.waitForIdle()

        val expectedRemaining = CurrencyFormatter.format(500.0, defaultCurrencies.first())
        composeTestRule.onNodeWithTag("mini_card_budget_Food & Dining").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_budget_remaining_Food & Dining").assertTextEquals(expectedRemaining)
        composeTestRule.onNodeWithTag("progress_budget_Food & Dining").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("mini_card_budget_Food & Dining")) and hasText("100%")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_budget_allocation_card_updates_remaining_reactively_when_expense_is_added() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }
        incomeViewModel.onTopTabSelected(FinanceTopTab.BUDGET)
        incomeViewModel.onBudgetAmountChange("500")
        incomeViewModel.onBudgetCategorySelected("Food & Dining", "Supermarket")
        incomeViewModel.saveBudget()
        composeTestRule.waitForIdle()

        runBlocking {
            expenseRepository.insertExpense(
                Expense(1L, 125.0, "2026-05-10", "Lunch", "Food & Dining", "Supermarket", periodId = 1L)
            )
        }
        composeTestRule.waitForIdle()

        val expectedRemaining = CurrencyFormatter.format(375.0, defaultCurrencies.first())
        composeTestRule.onNodeWithTag("text_budget_remaining_Food & Dining").performScrollTo().assertTextEquals(expectedRemaining)
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("mini_card_budget_Food & Dining")) and hasText("75%")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_creating_income_category_and_subcategory_does_not_hide_add_income_dialog() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // 1. Open Add Income Dialog
        composeTestRule.onNodeWithTag("btn_toggle_add_income").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_add_income").assertIsDisplayed()

        // 2. Simulate creating a category
        incomeViewModel.onNewCategoryNameChange("Investments")
        incomeViewModel.createCategory()
        composeTestRule.waitForIdle()

        // 3. Verify dialog is STILL displayed
        composeTestRule.onNodeWithTag("card_add_income").assertIsDisplayed()

        // 4. Simulate creating a subcategory
        incomeViewModel.onNewSubcategoryNameChange("Dividends")
        incomeViewModel.createSubcategory("Investments")
        composeTestRule.waitForIdle()

        // 5. Verify dialog is STILL displayed
        composeTestRule.onNodeWithTag("card_add_income").assertIsDisplayed()

        // 6. Complete and save income
        composeTestRule.onNodeWithTag("input_income_amount").performScrollTo().performTextInput("500")
        incomeViewModel.onDateTimeChange("2026-05-20 12:00")
        incomeViewModel.onCategorySelected("Investments", "Dividends")
        composeTestRule.onNodeWithTag("btn_save_income").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 7. Verify dialog is now closed after saving
        composeTestRule.onNodeWithTag("card_add_income").assertDoesNotExist()
    }

    @Test
    fun test_creating_budget_category_and_subcategory_does_not_hide_budget_dialog() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Switch to Budget tab
        incomeViewModel.onTopTabSelected(FinanceTopTab.BUDGET)
        composeTestRule.waitForIdle()

        // 1. Open Add Budget Dialog
        composeTestRule.onNodeWithTag("btn_toggle_add_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertIsDisplayed()

        // 2. Simulate creating a budget category
        incomeViewModel.onNewCategoryNameChange("Health & Fitness")
        incomeViewModel.createBudgetCategory()
        composeTestRule.waitForIdle()

        // 3. Verify dialog is STILL displayed
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertIsDisplayed()

        // 4. Simulate creating a budget subcategory
        incomeViewModel.onNewSubcategoryNameChange("Gym")
        incomeViewModel.createBudgetSubcategory("Health & Fitness")
        composeTestRule.waitForIdle()

        // 5. Verify dialog is STILL displayed
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertIsDisplayed()

        // 6. Complete and save budget
        composeTestRule.onNodeWithTag("input_budget_amount").performTextInput("150")
        incomeViewModel.onBudgetCategorySelected("Health & Fitness", "Gym")
        composeTestRule.onNodeWithTag("btn_save_budget").performClick()
        composeTestRule.waitForIdle()

        // 7. Verify dialog is now closed after saving
        composeTestRule.onNodeWithTag("card_add_edit_budget").assertDoesNotExist()
    }

    @Test
    fun test_expenses_tab_disables_button_and_shows_banner_when_no_budget_active() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    expenseViewModel = expenseViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Verify 3rd top tab exists and switch to Gastos
        composeTestRule.onNodeWithTag("tab_top_expenses").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("container_expenses_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("banner_no_active_budget").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun test_register_expense_with_two_selectors_updates_expense_and_budget_cards() {
        val budgetId = runBlocking {
            budgetRepository.insertBudget(
                Budget(
                    amount = 500.0,
                    category = "Food & Dining",
                    subcategory = "Supermarket",
                    periodId = 1L
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    expenseViewModel = expenseViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Open Gastos tab and verify add button is enabled when budget exists
        composeTestRule.onNodeWithTag("tab_top_expenses").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("banner_no_active_budget").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        // 1st expense ($150) using Selector 1 (budget item) + Selector 2 (Alimentación > Restaurante)
        composeTestRule.onNodeWithTag("card_add_edit_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_expense_amount").performTextInput("150")
        composeTestRule.onNodeWithTag("selector_expense_budget").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("budget_option_$budgetId").performClick()
        composeTestRule.waitForIdle()
        expenseViewModel.onCategoryChange("Alimentación", "Restaurante")
        composeTestRule.onNodeWithTag("btn_save_expense_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 2nd expense ($50) in same expense category without subcategory
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_expense_amount").performTextInput("50")
        composeTestRule.onNodeWithTag("selector_expense_budget").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("budget_option_$budgetId").performClick()
        composeTestRule.waitForIdle()
        expenseViewModel.onCategoryChange("Alimentación", "")
        composeTestRule.onNodeWithTag("btn_save_expense_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // ExpenseOverviewCard and category-only ExpenseAllocationCard consolidate both expenses ($200)
        composeTestRule.onNodeWithTag("text_expense_total_spent").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(200.0, defaultCurrencies.first()))
        composeTestRule.onNodeWithTag("mini_card_expense_Alimentación").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_expense_category_spent_Alimentación")
            .assertTextEquals(CurrencyFormatter.format(200.0, defaultCurrencies.first()))
        composeTestRule.onNodeWithTag("mini_card_expense_Restaurante").assertDoesNotExist()

        // Switch to Budget tab and verify PeriodSummaryCard total + category remaining balance
        composeTestRule.onNodeWithTag("tab_top_budget").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_total_budgeted").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(500.0, defaultCurrencies.first()))
        composeTestRule.onNodeWithTag("text_budget_remaining_Food & Dining").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(300.0, defaultCurrencies.first()))
    }

    @Test
    fun test_period_selector_syncs_expenses_and_clamps_over_budget_remaining_at_zero() {
        runBlocking {
            val budgetId = budgetRepository.insertBudget(
                Budget(
                    amount = 100.0,
                    category = "Food & Dining",
                    subcategory = "Supermarket",
                    periodId = 1L
                )
            )
            expenseRepository.insertExpense(
                Expense(
                    id = 10L,
                    amount = 160.0,
                    date = "2026-05-10",
                    description = "Exceso",
                    category = "Alimentación",
                    subcategory = "Restaurante",
                    periodId = 1L,
                    budgetId = budgetId,
                    budgetCategory = "Food & Dining"
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    expenseViewModel = expenseViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Remaining balance must clamp at 0.00 (no negative values) in Budget tab
        composeTestRule.onNodeWithTag("tab_top_budget").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_total_budgeted").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(100.0, defaultCurrencies.first()))
        composeTestRule.onNodeWithTag("text_budget_remaining_Food & Dining").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(0.0, defaultCurrencies.first()))

        // Switch period to Period 2 (which has no budgets or expenses)
        composeTestRule.onNodeWithTag("selector_period").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("period_option_2").performClick()
        composeTestRule.waitForIdle()

        // Switch to Gastos tab and verify Period 2 state synced cleanly
        composeTestRule.onNodeWithTag("tab_top_expenses").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_expense_total_spent").performScrollTo()
            .assertTextEquals(CurrencyFormatter.format(0.0, defaultCurrencies.first()))
        composeTestRule.onNodeWithTag("banner_no_active_budget").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun test_budget_breakdown_options_menu_displays_edit_and_delete_and_uses_slide_dialog_to_delete() {
        runBlocking {
            budgetRepository.insertBudget(
                Budget(
                    amount = 500.0,
                    category = "Food & Dining",
                    subcategory = "Supermarket",
                    periodId = 1L
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Switch to Budget tab
        incomeViewModel.onTopTabSelected(FinanceTopTab.BUDGET)
        composeTestRule.waitForIdle()

        // Budget item and three-dots options menu button are displayed
        composeTestRule.onNodeWithTag("list_budget_breakdown").performScrollTo()
        composeTestRule.onNodeWithTag("budget_item_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_options_budget_1").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Menu displays both Edit and Delete options
        composeTestRule.onNodeWithTag("btn_edit_budget_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_delete_budget_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Delete confirmation dialog with slide-to-confirm button is displayed
        composeTestRule.onNodeWithTag("dialog_delete_budget").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_budget").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget").assertIsDisplayed()

        // Cancel dismisses dialog without deleting budget
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_budget").assertDoesNotExist()
        composeTestRule.onNodeWithTag("budget_item_1").assertExists()

        // Re-open menu, click delete, and confirm deletion
        composeTestRule.onNodeWithTag("btn_options_budget_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_delete_budget_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_budget").assertIsDisplayed()

        incomeViewModel.confirmDeleteBudget()
        composeTestRule.waitForIdle()

        // Dialog is closed and budget item is removed
        composeTestRule.onNodeWithTag("dialog_delete_budget").assertDoesNotExist()
        composeTestRule.onNodeWithTag("budget_item_1").assertDoesNotExist()
        runBlocking {
            val remaining = budgetRepository.getBudgetsByPeriod(1L).first()
            assertTrue(remaining.isEmpty())
        }
    }

    @Test
    fun test_expenses_tab_category_edit_and_delete_dialogs_with_slide_button_and_direct_subcategory_delete() {
        runBlocking {
            budgetRepository.insertBudget(
                Budget(
                    amount = 500.0,
                    category = "Food & Dining",
                    subcategory = "Supermarket",
                    periodId = 1L
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    expenseViewModel = expenseViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = {
                        incomeViewModel.onPeriodSelected(it)
                        expenseViewModel.onPeriodSelected(it)
                    },
                    i18nManager = i18nManager
                )
            }
        }

        // Switch to Expenses tab
        composeTestRule.onNodeWithTag("tab_top_expenses").performClick()
        composeTestRule.waitForIdle()

        // 1. Delete subcategory directly (single delete action)
        expenseViewModel.deleteSubcategory("Alimentación", "Restaurante")
        composeTestRule.waitForIdle()
        runBlocking {
            val cat = expenseCategoryRepository.getAllCategories().first().first { it.name == "Alimentación" }
            assertTrue(cat.subcategories.isEmpty())
        }

        // 2. Edit category opens EditExpenseCategoryDialog
        val category = expenseViewModel.uiState.value.categories.first { it.name == "Alimentación" }
        expenseViewModel.startEditingCategory(category)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_edit_expense_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_edit_expense_category_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_edit_expense_category").assertIsDisplayed()
        expenseViewModel.onEditCategoryNameChange("Comida")
        composeTestRule.onNodeWithTag("btn_save_edit_expense_category").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_edit_expense_category").assertDoesNotExist()

        // 3. Delete category triggers confirmation dialog with SlideToConfirmButton
        expenseViewModel.requestDeleteCategory("Comida")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_expense_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_expense_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_expense_category").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_expense_category").assertDoesNotExist()

        // 4. Request again and confirm deletion removes category
        expenseViewModel.requestDeleteCategory("Comida")
        composeTestRule.waitForIdle()
        expenseViewModel.confirmDeleteCategory()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_expense_category").assertDoesNotExist()
        runBlocking {
            assertTrue(expenseCategoryRepository.getAllCategories().first().none { it.name == "Comida" })
        }
    }

    @Test
    fun test_add_expense_dialog_uses_calendar_and_clock_date_time_picker() {
        runBlocking {
            budgetRepository.insertBudget(
                Budget(
                    amount = 500.0,
                    category = "Food & Dining",
                    subcategory = "Supermarket",
                    periodId = 1L
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    expenseViewModel = expenseViewModel,
                    allPeriods = listOf(period1, period2),
                    selectedPeriodId = 1L,
                    onPeriodSelected = {
                        incomeViewModel.onPeriodSelected(it)
                        expenseViewModel.onPeriodSelected(it)
                    },
                    i18nManager = i18nManager
                )
            }
        }

        // Open Expenses tab and open Add Expense dialog
        composeTestRule.onNodeWithTag("tab_top_expenses").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_open_add_expense").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Tap DateTimePickerField -> opens Calendar dialog
        composeTestRule.onNodeWithTag("field_date_time").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()

        // Confirm calendar date -> opens Clock dialog
        composeTestRule.onNodeWithTag("btn_confirm_date_picker").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertIsDisplayed()

        // Confirm clock time -> closes dialog and updates dateInput with YYYY-MM-DD HH:mm
        composeTestRule.onNodeWithTag("btn_confirm_time_picker").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertDoesNotExist()
        assertTrue(
            "Expected YYYY-MM-DD HH:mm but was '${expenseViewModel.uiState.value.dateInput}'",
            expenseViewModel.uiState.value.dateInput.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
        )
    }

    @Test
    fun test_period_selector_displays_compact_readable_date_ranges_in_pill_and_dropdown() {
        val crossMonthPeriod = FinancialPeriod(id = 3L, startDate = "2026-05-15", endDate = "2026-06-14")
        val crossYearPeriod = FinancialPeriod(id = 4L, startDate = "2026-12-15", endDate = "2027-01-14")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    allPeriods = listOf(period1, crossMonthPeriod, crossYearPeriod),
                    selectedPeriodId = 1L,
                    onPeriodSelected = { incomeViewModel.onPeriodSelected(it) },
                    i18nManager = i18nManager
                )
            }
        }

        // Selector pill shows compact readable range for period1 (same month & year)
        val expectedPeriod1 = formatPeriodRange("2026-05-01", "2026-05-31", "es")
        composeTestRule.onNode(
            hasTestTag("selector_period") and hasText(expectedPeriod1)
        ).assertIsDisplayed()

        // Open dropdown and verify all 3 compact range formats are shown
        composeTestRule.onNodeWithTag("selector_period").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_option_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_option_3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_option_4").assertIsDisplayed()

        val expectedCrossMonth = formatPeriodRange("2026-05-15", "2026-06-14", "es")
        val expectedCrossYear = formatPeriodRange("2026-12-15", "2027-01-14", "es")
        composeTestRule.onNodeWithText(expectedCrossMonth).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedCrossYear).assertIsDisplayed()

        // Selecting a different period updates the selector pill to show the newly selected period
        composeTestRule.onNodeWithTag("period_option_3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasTestTag("selector_period") and hasText(expectedCrossMonth)
        ).assertIsDisplayed()
    }
}
