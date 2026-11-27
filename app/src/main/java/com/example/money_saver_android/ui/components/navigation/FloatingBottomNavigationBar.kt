package com.example.money_saver_android.ui.components.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.AppTab
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextSecondary

private val FloatingNavCapsuleShape = RoundedCornerShape(24.dp)
private val FloatingNavItemShape = RoundedCornerShape(18.dp)

private data class BottomNavTabSpec(
    val tab: AppTab,
    val labelKey: String,
    val icon: ImageVector,
    val testTag: String
)

private val BottomNavTabs = listOf(
    BottomNavTabSpec(
        tab = AppTab.INCOMES,
        labelKey = "navigation.tab_incomes",
        icon = Icons.Default.AddCircle,
        testTag = "tab_incomes"
    ),
    BottomNavTabSpec(
        tab = AppTab.LOANS,
        labelKey = "navigation.tab_loans",
        icon = Icons.Default.AccountBox,
        testTag = "tab_loans"
    ),
    BottomNavTabSpec(
        tab = AppTab.SETTINGS,
        labelKey = "navigation.tab_settings",
        icon = Icons.Default.Settings,
        testTag = "tab_settings"
    ),
    BottomNavTabSpec(
        tab = AppTab.PERIOD,
        labelKey = "navigation.tab_period",
        icon = Icons.Default.DateRange,
        testTag = "tab_period"
    )
)

/**
 * Floating capsule bottom navigation bar with an inner pill highlight for the active tab.
 */
@Composable
fun FloatingBottomNavigationBar(
    activeTab: AppTab,
    isPeriodLocked: Boolean,
    i18nManager: I18nManager,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("floating_bottom_nav")
                .shadow(
                    elevation = 16.dp,
                    shape = FloatingNavCapsuleShape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(FloatingNavCapsuleShape)
                .background(CardBackground)
                .border(1.dp, BorderColor, FloatingNavCapsuleShape)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTabs.forEach { spec ->
                val isSelected = activeTab == spec.tab
                val isLocked = isPeriodLocked && spec.tab == AppTab.INCOMES

                FloatingBottomNavItem(
                    label = i18nManager.t(spec.labelKey),
                    icon = spec.icon,
                    isSelected = isSelected,
                    isLocked = isLocked,
                    testTag = spec.testTag,
                    onClick = { onTabSelected(spec.tab) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isLocked: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) GreenAccent.copy(alpha = 0.16f) else Color.Transparent,
        label = "floating_nav_pill_$testTag"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) GreenAccent else TextSecondary,
        label = "floating_nav_content_$testTag"
    )

    Column(
        modifier = modifier
            .testTag(testTag)
            .alpha(if (isLocked) 0.4f else 1f)
            .clip(FloatingNavItemShape)
            .background(pillColor)
            .selectable(
                selected = isSelected,
                enabled = !isLocked,
                role = Role.Tab,
                onClick = {
                    if (!isLocked) {
                        onClick()
                    }
                }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
