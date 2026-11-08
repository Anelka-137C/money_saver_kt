package com.example.money_saver_android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.PeriodResult
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeriodUiState(
    val startDateInput: String = "",
    val endDateInput: String = "",
    val activePeriod: FinancialPeriod? = null,
    val allPeriods: List<FinancialPeriod> = emptyList(),
    val isPeriodConfigured: Boolean = false,
    val errorMessage: String? = null
)

class PeriodViewModel(
    private val periodUseCase: PeriodUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeriodUiState())
    val uiState: StateFlow<PeriodUiState> = _uiState.asStateFlow()

    init {
        checkActivePeriod()
        observeAllPeriods()
    }

    private fun observeAllPeriods() {
        viewModelScope.launch {
            periodUseCase.getAllPeriods().collect { periods ->
                _uiState.update { it.copy(allPeriods = periods) }
            }
        }
    }

    fun onStartDateChanged(date: String) {
        _uiState.update { it.copy(startDateInput = date, errorMessage = null) }
    }

    fun onEndDateChanged(date: String) {
        _uiState.update { it.copy(endDateInput = date, errorMessage = null) }
    }

    fun savePeriod() {
        val start = _uiState.value.startDateInput
        val end = _uiState.value.endDateInput

        viewModelScope.launch {
            when (val result = periodUseCase.savePeriod(FinancialPeriod(id = 0L, startDate = start, endDate = end))) {
                is PeriodResult.Success -> {
                    _uiState.update {
                        it.copy(
                            startDateInput = "",
                            endDateInput = "",
                            errorMessage = null
                        )
                    }
                    checkActivePeriod()
                }
                is PeriodResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun updatePeriod(period: FinancialPeriod) {
        viewModelScope.launch {
            when (val result = periodUseCase.savePeriod(period)) {
                is PeriodResult.Success -> {
                    _uiState.update { it.copy(errorMessage = null) }
                    checkActivePeriod()
                }
                is PeriodResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun deletePeriod(id: Long) {
        viewModelScope.launch {
            when (val result = periodUseCase.deletePeriod(id)) {
                is PeriodResult.Success -> {
                    _uiState.update { it.copy(errorMessage = null) }
                    checkActivePeriod()
                }
                is PeriodResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    private fun checkActivePeriod() {
        viewModelScope.launch {
            val period = periodUseCase.getActivePeriod()
            _uiState.update {
                it.copy(
                    activePeriod = period,
                    isPeriodConfigured = period != null
                )
            }
        }
    }
}
