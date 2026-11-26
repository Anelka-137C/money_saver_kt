package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.domain.usecase.CategorySummaryItem
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.ui.components.atom.DONUT_FILL_ANIMATION_DURATION_MS
import com.example.money_saver_android.ui.components.atom.PeriodSummaryDonutChart
import com.example.money_saver_android.ui.components.atom.calculateAnimatedDonutSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodSummaryDonutChartTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val multiCategorySummary = PeriodCategorySummary(
        totalAmount = 1000.0,
        items = listOf(
            CategorySummaryItem("Food", "#1CC88A", 500.0, 0.50f, 50),
            CategorySummaryItem("Housing", "#3B82F6", 250.0, 0.25f, 25),
            CategorySummaryItem("Transport", "#F59E0B", 250.0, 0.25f, 25)
        )
    )

    @Test
    fun animation_duration_constant_is_one_second() {
        assertEquals(1000, DONUT_FILL_ANIMATION_DURATION_MS)
    }

    @Test
    fun calculateAnimatedDonutSegments_at_zero_progress_returns_empty() {
        val segments = calculateAnimatedDonutSegments(multiCategorySummary, 0f)
        assertTrue(segments.isEmpty())
    }

    @Test
    fun calculateAnimatedDonutSegments_at_half_progress_expands_all_segments_simultaneously_from_start() {
        val segments = calculateAnimatedDonutSegments(multiCategorySummary, 0.5f)

        assertEquals(3, segments.size)

        assertEquals("#1CC88A", segments[0].colorHex)
        assertEquals(-90f, segments[0].startAngle, 0.001f)
        assertEquals(90f, segments[0].sweepAngle, 0.001f)

        assertEquals("#3B82F6", segments[1].colorHex)
        assertEquals(90f, segments[1].startAngle, 0.001f)
        assertEquals(45f, segments[1].sweepAngle, 0.001f)

        assertEquals("#F59E0B", segments[2].colorHex)
        assertEquals(180f, segments[2].startAngle, 0.001f)
        assertEquals(45f, segments[2].sweepAngle, 0.001f)

        val totalSweep = segments.sumOf { it.sweepAngle.toDouble() }.toFloat()
        assertEquals(180f, totalSweep, 0.001f)
    }

    @Test
    fun calculateAnimatedDonutSegments_at_0_875_progress_expands_each_slice_proportionally() {
        val segments = calculateAnimatedDonutSegments(multiCategorySummary, 0.875f)

        assertEquals(3, segments.size)
        assertEquals(-90f, segments[0].startAngle, 0.001f)
        assertEquals(157.5f, segments[0].sweepAngle, 0.001f)

        assertEquals(90f, segments[1].startAngle, 0.001f)
        assertEquals(78.75f, segments[1].sweepAngle, 0.001f)

        assertEquals(180f, segments[2].startAngle, 0.001f)
        assertEquals(78.75f, segments[2].sweepAngle, 0.001f)

        val totalSweep = segments.sumOf { it.sweepAngle.toDouble() }.toFloat()
        assertEquals(315f, totalSweep, 0.001f)
    }

    @Test
    fun calculateAnimatedDonutSegments_at_full_progress_completes_360_degrees_contiguously() {
        val segments = calculateAnimatedDonutSegments(multiCategorySummary, 1f)

        assertEquals(3, segments.size)
        assertEquals(180f, segments[0].sweepAngle, 0.001f)
        assertEquals(90f, segments[1].sweepAngle, 0.001f)
        assertEquals(90f, segments[2].sweepAngle, 0.001f)
        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)

        for (i in 0 until segments.lastIndex) {
            assertEquals(
                segments[i].startAngle + segments[i].sweepAngle,
                segments[i + 1].startAngle,
                0.001f
            )
        }
    }

    @Test
    fun calculateAnimatedDonutSegments_handles_empty_out_of_range_and_non_finite_values() {
        assertTrue(calculateAnimatedDonutSegments(PeriodCategorySummary(), 1f).isEmpty())
        assertTrue(calculateAnimatedDonutSegments(multiCategorySummary, -0.5f).isEmpty())
        assertTrue(calculateAnimatedDonutSegments(multiCategorySummary, Float.NaN).isEmpty())
        assertTrue(calculateAnimatedDonutSegments(multiCategorySummary, Float.POSITIVE_INFINITY).isEmpty())
        assertTrue(calculateAnimatedDonutSegments(multiCategorySummary, Float.NEGATIVE_INFINITY).isEmpty())

        val clamped = calculateAnimatedDonutSegments(multiCategorySummary, 2.5f)
        assertEquals(360f, clamped.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)

        val invalidProportionSummary = PeriodCategorySummary(
            totalAmount = 100.0,
            items = listOf(
                CategorySummaryItem("BadNaN", "#1CC88A", 50.0, Float.NaN, 50),
                CategorySummaryItem("BadNegative", "#3B82F6", 25.0, -0.5f, 0),
                CategorySummaryItem("FallbackLast", "#F59E0B", 25.0, 0.25f, 25)
            )
        )
        val sanitized = calculateAnimatedDonutSegments(invalidProportionSummary, 1f)
        assertEquals(1, sanitized.size)
        assertEquals("#F59E0B", sanitized[0].colorHex)
        assertEquals(-90f, sanitized[0].startAngle, 0.001f)
        assertEquals(360f, sanitized[0].sweepAngle, 0.001f)
    }

    @Test
    fun donut_chart_animates_across_clock_steps_and_transitions_cleanly() {
        composeTestRule.mainClock.autoAdvance = false

        var summaryState by mutableStateOf(PeriodCategorySummary())
        var formattedTotal by mutableStateOf("$0.00")

        composeTestRule.setContent {
            PeriodSummaryDonutChart(
                summary = summaryState,
                centerLabel = "Total",
                formattedTotal = formattedTotal
            )
        }

        // Empty state: base ring without percentage badge
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$0.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertDoesNotExist()

        // Transition from empty to populated -> triggers 1000 ms fill animation
        Snapshot.withMutableSnapshot {
            summaryState = multiCategorySummary
            formattedTotal = "$1,000.00"
        }

        // 0 ms
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$1,000.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")

        // 500 ms (mid-animation)
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")

        // 1000 ms (animation complete)
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$1,000.00")

        // Change summary (e.g., period switch) -> restarts animation smoothly
        Snapshot.withMutableSnapshot {
            summaryState = PeriodCategorySummary(
                totalAmount = 400.0,
                items = listOf(
                    CategorySummaryItem("Salary", "#10B981", 400.0, 1.0f, 100)
                )
            )
            formattedTotal = "$400.00"
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$400.00")
        composeTestRule.mainClock.advanceTimeBy(DONUT_FILL_ANIMATION_DURATION_MS.toLong())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
    }
}
