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

@Entity(tableName = "savings")
data class SavingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val subcategory: String? = null,
    val periodId: Long,
    val incomeId: Long = 0L,
    val incomeCategory: String = "",
    val dateTime: String = "",
    val note: String = ""
)

@Dao
interface SavingDao {
    @Query("SELECT * FROM savings ORDER BY id ASC")
    fun getAllSavings(): Flow<List<SavingEntity>>

    @Query("SELECT * FROM savings WHERE periodId = :periodId ORDER BY id ASC")
    fun getSavingsByPeriod(periodId: Long): Flow<List<SavingEntity>>

    @Query("SELECT * FROM savings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavingEntity): Long

    @Update
    suspend fun update(entity: SavingEntity)

    @Query("DELETE FROM savings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM savings WHERE category = :categoryName COLLATE NOCASE")
    suspend fun countSavingsForCategory(categoryName: String): Int

    @Query("SELECT COUNT(*) FROM savings WHERE category = :categoryName COLLATE NOCASE AND subcategory = :subcategoryName COLLATE NOCASE")
    suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int
}

@Entity(tableName = "saving_category")
data class SavingCategoryEntity(
    @PrimaryKey val name: String,
    val icon: String = "💰",
    val color: String
)

@Entity(
    tableName = "saving_subcategory",
    foreignKeys = [
        ForeignKey(
            entity = SavingCategoryEntity::class,
            parentColumns = ["name"],
            childColumns = ["categoryName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryName"])]
)
data class SavingSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val name: String
)

data class SavingCategoryWithSubcategories(
    @Embedded val category: SavingCategoryEntity,
    @Relation(parentColumn = "name", entityColumn = "categoryName")
    val subcategories: List<SavingSubcategoryEntity>
)

@Dao
interface SavingCategoryDao {
    @Transaction
    @Query("SELECT * FROM saving_category ORDER BY name ASC")
    fun getCategoriesWithSubcategories(): Flow<List<SavingCategoryWithSubcategories>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: SavingCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: SavingCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<SavingCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<SavingSubcategoryEntity>)

    @Query("DELETE FROM saving_category WHERE name = :name COLLATE NOCASE")
    suspend fun deleteCategoryByName(name: String)

    @Query("DELETE FROM saving_subcategory WHERE categoryName = :categoryName COLLATE NOCASE AND name = :subcategoryName COLLATE NOCASE")
    suspend fun deleteSubcategoryByName(categoryName: String, subcategoryName: String)

    @Query("SELECT COUNT(*) FROM saving_category")
    suspend fun getCategoryCount(): Int
}

@Database(
    entities = [
        SavingEntity::class,
        SavingCategoryEntity::class,
        SavingSubcategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SavingsDatabase : RoomDatabase() {
    abstract fun savingDao(): SavingDao
    abstract fun savingCategoryDao(): SavingCategoryDao
}
