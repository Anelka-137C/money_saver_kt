package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.local.LoanDao
import com.example.money_saver_android.data.local.LoanEntity
import com.example.money_saver_android.data.local.LoanInstallmentEntity
import com.example.money_saver_android.data.local.LoanPaymentEntity
import com.example.money_saver_android.data.local.LoanWithDetails
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LoanRepository {
    fun getAllLoans(): Flow<List<Loan>>
    suspend fun insertLoan(loan: Loan): Long
    suspend fun updateLoan(loan: Loan) {}
    suspend fun deleteLoan(loanId: Long) {}
    suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {}
    suspend fun recordPayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {}
    suspend fun updatePayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {}
    suspend fun deletePayment(loanId: Long, paymentId: Long, updatedInstallments: List<LoanInstallment>) {}
    suspend fun reassignSavingCategory(oldName: String, newName: String) {}
}

class RoomLoanRepository(
    private val dao: LoanDao
) : LoanRepository {

    override fun getAllLoans(): Flow<List<Loan>> {
        return dao.getAllLoansWithDetails().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertLoan(loan: Loan): Long {
        return dao.insertLoanWithInstallments(
            loan = loan.toEntity(),
            installments = loan.installments.map { it.toEntity(loanId = loan.id) }
        )
    }

    override suspend fun updateLoan(loan: Loan) {
        dao.updateLoanWithInstallments(
            loan = loan.toEntity(),
            installments = loan.installments.map { it.toEntity(loanId = loan.id) }
        )
    }

    override suspend fun deleteLoan(loanId: Long) {
        dao.deleteById(loanId)
    }

    override suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {
        dao.replaceInstallments(
            loanId = loanId,
            installments = installments.map { it.toEntity(loanId = loanId) }
        )
    }

    override suspend fun recordPayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
        dao.recordPaymentAndReplaceInstallments(
            payment = payment.toEntity(),
            installments = updatedInstallments.map { it.toEntity(loanId = payment.loanId) }
        )
    }

    override suspend fun updatePayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
        dao.updatePaymentAndSyncInstallments(
            payment = payment.toEntity(),
            installments = updatedInstallments.map { it.toEntity(loanId = payment.loanId) }
        )
    }

    override suspend fun deletePayment(loanId: Long, paymentId: Long, updatedInstallments: List<LoanInstallment>) {
        dao.deletePaymentAndSyncInstallments(
            loanId = loanId,
            paymentId = paymentId,
            installments = updatedInstallments.map { it.toEntity(loanId = loanId) }
        )
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

fun LoanInstallmentEntity.toDomain(): LoanInstallment = LoanInstallment(
    id = id,
    loanId = loanId,
    dueDate = dueDate,
    expectedAmount = expectedAmount,
    paymentAmount = paymentAmount
)

fun LoanPaymentEntity.toDomain(): LoanPayment = LoanPayment(
    id = id,
    loanId = loanId,
    amount = amount,
    paidAt = paidAt,
    savingCategory = savingCategory,
    installmentId = installmentId
)

fun LoanWithDetails.toDomain(): Loan = loan.toDomain().copy(
    installments = installments
        .sortedWith(compareBy<LoanInstallmentEntity> { it.dueDate }.thenBy { it.id })
        .map { it.toDomain() },
    payments = payments
        .sortedWith(compareBy<LoanPaymentEntity> { it.paidAt }.thenBy { it.id })
        .map { it.toDomain() }
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    personName = personName,
    amountLent = amountLent,
    amountToReceive = amountToReceive,
    note = note,
    savingCategory = savingCategory.takeIf { it.isNotBlank() }
)

fun LoanInstallment.toEntity(loanId: Long = this.loanId): LoanInstallmentEntity = LoanInstallmentEntity(
    id = id,
    loanId = loanId,
    dueDate = dueDate,
    expectedAmount = expectedAmount,
    paymentAmount = paymentAmount
)

fun LoanPayment.toEntity(loanId: Long = this.loanId): LoanPaymentEntity = LoanPaymentEntity(
    id = id,
    loanId = loanId,
    amount = amount,
    paidAt = paidAt,
    savingCategory = savingCategory,
    installmentId = installmentId
)
