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

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val subcategory: String = "",
    val periodId: Long
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE periodId = :periodId ORDER BY id ASC")
    fun getBudgetsByPeriod(periodId: Long): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudgetEntity): Long

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: Long): BudgetEntity?

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM budgets WHERE category = :categoryName")
    suspend fun countBudgetsForCategory(categoryName: String): Int

    @Query("SELECT COUNT(*) FROM budgets WHERE category = :categoryName AND subcategory = :subcategoryName")
    suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int
}

@Entity(tableName = "budget_category")
data class BudgetCategoryEntity(
    @PrimaryKey val name: String,
    val icon: String = "💰",
    val color: String
)

@Entity(
    tableName = "budget_subcategory",
    foreignKeys = [
        ForeignKey(
            entity = BudgetCategoryEntity::class,
            parentColumns = ["name"],
            childColumns = ["categoryName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryName"])]
)
data class BudgetSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val name: String
)

data class BudgetCategoryWithSubcategories(
    @Embedded val category: BudgetCategoryEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "categoryName"
    )
    val subcategories: List<BudgetSubcategoryEntity>
)

@Dao
interface BudgetCategoryDao {
    @Transaction
    @Query("SELECT * FROM budget_category ORDER BY name ASC")
    fun getCategoriesWithSubcategories(): Flow<List<BudgetCategoryWithSubcategories>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: BudgetCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<BudgetCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<BudgetSubcategoryEntity>)

    @Query("DELETE FROM budget_category WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)

    @Query("DELETE FROM budget_subcategory WHERE categoryName = :categoryName AND name = :subcategoryName")
    suspend fun deleteSubcategoryByName(categoryName: String, subcategoryName: String)

    @Query("SELECT COUNT(*) FROM budget_category")
    suspend fun getCategoryCount(): Int
}

@Database(
    entities = [
        BudgetEntity::class,
        BudgetCategoryEntity::class,
        BudgetSubcategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetCategoryDao(): BudgetCategoryDao
}
