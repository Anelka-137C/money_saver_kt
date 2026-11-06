package com.example.money_saver_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import com.example.money_saver_android.data.AppDatabase
import com.example.money_saver_android.data.RoomExpenseRepository
import com.example.money_saver_android.ui.ExpenseScreen
import com.example.money_saver_android.ui.ExpenseViewModel
import com.example.money_saver_android.ui.theme.Money_saver_androidTheme

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "money_saver.db"
        ).build()
    }

    private val repository by lazy {
        RoomExpenseRepository(database.expenseDao())
    }

    private val viewModel by lazy {
        ExpenseViewModel(repository)
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