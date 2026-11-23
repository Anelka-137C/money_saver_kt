package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.usecase.CategorySummaryItem
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.ui.components.atom.PeriodSummaryCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodSummaryCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun displays_real_category_breakdown_without_comparative_badge() {
        val currency = defaultCurrencies.first()
        val summary = PeriodCategorySummary(
            totalAmount = 1000.0,
            items = listOf(
                CategorySummaryItem("Employment", "#1CC88A", 750.0, 0.75f, 75),
                CategorySummaryItem("Investments", "#3B82F6", 250.0, 0.25f, 25)
            )
        )

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Resumen de Ingresos",
                totalSubtitle = "Ingreso total",
                centerLabel = "Total",
                emptyMessage = "Sin ingresos registrados en este período",
                summary = summary,
                currency = currency
            )
        }

        composeTestRule.onNodeWithTag("text_period_summary_total")
            .assertTextEquals(CurrencyFormatter.format(1000.0, currency))
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertTextEquals("100%")
        composeTestRule.onNodeWithTag("legend_amount_Employment")
            .assertTextEquals(CurrencyFormatter.format(750.0, currency))
        composeTestRule.onNodeWithTag("legend_percent_Employment").assertTextEquals("75%")
        composeTestRule.onNodeWithTag("legend_percent_Investments").assertTextEquals("25%")
        composeTestRule.onNodeWithText("+12%", substring = true).assertDoesNotExist()
    }

    @Test
    fun empty_state_displays_zero_total_and_no_percentages_or_simulated_values() {
        val currency = defaultCurrencies.first()

        composeTestRule.setContent {
            PeriodSummaryCard(
                title = "Resumen de Ahorros",
                totalSubtitle = "Total ahorrado",
                centerLabel = "Total",
                emptyMessage = "Sin ahorros registrados en este período",
                summary = PeriodCategorySummary(),
                currency = currency
            )
        }

        composeTestRule.onNodeWithTag("text_period_summary_total")
            .assertTextEquals(CurrencyFormatter.format(0.0, currency))
        composeTestRule.onNodeWithTag("text_period_summary_empty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_summary_center_percent").assertDoesNotExist()
        composeTestRule.onNodeWithText("55%").assertDoesNotExist()
    }
}
