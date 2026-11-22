package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class CategoryResult {
    object Success : CategoryResult()
    data class Error(val message: String) : CategoryResult()
}

class CategoryUseCase(
    private val repository: CategoryRepository
) {
    private val mutationMutex = Mutex()
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

    suspend fun updateCategory(
        oldName: String,
        newNameInput: String,
        colorInput: String,
        iconInput: String? = null
    ): CategoryResult = mutationMutex.withLock {
        val trimmedOldName = oldName.trim()
        val trimmedNewName = newNameInput.trim()
        if (trimmedNewName.isEmpty()) {
            return@withLock CategoryResult.Error("El nombre de la categoría no puede estar vacío")
        }

        val existingCategories = repository.getAllCategories().first()
        val targetCategory = existingCategories.find { it.name.equals(trimmedOldName, ignoreCase = true) }
            ?: return@withLock CategoryResult.Error("Categoría no encontrada")

        val isDuplicate = existingCategories.any {
            !it.name.equals(targetCategory.name, ignoreCase = true) &&
                it.name.equals(trimmedNewName, ignoreCase = true)
        }
        if (isDuplicate) {
            return@withLock CategoryResult.Error("Ya existe una categoría con este nombre")
        }

        val resolvedColor = if (colorInput.isBlank()) targetCategory.color else colorInput
        val resolvedIcon = iconInput?.takeIf { it.isNotBlank() } ?: targetCategory.icon

        repository.updateCategory(
            oldName = targetCategory.name,
            newName = trimmedNewName,
            color = resolvedColor,
            icon = resolvedIcon
        )
        CategoryResult.Success
    }

    suspend fun deleteCategory(categoryName: String): CategoryResult = mutationMutex.withLock {
        val trimmedCategory = categoryName.trim()
        if (trimmedCategory.isEmpty()) {
            return@withLock CategoryResult.Error("El nombre de la categoría no puede estar vacío")
        }
        val count = repository.countExpensesForCategory(trimmedCategory)
        if (count > 0) {
            return@withLock CategoryResult.Error("No se puede eliminar la categoría porque tiene gastos asociados")
        }
        repository.deleteCategory(trimmedCategory)
        CategoryResult.Success
    }

    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String): CategoryResult = mutationMutex.withLock {
        val trimmedCategory = categoryName.trim()
        val trimmedSubcategory = subcategoryName.trim()
        if (trimmedCategory.isEmpty() || trimmedSubcategory.isEmpty()) {
            return@withLock CategoryResult.Error("El nombre no puede estar vacío")
        }
        val count = repository.countExpensesForSubcategory(trimmedCategory, trimmedSubcategory)
        if (count > 0) {
            return@withLock CategoryResult.Error("No se puede eliminar la subcategoría porque tiene gastos asociados")
        }
        repository.deleteSubcategory(trimmedCategory, trimmedSubcategory)
        CategoryResult.Success
    }
}
