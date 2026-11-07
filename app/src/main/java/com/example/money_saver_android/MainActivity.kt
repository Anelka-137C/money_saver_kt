package com.example.money_saver_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.ui.ExpenseScreen
import com.example.money_saver_android.ui.theme.Money_saver_androidTheme
import com.example.money_saver_android.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {

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
                ExpenseScreen(viewModel = viewModel)
            }
        }
    }
}