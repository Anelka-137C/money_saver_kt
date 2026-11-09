package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class IncomeResult {
    object Success : IncomeResult()
    data class Error(val message: String) : IncomeResult()
}

class IncomeUseCase(
    private val repository: IncomeRepository
) {
    private val deletionMutex = Mutex()
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

    suspend fun createCategory(
        nameInput: String,
        colorInput: String,
        icon: String = "💰"
    ): IncomeResult {
        val trimmedName = nameInput.trim()
        if (trimmedName.isEmpty()) {
            return IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }

        val existingCategories = repository.getAllCategories().first()
        val isDuplicate = existingCategories.any { it.name.equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            return IncomeResult.Error("Ya existe una categoría con este nombre")
        }

        val category = Category(
            name = trimmedName,
            icon = icon,
            color = if (colorInput.isBlank()) "#4E73DF" else colorInput,
            subcategories = emptyList()
        )
        repository.insertCategory(category)
        return IncomeResult.Success
    }

    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        repository.insertSubcategory(categoryName, subcategory)
    }

    suspend fun createSubcategory(
        categoryName: String,
        subcategoryNameInput: String
    ): IncomeResult {
        val trimmedSubcategory = subcategoryNameInput.trim()
        if (trimmedSubcategory.isEmpty()) {
            return IncomeResult.Error("El nombre de la subcategoría no puede estar vacío")
        }

        val categories = repository.getAllCategories().first()
        val targetCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
        val isDuplicate = targetCategory?.subcategories?.any {
            it.name.equals(trimmedSubcategory, ignoreCase = true)
        } == true

        if (isDuplicate) {
            return IncomeResult.Error("Ya existe una subcategoría con este nombre")
        }

        repository.insertSubcategory(
            categoryName = categoryName,
            subcategory = Subcategory(name = trimmedSubcategory)
        )
        return IncomeResult.Success
    }

    suspend fun deleteCategory(categoryName: String): IncomeResult = deletionMutex.withLock {
        val trimmedCategory = categoryName.trim()
        if (trimmedCategory.isEmpty()) {
            return@withLock IncomeResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val count = repository.countIncomesForCategory(trimmedCategory)
        if (count > 0) {
            return@withLock IncomeResult.Error("No se puede eliminar la categoría porque tiene ingresos asociados")
        }
        repository.deleteCategory(trimmedCategory)
        IncomeResult.Success
    }

    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String): IncomeResult = deletionMutex.withLock {
        val trimmedCategory = categoryName.trim()
        val trimmedSubcategory = subcategoryName.trim()
        if (trimmedCategory.isEmpty() || trimmedSubcategory.isEmpty()) {
            return@withLock IncomeResult.Error("El nombre no puede estar vacío")
        }
        val count = repository.countIncomesForSubcategory(trimmedCategory, trimmedSubcategory)
        if (count > 0) {
            return@withLock IncomeResult.Error("No se puede eliminar la subcategoría porque tiene ingresos asociados")
        }
        repository.deleteSubcategory(trimmedCategory, trimmedSubcategory)
        IncomeResult.Success
    }

    suspend fun saveIncome(
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String,
        descriptionInput: String,
        periodId: Long?
    ): IncomeResult {
        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
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

    suspend fun updateIncome(
        id: Long,
        amountInput: String,
        dateTimeInput: String,
        categoryInput: String,
        subcategoryInput: String,
        descriptionInput: String,
        periodId: Long?
    ): IncomeResult {
        val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            return IncomeResult.Error("El monto debe ser mayor a cero")
        }

        if (categoryInput.isBlank() || subcategoryInput.isBlank()) {
            return IncomeResult.Error("Debes seleccionar una categoría y subcategoría")
        }

        if (periodId == null) {
            return IncomeResult.Error("Período no seleccionado")
        }

        repository.updateIncome(
            Income(
                id = id,
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
