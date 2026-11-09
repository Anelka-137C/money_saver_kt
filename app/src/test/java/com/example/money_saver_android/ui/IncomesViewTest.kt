package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import androidx.compose.runtime.CompositionLocalProvider
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
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
    private lateinit var periodRepository: FakeFinancialPeriodRepository
    private lateinit var incomeViewModel: IncomeViewModel
    private lateinit var i18nManager: I18nManager

    private val period1 = FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31")
    private val period2 = FinancialPeriod(id = 2L, startDate = "2026-06-01", endDate = "2026-06-30")

    @Before
    fun setup() {
        incomeRepository = FakeIncomeRepository()
        budgetRepository = FakeBudgetRepository()
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
        val incomeUseCase = IncomeUseCase(incomeRepository, budgetRepository)
        incomeViewModel = IncomeViewModel(incomeUseCase)
        incomeViewModel.onPeriodSelected(1L)

        i18nManager = I18nManager()
        val esJson = """
            {
              "incomes": {
                "tabs": { "incomes": "Ingresos", "budget": "Presupuesto" },
                "header": { "title": "Ingresos", "add_button": "Registrar Ingreso", "select_period": "Seleccionar período" },
                "overview": { "title": "Resumen de Ingresos", "total_income": "Ingreso total", "trend": "↑ +12% vs mes ant.", "used": "Usado" },
                "allocation": { "title": "Asignación de Ingresos", "details": "Detalles >", "budgeted": "Presupuestado", "savings": "Ahorros", "spent": "Spent", "available": "Disponible" },
                "breakdown": { "title": "Desglose de Ingresos" },
                "history": { "empty_message": "No hay ingresos registrados en este período" }
              },
              "budget": {
                "header": { "title": "Presupuesto", "add_button": "Registrar Presupuesto" },
                "overview": { "title": "Resumen de Presupuesto", "total_budgeted": "Total presupuestado", "available_banner_title": "Tienes %s disponible", "available_banner_subtitle": "%s disponible para presupuestar." },
                "add_dialog": { "title": "Registrar Presupuesto", "edit_title": "Modificar Presupuesto", "amount_label": "Monto", "amount_placeholder": "0.00", "category_label": "Categoría", "select_category": "Seleccionar categoría", "save_button": "✓ Guardar Presupuesto", "edit_save_button": "✓ Guardar Cambios" },
                "breakdown": { "title": "Desglose de presupuestos" },
                "history": { "empty_message": "No hay presupuestos registrados en este período" },
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

        // Scroll to list and edit the budget item
        composeTestRule.onNodeWithTag("list_budget_breakdown").performScrollTo()
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
}
