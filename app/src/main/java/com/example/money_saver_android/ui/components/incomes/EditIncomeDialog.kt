package com.example.money_saver_android.ui.components.incomes

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.EditIncomeState

@Composable
fun EditIncomeDialog(
    editState: EditIncomeState,
    onAmountChange: (String) -> Unit,
    onDateTimeChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("dialog_edit_income"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("incomes.edit_dialog.title"),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("btn_close_edit_income")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Amount field centered
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("incomes.edit_dialog.amount_label"),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currency.symbol,
                            color = GreenAccent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = editState.amount,
                            onValueChange = { newValue ->
                                val allowsDecimals = currency.formatPattern.contains(".")
                                val maxDecimals = if (allowsDecimals) currency.formatPattern.substringAfter(".").length else 0
                                val filtered = buildString {
                                    var hasDot = false
                                    var decCount = 0
                                    for (char in newValue.replace(",", "")) {
                                        if (char.isDigit()) {
                                            if (hasDot) {
                                                if (decCount < maxDecimals) {
                                                    append(char)
                                                    decCount++
                                                }
                                            } else {
                                                append(char)
                                            }
                                        } else if ((char == '.' || char == ',') && allowsDecimals && !hasDot) {
                                            append('.')
                                            hasDot = true
                                        }
                                    }
                                }
                                onAmountChange(filtered)
                            },
                            visualTransformation = CurrencyFormatVisualTransformation(currency),
                            modifier = Modifier
                                .width(220.dp)
                                .testTag("input_edit_income_amount"),
                            placeholder = {
                                val placeholderText = if (currency.formatPattern.contains(".")) {
                                    t("incomes.edit_dialog.amount_placeholder")
                                } else {
                                    "0"
                                }
                                Text(
                                    placeholderText,
                                    color = TextSecondary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                // Date / Time picker
                DateTimePickerField(
                    value = editState.dateTime,
                    onValueChange = onDateTimeChange,
                    label = t("incomes.edit_dialog.date_time_label")
                )

                // Category selector field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelectCategoryClick() }
                        .testTag("btn_edit_income_select_category")
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏷️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = t("incomes.edit_dialog.category_label"),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val categoryText = if (editState.category.isNotBlank()) {
                                    if (editState.subcategory.isNotBlank()) {
                                        "${editState.category} / ${editState.subcategory}"
                                    } else {
                                        editState.category
                                    }
                                } else {
                                    t("incomes.edit_dialog.select_category")
                                }
                                Text(
                                    text = categoryText,
                                    color = if (editState.category.isNotBlank()) TextPrimary else GreenAccent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(">", color = TextSecondary, fontSize = 16.sp)
                    }
                }

                // Note (optional)
                OutlinedTextField(
                    value = editState.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_income_description"),
                    label = { Text(t("incomes.edit_dialog.description_label"), color = TextSecondary) },
                    placeholder = { Text(t("incomes.edit_dialog.description_placeholder"), color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = FieldBackground,
                        unfocusedContainerColor = FieldBackground,
                        focusedBorderColor = GreenAccent,
                        unfocusedBorderColor = BorderColor
                    )
                )

                // Error message if any
                editState.errorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("text_edit_income_error")
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_edit_income"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text(
                            text = t("incomes.edit_dialog.cancel_button"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_confirm_edit_income"),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = t("incomes.edit_dialog.save_button"),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
