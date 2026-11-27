package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LoanRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.LoanPayment
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanInstallmentValidationResult
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.PartialPaymentScheduleOption
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.domain.usecase.calculateCategoryAvailableSavings
import com.example.money_saver_android.domain.usecase.calculateLoanDueBadgeInfo
import com.example.money_saver_android.domain.usecase.calculateSavingsNetBalances
import com.example.money_saver_android.domain.usecase.formatLoanShortDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeLoanRepository : LoanRepository {
    val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
    private var nextInstallmentId = 1L
    private var nextPaymentId = 1L

    override fun getAllLoans(): Flow<List<Loan>> = loansFlow

    override suspend fun insertLoan(loan: Loan): Long {
        val id = (loansFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val savedInstallments = loan.installments
            .map { it.copy(id = nextInstallmentId++, loanId = id) }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        val savedPayments = loan.payments
            .map { it.copy(id = nextPaymentId++, loanId = id) }
            .sortedWith(compareBy({ it.paidAt }, { it.id }))
        loansFlow.value = listOf(loan.copy(id = id, installments = savedInstallments, payments = savedPayments)) + loansFlow.value
        return id
    }

    override suspend fun updateLoan(loan: Loan) {
        val savedInstallments = loan.installments
            .map { it.copy(id = nextInstallmentId++, loanId = loan.id) }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = loansFlow.value.map { existing ->
            if (existing.id == loan.id) {
                loan.copy(installments = savedInstallments, payments = existing.payments)
            } else {
                existing
            }
        }
    }

    override suspend fun deleteLoan(loanId: Long) {
        loansFlow.value = loansFlow.value.filterNot { it.id == loanId }
    }

    override suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {
        val savedInstallments = installments
            .map { it.copy(id = nextInstallmentId++, loanId = loanId) }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = loansFlow.value.map {
            if (it.id == loanId) it.copy(installments = savedInstallments) else it
        }
    }

    override suspend fun recordPayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
        val savedInstallments = updatedInstallments
            .map { inst ->
                val resolvedId = if (inst.id > 0L) inst.id else nextInstallmentId++
                inst.copy(id = resolvedId, loanId = payment.loanId)
            }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        val savedPayment = payment.copy(id = nextPaymentId++)
        loansFlow.value = loansFlow.value.map { existing ->
            if (existing.id == payment.loanId) {
                existing.copy(
                    installments = savedInstallments,
                    payments = (existing.payments + savedPayment).sortedWith(compareBy({ it.paidAt }, { it.id }))
                )
            } else {
                existing
            }
        }
    }

    override suspend fun updatePayment(payment: LoanPayment, updatedInstallments: List<LoanInstallment>) {
        val savedInstallments = updatedInstallments
            .map { inst ->
                val resolvedId = if (inst.id > 0L) inst.id else nextInstallmentId++
                inst.copy(id = resolvedId, loanId = payment.loanId)
            }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = loansFlow.value.map { existing ->
            if (existing.id == payment.loanId) {
                existing.copy(
                    installments = savedInstallments,
                    payments = existing.payments
                        .map { if (it.id == payment.id) payment else it }
                        .sortedWith(compareBy({ it.paidAt }, { it.id }))
                )
            } else {
                existing
            }
        }
    }

    override suspend fun deletePayment(loanId: Long, paymentId: Long, updatedInstallments: List<LoanInstallment>) {
        val savedInstallments = updatedInstallments
            .map { inst ->
                val resolvedId = if (inst.id > 0L) inst.id else nextInstallmentId++
                inst.copy(id = resolvedId, loanId = loanId)
            }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = loansFlow.value.map { existing ->
            if (existing.id == loanId) {
                existing.copy(
                    installments = savedInstallments,
                    payments = existing.payments.filterNot { it.id == paymentId }
                )
            } else {
                existing
            }
        }
    }

    override suspend fun reassignSavingCategory(oldName: String, newName: String) {
        loansFlow.value = loansFlow.value.map { loan ->
            val updatedLoanCat = if (loan.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)) {
                newName
            } else {
                loan.savingCategory
            }
            val updatedPayments = loan.payments.map { p ->
                if (p.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)) {
                    p.copy(savingCategory = newName)
                } else {
                    p
                }
            }
            loan.copy(savingCategory = updatedLoanCat, payments = updatedPayments)
        }
    }
}

