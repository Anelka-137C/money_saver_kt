package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.absoluteValue

private val LoanAvatarColors = listOf(
    Color(0xFF7C3AED),
    Color(0xFF2563EB),
    Color(0xFF059669),
    Color(0xFFD97706)
)
private val LoanPendingPurple = Color(0xFFA855F7)

@Composable
fun LoanCardItem(
    loan: Loan,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    val avatarColor = LoanAvatarColors[(loan.personName.trim().lowercase().hashCode().absoluteValue) % LoanAvatarColors.size]
    val subtitle = loan.note.trim().ifEmpty {
        loan.savingCategory.trim().ifEmpty { t("loans.card.personal_loan") }
    }

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
