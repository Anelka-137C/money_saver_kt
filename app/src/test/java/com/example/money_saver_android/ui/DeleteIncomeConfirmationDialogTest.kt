package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.ui.components.incomes.DeleteIncomeConfirmationDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteIncomeConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_delete_income_dialog_displays_date_below_category_and_not_side_by_side() {
        val income = Income(
            id = 1L,
            amount = 1200.0,
            dateTime = "2026-05-15 10:00",
            category = "Employment",
            subcategory = "Salary",
            description = "Main salary",
            periodId = 1L
        )

        composeTestRule.setContent {
            DeleteIncomeConfirmationDialog(
                income = income,
                onConfirm = {},
                onDismiss = {}
            )
        }

        // Subcategory is displayed as primary header
        composeTestRule.onNodeWithText("Salary").assertIsDisplayed()

        // Category is displayed
        composeTestRule.onNodeWithText("Employment").assertIsDisplayed()

        // Date is displayed as separate text below category
        composeTestRule.onNodeWithText("2026-05-15 10:00").assertIsDisplayed()

        // The old combined text ("Employment • 2026-05-15 10:00") must NOT exist
        composeTestRule.onNodeWithText("Employment • 2026-05-15 10:00").assertDoesNotExist()
    }

    @Test
    fun test_delete_income_dialog_with_empty_description_and_matching_subcategory() {
        val income = Income(
            id = 2L,
            amount = 350.0,
            dateTime = "2026-06-20 18:30",
            category = "Freelance",
            subcategory = "Freelance",
            description = "",
            periodId = 1L
        )

        composeTestRule.setContent {
            DeleteIncomeConfirmationDialog(
                income = income,
                onConfirm = {},
                onDismiss = {}
            )
        }

        // Date is displayed
        composeTestRule.onNodeWithText("2026-06-20 18:30").assertIsDisplayed()

        // Side-by-side format must not exist
        composeTestRule.onNodeWithText("Freelance • 2026-06-20 18:30").assertDoesNotExist()
    }
}
