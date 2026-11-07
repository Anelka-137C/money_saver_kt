package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun preloadDefaultCategoriesIfNeeded()
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
}
