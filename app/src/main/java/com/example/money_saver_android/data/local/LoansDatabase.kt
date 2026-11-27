package com.example.money_saver_android.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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

    @Query("UPDATE loans SET savingCategory = :newCategoryName WHERE LOWER(TRIM(COALESCE(savingCategory, ''))) = LOWER(TRIM(:oldCategoryName))")
    suspend fun reassignSavingCategory(oldCategoryName: String, newCategoryName: String)
}

@Database(entities = [LoanEntity::class], version = 1, exportSchema = false)
abstract class LoansDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao
}
