package com.example.money_saver_android.ui.components.incomes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.FinanceTopTab

@Composable
fun FinanceTopTabs(
    selectedTab: FinanceTopTab,
    onTabSelected: (FinanceTopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .testTag("row_finance_top_tabs"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isIncomes = selectedTab == FinanceTopTab.INCOMES
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isIncomes) FieldBackground else Color.Transparent)
                .clickable { onTabSelected(FinanceTopTab.INCOMES) }
                .testTag("tab_top_incomes"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = t("incomes.tabs.incomes"),
                color = if (isIncomes) GreenAccent else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isIncomes) FontWeight.Bold else FontWeight.Medium
            )
        }

        val isBudget = selectedTab == FinanceTopTab.BUDGET
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isBudget) FieldBackground else Color.Transparent)
                .clickable { onTabSelected(FinanceTopTab.BUDGET) }
                .testTag("tab_top_budget"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = t("incomes.tabs.budget"),
                color = if (isBudget) GreenAccent else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isBudget) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
