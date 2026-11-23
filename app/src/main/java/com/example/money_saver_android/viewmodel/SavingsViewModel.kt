package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.PeriodCategorySummary
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class EditSavingState(
    val savingId: Long = 0L,
    val amount: String = "",
    val incomeId: Long? = null,
    val incomeCategory: String = "",
    val incomeSubcategory: String = "",
    val category: String = "",
    val subcategory: String = "",
    val dateTime: String = "",
    val note: String = "",
    val periodId: Long = 0L,
    val errorMessage: String? = null
)

data class SavingsUiState(
    val selectedPeriodId: Long? = null,
    val incomes: List<Income> = emptyList(),
    val savings: List<Saving> = emptyList(),
    val savingCategories: List<Category> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalBudgeted: Double = 0.0,
    val totalSaved: Double = 0.0,
    val savingsSummary: PeriodCategorySummary = PeriodCategorySummary(),
    val availableIncome: Double = 0.0,
    val savingAmountInput: String = "",
    val savingIncomeIdInput: Long? = null,
    val savingIncomeCategoryInput: String = "",
    val savingIncomeSubcategoryInput: String = "",
    val savingCategoryInput: String = "",
    val savingSubcategoryInput: String = "",
    val savingDateTimeInput: String = "",
    val savingNoteInput: String = "",
    val savingErrorMessage: String? = null,
    val savingToastMessage: String? = null,
    val editingSaving: EditSavingState? = null,
    val savingToDelete: Saving? = null,
    val newCategoryNameInput: String = "",
    val selectedColorHex: String = "#4E73DF",
    val categoryError: String? = null,
    val newSubcategoryNameInput: String = "",
    val subcategoryError: String? = null,
    val savingCategoryToDelete: Category? = null
)

