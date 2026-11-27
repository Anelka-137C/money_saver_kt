package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
import com.example.money_saver_android.domain.usecase.calculateLoanDueBadgeInfo
import com.example.money_saver_android.domain.usecase.formatLoanPaymentDateTime
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val HistoryPendingPurple = Color(0xFFA855F7)
private val HistoryDueAmber = Color(0xFFF59E0B)
private val HistoryDueAmberText = Color(0xFFFBBF24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanPaymentHistorySheet(
    loan: Loan,
    currency: Currency,
    todayIso: String = "",
    onEditPaymentClick: (LoanPayment) -> Unit = {},
    onDeletePaymentClick: (LoanPayment) -> Unit = {},
    onRegisterNewPaymentClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val language = LocalI18nManager.current.currentLanguage
    val avatarColor = loanAvatarColor(loan.personName)
    val subtitle = loan.note.trim().ifEmpty {
        loan.savingCategory.trim().ifEmpty { t("loans.card.personal_loan") }
    }
    val effectiveTodayIso = todayIso.ifBlank {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    }
    val badgeInfo = if (loan.isSettled) null else calculateLoanDueBadgeInfo(loan.installments, effectiveTodayIso)

    val sortedInstallments = loan.installments
        .sortedWith(compareBy<LoanInstallment> { it.dueDate.trim { ch -> ch.isWhitespace() } }.thenBy { it.id })
    val totalInstallmentsCount = sortedInstallments.size
    val paidInstallmentsCount = sortedInstallments.count { it.isPaid }
    val paidPercent = (loan.progressFraction * 100f).roundToInt().coerceIn(0, 100)

    val sortedPayments = loan.payments
        .sortedWith(compareByDescending<LoanPayment> { it.paidAt }.thenByDescending { it.id })
    val maxSheetContentHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("sheet_loan_payment_history")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetContentHeight)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.85f))
                            .border(2.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildPersonInitials(loan.personName),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = loan.personName,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (loan.isSettled) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GreenAccent.copy(alpha = 0.16f))
                                .border(1.dp, GreenAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = t("loans.card.settled_badge"),
                                color = GreenAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (badgeInfo != null) {
                        val formattedDate = formatLoanShortDate(badgeInfo.nextDueDate, language)
                        val dueTemplate = t("loans.card.due_prefix")
                        val badgeLabel = runCatching { String.format(dueTemplate, formattedDate) }
                            .getOrDefault("Vence: $formattedDate")
                        val badgeBg = if (badgeInfo.isOverdue) ErrorRed.copy(alpha = 0.16f) else HistoryDueAmber.copy(alpha = 0.16f)
                        val badgeBorder = if (badgeInfo.isOverdue) ErrorRed.copy(alpha = 0.55f) else HistoryDueAmber.copy(alpha = 0.45f)
                        val badgeColor = if (badgeInfo.isOverdue) ErrorRed else HistoryDueAmberText

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(badgeBg)
                                .border(1.dp, badgeBorder, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = badgeLabel,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, CircleShape)
                        .testTag("btn_close_payment_history")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val paidPercentTemplate = t("loans.history_sheet.paid_percent_label")
                    val paidPercentText = runCatching { String.format(paidPercentTemplate, paidPercent) }
                        .getOrDefault("$paidPercent% pagado")
                    Text(
                        text = paidPercentText,
                        color = GreenAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("text_history_paid_percent")
                    )

                    val rightProgressText = if (totalInstallmentsCount > 0) {
                        val instTemplate = t("loans.history_sheet.installments_progress")
                        runCatching { String.format(instTemplate, paidInstallmentsCount, totalInstallmentsCount) }
                            .getOrDefault("$paidInstallmentsCount de $totalInstallmentsCount cuotas")
                    } else {
                        val countLabel = if (sortedPayments.size == 1) {
                            t("loans.history_sheet.payment_singular")
                        } else {
                            t("loans.history_sheet.payment_plural")
                        }
                        "${sortedPayments.size} $countLabel"
                    }
                    Text(
                        text = rightProgressText,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("text_history_progress_detail")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FieldBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loan.progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(GreenAccent)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoanMetricColumn(
                    label = t("loans.card.amount_lent"),
                    value = CurrencyFormatter.format(loan.amountLent, currency),
                    labelColor = TextSecondary,
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                LoanMetricColumn(
                    label = t("loans.card.paid"),
                    value = CurrencyFormatter.format(loan.paidAmount, currency),
                    labelColor = TextSecondary,
                    valueColor = GreenAccent,
                    valueTestTag = "text_history_total_paid",
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                LoanMetricColumn(
                    label = t("loans.card.pending"),
                    value = CurrencyFormatter.format(loan.pendingAmount, currency),
                    labelColor = TextSecondary,
                    valueColor = HistoryPendingPurple,
                    valueTestTag = "text_history_total_pending",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("loans.history_sheet.payments_section_title"),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val countLabel = if (sortedPayments.size == 1) {
                    t("loans.history_sheet.payment_singular")
                } else {
                    t("loans.history_sheet.payment_plural")
                }
                Text(
                    text = "${sortedPayments.size} $countLabel",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("text_payment_history_count")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag("list_payment_history"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sortedPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FieldBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("loans.history_sheet.empty_message"),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.testTag("text_payment_history_empty")
                        )
                    }
                } else {
                    sortedPayments.forEach { payment ->
                        PaymentHistoryRow(
                            payment = payment,
                            loan = loan,
                            sortedInstallments = sortedInstallments,
                            currency = currency,
                            language = language,
                            onEditClick = { onEditPaymentClick(payment) },
                            onDeleteClick = { onDeletePaymentClick(payment) }
                        )
                    }
                }
            }

            if (!loan.isSettled) {
                Button(
                    onClick = onRegisterNewPaymentClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_history_register_payment"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("loans.history_sheet.register_new_payment"),
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
private fun PaymentHistoryRow(
    payment: LoanPayment,
    loan: Loan,
    sortedInstallments: List<LoanInstallment>,
    currency: Currency,
    language: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val matchedIndex = payment.installmentId?.let { targetId ->
        sortedInstallments.indexOfFirst { it.id == targetId }.takeIf { it >= 0 }
    }
    val matchedInstallment = matchedIndex?.let { sortedInstallments[it] }

    val primaryTagText = if (matchedIndex != null && sortedInstallments.isNotEmpty()) {
        val template = t("loans.history_sheet.installment_of_total")
        runCatching { String.format(template, matchedIndex + 1, sortedInstallments.size) }
            .getOrDefault("Cuota ${matchedIndex + 1} de ${sortedInstallments.size}")
    } else {
        t("loans.history_sheet.debt_payment_tag")
    }

    val isCompleteForTarget = if (matchedInstallment != null) {
        BigDecimal.valueOf(payment.amount) >= BigDecimal.valueOf(matchedInstallment.expectedAmount)
    } else {
        BigDecimal.valueOf(payment.amount) >= BigDecimal.valueOf(loan.amountToReceive)
    }
    val statusBadgeText = if (isCompleteForTarget) {
        t("loans.history_sheet.full_payment_badge")
    } else {
        t("loans.history_sheet.partial_payment_badge")
    }

    val formattedDateTime = formatLoanPaymentDateTime(payment.paidAt, language)
    val detailLine = if (payment.savingCategory.isNotBlank()) {
        "$formattedDateTime • ${payment.savingCategory}"
    } else {
        formattedDateTime
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("payment_history_item_${payment.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GreenAccent.copy(alpha = 0.14f))
                .border(1.dp, GreenAccent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GreenAccent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = primaryTagText,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GreenAccent.copy(alpha = 0.14f))
                        .border(1.dp, GreenAccent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        color = GreenAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = detailLine,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "+${CurrencyFormatter.format(payment.amount, currency)}",
            color = GreenAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Box {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(30.dp)
                    .testTag("btn_options_payment_history_${payment.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Payment options",
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
                    text = { Text(t("loans.history_sheet.edit_payment"), color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onEditClick()
                    },
                    modifier = Modifier.testTag("btn_edit_payment_history_${payment.id}")
                )
                DropdownMenuItem(
                    text = { Text(t("loans.history_sheet.delete_payment"), color = ErrorRed) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = ErrorRed
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                    modifier = Modifier.testTag("btn_delete_payment_history_${payment.id}")
                )
            }
        }
    }
}
