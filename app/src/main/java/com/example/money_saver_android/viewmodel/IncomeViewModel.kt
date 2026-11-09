package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FinanceTopTab {
    INCOMES,
    BUDGET
}

data class EditBudgetState(
    val budgetId: Long = 0,
    val amount: String = "",
    val category: String = "",
    val subcategory: String = "",
    val periodId: Long = 0,
    val errorMessage: String? = null
)

data class EditIncomeState(
    val incomeId: Long = 0,
    val amount: String = "",
    val dateTime: String = "",
    val description: String = "",
    val category: String = "",
    val subcategory: String = "",
    val periodId: Long = 0,
    val errorMessage: String? = null
)

data class IncomeUiState(
    val amountInput: String = "",
    val dateTimeInput: String = "",
    val descriptionInput: String = "",
    val categoryInput: String = "",
    val subcategoryInput: String = "",
    val selectedPeriodId: Long? = null,
    val incomes: List<Income> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totalIncome: Double = 0.0,
    val newCategoryNameInput: String = "",
    val selectedColorHex: String = "#4E73DF",
    val categoryError: String? = null,
    val newSubcategoryNameInput: String = "",
    val subcategoryError: String? = null,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val editingIncome: EditIncomeState? = null,
    val incomeToDelete: Income? = null,
    val selectedTopTab: FinanceTopTab = FinanceTopTab.INCOMES,
    val budgets: List<Budget> = emptyList(),
    val budgetCategories: List<Category> = emptyList(),
    val totalBudgeted: Double = 0.0,
    val availableIncome: Double = 0.0,
    val budgetAmountInput: String = "",
    val budgetCategoryInput: String = "",
    val budgetSubcategoryInput: String = "",
    val budgetErrorMessage: String? = null,
    val budgetToastMessage: String? = null,
    val editingBudget: EditBudgetState? = null,
    val budgetCategoryToDelete: Category? = null
)

