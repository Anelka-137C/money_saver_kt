package com.example.money_saver_android.ui.components.incomes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date"
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val initialCalendar = remember(value) {
        Calendar.getInstance().apply {
            if (value.isNotBlank()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                    time = sdf.parse(value) ?: Date()
                } catch (_: Exception) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                        time = sdf.parse(value) ?: Date()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    var pendingDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(initialCalendar.time)
        )
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialCalendar.timeInMillis
    )

    val timePickerState = rememberTimePickerState(
        initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(Calendar.MINUTE),
        is24Hour = true
    )

    val displayValue = if (value.isNotBlank()) {
        value
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .clickable { showDatePicker = true }
            .testTag("field_date_time")
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayValue,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = "Pick date time",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            modifier = Modifier.testTag("date_picker_dialog"),
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_confirm_date_picker"),
                    onClick = {
                        val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        pendingDate = String.format(
                            Locale.ROOT,
                            "%04d-%02d-%02d",
                            utcCal.get(Calendar.YEAR),
                            utcCal.get(Calendar.MONTH) + 1,
                            utcCal.get(Calendar.DAY_OF_MONTH)
                        )
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text("OK", color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cancel_date_picker"),
                    onClick = { showDatePicker = false }
                ) {
                    Text(t("incomes.date_time_picker.cancel"), color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            modifier = Modifier.testTag("time_picker_dialog"),
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_confirm_time_picker"),
                    onClick = {
                        val formattedTime = String.format(
                            Locale.ROOT,
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        onValueChange("$pendingDate $formattedTime")
                        showTimePicker = false
                    }
                ) {
                    Text("OK", color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cancel_time_picker"),
                    onClick = { showTimePicker = false }
                ) {
                    Text(t("incomes.date_time_picker.cancel"), color = TextSecondary)
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
