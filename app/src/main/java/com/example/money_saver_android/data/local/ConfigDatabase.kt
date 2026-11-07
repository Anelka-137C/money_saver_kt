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

@Database(
    entities = [CurrencyEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ConfigDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
}
