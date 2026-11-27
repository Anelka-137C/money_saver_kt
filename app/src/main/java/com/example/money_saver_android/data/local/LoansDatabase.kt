package com.example.money_saver_android.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amountLent: Double,
    val amountToReceive: Double,
    val note: String = "",
    val savingCategory: String? = null
)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LoanEntity): Long

    @Query("SELECT * FROM loans WHERE savingCategory IS NOT NULL AND TRIM(savingCategory) != ''")
    suspend fun getLoansWithSavingCategory(): List<LoanEntity>

    @Update
    suspend fun updateAll(entities: List<LoanEntity>)

    /**
     * Reassigns linked loans from [oldCategoryName] to [newCategoryName] using Unicode-safe
     * case-insensitive comparison while never modifying personal loans (`savingCategory` null/blank).
     */
    @Transaction
    suspend fun reassignSavingCategory(oldCategoryName: String, newCategoryName: String) {
        val trimmedOld = oldCategoryName.trim { it.isWhitespace() }
        if (trimmedOld.isEmpty()) return
        val normalizedNew = newCategoryName.trim { it.isWhitespace() }.takeIf { it.isNotEmpty() }
        val toUpdate = getLoansWithSavingCategory()
            .filter { it.savingCategory?.trim { ch -> ch.isWhitespace() }?.equals(trimmedOld, ignoreCase = true) == true }
            .map { it.copy(savingCategory = normalizedNew) }
        if (toUpdate.isNotEmpty()) {
            updateAll(toUpdate)
        }
    }
}

@Database(entities = [LoanEntity::class], version = 1, exportSchema = false)
abstract class LoansDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao
}
