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
import java.math.BigDecimal

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
    val expectedAmount: Double,
    val paymentAmount: Double = 0.0
)

@Entity(
    tableName = "loan_payments",
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
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val paidAt: String,
    val savingCategory: String,
    val installmentId: Long? = null
)

data class LoanWithDetails(
    @Embedded val loan: LoanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "loanId"
    )
    val installments: List<LoanInstallmentEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "loanId"
    )
    val payments: List<LoanPaymentEntity>
)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Transaction
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoansWithDetails(): Flow<List<LoanWithDetails>>

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId")
    suspend fun getInstallmentsByLoanId(loanId: Long): List<LoanInstallmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LoanEntity): Long

    @Update
    suspend fun update(entity: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :loanId")
    suspend fun deleteById(loanId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(entities: List<LoanInstallmentEntity>)

    @Update
    suspend fun updateInstallments(entities: List<LoanInstallmentEntity>)

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId")
    suspend fun deleteInstallmentsByLoanId(loanId: Long)

    @Query("DELETE FROM loan_installments WHERE id IN (:ids)")
    suspend fun deleteInstallmentsByIds(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(entity: LoanPaymentEntity): Long

    @Update
    suspend fun updatePayment(entity: LoanPaymentEntity)

    @Query("DELETE FROM loan_payments WHERE id = :paymentId")
    suspend fun deletePaymentById(paymentId: Long)

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
    suspend fun updateLoanWithInstallments(
        loan: LoanEntity,
        installments: List<LoanInstallmentEntity>
    ) {
        update(loan)
        syncInstallments(loan.id, installments)
    }

    @Transaction
    suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallmentEntity>) {
        syncInstallments(loanId, installments)
    }

    @Transaction
    suspend fun syncInstallments(loanId: Long, installments: List<LoanInstallmentEntity>) {
        val existingIds = getInstallmentsByLoanId(loanId).map { it.id }.toSet()
        val seenUpdateIds = mutableSetOf<Long>()
        val toUpdate = mutableListOf<LoanInstallmentEntity>()
        val toInsert = mutableListOf<LoanInstallmentEntity>()
        for (item in installments) {
            if (item.id > 0L && item.id in existingIds && seenUpdateIds.add(item.id)) {
                toUpdate.add(item.copy(loanId = loanId))
            } else {
                toInsert.add(item.copy(id = 0, loanId = loanId))
            }
        }
        val idsToDelete = existingIds.filterNot { it in seenUpdateIds }
        if (idsToDelete.isNotEmpty()) {
            deleteInstallmentsByIds(idsToDelete)
        }
        if (toUpdate.isNotEmpty()) {
            updateInstallments(toUpdate)
        }
        if (toInsert.isNotEmpty()) {
            insertInstallments(toInsert)
        }
    }

    @Transaction
    suspend fun recordPaymentAndReplaceInstallments(
        payment: LoanPaymentEntity,
        installments: List<LoanInstallmentEntity>
    ): Long {
        val paymentId = insertPayment(payment.copy(id = 0))
        syncInstallments(loanId = payment.loanId, installments = installments)
        return paymentId
    }

    @Transaction
    suspend fun updatePaymentAndSyncInstallments(
        payment: LoanPaymentEntity,
        installments: List<LoanInstallmentEntity>
    ) {
        updatePayment(payment)
        syncInstallments(loanId = payment.loanId, installments = installments)
    }

    @Transaction
    suspend fun deletePaymentAndSyncInstallments(
        loanId: Long,
        paymentId: Long,
        installments: List<LoanInstallmentEntity>
    ) {
        deletePaymentById(paymentId)
        syncInstallments(loanId = loanId, installments = installments)
    }

    @Query("SELECT * FROM loans WHERE savingCategory IS NOT NULL AND TRIM(savingCategory) != ''")
    suspend fun getLoansWithSavingCategory(): List<LoanEntity>

    @Query("SELECT * FROM loan_payments WHERE TRIM(savingCategory) != ''")
    suspend fun getPaymentsWithSavingCategory(): List<LoanPaymentEntity>

    @Update
    suspend fun updateAll(entities: List<LoanEntity>)

    @Update
    suspend fun updateAllPayments(entities: List<LoanPaymentEntity>)

    /**
     * Reassigns linked loans and loan payments from [oldCategoryName] to [newCategoryName]
     * using Unicode-safe case-insensitive comparison while never modifying personal loans
     * (`savingCategory` null/blank).
     */
    @Transaction
    suspend fun reassignSavingCategory(oldCategoryName: String, newCategoryName: String) {
        val trimmedOld = oldCategoryName.trim { it.isWhitespace() }
        val trimmedNew = newCategoryName.trim { it.isWhitespace() }
        if (trimmedOld.isEmpty() || trimmedNew.isEmpty()) return
        val toUpdate = getLoansWithSavingCategory()
            .filter { it.savingCategory?.trim { ch -> ch.isWhitespace() }?.equals(trimmedOld, ignoreCase = true) == true }
            .map { it.copy(savingCategory = trimmedNew) }
        if (toUpdate.isNotEmpty()) {
            updateAll(toUpdate)
        }
        val paymentsToUpdate = getPaymentsWithSavingCategory()
            .filter { it.savingCategory.trim { ch -> ch.isWhitespace() }.equals(trimmedOld, ignoreCase = true) }
            .map { it.copy(savingCategory = trimmedNew) }
        if (paymentsToUpdate.isNotEmpty()) {
            updateAllPayments(paymentsToUpdate)
        }
    }
}

@Database(
    entities = [LoanEntity::class, LoanInstallmentEntity::class, LoanPaymentEntity::class],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loan_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `loanId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `paidAt` TEXT NOT NULL,
                        `savingCategory` TEXT NOT NULL,
                        FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `loan_installments` ADD COLUMN `paymentAmount` REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE `loan_payments` ADD COLUMN `installmentId` INTEGER DEFAULT NULL"
                )

                val paidByLoanId = mutableMapOf<Long, BigDecimal>()
                db.query("SELECT `loanId`, `amount` FROM `loan_payments`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val loanId = cursor.getLong(0)
                        val amount = cursor.getDouble(1)
                        if (amount.isFinite() && amount > 0.0) {
                            paidByLoanId[loanId] = (paidByLoanId[loanId] ?: BigDecimal.ZERO)
                                .add(BigDecimal.valueOf(amount))
                        }
                    }
                }

                val targetByLoanId = mutableMapOf<Long, BigDecimal>()
                if (paidByLoanId.isNotEmpty()) {
                    db.query("SELECT `id`, `amountToReceive` FROM `loans`").use { cursor ->
                        while (cursor.moveToNext()) {
                            val loanId = cursor.getLong(0)
                            val amountToReceive = cursor.getDouble(1)
                            if (amountToReceive.isFinite() && amountToReceive > 0.0) {
                                targetByLoanId[loanId] = BigDecimal.valueOf(amountToReceive)
                            }
                        }
                    }
                }

                for ((loanId, totalPaid) in paidByLoanId) {
                    if (totalPaid <= BigDecimal.ZERO) continue
                    val installments = mutableListOf<Pair<Long, BigDecimal>>()
                    var totalExpected = BigDecimal.ZERO
                    db.query(
                        "SELECT `id`, `expectedAmount` FROM `loan_installments` WHERE `loanId` = ? ORDER BY `dueDate` ASC, `id` ASC",
                        arrayOf(loanId.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val instId = cursor.getLong(0)
                            val expected = cursor.getDouble(1)
                            if (expected.isFinite() && expected > 0.0) {
                                val expectedDec = BigDecimal.valueOf(expected)
                                installments.add(instId to expectedDec)
                                totalExpected = totalExpected.add(expectedDec)
                            }
                        }
                    }
                    if (installments.isEmpty()) continue

                    val loanTarget = targetByLoanId[loanId] ?: totalExpected
                    val pendingTarget = loanTarget.subtract(totalPaid).max(BigDecimal.ZERO)
                    var remainingToBackfill = totalExpected.subtract(pendingTarget).max(BigDecimal.ZERO)
                    if (remainingToBackfill <= BigDecimal.ZERO) continue

                    for ((instId, expectedDec) in installments) {
                        if (remainingToBackfill <= BigDecimal.ZERO) break
                        val applied = remainingToBackfill.min(expectedDec)
                        if (applied > BigDecimal.ZERO) {
                            db.execSQL(
                                "UPDATE `loan_installments` SET `paymentAmount` = ? WHERE `id` = ?",
                                arrayOf<Any>(applied.toDouble(), instId)
                            )
                            remainingToBackfill = remainingToBackfill.subtract(applied)
                        }
                    }
                }
            }
        }
    }
}
