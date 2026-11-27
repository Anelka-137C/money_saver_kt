package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    sectionTitle: String? = null,
    showEmptyState: Boolean = false
) {
    val language = LocalI18nManager.current.currentLanguage
    val scheduledTotal = installments.fold(BigDecimal.ZERO) { acc, item ->
        val parsed = parseLoanAmount(item.expectedAmountInput)
        if (parsed != null && parsed.isFinite() && parsed > 0.0) {
            acc.add(BigDecimal.valueOf(parsed))
        } else {
            acc
        }
    }

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
                val isPastDue = todayIso.isNotBlank() && item.dueDate < todayIso
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FieldBackground)
                        .border(
                            width = 1.dp,
                            color = if (isPastDue) ErrorRed.copy(alpha = 0.6f) else BorderColor,
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
                            if (isPastDue) {
                                Text(
                                    text = t("loans.schedule.overdue_label"),
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            text = CurrencyFormatter.format(parsedAmount, currency),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick = { onRemoveClick(item.draftId) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("${removeButtonTagPrefix}_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = t("loans.schedule.remove_content_desc"),
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
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

        OutlinedButton(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag(addButtonTestTag),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GreenAccent)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = GreenAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = t("loans.schedule.add_button"),
                color = GreenAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
