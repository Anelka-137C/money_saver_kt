package com.example.money_saver_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.components.period.DeletePeriodConfirmationDialog
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.PeriodViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodView(
    periodViewModel: PeriodViewModel,
    i18nManager: I18nManager,
    modifier: Modifier = Modifier
) {
    val state by periodViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<FinancialPeriod?>(null) }
    var periodToDelete by remember { mutableStateOf<FinancialPeriod?>(null) }

    val isoFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val todayMillis = remember {
        try {
            isoFormatter.parse(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))?.time
        } catch (_: Exception) { null }
    }

    val initialStartMillis = remember(state.startDateInput) {
        try {
            if (state.startDateInput.isNotBlank()) {
                isoFormatter.parse(state.startDateInput)?.time
            } else {
                todayMillis
            }
        } catch (_: Exception) { todayMillis }
    }

    val initialEndMillis = remember(state.endDateInput) {
        try {
            if (state.endDateInput.isNotBlank()) {
                isoFormatter.parse(state.endDateInput)?.time
            } else {
                todayMillis
            }
        } catch (_: Exception) { todayMillis }
    }

    val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialStartMillis)
    val endDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEndMillis)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("period_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = i18nManager.t("period.title"),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        if (state.activePeriod != null) {
            var activeMenuExpanded by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_period_info")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = i18nManager.t("period.active_period_title"),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${state.activePeriod?.startDate} - ${state.activePeriod?.endDate}",
                            color = GreenAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { activeMenuExpanded = true },
                            modifier = Modifier.testTag("btn_active_period_options")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = activeMenuExpanded,
                            onDismissRequest = { activeMenuExpanded = false },
                            modifier = Modifier
                                .background(CardBackground)
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text(i18nManager.t("period.menu_edit"), color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                onClick = {
                                    activeMenuExpanded = false
                                    editingPeriod = state.activePeriod
                                },
                                modifier = Modifier.testTag("menu_option_edit")
                            )
                            DropdownMenuItem(
                                text = { Text(i18nManager.t("period.menu_delete"), color = ErrorRed) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = ErrorRed
                                    )
                                },
                                onClick = {
                                    activeMenuExpanded = false
                                    periodToDelete = state.activePeriod
                                },
                                modifier = Modifier.testTag("menu_option_delete")
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.startDateInput,
            onValueChange = { periodViewModel.onStartDateChanged(it) },
            label = { Text(i18nManager.t("period.start_date")) },
            trailingIcon = {
                IconButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.testTag("btn_pick_start_date")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = i18nManager.t("period.start_date"),
                        tint = GreenAccent
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_start_date"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = GreenAccent,
                unfocusedBorderColor = TextSecondary,
                focusedLabelColor = GreenAccent,
                unfocusedLabelColor = TextSecondary
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = state.endDateInput,
            onValueChange = { periodViewModel.onEndDateChanged(it) },
            label = { Text(i18nManager.t("period.end_date")) },
            trailingIcon = {
                IconButton(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.testTag("btn_pick_end_date")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = i18nManager.t("period.end_date"),
                        tint = GreenAccent
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_end_date"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = GreenAccent,
                unfocusedBorderColor = TextSecondary,
                focusedLabelColor = GreenAccent,
                unfocusedLabelColor = TextSecondary
            ),
            singleLine = true
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.testTag("period_error_message")
            )
        }

        Button(
            onClick = { periodViewModel.savePeriod() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_save_period"),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenAccent,
                contentColor = DarkBackground
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = i18nManager.t("period.save_button"),
                fontWeight = FontWeight.Bold
            )
        }

        if (state.allPeriods.isNotEmpty()) {
            Text(
                text = i18nManager.t("period.history_title"),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("period_history_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.allPeriods.forEach { period ->
                    var itemMenuExpanded by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("period_card_${period.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${period.startDate} / ${period.endDate}",
                                    color = if (period.id == state.activePeriod?.id) GreenAccent else TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { itemMenuExpanded = true },
                                    modifier = Modifier.testTag("btn_period_options_${period.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = TextSecondary
                                    )
                                }

                                DropdownMenu(
                                    expanded = itemMenuExpanded,
                                    onDismissRequest = { itemMenuExpanded = false },
                                    modifier = Modifier
                                        .background(CardBackground)
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(i18nManager.t("period.menu_edit"), color = TextPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        },
                                        onClick = {
                                            itemMenuExpanded = false
                                            editingPeriod = period
                                        },
                                        modifier = Modifier.testTag("menu_option_edit")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(i18nManager.t("period.menu_delete"), color = ErrorRed) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = ErrorRed
                                            )
                                        },
                                        onClick = {
                                            itemMenuExpanded = false
                                            periodToDelete = period
                                        },
                                        modifier = Modifier.testTag("menu_option_delete")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            modifier = Modifier.testTag("date_picker_dialog"),
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_confirm_date_picker"),
                    onClick = {
                        startDatePickerState.selectedDateMillis?.let { millis ->
                            periodViewModel.onStartDateChanged(isoFormatter.format(Date(millis)))
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK", color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cancel_date_picker"),
                    onClick = { showStartDatePicker = false }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        ) {
            DatePicker(
                state = startDatePickerState,
                modifier = Modifier.testTag("start_date_picker")
            )
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            modifier = Modifier.testTag("date_picker_dialog"),
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_confirm_date_picker"),
                    onClick = {
                        endDatePickerState.selectedDateMillis?.let { millis ->
                            periodViewModel.onEndDateChanged(isoFormatter.format(Date(millis)))
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text("OK", color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cancel_date_picker"),
                    onClick = { showEndDatePicker = false }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        ) {
            DatePicker(
                state = endDatePickerState,
                modifier = Modifier.testTag("end_date_picker")
            )
        }
    }

    if (editingPeriod != null) {
        val period = editingPeriod!!
        var editStartDate by remember(period) { mutableStateOf(period.startDate) }
        var editEndDate by remember(period) { mutableStateOf(period.endDate) }
        var editError by remember { mutableStateOf<String?>(null) }

        var showEditStartDatePicker by remember { mutableStateOf(false) }
        var showEditEndDatePicker by remember { mutableStateOf(false) }

        val initialEditStartMillis = remember(editStartDate) {
            try {
                if (editStartDate.isNotBlank()) isoFormatter.parse(editStartDate)?.time else todayMillis
            } catch (_: Exception) { todayMillis }
        }
        val initialEditEndMillis = remember(editEndDate) {
            try {
                if (editEndDate.isNotBlank()) isoFormatter.parse(editEndDate)?.time else todayMillis
            } catch (_: Exception) { todayMillis }
        }

        val editStartDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEditStartMillis)
        val editEndDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEditEndMillis)

        AlertDialog(
            onDismissRequest = { editingPeriod = null },
            modifier = Modifier.testTag("edit_period_dialog"),
            containerColor = CardBackground,
            title = {
                Text(
                    text = i18nManager.t("period.edit_dialog_title"),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editStartDate,
                        onValueChange = { editStartDate = it; editError = null },
                        label = { Text(i18nManager.t("period.start_date")) },
                        trailingIcon = {
                            IconButton(
                                onClick = { showEditStartDatePicker = true },
                                modifier = Modifier.testTag("btn_pick_edit_start_date")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = i18nManager.t("period.start_date"),
                                    tint = GreenAccent
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_start_date"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = GreenAccent,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = GreenAccent,
                            unfocusedLabelColor = TextSecondary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editEndDate,
                        onValueChange = { editEndDate = it; editError = null },
                        label = { Text(i18nManager.t("period.end_date")) },
                        trailingIcon = {
                            IconButton(
                                onClick = { showEditEndDatePicker = true },
                                modifier = Modifier.testTag("btn_pick_edit_end_date")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = i18nManager.t("period.end_date"),
                                    tint = GreenAccent
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_end_date"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = GreenAccent,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = GreenAccent,
                            unfocusedLabelColor = TextSecondary
                        ),
                        singleLine = true
                    )

                    if (editError != null) {
                        Text(
                            text = editError ?: "",
                            color = Color.Red,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editEndDate < editStartDate) {
                            editError = "La fecha de fin no puede ser anterior a la de inicio"
                            return@TextButton
                        }
                        periodViewModel.updatePeriod(
                            period.copy(startDate = editStartDate, endDate = editEndDate)
                        )
                        editingPeriod = null
                    },
                    modifier = Modifier.testTag("btn_save_edit_period")
                ) {
                    Text(i18nManager.t("period.edit_dialog_save"), color = GreenAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingPeriod = null },
                    modifier = Modifier.testTag("btn_cancel_edit_period")
                ) {
                    Text(i18nManager.t("period.edit_dialog_cancel"), color = TextSecondary)
                }
            }
        )

        if (showEditStartDatePicker) {
            DatePickerDialog(
                modifier = Modifier.testTag("edit_start_date_picker_dialog"),
                onDismissRequest = { showEditStartDatePicker = false },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("btn_confirm_edit_start_date_picker"),
                        onClick = {
                            editStartDatePickerState.selectedDateMillis?.let { millis ->
                                editStartDate = isoFormatter.format(Date(millis))
                                editError = null
                            }
                            showEditStartDatePicker = false
                        }
                    ) {
                        Text("OK", color = GreenAccent)
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("btn_cancel_edit_start_date_picker"),
                        onClick = { showEditStartDatePicker = false }
                    ) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            ) {
                DatePicker(
                    state = editStartDatePickerState,
                    modifier = Modifier.testTag("edit_start_date_picker")
                )
            }
        }

        if (showEditEndDatePicker) {
            DatePickerDialog(
                modifier = Modifier.testTag("edit_end_date_picker_dialog"),
                onDismissRequest = { showEditEndDatePicker = false },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("btn_confirm_edit_end_date_picker"),
                        onClick = {
                            editEndDatePickerState.selectedDateMillis?.let { millis ->
                                editEndDate = isoFormatter.format(Date(millis))
                                editError = null
                            }
                            showEditEndDatePicker = false
                        }
                    ) {
                        Text("OK", color = GreenAccent)
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("btn_cancel_edit_end_date_picker"),
                        onClick = { showEditEndDatePicker = false }
                    ) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            ) {
                DatePicker(
                    state = editEndDatePickerState,
                    modifier = Modifier.testTag("edit_end_date_picker")
                )
            }
        }
    }

    if (periodToDelete != null) {
        DeletePeriodConfirmationDialog(
            period = periodToDelete!!,
            onConfirm = {
                val id = periodToDelete!!.id
                periodViewModel.deletePeriod(id)
                periodToDelete = null
            },
            onDismiss = {
                periodToDelete = null
            },
            i18nManager = i18nManager
        )
    }
}
