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
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.LoanInstallmentInput
import com.example.money_saver_android.domain.usecase.LoanInstallmentValidationResult
import com.example.money_saver_android.domain.usecase.LoanResult
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
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

    override fun getAllLoans(): Flow<List<Loan>> = loansFlow

    override suspend fun insertLoan(loan: Loan): Long {
        val id = (loansFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val savedInstallments = loan.installments
            .map { it.copy(id = nextInstallmentId++, loanId = id) }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = listOf(loan.copy(id = id, installments = savedInstallments)) + loansFlow.value
        return id
    }

    override suspend fun replaceInstallments(loanId: Long, installments: List<LoanInstallment>) {
        val savedInstallments = installments
            .map { it.copy(id = nextInstallmentId++, loanId = loanId) }
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
        loansFlow.value = loansFlow.value.map {
            if (it.id == loanId) it.copy(installments = savedInstallments) else it
        }
    }

    override suspend fun reassignSavingCategory(oldName: String, newName: String) {
        loansFlow.value = loansFlow.value.map {
            if (it.savingCategory.trim().equals(oldName.trim(), ignoreCase = true)) {
                it.copy(savingCategory = newName)
            } else it
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
}
