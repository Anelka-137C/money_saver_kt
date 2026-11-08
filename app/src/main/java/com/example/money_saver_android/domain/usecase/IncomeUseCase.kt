package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import kotlinx.coroutines.flow.Flow

sealed class IncomeResult {
    object Success : IncomeResult()
    data class Error(val message: String) : IncomeResult()
}

class IncomeUseCase(
    private val repository: IncomeRepository
) {
    fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> {
        return repository.getIncomesByPeriod(periodId)
    }

    fun calculateTotalIncome(incomes: List<Income>): Double {
        return incomes.sumOf { it.amount }
    }

    fun getCategories(): Flow<List<Category>> {
        return repository.getAllCategories()
    }

    suspend fun preloadDefaultCategoriesIfNeeded() {
        repository.preloadDefaultCategoriesIfNeeded()
    }

    suspend fun insertCategory(category: Category) {
        repository.insertCategory(category)
    }

    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        repository.insertSubcategory(categoryName, subcategory)
    }

    suspend fun deleteCategory(categoryName: String): IncomeResult {
        val count = repository.countIncomesForCategory(categoryName)
        if (count > 0) {
            return IncomeResult.Error("No se puede eliminar la categoría porque tiene ingresos asociados")
        }
        repository.deleteCategory(categoryName)
        return IncomeResult.Success
    }

    suspend fun saveIncome(
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String,
        descriptionInput: String,
        periodId: Long?
    ): IncomeResult {
        val amount = amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            return IncomeResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank() || subcategoryInput.isBlank()) {
            return IncomeResult.Error("Debes seleccionar una categoría y subcategoría")
        }

        if (periodId == null) {
            return IncomeResult.Error("Período no seleccionado")
        }

        repository.insertIncome(
            Income(
                amount = amount,
                dateTime = dateTimeInput,
                category = categoryInput,
                subcategory = subcategoryInput,
                description = descriptionInput,
                periodId = periodId
            )
        )
        return IncomeResult.Success
    }

    suspend fun deleteIncome(id: Long): IncomeResult {
        repository.deleteIncome(id)
        return IncomeResult.Success
    }
}
