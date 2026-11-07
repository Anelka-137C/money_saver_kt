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
    val subcategory: String
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

    @Insert
    suspend fun insert(entity: ExpenseEntity)

    @Update
    suspend fun update(entity: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
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
}

@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class, SubcategoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
}
