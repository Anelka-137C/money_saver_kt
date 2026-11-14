package com.example.money_saver_android.ui.components.budget

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.CategoryBudgetItem
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.parseHexColor
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun BudgetAllocationCard(
    categoryBudgets: List<CategoryBudgetItem>,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_budget_allocation"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("budget.allocation.title"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(t("budget.allocation.details"), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (categoryBudgets.isEmpty()) {
                Text(
                    text = t("budget.allocation.empty_message"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("text_budget_allocation_empty")
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryBudgets.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BudgetAllocationMiniCard(rowItems[0], currency, Modifier.weight(1f))
                            if (rowItems.size > 1) {
                                BudgetAllocationMiniCard(rowItems[1], currency, Modifier.weight(1f))
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BudgetAllocationMiniCard(
    item: CategoryBudgetItem,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    val accentColor = parseHexColor(item.colorHex, GreenAccent)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .testTag("mini_card_budget_${item.categoryName}")
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.categoryName,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CurrencyFormatter.format(item.remainingAmount, currency),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("text_budget_remaining_${item.categoryName}")
                    )
                    Text(
                        text = "${t("budget.allocation.budget_label")}: ${CurrencyFormatter.format(item.budgetAmount, currency)}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${item.percent}%",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .testTag("progress_budget_${item.categoryName}"),
                color = accentColor,
                trackColor = CardBackground
            )
        }
    }
}
