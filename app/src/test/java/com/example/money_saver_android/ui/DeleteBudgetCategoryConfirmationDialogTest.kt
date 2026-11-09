package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.ui.components.budget.DeleteBudgetCategoryConfirmationDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteBudgetCategoryConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_delete_budget_category_dialog_displays_category_details_and_buttons() {
        val category = Category(
            name = "Groceries",
            icon = "🛒",
            color = "#1CC88A",
            subcategories = emptyList()
        )

        composeTestRule.setContent {
            DeleteBudgetCategoryConfirmationDialog(
                category = category,
                onConfirm = {},
                onDismiss = {}
            )
        }

        // Dialog container is displayed
        composeTestRule.onNodeWithTag("dialog_delete_budget_category").assertIsDisplayed()

        // Category icon and name are displayed
        composeTestRule.onNodeWithText("🛒").assertIsDisplayed()
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()

        // Slide to confirm button is displayed
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_budget_category").assertIsDisplayed()

        // Cancel button is displayed
        composeTestRule.onNodeWithTag("btn_cancel_delete_budget_category").assertIsDisplayed()
    }

    @Test
    fun test_delete_budget_category_dialog_cancel_button_triggers_dismiss() {
        var dismissed = false
        val category = Category(
            name = "Utilities",
            icon = "💡",
            color = "#36B9CC",
            subcategories = emptyList()
        )

        composeTestRule.setContent {
            DeleteBudgetCategoryConfirmationDialog(
                category = category,
                onConfirm = {},
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithTag("btn_cancel_delete_budget_category").performClick()
        assertTrue(dismissed)
    }
}
