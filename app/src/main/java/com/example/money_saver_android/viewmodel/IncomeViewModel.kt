package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val incomeToDelete: Income? = null
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
            incomeUseCase.getCategories().collect { list ->
                _uiState.update { it.copy(categories = list) }
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
                        _uiState.update { it.copy(incomes = incomeList, totalIncome = total) }
                    }
            }
        } else {
            _uiState.update { it.copy(incomes = emptyList(), totalIncome = 0.0) }
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
}