private class TestSavingsRepo : SavingsRepository {
    val savingsFlow = MutableStateFlow<List<Saving>>(emptyList())
    val categoriesFlow = MutableStateFlow(listOf(Category("Emergencias", "🚨", "#3B82F6", emptyList())))

    override fun getAllSavings(): Flow<List<Saving>> = savingsFlow
    override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
        savingsFlow.map { list -> list.filter { it.periodId == periodId } }
    override suspend fun getSavingById(id: Long): Saving? = savingsFlow.value.find { it.id == id }
    override suspend fun insertSaving(saving: Saving): Long {
        val id = (savingsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        savingsFlow.value = savingsFlow.value + saving.copy(id = id)
        return id
    }
    override suspend fun updateSaving(saving: Saving) {
        savingsFlow.value = savingsFlow.value.map { if (it.id == saving.id) saving else it }
    }
    override suspend fun deleteSaving(id: Long) {
        savingsFlow.value = savingsFlow.value.filter { it.id != id }
    }
    override suspend fun countSavingsForCategory(categoryName: String): Int =
        savingsFlow.value.count { it.category.equals(categoryName, ignoreCase = true) }
    override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
    override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
    override suspend fun insertCategory(category: Category) { categoriesFlow.value += category }
    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
    override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        categoriesFlow.value = categoriesFlow.value.map {
            if (it.name == oldName) it.copy(name = newName, color = color, icon = icon) else it
        }
        savingsFlow.value = savingsFlow.value.map {
            if (it.category.equals(oldName, ignoreCase = true)) it.copy(category = newName) else it
        }
    }
    override suspend fun deleteCategory(categoryName: String) {}
    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
    override suspend fun preloadDefaultCategoriesIfNeeded() {}
}

private class TestExpenseRepo : ExpenseRepository {
    val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    override fun getAllExpenses(): Flow<List<Expense>> = expensesFlow
    override suspend fun insertExpense(expense: Expense) {
        val id = (expensesFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        expensesFlow.value = expensesFlow.value + expense.copy(id = id)
    }
    override suspend fun updateExpense(expense: Expense) {}
    override suspend fun deleteExpense(id: Long) {}
}

private class TestIncomeRepo : IncomeRepository {
    val incomesFlow = MutableStateFlow(
        listOf(Income(id = 1L, amount = 2000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = 1L))
    )
    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        incomesFlow.map { list -> list.filter { it.periodId == periodId } }
    override suspend fun getIncomeById(id: Long): Income? = incomesFlow.value.find { it.id == id }
    override suspend fun insertIncome(income: Income): Long = 1L
    override suspend fun updateIncome(income: Income) {}
    override suspend fun deleteIncome(id: Long) {}
    override suspend fun countIncomesForCategory(categoryName: String): Int = 0
    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
    override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override suspend fun insertCategory(category: Category) {}
    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
    override suspend fun deleteCategory(categoryName: String) {}
    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
    override suspend fun preloadDefaultCategoriesIfNeeded() {}
}

class LoanUseCaseTest {

    private lateinit var loanRepo: FakeLoanRepository
    private lateinit var savingsRepo: TestSavingsRepo
    private lateinit var expenseRepo: TestExpenseRepo
    private lateinit var incomeRepo: TestIncomeRepo
    private lateinit var loanUseCase: LoanUseCase
    private lateinit var expenseUseCase: ExpenseUseCase
    private lateinit var savingsUseCase: SavingsUseCase

    @Before
    fun setUp() {
        loanRepo = FakeLoanRepository()
        savingsRepo = TestSavingsRepo()
        expenseRepo = TestExpenseRepo()
        incomeRepo = TestIncomeRepo()
        loanUseCase = LoanUseCase(
            loanRepository = loanRepo,
            savingsRepository = savingsRepo,
            expenseRepository = expenseRepo,
            currentDateProvider = { "2026-11-26" }
        )
        expenseUseCase = ExpenseUseCase(expenseRepo, null, savingsRepo, loanRepo)
        savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, null, expenseRepo, loanRepo)
    }

