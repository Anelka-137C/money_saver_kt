package com.example.money_saver_android.ui.components.loans

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.common.DatePickerField
import com.example.money_saver_android.ui.components.incomes.CurrencyFormatVisualTransformation
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.LoanCreationPaymentMode

@Composable
fun AddLoanDialog(
    personName: String,
    amountLent: String,
    amountToReceive: String,
    note: String,
    selectedSavingCategory: String,
    savingsBalances: List<SavingsCategoryBalance>,
    creationPaymentMode: LoanCreationPaymentMode = LoanCreationPaymentMode.NO_DATE,
    singleDueDateInput: String = "",
    todayIso: String = "",
    errorMessage: String?,
    showDiscardConfirmation: Boolean = false,
    currency: Currency = defaultCurrencies.first(),
    onPersonNameChange: (String) -> Unit,
    onAmountLentChange: (String) -> Unit,
    onAmountToReceiveChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSavingCategorySelected: (String) -> Unit,
    onCreationPaymentModeSelected: (LoanCreationPaymentMode) -> Unit = {},
    onSingleDueDateChange: (String) -> Unit = {},
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    onConfirmDiscardChanges: () -> Unit = onCloseClick,
    onDismissDiscardChanges: () -> Unit = {},
    modifier: Modifier = Modifier,
    isEditing: Boolean = false
) {
    val language = LocalI18nManager.current.currentLanguage
    var savingMenuExpanded by remember { mutableStateOf(false) }
    val selectedBalance = savingsBalances.find {
        it.categoryName.equals(selectedSavingCategory, ignoreCase = true)
    }
    val savingDisplayValue = if (selectedSavingCategory.isNotBlank()) {
        selectedBalance?.let {
            "${it.categoryName} • ${CurrencyFormatter.format(it.netBalance, currency)}"
        } ?: selectedSavingCategory
    } else {
        t("loans.add_dialog.saving_none")
    }

    LoanFormDialog(
        title = if (isEditing) t("loans.edit_dialog.title") else t("loans.add_dialog.title"),
        errorMessage = errorMessage,
        confirmButtonText = if (isEditing) t("loans.edit_dialog.save_button") else t("loans.add_dialog.save_button"),
        dialogTestTag = "card_add_loan_dialog",
        closeButtonTestTag = "btn_close_loan_dialog",
        errorTestTag = "text_loan_dialog_error",
        confirmButtonTestTag = "btn_save_loan_dialog",
        scrollTestTag = "scroll_add_loan_dialog",
        onConfirmClick = onSaveClick,
        onCloseClick = onCloseClick,
        modifier = modifier
    ) {
        LoanTextField(
            label = t("loans.add_dialog.person_label"),
            value = personName,
            placeholder = t("loans.add_dialog.person_placeholder"),
            onValueChange = onPersonNameChange,
            testTag = "input_loan_person"
        )

        LoanAmountField(
            label = t("loans.add_dialog.amount_lent_label"),
            value = amountLent,
            currency = currency,
            onValueChange = onAmountLentChange,
            testTag = "input_loan_amount_lent"
        )

        LoanAmountField(
            label = t("loans.add_dialog.amount_to_receive_label"),
            value = amountToReceive,
            currency = currency,
            onValueChange = onAmountToReceiveChange,
            testTag = "input_loan_amount_to_receive"
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = t("loans.add_dialog.saving_source_label"),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .clickable { savingMenuExpanded = true }
                        .padding(horizontal = 16.dp)
                        .testTag("selector_loan_saving_category"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = savingDisplayValue,
                        color = if (selectedSavingCategory.isNotBlank()) TextPrimary else TextSecondary,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = savingMenuExpanded,
                    onDismissRequest = { savingMenuExpanded = false },
                    modifier = Modifier.background(CardBackground)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = t("loans.add_dialog.saving_none"),
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            onSavingCategorySelected("")
                            savingMenuExpanded = false
                        },
                        modifier = Modifier.testTag("loan_saving_option_none")
                    )
                    savingsBalances.filter { it.netBalance > 0.0 }.forEach { balance ->
                        val formatted = CurrencyFormatter.format(balance.netBalance, currency)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${balance.categoryName} • $formatted",
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                onSavingCategorySelected(balance.categoryName)
                                savingMenuExpanded = false
                            },
                            modifier = Modifier.testTag("loan_saving_option_${balance.categoryName}")
                        )
                    }
                }
            }
        }

        LoanTextField(
            label = t("loans.add_dialog.note_label"),
            value = note,
            placeholder = t("loans.add_dialog.note_placeholder"),
            onValueChange = onNoteChange,
            testTag = "input_loan_note"
        )

        if (!isEditing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = t("loans.add_dialog.payment_plan_label"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                LoanCreationModeOption(
                    label = t("loans.add_dialog.payment_mode_none"),
                    selected = creationPaymentMode == LoanCreationPaymentMode.NO_DATE,
                    onClick = { onCreationPaymentModeSelected(LoanCreationPaymentMode.NO_DATE) },
                    testTag = "option_loan_payment_none"
                )
                LoanCreationModeOption(
                    label = t("loans.add_dialog.payment_mode_single"),
                    selected = creationPaymentMode == LoanCreationPaymentMode.SINGLE_DATE,
                    onClick = { onCreationPaymentModeSelected(LoanCreationPaymentMode.SINGLE_DATE) },
                    testTag = "option_loan_payment_single"
                )
                if (creationPaymentMode == LoanCreationPaymentMode.SINGLE_DATE) {
                    DatePickerField(
                        value = singleDueDateInput,
                        onValueChange = onSingleDueDateChange,
                        label = t("loans.add_dialog.single_due_date_label"),
                        minDateIso = todayIso.takeIf { it.isNotBlank() },
                        displayFormatter = { iso -> formatLoanShortDate(iso, language) },
                        cancelText = t("loans.schedule.cancel_button"),
                        fieldTestTag = "input_loan_single_due_date"
                    )
                }
                LoanCreationModeOption(
                    label = t("loans.add_dialog.payment_mode_installments"),
                    selected = creationPaymentMode == LoanCreationPaymentMode.INSTALLMENTS,
                    onClick = { onCreationPaymentModeSelected(LoanCreationPaymentMode.INSTALLMENTS) },
                    testTag = "option_loan_payment_installments"
                )
            }
        }
    }

    if (isEditing && showDiscardConfirmation) {
        DiscardChangesDialog(
            title = t("loans.edit_dialog.discard_dialog_title"),
            message = t("loans.edit_dialog.discard_dialog_message"),
            cancelText = t("loans.schedule.discard_cancel"),
            confirmText = t("loans.schedule.discard_confirm"),
            dialogTestTag = "dialog_discard_edit_loan",
            cancelButtonTestTag = "btn_cancel_discard_edit_loan",
            confirmButtonTestTag = "btn_confirm_discard_edit_loan",
            onConfirmDiscard = onConfirmDiscardChanges,
            onDismiss = onDismissDiscardChanges
        )
    }
}

