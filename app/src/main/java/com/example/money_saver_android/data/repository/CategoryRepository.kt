package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun preloadDefaultCategoriesIfNeeded()
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
    suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {}
    suspend fun deleteCategory(categoryName: String) {}
    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
    suspend fun countExpensesForCategory(categoryName: String): Int = 0
    suspend fun countExpensesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
    suspend fun replaceAllCategories(categories: List<Category>) {}
}