    @Test
    fun saveLoan_personalLoanWithoutSavings_savesAndUpdatesReceivable() = runBlocking {
        val result = loanUseCase.saveLoan(
            personNameInput = "María Castillo",
            amountLentInput = "1500",
            amountToReceiveInput = "1650",
            noteInput = "Préstamo previo"
        )
        assertTrue(result is LoanResult.Success)
        val loans = loanUseCase.getAllLoans().first()
        assertEquals(1, loans.size)
        assertEquals("María Castillo", loans[0].personName)
        assertEquals(1500.0, loans[0].amountLent, 0.001)
        assertEquals(1650.0, loans[0].amountToReceive, 0.001)
        assertEquals("", loans[0].savingCategory)
        assertEquals(1650.0, loanUseCase.calculateTotalReceivable(loans), 0.001)
    }

    @Test
    fun saveLoan_fromSavings_deductsBalanceAcrossModulesAndProtectsSaving() = runBlocking {
        val savingId = savingsRepo.insertSaving(
            Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )

        val loanResult = loanUseCase.saveLoan(
            personNameInput = "Juan Pérez",
            amountLentInput = "300",
            amountToReceiveInput = "350",
            savingCategoryInput = "emergencias"
        )
        assertTrue(loanResult is LoanResult.Success)
        assertEquals("Emergencias", loanRepo.loansFlow.value.single().savingCategory)

        val balances = calculateSavingsNetBalances(
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            categories = savingsRepo.categoriesFlow.value,
            loans = loanRepo.loansFlow.value
        )
        assertEquals(200.0, balances.single().netBalance, 0.001)

        // Expense from savings exceeding remaining 200 must fail
        val overExpense = expenseUseCase.saveExpense(
            amountInput = "250",
            dateInput = "2026-11-10",
            descriptionInput = "Reparación",
            categoryInput = "Hogar",
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Emergencias"
        )
        assertTrue(overExpense is ExpenseResult.Error)

        // Deleting or reducing saving below 300 must be blocked
        assertTrue(savingsUseCase.deleteSaving(savingId) is SavingsResult.Error)
        val reduceSaving = savingsUseCase.updateSaving(
            id = savingId,
            amountInput = "250",
            categoryInput = "Emergencias",
            periodId = 1L,
            incomeId = 1L,
            dateTimeInput = "2026-11-05 10:00"
        )
        assertTrue(reduceSaving is SavingsResult.Error)

        // Renaming saving category updates linked loan category
        val renameResult = savingsUseCase.updateCategory("Emergencias", "Reserva", "#3B82F6")
        assertTrue(renameResult is SavingsResult.Success)
        assertEquals("Reserva", loanRepo.loansFlow.value.single().savingCategory)
    }

