package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed class CategoryResult {
    object Success : CategoryResult()
    data class Error(val message: String) : CategoryResult()
}

class CategoryUseCase(
    private val repository: CategoryRepository
) {
    fun getCategories(): Flow<List<Category>> {
        return repository.getAllCategories()
    }

    suspend fun preloadDefaultCategoriesIfNeeded() {
        repository.preloadDefaultCategoriesIfNeeded()
    }

    suspend fun createCategory(
        nameInput: String,
        colorInput: String,
        icon: String = "💰"
    ): CategoryResult {
        val trimmedName = nameInput.trim()
        if (trimmedName.isEmpty()) {
            return CategoryResult.Error("El nombre de la categoría no puede estar vacío")
        }

        val existingCategories = repository.getAllCategories().first()
        val isDuplicate = existingCategories.any { it.name.equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            return CategoryResult.Error("Ya existe una categoría con este nombre")
        }

        val category = Category(
            name = trimmedName,
            icon = icon,
            color = if (colorInput.isBlank()) "#4E73DF" else colorInput,
            subcategories = emptyList()
        )
        repository.insertCategory(category)
        return CategoryResult.Success
    }

    suspend fun createSubcategory(
        categoryName: String,
        subcategoryNameInput: String
    ): CategoryResult {
        val trimmedSubcategory = subcategoryNameInput.trim()
        if (trimmedSubcategory.isEmpty()) {
            return CategoryResult.Error("El nombre de la subcategoría no puede estar vacío")
        }

        val categories = repository.getAllCategories().first()
        val targetCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
        val isDuplicate = targetCategory?.subcategories?.any {
            it.name.equals(trimmedSubcategory, ignoreCase = true)
        } == true

        if (isDuplicate) {
            return CategoryResult.Error("Ya existe una subcategoría con este nombre")
        }

        repository.insertSubcategory(
            categoryName = categoryName,
            subcategory = Subcategory(name = trimmedSubcategory)
        )
        return CategoryResult.Success
    }
}
