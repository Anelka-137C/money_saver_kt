package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.usecase.calculateLoanDueBadgeInfo
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private val LoanAvatarColors = listOf(
    Color(0xFF7C3AED),
    Color(0xFF2563EB),
    Color(0xFF059669),
    Color(0xFFD97706)
)
private val LoanPendingPurple = Color(0xFFA855F7)
private val LoanDueAmber = Color(0xFFF59E0B)
private val LoanDueAmberText = Color(0xFFFBBF24)

@Composable
fun LoanCardItem(
    loan: Loan,
    currency: Currency,
    modifier: Modifier = Modifier,
    todayIso: String = "",
    onManageInstallmentsClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val avatarColor = LoanAvatarColors[(loan.personName.trim().lowercase().hashCode().absoluteValue) % LoanAvatarColors.size]
    val subtitle = loan.note.trim().ifEmpty {
        loan.savingCategory.trim().ifEmpty { t("loans.card.personal_loan") }
    }
    val effectiveTodayIso = todayIso.ifBlank {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    }
    val badgeInfo = calculateLoanDueBadgeInfo(loan.installments, effectiveTodayIso)
    val language = LocalI18nManager.current.currentLanguage

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("loan_card_${loan.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buildPersonInitials(loan.personName),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.personName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
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

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_options_loan_${loan.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary
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
                            text = { Text(t("loans.options.manage_installments"), color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onManageInstallmentsClick()
                            },
                            modifier = Modifier.testTag("btn_installments_loan_${loan.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(t("loans.options.edit"), color = TextPrimary) },
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
                            modifier = Modifier.testTag("btn_edit_loan_${loan.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(t("loans.options.delete"), color = ErrorRed) },
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
                            modifier = Modifier.testTag("btn_delete_loan_${loan.id}")
                        )
                    }
                }
            }

            if (badgeInfo != null) {
                val formattedDate = formatLoanShortDate(badgeInfo.nextDueDate, language)
                val dueTemplate = t("loans.card.due_prefix")
                val baseBadge = runCatching {
                    String.format(dueTemplate, formattedDate)
                }.getOrDefault("Vence: $formattedDate")
                val badgeLabel = if (badgeInfo.additionalCount > 0) {
                    "$baseBadge +${badgeInfo.additionalCount}"
                } else {
                    baseBadge
                }
                val badgeBg = if (badgeInfo.isOverdue) {
                    ErrorRed.copy(alpha = 0.16f)
                } else {
                    LoanDueAmber.copy(alpha = 0.16f)
                }
                val badgeBorder = if (badgeInfo.isOverdue) {
                    ErrorRed.copy(alpha = 0.55f)
                } else {
                    LoanDueAmber.copy(alpha = 0.45f)
                }
                val badgeContent = if (badgeInfo.isOverdue) ErrorRed else LoanDueAmberText

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("loan_due_badge_${loan.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = badgeContent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = badgeLabel,
                        color = badgeContent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress bar (0% for #15 before payments exist)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FieldBackground)
                    .testTag("loan_progress_${loan.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0f)
                        .fillMaxHeight()
                        .background(avatarColor)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
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
                    value = CurrencyFormatter.format(0.0, currency),
                    labelColor = TextSecondary,
                    valueColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                LoanMetricColumn(
                    label = t("loans.card.pending"),
                    value = CurrencyFormatter.format(loan.amountToReceive, currency),
                    labelColor = LoanPendingPurple,
                    valueColor = LoanPendingPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LoanMetricColumn(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(BorderColor)
    )
}

internal fun buildPersonInitials(personName: String): String {
    val parts = personName.trim { it.isWhitespace() }.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].takeCodePoints(2).uppercase(Locale.ROOT)
        else -> "${parts[0].takeCodePoints(1)}${parts[1].takeCodePoints(1)}".uppercase(Locale.ROOT)
    }
}

private fun String.takeCodePoints(count: Int): String {
    if (isEmpty() || count <= 0) return ""
    var offset = 0
    var taken = 0
    while (offset < length && taken < count) {
        offset += Character.charCount(codePointAt(offset))
        taken++
    }
    return substring(0, offset)
}
