package com.example.money_saver_android.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val subcategory: String? = null,
    val periodId: Long? = null,
    val budgetId: Long? = null,
    val budgetCategory: String? = null,
    val fundingSource: String? = null,
    val savingCategory: String? = null
)

@Entity(tableName = "expenses_categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val icon: String = "💰",
    val color: String
)

@Entity(
    tableName = "expenses_subcategories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["name"],
            childColumns = ["categoryName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryName"])]
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val name: String
)

data class CategoryWithSubcategories(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "categoryName"
    )
    val subcategories: List<SubcategoryEntity>
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE COALESCE(periodId, 0) = :periodId ORDER BY id DESC")
    fun getByPeriod(periodId: Long): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insert(entity: ExpenseEntity)

    @Update
    suspend fun update(entity: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE expenses SET budgetCategory = :newCategoryName WHERE LOWER(TRIM(COALESCE(budgetCategory, ''))) = LOWER(TRIM(:oldCategoryName))")
    suspend fun reassignBudgetCategory(oldCategoryName: String, newCategoryName: String)

    @Query("UPDATE expenses SET savingCategory = :newCategoryName WHERE LOWER(TRIM(COALESCE(savingCategory, ''))) = LOWER(TRIM(:oldCategoryName))")
    suspend fun reassignSavingCategory(oldCategoryName: String, newCategoryName: String)
}

@Dao
interface CategoryDao {
    @Transaction
    @Query("SELECT * FROM expenses_categories ORDER BY name ASC")
    fun getCategoriesWithSubcategories(): Flow<List<CategoryWithSubcategories>>

    @Query("SELECT * FROM expenses_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM expenses_subcategories ORDER BY name ASC")
    fun getAllSubcategories(): Flow<List<SubcategoryEntity>>

    @Query("SELECT COUNT(*) FROM expenses_categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT COUNT(*) FROM expenses_subcategories")
    suspend fun getSubcategoryCount(): Int

    @Query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'expenses_categories'")
    suspend fun isCategoryTableCreated(): Int

    @Query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'expenses_subcategories'")
    suspend fun isSubcategoryTableCreated(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<SubcategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategory(subcategory: SubcategoryEntity)

    @Query("DELETE FROM expenses_categories WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)

    @Query("DELETE FROM expenses_subcategories WHERE categoryName = :categoryName AND name = :subcategoryName")
    suspend fun deleteSubcategoryByName(categoryName: String, subcategoryName: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE LOWER(TRIM(category)) = LOWER(TRIM(:categoryName))")
    suspend fun countExpensesForCategory(categoryName: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE LOWER(TRIM(category)) = LOWER(TRIM(:categoryName)) AND LOWER(TRIM(COALESCE(subcategory, ''))) = LOWER(TRIM(:subcategoryName))")
    suspend fun countExpensesForSubcategory(categoryName: String, subcategoryName: String): Int

    @Query("UPDATE expenses_categories SET color = :color, icon = :icon WHERE name = :name")
    suspend fun updateCategoryStyle(name: String, color: String, icon: String)

    @Query("UPDATE expenses_subcategories SET categoryName = :newCategoryName WHERE categoryName = :oldCategoryName")
    suspend fun reassignSubcategories(oldCategoryName: String, newCategoryName: String)

    @Query("UPDATE expenses SET category = :newCategoryName WHERE LOWER(TRIM(category)) = LOWER(TRIM(:oldCategoryName))")
    suspend fun reassignExpensesCategory(oldCategoryName: String, newCategoryName: String)

    @Transaction
    suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        if (oldName == newName) {
            updateCategoryStyle(name = oldName, color = color, icon = icon)
        } else {
            insertCategory(CategoryEntity(name = newName, icon = icon, color = color))
            reassignSubcategories(oldCategoryName = oldName, newCategoryName = newName)
            reassignExpensesCategory(oldCategoryName = oldName, newCategoryName = newName)
            deleteCategoryByName(oldName)
        }
    }
}

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        SubcategoryEntity::class,
        IncomeEntity::class,
        IncomeCategoryEntity::class,
        IncomeSubcategoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun incomeDao(): IncomeDao
    abstract fun incomeCategoryDao(): IncomeCategoryDao
}
