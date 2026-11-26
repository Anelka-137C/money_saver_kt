package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.repository.BackupPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyPreferenceRepository
import com.example.money_saver_android.data.repository.CurrencyRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import com.example.money_saver_android.domain.usecase.BackupResult
import com.example.money_saver_android.domain.usecase.BackupSnapshot
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.domain.usecase.RestoreParseResult
import com.example.money_saver_android.domain.usecase.RestoreResult
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.notifications.BackupReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LanguageOption(
    val code: String,
    val nativeName: String
)

enum class BackupStatus {
    IDLE,
    IN_PROGRESS,
    SUCCESS,
    ERROR,
    CANCELLED
}

enum class RestoreStatus {
    IDLE,
    READING,
    RESTORING,
    SUCCESS,
    INVALID_FILE,
    ERROR,
    CANCELLED
}

data class SettingsUiState(
    val selectedLanguage: String = "es",
    val availableLanguages: List<LanguageOption> = listOf(
        LanguageOption("es", "Español"),
        LanguageOption("en", "English"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("pt", "Português (Brasil)")
    ),
    val selectedCurrency: String = "COP",
    val availableCurrencies: List<Currency> = emptyList(),
    val lastBackupTimestamp: Long? = null,
    val backupStatus: BackupStatus = BackupStatus.IDLE,
    val restoreStatus: RestoreStatus = RestoreStatus.IDLE,
    val pendingRestoreSnapshot: BackupSnapshot? = null
)

class SettingsViewModel(
    private val languageRepository: LanguagePreferenceRepository,
    private val i18nManager: I18nManager,
    private val currencyRepository: CurrencyRepository? = null,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository? = null,
    private val backupUseCase: BackupUseCase? = null,
    private val backupPreferenceRepository: BackupPreferenceRepository? = null,
    private val backupReminderScheduler: BackupReminderScheduler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var activeRestoreToken: Long = 0L

    init {
        val initialLanguage = languageRepository.getLanguage()
        i18nManager.setLanguage(initialLanguage)
        val initialCurrency = currencyPreferenceRepository?.getCurrency() ?: "COP"
        val initialLastBackup = backupUseCase?.getLastBackupTimestamp()
            ?: backupPreferenceRepository?.getLastBackupTimestamp()
        _uiState.update {
            it.copy(
                selectedLanguage = initialLanguage,
                selectedCurrency = initialCurrency,
                lastBackupTimestamp = initialLastBackup
            )
        }
        if (initialLastBackup != null) {
            backupReminderScheduler?.scheduleNextReminder(initialLastBackup)
        }

        currencyRepository?.let { repo ->
            viewModelScope.launch {
                repo.preloadDefaultCurrenciesIfNeeded()
                repo.getAllCurrencies().collect { currencies ->
                    _uiState.update { it.copy(availableCurrencies = currencies) }
                }
            }
        }
    }

    fun onLanguageSelected(languageCode: String) {
        languageRepository.saveLanguage(languageCode)
        i18nManager.setLanguage(languageCode)
        _uiState.update { it.copy(selectedLanguage = languageCode) }
    }

    fun onCurrencySelected(currencyCode: String) {
        currencyPreferenceRepository?.saveCurrency(currencyCode)
        _uiState.update { it.copy(selectedCurrency = currencyCode) }
    }

    private fun isBackupOrRestoreBusy(): Boolean {
        val state = _uiState.value
        return state.backupStatus == BackupStatus.IN_PROGRESS ||
            state.restoreStatus == RestoreStatus.READING ||
            state.restoreStatus == RestoreStatus.RESTORING
    }

    fun performBackup(writeContent: suspend (String) -> Unit) {
        if (isBackupOrRestoreBusy()) return
        val useCase = backupUseCase
        if (useCase == null) {
            _uiState.update { it.copy(backupStatus = BackupStatus.ERROR) }
            return
        }
        _uiState.update { it.copy(backupStatus = BackupStatus.IN_PROGRESS) }
        viewModelScope.launch {
            when (val result = useCase.exportBackup(writeContent)) {
                is BackupResult.Success -> {
                    try {
                        backupReminderScheduler?.scheduleNextReminder(result.timestampMillis)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        // Do not mask a completed backup or leave UI stuck in IN_PROGRESS if scheduling fails
                    }
                    _uiState.update {
                        it.copy(
                            lastBackupTimestamp = result.timestampMillis,
                            backupStatus = BackupStatus.SUCCESS
                        )
                    }
                }
                is BackupResult.Error -> {
                    _uiState.update { it.copy(backupStatus = BackupStatus.ERROR) }
                }
            }
        }
    }

    fun onBackupCancelled() {
        if (_uiState.value.backupStatus == BackupStatus.IN_PROGRESS) return
        _uiState.update { it.copy(backupStatus = BackupStatus.CANCELLED) }
    }

    fun clearBackupStatus() {
        if (_uiState.value.backupStatus == BackupStatus.IN_PROGRESS) return
        _uiState.update { it.copy(backupStatus = BackupStatus.IDLE) }
    }

    fun prepareRestore(readContent: suspend () -> String) {
        if (isBackupOrRestoreBusy()) return
        val useCase = backupUseCase
        if (useCase == null) {
            _uiState.update { it.copy(restoreStatus = RestoreStatus.ERROR) }
            return
        }
        val token = ++activeRestoreToken
        _uiState.update { it.copy(restoreStatus = RestoreStatus.READING, pendingRestoreSnapshot = null) }
        viewModelScope.launch {
            val rawJson = try {
                readContent()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (token == activeRestoreToken) {
                    _uiState.update { it.copy(restoreStatus = RestoreStatus.ERROR) }
                }
                return@launch
            }
            if (token != activeRestoreToken) return@launch
            when (val parsed = useCase.parseBackupJson(rawJson)) {
                is RestoreParseResult.Valid -> {
                    if (token != activeRestoreToken) return@launch
                    _uiState.update {
                        it.copy(
                            restoreStatus = RestoreStatus.IDLE,
                            pendingRestoreSnapshot = parsed.snapshot
                        )
                    }
                }
                is RestoreParseResult.Invalid -> {
                    if (token != activeRestoreToken) return@launch
                    _uiState.update {
                        it.copy(
                            restoreStatus = RestoreStatus.INVALID_FILE,
                            pendingRestoreSnapshot = null
                        )
                    }
                }
            }
        }
    }

    fun cancelPendingRestore() {
        if (_uiState.value.restoreStatus == RestoreStatus.RESTORING) return
        activeRestoreToken++
        _uiState.update {
            it.copy(
                pendingRestoreSnapshot = null,
                restoreStatus = RestoreStatus.CANCELLED
            )
        }
    }

    fun onRestorePickerCancelled() {
        if (_uiState.value.restoreStatus == RestoreStatus.READING ||
            _uiState.value.restoreStatus == RestoreStatus.RESTORING
        ) return
        _uiState.update { it.copy(restoreStatus = RestoreStatus.CANCELLED) }
    }

    fun confirmRestore(onRestored: () -> Unit = {}) {
        if (isBackupOrRestoreBusy()) return
        val snapshot = _uiState.value.pendingRestoreSnapshot ?: return
        val useCase = backupUseCase
        if (useCase == null) {
            _uiState.update { it.copy(pendingRestoreSnapshot = null, restoreStatus = RestoreStatus.ERROR) }
            return
        }
        _uiState.update { it.copy(pendingRestoreSnapshot = null, restoreStatus = RestoreStatus.RESTORING) }
        viewModelScope.launch {
            when (val result = useCase.restoreBackup(snapshot)) {
                is RestoreResult.Success -> {
                    val restoredLanguage = result.snapshot.language.takeIf {
                        it in SharedPrefsLanguageRepository.SUPPORTED_LANGUAGES
                    }
                    if (restoredLanguage != null) {
                        i18nManager.setLanguage(restoredLanguage)
                    }
                    val supportedCurrencies = (
                        _uiState.value.availableCurrencies.map { it.code } +
                            defaultCurrencies.map { it.code }
                        ).toSet()
                    val restoredCurrency = result.snapshot.currency.takeIf { it in supportedCurrencies }
                    _uiState.update { current ->
                        current.copy(
                            selectedLanguage = restoredLanguage ?: current.selectedLanguage,
                            selectedCurrency = restoredCurrency ?: current.selectedCurrency,
                            restoreStatus = RestoreStatus.SUCCESS
                        )
                    }
                    onRestored()
                }
                is RestoreResult.Error -> {
                    _uiState.update { it.copy(restoreStatus = RestoreStatus.ERROR) }
                }
            }
        }
    }

    fun clearRestoreStatus() {
        if (_uiState.value.restoreStatus == RestoreStatus.READING ||
            _uiState.value.restoreStatus == RestoreStatus.RESTORING
        ) return
        _uiState.update { it.copy(restoreStatus = RestoreStatus.IDLE) }
    }
}
