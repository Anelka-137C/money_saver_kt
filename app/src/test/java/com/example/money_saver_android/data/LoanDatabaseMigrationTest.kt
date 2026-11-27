package com.example.money_saver_android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.money_saver_android.data.local.LoansDatabase
import com.example.money_saver_android.data.repository.RoomLoanRepository
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
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
            .addMigrations(LoansDatabase.MIGRATION_1_2)
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
}
