package com.example.money_saver_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.components.navigation.FloatingBottomNavigationBar
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel

enum class AppTab {
    INCOMES,
    LOANS,
    SETTINGS,
    PERIOD
}

@Composable
fun MainContainer(
    settingsViewModel: SettingsViewModel,
    i18nManager: I18nManager,
    modifier: Modifier = Modifier,
    periodViewModel: PeriodViewModel? = null,
    requestedTab: AppTab? = null,
    onRequestedTabConsumed: () -> Unit = {},
    onCreateBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {},
    onRestoreConfirmed: () -> Unit = {},
    incomesContent: (@Composable () -> Unit)? = null,
    loansContent: (@Composable () -> Unit)? = null
) {
    // Observe language changes to re-render texts in real-time
    val settingsState by settingsViewModel.uiState.collectAsState()
    val periodState = periodViewModel?.uiState?.collectAsState()?.value

    if (periodViewModel != null && periodState?.isInitialCheckDone != true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        )
        return
    }

    val isPeriodLocked = periodState?.let { !it.isPeriodConfigured } ?: false

    var currentTab by rememberSaveable {
        mutableStateOf(
            when {
                requestedTab == AppTab.SETTINGS -> AppTab.SETTINGS
                requestedTab == AppTab.LOANS -> AppTab.LOANS
                isPeriodLocked -> AppTab.PERIOD
                else -> requestedTab ?: AppTab.INCOMES
            }
        )
    }

    LaunchedEffect(requestedTab, isPeriodLocked) {
        if (requestedTab != null && (!isPeriodLocked || requestedTab != AppTab.INCOMES)) {
            currentTab = requestedTab
            onRequestedTabConsumed()
        }
    }

    val activeTab = if (isPeriodLocked && currentTab == AppTab.INCOMES) AppTab.PERIOD else currentTab

    CompositionLocalProvider(LocalI18nManager provides i18nManager) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = DarkBackground,
            bottomBar = {
                FloatingBottomNavigationBar(
                    activeTab = activeTab,
                    isPeriodLocked = isPeriodLocked,
                    i18nManager = i18nManager,
                    onTabSelected = { tab ->
                        if (!isPeriodLocked || tab != AppTab.INCOMES) {
                            currentTab = tab
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    AppTab.INCOMES -> incomesContent?.invoke()
                    AppTab.LOANS -> loansContent?.invoke()
                    AppTab.SETTINGS -> SettingsView(
                        settingsViewModel = settingsViewModel,
                        i18nManager = i18nManager,
                        onCreateBackupClick = onCreateBackupClick,
                        onRestoreBackupClick = onRestoreBackupClick,
                        onRestoreConfirmed = onRestoreConfirmed
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
