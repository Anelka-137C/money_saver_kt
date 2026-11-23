package com.example.money_saver_android.ui.components.atom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.ui.parseHexColor
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
internal fun PeriodSummaryDonutChart(
    summary: PeriodCategorySummary,
    centerLabel: String,
    formattedTotal: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(136.dp)
            .testTag("chart_period_summary_donut"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(126.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

            if (summary.isEmpty) {
                drawArc(
                    color = FieldBackground,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                    topLeft = topLeft,
                    size = arcSize
                )
            } else {
                var startAngle = -90f
                var accumulatedSweep = 0f
                summary.items.forEachIndexed { index, item ->
                    val sweep = if (index == summary.items.lastIndex) {
                        (360f - accumulatedSweep).coerceAtLeast(0f)
                    } else {
                        (item.proportion * 360f).coerceIn(0f, 360f - accumulatedSweep)
                    }
                    if (sweep > 0f) {
                        drawArc(
                            color = parseHexColor(item.colorHex),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = stroke,
                            topLeft = topLeft,
                            size = arcSize
                        )
                        startAngle += sweep
                        accumulatedSweep += sweep
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                text = centerLabel,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formattedTotal,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("text_summary_center_total")
            )
            if (!summary.isEmpty) {
                Text(
                    text = "100%",
                    color = GreenAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("text_summary_center_percent")
                )
            }
        }
    }
}
