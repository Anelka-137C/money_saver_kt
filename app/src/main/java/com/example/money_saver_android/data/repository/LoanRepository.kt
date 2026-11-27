package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.local.LoanDao
import com.example.money_saver_android.data.local.LoanEntity
import com.example.money_saver_android.domain.model.Loan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LoanRepository {
    fun getAllLoans(): Flow<List<Loan>>
    suspend fun insertLoan(loan: Loan): Long
    suspend fun reassignSavingCategory(oldName: String, newName: String) {}
}

class RoomLoanRepository(
    private val dao: LoanDao
) : LoanRepository {

    override fun getAllLoans(): Flow<List<Loan>> {
        return dao.getAllLoans().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertLoan(loan: Loan): Long {
        return dao.insert(loan.toEntity())
    }

    override suspend fun reassignSavingCategory(oldName: String, newName: String) {
        dao.reassignSavingCategory(oldCategoryName = oldName, newCategoryName = newName)
    }
}

fun LoanEntity.toDomain(): Loan = Loan(
    id = id,
    personName = personName,
    amountLent = amountLent,
    amountToReceive = amountToReceive,
    note = note,
    savingCategory = savingCategory.orEmpty()
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    personName = personName,
    amountLent = amountLent,
    amountToReceive = amountToReceive,
    note = note,
    savingCategory = savingCategory.takeIf { it.isNotBlank() }
)
