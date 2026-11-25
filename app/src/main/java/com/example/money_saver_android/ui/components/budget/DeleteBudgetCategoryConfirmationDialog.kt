package com.example.money_saver_android.ui.components.budget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.atom.CategoryManagerTestTags
import com.example.money_saver_android.ui.components.atom.DeleteCategoryConfirmationDialog

internal val BudgetCategoryManagerTestTags = CategoryManagerTestTags(
    editDialog = "dialog_edit_budget_category",
    editNameInput = "input_edit_budget_category_name",
    editColorOptionPrefix = "color_option_edit_category_",
    editErrorText = "text_edit_budget_category_error",
    editCancelButton = "btn_cancel_edit_budget_category",
    editSaveButton = "btn_save_edit_budget_category",
    deleteDialog = "dialog_delete_budget_category",
    deleteConfirmButton = "btn_slide_to_confirm_delete_budget_category",
    deleteCancelButton = "btn_cancel_delete_budget_category"
)

@Composable
fun DeleteBudgetCategoryConfirmationDialog(
    category: Category,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DeleteCategoryConfirmationDialog(
        category = category,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = t("budget.delete_category_dialog.title"),
        warningMessage = t("budget.delete_category_dialog.warning_message"),
        cancelLabel = t("budget.delete_category_dialog.cancel_button"),
        testTags = BudgetCategoryManagerTestTags,
        modifier = modifier
    )
}
