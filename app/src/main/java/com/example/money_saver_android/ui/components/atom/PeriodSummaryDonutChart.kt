package com.example.money_saver_android.ui.components.atom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

internal const val DONUT_FILL_ANIMATION_DURATION_MS: Int = 1000

/**
 * Geometry descriptor for a single category arc drawn in [PeriodSummaryDonutChart].
 */
internal data class DonutArcSegment(
    val colorHex: String,
    val startAngle: Float,
    val sweepAngle: Float
)

/**
 * Computes the visible donut arc segments for a normalized fill [progress] in `0f..1f`.
 * Every category segment starts at its final clockwise angle (from `-90f`, 12 o'clock)
 * and expands simultaneously in proportion to [progress].
 */
internal fun calculateAnimatedDonutSegments(
    summary: PeriodCategorySummary,
    progress: Float
): List<DonutArcSegment> {
    if (summary.isEmpty || !summary.totalAmount.isFinite() || !progress.isFinite()) return emptyList()
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) return emptyList()

    val validItems = summary.items.filter { it.proportion.isFinite() && it.proportion > 0f }
    if (validItems.isEmpty()) return emptyList()

    val segments = mutableListOf<DonutArcSegment>()
    // Accumulate in Double and derive startAngle from accumulatedSweep to avoid Float drift across many slices.
    var accumulatedSweep = 0.0
    val progressRatio = clampedProgress.toDouble()

    validItems.forEachIndexed { index, item ->
        val remainingCircle = (360.0 - accumulatedSweep).coerceAtLeast(0.0)
        val fullSweep = if (index == validItems.lastIndex) {
            remainingCircle
        } else {
            (item.proportion.toDouble() * 360.0).coerceIn(0.0, remainingCircle)
        }
        if (fullSweep > 0.0) {
            val startAngle = (-90.0 + accumulatedSweep).toFloat()
            val animatedSweep = (fullSweep * progressRatio).toFloat()
            if (animatedSweep > 0f) {
                segments.add(
                    DonutArcSegment(
                        colorHex = item.colorHex,
                        startAngle = startAngle,
                        sweepAngle = animatedSweep
                    )
                )
            }
            accumulatedSweep += fullSweep
        }
    }

    return segments
}

@Composable
internal fun PeriodSummaryDonutChart(
    summary: PeriodCategorySummary,
    centerLabel: String,
    formattedTotal: String,
    modifier: Modifier = Modifier
) {
    val sweepProgress = remember { Animatable(0f) }
    val segmentColors = remember(summary) {
        summary.items.associate { it.colorHex to parseHexColor(it.colorHex) }
    }

    LaunchedEffect(summary) {
        sweepProgress.snapTo(0f)
        if (!summary.isEmpty) {
            sweepProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DONUT_FILL_ANIMATION_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(136.dp)
            .testTag("chart_period_summary_donut"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(126.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            if (!diameter.isFinite() || diameter <= 0f) return@Canvas
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

            drawArc(
                color = FieldBackground,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                topLeft = topLeft,
                size = arcSize
            )

            val segments = calculateAnimatedDonutSegments(
                summary = summary,
                progress = sweepProgress.value
            )
            segments.forEach { segment ->
                drawArc(
                    color = segmentColors[segment.colorHex] ?: parseHexColor(segment.colorHex),
                    startAngle = segment.startAngle,
                    sweepAngle = segment.sweepAngle,
                    useCenter = false,
                    style = stroke,
                    topLeft = topLeft,
                    size = arcSize
                )
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
