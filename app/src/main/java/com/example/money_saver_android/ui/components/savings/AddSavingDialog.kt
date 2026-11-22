package com.example.money_saver_android.ui.components.savings

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.incomes.CurrencyFormatVisualTransformation
import com.example.money_saver_android.ui.components.incomes.DateTimePickerField
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun AddSavingDialog(
    amount: String,
    incomes: List<Income>,
    selectedIncomeId: Long?,
    selectedIncomeCategory: String,
    selectedIncomeSubcategory: String,
    category: String,
    subcategory: String,
    dateTime: String,
    note: String,
    errorMessage: String?,
    isEditing: Boolean = false,
    currency: Currency = defaultCurrencies.first(),
    onAmountChange: (String) -> Unit,
    onIncomeSelected: (Income) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onDateTimeChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var incomeMenuExpanded by remember { mutableStateOf(false) }

    val selectedIncome = remember(incomes, selectedIncomeId) {
        selectedIncomeId?.let { id -> incomes.find { it.id == id } }
    }
    val selectedIncomeDisplay = when {
        selectedIncome != null -> {
            val label = formatSavingLabel(selectedIncome.category, selectedIncome.subcategory).orEmpty()
            val amountFormatted = CurrencyFormatter.format(selectedIncome.amount, currency)
            "$label • $amountFormatted"
        }
        selectedIncomeCategory.isNotBlank() -> formatSavingLabel(selectedIncomeCategory, selectedIncomeSubcategory)
        else -> null
    }

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("card_add_saving"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) t("savings.edit_dialog.title") else t("savings.add_dialog.title"),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_close_saving_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("savings.add_dialog.amount_label"),
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
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    val parts = filtered.split(".")
                                    if (parts.size == 1 || parts[1].length <= 2) {
                                        onAmountChange(filtered)
                                    }
                                }
                            },
                            visualTransformation = CurrencyFormatVisualTransformation(currency),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = t("savings.add_dialog.amount_placeholder"),
                                    color = TextSecondary.copy(alpha = 0.5f),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = savingTransparentFieldColors(),
                            textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .width(200.dp)
                                .testTag("input_saving_amount")
                        )
                    }
                }

                // Field 1: Source Income Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    SavingSelectorField(
                        label = t("savings.add_dialog.income_label"),
                        displayValue = selectedIncomeDisplay,
                        placeholder = t("savings.add_dialog.select_income"),
                        testTag = "selector_saving_income",
                        onClick = { incomeMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = incomeMenuExpanded,
                        onDismissRequest = { incomeMenuExpanded = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        if (incomes.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = t("savings.add_dialog.no_incomes_available"),
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = { incomeMenuExpanded = false },
                                enabled = false,
                                modifier = Modifier.testTag("income_option_empty")
                            )
                        } else {
                            incomes.forEach { income ->
                                val label = formatSavingLabel(income.category, income.subcategory).orEmpty()
                                val amountFormatted = CurrencyFormatter.format(income.amount, currency)
                                val detail = listOf(income.dateTime, income.description)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "$label • $amountFormatted",
                                                color = TextPrimary,
                                                fontSize = 14.sp
                                            )
                                            if (detail.isNotBlank()) {
                                                Text(
                                                    text = detail,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onIncomeSelected(income)
                                        incomeMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("income_option_${income.id}")
                                )
                            }
                        }
                    }
                }

                // Saving Category Selector
                SavingSelectorField(
                    label = t("savings.add_dialog.category_label"),
                    displayValue = formatSavingLabel(category, subcategory),
                    placeholder = t("savings.add_dialog.select_category"),
                    testTag = "selector_saving_category",
                    onClick = onSelectCategoryClick
                )

                // Field 2: Registration Date & Time (Calendar + Clock)
                DateTimePickerField(
                    value = dateTime,
                    onValueChange = onDateTimeChange,
                    label = t("savings.add_dialog.date_time_label")
                )

                // Field 3: Optional Note
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("savings.add_dialog.note_label"),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        placeholder = {
                            Text(t("savings.add_dialog.note_placeholder"), color = TextSecondary)
                        },
                        colors = savingTransparentFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FieldBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .testTag("input_saving_note")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("text_saving_error")
                    )
                }

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_saving"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isEditing) t("savings.edit_dialog.save_button") else t("savings.add_dialog.save_button"),
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingSelectorField(
    label: String,
    displayValue: String?,
    placeholder: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FieldBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp)
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayValue ?: placeholder,
                    color = if (displayValue != null) TextPrimary else TextSecondary,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatSavingLabel(category: String, subcategory: String): String? {
    if (category.isBlank()) return null
    return if (subcategory.isNotBlank()) "$category • $subcategory" else category
}

@Composable
private fun savingTransparentFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
