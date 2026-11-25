package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.atom.CategoryManagerSheet
import com.example.money_saver_android.ui.components.atom.CategoryManagerTestTags
import com.example.money_saver_android.ui.components.atom.PeriodSummaryCard
import com.example.money_saver_android.ui.components.budget.AddEditBudgetDialog
import com.example.money_saver_android.ui.components.budget.BudgetAllocationCard
import com.example.money_saver_android.ui.components.budget.BudgetCategoryManagerTestTags
import com.example.money_saver_android.ui.components.budget.DeleteBudgetConfirmationDialog
import com.example.money_saver_android.ui.components.incomes.AddIncomeCard
import com.example.money_saver_android.ui.components.incomes.AddIncomeDialog
import com.example.money_saver_android.ui.components.incomes.DeleteIncomeConfirmationDialog
import com.example.money_saver_android.ui.components.incomes.EditIncomeDialog
import com.example.money_saver_android.ui.components.incomes.FinanceTopTabs
import com.example.money_saver_android.ui.components.incomes.IncomeAllocationCard
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.FinanceTopTab
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun IncomesView(
    incomeViewModel: IncomeViewModel,
    expenseViewModel: ExpenseViewModel? = null,
    savingsViewModel: SavingsViewModel? = null,
    allPeriods: List<FinancialPeriod> = emptyList(),
    selectedPeriodId: Long? = null,
    onPeriodSelected: (Long) -> Unit = {},
    i18nManager: I18nManager? = null,
    currency: Currency = defaultCurrencies.first()
) {
    val state by incomeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var periodMenuExpanded by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var showIncomeCategorySheet by remember { mutableStateOf(false) }
    var isIncomeCategorySheetForEdit by remember { mutableStateOf(false) }

    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var showBudgetCategorySheet by remember { mutableStateOf(false) }
    var isBudgetCategorySheetForEdit by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (message == IncomeViewModel.MSG_INCOME_SAVED) {
                showAddForm = false
            }
            incomeViewModel.clearToastMessage()
        }
    }

    LaunchedEffect(state.budgetToastMessage) {
        state.budgetToastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (message == IncomeViewModel.MSG_BUDGET_SAVED) {
                showAddBudgetDialog = false
            }
            incomeViewModel.clearBudgetToastMessage()
        }
    }

    LaunchedEffect(state.errorMessage, state.editingIncome?.errorMessage) {
        if (state.errorMessage != null || state.editingIncome?.errorMessage != null) {
            showIncomeCategorySheet = false
        }
    }

    LaunchedEffect(state.budgetErrorMessage, state.editingBudget?.errorMessage) {
        if (state.budgetErrorMessage != null || state.editingBudget?.errorMessage != null) {
            showBudgetCategorySheet = false
        }
    }

    LaunchedEffect(selectedPeriodId) {
        if (selectedPeriodId != null && state.selectedPeriodId != selectedPeriodId) {
            incomeViewModel.onPeriodSelected(selectedPeriodId)
        }
    }

    LaunchedEffect(state.selectedPeriodId) {
        expenseViewModel?.onPeriodSelected(state.selectedPeriodId)
        savingsViewModel?.onPeriodSelected(state.selectedPeriodId)
    }

    val effectiveSelectedPeriodId = state.selectedPeriodId ?: selectedPeriodId
    val activePeriod = allPeriods.find { it.id == effectiveSelectedPeriodId } ?: allPeriods.firstOrNull()
    val currentLang = LocalI18nManager.current.currentLanguage
    val selectPeriodFallback = t("incomes.header.select_period")
    val periodLabel = remember(activePeriod, currentLang, selectPeriodFallback) {
        activePeriod?.let {
            formatPeriodRange(it.startDate, it.endDate, currentLang)
        } ?: selectPeriodFallback
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 0. Top Navigation Tabs (Ingresos / Presupuesto)
            FinanceTopTabs(
                selectedTab = state.selectedTopTab,
                onTabSelected = incomeViewModel::onTopTabSelected
            )

            // 1. Period Selector Header
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .clickable { periodMenuExpanded = true }
                        .testTag("selector_period")
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = periodLabel,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = t("incomes.header.select_period"),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false },
                    modifier = Modifier
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                ) {
                    allPeriods.forEach { period ->
                        val isSelected = period.id == effectiveSelectedPeriodId
                        val optionRangeLabel = formatPeriodRange(period.startDate, period.endDate, currentLang)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = optionRangeLabel,
                                    color = if (isSelected) GreenAccent else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                onPeriodSelected(period.id)
                                periodMenuExpanded = false
                            },
                            modifier = Modifier.testTag("period_option_${period.id}")
                        )
                    }
                }
            }

            when (state.selectedTopTab) {
                FinanceTopTab.INCOMES -> {
                    // 2. Income Summary Card
                    PeriodSummaryCard(
                        title = t("incomes.overview.title"),
                        totalSubtitle = t("incomes.overview.total_income"),
                        centerLabel = t("incomes.overview.center_label"),
                        emptyMessage = t("incomes.overview.empty_message"),
                        summary = state.incomeSummary,
                        currency = currency,
                        cardTestTag = "card_income_overview",
                        totalTestTag = "text_total_income"
                    )

                    // 3. Income Allocation Card
                    IncomeAllocationCard(
                        totalIncome = state.totalIncome,
                        totalBudgeted = state.totalBudgeted,
                        totalSaved = state.totalSaved,
                        currency = currency
                    )

                    // 4. Add Income Action Button
                    Button(
                        onClick = { showAddForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_toggle_add_income"),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t("incomes.header.add_button"),
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 5. Income Breakdown Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("incomes.breakdown.title"),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Incomes List
                    val historyScrollState = rememberScrollState()
                    LaunchedEffect(state.selectedPeriodId) {
                        historyScrollState.scrollTo(0)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(historyScrollState)
                            .testTag("list_income_breakdown"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.incomes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CardBackground)
                                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = t("incomes.history.empty_message"),
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            state.incomes.forEach { income ->
                                val category = state.categories.find { it.name.equals(income.category, ignoreCase = true) }
                                val categoryColor = category?.color?.let { parseHexColor(it) } ?: GreenAccent
                                IncomeListItem(
                                    income = income,
                                    currency = currency,
                                    categoryColor = categoryColor,
                                    onEditClick = { incomeViewModel.startEditing(income) },
                                    onDeleteClick = { incomeViewModel.requestDeleteIncome(income) }
                                )
                            }
                        }
                    }
                }
                FinanceTopTab.BUDGET -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("container_budget_view"),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // 1. Budget Summary Card
                        PeriodSummaryCard(
                            title = t("budget.overview.title"),
                            totalSubtitle = t("budget.overview.total_budgeted"),
                            centerLabel = t("budget.overview.center_label"),
                            emptyMessage = t("budget.overview.empty_message"),
                            summary = state.budgetSummary,
                            currency = currency,
                            cardTestTag = "card_budget_overview",
                            totalTestTag = "text_total_budgeted"
                        )

                        // 2. Budget Allocation Card
                        val categoryBudgets = remember(
                            state.budgets,
                            state.budgetCategories,
                            state.expenses,
                            activePeriod
                        ) {
                            incomeViewModel.calculateCategoryBudgets(activePeriod)
                        }
                        BudgetAllocationCard(
                            categoryBudgets = categoryBudgets,
                            currency = currency
                        )

                        // 3. Add Budget Action Button
                        Button(
                            onClick = { showAddBudgetDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_toggle_add_budget"),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = t("budget.header.add_button"),
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3. Budget Breakdown Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t("budget.breakdown.title"),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 4. Budgets List
                        val budgetHistoryScrollState = rememberScrollState()
                        LaunchedEffect(state.selectedPeriodId) {
                            budgetHistoryScrollState.scrollTo(0)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(budgetHistoryScrollState)
                                .testTag("list_budget_breakdown"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.budgets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CardBackground)
                                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = t("budget.history.empty_message"),
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                state.budgets.forEach { budget ->
                                    val category = state.budgetCategories.find { it.name.equals(budget.category, ignoreCase = true) }
                                    val categoryColor = category?.color?.let { parseHexColor(it) } ?: GreenAccent
                                    val categoryIcon = category?.icon?.ifBlank { "📋" } ?: "📋"
                                    BudgetListItem(
                                        budget = budget,
                                        currency = currency,
                                        categoryColor = categoryColor,
                                        categoryIcon = categoryIcon,
                                        onEditClick = { incomeViewModel.startEditingBudget(budget) },
                                        onDeleteClick = { incomeViewModel.requestDeleteBudget(budget) }
                                    )
                                }
                            }
                        }
                    }
                }
                FinanceTopTab.EXPENSES -> {
                    if (expenseViewModel != null) {
                        ExpensesTabContent(
                            viewModel = expenseViewModel,
                            currency = currency
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("container_expenses_view")
                        )
                    }
                }
                FinanceTopTab.SAVINGS -> {
                    if (savingsViewModel != null) {
                        SavingsTabContent(
                            viewModel = savingsViewModel,
                            currency = currency
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("container_savings_view")
                        )
                    }
                }
            }
        }
    }

    if (showAddForm && !showIncomeCategorySheet) {
        AddIncomeDialog(
            state = state,
            currency = currency,
            onAmountChange = incomeViewModel::onAmountChange,
            onDateTimeChange = incomeViewModel::onDateTimeChange,
            onDescriptionChange = incomeViewModel::onDescriptionChange,
            onSelectCategoryClick = {
                isIncomeCategorySheetForEdit = false
                showIncomeCategorySheet = true
            },
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                incomeViewModel.saveIncome()
            },
            onCloseClick = {
                incomeViewModel.clearErrorMessage()
                showAddForm = false
            }
        )
    }

    if (!showIncomeCategorySheet) {
        state.editingIncome?.let { editState ->
            EditIncomeDialog(
                editState = editState,
                currency = currency,
                onAmountChange = incomeViewModel::onEditAmountChange,
                onDateTimeChange = incomeViewModel::onEditDateTimeChange,
                onDescriptionChange = incomeViewModel::onEditDescriptionChange,
                onSelectCategoryClick = {
                    isIncomeCategorySheetForEdit = true
                    showIncomeCategorySheet = true
                },
                onConfirm = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    incomeViewModel.confirmEdit()
                },
                onDismiss = { incomeViewModel.cancelEditing() }
            )
        }
    }

    CategoryManagerSheet(
        visible = showIncomeCategorySheet,
        categories = state.categories,
        newCategoryName = state.newCategoryNameInput,
        selectedColorHex = state.selectedColorHex,
        categoryError = state.categoryError,
        newSubcategoryName = state.newSubcategoryNameInput,
        subcategoryError = state.subcategoryError,
        editingCategory = state.editingCategory,
        categoryToDelete = state.categoryToDelete,
        onCategoryNameChange = incomeViewModel::onNewCategoryNameChange,
        onColorSelect = incomeViewModel::onColorSelect,
        onCreateCategory = incomeViewModel::createCategory,
        onSubcategoryNameChange = incomeViewModel::onNewSubcategoryNameChange,
        onCreateSubcategory = { parentName -> incomeViewModel.createSubcategory(parentName) },
        onEditCategoryClick = incomeViewModel::startEditingCategory,
        onEditCategoryNameChange = incomeViewModel::onEditCategoryNameChange,
        onEditCategoryColorSelect = incomeViewModel::onEditCategoryColorChange,
        onConfirmEditCategory = incomeViewModel::confirmEditCategory,
        onDismissEditCategory = incomeViewModel::cancelEditingCategory,
        onDeleteCategoryClick = { incomeViewModel.requestDeleteCategory(it) },
        onConfirmDeleteCategory = incomeViewModel::confirmDeleteCategory,
        onDismissDeleteCategory = incomeViewModel::dismissDeleteCategoryConfirmation,
        onDeleteSubcategory = { cat, sub -> incomeViewModel.deleteSubcategory(cat, sub) },
        onClearCategoryError = incomeViewModel::clearCategoryError,
        onClearSubcategoryError = incomeViewModel::clearSubcategoryError,
        onDismiss = { showIncomeCategorySheet = false },
        onCategorySelected = { cat, sub ->
            if (isIncomeCategorySheetForEdit) {
                incomeViewModel.onEditCategoryChange(cat, sub)
            } else {
                incomeViewModel.onCategorySelected(cat, sub)
            }
            showIncomeCategorySheet = false
        },
        testTags = IncomeCategoryManagerTestTags,
        deleteDialogTitle = resolveDeleteDialogText(
            primaryKey = "incomes.delete_category_dialog.title",
            fallbackKey = "expenses.delete_category_dialog.title",
            defaultText = "¿Eliminar esta categoría?"
        ),
        deleteDialogWarning = resolveDeleteDialogText(
            primaryKey = "incomes.delete_category_dialog.warning_message",
            fallbackKey = "expenses.delete_category_dialog.warning_message",
            defaultText = "Esta acción no se puede deshacer. Desliza el botón para confirmar."
        ),
        deleteDialogCancel = resolveDeleteDialogText(
            primaryKey = "incomes.delete_category_dialog.cancel_button",
            fallbackKey = "expenses.delete_category_dialog.cancel_button",
            defaultText = "Cancelar"
        )
    )

    state.incomeToDelete?.let { incomeToDelete ->
        DeleteIncomeConfirmationDialog(
            income = incomeToDelete,
            currency = currency,
            onConfirm = { incomeViewModel.confirmDeleteIncome() },
            onDismiss = { incomeViewModel.dismissDeleteConfirmation() }
        )
    }

    if (showAddBudgetDialog && !showBudgetCategorySheet) {
        AddEditBudgetDialog(
            isEditing = false,
            amount = state.budgetAmountInput,
            category = state.budgetCategoryInput,
            subcategory = state.budgetSubcategoryInput,
            errorMessage = state.budgetErrorMessage,
            currency = currency,
            onAmountChange = incomeViewModel::onBudgetAmountChange,
            onSelectCategoryClick = {
                isBudgetCategorySheetForEdit = false
                showBudgetCategorySheet = true
            },
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                incomeViewModel.saveBudget()
            },
            onCloseClick = {
                incomeViewModel.clearBudgetErrorMessage()
                showAddBudgetDialog = false
            }
        )
    }

    if (!showBudgetCategorySheet) {
        state.editingBudget?.let { editing ->
            AddEditBudgetDialog(
                isEditing = true,
                amount = editing.amount,
                category = editing.category,
                subcategory = editing.subcategory,
                errorMessage = editing.errorMessage,
                currency = currency,
                onAmountChange = incomeViewModel::onEditBudgetAmountChange,
                onSelectCategoryClick = {
                    isBudgetCategorySheetForEdit = true
                    showBudgetCategorySheet = true
                },
                onSaveClick = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    incomeViewModel.saveEditedBudget()
                },
                onCloseClick = {
                    incomeViewModel.cancelEditingBudget()
                }
            )
        }
    }

    CategoryManagerSheet(
        visible = showBudgetCategorySheet,
        categories = state.budgetCategories,
        newCategoryName = state.newCategoryNameInput,
        selectedColorHex = state.selectedColorHex,
        categoryError = state.categoryError,
        newSubcategoryName = state.newSubcategoryNameInput,
        subcategoryError = state.subcategoryError,
        editingCategory = state.editingBudgetCategory,
        categoryToDelete = state.budgetCategoryToDelete,
        onCategoryNameChange = incomeViewModel::onNewCategoryNameChange,
        onColorSelect = incomeViewModel::onColorSelect,
        onCreateCategory = incomeViewModel::createBudgetCategory,
        onSubcategoryNameChange = incomeViewModel::onNewSubcategoryNameChange,
        onCreateSubcategory = { parentName -> incomeViewModel.createBudgetSubcategory(parentName) },
        onEditCategoryClick = incomeViewModel::startEditingBudgetCategory,
        onEditCategoryNameChange = incomeViewModel::onEditBudgetCategoryNameChange,
        onEditCategoryColorSelect = incomeViewModel::onEditBudgetCategoryColorChange,
        onConfirmEditCategory = incomeViewModel::confirmEditBudgetCategory,
        onDismissEditCategory = incomeViewModel::cancelEditingBudgetCategory,
        onDeleteCategoryClick = { incomeViewModel.requestDeleteBudgetCategory(it) },
        onConfirmDeleteCategory = incomeViewModel::confirmDeleteBudgetCategory,
        onDismissDeleteCategory = incomeViewModel::dismissDeleteBudgetCategoryConfirmation,
        onDeleteSubcategory = { cat, sub -> incomeViewModel.deleteBudgetSubcategory(cat, sub) },
        onClearCategoryError = incomeViewModel::clearCategoryError,
        onClearSubcategoryError = incomeViewModel::clearSubcategoryError,
        onDismiss = { showBudgetCategorySheet = false },
        onCategorySelected = { cat, sub ->
            if (isBudgetCategorySheetForEdit) {
                incomeViewModel.onEditBudgetCategorySelected(cat, sub)
            } else {
                incomeViewModel.onBudgetCategorySelected(cat, sub)
            }
            showBudgetCategorySheet = false
        },
        testTags = BudgetCategoryManagerTestTags,
        deleteDialogTitle = resolveDeleteDialogText(
            primaryKey = "budget.delete_category_dialog.title",
            fallbackKey = "expenses.delete_category_dialog.title",
            defaultText = "¿Eliminar esta categoría?"
        ),
        deleteDialogWarning = resolveDeleteDialogText(
            primaryKey = "budget.delete_category_dialog.warning_message",
            fallbackKey = "expenses.delete_category_dialog.warning_message",
            defaultText = "Esta acción no se puede deshacer. Desliza el botón para confirmar."
        ),
        deleteDialogCancel = resolveDeleteDialogText(
            primaryKey = "budget.delete_category_dialog.cancel_button",
            fallbackKey = "expenses.delete_category_dialog.cancel_button",
            defaultText = "Cancelar"
        )
    )

    state.budgetToDelete?.let { budgetToDelete ->
        val category = state.budgetCategories.find { it.name.equals(budgetToDelete.category, ignoreCase = true) }
        val categoryColor = category?.color?.let { parseHexColor(it) } ?: GreenAccent
        val categoryIcon = category?.icon?.ifBlank { "📋" } ?: "📋"
        DeleteBudgetConfirmationDialog(
            budget = budgetToDelete,
            currency = currency,
            categoryColor = categoryColor,
            categoryIcon = categoryIcon,
            onConfirm = { incomeViewModel.confirmDeleteBudget() },
            onDismiss = { incomeViewModel.dismissDeleteBudgetConfirmation() }
        )
    }
}

