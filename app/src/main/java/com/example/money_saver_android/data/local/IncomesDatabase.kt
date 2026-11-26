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
    val subcategory: String? = null,
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

    @Query("SELECT * FROM incomes WHERE id = :id LIMIT 1")
    suspend fun getIncomeById(id: Long): IncomeEntity?

    @Insert
    suspend fun insert(entity: IncomeEntity): Long

    @Update
    suspend fun update(entity: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM incomes WHERE LOWER(TRIM(category)) = LOWER(TRIM(:categoryName))")
    suspend fun countIncomesForCategory(categoryName: String): Int

    @Query("SELECT COUNT(*) FROM incomes WHERE LOWER(TRIM(category)) = LOWER(TRIM(:categoryName)) AND LOWER(TRIM(subcategory)) = LOWER(TRIM(:subcategoryName))")
    suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int

    @Query("DELETE FROM incomes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IncomeEntity>)

    @Transaction
    suspend fun replaceAll(entities: List<IncomeEntity>) {
        deleteAll()
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}

@Dao
interface IncomeCategoryDao {
    @Transaction
    @Query("SELECT * FROM incomes_category ORDER BY name ASC")
    fun getCategoriesWithSubcategories(): Flow<List<IncomeCategoryWithSubcategories>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: IncomeCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: IncomeCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<IncomeCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<IncomeSubcategoryEntity>)

    @Query("DELETE FROM incomes_category WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)

    @Query("DELETE FROM incomes_subcategory WHERE categoryName = :categoryName AND name = :subcategoryName")
    suspend fun deleteSubcategoryByName(categoryName: String, subcategoryName: String)

    @Query("SELECT COUNT(*) FROM incomes_category")
    suspend fun getCategoryCount(): Int

    @Query("UPDATE incomes_category SET color = :color, icon = :icon WHERE name = :name")
    suspend fun updateCategoryStyle(name: String, color: String, icon: String)

    @Query("UPDATE incomes_subcategory SET categoryName = :newCategoryName WHERE categoryName = :oldCategoryName")
    suspend fun reassignSubcategories(oldCategoryName: String, newCategoryName: String)

    @Query("UPDATE incomes SET category = :newCategoryName WHERE LOWER(TRIM(category)) = LOWER(TRIM(:oldCategoryName))")
    suspend fun reassignIncomesCategory(oldCategoryName: String, newCategoryName: String)

    @Transaction
    suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        if (oldName == newName) {
            updateCategoryStyle(name = oldName, color = color, icon = icon)
        } else {
            insertCategory(IncomeCategoryEntity(name = newName, icon = icon, color = color))
            reassignSubcategories(oldCategoryName = oldName, newCategoryName = newName)
            reassignIncomesCategory(oldCategoryName = oldName, newCategoryName = newName)
            deleteCategoryByName(oldName)
        }
    }

    @Query("DELETE FROM incomes_subcategory")
    suspend fun deleteAllSubcategories()

    @Query("DELETE FROM incomes_category")
    suspend fun deleteAllCategories()

    @Transaction
    suspend fun replaceAllCategories(
        categories: List<IncomeCategoryEntity>,
        subcategories: List<IncomeSubcategoryEntity>
    ) {
        deleteAllSubcategories()
        deleteAllCategories()
        if (categories.isNotEmpty()) {
            insertCategories(categories)
        }
        if (subcategories.isNotEmpty()) {
            insertSubcategories(subcategories)
        }
    }
}

@Database(
    entities = [
        IncomeEntity::class,
        IncomeCategoryEntity::class,
        IncomeSubcategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class IncomesDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun incomeCategoryDao(): IncomeCategoryDao
}
