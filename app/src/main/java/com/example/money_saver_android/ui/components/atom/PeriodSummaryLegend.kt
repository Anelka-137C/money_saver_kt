package com.example.money_saver_android.ui.components.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.CategorySummaryItem
import com.example.money_saver_android.ui.parseHexColor
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
internal fun PeriodSummaryLegend(
    items: List<CategorySummaryItem>,
    currency: Currency,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Text(
            text = emptyMessage,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = modifier.testTag("text_period_summary_empty")
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            val categoryColor = parseHexColor(item.colorHex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("legend_item_${item.categoryName}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.categoryName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = CurrencyFormatter.format(item.amount, currency),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.testTag("legend_amount_${item.categoryName}")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${item.percentage}%",
                    color = categoryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.testTag("legend_percent_${item.categoryName}")
                )
            }
            if (index < items.lastIndex) {
                HorizontalDivider(
                    color = BorderColor.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
