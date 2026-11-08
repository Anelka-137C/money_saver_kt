package com.example.money_saver_android.ui.components.incomes

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale

@Composable
fun IncomeOverviewCard(
    totalIncome: Double,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    // Example breakdown proportions consistent with design/incomes.png
    val budgetedPercent = 0.34
    val savingsPercent = 0.21
    val spentPercent = 0.55
    val availablePercent = 0.45

    val budgetedAmount = totalIncome * budgetedPercent
    val savingsAmount = totalIncome * savingsPercent
    val spentAmount = totalIncome * spentPercent
    val availableAmount = totalIncome * availablePercent

    val formattedTotal = CurrencyFormatter.format(totalIncome, currency)
    val formattedSpent = CurrencyFormatter.format(spentAmount, currency)
    val formattedAvailable = CurrencyFormatter.format(availableAmount, currency)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_income_overview"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("incomes.overview.title"),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF143026))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = t("incomes.overview.trend"),
                        color = GreenAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedTotal,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("text_total_income")
            )

            Text(
                text = t("incomes.overview.total_income"),
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Donut chart + Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut Chart Box
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        val strokeWidth = 16.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

                        // 4 segments from design: Green (34%), Blue (21%), Yellow (25%), Purple (20%)
                        drawArc(
                            color = Color(0xFF1CC88A),
                            startAngle = -90f,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                        drawArc(
                            color = Color(0xFF3B82F6),
                            startAngle = 15f,
                            sweepAngle = 75f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                        drawArc(
                            color = Color(0xFFF59E0B),
                            startAngle = 95f,
                            sweepAngle = 110f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                        drawArc(
                            color = Color(0xFFA855F7),
                            startAngle = 210f,
                            sweepAngle = 55f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                            topLeft = topLeft,
                            size = arcSize
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t("incomes.overview.used"), color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = formattedSpent,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("55%", color = GreenAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Legend
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    LegendItem(color = Color(0xFF1CC88A), label = t("incomes.allocation.budgeted"), amount = CurrencyFormatter.format(budgetedAmount, currency), percent = "34%")
                    LegendItem(color = Color(0xFF3B82F6), label = t("incomes.allocation.savings"), amount = CurrencyFormatter.format(savingsAmount, currency), percent = "21%")
                    LegendItem(color = Color(0xFFF59E0B), label = t("incomes.allocation.spent"), amount = CurrencyFormatter.format(spentAmount, currency), percent = "55%")
                    LegendItem(color = Color(0xFFA855F7), label = t("incomes.allocation.available"), amount = CurrencyFormatter.format(availableAmount, currency), percent = "45%")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Alert / Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💳", fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val rawBannerTitle = t("incomes.overview.available_banner_title")
                    val bannerTitleText = try {
                        if (rawBannerTitle.contains("%s")) String.format(rawBannerTitle, formattedAvailable) else "$rawBannerTitle $formattedAvailable"
                    } catch (_: Exception) {
                        rawBannerTitle
                    }

                    val rawBannerSub = t("incomes.overview.available_banner_subtitle")
                    val bannerSubText = try {
                        if (rawBannerSub.contains("%s")) String.format(rawBannerSub, "45%") else rawBannerSub
                    } catch (_: Exception) {
                        rawBannerSub
                    }

                    Column {
                        Text(
                            text = bannerTitleText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bannerSubText,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    amount: String,
    percent: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.width(170.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(amount, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(6.dp))
            Text(percent, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
