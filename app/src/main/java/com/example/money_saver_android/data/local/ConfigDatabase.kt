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

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val formatPattern: String
)

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY name ASC")
    fun getAllCurrencies(): Flow<List<CurrencyEntity>>

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun getCurrencyCount(): Int

    @Query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'currencies'")
    suspend fun isCurrencyTableCreated(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencies(currencies: List<CurrencyEntity>)
}

@Entity(tableName = "financial_periods")
data class FinancialPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: String,
    val endDate: String
)

@Dao
interface FinancialPeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: FinancialPeriodEntity): Long

    @Query("SELECT * FROM financial_periods WHERE startDate <= :date AND endDate >= :date ORDER BY id DESC LIMIT 1")
    suspend fun getPeriodForDate(date: String): FinancialPeriodEntity?

    @Query("SELECT * FROM financial_periods ORDER BY id DESC LIMIT 1")
    fun getLatestPeriod(): Flow<FinancialPeriodEntity?>

    @Query("SELECT * FROM financial_periods ORDER BY startDate DESC")
    fun getAllPeriods(): Flow<List<FinancialPeriodEntity>>

    @Query("DELETE FROM financial_periods WHERE id = :id")
    suspend fun deletePeriod(id: Long)
}

@Database(
    entities = [CurrencyEntity::class, FinancialPeriodEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ConfigDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun financialPeriodDao(): FinancialPeriodDao
}
