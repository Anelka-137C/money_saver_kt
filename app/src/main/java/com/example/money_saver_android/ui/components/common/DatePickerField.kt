package com.example.money_saver_android.ui.components.common

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reusable date-only picker field (ISO `yyyy-MM-dd` value) without time selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minDateIso: String? = null,
    displayFormatter: (String) -> String = { it },
    confirmText: String = "OK",
    cancelText: String = "Cancelar",
    fieldTestTag: String = "field_date_picker"
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val minDateMillis = remember(minDateIso) { parseIsoToUtcMillis(minDateIso) }
    val minYear = remember(minDateMillis) {
        minDateMillis?.let { millis ->
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }.get(Calendar.YEAR)
        }
    }

    val initialSelectedMillis = remember(value, minDateMillis) {
        val parsed = parseIsoToUtcMillis(value)
        when {
            parsed != null && (minDateMillis == null || parsed >= minDateMillis) -> parsed
            minDateMillis != null -> minDateMillis
            else -> currentLocalDateUtcMillis()
        }
    }

    val selectableDates = remember(minDateMillis, minYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return minDateMillis == null || utcTimeMillis >= minDateMillis
            }

            override fun isSelectableYear(year: Int): Boolean {
                return minYear == null || year >= minYear
            }
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedMillis,
        selectableDates = selectableDates
    )

    val displayText = if (value.isNotBlank()) displayFormatter(value) else ""

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FieldBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true }
                .testTag(fieldTestTag)
                .padding(horizontal = 14.dp, vertical = 14.dp)
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
                        tint = GreenAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = displayText.ifEmpty { label },
                        color = if (displayText.isNotEmpty()) TextPrimary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            modifier = Modifier.testTag("date_only_picker_dialog"),
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_confirm_date_only_picker"),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onValueChange(formatUtcMillisToIso(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(confirmText, color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cancel_date_only_picker"),
                    onClick = { showDatePicker = false }
                ) {
                    Text(cancelText, color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun parseIsoToUtcMillis(isoDate: String?): Long? {
    val trimmed = isoDate?.trim { it.isWhitespace() }.orEmpty()
    if (trimmed.isEmpty()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val pos = ParsePosition(0)
    val parsed = parser.parse(trimmed, pos) ?: return null
    if (pos.index != trimmed.length || pos.errorIndex != -1) return null
    return parsed.time
}

private fun formatUtcMillisToIso(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(millis))
}

private fun currentLocalDateUtcMillis(): Long {
    val localIso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    return parseIsoToUtcMillis(localIso) ?: System.currentTimeMillis()
}
