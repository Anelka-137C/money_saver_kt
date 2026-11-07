package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseCardItemTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_expense_card_displays_amount_with_active_currency_symbol_and_decimals() {
        val expense = Expense(
            id = 1L,
            amount = 15000.0,
            date = "2026-11-07",
            description = "Almuerzo",
            category = "Comida",
            subcategory = "Restaurante"
        )
        val cop = Currency(code = "COP", name = "Peso colombiano", symbol = "$", formatPattern = "#,##0")

        composeTestRule.setContent {
            ExpenseCardItem(
                expense = expense,
                currency = cop
            )
        }

        // Must display formatted amount with 0 decimals for COP
        composeTestRule.onNodeWithText("$ 15,000").assertIsDisplayed()
    }

    @Test
    fun test_expense_card_displays_euro_symbol_and_two_decimals() {
        val expense = Expense(
            id = 2L,
            amount = 42.5,
            date = "2026-11-07",
            description = "Libro",
            category = "Educación",
            subcategory = "Libros"
        )
        val eur = Currency(code = "EUR", name = "Euro", symbol = "€", formatPattern = "#,##0.00")

        composeTestRule.setContent {
            ExpenseCardItem(
                expense = expense,
                currency = eur
            )
        }

        // Must display formatted amount with € symbol and 2 decimals
        composeTestRule.onNodeWithText("€ 42.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("€").assertIsDisplayed()
    }
}
