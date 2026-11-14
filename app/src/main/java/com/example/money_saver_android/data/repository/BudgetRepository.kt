package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCategories
import com.example.money_saver_android.data.local.BudgetCategoryDao
import com.example.money_saver_android.data.local.BudgetCategoryEntity
import com.example.money_saver_android.data.local.BudgetDao
import com.example.money_saver_android.data.local.BudgetEntity
import com.example.money_saver_android.data.local.BudgetSubcategoryEntity
import com.example.money_saver_android.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

interface BudgetRepository {
    fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>>
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun getBudgetById(id: Long): Budget?
    suspend fun deleteBudget(id: Long)

    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
    suspend fun deleteCategory(categoryName: String)
    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String)
    suspend fun countBudgetsForCategory(categoryName: String): Int
    suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int
    suspend fun preloadDefaultCategoriesIfNeeded()
}

class RoomBudgetRepository(
    private val budgetDao: BudgetDao,
    private val categoryDao: BudgetCategoryDao? = null
) : BudgetRepository {

    override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> {
        return budgetDao.getBudgetsByPeriod(periodId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertBudget(budget: Budget): Long {
        return budgetDao.insert(budget.toEntity())
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        return budgetDao.getBudgetById(id)?.toDomain()
    }

    override suspend fun deleteBudget(id: Long) {
        budgetDao.deleteById(id)
    }

    override fun getAllCategories(): Flow<List<Category>> {
        val dao = categoryDao ?: return emptyFlow()
        return dao.getCategoriesWithSubcategories().map { list ->
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
        val dao = categoryDao ?: return
        val entity = BudgetCategoryEntity(
            name = category.name,
            icon = category.icon,
            color = category.color
        )
        val rowId = dao.insertCategory(entity)
        if (rowId == -1L) {
            dao.updateCategory(entity)
        }
        if (category.subcategories.isNotEmpty()) {
            dao.insertSubcategories(
                category.subcategories.map {
                    BudgetSubcategoryEntity(
                        categoryName = category.name,
                        name = it.name
                    )
                }
            )
        }
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        val dao = categoryDao ?: return
        dao.insertSubcategories(
            listOf(
                BudgetSubcategoryEntity(
                    categoryName = categoryName,
                    name = subcategory.name
                )
            )
        )
    }

    override suspend fun deleteCategory(categoryName: String) {
        categoryDao?.deleteCategoryByName(categoryName)
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        categoryDao?.deleteSubcategoryByName(categoryName, subcategoryName)
    }

    override suspend fun countBudgetsForCategory(categoryName: String): Int {
        return budgetDao.countBudgetsForCategory(categoryName)
    }

    override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int {
        return budgetDao.countBudgetsForSubcategory(categoryName, subcategoryName)
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        val dao = categoryDao ?: return
        if (dao.getCategoryCount() == 0) {
            val categoriesToInsert = defaultCategories.map {
                BudgetCategoryEntity(name = it.name, icon = it.icon, color = it.color)
            }
            dao.insertCategories(categoriesToInsert)

            val subcategoriesToInsert = defaultCategories.flatMap { cat ->
                cat.subcategories.map { sub ->
                    BudgetSubcategoryEntity(categoryName = cat.name, name = sub.name)
                }
            }
            dao.insertSubcategories(subcategoriesToInsert)
        }
    }
}

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    amount = amount,
    category = category,
    subcategory = subcategory,
    periodId = periodId
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    amount = amount,
    category = category,
    subcategory = subcategory,
    periodId = periodId
)