class SavingsViewModel(
    private val savingsUseCase: SavingsUseCase
) : ViewModel() {

    companion object {
        const val MSG_SAVING_SAVED = "savings.messages.saved"
        const val MSG_SAVING_UPDATED = "savings.messages.updated"
        const val MSG_SAVING_DELETED = "savings.messages.deleted"
        const val MSG_CATEGORY_CREATED = "savings.messages.category_created"
        const val MSG_SUBCATEGORY_CREATED = "savings.messages.subcategory_created"
        const val MSG_CATEGORY_DELETED = "savings.messages.category_deleted"
        const val MSG_SUBCATEGORY_DELETED = "savings.messages.subcategory_deleted"
    }

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    private var incomesPeriodJob: Job? = null
    private var budgetsPeriodJob: Job? = null
    private var savingsPeriodJob: Job? = null
    private var isSaving: Boolean = false

    init {
        viewModelScope.launch {
            savingsUseCase.preloadDefaultCategoriesIfNeeded()
        }
        viewModelScope.launch {
            savingsUseCase.getCategories().collect { list ->
                _uiState.update { current ->
                    current.copy(
                        savingCategories = list,
                        savingsSummary = savingsUseCase.calculateSavingsCategorySummary(current.savings, list)
                    )
                }
            }
        }
    }

    fun onPeriodSelected(periodId: Long?) {
        incomesPeriodJob?.cancel()
        budgetsPeriodJob?.cancel()
        savingsPeriodJob?.cancel()
        val periodChanged = _uiState.value.selectedPeriodId != periodId
        _uiState.update { current ->
            if (periodId == null || periodChanged) {
                current.copy(
                    selectedPeriodId = periodId,
                    incomes = emptyList(),
                    savings = emptyList(),
                    totalIncome = 0.0,
                    totalBudgeted = 0.0,
                    totalSaved = 0.0,
                    savingsSummary = PeriodCategorySummary(),
                    availableIncome = 0.0,
                    savingAmountInput = "",
                    savingIncomeIdInput = null,
                    savingIncomeCategoryInput = "",
                    savingIncomeSubcategoryInput = "",
                    savingCategoryInput = "",
                    savingSubcategoryInput = "",
                    savingDateTimeInput = "",
                    savingNoteInput = "",
                    savingErrorMessage = null,
                    editingSaving = null,
                    savingToDelete = null,
                    categoryError = null,
                    subcategoryError = null,
                    savingCategoryToDelete = null
                )
            } else {
                current.copy(selectedPeriodId = periodId)
            }
        }
        if (periodId != null) {
            incomesPeriodJob = viewModelScope.launch {
                savingsUseCase.getIncomesByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { incomes ->
                        val totalIncome = incomes.sumOf { it.amount }
                        _uiState.update { current ->
                            val available = savingsUseCase.calculateAvailableIncome(
                                totalIncome, current.totalBudgeted, current.totalSaved
                            )
                            val matchedIncome = current.savingIncomeIdInput?.let { selectedId ->
                                incomes.find { it.id == selectedId }
                            }
                            current.copy(
                                incomes = incomes,
                                totalIncome = totalIncome,
                                availableIncome = available,
                                savingIncomeIdInput = matchedIncome?.id,
                                savingIncomeCategoryInput = matchedIncome?.category.orEmpty(),
                                savingIncomeSubcategoryInput = matchedIncome?.subcategory.orEmpty()
                            )
                        }
                    }
            }
            budgetsPeriodJob = viewModelScope.launch {
                savingsUseCase.getBudgetsByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { budgets ->
                        val totalBudgeted = budgets.sumOf { it.amount }
                        _uiState.update { current ->
                            val available = savingsUseCase.calculateAvailableIncome(
                                current.totalIncome, totalBudgeted, current.totalSaved
                            )
                            current.copy(totalBudgeted = totalBudgeted, availableIncome = available)
                        }
                    }
            }
            savingsPeriodJob = viewModelScope.launch {
                savingsUseCase.getSavingsByPeriod(periodId)
                    .catch { emit(emptyList()) }
                    .collect { savingList ->
                        val totalSaved = savingsUseCase.calculateTotalSaved(savingList)
                        _uiState.update { current ->
                            val available = savingsUseCase.calculateAvailableIncome(
                                current.totalIncome, current.totalBudgeted, totalSaved
                            )
                            current.copy(
                                savings = savingList,
                                totalSaved = totalSaved,
                                savingsSummary = savingsUseCase.calculateSavingsCategorySummary(savingList, current.savingCategories),
                                availableIncome = available
                            )
                        }
                    }
            }
        } else {
            incomesPeriodJob = null
            budgetsPeriodJob = null
            savingsPeriodJob = null
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(savingAmountInput = value, savingErrorMessage = null) }
    }

    fun onIncomeSelected(income: Income) {
        _uiState.update {
            it.copy(
                savingIncomeIdInput = income.id,
                savingIncomeCategoryInput = income.category,
                savingIncomeSubcategoryInput = income.subcategory,
                savingErrorMessage = null
            )
        }
    }

    fun onDateTimeChange(value: String) {
        _uiState.update { it.copy(savingDateTimeInput = value, savingErrorMessage = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(savingNoteInput = value) }
    }

    fun onCategorySelected(category: String, subcategory: String) {
        _uiState.update {
            it.copy(
                savingCategoryInput = category,
                savingSubcategoryInput = subcategory,
                savingErrorMessage = null
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(savingErrorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(savingToastMessage = null) }
    }

    fun saveSaving() {
        if (isSaving) return
        val state = _uiState.value
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = savingsUseCase.saveSaving(
                    amountInput = state.savingAmountInput,
                    categoryInput = state.savingCategoryInput,
                    subcategoryInput = state.savingSubcategoryInput,
                    periodId = state.selectedPeriodId,
                    incomeId = state.savingIncomeIdInput,
                    dateTimeInput = state.savingDateTimeInput,
                    noteInput = state.savingNoteInput
                )) {
                    is SavingsResult.Success -> {
                        _uiState.update {
                            it.copy(
                                savingAmountInput = "",
                                savingIncomeIdInput = null,
                                savingIncomeCategoryInput = "",
                                savingIncomeSubcategoryInput = "",
                                savingCategoryInput = "",
                                savingSubcategoryInput = "",
                                savingDateTimeInput = "",
                                savingNoteInput = "",
                                savingErrorMessage = null,
                                savingToastMessage = MSG_SAVING_SAVED
                            )
                        }
                    }
                    is SavingsResult.Error -> {
                        _uiState.update { it.copy(savingErrorMessage = result.message) }
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun startEditingSaving(saving: Saving) {
        val amountStr = formatAmountForEdit(saving.amount)
        val matchingIncome = if (saving.incomeId > 0L) {
            _uiState.value.incomes.find { it.id == saving.incomeId }
        } else {
            null
        }
        _uiState.update {
            it.copy(
                editingSaving = EditSavingState(
                    savingId = saving.id,
                    amount = amountStr,
                    incomeId = saving.incomeId.takeIf { id -> id > 0L },
                    incomeCategory = matchingIncome?.category ?: saving.incomeCategory,
                    incomeSubcategory = matchingIncome?.subcategory.orEmpty(),
                    category = saving.category,
                    subcategory = saving.subcategory,
                    dateTime = saving.dateTime,
                    note = saving.note,
                    periodId = saving.periodId.takeIf { id -> id > 0L } ?: (it.selectedPeriodId ?: 0L)
                )
            )
        }
    }

    private fun formatAmountForEdit(amount: Double): String {
        if (!amount.isFinite() || amount == 0.0) return "0"
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
    }

    fun onEditAmountChange(value: String) {
        _uiState.update { state ->
            state.copy(editingSaving = state.editingSaving?.copy(amount = value, errorMessage = null))
        }
    }

    fun onEditIncomeSelected(income: Income) {
        _uiState.update { state ->
            state.copy(
                editingSaving = state.editingSaving?.copy(
                    incomeId = income.id,
                    incomeCategory = income.category,
                    incomeSubcategory = income.subcategory,
                    errorMessage = null
                )
            )
        }
    }

    fun onEditCategorySelected(category: String, subcategory: String) {
        _uiState.update { state ->
            state.copy(
                editingSaving = state.editingSaving?.copy(
                    category = category,
                    subcategory = subcategory,
                    errorMessage = null
                )
            )
        }
    }

    fun onEditDateTimeChange(value: String) {
        _uiState.update { state ->
            state.copy(editingSaving = state.editingSaving?.copy(dateTime = value, errorMessage = null))
        }
    }

    fun onEditNoteChange(value: String) {
        _uiState.update { state ->
            state.copy(editingSaving = state.editingSaving?.copy(note = value))
        }
    }

    fun cancelEditingSaving() {
        _uiState.update { it.copy(editingSaving = null) }
    }

    fun confirmEditSaving() {
        if (isSaving) return
        val editState = _uiState.value.editingSaving ?: return
        val periodId = editState.periodId.takeIf { it > 0L } ?: _uiState.value.selectedPeriodId
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = savingsUseCase.updateSaving(
                    id = editState.savingId,
                    amountInput = editState.amount,
                    categoryInput = editState.category,
                    subcategoryInput = editState.subcategory,
                    periodId = periodId,
                    incomeId = editState.incomeId,
                    dateTimeInput = editState.dateTime,
                    noteInput = editState.note
                )) {
                    is SavingsResult.Success -> {
                        _uiState.update {
                            it.copy(
                                editingSaving = null,
                                savingToastMessage = MSG_SAVING_UPDATED
                            )
                        }
                    }
                    is SavingsResult.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                editingSaving = state.editingSaving?.copy(errorMessage = result.message)
                            )
                        }
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun requestDeleteSaving(saving: Saving) {
        _uiState.update { it.copy(savingToDelete = saving) }
    }

    fun dismissDeleteSavingConfirmation() {
        _uiState.update { it.copy(savingToDelete = null) }
    }

    fun confirmDeleteSaving() {
        val saving = _uiState.value.savingToDelete ?: return
        viewModelScope.launch {
            when (savingsUseCase.deleteSaving(saving.id)) {
                is SavingsResult.Success -> {
                    _uiState.update {
                        it.copy(
                            savingToDelete = null,
                            savingToastMessage = MSG_SAVING_DELETED
                        )
                    }
                }
                is SavingsResult.Error -> {
                    _uiState.update { it.copy(savingToDelete = null) }
                }
            }
        }
    }

    fun onNewCategoryNameChange(name: String) {
        _uiState.update { it.copy(newCategoryNameInput = name, categoryError = null) }
    }

    fun onColorSelect(colorHex: String) {
        _uiState.update { it.copy(selectedColorHex = colorHex) }
    }

    fun clearCategoryError() {
        _uiState.update { it.copy(categoryError = null) }
    }

    fun onNewSubcategoryNameChange(name: String) {
        _uiState.update { it.copy(newSubcategoryNameInput = name, subcategoryError = null) }
    }

    fun clearSubcategoryError() {
        _uiState.update { it.copy(subcategoryError = null) }
    }

    fun createSavingCategory(
        name: String = _uiState.value.newCategoryNameInput,
        color: String = _uiState.value.selectedColorHex,
        icon: String = "💰"
    ) {
        viewModelScope.launch {
            when (val result = savingsUseCase.createCategory(name, color, icon)) {
                is SavingsResult.Success -> _uiState.update {
                    it.copy(newCategoryNameInput = "", selectedColorHex = "#4E73DF", categoryError = null, savingToastMessage = MSG_CATEGORY_CREATED)
                }
                is SavingsResult.Error -> _uiState.update { it.copy(categoryError = result.message) }
            }
        }
    }

    fun createSavingSubcategory(
        parentCategory: String,
        name: String = _uiState.value.newSubcategoryNameInput
    ) {
        viewModelScope.launch {
            when (val result = savingsUseCase.createSubcategory(parentCategory, name)) {
                is SavingsResult.Success -> _uiState.update {
                    it.copy(newSubcategoryNameInput = "", subcategoryError = null, savingToastMessage = MSG_SUBCATEGORY_CREATED)
                }
                is SavingsResult.Error -> _uiState.update { it.copy(subcategoryError = result.message) }
            }
        }
    }

    fun requestDeleteSavingCategory(category: Category) {
        _uiState.update { it.copy(savingCategoryToDelete = category) }
    }

    fun requestDeleteSavingCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        val category = _uiState.value.savingCategories.find { it.name.equals(trimmed, ignoreCase = true) }
            ?: Category(name = trimmed, icon = "📁", color = "#4E73DF", subcategories = emptyList())
        requestDeleteSavingCategory(category)
    }

    fun dismissDeleteSavingCategoryConfirmation() {
        _uiState.update { it.copy(savingCategoryToDelete = null) }
    }

    fun confirmDeleteSavingCategory() {
        val category = _uiState.value.savingCategoryToDelete ?: return
        _uiState.update { it.copy(savingCategoryToDelete = null) }
        deleteSavingCategory(category.name)
    }

    fun deleteSavingCategory(categoryName: String) {
        viewModelScope.launch {
            when (val result = savingsUseCase.deleteCategory(categoryName)) {
                is SavingsResult.Success -> {
                    val trimmed = categoryName.trim()
                    _uiState.update { current ->
                        val shouldClear = current.savingCategoryInput.equals(trimmed, ignoreCase = true)
                        val shouldClearEdit = current.editingSaving?.category?.equals(trimmed, ignoreCase = true) == true
                        current.copy(
                            savingCategoryInput = if (shouldClear) "" else current.savingCategoryInput,
                            savingSubcategoryInput = if (shouldClear) "" else current.savingSubcategoryInput,
                            editingSaving = if (shouldClearEdit) {
                                current.editingSaving?.copy(category = "", subcategory = "")
                            } else {
                                current.editingSaving
                            },
                            savingToastMessage = MSG_CATEGORY_DELETED
                        )
                    }
                }
                is SavingsResult.Error -> _uiState.update { it.copy(categoryError = result.message) }
            }
        }
    }

    fun deleteSavingSubcategory(categoryName: String, subcategoryName: String) {
        viewModelScope.launch {
            when (val result = savingsUseCase.deleteSubcategory(categoryName, subcategoryName)) {
                is SavingsResult.Success -> {
                    val trimmedCat = categoryName.trim()
                    val trimmedSub = subcategoryName.trim()
                    _uiState.update { current ->
                        val shouldClear = current.savingCategoryInput.equals(trimmedCat, ignoreCase = true) &&
                            current.savingSubcategoryInput.equals(trimmedSub, ignoreCase = true)
                        val shouldClearEdit = current.editingSaving?.let { edit ->
                            edit.category.equals(trimmedCat, ignoreCase = true) &&
                                edit.subcategory.equals(trimmedSub, ignoreCase = true)
                        } == true
                        current.copy(
                            savingSubcategoryInput = if (shouldClear) "" else current.savingSubcategoryInput,
                            editingSaving = if (shouldClearEdit) {
                                current.editingSaving?.copy(subcategory = "")
                            } else {
                                current.editingSaving
                            },
                            subcategoryError = null,
                            savingToastMessage = MSG_SUBCATEGORY_DELETED
                        )
                    }
                }
                is SavingsResult.Error -> _uiState.update { it.copy(subcategoryError = result.message) }
            }
        }
    }
}
