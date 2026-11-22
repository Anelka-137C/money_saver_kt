package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.ui.components.savings.AddSavingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddSavingDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addSavingDialog_allowsSelectingIncomeDateTimeWithCalendarAndClockAndEnteringNote() {
        val incomes = listOf(
            Income(
                id = 10L,
                amount = 1500.0,
                dateTime = "2026-11-01 09:00",
                category = "Salario",
                subcategory = "Nómina",
                periodId = 1L
            ),
            Income(
                id = 11L,
                amount = 300.0,
                dateTime = "2026-11-10 12:00",
                category = "Freelance",
                periodId = 1L
            )
        )

        var selectedIncome: Income? = null
        var selectedDateTime = "2026-11-22 10:30"
        var enteredNote = ""

        composeTestRule.setContent {
            var amount by remember { mutableStateOf("200") }
            var incomeId by remember { mutableStateOf<Long?>(null) }
            var incomeCat by remember { mutableStateOf("") }
            var incomeSub by remember { mutableStateOf("") }
            var dateTime by remember { mutableStateOf(selectedDateTime) }
            var note by remember { mutableStateOf("") }

            AddSavingDialog(
                amount = amount,
                incomes = incomes,
                selectedIncomeId = incomeId,
                selectedIncomeCategory = incomeCat,
                selectedIncomeSubcategory = incomeSub,
                category = "Emergencias",
                subcategory = "",
                dateTime = dateTime,
                note = note,
                errorMessage = null,
                onAmountChange = { amount = it },
                onIncomeSelected = {
                    selectedIncome = it
                    incomeId = it.id
                    incomeCat = it.category
                    incomeSub = it.subcategory
                },
                onSelectCategoryClick = {},
                onDateTimeChange = {
                    dateTime = it
                    selectedDateTime = it
                },
                onNoteChange = {
                    note = it
                    enteredNote = it
                },
                onSaveClick = {},
                onCloseClick = {}
            )
        }

        // 1. Source income selector opens dropdown and selects income
        composeTestRule.onNodeWithTag("selector_saving_income").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("income_option_10").assertIsDisplayed().performClick()
        assertEquals(10L, selectedIncome?.id)

        // 2. Date & time field opens calendar (DatePicker) then clock (TimePicker)
        composeTestRule.onNodeWithTag("field_date_time").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_date_picker").performClick()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_time_picker").performClick()
        assertTrue(
            "Expected YYYY-MM-DD HH:mm format, got '$selectedDateTime'",
            selectedDateTime.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
        )

        // 3. Note input accepts text
        composeTestRule.onNodeWithTag("input_saving_note").performScrollTo().performTextInput("Ahorro para emergencias")
        assertEquals("Ahorro para emergencias", enteredNote)
    }

    @Test
    fun addSavingDialog_showsEmptyOptionWhenNoIncomesInPeriod() {
        composeTestRule.setContent {
            AddSavingDialog(
                amount = "",
                incomes = emptyList(),
                selectedIncomeId = null,
                selectedIncomeCategory = "",
                selectedIncomeSubcategory = "",
                category = "",
                subcategory = "",
                dateTime = "2026-11-22 10:30",
                note = "",
                errorMessage = null,
                onAmountChange = {},
                onIncomeSelected = {},
                onSelectCategoryClick = {},
                onDateTimeChange = {},
                onNoteChange = {},
                onSaveClick = {},
                onCloseClick = {}
            )
        }

        composeTestRule.onNodeWithTag("selector_saving_income").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("income_option_empty").assertIsDisplayed()
    }
}
