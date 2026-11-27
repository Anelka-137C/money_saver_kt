package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.domain.usecase.parseLoanAmount
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.common.DatePickerField
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.LoanInstallmentDraft
import java.math.BigDecimal

@Composable
internal fun LoanInstallmentsSection(
    installments: List<LoanInstallmentDraft>,
    dateInput: String,
    amountInput: String,
    todayIso: String,
    targetAmountToReceive: Double?,
    currency: Currency,
    onDateChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    dateFieldTestTag: String,
    amountFieldTestTag: String,
    addButtonTestTag: String,
    removeButtonTagPrefix: String,
    itemTagPrefix: String,
    editingDraftId: String? = null,
    onEditClick: (String) -> Unit = {},
    onCancelEditClick: () -> Unit = {},
    optionsButtonTagPrefix: String = "btn_options_schedule_installment",
    editButtonTagPrefix: String = "btn_edit_schedule_installment",
    sectionTitle: String? = null,
    showEmptyState: Boolean = false
) {
    val language = LocalI18nManager.current.currentLanguage
    val scheduledTotal = installments.fold(BigDecimal.ZERO) { acc, item ->
        val parsed = parseLoanAmount(item.expectedAmountInput)
        if (parsed != null && parsed.isFinite() && parsed > 0.0) {
            val paid = if (item.paymentAmount.isFinite() && item.paymentAmount > 0.0) {
                BigDecimal.valueOf(item.paymentAmount)
            } else {
                BigDecimal.ZERO
            }
            val rem = BigDecimal.valueOf(parsed).subtract(paid).max(BigDecimal.ZERO)
            acc.add(rem)
        } else {
            acc
        }
    }
    val isEditingInstallment = editingDraftId != null && installments.any { it.draftId == editingDraftId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!sectionTitle.isNullOrBlank() || (installments.isNotEmpty() && targetAmountToReceive != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!sectionTitle.isNullOrBlank()) {
                    Text(
                        text = sectionTitle,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (targetAmountToReceive != null && targetAmountToReceive.isFinite() && targetAmountToReceive > 0.0) {
                    val scheduledFmt = CurrencyFormatter.format(scheduledTotal, currency)
                    val targetFmt = CurrencyFormatter.format(targetAmountToReceive, currency)
                    val sumTemplate = t("loans.schedule.sum_label")
                    val sumText = runCatching {
                        String.format(sumTemplate, scheduledFmt, targetFmt)
                    }.getOrDefault("$scheduledFmt / $targetFmt")
                    val matchesTarget = scheduledTotal.compareTo(BigDecimal.valueOf(targetAmountToReceive)) == 0
                    Text(
                        text = sumText,
                        color = if (installments.isNotEmpty() && matchesTarget) GreenAccent else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (installments.isEmpty() && showEmptyState) {
            Text(
                text = t("loans.schedule.empty_message"),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            installments.forEachIndexed { index, item ->
                val parsedAmount = parseLoanAmount(item.expectedAmountInput) ?: 0.0
                val isPaid = item.isPaid
                val isPastDue = !isPaid && todayIso.isNotBlank() && item.dueDate < todayIso
                val isBeingEdited = item.draftId == editingDraftId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FieldBackground)
                        .border(
                            width = 1.dp,
                            color = when {
                                isBeingEdited -> GreenAccent
                                isPaid -> GreenAccent.copy(alpha = 0.5f)
                                isPastDue -> ErrorRed.copy(alpha = 0.6f)
                                else -> BorderColor
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("${itemTagPrefix}_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatLoanShortDate(item.dueDate, language),
                                color = if (isPastDue) ErrorRed else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isPaid) {
                                Text(
                                    text = t("loans.schedule.paid_label"),
                                    color = GreenAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (isPastDue) {
                                Text(
                                    text = t("loans.schedule.overdue_label"),
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        val amountText = if (item.paymentAmount > 0.0) {
                            "${CurrencyFormatter.format(parsedAmount, currency)} • ${t("loans.card.paid")}: ${CurrencyFormatter.format(item.paymentAmount, currency)}"
                        } else {
                            CurrencyFormatter.format(parsedAmount, currency)
                        }
                        Text(
                            text = amountText,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (!isPaid) {
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("${optionsButtonTagPrefix}_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = t("loans.schedule.options_content_desc"),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .background(CardBackground)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(t("loans.schedule.edit_installment"), color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onEditClick(item.draftId)
                                    },
                                    modifier = Modifier.testTag("${editButtonTagPrefix}_$index")
                                )
                                DropdownMenuItem(
                                    text = { Text(t("loans.schedule.remove_content_desc"), color = ErrorRed) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = ErrorRed
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveClick(item.draftId)
                                    },
                                    modifier = Modifier.testTag("${removeButtonTagPrefix}_$index")
                                )
                            }
                        }
                    }
                }
            }
        }

        DatePickerField(
            value = dateInput,
            onValueChange = onDateChange,
            label = t("loans.schedule.date_label"),
            minDateIso = todayIso.takeIf { it.isNotBlank() },
            displayFormatter = { iso -> formatLoanShortDate(iso, language) },
            cancelText = t("loans.schedule.cancel_button"),
            fieldTestTag = dateFieldTestTag
        )

        LoanAmountField(
            label = t("loans.schedule.amount_label"),
            value = amountInput,
            currency = currency,
            onValueChange = onAmountChange,
            testTag = amountFieldTestTag
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isEditingInstallment) {
                OutlinedButton(
                    onClick = onCancelEditClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_cancel_edit_schedule_installment"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        text = t("loans.schedule.cancel_button"),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag(addButtonTestTag),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GreenAccent)
            ) {
                Icon(
                    imageVector = if (isEditingInstallment) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEditingInstallment) {
                        t("loans.schedule.update_installment_button")
                    } else {
                        t("loans.schedule.add_button")
                    },
                    color = GreenAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
