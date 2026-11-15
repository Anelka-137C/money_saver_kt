package com.example.money_saver_android.ui.components.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Period expense summary card matching BudgetOverviewCard's visual structure.
 */
@Composable
fun ExpenseOverviewCard(
    totalSpent: Double,
    totalBudgeted: Double,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    val progress = if (totalBudgeted > 0.0) (totalSpent / totalBudgeted).toFloat().coerceIn(0f, 1f) else 0f
    val rawPercent = if (totalBudgeted > 0.0) ((totalSpent / totalBudgeted) * 100).toInt() else 0
    val isOverBudget = totalBudgeted > 0.0 && totalSpent > totalBudgeted
    val accentColor = if (isOverBudget) Color(0xFFEF4444) else GreenAccent
    val badgeBg = if (isOverBudget) Color(0xFF3B1A1A) else Color(0xFF143026)
    val remainingBudget = maxOf(0.0, totalBudgeted - totalSpent)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_expense_overview"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("expenses.overview.title"), color = TextSecondary, fontSize = 15.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$rawPercent%",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("text_expense_percent_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = CurrencyFormatter.format(totalSpent, currency),
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("text_expense_total_spent")
            )
            Text(t("expenses.overview.total_spent"), color = TextSecondary, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)
                        drawArc(
                            color = FieldBackground,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                    }
                    Text(
                        text = "$rawPercent%",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(t("expenses.overview.budget_label"), color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = CurrencyFormatter.format(totalBudgeted, currency),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_expense_total_budgeted")
                        )
                    }
                    Column {
                        Text(t("budget.overview.remaining_label"), color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = CurrencyFormatter.format(remainingBudget, currency),
                            color = GreenAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_expense_budget_remaining")
                        )
                    }
                }
            }
        }
    }
}
