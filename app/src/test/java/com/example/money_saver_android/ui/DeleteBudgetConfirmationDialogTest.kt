package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.ui.components.budget.DeleteBudgetConfirmationDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteBudgetConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_delete_budget_dialog_displays_details_slide_button_and_cancel_button() {
        val budget = Budget(
            id = 1L,
            amount = 450.0,
            category = "Food & Dining",
            subcategory = "Supermarket",
            periodId = 1L
        )
        val currency = defaultCurrencies.first()

        composeTestRule.setContent {
            DeleteBudgetConfirmationDialog(
                budget = budget,
                currency = currency,
                categoryIcon = "🍔",
                onConfirm = {},
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithTag("dialog_delete_budget").assertIsDisplayed()
        composeTestRule.onNodeWithText("🍔").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food & Dining").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supermarket").assertIsDisplayed()
        composeTestRule.onNodeWithText(CurrencyFormatter.format(450.0, currency)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_budget").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget").assertIsDisplayed()
    }

    @Test
    fun test_delete_budget_dialog_cancel_button_triggers_dismiss() {
        var dismissed = false
        val budget = Budget(
            id = 2L,
            amount = 200.0,
            category = "Transport",
            subcategory = "",
            periodId = 1L
        )

        composeTestRule.setContent {
            DeleteBudgetConfirmationDialog(
                budget = budget,
                onConfirm = {},
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithTag("btn_cancel_delete_budget").performClick()
        assertTrue(dismissed)
    }
}
