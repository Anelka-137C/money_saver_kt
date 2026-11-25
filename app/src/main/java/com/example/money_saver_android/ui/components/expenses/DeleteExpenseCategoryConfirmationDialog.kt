package com.example.money_saver_android.ui.components.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.atom.DeleteCategoryConfirmationDialog

@Composable
fun DeleteExpenseCategoryConfirmationDialog(
    category: Category,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DeleteCategoryConfirmationDialog(
        category = category,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = t("expenses.delete_category_dialog.title"),
        warningMessage = t("expenses.delete_category_dialog.warning_message"),
        cancelLabel = t("expenses.delete_category_dialog.cancel_button"),
        testTags = ExpenseCategoryManagerTestTags,
        modifier = modifier
    )
}
