package com.example.money_saver_android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.data.repository.RoomCurrencyRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomFinancialPeriodRepository
import com.example.money_saver_android.data.repository.SharedPrefsCurrencyRepository
import com.example.money_saver_android.data.repository.SharedPrefsLanguageRepository
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.ExpensesView
import com.example.money_saver_android.ui.MainContainer
import com.example.money_saver_android.ui.theme.Money_saver_androidTheme
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.PeriodViewModel
import com.example.money_saver_android.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

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

    private val settingsViewModel by lazy {
        SettingsViewModel(
            languageRepository = languageRepository,
            i18nManager = i18nManager,
            currencyRepository = currencyRepository,
            currencyPreferenceRepository = currencyPreferenceRepository
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
        ExpenseUseCase(repository)
    }

    private val categoryUseCase by lazy {
        CategoryUseCase(categoryRepository)
    }

    private val viewModel by lazy {
        ExpenseViewModel(expenseUseCase, categoryUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Money_saver_androidTheme {
                val settingsState by settingsViewModel.uiState.collectAsState()
                val activeCurrency = settingsState.availableCurrencies.find { it.code == settingsState.selectedCurrency }
                    ?: defaultCurrencies.first()

                MainContainer(
                    settingsViewModel = settingsViewModel,
                    periodViewModel = periodViewModel,
                    i18nManager = i18nManager,
                    expensesContent = {
                        ExpensesView(
                            viewModel = viewModel,
                            currency = activeCurrency
                        )
                    }
                )
            }
        }
    }
}