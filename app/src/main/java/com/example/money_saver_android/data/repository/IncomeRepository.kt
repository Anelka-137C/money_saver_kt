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
    suspend fun getIncomeById(id: Long): Income? = null
    suspend fun insertIncome(income: Income): Long
    suspend fun updateIncome(income: Income)
    suspend fun deleteIncome(id: Long)
    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
    suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {}
    suspend fun deleteCategory(categoryName: String)
    suspend fun countIncomesForCategory(categoryName: String): Int
    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String)
    suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int
    suspend fun preloadDefaultCategoriesIfNeeded()
    suspend fun replaceAllIncomes(incomes: List<Income>) {}
    suspend fun replaceAllCategories(categories: List<Category>) {}
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

    override suspend fun getIncomeById(id: Long): Income? {
        return incomeDao.getIncomeById(id)?.toDomain()
    }

    override suspend fun insertIncome(income: Income): Long {
        return incomeDao.insert(income.toEntity())
    }

    override suspend fun updateIncome(income: Income) {
        incomeDao.update(income.toEntity())
    }

    override suspend fun deleteIncome(id: Long) {
        incomeDao.deleteById(id)
    }

    override suspend fun replaceAllIncomes(incomes: List<Income>) {
        incomeDao.replaceAll(incomes.map { it.toEntity() })
    }

    override suspend fun replaceAllCategories(categories: List<Category>) {
        val uniqueCategories = categories.associateBy { it.name }.values.toList()
        val catEntities = uniqueCategories.map {
            IncomeCategoryEntity(name = it.name, icon = it.icon, color = it.color)
        }
        val subEntities = categories.flatMap { cat ->
            cat.subcategories.map { sub ->
                IncomeSubcategoryEntity(categoryName = cat.name, name = sub.name)
            }
        }.distinctBy { it.categoryName to it.name }
        categoryDao.replaceAllCategories(catEntities, subEntities)
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
        val entity = IncomeCategoryEntity(
            name = category.name,
            icon = category.icon,
            color = category.color
        )
        val rowId = categoryDao.insertCategory(entity)
        if (rowId == -1L) {
            categoryDao.updateCategory(entity)
        }
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

    override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        categoryDao.updateCategory(oldName = oldName, newName = newName, color = color, icon = icon)
    }

    override suspend fun countIncomesForCategory(categoryName: String): Int {
        return incomeDao.countIncomesForCategory(categoryName)
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        categoryDao.deleteSubcategoryByName(categoryName, subcategoryName)
    }

    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int {
        return incomeDao.countIncomesForSubcategory(categoryName, subcategoryName)
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
        subcategory = subcategory.orEmpty(),
        description = description,
        periodId = periodId
    )

    private fun Income.toEntity() = IncomeEntity(
        id = id,
        amount = amount,
        dateTime = dateTime,
        category = category,
        subcategory = subcategory.takeIf { it.isNotBlank() },
        description = description,
        periodId = periodId
    )
}
