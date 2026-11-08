package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCategories
import com.example.money_saver_android.data.local.IncomeCategoryDao
import com.example.money_saver_android.data.local.IncomeCategoryEntity
import com.example.money_saver_android.data.local.IncomeDao
import com.example.money_saver_android.data.local.IncomeEntity
import com.example.money_saver_android.data.local.IncomeSubcategoryEntity
import com.example.money_saver_android.domain.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface IncomeRepository {
    fun getIncomesByPeriod(periodId: Long): Flow<List<Income>>
    suspend fun insertIncome(income: Income): Long
    suspend fun deleteIncome(id: Long)
    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
    suspend fun deleteCategory(categoryName: String)
    suspend fun countIncomesForCategory(categoryName: String): Int
    suspend fun preloadDefaultCategoriesIfNeeded()
}

class RoomIncomeRepository(
    private val incomeDao: IncomeDao,
    private val categoryDao: IncomeCategoryDao
) : IncomeRepository {

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> {
        return incomeDao.getIncomesByPeriod(periodId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertIncome(income: Income): Long {
        return incomeDao.insert(income.toEntity())
    }

    override suspend fun deleteIncome(id: Long) {
        incomeDao.deleteById(id)
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getCategoriesWithSubcategories().map { list ->
            list.map { withSubs ->
                Category(
                    name = withSubs.category.name,
                    icon = withSubs.category.icon,
                    color = withSubs.category.color,
                    subcategories = withSubs.subcategories.map { Subcategory(name = it.name) }
                )
            }
        }
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(
            IncomeCategoryEntity(
                name = category.name,
                icon = category.icon,
                color = category.color
            )
        )
        if (category.subcategories.isNotEmpty()) {
            categoryDao.insertSubcategories(
                category.subcategories.map {
                    IncomeSubcategoryEntity(
                        categoryName = category.name,
                        name = it.name
                    )
                }
            )
        }
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        categoryDao.insertSubcategories(
            listOf(
                IncomeSubcategoryEntity(
                    categoryName = categoryName,
                    name = subcategory.name
                )
            )
        )
    }

    override suspend fun deleteCategory(categoryName: String) {
        categoryDao.deleteCategoryByName(categoryName)
    }

    override suspend fun countIncomesForCategory(categoryName: String): Int {
        return incomeDao.countIncomesForCategory(categoryName)
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (categoryDao.getCategoryCount() == 0) {
            val categoriesToInsert = defaultCategories.map {
                IncomeCategoryEntity(name = it.name, icon = it.icon, color = it.color)
            }
            categoryDao.insertCategories(categoriesToInsert)

            val subcategoriesToInsert = defaultCategories.flatMap { cat ->
                cat.subcategories.map { sub ->
                    IncomeSubcategoryEntity(categoryName = cat.name, name = sub.name)
                }
            }
            categoryDao.insertSubcategories(subcategoriesToInsert)
        }
    }

    private fun IncomeEntity.toDomain() = Income(
        id = id,
        amount = amount,
        dateTime = dateTime,
        category = category,
        subcategory = subcategory,
        description = description,
        periodId = periodId
    )

    private fun Income.toEntity() = IncomeEntity(
        id = id,
        amount = amount,
        dateTime = dateTime,
        category = category,
        subcategory = subcategory,
        description = description,
        periodId = periodId
    )
}
