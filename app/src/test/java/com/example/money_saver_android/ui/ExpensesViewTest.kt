package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.FakeCategoryRepoForVm
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpensesViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: ExpenseViewModel
    private lateinit var i18nManager: I18nManager

    @Before
    fun setUp() {
        i18nManager = I18nManager()
        val expenseRepo = FakeExpenseRepository()
        val categoryRepo = FakeCategoryRepoForVm()
        val expenseUseCase = ExpenseUseCase(expenseRepo)
        val categoryUseCase = CategoryUseCase(categoryRepo)
        viewModel = ExpenseViewModel(expenseUseCase, categoryUseCase)
    }

    @Test
    fun test_add_and_edit_expense_inputs_display_active_currency_symbol() {
        val eur = Currency(code = "EUR", name = "Euro", symbol = "€", formatPattern = "#,##0.00")

        composeTestRule.setContent {
            ExpensesView(
                viewModel = viewModel,
                currency = eur
            )
        }

        // Verify that the Add Expense card input has the active currency symbol "€"
        composeTestRule.onNodeWithTag("add_amount_currency_symbol").assertIsDisplayed()
        composeTestRule.onNodeWithText("€ ").assertIsDisplayed()

        // Start editing an expense
        val expense = Expense(1L, 50.0, "2026-11-07", "Cena", "Comida", "Restaurante")
        composeTestRule.runOnUiThread {
            viewModel.startEditing(expense)
        }

        // Verify that the Edit Expense dialog input has the active currency symbol "€"
        composeTestRule.onNodeWithTag("edit_amount_currency_symbol").assertIsDisplayed()
    }
}
