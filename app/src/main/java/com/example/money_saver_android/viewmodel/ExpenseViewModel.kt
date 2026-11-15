package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.CategoryExpenseItem
import com.example.money_saver_android.domain.usecase.CategoryResult
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditExpenseState(
    val expenseId: Long = 0,
    val amount: String = "",
    val date: String = "",
    val description: String = "",
    val category: String = "",
    val subcategory: String = "",
    val periodId: Long = 0L,
    val budgetId: Long? = null,
    val budgetCategory: String = "",
    val budgetSubcategory: String = "",
    val errorMessage: String? = null
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val selectedPeriodId: Long? = null,
    val budgets: List<Budget> = emptyList(),
    val hasActiveBudget: Boolean = false,
    val totalBudgeted: Double = 0.0,
    val totalSpent: Double = 0.0,
    val amountInput: String = "",
    val dateInput: String = "",
    val descriptionInput: String = "",
    val categoryInput: String = "",
    val subcategoryInput: String = "",
    val budgetIdInput: Long? = null,
    val budgetCategoryInput: String = "",
    val budgetSubcategoryInput: String = "",
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val editingExpense: EditExpenseState? = null,
    val expenseToDelete: Expense? = null,
    val categories: List<Category> = emptyList(),
    val newCategoryNameInput: String = "",
    val newCategoryColorInput: String = "#4E73DF",
    val categoryErrorMessage: String? = null,
    val newSubcategoryNameInput: String = "",
    val subcategoryErrorMessage: String? = null
)