private val IncomeCategoryManagerTestTags = CategoryManagerTestTags(
    editDialog = "dialog_edit_income_category",
    editNameInput = "input_edit_income_category_name",
    editColorOptionPrefix = "color_option_edit_category_",
    editErrorText = "text_edit_income_category_error",
    editCancelButton = "btn_cancel_edit_income_category",
    editSaveButton = "btn_save_edit_income_category",
    deleteDialog = "dialog_delete_income_category",
    deleteConfirmButton = "btn_slide_to_confirm_delete_income_category",
    deleteCancelButton = "btn_cancel_delete_income_category"
)

@Composable
private fun resolveDeleteDialogText(
    primaryKey: String,
    fallbackKey: String,
    defaultText: String
): String {
    val primary = t(primaryKey)
    if (primary != primaryKey) return primary
    val fallback = t(fallbackKey)
    return if (fallback != fallbackKey) fallback else defaultText
}

@Composable
fun IncomeListItem(
    income: Income,
    currency: Currency,
    categoryColor: Color = GreenAccent,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("income_item_${income.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currency.symbol,
                        color = categoryColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = income.subcategory.ifBlank { income.category },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (income.subcategory.isNotBlank() && income.subcategory != income.category) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = income.category,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = income.dateTime,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyFormatter.format(income.amount, currency),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_options_income_${income.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("incomes.options.edit"), color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            },
                            modifier = Modifier.testTag("btn_edit_income_${income.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(t("incomes.options.delete"), color = ErrorRed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = ErrorRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            modifier = Modifier.testTag("btn_delete_income_${income.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetListItem(
    budget: Budget,
    currency: Currency,
    categoryColor: Color,
    categoryIcon: String = "📋",
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_item_${budget.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = categoryIcon,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = budget.category,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (budget.subcategory.isNotBlank() &&
                        !budget.subcategory.trim().equals(budget.category.trim(), ignoreCase = true)
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = budget.subcategory,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyFormatter.format(budget.amount, currency),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("text_budget_amount_${budget.id}")
                )

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_options_budget_${budget.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("budget.options.edit"), color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            },
                            modifier = Modifier.testTag("btn_edit_budget_${budget.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(t("budget.options.delete"), color = ErrorRed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = ErrorRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            modifier = Modifier.testTag("btn_delete_budget_${budget.id}")
                        )
                    }
                }
            }
        }
    }
}

