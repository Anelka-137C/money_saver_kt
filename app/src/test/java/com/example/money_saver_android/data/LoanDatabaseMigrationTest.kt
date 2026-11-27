package com.example.money_saver_android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.money_saver_android.data.local.LoansDatabase
import com.example.money_saver_android.data.repository.RoomLoanRepository
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LoanDatabaseMigrationTest {

    @Test
    fun migration1To2_preservesExistingLoansAndPersistsInstallments() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val dbFile = File(context.cacheDir, "test_loans_migration_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        // Create version 1 schema and insert a pre-existing loan
        val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personName` TEXT NOT NULL,
                `amountLent` REAL NOT NULL,
                `amountToReceive` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `savingCategory` TEXT
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            "INSERT INTO `loans` (`id`, `personName`, `amountLent`, `amountToReceive`, `note`, `savingCategory`) " +
                "VALUES (1, 'María Castillo', 1500.0, 1650.0, 'Viaje', 'Emergencias')"
        )
        v1Db.version = 1
        v1Db.close()

        val roomDb = Room.databaseBuilder(context, LoansDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LoansDatabase.MIGRATION_1_2, LoansDatabase.MIGRATION_2_3, LoansDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val migratedLoans = repo.getAllLoans().first()
            assertEquals(1, migratedLoans.size)
            val existing = migratedLoans.single()
            assertEquals(1L, existing.id)
            assertEquals("María Castillo", existing.personName)
            assertEquals(1500.0, existing.amountLent, 0.001)
            assertEquals(1650.0, existing.amountToReceive, 0.001)
            assertEquals("Viaje", existing.note)
            assertEquals("Emergencias", existing.savingCategory)
            assertTrue(existing.installments.isEmpty())
            assertTrue(existing.payments.isEmpty())

            // Add installments to the migrated loan
            repo.replaceInstallments(
                loanId = existing.id,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-20", expectedAmount = 650.0),
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 1000.0)
                )
            )

            // Insert a new loan with installments
            val newId = repo.insertLoan(
                Loan(
                    personName = "Juan Pérez",
                    amountLent = 500.0,
                    amountToReceive = 550.0,
                    installments = listOf(LoanInstallment(dueDate = "2026-12-15", expectedAmount = 550.0))
                )
            )

            val updatedLoans = repo.getAllLoans().first()
            assertEquals(2, updatedLoans.size)
            val updatedExisting = updatedLoans.single { it.id == 1L }
            assertEquals(2, updatedExisting.installments.size)
            assertEquals("2026-12-01", updatedExisting.installments[0].dueDate)
            assertEquals(1000.0, updatedExisting.installments[0].expectedAmount, 0.001)
            assertEquals("2026-12-20", updatedExisting.installments[1].dueDate)

            val insertedNew = updatedLoans.single { it.id == newId }
            assertEquals(1, insertedNew.installments.size)
            assertEquals("2026-12-15", insertedNew.installments[0].dueDate)
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun migration2To3_preservesLoansAndInstallmentsAndPersistsPayments() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val dbFile = File(context.cacheDir, "test_loans_migration_v2_v3_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v2Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v2Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personName` TEXT NOT NULL,
                `amountLent` REAL NOT NULL,
                `amountToReceive` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `savingCategory` TEXT
            )
            """.trimIndent()
        )
        v2Db.execSQL(
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
        v2Db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_installments_loanId` ON `loan_installments` (`loanId`)")
        v2Db.execSQL(
            "INSERT INTO `loans` (`id`, `personName`, `amountLent`, `amountToReceive`, `note`, `savingCategory`) " +
                "VALUES (1, 'María Castillo', 1000.0, 1200.0, 'Viaje', 'Emergencias')"
        )
        v2Db.execSQL(
            "INSERT INTO `loan_installments` (`id`, `loanId`, `dueDate`, `expectedAmount`) " +
                "VALUES (1, 1, '2026-12-01', 600.0), (2, 1, '2026-12-20', 600.0)"
        )
        v2Db.version = 2
        v2Db.close()

        val roomDb = Room.databaseBuilder(context, LoansDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LoansDatabase.MIGRATION_2_3, LoansDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val initial = repo.getAllLoans().first().single()
            assertEquals("María Castillo", initial.personName)
            assertEquals(2, initial.installments.size)
            assertTrue(initial.payments.isEmpty())

            repo.recordPayment(
                payment = LoanPayment(
                    loanId = 1L,
                    amount = 600.0,
                    paidAt = "2026-11-27 12:30",
                    savingCategory = "Emergencias"
                ),
                updatedInstallments = listOf(
                    LoanInstallment(loanId = 1L, dueDate = "2026-12-20", expectedAmount = 600.0)
                )
            )

            val afterPayment = repo.getAllLoans().first().single()
            assertEquals(1, afterPayment.payments.size)
            assertEquals(600.0, afterPayment.payments[0].amount, 0.001)
            assertEquals("2026-11-27 12:30", afterPayment.payments[0].paidAt)
            assertEquals("Emergencias", afterPayment.payments[0].savingCategory)
            assertEquals(600.0, afterPayment.paidAmount, 0.001)
            assertEquals(600.0, afterPayment.pendingAmount, 0.001)
            assertEquals(1, afterPayment.installments.size)
            assertEquals("2026-12-20", afterPayment.installments[0].dueDate)

            repo.reassignSavingCategory("Emergencias", "Reserva")
            val afterRename = repo.getAllLoans().first().single()
            assertEquals("Reserva", afterRename.savingCategory)
            assertEquals("Reserva", afterRename.payments.single().savingCategory)
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun migration3To4_addsPaymentAmountToInstallmentsAndPreservesExistingData() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val dbFile = File(context.cacheDir, "test_loans_migration_v3_v4_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v3Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `personName` TEXT NOT NULL,
                `amountLent` REAL NOT NULL,
                `amountToReceive` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `savingCategory` TEXT
            )
            """.trimIndent()
        )
        v3Db.execSQL(
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
        v3Db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_installments_loanId` ON `loan_installments` (`loanId`)")
        v3Db.execSQL(
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
        v3Db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
        v3Db.execSQL(
            "INSERT INTO `loans` (`id`, `personName`, `amountLent`, `amountToReceive`, `note`, `savingCategory`) " +
                "VALUES (1, 'Sofía', 500.0, 500.0, '', 'Emergencias')"
        )
        v3Db.execSQL(
            "INSERT INTO `loan_installments` (`id`, `loanId`, `dueDate`, `expectedAmount`) " +
                "VALUES (10, 1, '2026-12-01', 250.0), (11, 1, '2026-12-20', 250.0)"
        )
        v3Db.version = 3
        v3Db.close()

        val roomDb = Room.databaseBuilder(context, LoansDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LoansDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RoomLoanRepository(roomDb.loanDao())
            val initial = repo.getAllLoans().first().single()
            assertEquals(2, initial.installments.size)
            assertEquals(0.0, initial.installments[0].paymentAmount, 0.001)

            // Pay 100 toward installment #11
            repo.recordPayment(
                payment = LoanPayment(
                    loanId = 1L,
                    amount = 100.0,
                    paidAt = "2026-11-27 13:10",
                    savingCategory = "Emergencias",
                    installmentId = 11L
                ),
                updatedInstallments = listOf(
                    initial.installments[0],
                    initial.installments[1].copy(paymentAmount = 100.0)
                )
            )
            val after = repo.getAllLoans().first().single()
            assertEquals(11L, after.payments.single().installmentId)
            assertEquals(0.0, after.installments.single { it.id == 10L }.paymentAmount, 0.001)
            assertEquals(100.0, after.installments.single { it.id == 11L }.paymentAmount, 0.001)
        } finally {
            roomDb.close()
            dbFile.delete()
        }
    }
}
