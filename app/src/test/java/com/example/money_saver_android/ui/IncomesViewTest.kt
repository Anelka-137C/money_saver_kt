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
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import androidx.compose.runtime.CompositionLocalProvider
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.FakeIncomeRepository
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import kotlinx.coroutines.runBlocking
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
    private lateinit var periodRepository: FakeFinancialPeriodRepository
    private lateinit var incomeViewModel: IncomeViewModel
    private lateinit var i18nManager: I18nManager

    private val period1 = FinancialPeriod(id = 1L, startDate = "2026-05-01", endDate = "2026-05-31")
    private val period2 = FinancialPeriod(id = 2L, startDate = "2026-06-01", endDate = "2026-06-30")

    @Before
    fun setup() {
        incomeRepository = FakeIncomeRepository()
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
        incomeViewModel = IncomeViewModel(incomeRepository)
        incomeViewModel.onPeriodSelected(1L)

        i18nManager = I18nManager()
        val esJson = """
            {
              "incomes": {
                "header": { "title": "Ingresos", "add_button": "Registrar Ingreso", "select_period": "Seleccionar período" },
                "overview": { "title": "Resumen de Ingresos", "total_income": "Ingreso total", "trend": "↑ +12% vs mes ant.", "used": "Usado" },
                "allocation": { "title": "Asignación de Ingresos", "details": "Detalles >", "budgeted": "Presupuestado", "savings": "Ahorros", "spent": "Gastado", "available": "Disponible" },
                "breakdown": { "title": "Desglose de Ingresos" },
                "history": { "empty_message": "No hay ingresos registrados en este período" }
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
}
