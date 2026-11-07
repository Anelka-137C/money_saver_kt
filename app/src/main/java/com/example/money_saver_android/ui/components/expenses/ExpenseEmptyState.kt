package com.example.money_saver_android.ui.components.expenses

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun ExpenseEmptyState(
    modifier: Modifier = Modifier
) {
    Text(
        text = "Aún no hay gastos registrados",
        color = TextSecondary,
        fontSize = 14.sp,
        modifier = modifier.padding(vertical = 12.dp)
    )
}
