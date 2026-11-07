package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.expenses.AddExpenseCard
import com.example.money_saver_android.ui.components.expenses.DeleteExpenseConfirmationDialog
import com.example.money_saver_android.ui.components.expenses.EditExpenseDialog
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import com.example.money_saver_android.ui.components.expenses.ExpenseEmptyState
import com.example.money_saver_android.ui.components.expenses.ExpensesHeader
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpensesView(
    viewModel: ExpenseViewModel,
    currency: Currency = defaultCurrencies.first()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showEditCategorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    // Inicializar fecha de hoy por defecto si está vacía
    val todayFormatted = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    if (state.dateInput.isBlank()) {
        viewModel.onDateChange(todayFormatted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ExpensesHeader(title = t("expenses.header.title"))
            }

            // Formulario "Add Expense"
            item {
                AddExpenseCard(
                    state = state,
                    currency = currency,
                    onAmountChange = viewModel::onAmountChange,
                    onDateChange = viewModel::onDateChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onSelectCategoryClick = { showCategorySheet = true },
                    onSaveClick = {
                        hideKeyboard(context, view, keyboardController, focusManager)
                        viewModel.saveExpense()
                    }
                )
            }

            // Lista de gastos
            item {
                Text(
                    text = t("expenses.history.title"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.expenses.isEmpty()) {
                item {
                    ExpenseEmptyState()
                }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseCardItem(
                        expense = expense,
                        currency = currency,
                        onEditClick = { viewModel.startEditing(expense) },
                        onDeleteClick = { viewModel.requestDeleteExpense(expense) }
                    )
                }
            }
        }

        if (showCategorySheet) {
            CategorySelectionSheet(
                categories = state.categories,
                newCategoryName = state.newCategoryNameInput,
                selectedColorHex = state.newCategoryColorInput,
                categoryError = state.categoryErrorMessage,
                newSubcategoryName = state.newSubcategoryNameInput,
                subcategoryError = state.subcategoryErrorMessage,
                onCategoryNameChange = viewModel::onNewCategoryNameChange,
                onColorSelect = viewModel::onNewCategoryColorChange,
                onCreateCategory = viewModel::createCategory,
                onSubcategoryNameChange = viewModel::onNewSubcategoryNameChange,
                onCreateSubcategory = viewModel::createSubcategory,
                onDismiss = {
                    showCategorySheet = false
                    viewModel.clearCategoryError()
                    viewModel.clearSubcategoryError()
                },
                onCategorySelected = { category, subcategory ->
                    viewModel.onCategoryChange(category, subcategory)
                    showCategorySheet = false
                }
            )
        }

        val editState = state.editingExpense
        if (editState != null) {
            EditExpenseDialog(
                editState = editState,
                currency = currency,
                onAmountChange = viewModel::onEditAmountChange,
                onDateChange = viewModel::onEditDateChange,
                onDescriptionChange = viewModel::onEditDescriptionChange,
                onSelectCategoryClick = { showEditCategorySheet = true },
                onConfirm = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    viewModel.confirmEdit()
                },
                onDismiss = viewModel::cancelEditing
            )
        }

        if (showEditCategorySheet) {
            CategorySelectionSheet(
                categories = state.categories,
                newCategoryName = state.newCategoryNameInput,
                selectedColorHex = state.newCategoryColorInput,
                categoryError = state.categoryErrorMessage,
                newSubcategoryName = state.newSubcategoryNameInput,
                subcategoryError = state.subcategoryErrorMessage,
                onCategoryNameChange = viewModel::onNewCategoryNameChange,
                onColorSelect = viewModel::onNewCategoryColorChange,
                onCreateCategory = viewModel::createCategory,
                onSubcategoryNameChange = viewModel::onNewSubcategoryNameChange,
                onCreateSubcategory = viewModel::createSubcategory,
                onDismiss = {
                    showEditCategorySheet = false
                    viewModel.clearCategoryError()
                    viewModel.clearSubcategoryError()
                },
                onCategorySelected = { category, subcategory ->
                    viewModel.onEditCategoryChange(category, subcategory)
                    showEditCategorySheet = false
                }
            )
        }

        val expenseToDelete = state.expenseToDelete
        if (expenseToDelete != null) {
            DeleteExpenseConfirmationDialog(
                expense = expenseToDelete,
                onConfirm = viewModel::confirmDeleteExpense,
                onDismiss = viewModel::dismissDeleteConfirmation
            )
        }
    }
}

/**
 * Compatibility alias for callers still referencing ExpenseScreen.
 */
@Deprecated(
    message = "Use ExpensesView instead",
    replaceWith = ReplaceWith("ExpensesView(viewModel)")
)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel) {
    ExpensesView(viewModel = viewModel)
}
