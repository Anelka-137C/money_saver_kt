package com.example.money_saver_android.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val subcategory: String
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insert(entity: ExpenseEntity)
}

@Database(entities = [ExpenseEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> {
        return dao.getAll().map { list ->
            list.map { entity ->
                Expense(
                    id = entity.id,
                    amount = entity.amount,
                    date = entity.date,
                    description = entity.description,
                    category = entity.category,
                    subcategory = entity.subcategory
                )
            }
        }
    }

    override suspend fun insertExpense(expense: Expense) {
        dao.insert(
            ExpenseEntity(
                id = expense.id,
                amount = expense.amount,
                date = expense.date,
                description = expense.description,
                category = expense.category,
                subcategory = expense.subcategory
            )
        )
    }
}
