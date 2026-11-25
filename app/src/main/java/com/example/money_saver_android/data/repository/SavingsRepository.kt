package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCategories
import com.example.money_saver_android.data.local.SavingCategoryDao
import com.example.money_saver_android.data.local.SavingCategoryEntity
import com.example.money_saver_android.data.local.SavingDao
import com.example.money_saver_android.data.local.SavingEntity
import com.example.money_saver_android.data.local.SavingSubcategoryEntity
import com.example.money_saver_android.domain.model.Saving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

interface SavingsRepository {
    fun getAllSavings(): Flow<List<Saving>> = emptyFlow()
    fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>>
    suspend fun getSavingById(id: Long): Saving?
    suspend fun insertSaving(saving: Saving): Long
    suspend fun updateSaving(saving: Saving)
    suspend fun deleteSaving(id: Long)
    suspend fun countSavingsForCategory(categoryName: String): Int
    suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int

    fun getAllCategories(): Flow<List<Category>>
    suspend fun insertCategory(category: Category)
    suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory)
    suspend fun deleteCategory(categoryName: String)
    suspend fun deleteSubcategory(categoryName: String, subcategoryName: String)
    suspend fun preloadDefaultCategoriesIfNeeded()
}

class RoomSavingsRepository(
    private val savingDao: SavingDao,
    private val categoryDao: SavingCategoryDao? = null
) : SavingsRepository {

    override fun getAllSavings(): Flow<List<Saving>> {
        return savingDao.getAllSavings().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> {
        return savingDao.getSavingsByPeriod(periodId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSavingById(id: Long): Saving? {
        return savingDao.getById(id)?.toDomain()
    }

    override suspend fun insertSaving(saving: Saving): Long {
        return savingDao.insert(saving.toEntity())
    }

    override suspend fun updateSaving(saving: Saving) {
        savingDao.update(saving.toEntity())
    }

    override suspend fun deleteSaving(id: Long) {
        savingDao.deleteById(id)
    }

    override suspend fun countSavingsForCategory(categoryName: String): Int {
        return savingDao.countSavingsForCategory(categoryName)
    }

    override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int {
        return savingDao.countSavingsForSubcategory(categoryName, subcategoryName)
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
        val entity = SavingCategoryEntity(
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
                    SavingSubcategoryEntity(categoryName = category.name, name = it.name)
                }
            )
        }
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        val dao = categoryDao ?: return
        dao.insertSubcategories(
            listOf(SavingSubcategoryEntity(categoryName = categoryName, name = subcategory.name))
        )
    }

    override suspend fun deleteCategory(categoryName: String) {
        categoryDao?.deleteCategoryByName(categoryName)
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        categoryDao?.deleteSubcategoryByName(categoryName, subcategoryName)
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        val dao = categoryDao ?: return
        if (dao.getCategoryCount() == 0) {
            dao.insertCategories(
                defaultCategories.map { SavingCategoryEntity(name = it.name, icon = it.icon, color = it.color) }
            )
            dao.insertSubcategories(
                defaultCategories.flatMap { cat ->
                    cat.subcategories.map { sub -> SavingSubcategoryEntity(categoryName = cat.name, name = sub.name) }
                }
            )
        }
    }
}

fun SavingEntity.toDomain(): Saving = Saving(
    id = id,
    amount = amount,
    category = category,
    subcategory = subcategory.orEmpty(),
    periodId = periodId,
    incomeId = incomeId,
    incomeCategory = incomeCategory,
    dateTime = dateTime,
    note = note
)

fun Saving.toEntity(): SavingEntity = SavingEntity(
    id = id,
    amount = amount,
    category = category,
    subcategory = subcategory.takeIf { it.isNotBlank() },
    periodId = periodId,
    incomeId = incomeId,
    incomeCategory = incomeCategory,
    dateTime = dateTime,
    note = note
)
