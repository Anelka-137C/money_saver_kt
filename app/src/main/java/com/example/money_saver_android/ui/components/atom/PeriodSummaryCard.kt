package com.example.money_saver_android.ui.components.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun PeriodSummaryCard(
    title: String,
    totalSubtitle: String,
    centerLabel: String,
    emptyMessage: String,
    summary: PeriodCategorySummary,
    currency: Currency = defaultCurrencies.first(),
    cardTestTag: String = "card_period_summary",
    totalTestTag: String = "text_period_summary_total",
    modifier: Modifier = Modifier
) {
    val displayTotal = if (summary.isEmpty) 0.0 else summary.totalAmount
    val formattedTotal = CurrencyFormatter.format(displayTotal, currency)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedTotal,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(totalTestTag)
            )

            Text(
                text = totalSubtitle,
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PeriodSummaryDonutChart(
                    summary = summary,
                    centerLabel = centerLabel,
                    formattedTotal = formattedTotal
                )

                PeriodSummaryLegend(
                    items = summary.items,
                    currency = currency,
                    emptyMessage = emptyMessage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
