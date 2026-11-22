package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCategories
import com.example.money_saver_android.data.local.CategoryDao
import com.example.money_saver_android.data.local.CategoryEntity
import com.example.money_saver_android.data.local.SubcategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val dao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> {
        return dao.getCategoriesWithSubcategories().map { list ->
            list.map { item ->
                Category(
                    name = item.category.name,
                    icon = item.category.icon,
                    color = item.category.color,
                    subcategories = item.subcategories.map { Subcategory(it.name) }
                )
            }
        }
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (dao.isCategoryTableCreated() > 0 && dao.getCategoryCount() == 0) {
            val categoryEntities = defaultCategories.map {
                CategoryEntity(
                    name = it.name,
                    icon = it.icon,
                    color = it.color
                )
            }
            dao.insertCategories(categoryEntities)
        }

        if (dao.isSubcategoryTableCreated() > 0 && dao.getSubcategoryCount() == 0) {
            val existingCategoryNames = dao.getAllCategories().first().map { it.name }.toSet()
            val subcategoryEntities = defaultCategories
                .filter { it.name in existingCategoryNames }
                .flatMap { cat ->
                    cat.subcategories.map { sub ->
                        SubcategoryEntity(
                            categoryName = cat.name,
                            name = sub.name
                        )
                    }
                }
            if (subcategoryEntities.isNotEmpty()) {
                dao.insertSubcategories(subcategoryEntities)
            }
        }
    }

    override suspend fun insertCategory(category: Category) {
        dao.insertCategory(
            CategoryEntity(
                name = category.name,
                icon = category.icon,
                color = category.color
            )
        )
        if (category.subcategories.isNotEmpty()) {
            val subEntities = category.subcategories.map {
                SubcategoryEntity(categoryName = category.name, name = it.name)
            }
            dao.insertSubcategories(subEntities)
        }
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        dao.insertSubcategory(
            SubcategoryEntity(
                categoryName = categoryName,
                name = subcategory.name
            )
        )
    }

    override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        dao.updateCategory(oldName = oldName, newName = newName, color = color, icon = icon)
    }

    override suspend fun deleteCategory(categoryName: String) {
        dao.deleteCategoryByName(categoryName)
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        dao.deleteSubcategoryByName(categoryName, subcategoryName)
    }

    override suspend fun countExpensesForCategory(categoryName: String): Int {
        return dao.countExpensesForCategory(categoryName)
    }

    override suspend fun countExpensesForSubcategory(categoryName: String, subcategoryName: String): Int {
        return dao.countExpensesForSubcategory(categoryName, subcategoryName)
    }
}
