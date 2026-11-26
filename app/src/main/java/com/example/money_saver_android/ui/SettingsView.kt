package com.example.money_saver_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.components.settings.BackupSectionCard
import com.example.money_saver_android.ui.components.settings.CurrencySectionCard
import com.example.money_saver_android.ui.components.settings.LanguageSectionCard
import com.example.money_saver_android.ui.components.settings.RestoreBackupConfirmationDialog
import com.example.money_saver_android.ui.components.settings.SettingsHeader
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.viewmodel.SettingsViewModel

@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    i18nManager: I18nManager,
    onCreateBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {},
    onRestoreConfirmed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by settingsViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(
            title = i18nManager.t("settings.header.title")
        )

        LanguageSectionCard(
            title = i18nManager.t("settings.language_section.title"),
            description = i18nManager.t("settings.language_section.description"),
            availableLanguages = state.availableLanguages,
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = settingsViewModel::onLanguageSelected
        )

        CurrencySectionCard(
            title = i18nManager.t("settings.currency_section.title"),
            description = i18nManager.t("settings.currency_section.description"),
            availableCurrencies = state.availableCurrencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = settingsViewModel::onCurrencySelected
        )

        BackupSectionCard(
            title = i18nManager.tOrFallback("settings.backup_section.title", "Respaldo en Google Drive"),
            description = i18nManager.tOrFallback(
                "settings.backup_section.description",
                "Guarda una copia de tus datos financieros (money_saver_backup) en la carpeta de Google Drive que elijas."
            ),
            lastBackupLabel = i18nManager.tOrFallback("settings.backup_section.last_backup_label", "Último respaldo"),
            neverBackedUpText = i18nManager.tOrFallback("settings.backup_section.never", "Sin respaldos"),
            actionButtonLabel = i18nManager.tOrFallback("settings.backup_section.action_button", "Respaldar en Google Drive"),
            inProgressLabel = i18nManager.tOrFallback("settings.backup_section.in_progress", "Generando respaldo..."),
            successMessage = i18nManager.tOrFallback("settings.backup_section.success", "Respaldo guardado correctamente"),
            errorMessage = i18nManager.tOrFallback("settings.backup_section.error", "No se pudo guardar el respaldo"),
            cancelledMessage = i18nManager.tOrFallback("settings.backup_section.cancelled", "Respaldo cancelado"),
            lastBackupTimestamp = state.lastBackupTimestamp,
            backupStatus = state.backupStatus,
            onCreateBackupClick = onCreateBackupClick,
            restoreButtonLabel = i18nManager.tOrFallback(
                "settings.backup_section.restore_button",
                "Restaurar desde Google Drive"
            ),
            restoreInProgressLabel = i18nManager.tOrFallback(
                "settings.backup_section.restore_in_progress",
                "Restaurando respaldo..."
            ),
            restoreSuccessMessage = i18nManager.tOrFallback(
                "settings.backup_section.restore_success",
                "Datos restaurados correctamente"
            ),
            restoreInvalidFileMessage = i18nManager.tOrFallback(
                "settings.backup_section.restore_invalid_file",
                "El archivo seleccionado no es un respaldo válido"
            ),
            restoreErrorMessage = i18nManager.tOrFallback(
                "settings.backup_section.restore_error",
                "No se pudieron restaurar los datos"
            ),
            restoreCancelledMessage = i18nManager.tOrFallback(
                "settings.backup_section.restore_cancelled",
                "Restauración cancelada"
            ),
            restoreStatus = state.restoreStatus,
            onRestoreBackupClick = onRestoreBackupClick
        )
    }

    state.pendingRestoreSnapshot?.let { snapshot ->
        val summaryTemplate = i18nManager.tOrFallback(
            "settings.backup_section.restore_dialog_summary",
            "%d períodos · %d ingresos · %d presupuestos · %d gastos · %d ahorros"
        )
        val summaryText = try {
            String.format(
                summaryTemplate,
                snapshot.periods.size,
                snapshot.incomes.size,
                snapshot.budgets.size,
                snapshot.expenses.size,
                snapshot.savings.size
            )
        } catch (_: Exception) {
            summaryTemplate
        }
        RestoreBackupConfirmationDialog(
            snapshot = snapshot,
            title = i18nManager.tOrFallback(
                "settings.backup_section.restore_dialog_title",
                "¿Restaurar este respaldo?"
            ),
            warningMessage = i18nManager.tOrFallback(
                "settings.backup_section.restore_dialog_warning",
                "Esta acción reemplazará todos tus datos locales actuales. Desliza el botón para confirmar."
            ),
            dateLabel = i18nManager.tOrFallback(
                "settings.backup_section.restore_dialog_date_label",
                "Fecha del respaldo"
            ),
            summaryText = summaryText,
            cancelLabel = i18nManager.tOrFallback(
                "settings.backup_section.restore_dialog_cancel",
                "Cancelar"
            ),
            onConfirm = { settingsViewModel.confirmRestore(onRestored = onRestoreConfirmed) },
            onDismiss = settingsViewModel::cancelPendingRestore
        )
    }
}

private fun I18nManager.tOrFallback(key: String, fallback: String): String {
    val resolved = t(key)
    return if (resolved == key) fallback else resolved
}
