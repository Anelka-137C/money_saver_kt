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
    val errorMessage: String? = null,
    val toastMessage: String? = null
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
                    _uiState.update {
                        it.copy(
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

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
