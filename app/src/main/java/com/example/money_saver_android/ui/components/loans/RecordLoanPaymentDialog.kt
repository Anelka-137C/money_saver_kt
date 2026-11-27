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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.domain.usecase.hasFutureLoanInstallments
import com.example.money_saver_android.domain.usecase.parseLoanAmount
import com.example.money_saver_android.domain.usecase.previewLoanInstallmentsAfterPayment
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.common.DatePickerField
import com.example.money_saver_android.ui.components.incomes.DateTimePickerField
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.PartialScheduleAction
import java.math.BigDecimal

@Composable
fun RecordLoanPaymentDialog(
    loan: Loan,
    amountInput: String,
    dateTimeInput: String,
    selectedSavingCategory: String,
    savingCategories: List<Category>,
    savingsBalances: List<SavingsCategoryBalance>,
    selectedTargetInstallmentId: Long? = null,
    defineNewDateOnNoSchedule: Boolean = false,
    newDueDateInput: String,
    partialScheduleAction: PartialScheduleAction,
    extraInstallmentDateInput: String,
    extraInstallmentAmountInput: String,
    adjustedNextAmountInput: String,
    adjustedRemainderDateInput: String,
    todayIso: String,
    errorMessage: String?,
    currency: Currency,
    onAmountChange: (String) -> Unit,
    onFullAmountClick: () -> Unit,
    onDateTimeChange: (String) -> Unit,
    onSavingCategorySelected: (String) -> Unit,
    onTargetInstallmentSelected: (Long) -> Unit = {},
    onDefineNewDateToggle: (Boolean) -> Unit = {},
    onNewDueDateChange: (String) -> Unit,
    onPartialScheduleActionSelected: (PartialScheduleAction) -> Unit,
    onExtraInstallmentDateChange: (String) -> Unit,
    onExtraInstallmentAmountChange: (String) -> Unit,
    onAdjustedNextAmountChange: (String) -> Unit,
    onAdjustedRemainderDateChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false
) {
    val language = LocalI18nManager.current.currentLanguage
    var savingMenuExpanded by remember { mutableStateOf(false) }

    val unpaidInstallments = loan.installments
        .filter { !it.isPaid }
        .sortedWith(compareBy({ it.dueDate.trim { ch -> ch.isWhitespace() } }, { it.id }))
    val effectiveTargetId = selectedTargetInstallmentId?.takeIf { id -> unpaidInstallments.any { it.id == id } }
        ?: unpaidInstallments.firstOrNull()?.id
    val selectedInstallment = effectiveTargetId?.let { id -> unpaidInstallments.find { it.id == id } }

    val parsedAmount = parseLoanAmount(amountInput)
    val isValidPositive = parsedAmount != null && parsedAmount.isFinite() && parsedAmount > 0.0
    val pendingBefore = BigDecimal.valueOf(loan.pendingAmount)
    val remainingAfter = if (isValidPositive) {
        pendingBefore.subtract(BigDecimal.valueOf(parsedAmount!!)).max(BigDecimal.ZERO)
    } else {
        pendingBefore
    }
    val isPartialPayment = isValidPositive && remainingAfter > BigDecimal.ZERO
    val willSettleLoan = isValidPositive && BigDecimal.valueOf(parsedAmount!!) <= pendingBefore && remainingAfter <= BigDecimal.ZERO

    val afterApplying = if (isPartialPayment) {
        previewLoanInstallmentsAfterPayment(loan, parsedAmount!!, effectiveTargetId)
    } else {
        emptyList()
    }
    val hasFutureInstallments = isPartialPayment && hasFutureLoanInstallments(afterApplying, todayIso)
    val nextUnpaidAfter = afterApplying.firstOrNull { !it.isPaid }
    val remainingUnpaidCountAfter = afterApplying.count { !it.isPaid }

    val subtitleText = "${loan.personName} • ${t("loans.card.pending")}: ${CurrencyFormatter.format(loan.pendingAmount, currency)}"

    LoanFormDialog(
        title = if (isEditing) t("loans.payment_dialog.edit_title") else t("loans.payment_dialog.title"),
        subtitle = subtitleText,
        errorMessage = errorMessage,
        confirmButtonText = if (isEditing) t("loans.edit_dialog.save_button") else t("loans.payment_dialog.save_button"),
        dialogTestTag = "card_record_loan_payment_dialog",
        closeButtonTestTag = "btn_close_payment_dialog",
        errorTestTag = "text_payment_dialog_error",
        confirmButtonTestTag = "btn_save_payment_dialog",
        scrollTestTag = "scroll_record_loan_payment_dialog",
        onConfirmClick = onSaveClick,
        onCloseClick = onCloseClick,
        modifier = modifier
    ) {
        if (unpaidInstallments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_payment_target_installment"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = t("loans.payment_dialog.target_installment_label"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                unpaidInstallments.forEachIndexed { index, inst ->
                    val isSelected = inst.id == effectiveTargetId
                    val dateFmt = formatLoanShortDate(inst.dueDate, language)
                    val titlePrefix = if (unpaidInstallments.size == 1) {
                        t("loans.payment_dialog.single_scheduled_prefix")
                    } else {
                        val template = t("loans.payment_dialog.installment_number_prefix")
                        runCatching { String.format(template, index + 1) }.getOrDefault("#${index + 1}")
                    }
                    val instRemFmt = CurrencyFormatter.format(inst.remainingAmount, currency)
                    val instPaidFmt = CurrencyFormatter.format(inst.paymentAmount, currency)
                    val instExpFmt = CurrencyFormatter.format(inst.expectedAmount, currency)
                    val detailTemplate = t("loans.payment_dialog.installment_detail")
                    val detailText = runCatching {
                        String.format(detailTemplate, instRemFmt, instPaidFmt, instExpFmt)
                    }.getOrDefault("$instRemFmt ($instPaidFmt / $instExpFmt)")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) GreenAccent.copy(alpha = 0.14f) else FieldBackground)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) GreenAccent else BorderColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onTargetInstallmentSelected(inst.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("payment_installment_option_${inst.id}")
                    ) {
                        Text(
                            text = "$titlePrefix • $dateFmt",
                            color = if (isSelected) GreenAccent else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = detailText,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            LoanAmountField(
                label = t("loans.payment_dialog.amount_label"),
                value = amountInput,
                currency = currency,
                onValueChange = onAmountChange,
                testTag = "input_payment_amount"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GreenAccent.copy(alpha = 0.14f))
                        .border(1.dp, GreenAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .clickable { onFullAmountClick() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("btn_payment_full_amount")
                ) {
                    Text(
                        text = t("loans.payment_dialog.pay_full_button"),
                        color = GreenAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (selectedInstallment != null && isPartialPayment &&
            BigDecimal.valueOf(parsedAmount!!) > BigDecimal.valueOf(selectedInstallment.remainingAmount)
        ) {
            Text(
                text = t("loans.payment_dialog.overflow_installment_hint"),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.testTag("text_payment_overflow_hint")
            )
        }

        DateTimePickerField(
            value = dateTimeInput,
            onValueChange = onDateTimeChange,
            label = t("loans.payment_dialog.datetime_label")
        )

        if (loan.savingCategory.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t("loans.payment_dialog.saving_destination_label"),
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
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("text_payment_auto_saving_category")
                ) {
                    Text(
                        text = "${loan.savingCategory} • ${t("loans.payment_dialog.auto_credited_tag")}",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            val selectedBalance = savingsBalances.find {
                it.categoryName.equals(selectedSavingCategory, ignoreCase = true)
            }?.netBalance ?: 0.0
            val savingDisplay = if (selectedSavingCategory.isNotBlank()) {
                "$selectedSavingCategory • ${CurrencyFormatter.format(selectedBalance, currency)}"
            } else {
                t("loans.payment_dialog.saving_destination_placeholder")
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t("loans.payment_dialog.saving_destination_label"),
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
                            .testTag("selector_payment_saving_category"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = savingDisplay,
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
                        savingCategories.forEach { category ->
                            val balance = savingsBalances.find {
                                it.categoryName.equals(category.name, ignoreCase = true)
                            }?.netBalance ?: 0.0
                            val formatted = CurrencyFormatter.format(balance, currency)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${category.name} • $formatted",
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    onSavingCategorySelected(category.name)
                                    savingMenuExpanded = false
                                },
                                modifier = Modifier.testTag("payment_saving_option_${category.name}")
                            )
                        }
                    }
                }
            }
        }

        if (willSettleLoan) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GreenAccent.copy(alpha = 0.12f))
                    .border(1.dp, GreenAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("text_payment_will_settle")
            ) {
                Text(
                    text = t("loans.payment_dialog.will_settle_hint"),
                    color = GreenAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (isPartialPayment) {
            val remainingTemplate = t("loans.payment_dialog.remaining_balance_hint")
            val formattedRemaining = CurrencyFormatter.format(remainingAfter.toDouble(), currency)
            Text(
                text = runCatching { String.format(remainingTemplate, formattedRemaining) }
                    .getOrDefault("Saldo pendiente después del pago: $formattedRemaining"),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("text_payment_remaining_after")
            )

            if (!hasFutureInstallments) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .clickable { onDefineNewDateToggle(!defineNewDateOnNoSchedule) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("toggle_payment_new_due_date"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = defineNewDateOnNoSchedule,
                        onCheckedChange = onDefineNewDateToggle,
                        colors = CheckboxDefaults.colors(
                            checkedColor = GreenAccent,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.Black
                        )
                    )
                    Text(
                        text = t("loans.payment_dialog.optional_new_due_date_toggle"),
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
                if (defineNewDateOnNoSchedule) {
                    DatePickerField(
                        value = newDueDateInput,
                        onValueChange = onNewDueDateChange,
                        label = t("loans.payment_dialog.new_due_date_label"),
                        minDateIso = todayIso.takeIf { it.isNotBlank() },
                        displayFormatter = { iso -> formatLoanShortDate(iso, language) },
                        cancelText = t("loans.schedule.cancel_button"),
                        fieldTestTag = "input_payment_new_due_date"
                    )
                }
            } else {
                Text(
                    text = t("loans.payment_dialog.partial_schedule_title"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PartialScheduleOptionRow(
                        label = t("loans.payment_dialog.schedule_keep"),
                        selected = partialScheduleAction == PartialScheduleAction.KEEP_CURRENT,
                        onClick = { onPartialScheduleActionSelected(PartialScheduleAction.KEEP_CURRENT) },
                        testTag = "option_schedule_keep"
                    )
                    PartialScheduleOptionRow(
                        label = t("loans.payment_dialog.schedule_add"),
                        selected = partialScheduleAction == PartialScheduleAction.ADD_INSTALLMENT,
                        onClick = { onPartialScheduleActionSelected(PartialScheduleAction.ADD_INSTALLMENT) },
                        testTag = "option_schedule_add"
                    )
                    PartialScheduleOptionRow(
                        label = t("loans.payment_dialog.schedule_adjust"),
                        selected = partialScheduleAction == PartialScheduleAction.ADJUST_NEXT_INSTALLMENT,
                        onClick = { onPartialScheduleActionSelected(PartialScheduleAction.ADJUST_NEXT_INSTALLMENT) },
                        testTag = "option_schedule_adjust"
                    )
                }

                when (partialScheduleAction) {
                    PartialScheduleAction.KEEP_CURRENT -> Unit
                    PartialScheduleAction.ADD_INSTALLMENT -> {
                        DatePickerField(
                            value = extraInstallmentDateInput,
                            onValueChange = onExtraInstallmentDateChange,
                            label = t("loans.schedule.date_label"),
                            minDateIso = todayIso.takeIf { it.isNotBlank() },
                            displayFormatter = { iso -> formatLoanShortDate(iso, language) },
                            cancelText = t("loans.schedule.cancel_button"),
                            fieldTestTag = "input_payment_extra_date"
                        )
                        LoanAmountField(
                            label = t("loans.schedule.amount_label"),
                            value = extraInstallmentAmountInput,
                            currency = currency,
                            onValueChange = onExtraInstallmentAmountChange,
                            testTag = "input_payment_extra_amount"
                        )
                    }
                    PartialScheduleAction.ADJUST_NEXT_INSTALLMENT -> {
                        if (nextUnpaidAfter != null) {
                            val nextDateFmt = formatLoanShortDate(nextUnpaidAfter.dueDate, language)
                            val labelTemplate = t("loans.payment_dialog.next_installment_label")
                            val fieldLabel = runCatching { String.format(labelTemplate, nextDateFmt) }
                                .getOrDefault("${t("loans.schedule.amount_label")} ($nextDateFmt)")
                            LoanAmountField(
                                label = fieldLabel,
                                value = adjustedNextAmountInput,
                                currency = currency,
                                onValueChange = onAdjustedNextAmountChange,
                                testTag = "input_payment_adjusted_next_amount"
                            )
                            val parsedAdjusted = parseLoanAmount(adjustedNextAmountInput)
                            val needsRemainderDate = remainingUnpaidCountAfter == 1 &&
                                parsedAdjusted != null &&
                                parsedAdjusted.isFinite() &&
                                parsedAdjusted > 0.0 &&
                                BigDecimal.valueOf(parsedAdjusted) < remainingAfter
                            if (needsRemainderDate) {
                                DatePickerField(
                                    value = adjustedRemainderDateInput,
                                    onValueChange = onAdjustedRemainderDateChange,
                                    label = t("loans.payment_dialog.remainder_due_date_label"),
                                    minDateIso = todayIso.takeIf { it.isNotBlank() },
                                    displayFormatter = { iso -> formatLoanShortDate(iso, language) },
                                    cancelText = t("loans.schedule.cancel_button"),
                                    fieldTestTag = "input_payment_adjusted_remainder_date"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialScheduleOptionRow(
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
