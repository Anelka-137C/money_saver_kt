package com.example.money_saver_android.ui.components.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.money_saver_android.ui.components.atom.CategoryManagerTestTags
import com.example.money_saver_android.ui.components.atom.EditCategoryDialog
import com.example.money_saver_android.viewmodel.EditExpenseCategoryState

internal val ExpenseCategoryManagerTestTags = CategoryManagerTestTags(
    editDialog = "dialog_edit_expense_category",
    editNameInput = "input_edit_expense_category_name",
    editColorOptionPrefix = "color_option_edit_category_",
    editErrorText = "text_edit_expense_category_error",
    editCancelButton = "btn_cancel_edit_expense_category",
    editSaveButton = "btn_save_edit_expense_category",
    deleteDialog = "dialog_delete_expense_category",
    deleteConfirmButton = "btn_slide_to_confirm_delete_expense_category",
    deleteCancelButton = "btn_cancel_delete_expense_category"
)

@Composable
fun EditExpenseCategoryDialog(
    editState: EditExpenseCategoryState,
    onNameChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    EditCategoryDialog(
        editState = editState,
        onNameChange = onNameChange,
        onColorSelect = onColorSelect,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTags = ExpenseCategoryManagerTestTags,
        modifier = modifier
    )
}