@Composable
private fun LoanCreationModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GreenAccent.copy(alpha = 0.14f) else FieldBackground)
            .border(
                width = 1.dp,
                color = if (selected) GreenAccent else BorderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (selected) GreenAccent else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun LoanTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(text = placeholder, color = TextSecondary) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = FieldBackground,
                unfocusedContainerColor = FieldBackground,
                focusedBorderColor = GreenAccent,
                unfocusedBorderColor = BorderColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

@Composable
internal fun LoanAmountField(
    label: String,
    value: String,
    currency: Currency,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    val allowsDecimals = currency.formatPattern.contains(".")
    val maxDecimals = if (allowsDecimals) currency.formatPattern.substringAfter(".").length else 0
    val placeholderText = if (allowsDecimals) "0.00" else "0"

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FieldBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currency.symbol,
                color = GreenAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(filterDecimalInput(newValue, allowsDecimals, maxDecimals))
                },
                visualTransformation = CurrencyFormatVisualTransformation(currency),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                placeholder = {
                    Text(text = placeholderText, color = TextSecondary, fontSize = 16.sp)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}

internal fun filterDecimalInput(raw: String, allowsDecimals: Boolean, maxDecimals: Int): String {
    return buildString {
        var hasDot = false
        var decCount = 0
        for (char in raw) {
            if (char in '0'..'9') {
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
}
