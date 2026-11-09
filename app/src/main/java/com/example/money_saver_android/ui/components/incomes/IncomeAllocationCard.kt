package com.example.money_saver_android.ui.components.incomes

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun IncomeAllocationCard(
    totalIncome: Double,
    totalBudgeted: Double = 0.0,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    val budgeted = totalBudgeted
    val budgetedPercent = if (totalIncome > 0) ((budgeted / totalIncome) * 100).toInt() else 0
    val budgetedProgress = if (totalIncome > 0) (budgeted / totalIncome).toFloat().coerceIn(0f, 1f) else 0f
    val savings = totalIncome * 0.21

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_income_allocation"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("incomes.allocation.title"),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = t("incomes.allocation.details"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AllocationMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = "📋",
                    label = t("incomes.allocation.budgeted"),
                    amount = CurrencyFormatter.format(budgeted, currency),
                    percent = "$budgetedPercent%",
                    progress = budgetedProgress,
                    accentColor = Color(0xFF1CC88A)
                )

                AllocationMiniCard(
                    modifier = Modifier.weight(1f),
                    icon = "🐖",
                    label = t("incomes.allocation.savings"),
                    amount = CurrencyFormatter.format(savings, currency),
                    percent = "21%",
                    progress = 0.21f,
                    accentColor = Color(0xFF3B82F6)
                )
            }
        }
    }
}

@Composable
private fun AllocationMiniCard(
    icon: String,
    label: String,
    amount: String,
    percent: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = amount,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = percent,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = CardBackground
            )
        }
    }
}
