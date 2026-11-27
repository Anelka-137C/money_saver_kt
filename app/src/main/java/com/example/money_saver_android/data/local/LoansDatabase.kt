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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(
    tableName = "loan_installments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["loanId"])]
)
data class LoanInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val dueDate: String,
    val expectedAmount: Double
)

data class LoanWithInstallments(
    @Embedded val loan: LoanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "loanId"
    )
    val installments: List<LoanInstallmentEntity>
)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Transaction
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoansWithInstallments(): Flow<List<LoanWithInstallments>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LoanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(entities: List<LoanInstallmentEntity>)

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId")
    suspend fun deleteInstallmentsByLoanId(loanId: Long)

    @Transaction
    suspend fun insertLoanWithInstallments(
        loan: LoanEntity,
        installments: List<LoanInstallmentEntity>
    ): Long {
        val loanId = insert(loan)
        if (installments.isNotEmpty()) {
            insertInstallments(installments.map { it.copy(id = 0, loanId = loanId) })
        }
        return loanId
    }

    @Transaction
    suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallmentEntity>) {
        deleteInstallmentsByLoanId(loanId)
        if (installments.isNotEmpty()) {
            insertInstallments(installments.map { it.copy(id = 0, loanId = loanId) })
        }
    }

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

@Database(
    entities = [LoanEntity::class, LoanInstallmentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LoansDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loan_installments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `loanId` INTEGER NOT NULL,
                        `dueDate` TEXT NOT NULL,
                        `expectedAmount` REAL NOT NULL,
                        FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_loan_installments_loanId` ON `loan_installments` (`loanId`)"
                )
            }
        }
    }
}
