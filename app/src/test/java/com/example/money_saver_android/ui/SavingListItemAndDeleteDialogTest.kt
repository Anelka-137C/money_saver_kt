package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.ui.components.savings.DeleteSavingConfirmationDialog
import com.example.money_saver_android.ui.components.savings.SavingListItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingListItemAndDeleteDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savingListItem_matchesExpenseDesignAndTriggersEditAndDeleteFromMenu() {
        var editClicked = false
        var deleteClicked = false
        val currency = defaultCurrencies.first()
        val saving = Saving(
            id = 7L,
            amount = 250.0,
            category = "Emergencias",
            subcategory = "Salud",
            periodId = 1L,
            incomeId = 1L,
            incomeCategory = "Salario",
            dateTime = "2026-11-22 10:30",
            note = "Fondo médico"
        )

        composeTestRule.setContent {
            SavingListItem(
                saving = saving,
                currency = currency,
                onEditClick = { editClicked = true },
                onDeleteClick = { deleteClicked = true }
            )
        }

        composeTestRule.onNodeWithTag("saving_item_7").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salud").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fondo médico").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_saving_datetime_7").assertIsDisplayed()
        composeTestRule.onNodeWithText(CurrencyFormatter.format(250.0, currency)).assertIsDisplayed()

        // Open 3-dot menu and click Edit
        composeTestRule.onNodeWithTag("btn_options_saving_7").performClick()
        composeTestRule.onNodeWithTag("btn_edit_saving_7").assertIsDisplayed().performClick()
        assertTrue(editClicked)

        // Open 3-dot menu and click Delete
        composeTestRule.onNodeWithTag("btn_options_saving_7").performClick()
        composeTestRule.onNodeWithTag("btn_delete_saving_7").assertIsDisplayed().performClick()
        assertTrue(deleteClicked)
    }

    @Test
    fun deleteSavingConfirmationDialog_displaysSlideToConfirmButtonAndCancel() {
        var dismissed = false
        val currency = defaultCurrencies.first()
        val saving = Saving(
            id = 8L,
            amount = 300.0,
            category = "Viajes",
            subcategory = "Vuelos",
            periodId = 1L,
            dateTime = "2026-11-22 11:00",
            note = "Vacaciones"
        )

        composeTestRule.setContent {
            DeleteSavingConfirmationDialog(
                saving = saving,
                currency = currency,
                onConfirm = {},
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithTag("dialog_delete_saving").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vuelos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vacaciones").assertIsDisplayed()
        composeTestRule.onNodeWithText(CurrencyFormatter.format(300.0, currency)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_saving").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_saving").performClick()
        assertTrue(dismissed)
    }
}
