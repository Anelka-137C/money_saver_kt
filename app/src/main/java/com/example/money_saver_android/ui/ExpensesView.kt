package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.atom.PeriodSummaryCard
import com.example.money_saver_android.ui.components.expenses.AddEditExpenseDialog
import com.example.money_saver_android.ui.components.expenses.AddExpenseCard
import com.example.money_saver_android.ui.components.expenses.DeleteExpenseCategoryConfirmationDialog
import com.example.money_saver_android.ui.components.expenses.DeleteExpenseConfirmationDialog
import com.example.money_saver_android.ui.components.expenses.EditExpenseCategoryDialog
import com.example.money_saver_android.ui.components.expenses.EditExpenseDialog
import com.example.money_saver_android.ui.components.expenses.ExpenseAllocationCard
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import com.example.money_saver_android.ui.components.expenses.ExpenseEmptyState
import com.example.money_saver_android.ui.components.expenses.ExpensesHeader
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.ExpenseUiState
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expenses tab content integrated inside Contabilidad alongside Ingresos and Presupuesto.
 */
@Composable
fun ExpensesTabContent(
    viewModel: ExpenseViewModel,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var isCategorySheetForEdit by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (message == "Gasto guardado") {
                showAddDialog = false
            }
            viewModel.clearToastMessage()
        }
    }

    LaunchedEffect(state.errorMessage, state.editingExpense?.errorMessage) {
        if (state.errorMessage != null || state.editingExpense?.errorMessage != null) {
            showCategorySheet = false
        }
    }

    val todayFormatted = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
    }
    LaunchedEffect(state.dateInput) {
        if (state.dateInput.isBlank()) {
            viewModel.onDateChange(todayFormatted)
        }
    }

    val categoryExpenses = remember(state.expenses, state.categories) {
        viewModel.calculateCategoryExpenses()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("container_expenses_view"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PeriodSummaryCard(
            title = t("expenses.overview.title"),
            totalSubtitle = t("expenses.overview.total_spent"),
            centerLabel = t("expenses.overview.center_label"),
            emptyMessage = t("expenses.overview.empty_message"),
            summary = state.expenseSummary,
            currency = currency,
            cardTestTag = "card_expense_overview",
            totalTestTag = "text_expense_total_spent"
        )

        ExpenseAllocationCard(
            categoryExpenses = categoryExpenses,
            currency = currency
        )

        val canRegisterExpense = state.hasActiveBudget || state.hasAvailableSavings

        if (!canRegisterExpense) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground)
                    .border(BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("banner_no_active_budget"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = t("expenses.no_budget_banner.title"),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t("expenses.no_budget_banner.subtitle"),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Button(
            onClick = {
                viewModel.clearErrorMessage()
                showAddDialog = true
            },
            enabled = canRegisterExpense,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_open_add_expense"),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenAccent,
                disabledContainerColor = FieldBackground,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (canRegisterExpense) Color.Black else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = t("expenses.add_card.title"),
                color = if (canRegisterExpense) Color.Black else TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = t("expenses.breakdown.title"),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        val breakdownScrollState = rememberScrollState()
        LaunchedEffect(state.selectedPeriodId) {
            breakdownScrollState.scrollTo(0)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(breakdownScrollState)
                .testTag("list_expense_breakdown"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("expenses.history.period_empty_message"),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("text_expenses_empty")
                    )
                }
            } else {
                state.expenses.forEach { expense ->
                    ExpenseCardItem(
                        expense = expense,
                        currency = currency,
                        onEditClick = { viewModel.startEditing(expense) },
                        onDeleteClick = { viewModel.requestDeleteExpense(expense) }
                    )
                }
            }
        }
    }

    if (showAddDialog && !showCategorySheet) {
        AddEditExpenseDialog(
            isEditing = false,
            amount = state.amountInput,
            date = state.dateInput,
            description = state.descriptionInput,
            budgetCategory = state.budgetCategoryInput,
            budgetSubcategory = state.budgetSubcategoryInput,
            category = state.categoryInput,
            subcategory = state.subcategoryInput,
            budgets = state.budgets,
            errorMessage = state.errorMessage,
            currency = currency,
            fundingSource = state.fundingSourceInput,
            savingCategory = state.savingCategoryInput,
            savingsBalances = state.savingsBalances,
            onFundingSourceSelected = viewModel::onFundingSourceSelected,
            onSavingCategorySelected = viewModel::onSavingCategorySelected,
            onAmountChange = viewModel::onAmountChange,
            onDateChange = viewModel::onDateChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onBudgetSelected = viewModel::onBudgetSelected,
            onSelectCategoryClick = {
                isCategorySheetForEdit = false
                showCategorySheet = true
            },
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                viewModel.saveExpense()
            },
            onCloseClick = {
                showAddDialog = false
                viewModel.clearErrorMessage()
            }
        )
    }

    if (!showCategorySheet) {
        state.editingExpense?.let { editState ->
            AddEditExpenseDialog(
                isEditing = true,
                amount = editState.amount,
                date = editState.date,
                description = editState.description,
                budgetCategory = editState.budgetCategory,
                budgetSubcategory = editState.budgetSubcategory,
                category = editState.category,
                subcategory = editState.subcategory,
                budgets = state.budgets,
                errorMessage = editState.errorMessage,
                currency = currency,
                fundingSource = editState.fundingSource,
                savingCategory = editState.savingCategory,
                savingsBalances = state.editSavingsBalances,
                onFundingSourceSelected = viewModel::onEditFundingSourceSelected,
                onSavingCategorySelected = viewModel::onEditSavingCategorySelected,
                onAmountChange = viewModel::onEditAmountChange,
                onDateChange = viewModel::onEditDateChange,
                onDescriptionChange = viewModel::onEditDescriptionChange,
                onBudgetSelected = viewModel::onEditBudgetSelected,
                onSelectCategoryClick = {
                    isCategorySheetForEdit = true
                    showCategorySheet = true
                },
                onSaveClick = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    viewModel.confirmEdit()
                },
                onCloseClick = viewModel::cancelEditing
            )
        }
    }

    if (showCategorySheet) {
        ExpenseCategorySheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showCategorySheet = false },
            onCategorySelected = { category, subcategory ->
                if (isCategorySheetForEdit) {
                    viewModel.onEditCategoryChange(category, subcategory)
                } else {
                    viewModel.onCategoryChange(category, subcategory)
                }
                showCategorySheet = false
            }
        )
    }

    state.expenseToDelete?.let { expenseToDelete ->
        DeleteExpenseConfirmationDialog(
            expense = expenseToDelete,
            onConfirm = viewModel::confirmDeleteExpense,
            onDismiss = viewModel::dismissDeleteConfirmation
        )
    }

    state.editingCategory?.let { editCategoryState ->
        EditExpenseCategoryDialog(
            editState = editCategoryState,
            onNameChange = viewModel::onEditCategoryNameChange,
            onColorSelect = viewModel::onEditCategoryColorChange,
            onConfirm = {
                hideKeyboard(context, view, keyboardController, focusManager)
                viewModel.confirmEditCategory()
            },
            onDismiss = viewModel::cancelEditingCategory
        )
    }

    state.categoryToDelete?.let { categoryToDelete ->
        DeleteExpenseCategoryConfirmationDialog(
            category = categoryToDelete,
            onConfirm = viewModel::confirmDeleteCategory,
            onDismiss = viewModel::dismissDeleteCategoryConfirmation
        )
    }
}