    @Test
    fun saveLoan_invalidInputs_returnErrorsAndDoNotPersist() = runBlocking {
        savingsRepo.insertSaving(
            Saving(amount = 200.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )

        assertTrue(loanUseCase.saveLoan("   ", "100", "100") is LoanResult.Error)
        assertTrue(loanUseCase.saveLoan("Ana", "0", "100") is LoanResult.Error)
        assertTrue(loanUseCase.saveLoan("Ana", "-50", "100") is LoanResult.Error)
        assertTrue(loanUseCase.saveLoan("Ana", "100", "80") is LoanResult.Error)
        assertTrue(loanUseCase.saveLoan("Ana", "250", "250", savingCategoryInput = "Emergencias") is LoanResult.Error)

        assertTrue(loanRepo.loansFlow.value.isEmpty())
    }

    @Test
    fun saveLoan_withValidInstallmentsMatchingExactTotal_savesSortedInstallments() = runBlocking {
        val result = loanUseCase.saveLoan(
            personNameInput = "María Castillo",
            amountLentInput = "0.25",
            amountToReceiveInput = "0.30",
            installmentInputs = listOf(
                LoanInstallmentInput("2026-12-15", "0.20"),
                LoanInstallmentInput("2026-11-26", "0.10")
            )
        )
        assertTrue(result is LoanResult.Success)
        val saved = loanRepo.loansFlow.value.single()
        assertEquals(2, saved.installments.size)
        assertEquals("2026-11-26", saved.installments[0].dueDate)
        assertEquals(0.10, saved.installments[0].expectedAmount, 0.0001)
        assertEquals("2026-12-15", saved.installments[1].dueDate)
        assertEquals(0.20, saved.installments[1].expectedAmount, 0.0001)
    }

    @Test
    fun saveLoan_withInvalidInstallmentsOrMismatchedSum_returnsErrorWithoutPersisting() = runBlocking {
        // Past date
        assertTrue(
            loanUseCase.saveLoan(
                "Ana", "100", "100",
                installmentInputs = listOf(LoanInstallmentInput("2026-11-25", "100"))
            ) is LoanResult.Error
        )
        // Invalid calendar date
        assertTrue(
            loanUseCase.saveLoan(
                "Ana", "100", "100",
                installmentInputs = listOf(LoanInstallmentInput("2026-02-30", "100"))
            ) is LoanResult.Error
        )
        // Zero or negative installment amount
        assertTrue(
            loanUseCase.saveLoan(
                "Ana", "100", "100",
                installmentInputs = listOf(LoanInstallmentInput("2026-12-01", "0"))
            ) is LoanResult.Error
        )
        assertTrue(
            loanUseCase.saveLoan(
                "Ana", "100", "100",
                installmentInputs = listOf(LoanInstallmentInput("2026-12-01", "-10"))
            ) is LoanResult.Error
        )
        // Sum does not equal amountToReceive
        assertTrue(
            loanUseCase.saveLoan(
                "Ana", "100", "120",
                installmentInputs = listOf(
                    LoanInstallmentInput("2026-12-01", "60"),
                    LoanInstallmentInput("2026-12-15", "59.99")
                )
            ) is LoanResult.Error
        )

        assertTrue(loanRepo.loansFlow.value.isEmpty())
    }

    @Test
    fun updateLoanInstallments_supportsAddingRemovingEmptyingAndKeepingPersistedOverdue() = runBlocking {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Carlos",
                amountLent = 500.0,
                amountToReceive = 600.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-11-20", expectedAmount = 200.0),
                    LoanInstallment(dueDate = "2026-12-10", expectedAmount = 400.0)
                )
            )
        )
        val persistedPast = loanRepo.loansFlow.value.single().installments.first()

        // Keeps persisted overdue installment while replacing the future one with two valid future installments
        val updateResult = loanUseCase.updateLoanInstallments(
            loanId = loanId,
            installmentInputs = listOf(
                LoanInstallmentInput(persistedPast.dueDate, "200", persistedId = persistedPast.id),
                LoanInstallmentInput("2026-12-01", "250"),
                LoanInstallmentInput("2026-12-20", "150")
            )
        )
        assertTrue(updateResult is LoanResult.Success)
        assertEquals(3, loanRepo.loansFlow.value.single().installments.size)

        // Adding a brand-new past installment must be rejected
        val invalidNewPast = loanUseCase.updateLoanInstallments(
            loanId = loanId,
            installmentInputs = listOf(
                LoanInstallmentInput("2026-11-25", "600")
            )
        )
        assertTrue(invalidNewPast is LoanResult.Error)
        assertEquals(3, loanRepo.loansFlow.value.single().installments.size)

        // Emptying schedule is allowed
        val clearResult = loanUseCase.updateLoanInstallments(loanId = loanId, installmentInputs = emptyList())
        assertTrue(clearResult is LoanResult.Success)
        assertTrue(loanRepo.loansFlow.value.single().installments.isEmpty())
    }

    @Test
    fun validateInstallmentDraft_andBadgeCalculation_andLocalizedFormatting() {
        assertTrue(loanUseCase.validateInstallmentDraft("2026-11-25", "100") is LoanInstallmentValidationResult.Error)
        assertTrue(loanUseCase.validateInstallmentDraft("2026-11-26", "0") is LoanInstallmentValidationResult.Error)
        assertTrue(loanUseCase.validateInstallmentDraft("2026-11-26", "100") is LoanInstallmentValidationResult.Valid)

        assertNull(calculateLoanDueBadgeInfo(emptyList(), "2026-11-26"))

        val upcoming = calculateLoanDueBadgeInfo(
            listOf(
                LoanInstallment(dueDate = "2026-12-20", expectedAmount = 50.0),
                LoanInstallment(dueDate = "2026-11-26", expectedAmount = 50.0)
            ),
            todayIso = "2026-11-26"
        )
        assertEquals("2026-11-26", upcoming?.nextDueDate)
        assertEquals(1, upcoming?.additionalCount)
        assertFalse(upcoming?.isOverdue ?: true)

        val overdue = calculateLoanDueBadgeInfo(
            listOf(
                LoanInstallment(dueDate = "2026-11-25", expectedAmount = 50.0),
                LoanInstallment(dueDate = "2026-12-05", expectedAmount = 50.0),
                LoanInstallment(dueDate = "2026-12-20", expectedAmount = 50.0)
            ),
            todayIso = "2026-11-26"
        )
        assertEquals("2026-11-25", overdue?.nextDueDate)
        assertEquals(2, overdue?.additionalCount)
        assertTrue(overdue?.isOverdue == true)

        assertEquals("30 Jul 2026", formatLoanShortDate("2026-07-30", "es"))
        assertEquals("15 Aug 2026", formatLoanShortDate("2026-08-15", "en"))
        assertEquals("15 Ago 2026", formatLoanShortDate("2026-08-15", "pt"))
        assertTrue(formatLoanShortDate("2026-08-15", "de").contains("2026"))
    }

    @Test
    fun updateLoan_andDeleteLoan_adjustSavingsBalancesCorrectly() = runBlocking {
        savingsRepo.insertSaving(
            Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )
        assertTrue(
            loanUseCase.saveLoan("Ana", "300", "350", savingCategoryInput = "Emergencias") is LoanResult.Success
        )
        val loanId = loanRepo.loansFlow.value.single().id

        // Increasing from 300 to 450 in same category must succeed (500 total available excluding self)
        val okUpdate = loanUseCase.updateLoan(
            loanId = loanId,
            personNameInput = "Ana María",
            amountLentInput = "450",
            amountToReceiveInput = "500",
            noteInput = "Editado",
            savingCategoryInput = "Emergencias",
            installmentInputs = listOf(LoanInstallmentInput("2026-12-10", "500"))
        )
        assertTrue(okUpdate is LoanResult.Success)
        val updated = loanRepo.loansFlow.value.single()
        assertEquals("Ana María", updated.personName)
        assertEquals(450.0, updated.amountLent, 0.001)
        assertEquals(500.0, updated.amountToReceive, 0.001)
        assertEquals(1, updated.installments.size)

        // Exceeding 500 must fail
        assertTrue(
            loanUseCase.updateLoan(loanId, "Ana María", "501", "550", savingCategoryInput = "Emergencias") is LoanResult.Error
        )

        // Deleting loan removes it and frees full $500 balance
        assertTrue(loanUseCase.deleteLoan(loanId) is LoanResult.Success)
        assertTrue(loanRepo.loansFlow.value.isEmpty())
        val balances = calculateSavingsNetBalances(
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            categories = savingsRepo.categoriesFlow.value,
            loans = loanRepo.loansFlow.value
        )
        assertEquals(500.0, balances.single().netBalance, 0.001)
    }

    @Test
    fun recordLoanPayment_savingsBackedLoan_creditsOriginCategoryIncludingInterestAndSettles() = runBlocking {
        savingsRepo.insertSaving(
            Saving(amount = 500.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )
        assertTrue(
            loanUseCase.saveLoan(
                personNameInput = "Carlos",
                amountLentInput = "300",
                amountToReceiveInput = "350",
                savingCategoryInput = "Emergencias",
                installmentInputs = listOf(LoanInstallmentInput("2026-12-10", "350"))
            ) is LoanResult.Success
        )
        val loanId = loanRepo.loansFlow.value.single().id

        // Partial payment of 150 keeping current future installment
        val partialResult = loanUseCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "150",
            paidAtInput = "2026-11-26 14:00",
            scheduleOption = PartialPaymentScheduleOption.KeepCurrent
        )
        assertTrue(partialResult is LoanResult.Success)
        val afterPartial = loanRepo.loansFlow.value.single()
        assertEquals(150.0, afterPartial.paidAmount, 0.001)
        assertEquals(200.0, afterPartial.pendingAmount, 0.001)
        assertFalse(afterPartial.isSettled)
        assertEquals(350.0, afterPartial.installments.single().expectedAmount, 0.001)
        assertEquals(150.0, afterPartial.installments.single().paymentAmount, 0.001)
        assertEquals(200.0, afterPartial.installments.single().remainingAmount, 0.001)

        // Net balance should be 500 - 300 + 150 = 350
        val balanceAfterPartial = calculateCategoryAvailableSavings(
            categoryName = "Emergencias",
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            loans = loanRepo.loansFlow.value
        ).toDouble()
        assertEquals(350.0, balanceAfterPartial, 0.001)

        // Final payment of 200 settles loan, marks installment paid (paymentAmount == 350), and credits total 350 (net 550)
        val finalResult = loanUseCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "200",
            paidAtInput = "2026-11-27 09:30"
        )
        assertTrue(finalResult is LoanResult.Success)
        val settled = loanRepo.loansFlow.value.single()
        assertTrue(settled.isSettled)
        assertEquals(350.0, settled.paidAmount, 0.001)
        assertEquals(0.0, settled.pendingAmount, 0.001)
        assertTrue(settled.installments.single().isPaid)
        assertEquals(350.0, settled.installments.single().paymentAmount, 0.001)
        assertNull(calculateLoanDueBadgeInfo(settled.installments, "2026-11-27"))
        assertEquals(0.0, loanUseCase.calculateTotalReceivable(listOf(settled)), 0.001)
        assertEquals(350.0, loanUseCase.calculateTotalPaid(listOf(settled)), 0.001)

        val finalNet = calculateSavingsNetBalances(
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            categories = savingsRepo.categoriesFlow.value,
            loans = loanRepo.loansFlow.value
        ).single().netBalance
        assertEquals(550.0, finalNet, 0.001)
    }

    @Test
    fun recordLoanPayment_personalLoanWithoutInstallments_requiresCategoryAndNewDueDateWhenPartial() = runBlocking {
        savingsRepo.insertCategory(Category("Viajes", "✈️", "#10B981", emptyList()))
        val loanId = loanRepo.insertLoan(
            Loan(personName = "María", amountLent = 400.0, amountToReceive = 400.0)
        )

        // Missing destination category must fail
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "",
                scheduleOption = PartialPaymentScheduleOption.SetNewDueDate("2026-12-15")
            ) is LoanResult.Error
        )

        // Loan without scheduled dates allows partial payment with no due date (KeepCurrent)
        val partialNoDate = loanUseCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "50",
            paidAtInput = "2026-11-26 10:00",
            savingCategoryInput = "Viajes",
            scheduleOption = PartialPaymentScheduleOption.KeepCurrent
        )
        assertTrue(partialNoDate is LoanResult.Success)
        assertTrue(loanRepo.loansFlow.value.single().installments.isEmpty())
        assertEquals(350.0, loanRepo.loansFlow.value.single().pendingAmount, 0.001)

        // Past due date when optionally setting a date must fail
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 10:00",
                savingCategoryInput = "Viajes",
                scheduleOption = PartialPaymentScheduleOption.SetNewDueDate("2026-11-25")
            ) is LoanResult.Error
        )

        // Valid partial payment with chosen category (even with 0 prior Savings) and optional future due date
        val ok = loanUseCase.recordLoanPayment(
            loanId = loanId,
            amountInput = "100",
            paidAtInput = "2026-11-26 10:00",
            savingCategoryInput = "viajes",
            scheduleOption = PartialPaymentScheduleOption.SetNewDueDate("2026-12-15")
        )
        assertTrue(ok is LoanResult.Success)
        val updated = loanRepo.loansFlow.value.single()
        assertEquals("Viajes", updated.payments.last().savingCategory)
        assertEquals("2026-12-15", updated.installments.single().dueDate)
        assertEquals(250.0, updated.installments.single().expectedAmount, 0.001)

        val balances = calculateSavingsNetBalances(
            savings = savingsRepo.savingsFlow.value,
            expenses = expenseRepo.expensesFlow.value,
            categories = savingsRepo.categoriesFlow.value,
            loans = loanRepo.loansFlow.value
        )
        assertEquals(1, balances.size)
        assertEquals("Viajes", balances.single().categoryName)
        assertEquals(150.0, balances.single().netBalance, 0.001)

        // Credited loan repayment is immediately spendable via savings-funded expense
        val spendWithin = expenseUseCase.saveExpense(
            amountInput = "120",
            dateInput = "2026-11-27",
            descriptionInput = "Boleto",
            categoryInput = "Transporte",
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Viajes"
        )
        assertTrue(spendWithin is ExpenseResult.Success)
        val spendOver = expenseUseCase.saveExpense(
            amountInput = "40",
            dateInput = "2026-11-27",
            descriptionInput = "Extra",
            categoryInput = "Transporte",
            fundingSource = ExpenseFundingSource.SAVINGS,
            savingCategoryInput = "Viajes"
        )
        assertTrue(spendOver is ExpenseResult.Error)
    }

    @Test
    fun recordLoanPayment_withFutureInstallments_supportsFifoAdvanceAddAndAdjustAndValidatesErrors() = runBlocking {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Pedro",
                amountLent = 1000.0,
                amountToReceive = 1000.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 400.0),
                    LoanInstallment(dueDate = "2026-12-20", expectedAmount = 600.0)
                )
            )
        )

        // Invalid amounts & dateTime rejected
        assertTrue(loanUseCase.recordLoanPayment(loanId, "0", "2026-11-26 10:00", "Emergencias") is LoanResult.Error)
        assertTrue(loanUseCase.recordLoanPayment(loanId, "1001", "2026-11-26 10:00", "Emergencias") is LoanResult.Error)
        assertTrue(loanUseCase.recordLoanPayment(loanId, "100", "2026-02-30 10:00", "Emergencias") is LoanResult.Error)

        // Pay 250 and add a new installment for the remaining 150 of Dec 1 on 2027-01-10
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "250",
                paidAtInput = "2026-11-26 11:00",
                savingCategoryInput = "Emergencias",
                scheduleOption = PartialPaymentScheduleOption.AddInstallment("2027-01-10", "150")
            ) is LoanResult.Success
        )
        val afterAdd = loanRepo.loansFlow.value.single()
        assertEquals(3, afterAdd.installments.size)
        assertEquals("2026-12-01", afterAdd.installments[0].dueDate)
        assertEquals(250.0, afterAdd.installments[0].expectedAmount, 0.001)
        assertEquals(250.0, afterAdd.installments[0].paymentAmount, 0.001)
        assertTrue(afterAdd.installments[0].isPaid)
        assertEquals("2026-12-20", afterAdd.installments[1].dueDate)
        assertEquals(600.0, afterAdd.installments[1].expectedAmount, 0.001)
        assertEquals("2027-01-10", afterAdd.installments[2].dueDate)
        assertEquals(150.0, afterAdd.installments[2].expectedAmount, 0.001)

        // Pay 100 toward Dec 20 installment and adjust its remaining from 500 to 350, rebalancing Jan 10 to 300
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "100",
                paidAtInput = "2026-11-26 12:00",
                savingCategoryInput = "Emergencias",
                targetInstallmentId = afterAdd.installments[1].id,
                scheduleOption = PartialPaymentScheduleOption.AdjustNextInstallment("350")
            ) is LoanResult.Success
        )
        val afterAdjust = loanRepo.loansFlow.value.single()
        assertEquals(650.0, afterAdjust.pendingAmount, 0.001)
        assertEquals(3, afterAdjust.installments.size)
        val dec20 = afterAdjust.installments[1]
        assertEquals(100.0, dec20.paymentAmount, 0.001)
        assertEquals(450.0, dec20.expectedAmount, 0.001)
        assertEquals(350.0, dec20.remainingAmount, 0.001)
        assertEquals(300.0, afterAdjust.installments[2].expectedAmount, 0.001)
    }

    @Test
    fun recordLoanPayment_directedToSpecificInstallment_recordsPaymentAmountOnChosenInstallment() = runBlocking {
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Lucía",
                amountLent = 1000.0,
                amountToReceive = 1000.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 400.0),
                    LoanInstallment(dueDate = "2026-12-20", expectedAmount = 600.0)
                )
            )
        )
        val initialInstallments = loanRepo.loansFlow.value.single().installments
        val firstId = initialInstallments[0].id
        val secondId = initialInstallments[1].id

        // Direct a 250 payment explicitly to installment #2
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "250",
                paidAtInput = "2026-11-26 14:00",
                savingCategoryInput = "Emergencias",
                targetInstallmentId = secondId
            ) is LoanResult.Success
        )
        val afterFirstPay = loanRepo.loansFlow.value.single()
        assertEquals(secondId, afterFirstPay.payments.single().installmentId)
        assertEquals(0.0, afterFirstPay.installments.single { it.id == firstId }.paymentAmount, 0.001)
        assertEquals(250.0, afterFirstPay.installments.single { it.id == secondId }.paymentAmount, 0.001)
        assertEquals(350.0, afterFirstPay.installments.single { it.id == secondId }.remainingAmount, 0.001)
        // Badge still points to installment #1 since installment #1 is unpaid
        assertEquals("2026-12-01", calculateLoanDueBadgeInfo(afterFirstPay.installments, "2026-11-26")?.nextDueDate)

        // Direct 400 payment to installment #1 -> settles #1, badge advances to #2
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "400",
                paidAtInput = "2026-11-27 09:00",
                savingCategoryInput = "Emergencias",
                targetInstallmentId = firstId
            ) is LoanResult.Success
        )
        val afterSecondPay = loanRepo.loansFlow.value.single()
        assertTrue(afterSecondPay.installments.single { it.id == firstId }.isPaid)
        assertEquals("2026-12-20", calculateLoanDueBadgeInfo(afterSecondPay.installments, "2026-11-27")?.nextDueDate)
        assertEquals(0, calculateLoanDueBadgeInfo(afterSecondPay.installments, "2026-11-27")?.additionalCount)
    }

    @Test
    fun updateAndDeleteLoanPayment_recomputesInstallmentsSavingsAndProtectsCommittedSavings() = runBlocking {
        savingsRepo.insertSaving(
            Saving(amount = 200.0, category = "Emergencias", periodId = 1L, incomeId = 1L, dateTime = "2026-11-05 10:00")
        )
        val loanId = loanRepo.insertLoan(
            Loan(
                personName = "Mateo",
                amountLent = 1000.0,
                amountToReceive = 1000.0,
                installments = listOf(
                    LoanInstallment(dueDate = "2026-12-01", expectedAmount = 400.0),
                    LoanInstallment(dueDate = "2026-12-20", expectedAmount = 600.0)
                )
            )
        )
        val inst1Id = loanRepo.loansFlow.value.single().installments[0].id
        val inst2Id = loanRepo.loansFlow.value.single().installments[1].id

        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loanId,
                amountInput = "300",
                paidAtInput = "2026-11-27 10:00",
                savingCategoryInput = "Emergencias",
                targetInstallmentId = inst1Id
            ) is LoanResult.Success
        )
        val paymentId = loanRepo.loansFlow.value.single().payments.single().id

        // Edit payment: change amount to 250 and redirect to installment #2
        assertTrue(
            loanUseCase.updateLoanPayment(
                loanId = loanId,
                paymentId = paymentId,
                amountInput = "250",
                paidAtInput = "2026-11-27 12:00",
                savingCategoryInput = "Emergencias",
                targetInstallmentId = inst2Id
            ) is LoanResult.Success
        )
        val afterEdit = loanRepo.loansFlow.value.single()
        assertEquals(250.0, afterEdit.paidAmount, 0.001)
        assertEquals(750.0, afterEdit.pendingAmount, 0.001)
        assertEquals(0.0, afterEdit.installments.single { it.id == inst1Id }.paymentAmount, 0.001)
        assertEquals(250.0, afterEdit.installments.single { it.id == inst2Id }.paymentAmount, 0.001)

        // Spend 300 from Emergencias (200 initial + 250 payment = 450 available -> 150 remaining)
        assertTrue(
            expenseUseCase.saveExpense(
                amountInput = "300",
                dateInput = "2026-11-27",
                descriptionInput = "Uso",
                categoryInput = "Hogar",
                fundingSource = ExpenseFundingSource.SAVINGS,
                savingCategoryInput = "Emergencias"
            ) is ExpenseResult.Success
        )

        // Deleting payment must now be blocked because 200 < 300 committed outflow
        assertTrue(loanUseCase.deleteLoanPayment(loanId, paymentId) is LoanResult.Error)

        // Create a second loan without committed expenses and delete its payment
        val loan2Id = loanRepo.insertLoan(
            Loan(personName = "Rosa", amountLent = 100.0, amountToReceive = 100.0)
        )
        assertTrue(
            loanUseCase.recordLoanPayment(
                loanId = loan2Id,
                amountInput = "50",
                paidAtInput = "2026-11-27 13:00",
                savingCategoryInput = "Emergencias"
            ) is LoanResult.Success
        )
        val p2Id = loanRepo.loansFlow.value.single { it.id == loan2Id }.payments.single().id
        assertTrue(loanUseCase.deleteLoanPayment(loan2Id, p2Id) is LoanResult.Success)
        val loan2AfterDelete = loanRepo.loansFlow.value.single { it.id == loan2Id }
        assertTrue(loan2AfterDelete.payments.isEmpty())
        assertEquals(100.0, loan2AfterDelete.pendingAmount, 0.001)
    }
}
