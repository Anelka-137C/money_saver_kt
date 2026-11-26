package com.example.money_saver_android.adversarial

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import kotlin.math.abs
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for the simultaneous 1-second fill animation
 * in `PeriodSummaryDonutChart.kt`.
 *
 * Rules observed:
 * 1. Strictly test-only; production code under `app/src/main` is untouched.
 * 2. Property-Based Testing invariants over randomized inputs (simultaneous expansion,
 *    fixed anchor angles, linearity, non-overlap, monotonicity, clamping, and 360° closure).
 * 3. Adversarial boundary & numerical edge cases designed to expose real defects
 *    (non-finite `totalAmount`, invalid last-item proportion hijacking `remainingCircle`,
 *    float overflow/underflow, and cumulative floating-point drift over 10,000 slices).
 * 4. Android / Compose lifecycle, `LaunchedEffect` coroutine cancellation under rapid
 *    state churn, tight layout constraints (`size < strokeWidth`), and hostile inputs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodSummaryDonutChartAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // =========================================================================
    // SECTION 1: Property-Based Testing & Strict Geometric Invariants
    // =========================================================================

    /**
     * Invariants verified across 500 randomized multi-category summaries:
     * 1. Finiteness & bounds: every segment has finite `startAngle in -90f..270f` and
     *    finite `sweepAngle in (0f, 360f]`.
     * 2. Fixed simultaneous start anchor: every segment's `startAngle` at any `p in (0, 1]`
     *    equals its final `startAngle` at `p = 1f`.
     * 3. Exact proportional simultaneous growth: `sweep(p) == sweep(1f) * p`.
     * 4. Strict non-overlap: `seg[i].startAngle + seg[i].sweepAngle <= seg[i+1].startAngle`.
     * 5. Total visible sweep equals `360f * p`.
     * 6. Monotonicity: for `p1 <= p2`, every slice's sweep is non-decreasing.
     * 7. Full 360° contiguous closure at `p = 1f` from `-90f` to `270f`.
     */
    @Test
    fun property_simultaneous_expansion_invariants_across_500_randomized_summaries() {
        val random = Random(20261126)
        val palette = listOf("#1CC88A", "#3B82F6", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")
        val checkpoints = listOf(0.001f, 0.25f, 0.5f, 0.75f, 0.999f, 1.0f)

        repeat(500) { iteration ->
            val categoryCount = random.nextInt(1, 25)
            val rawAmounts = List(categoryCount) { random.nextDouble(1.0, 50_000.0) }
            val total = rawAmounts.sum()
            val items = rawAmounts.mapIndexed { idx, amount ->
                val proportion = (amount / total).toFloat()
                CategorySummaryItem(
                    categoryName = "Cat_${iteration}_$idx",
                    colorHex = palette[idx % palette.size],
                    amount = amount,
                    proportion = proportion,
                    percentage = (proportion * 100f).toInt()
                )
            }
            val summary = PeriodCategorySummary(totalAmount = total, items = items)

            val fullSegments = calculateAnimatedDonutSegments(summary, 1f)
            assertTrue(
                "Iter $iteration: non-empty summary must produce at least 1 segment at progress=1f",
                fullSegments.isNotEmpty()
            )
            assertEquals(
                "Iter $iteration: first segment must start at 12 o'clock (-90f)",
                -90f,
                fullSegments.first().startAngle,
                0.001f
            )

            // Invariant 7: Full contiguous 360° closure at p = 1f
            for (i in 0 until fullSegments.lastIndex) {
                assertEquals(
                    "Iter $iteration: segments $i and ${i + 1} must meet seamlessly at progress=1f",
                    fullSegments[i].startAngle + fullSegments[i].sweepAngle,
                    fullSegments[i + 1].startAngle,
                    0.01f
                )
            }
            assertEquals(
                "Iter $iteration: last segment must close at 270f (-90f + 360f)",
                270f,
                fullSegments.last().startAngle + fullSegments.last().sweepAngle,
                0.05f
            )

            val dynamicProgress = random.nextFloat().coerceIn(0.01f, 0.99f)
            val progressSeries = (checkpoints + dynamicProgress).sorted()
            var previousSweeps: List<Float>? = null

            progressSeries.forEach { progress ->
                val segments = calculateAnimatedDonutSegments(summary, progress)
                assertEquals(
                    "Iter $iteration @ p=$progress: segment count must match full count",
                    fullSegments.size,
                    segments.size
                )

                segments.forEachIndexed { idx, seg ->
                    // Invariant 1: Finiteness & bounds
                    assertTrue(
                        "Iter $iteration @ p=$progress: startAngle must be finite",
                        seg.startAngle.isFinite()
                    )
                    assertTrue(
                        "Iter $iteration @ p=$progress: sweepAngle must be finite",
                        seg.sweepAngle.isFinite()
                    )
                    assertTrue(
                        "Iter $iteration @ p=$progress: startAngle=${seg.startAngle} out of [-90, 270]",
                        seg.startAngle in -90.001f..270.001f
                    )
                    assertTrue(
                        "Iter $iteration @ p=$progress: sweepAngle=${seg.sweepAngle} out of (0, 360]",
                        seg.sweepAngle > 0f && seg.sweepAngle <= 360.001f
                    )

                    // Invariant 2: Fixed start angle independent of progress
                    assertEquals(
                        "Iter $iteration @ p=$progress: slice $idx startAngle shifted",
                        fullSegments[idx].startAngle,
                        seg.startAngle,
                        0.0001f
                    )

                    // Invariant 3: Proportional simultaneous scaling
                    assertEquals(
                        "Iter $iteration @ p=$progress: slice $idx sweep did not scale linearly",
                        fullSegments[idx].sweepAngle * progress,
                        seg.sweepAngle,
                        0.005f
                    )

                    // Invariant 4: Strict non-overlap with next segment
                    if (idx < segments.lastIndex) {
                        assertTrue(
                            "Iter $iteration @ p=$progress: slice $idx overlapped slice ${idx + 1}",
                            seg.startAngle + seg.sweepAngle <= segments[idx + 1].startAngle + 0.005f
                        )
                    }
                }

                // Invariant 5: Total visible sweep == 360 * progress
                val totalSweep = segments.sumOf { it.sweepAngle.toDouble() }.toFloat()
                assertEquals(
                    "Iter $iteration @ p=$progress: total visible sweep mismatch",
                    360f * progress,
                    totalSweep,
                    0.1f
                )

                // Invariant 6: Monotonicity across increasing progress
                val currentSweeps = segments.map { it.sweepAngle }
                previousSweeps?.let { prev ->
                    currentSweeps.indices.forEach { idx ->
                        assertTrue(
                            "Iter $iteration @ p=$progress: slice $idx decreased sweep",
                            currentSweeps[idx] + 1e-6f >= prev[idx]
                        )
                    }
                }
                previousSweeps = currentSweeps
            }
        }
    }

    /**
     * Invariants for progress boundary sanitization and upper clamping:
     * - Any `progress <= 0f` or non-finite progress must return `emptyList()`.
     * - Any finite `progress > 1f` must produce the exact same segments as `progress = 1f`.
     */
    @Test
    fun property_clamping_and_invalid_progress_invariants_across_200_trials() {
        val random = Random(99)
        val invalidProgressValues = listOf(
            0f,
            -0.0f,
            -Float.MIN_VALUE,
            -1e-6f,
            -1f,
            -Float.MAX_VALUE,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        )
        val overUnityProgressValues = listOf(
            1.00001f,
            1.5f,
            100f,
            1_000_000f,
            Float.MAX_VALUE
        )

        repeat(200) { iteration ->
            val summary = PeriodCategorySummary(
                totalAmount = 1000.0,
                items = listOf(
                    CategorySummaryItem("A", "#1CC88A", 600.0, 0.6f, 60),
                    CategorySummaryItem("B", "#3B82F6", 400.0, 0.4f, 40)
                )
            )

            invalidProgressValues.forEach { badProgress ->
                assertTrue(
                    "Iter $iteration: progress=$badProgress must produce empty segments",
                    calculateAnimatedDonutSegments(summary, badProgress).isEmpty()
                )
            }

            val full = calculateAnimatedDonutSegments(summary, 1f)
            val randomOverUnity = 1f + random.nextFloat() * 10_000f
            (overUnityProgressValues + randomOverUnity).forEach { highProgress ->
                assertEquals(
                    "Iter $iteration: progress=$highProgress must clamp identically to 1f",
                    full,
                    calculateAnimatedDonutSegments(summary, highProgress)
                )
            }
        }
    }

    // =========================================================================
    // SECTION 2: Adversarial Boundary Cases & Defect Exposure
    // =========================================================================

    /**
     * EXPOSES POTENTIAL BUG #1:
     * `PeriodCategorySummary.isEmpty` checks `items.isEmpty() || totalAmount <= 0.0`.
     * In IEEE-754, `Double.NaN <= 0.0` is `false` and `Double.POSITIVE_INFINITY <= 0.0` is `false`.
     * Because `calculateAnimatedDonutSegments` only checks `summary.isEmpty` and does not verify
     * `summary.totalAmount.isFinite()`, a corrupt summary with `NaN` or `+Infinity` total
     * bypasses the empty guard and emits full 360° segments.
     */
    @Test
    fun adversarial_corrupt_nan_and_infinite_totalAmount_must_return_empty_segments() {
        val nanTotalSummary = PeriodCategorySummary(
            totalAmount = Double.NaN,
            items = listOf(
                CategorySummaryItem("CorruptNaN", "#EF4444", 100.0, 1.0f, 100)
            )
        )
        val infiniteTotalSummary = PeriodCategorySummary(
            totalAmount = Double.POSITIVE_INFINITY,
            items = listOf(
                CategorySummaryItem("CorruptInf", "#EF4444", Double.MAX_VALUE, 1.0f, 100)
            )
        )

        assertTrue(
            "Corrupt summary with totalAmount=Double.NaN must not emit donut segments",
            calculateAnimatedDonutSegments(nanTotalSummary, 1f).isEmpty()
        )
        assertTrue(
            "Corrupt summary with totalAmount=Double.POSITIVE_INFINITY must not emit donut segments",
            calculateAnimatedDonutSegments(infiniteTotalSummary, 1f).isEmpty()
        )
    }

    /**
     * EXPOSES POTENTIAL BUG #2:
     * In `calculateAnimatedDonutSegments`, when `index == summary.items.lastIndex`,
     * `fullSweep` is unconditionally assigned `remainingCircle`, ignoring `item.proportion`.
     * As a result, a trailing category with `proportion = 0f`, negative proportion, or
     * `Float.NaN` silently hijacks all remaining degrees (up to 360°) of the donut chart.
     */
    @Test
    fun adversarial_last_item_with_zero_negative_or_nan_proportion_must_not_steal_remaining_circle() {
        val singleZeroProportion = PeriodCategorySummary(
            totalAmount = 100.0,
            items = listOf(
                CategorySummaryItem("ZeroSlice", "#EF4444", 0.0, 0f, 0)
            )
        )
        val singleNanProportion = PeriodCategorySummary(
            totalAmount = 100.0,
            items = listOf(
                CategorySummaryItem("NaNSlice", "#EF4444", 100.0, Float.NaN, 0)
            )
        )
        val trailingInvalidSlice = PeriodCategorySummary(
            totalAmount = 100.0,
            items = listOf(
                CategorySummaryItem("ValidHalf", "#1CC88A", 50.0, 0.5f, 50),
                CategorySummaryItem("NegativeTrailing", "#EF4444", 0.0, -1.0f, 0)
            )
        )

        assertTrue(
            "A category with proportion=0f must not hijack 360° as lastIndex",
            calculateAnimatedDonutSegments(singleZeroProportion, 1f).isEmpty()
        )
        assertTrue(
            "A category with proportion=NaN must not hijack 360° as lastIndex",
            calculateAnimatedDonutSegments(singleNanProportion, 1f).isEmpty()
        )

        val segments = calculateAnimatedDonutSegments(trailingInvalidSlice, 1f)
        assertTrue(
            "Trailing item with negative proportion must not be rendered with positive sweep",
            segments.none { it.colorHex == "#EF4444" }
        )
    }

    /**
     * EXPOSES POTENTIAL BUG #3 (Cumulative Float Precision Drift):
     * `startAngle` is accumulated from `-90f` while `accumulatedSweep` is accumulated from `0f`.
     * Because IEEE-754 single-precision floats have different ULP spacing near `-90f` vs `0f`,
     * summing thousands of tiny slices causes `startAngle` and `-90f + accumulatedSweep` to
     * diverge, so the final slice (`startAngle + (360f - accumulatedSweep)`) drifts away from
     * exact closure at `270f`.
     */
    @Test
    fun adversarial_massive_10000_tiny_segments_must_close_precisely_at_270_degrees() {
        val count = 10_000
        val proportion = 1f / count
        val items = List(count) { i ->
            CategorySummaryItem(
                categoryName = "Micro_$i",
                colorHex = if (i % 2 == 0) "#1CC88A" else "#3B82F6",
                amount = 0.01,
                proportion = proportion,
                percentage = 0
            )
        }
        val summary = PeriodCategorySummary(totalAmount = count * 0.01, items = items)

        val segments = calculateAnimatedDonutSegments(summary, 1f)
        assertEquals(count, segments.size)

        val finalEndAngle = segments.last().startAngle + segments.last().sweepAngle
        assertTrue(
            "Expected exact closure near 270f without cumulative float drift, but ended at $finalEndAngle",
            abs(finalEndAngle - 270f) <= 0.001f
        )
    }

    /**
     * Extreme Float proportion values (`Float.MAX_VALUE`, `±Infinity`, `-Float.MAX_VALUE`)
     * must never produce `NaN` or `Infinity` sweeps, nor exceed `360f` total sweep.
     */
    @Test
    fun adversarial_extreme_float_proportions_do_not_overflow_or_exceed_360() {
        val extremeSummary = PeriodCategorySummary(
            totalAmount = 1_000.0,
            items = listOf(
                CategorySummaryItem("MaxFloat", "#1CC88A", 250.0, Float.MAX_VALUE, 100),
                CategorySummaryItem("PosInf", "#3B82F6", 250.0, Float.POSITIVE_INFINITY, 0),
                CategorySummaryItem("NegInf", "#F59E0B", 250.0, Float.NEGATIVE_INFINITY, 0),
                CategorySummaryItem("NegMax", "#EF4444", 250.0, -Float.MAX_VALUE, 0)
            )
        )

        val segmentsAtHalf = calculateAnimatedDonutSegments(extremeSummary, 0.5f)
        val segmentsAtFull = calculateAnimatedDonutSegments(extremeSummary, 1f)

        (segmentsAtHalf + segmentsAtFull).forEach { seg ->
            assertTrue("startAngle must be finite", seg.startAngle.isFinite())
            assertTrue("sweepAngle must be finite", seg.sweepAngle.isFinite())
            assertTrue("sweepAngle must be strictly positive", seg.sweepAngle > 0f)
            assertTrue("sweepAngle must not exceed 360f", seg.sweepAngle <= 360f)
        }

        val totalFullSweep = segmentsAtFull.sumOf { it.sweepAngle.toDouble() }.toFloat()
        assertTrue("Total sweep must never exceed 360f, was $totalFullSweep", totalFullSweep <= 360.001f)
    }

    /**
     * Subnormal tiny progress (`Float.MIN_VALUE`) and negative zero (`-0.0f`) must not crash
     * or emit non-finite values, and proportions summing > 1.0 must never exceed 360°.
     */
    @Test
    fun adversarial_subnormal_progress_and_proportions_exceeding_one() {
        val oversubscribedSummary = PeriodCategorySummary(
            totalAmount = 300.0,
            items = listOf(
                CategorySummaryItem("Over1", "#1CC88A", 100.0, 0.6f, 60),
                CategorySummaryItem("Over2", "#3B82F6", 100.0, 0.6f, 60),
                CategorySummaryItem("Over3", "#F59E0B", 100.0, 0.6f, 60)
            )
        )

        assertTrue(calculateAnimatedDonutSegments(oversubscribedSummary, -0.0f).isEmpty())

        val subnormalSegments = calculateAnimatedDonutSegments(oversubscribedSummary, Float.MIN_VALUE)
        subnormalSegments.forEach { seg ->
            assertTrue(seg.startAngle.isFinite())
            assertTrue(seg.sweepAngle.isFinite() && seg.sweepAngle > 0f)
        }

        val fullSegments = calculateAnimatedDonutSegments(oversubscribedSummary, 1f)
        assertEquals(2, fullSegments.size)
        assertEquals(216f, fullSegments[0].sweepAngle, 0.001f)
        assertEquals(144f, fullSegments[1].sweepAngle, 0.001f)
        assertEquals(360f, fullSegments.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)
    }

    // =========================================================================
    // SECTION 3: Android / Compose Lifecycle, Cancellation & Extreme UI Inputs
    // =========================================================================

    /**
     * Rapid state churn every 50 ms cancels in-flight `Animatable.animateTo` coroutines inside
     * `LaunchedEffect(summary)`. Must handle `CancellationException` cleanly and settle on the
     * final summary after `DONUT_FILL_ANIMATION_DURATION_MS`.
     */
    @Test
    fun compose_rapid_summary_churn_cancels_inflight_animations_and_converges_cleanly() {
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

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        // Rapidly alternate summaries every 50 ms while animations are mid-flight
        repeat(10) { step ->
            Snapshot.withMutableSnapshot {
                if (step % 3 == 0) {
                    summaryState = PeriodCategorySummary()
                    formattedTotal = "$0.00"
                } else {
                    val amount = (step + 1) * 100.0
                    summaryState = PeriodCategorySummary(
                        totalAmount = amount,
                        items = listOf(
                            CategorySummaryItem("CatA_$step", "#1CC88A", amount * 0.7, 0.7f, 70),
                            CategorySummaryItem("CatB_$step", "#3B82F6", amount * 0.3, 0.3f, 30)
                        )
                    )
                    formattedTotal = "$$amount"
                }
            }
            composeTestRule.mainClock.advanceTimeBy(50L)
            composeTestRule.waitForIdle()
        }

        // Settle on final summary and allow full animation duration to finish
        Snapshot.withMutableSnapshot {
            summaryState = PeriodCategorySummary(
                totalAmount = 999.0,
                items = listOf(
                    CategorySummaryItem("FinalA", "#1CC88A", 666.0, 0.666f, 67),
                    CategorySummaryItem("FinalB", "#F59E0B", 333.0, 0.334f, 33)
                )
            )
            formattedTotal = "$999.00"
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(DONUT_FILL_ANIMATION_DURATION_MS.toLong())
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$999.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
    }

    /**
     * Transitioning from populated -> empty mid-animation (at 500 ms) must cancel the running
     * animation, snap back to empty without glitches, hide `text_summary_center_percent`, and
     * animate cleanly again when repopulated.
     */
    @Test
    fun compose_populated_to_empty_mid_animation_resets_and_hides_percent_badge() {
        composeTestRule.mainClock.autoAdvance = false

        val initialSummary = PeriodCategorySummary(
            totalAmount = 500.0,
            items = listOf(
                CategorySummaryItem("Food", "#1CC88A", 250.0, 0.5f, 50),
                CategorySummaryItem("Bills", "#3B82F6", 250.0, 0.5f, 50)
            )
        )
        var summaryState by mutableStateOf(initialSummary)
        var formattedTotal by mutableStateOf("$500.00")

        composeTestRule.setContent {
            PeriodSummaryDonutChart(
                summary = summaryState,
                centerLabel = "Gastos",
                formattedTotal = formattedTotal
            )
        }

        // Advance halfway through the 1000 ms animation
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")

        // Switch abruptly to empty period mid-animation
        Snapshot.withMutableSnapshot {
            summaryState = PeriodCategorySummary()
            formattedTotal = "$0.00"
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$0.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertDoesNotExist()

        // Advance full duration while empty to confirm no stray animation crashes
        composeTestRule.mainClock.advanceTimeBy(DONUT_FILL_ANIMATION_DURATION_MS.toLong())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertDoesNotExist()

        // Repopulate and verify clean animation cycle
        Snapshot.withMutableSnapshot {
            summaryState = initialSummary
            formattedTotal = "$500.00"
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(DONUT_FILL_ANIMATION_DURATION_MS.toLong())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("$500.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
    }

    /**
     * Recomposition with a newly instantiated but structurally equal `PeriodCategorySummary`
     * while unrelated props (`centerLabel`, `formattedTotal`) change must keep rendering stable.
     */
    @Test
    fun compose_unrelated_recomposition_with_structurally_equal_summary_remains_stable() {
        composeTestRule.mainClock.autoAdvance = false

        fun buildSummary() = PeriodCategorySummary(
            totalAmount = 1000.0,
            items = listOf(
                CategorySummaryItem("Salary", "#1CC88A", 600.0, 0.6f, 60),
                CategorySummaryItem("Bonus", "#3B82F6", 400.0, 0.4f, 40)
            )
        )

        var summaryState by mutableStateOf(buildSummary())
        var centerLabel by mutableStateOf("Total")
        var formattedTotal by mutableStateOf("$1,000.00")

        composeTestRule.setContent {
            PeriodSummaryDonutChart(
                summary = summaryState,
                centerLabel = centerLabel,
                formattedTotal = formattedTotal
            )
        }

        composeTestRule.mainClock.advanceTimeBy(DONUT_FILL_ANIMATION_DURATION_MS.toLong())
        composeTestRule.waitForIdle()

        // Trigger recomposition with structurally equal summary + updated labels
        Snapshot.withMutableSnapshot {
            summaryState = buildSummary()
            centerLabel = "Neto"
            formattedTotal = "USD 1,000.00"
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neto").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals("USD 1,000.00")
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
    }

    /**
     * Tight parent constraints smaller than `strokeWidth` (16.dp) — e.g. `8.dp` or `0.dp` —
     * cause `diameter = size.minDimension - strokeWidth` to become negative inside `Canvas`.
     * Verifies `PeriodSummaryDonutChart` does not crash during measurement/draw across frames.
     */
    @Test
    fun compose_tight_constraints_smaller_than_stroke_width_does_not_crash_canvas() {
        composeTestRule.mainClock.autoAdvance = false

        var chartSize by mutableStateOf(8.dp)
        val summary = PeriodCategorySummary(
            totalAmount = 200.0,
            items = listOf(
                CategorySummaryItem("A", "#1CC88A", 100.0, 0.5f, 50),
                CategorySummaryItem("B", "#3B82F6", 100.0, 0.5f, 50)
            )
        )

        composeTestRule.setContent {
            PeriodSummaryDonutChart(
                summary = summary,
                centerLabel = "Total",
                formattedTotal = "$200",
                modifier = Modifier.size(chartSize)
            )
        }

        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        Snapshot.withMutableSnapshot {
            chartSize = 0.dp
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertExists()
    }

    /**
     * Stress & hostile inputs in Compose UI:
     * - Malformed hex colors (`""`, `"#"`, `"#GGGGGG"`, `"not_a_color"`, oversized hex)
     * - Duplicate hex colors across multiple items (exercises `associate` color map cache)
     * - 200 categories drawn simultaneously across `0 ms`, `500 ms`, and `1000 ms`
     * - Extreme Unicode/RTL/emoji/XSS/long strings in `centerLabel` and `formattedTotal`
     */
    @Test
    fun compose_adversarial_strings_malformed_hex_and_200_categories_render_safely() {
        composeTestRule.mainClock.autoAdvance = false

        val hostileColors = listOf(
            "",
            "#",
            "#ZZZ",
            "MALFORMED",
            "#123456789012345",
            "#1CC88A",
            "#1CC88A" // duplicate color hex
        )
        val items = List(200) { idx ->
            CategorySummaryItem(
                categoryName = "🍔_<script>$idx</script>",
                colorHex = hostileColors[idx % hostileColors.size],
                amount = 5.0,
                proportion = 1f / 200f,
                percentage = 0
            )
        }
        val hostileSummary = PeriodCategorySummary(totalAmount = 1000.0, items = items)
        val hostileLabel = "إجمالي 💸 " + "L".repeat(500)
        val hostileTotal = "$999,999,999,999.99\n\t<img src=x>"

        composeTestRule.setContent {
            PeriodSummaryDonutChart(
                summary = hostileSummary,
                centerLabel = hostileLabel,
                formattedTotal = hostileTotal
            )
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_period_summary_donut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_total").assertTextEquals(hostileTotal)
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
    }
}