@Composable
private fun ExpenseCategorySheet(
    state: ExpenseUiState,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit,
    onCategorySelected: (String, String) -> Unit
) {
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
        onEditCategory = viewModel::startEditingCategory,
        onDeleteCategory = { viewModel.requestDeleteCategory(it) },
        onDeleteSubcategory = { cat, sub -> viewModel.deleteSubcategory(cat, sub) },
        onClearSubcategoryError = viewModel::clearSubcategoryError,
        onDismiss = {
            onDismiss()
            viewModel.clearCategoryError()
            viewModel.clearSubcategoryError()
        },
        onCategorySelected = onCategorySelected
    )
}

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
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
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
            ExpenseCategorySheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showCategorySheet = false },
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
            ExpenseCategorySheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showEditCategorySheet = false },
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

        state.editingCategory?.let { editCategoryState ->
            EditExpenseCategoryDialog(
                editState = editCategoryState,
                onNameChange = viewModel::onEditCategoryNameChange,
                onColorSelect = viewModel::onEditCategoryColorChange,
                onConfirm = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    viewModel.confirmEditCategory()
                },
                onDismiss = viewModel::cancelEditingCategory
            )
        }

        state.categoryToDelete?.let { categoryToDelete ->
            DeleteExpenseCategoryConfirmationDialog(
                category = categoryToDelete,
                onConfirm = viewModel::confirmDeleteCategory,
                onDismiss = viewModel::dismissDeleteCategoryConfirmation
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
