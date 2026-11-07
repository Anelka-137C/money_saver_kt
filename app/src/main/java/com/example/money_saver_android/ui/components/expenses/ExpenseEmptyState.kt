package com.example.money_saver_android.ui.components.expenses

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.TextSecondary

@Composable
fun ExpenseEmptyState(
    modifier: Modifier = Modifier,
    message: String = t("expenses.history.empty_message")
) {
    Text(
        text = message,
        color = TextSecondary,
        fontSize = 14.sp,
        modifier = modifier.padding(vertical = 12.dp)
    )
}
