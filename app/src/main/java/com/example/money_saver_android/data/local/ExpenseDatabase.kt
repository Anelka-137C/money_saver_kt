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

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val dateTime: String,
    val description: String = "",
    val category: String,
    val subcategory: String,
    val periodId: Long
)

@Entity(tableName = "incomes_category")
data class IncomeCategoryEntity(
    @PrimaryKey val name: String,
    val icon: String = "💰",
    val color: String
)

@Entity(
    tableName = "incomes_subcategory",
    foreignKeys = [
        ForeignKey(
            entity = IncomeCategoryEntity::class,
            parentColumns = ["name"],
            childColumns = ["categoryName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryName"])]
)
data class IncomeSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val name: String
)

data class IncomeCategoryWithSubcategories(
    @Embedded val category: IncomeCategoryEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "categoryName"
    )
    val subcategories: List<IncomeSubcategoryEntity>
)

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes WHERE periodId = :periodId ORDER BY dateTime DESC, id DESC")
    fun getIncomesByPeriod(periodId: Long): Flow<List<IncomeEntity>>

    @Insert
    suspend fun insert(entity: IncomeEntity): Long

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM incomes WHERE category = :categoryName")
    suspend fun countIncomesForCategory(categoryName: String): Int
}

@Dao
interface IncomeCategoryDao {
    @Transaction
    @Query("SELECT * FROM incomes_category ORDER BY name ASC")
    fun getCategoriesWithSubcategories(): Flow<List<IncomeCategoryWithSubcategories>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: IncomeCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<IncomeCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<IncomeSubcategoryEntity>)

    @Query("DELETE FROM incomes_category WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)

    @Query("SELECT COUNT(*) FROM incomes_category")
    suspend fun getCategoryCount(): Int
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
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun incomeDao(): IncomeDao
    abstract fun incomeCategoryDao(): IncomeCategoryDao
}
