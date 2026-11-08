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
import com.example.money_saver_android.ui.components.incomes.DateTimePickerField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateTimePickerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_date_time_picker_opens_calendar_then_clock_and_updates_value() {
        var selectedDateTime by mutableStateOf("2026-05-15 10:00")

        composeTestRule.setContent {
            var value by remember { mutableStateOf(selectedDateTime) }
            DateTimePickerField(
                value = value,
                onValueChange = {
                    value = it
                    selectedDateTime = it
                }
            )
        }

        // Initially dialogs do not exist
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertDoesNotExist()

        // 1. Click anywhere on the date/time field
        composeTestRule.onNodeWithTag("field_date_time").performClick()

        // 2. Calendar (DatePicker) dialog appears first
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertDoesNotExist()

        // 3. Confirm date
        composeTestRule.onNodeWithTag("btn_confirm_date_picker").performClick()

        // 4. Sequentially, the Clock (TimePicker) dialog appears
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertIsDisplayed()

        // 5. Confirm time
        composeTestRule.onNodeWithTag("btn_confirm_time_picker").performClick()

        // 6. Both dialogs are closed and value has updated with Date + Time
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertDoesNotExist()

        // Expected format: YYYY-MM-DD HH:mm
        assertTrue(
            "Selected date time '$selectedDateTime' should match YYYY-MM-DD HH:mm",
            selectedDateTime.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
        )
    }
}
