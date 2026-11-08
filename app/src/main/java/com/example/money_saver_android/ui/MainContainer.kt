package com.example.money_saver_android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel

enum class AppTab {
    EXPENSES,
    INCOMES,
    SETTINGS,
    PERIOD
}

@Composable
fun MainContainer(
    settingsViewModel: SettingsViewModel,
    i18nManager: I18nManager,
    modifier: Modifier = Modifier,
    periodViewModel: PeriodViewModel? = null,
    incomesContent: (@Composable () -> Unit)? = null,
    expensesContent: @Composable () -> Unit
) {
    // Observe language changes to re-render texts in real-time
    val settingsState by settingsViewModel.uiState.collectAsState()
    val periodState = periodViewModel?.uiState?.collectAsState()?.value
    val isPeriodLocked = periodState?.let { !it.isPeriodConfigured } ?: false

    var currentTab by rememberSaveable {
        mutableStateOf(if (isPeriodLocked) AppTab.PERIOD else AppTab.EXPENSES)
    }

    val activeTab = if (isPeriodLocked) AppTab.PERIOD else currentTab

    CompositionLocalProvider(LocalI18nManager provides i18nManager) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(
                    containerColor = CardBackground,
                    contentColor = TextPrimary
                ) {
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_expenses"),
                        selected = activeTab == AppTab.EXPENSES,
                        onClick = {
                            if (!isPeriodLocked) {
                                currentTab = AppTab.EXPENSES
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = i18nManager.t("navigation.tab_expenses")) },
                        label = { Text(i18nManager.t("navigation.tab_expenses")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenAccent,
                            selectedTextColor = GreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = CardBackground
                        )
                    )
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_incomes"),
                        selected = activeTab == AppTab.INCOMES,
                        onClick = {
                            if (!isPeriodLocked) {
                                currentTab = AppTab.INCOMES
                            }
                        },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = i18nManager.t("navigation.tab_incomes")) },
                        label = { Text(i18nManager.t("navigation.tab_incomes")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenAccent,
                            selectedTextColor = GreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = CardBackground
                        )
                    )
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_settings"),
                        selected = activeTab == AppTab.SETTINGS,
                        onClick = {
                            if (!isPeriodLocked) {
                                currentTab = AppTab.SETTINGS
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = i18nManager.t("navigation.tab_settings")) },
                        label = { Text(i18nManager.t("navigation.tab_settings")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenAccent,
                            selectedTextColor = GreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = CardBackground
                        )
                    )
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_period"),
                        selected = activeTab == AppTab.PERIOD,
                        onClick = { currentTab = AppTab.PERIOD },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = i18nManager.t("navigation.tab_period")) },
                        label = { Text(i18nManager.t("navigation.tab_period")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenAccent,
                            selectedTextColor = GreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = CardBackground
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    AppTab.EXPENSES -> expensesContent()
                    AppTab.INCOMES -> incomesContent?.invoke()
                    AppTab.SETTINGS -> SettingsView(
                        settingsViewModel = settingsViewModel,
                        i18nManager = i18nManager
                    )
                    AppTab.PERIOD -> {
                        periodViewModel?.let {
                            PeriodView(
                                periodViewModel = it,
                                i18nManager = i18nManager
                            )
                        }
                    }
                }
            }
        }
    }
}
