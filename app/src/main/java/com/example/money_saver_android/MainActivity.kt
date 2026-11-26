package com.example.money_saver_android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.data.repository.RoomCurrencyRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomFinancialPeriodRepository
import com.example.money_saver_android.data.repository.RoomIncomeRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.data.repository.SharedPrefsAppLockPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsBackupPreferenceRepository
import com.example.money_saver_android.data.repository.SharedPrefsCurrencyRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import com.example.money_saver_android.domain.usecase.BackupUseCase
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.notifications.AndroidBackupReminderScheduler
import com.example.money_saver_android.notifications.BackupReminderReceiver
import com.example.money_saver_android.security.AndroidDeviceAuthCapabilityChecker
import com.example.money_saver_android.security.AppLockController
import com.example.money_saver_android.security.NativeBiometricAuthenticator
import com.example.money_saver_android.ui.AppTab
import com.example.money_saver_android.ui.IncomesView
import com.example.money_saver_android.ui.MainContainer
import com.example.money_saver_android.ui.components.security.AppLockGate
import com.example.money_saver_android.ui.theme.Money_saver_androidTheme
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    companion object {
        private const val KEY_APP_LOCKED = "app_lock_is_locked"
        private const val KEY_APP_LOCK_ERROR = "app_lock_show_error"
        private const val KEY_PROCESS_TOKEN = "app_lock_process_token"
        private val PROCESS_TOKEN: String = java.util.UUID.randomUUID().toString()
    }

    private val i18nManager by lazy {
        I18nManager().apply {
            initFromContext(applicationContext)
        }
    }

    private val languageRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        SharedPrefsLanguageRepository(prefs)
    }

    private val configDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            ConfigDatabase::class.java,
            "config.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    private val currencyRepository by lazy {
        RoomCurrencyRepository(configDatabase.currencyDao())
    }

    private val periodRepository by lazy {
        RoomFinancialPeriodRepository(configDatabase.financialPeriodDao())
    }

    private val periodUseCase by lazy {
        PeriodUseCase(periodRepository)
    }

    private val periodViewModel by lazy {
        PeriodViewModel(periodUseCase)
    }

    private val currencyPreferenceRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        SharedPrefsCurrencyRepository(prefs)
    }

    private val backupPreferenceRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        SharedPrefsBackupPreferenceRepository(prefs)
    }

    private val appLockPreferenceRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        SharedPrefsAppLockPreferenceRepository(prefs)
    }

    private val deviceAuthCapabilityChecker by lazy {
        AndroidDeviceAuthCapabilityChecker(applicationContext)
    }

    private val nativeBiometricAuthenticator by lazy {
        NativeBiometricAuthenticator(
            activity = this,
            titleProvider = { i18nManager.t("app_lock.prompt_title") },
            subtitleProvider = { i18nManager.t("app_lock.prompt_subtitle") }
        )
    }

    private val appLockController by lazy {
        AppLockController(
            appLockPreferenceRepository = appLockPreferenceRepository,
            deviceAuthCapabilityProvider = deviceAuthCapabilityChecker,
            launchDeviceAuth = nativeBiometricAuthenticator::authenticate
        )
    }

    private var hasCompletedInitialStart = false

    private val backupReminderScheduler by lazy {
        AndroidBackupReminderScheduler(applicationContext)
    }

    private val backupUseCase by lazy {
        BackupUseCase(
            periodRepository = periodRepository,
            incomeRepository = incomeRepository,
            budgetRepository = budgetRepository,
            expenseRepository = repository,
            expenseCategoryRepository = categoryRepository,
            savingsRepository = savingsRepository,
            languagePreferenceRepository = languageRepository,
            currencyPreferenceRepository = currencyPreferenceRepository,
            backupPreferenceRepository = backupPreferenceRepository
        )
    }

    private val settingsViewModel by lazy {
        SettingsViewModel(
            languageRepository = languageRepository,
            i18nManager = i18nManager,
            currencyRepository = currencyRepository,
            currencyPreferenceRepository = currencyPreferenceRepository,
            backupUseCase = backupUseCase,
            backupPreferenceRepository = backupPreferenceRepository,
            backupReminderScheduler = backupReminderScheduler,
            appLockPreferenceRepository = appLockPreferenceRepository,
            deviceAuthCapabilityProvider = deviceAuthCapabilityChecker
        )
    }

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "money_saver.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    private val repository by lazy {
        RoomExpenseRepository(database.expenseDao())
    }

    private val categoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao())
    }

    private val expenseUseCase by lazy {
        ExpenseUseCase(repository, budgetRepository, savingsRepository)
    }

    private val categoryUseCase by lazy {
        CategoryUseCase(categoryRepository)
    }

    private val viewModel by lazy {
        ExpenseViewModel(expenseUseCase, categoryUseCase)
    }

    private val incomeRepository by lazy {
        RoomIncomeRepository(database.incomeDao(), database.incomeCategoryDao())
    }

    private val budgetDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            BudgetDatabase::class.java,
            "budgets.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    private val budgetRepository by lazy {
        RoomBudgetRepository(budgetDatabase.budgetDao(), budgetDatabase.budgetCategoryDao())
    }

    private val savingsDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            SavingsDatabase::class.java,
            "savings.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    private val savingsRepository by lazy {
        RoomSavingsRepository(savingsDatabase.savingDao(), savingsDatabase.savingCategoryDao())
    }

    private val savingsUseCase by lazy {
        SavingsUseCase(savingsRepository, incomeRepository, budgetRepository, expenseRepository = repository)
    }

    private val savingsViewModel by lazy {
        SavingsViewModel(savingsUseCase)
    }

    private val incomeUseCase by lazy {
        IncomeUseCase(
            incomeRepository,
            budgetRepository,
            expenseRepository = repository,
            savingsRepository = savingsRepository
        )
    }

    private val incomeViewModel by lazy {
        IncomeViewModel(incomeUseCase)
    }

    private var requestedTab by mutableStateOf<AppTab?>(null)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        appLockController.clearBackgroundLockSuppression()
    }

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        appLockController.clearBackgroundLockSuppression()
        if (uri == null) {
            settingsViewModel.onBackupCancelled()
            return@registerForActivityResult
        }
        settingsViewModel.performBackup { jsonPayload ->
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(jsonPayload)
                } ?: error("Unable to open backup destination")
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    private val openBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        appLockController.clearBackgroundLockSuppression()
        if (uri == null) {
            settingsViewModel.onRestorePickerCancelled()
            return@registerForActivityResult
        }
        settingsViewModel.prepareRestore {
            withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    reader.readText()
                } ?: error("Unable to read backup file")
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            appLockController.suppressNextBackgroundLock()
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun resolveTabFromIntent(intent: Intent?): AppTab? {
        return when (intent?.getStringExtra(BackupReminderReceiver.EXTRA_OPEN_TAB)) {
            BackupReminderReceiver.TAB_SETTINGS -> AppTab.SETTINGS
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveTabFromIntent(intent)?.let { requestedTab = it }
    }

    override fun onStart() {
        super.onStart()
        val state = appLockController.uiState.value
        if (hasCompletedInitialStart || (state.isLocked && !state.isAuthenticating && !state.showAuthError)) {
            appLockController.onAppForegrounded()
        }
        hasCompletedInitialStart = true
    }

    override fun onResume() {
        super.onResume()
        periodViewModel.checkActivePeriod()
        settingsViewModel.refreshDeviceAuthCapability()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            appLockController.onAppBackgrounded()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val lockState = appLockController.uiState.value
        val shouldPersistLocked = lockState.isLocked ||
            (!isChangingConfigurations &&
                !appLockController.isBackgroundLockSuppressed() &&
                appLockPreferenceRepository.isAppLockEnabled())
        outState.putString(KEY_PROCESS_TOKEN, PROCESS_TOKEN)
        outState.putBoolean(KEY_APP_LOCKED, shouldPersistLocked)
        outState.putBoolean(KEY_APP_LOCK_ERROR, lockState.showAuthError)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isSameProcess = savedInstanceState?.getString(KEY_PROCESS_TOKEN) == PROCESS_TOKEN
        if (savedInstanceState == null || !isSameProcess) {
            appLockController.onAppLaunch()
        } else {
            appLockController.restoreLockedState(
                wasLocked = savedInstanceState.getBoolean(KEY_APP_LOCKED, false),
                showAuthError = savedInstanceState.getBoolean(KEY_APP_LOCK_ERROR, false)
            )
        }
        requestedTab = resolveTabFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            Money_saver_androidTheme {
                val lockState by appLockController.uiState.collectAsState()
                val settingsState by settingsViewModel.uiState.collectAsState()
                val activeCurrency = settingsState.availableCurrencies.find { it.code == settingsState.selectedCurrency }
                    ?: defaultCurrencies.first()
                val periodState by periodViewModel.uiState.collectAsState()
                val incomeState by incomeViewModel.uiState.collectAsState()
                val currentSelectedPeriodId = incomeState.selectedPeriodId
                    ?.takeIf { id -> periodState.allPeriods.any { it.id == id } }
                    ?: periodState.activePeriod?.id
                    ?: periodState.allPeriods.firstOrNull()?.id

                AppLockGate(
                    lockState = lockState,
                    i18nManager = i18nManager,
                    onRetry = appLockController::retryAuthentication
                ) {
                    MainContainer(
                        settingsViewModel = settingsViewModel,
                        periodViewModel = periodViewModel,
                        i18nManager = i18nManager,
                        requestedTab = requestedTab,
                        onRequestedTabConsumed = { requestedTab = null },
                        onCreateBackupClick = {
                            settingsViewModel.clearBackupStatus()
                            appLockController.suppressNextBackgroundLock()
                            createBackupLauncher.launch(BackupUseCase.BACKUP_FILE_NAME)
                        },
                        onRestoreBackupClick = {
                            settingsViewModel.clearRestoreStatus()
                            appLockController.suppressNextBackgroundLock()
                            openBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        },
                        onRestoreConfirmed = {
                            periodViewModel.checkActivePeriod()
                        },
                        incomesContent = {
                            IncomesView(
                                incomeViewModel = incomeViewModel,
                                expenseViewModel = viewModel,
                                savingsViewModel = savingsViewModel,
                                allPeriods = periodState.allPeriods,
                                selectedPeriodId = currentSelectedPeriodId,
                                onPeriodSelected = { periodId ->
                                    incomeViewModel.onPeriodSelected(periodId)
                                    viewModel.onPeriodSelected(periodId)
                                    savingsViewModel.onPeriodSelected(periodId)
                                },
                                i18nManager = i18nManager,
                                currency = activeCurrency
                            )
                        }
                    )
                }
            }
        }
    }
}