class IncomeViewModel(
    private val incomeUseCase: IncomeUseCase
) : ViewModel() {

    constructor(incomeRepository: IncomeRepository) : this(IncomeUseCase(incomeRepository))

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            incomeUseCase.preloadDefaultCategoriesIfNeeded()
        }
        viewModelScope.launch {
            incomeUseCase.preloadDefaultBudgetCategoriesIfNeeded()
        }
        viewModelScope.launch {
            incomeUseCase.getCategories().collect { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            incomeUseCase.getBudgetCategories().collect { list ->
                _uiState.update { it.copy(budgetCategories = list) }
            }
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onDateTimeChange(value: String) {
        _uiState.update { it.copy(dateTimeInput = value) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(descriptionInput = value) }
    }

    fun onCategorySelected(category: String, subcategory: String) {
        _uiState.update {
            it.copy(categoryInput = category, subcategoryInput = subcategory, errorMessage = null)
        }
    }

    fun onPeriodSelected(periodId: Long?) {
        _uiState.update { it.copy(selectedPeriodId = periodId) }
        if (periodId != null) {
            viewModelScope.launch {
                incomeUseCase.getIncomesByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { incomeList ->
                        val total = incomeUseCase.calculateTotalIncome(incomeList)
                        _uiState.update { current ->
                            val available = incomeUseCase.calculateAvailableIncome(total, current.totalBudgeted)
                            current.copy(incomes = incomeList, totalIncome = total, availableIncome = available)
                        }
                    }
            }
            viewModelScope.launch {
                incomeUseCase.getBudgetsByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { budgetList ->
                        val totalBudgeted = incomeUseCase.calculateTotalBudgeted(budgetList)
                        _uiState.update { current ->
                            val available = incomeUseCase.calculateAvailableIncome(current.totalIncome, totalBudgeted)
                            current.copy(budgets = budgetList, totalBudgeted = totalBudgeted, availableIncome = available)
                        }
                    }
            }
        } else {
            _uiState.update { it.copy(incomes = emptyList(), totalIncome = 0.0, budgets = emptyList(), totalBudgeted = 0.0, availableIncome = 0.0) }
        }
    }

    fun saveIncome() {
        val state = _uiState.value
        viewModelScope.launch {
            when (val result = incomeUseCase.saveIncome(
                amountInput = state.amountInput,
                dateTimeInput = state.dateTimeInput,
                categoryInput = state.categoryInput,
                subcategoryInput = state.subcategoryInput,
                descriptionInput = state.descriptionInput,
                periodId = state.selectedPeriodId
            )) {
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            amountInput = "",
                            descriptionInput = "",
                            errorMessage = null,
                            toastMessage = "Ingreso guardado exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            when (val result = incomeUseCase.deleteCategory(categoryName)) {
                is IncomeResult.Success -> {
                    val trimmed = categoryName.trim()
                    _uiState.update { current ->
                        val shouldClearForm = current.categoryInput.equals(trimmed, ignoreCase = true)
                        val shouldClearEdit = current.editingIncome?.category?.equals(trimmed, ignoreCase = true) == true
                        current.copy(
                            categoryInput = if (shouldClearForm) "" else current.categoryInput,
                            subcategoryInput = if (shouldClearForm) "" else current.subcategoryInput,
                            editingIncome = if (shouldClearEdit) current.editingIncome?.copy(category = "", subcategory = "") else current.editingIncome,
                            errorMessage = null,
                            toastMessage = "Categoría eliminada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        viewModelScope.launch {
            when (val result = incomeUseCase.deleteSubcategory(categoryName, subcategoryName)) {
                is IncomeResult.Success -> {
                    val trimmedCategory = categoryName.trim()
                    val trimmedSubcategory = subcategoryName.trim()
                    _uiState.update { current ->
                        val shouldClearForm = current.categoryInput.equals(trimmedCategory, ignoreCase = true) &&
                                current.subcategoryInput.equals(trimmedSubcategory, ignoreCase = true)
                        val shouldClearEdit = current.editingIncome?.let { edit ->
                            edit.category.equals(trimmedCategory, ignoreCase = true) &&
                                    edit.subcategory.equals(trimmedSubcategory, ignoreCase = true)
                        } == true
                        current.copy(
                            subcategoryInput = if (shouldClearForm) "" else current.subcategoryInput,
                            editingIncome = if (shouldClearEdit) current.editingIncome?.copy(subcategory = "") else current.editingIncome,
                            subcategoryError = null,
                            toastMessage = "Subcategoría eliminada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(subcategoryError = result.message) }
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryError = null) }
    }

    fun clearSubcategoryError() {
        _uiState.update { it.copy(subcategoryError = null) }
    }

    fun onNewCategoryNameChange(name: String) {
        _uiState.update { it.copy(newCategoryNameInput = name, categoryError = null) }
    }

    fun onColorSelect(colorHex: String) {
        _uiState.update { it.copy(selectedColorHex = colorHex) }
    }

    fun createCategory() {
        val current = _uiState.value
        viewModelScope.launch {
            when (val result = incomeUseCase.createCategory(
                nameInput = current.newCategoryNameInput,
                colorInput = current.selectedColorHex
            )) {
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(categoryError = result.message) }
                }
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newCategoryNameInput = "",
                            selectedColorHex = "#4E73DF",
                            categoryError = null,
                            toastMessage = "Categoría creada exitosamente"
                        )
                    }
                }
            }
        }
    }

    fun onNewSubcategoryNameChange(name: String) {
        _uiState.update { it.copy(newSubcategoryNameInput = name, subcategoryError = null) }
    }

    fun createSubcategory(categoryName: String) {
        val current = _uiState.value
        viewModelScope.launch {
            when (val result = incomeUseCase.createSubcategory(
                categoryName = categoryName,
                subcategoryNameInput = current.newSubcategoryNameInput
            )) {
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(subcategoryError = result.message) }
                }
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newSubcategoryNameInput = "",
                            subcategoryError = null,
                            toastMessage = "Subcategoría creada exitosamente"
                        )
                    }
                }
            }
        }
    }

    fun startEditing(income: Income) {
        val amountStr = if (income.amount % 1.0 == 0.0) {
            income.amount.toLong().toString()
        } else {
            income.amount.toString()
        }
        _uiState.update {
            it.copy(
                editingIncome = EditIncomeState(
                    incomeId = income.id,
                    amount = amountStr,
                    dateTime = income.dateTime,
                    description = income.description,
                    category = income.category,
                    subcategory = income.subcategory,
                    periodId = income.periodId
                )
            )
        }
    }

    fun onEditAmountChange(amount: String) {
        _uiState.update { state ->
            state.copy(editingIncome = state.editingIncome?.copy(amount = amount, errorMessage = null))
        }
    }

    fun onEditDateTimeChange(dateTime: String) {
        _uiState.update { state ->
            state.copy(editingIncome = state.editingIncome?.copy(dateTime = dateTime))
        }
    }

    fun onEditDescriptionChange(description: String) {
        _uiState.update { state ->
            state.copy(editingIncome = state.editingIncome?.copy(description = description))
        }
    }

    fun onEditCategoryChange(category: String, subcategory: String) {
        _uiState.update { state ->
            state.copy(
                editingIncome = state.editingIncome?.copy(
                    category = category,
                    subcategory = subcategory,
                    errorMessage = null
                )
            )
        }
    }

    fun confirmEdit() {
        val editState = _uiState.value.editingIncome ?: return
        viewModelScope.launch {
            when (val result = incomeUseCase.updateIncome(
                id = editState.incomeId,
                amountInput = editState.amount,
                dateTimeInput = editState.dateTime,
                categoryInput = editState.category,
                subcategoryInput = editState.subcategory,
                descriptionInput = editState.description,
                periodId = editState.periodId
            )) {
                is IncomeResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            editingIncome = state.editingIncome?.copy(
                                errorMessage = result.message
                            )
                        )
                    }
                }
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            editingIncome = null,
                            toastMessage = "Ingreso guardado exitosamente"
                        )
                    }
                }
            }
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingIncome = null) }
    }

    fun requestDeleteIncome(income: Income) {
        _uiState.update { it.copy(incomeToDelete = income) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(incomeToDelete = null) }
    }

    fun confirmDeleteIncome() {
        val income = _uiState.value.incomeToDelete ?: return
        viewModelScope.launch {
            when (incomeUseCase.deleteIncome(income.id)) {
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            incomeToDelete = null,
                            toastMessage = "Ingreso eliminado exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    // No-op
                }
            }
        }
    }

    fun onTopTabSelected(tab: FinanceTopTab) {
        _uiState.update { it.copy(selectedTopTab = tab) }
    }

    fun onBudgetAmountChange(value: String) {
        _uiState.update { it.copy(budgetAmountInput = value, budgetErrorMessage = null) }
    }

    fun onBudgetCategorySelected(category: String, subcategory: String) {
        _uiState.update {
            it.copy(budgetCategoryInput = category, budgetSubcategoryInput = subcategory, budgetErrorMessage = null)
        }
    }

    fun clearBudgetErrorMessage() {
        _uiState.update { it.copy(budgetErrorMessage = null) }
    }

    fun clearBudgetToastMessage() {
        _uiState.update { it.copy(budgetToastMessage = null) }
    }

    fun saveBudget() {
        val state = _uiState.value
        viewModelScope.launch {
            when (val result = incomeUseCase.saveBudget(
                amountInput = state.budgetAmountInput,
                categoryInput = state.budgetCategoryInput,
                subcategoryInput = state.budgetSubcategoryInput,
                periodId = state.selectedPeriodId
            )) {
                is BudgetResult.Success -> {
                    _uiState.update {
                        it.copy(
                            budgetAmountInput = "",
                            budgetCategoryInput = "",
                            budgetSubcategoryInput = "",
                            budgetErrorMessage = null,
                            budgetToastMessage = "Presupuesto guardado exitosamente"
                        )
                    }
                }
                is BudgetResult.Error -> {
                    _uiState.update { it.copy(budgetErrorMessage = result.message) }
                }
            }
        }
    }

    fun startEditingBudget(budget: Budget) {
        _uiState.update {
            it.copy(
                editingBudget = EditBudgetState(
                    budgetId = budget.id,
                    amount = if (budget.amount % 1.0 == 0.0) budget.amount.toLong().toString() else budget.amount.toString(),
                    category = budget.category,
                    subcategory = budget.subcategory,
                    periodId = budget.periodId
                )
            )
        }
    }

    fun cancelEditingBudget() {
        _uiState.update { it.copy(editingBudget = null) }
    }

    fun onEditBudgetAmountChange(value: String) {
        _uiState.update { current ->
            current.copy(editingBudget = current.editingBudget?.copy(amount = value, errorMessage = null))
        }
    }

    fun onEditBudgetCategorySelected(category: String, subcategory: String) {
        _uiState.update { current ->
            current.copy(editingBudget = current.editingBudget?.copy(category = category, subcategory = subcategory, errorMessage = null))
        }
    }

    fun saveEditedBudget() {
        val editing = _uiState.value.editingBudget ?: return
        viewModelScope.launch {
            when (val result = incomeUseCase.updateBudget(
                id = editing.budgetId,
                amountInput = editing.amount,
                categoryInput = editing.category,
                subcategoryInput = editing.subcategory,
                periodId = editing.periodId
            )) {
                is BudgetResult.Success -> {
                    _uiState.update {
                        it.copy(
                            editingBudget = null,
                            budgetToastMessage = "Presupuesto actualizado exitosamente"
                        )
                    }
                }
                is BudgetResult.Error -> {
                    _uiState.update { current ->
                        current.copy(editingBudget = current.editingBudget?.copy(errorMessage = result.message))
                    }
                }
            }
        }
    }

    fun createBudgetCategory() {
        val state = _uiState.value
        createBudgetCategory(state.newCategoryNameInput, state.selectedColorHex)
    }

    fun createBudgetCategory(name: String, color: String, icon: String = "💰") {
        viewModelScope.launch {
            when (val result = incomeUseCase.createBudgetCategory(name, color, icon)) {
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newCategoryNameInput = "",
                            categoryError = null,
                            budgetToastMessage = "Categoría creada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(categoryError = result.message) }
                }
            }
        }
    }

    fun createBudgetSubcategory(parentCategory: String) {
        val state = _uiState.value
        createBudgetSubcategory(parentCategory, state.newSubcategoryNameInput)
    }

    fun createBudgetSubcategory(parentCategory: String, name: String) {
        viewModelScope.launch {
            when (val result = incomeUseCase.createBudgetSubcategory(parentCategory, name)) {
                is IncomeResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newSubcategoryNameInput = "",
                            subcategoryError = null,
                            budgetToastMessage = "Subcategoría creada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(subcategoryError = result.message) }
                }
            }
        }
    }

    fun deleteBudgetCategory(categoryName: String) {
        viewModelScope.launch {
            when (val result = incomeUseCase.deleteBudgetCategory(categoryName)) {
                is IncomeResult.Success -> {
                    val trimmed = categoryName.trim()
                    _uiState.update { current ->
                        val shouldClear = current.budgetCategoryInput.equals(trimmed, ignoreCase = true)
                        val shouldClearEdit = current.editingBudget?.category?.equals(trimmed, ignoreCase = true) == true
                        current.copy(
                            budgetCategoryInput = if (shouldClear) "" else current.budgetCategoryInput,
                            budgetSubcategoryInput = if (shouldClear) "" else current.budgetSubcategoryInput,
                            editingBudget = if (shouldClearEdit) current.editingBudget?.copy(category = "", subcategory = "") else current.editingBudget,
                            budgetToastMessage = "Categoría eliminada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(categoryError = result.message) }
                }
            }
        }
    }

    fun requestDeleteBudgetCategory(category: Category) {
        _uiState.update { it.copy(budgetCategoryToDelete = category) }
    }

    fun requestDeleteBudgetCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        val category = _uiState.value.budgetCategories.find { it.name.equals(trimmed, ignoreCase = true) }
            ?: Category(name = trimmed, icon = "📁", color = "#4E73DF", subcategories = emptyList())
        requestDeleteBudgetCategory(category)
    }

    fun dismissDeleteBudgetCategoryConfirmation() {
        _uiState.update { it.copy(budgetCategoryToDelete = null) }
    }

    fun confirmDeleteBudgetCategory() {
        val category = _uiState.value.budgetCategoryToDelete ?: return
        _uiState.update { it.copy(budgetCategoryToDelete = null) }
        deleteBudgetCategory(category.name)
    }

    fun deleteBudgetSubcategory(categoryName: String, subcategoryName: String) {
        viewModelScope.launch {
            when (val result = incomeUseCase.deleteBudgetSubcategory(categoryName, subcategoryName)) {
                is IncomeResult.Success -> {
                    val trimmedSub = subcategoryName.trim()
                    _uiState.update { current ->
                        val shouldClear = current.budgetSubcategoryInput.equals(trimmedSub, ignoreCase = true)
                        val shouldClearEdit = current.editingBudget?.subcategory?.equals(trimmedSub, ignoreCase = true) == true
                        current.copy(
                            budgetSubcategoryInput = if (shouldClear) "" else current.budgetSubcategoryInput,
                            editingBudget = if (shouldClearEdit) current.editingBudget?.copy(subcategory = "") else current.editingBudget,
                            budgetToastMessage = "Subcategoría eliminada exitosamente"
                        )
                    }
                }
                is IncomeResult.Error -> {
                    _uiState.update { it.copy(subcategoryError = result.message) }
                }
            }
        }
    }
}
