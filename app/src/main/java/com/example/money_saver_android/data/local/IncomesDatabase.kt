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

    @Update
    suspend fun update(entity: IncomeEntity)

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
        IncomeEntity::class,
        IncomeCategoryEntity::class,
        IncomeSubcategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IncomesDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun incomeCategoryDao(): IncomeCategoryDao
}