class ExpenseViewModel(
    private val expenseUseCase: ExpenseUseCase,
    private val categoryUseCase: CategoryUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    private var expensesJob: Job? = null
    private var budgetsJob: Job? = null

    init {
        expensesJob = viewModelScope.launch {
            expenseUseCase.getExpenses().collect { list ->
                _uiState.update { it.copy(expenses = list, totalSpent = list.sumOf { e -> e.amount }) }
            }
        }
        categoryUseCase?.let { uc ->
            viewModelScope.launch {
                uc.preloadDefaultCategoriesIfNeeded()
                uc.getCategories().collect { list ->
                    _uiState.update { it.copy(categories = list) }
                }
            }
        }
    }

    fun onPeriodSelected(periodId: Long?) {
        expensesJob?.cancel()
        budgetsJob?.cancel()
        val periodChanged = _uiState.value.selectedPeriodId != periodId
        _uiState.update { current ->
            if (periodId == null || periodChanged) {
                current.copy(
                    selectedPeriodId = periodId,
                    expenses = emptyList(),
                    budgets = emptyList(),
                    hasActiveBudget = false,
                    totalBudgeted = 0.0,
                    totalSpent = 0.0,
                    budgetIdInput = null,
                    budgetCategoryInput = "",
                    budgetSubcategoryInput = ""
                )
            } else {
                current.copy(selectedPeriodId = periodId)
            }
        }
        if (periodId != null) {
            expensesJob = viewModelScope.launch {
                expenseUseCase.getExpensesByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        _uiState.update { it.copy(expenses = list, totalSpent = list.sumOf { e -> e.amount }) }
                    }
            }
            budgetsJob = viewModelScope.launch {
                expenseUseCase.getBudgetsByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { budgetList ->
                        _uiState.update {
                            it.copy(
                                budgets = budgetList,
                                hasActiveBudget = budgetList.isNotEmpty(),
                                totalBudgeted = budgetList.sumOf { b -> b.amount }
                            )
                        }
                    }
            }
        } else {
            expensesJob = null
            budgetsJob = null
        }
    }

    fun onBudgetSelected(budget: Budget) {
        _uiState.update {
            it.copy(
                budgetIdInput = budget.id,
                budgetCategoryInput = budget.category,
                budgetSubcategoryInput = budget.subcategory,
                errorMessage = null
            )
        }
    }

    fun onAmountChange(amount: String) {
        _uiState.update { it.copy(amountInput = amount, errorMessage = null) }
    }

    fun onDateChange(date: String) {
        _uiState.update { it.copy(dateInput = date) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(descriptionInput = description) }
    }

    fun onCategoryChange(category: String, subcategory: String) {
        _uiState.update { it.copy(categoryInput = category, subcategoryInput = subcategory, errorMessage = null) }
    }

    fun startEditing(expense: Expense) {
        val amountStr = if (expense.amount % 1.0 == 0.0) {
            expense.amount.toLong().toString()
        } else {
            expense.amount.toString()
        }
        val matchingBudget = _uiState.value.budgets.find {
            it.id == expense.budgetId && it.category.equals(expense.budgetCategory, ignoreCase = true)
        }
        _uiState.update {
            it.copy(
                editingExpense = EditExpenseState(
                    expenseId = expense.id,
                    amount = amountStr,
                    date = expense.date,
                    description = expense.description,
                    category = expense.category,
                    subcategory = expense.subcategory,
                    periodId = expense.periodId.takeIf { id -> id > 0L } ?: (it.selectedPeriodId ?: 0L),
                    budgetId = expense.budgetId.takeIf { id -> id > 0L },
                    budgetCategory = expense.budgetCategory,
                    budgetSubcategory = matchingBudget?.subcategory.orEmpty()
                )
            )
        }
    }

    fun onEditBudgetSelected(budget: Budget) {
        _uiState.update { state ->
            state.copy(
                editingExpense = state.editingExpense?.copy(
                    budgetId = budget.id,
                    budgetCategory = budget.category,
                    budgetSubcategory = budget.subcategory,
                    errorMessage = null
                )
            )
        }
    }

    fun onEditAmountChange(amount: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(amount = amount, errorMessage = null))
        }
    }

    fun onEditDateChange(date: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(date = date))
        }
    }

    fun onEditDescriptionChange(description: String) {
        _uiState.update { state ->
            state.copy(editingExpense = state.editingExpense?.copy(description = description))
        }
    }

    fun onEditCategoryChange(category: String, subcategory: String) {
        _uiState.update { state ->
            state.copy(
                editingExpense = state.editingExpense?.copy(
                    category = category,
                    subcategory = subcategory,
                    errorMessage = null
                )
            )
        }
    }

    fun confirmEdit() {
        val editState = _uiState.value.editingExpense ?: return
        val periodId = editState.periodId.takeIf { it > 0L } ?: _uiState.value.selectedPeriodId
        viewModelScope.launch {
            when (val result = expenseUseCase.updateExpense(
                id = editState.expenseId,
                amountInput = editState.amount,
                dateInput = editState.date,
                descriptionInput = editState.description,
                categoryInput = editState.category,
                subcategoryInput = editState.subcategory,
                periodId = periodId,
                budgetId = editState.budgetId,
                budgetCategoryInput = editState.budgetCategory
            )) {
                is ExpenseResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            editingExpense = state.editingExpense?.copy(
                                errorMessage = result.message
                            )
                        )
                    }
                }
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            editingExpense = null,
                            toastMessage = "Gasto guardado"
                        )
                    }
                }
            }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun requestDeleteExpense(expense: Expense) {
        _uiState.update { it.copy(expenseToDelete = expense) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(expenseToDelete = null) }
    }

    fun confirmDeleteExpense() {
        val expense = _uiState.value.expenseToDelete ?: return
        viewModelScope.launch {
            when (expenseUseCase.deleteExpense(expense.id)) {
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            expenseToDelete = null,
                            toastMessage = "Gasto eliminado"
                        )
                    }
                }
                is ExpenseResult.Error -> {
                    // No-op for now
                }
            }
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun saveExpense() {
        val current = _uiState.value
        viewModelScope.launch {
            when (val result = expenseUseCase.saveExpense(
                amountInput = current.amountInput,
                dateInput = current.dateInput,
                descriptionInput = current.descriptionInput,
                categoryInput = current.categoryInput,
                subcategoryInput = current.subcategoryInput,
                periodId = current.selectedPeriodId,
                budgetId = current.budgetIdInput,
                budgetCategoryInput = current.budgetCategoryInput
            )) {
                is ExpenseResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                is ExpenseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            amountInput = "",
                            descriptionInput = "",
                            categoryInput = "",
                            subcategoryInput = "",
                            budgetIdInput = null,
                            budgetCategoryInput = "",
                            budgetSubcategoryInput = "",
                            errorMessage = null,
                            toastMessage = "Gasto guardado"
                        )
                    }
                }
            }
        }
    }

    fun calculateCategoryExpenses(): List<CategoryExpenseItem> {
        val current = _uiState.value
        return expenseUseCase.calculateCategoryExpenses(current.expenses, current.categories)
    }

    fun onNewCategoryNameChange(name: String) {
        _uiState.update { it.copy(newCategoryNameInput = name, categoryErrorMessage = null) }
    }

    fun onNewCategoryColorChange(color: String) {
        _uiState.update { it.copy(newCategoryColorInput = color) }
    }

    fun createCategory() {
        val current = _uiState.value
        val uc = categoryUseCase ?: return
        viewModelScope.launch {
            when (val result = uc.createCategory(
                nameInput = current.newCategoryNameInput,
                colorInput = current.newCategoryColorInput
            )) {
                is CategoryResult.Error -> {
                    _uiState.update { it.copy(categoryErrorMessage = result.message) }
                }
                is CategoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newCategoryNameInput = "",
                            newCategoryColorInput = "#4E73DF",
                            categoryErrorMessage = null,
                            toastMessage = "Categoría creada"
                        )
                    }
                }
            }
        }
    }

    fun onNewSubcategoryNameChange(name: String) {
        _uiState.update { it.copy(newSubcategoryNameInput = name, subcategoryErrorMessage = null) }
    }

    fun createSubcategory(categoryName: String) {
        val current = _uiState.value
        val uc = categoryUseCase ?: return
        viewModelScope.launch {
            when (val result = uc.createSubcategory(
                categoryName = categoryName,
                subcategoryNameInput = current.newSubcategoryNameInput
            )) {
                is CategoryResult.Error -> {
                    _uiState.update { it.copy(subcategoryErrorMessage = result.message) }
                }
                is CategoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newSubcategoryNameInput = "",
                            subcategoryErrorMessage = null,
                            toastMessage = "Subcategoría creada"
                        )
                    }
                }
            }
        }
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryErrorMessage = null) }
    }

    fun clearSubcategoryError() {
        _uiState.update { it.copy(subcategoryErrorMessage = null) }
    }
}
