package com.example.money_saver_android.ui.components.loans

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.parseLoanAmount
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.incomes.CurrencyFormatVisualTransformation
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.LoanInstallmentDraft

@Composable
fun AddLoanDialog(
    personName: String,
    amountLent: String,
    amountToReceive: String,
    note: String,
    selectedSavingCategory: String,
    savingsBalances: List<SavingsCategoryBalance>,
    errorMessage: String?,
    currency: Currency = defaultCurrencies.first(),
    onPersonNameChange: (String) -> Unit,
    onAmountLentChange: (String) -> Unit,
    onAmountToReceiveChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSavingCategorySelected: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    draftInstallments: List<LoanInstallmentDraft> = emptyList(),
    draftInstallmentDate: String = "",
    draftInstallmentAmount: String = "",
    todayIso: String = "",
    onDraftInstallmentDateChange: (String) -> Unit = {},
    onDraftInstallmentAmountChange: (String) -> Unit = {},
    onAddInstallmentClick: () -> Unit = {},
    onRemoveInstallmentClick: (String) -> Unit = {}
) {
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

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 16.dp)
                .heightIn(max = maxDialogHeight)
                .testTag("card_add_loan_dialog"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("loans.add_dialog.title"),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.size(28.dp).testTag("btn_close_loan_dialog")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag("scroll_add_loan_dialog"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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

                LoanInstallmentsSection(
                    installments = draftInstallments,
                    dateInput = draftInstallmentDate,
                    amountInput = draftInstallmentAmount,
                    todayIso = todayIso,
                    targetAmountToReceive = parseLoanAmount(amountToReceive),
                    currency = currency,
                    onDateChange = onDraftInstallmentDateChange,
                    onAmountChange = onDraftInstallmentAmountChange,
                    onAddClick = onAddInstallmentClick,
                    onRemoveClick = onRemoveInstallmentClick,
                    dateFieldTestTag = "input_loan_installment_date",
                    amountFieldTestTag = "input_loan_installment_amount",
                    addButtonTestTag = "btn_add_loan_installment",
                    removeButtonTagPrefix = "btn_remove_loan_installment",
                    itemTagPrefix = "loan_draft_installment_item",
                    sectionTitle = t("loans.schedule.section_title")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("text_loan_dialog_error")
                    )
                }

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_save_loan_dialog"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = t("loans.add_dialog.save_button"),
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                }
            }
        }
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