internal fun formatPeriodRange(startIso: String, endIso: String, lang: String = "es"): String {
    val isoDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    if (!isoDateRegex.matches(startIso) || !isoDateRegex.matches(endIso)) {
        return "$startIso – $endIso"
    }
    return try {
        val locale = Locale.forLanguageTag(lang)
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val startPos = ParsePosition(0)
        val startDate = parser.parse(startIso, startPos)
        if (startDate == null || startPos.index != startIso.length || startPos.errorIndex != -1) {
            return "$startIso – $endIso"
        }
        val endPos = ParsePosition(0)
        val endDate = parser.parse(endIso, endPos)
        if (endDate == null || endPos.index != endIso.length || endPos.errorIndex != -1) {
            return "$startIso – $endIso"
        }

        val startCal = Calendar.getInstance().apply { time = startDate }
        val endCal = Calendar.getInstance().apply { time = endDate }

        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)
        val startMonth = startCal.get(Calendar.MONTH)
        val endMonth = endCal.get(Calendar.MONTH)
        val startYear = startCal.get(Calendar.YEAR)
        val endYear = endCal.get(Calendar.YEAR)

        val monthFormatter = SimpleDateFormat("MMM", locale)
        val startMonthLabel = monthFormatter.format(startDate)
            .replace(".", "")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val endMonthLabel = monthFormatter.format(endDate)
            .replace(".", "")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

        when {
            startYear == endYear && startMonth == endMonth ->
                "$startDay – $endDay $startMonthLabel $startYear"
            startYear == endYear ->
                "$startDay $startMonthLabel – $endDay $endMonthLabel $startYear"
            else ->
                "$startDay $startMonthLabel $startYear – $endDay $endMonthLabel $endYear"
        }
    } catch (_: Exception) {
        "$startIso – $endIso"
    }
}
