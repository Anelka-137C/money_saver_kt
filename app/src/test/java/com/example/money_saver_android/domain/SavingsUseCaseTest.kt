package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCategories
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSavingsRepository : SavingsRepository {
    private val _savings = MutableStateFlow<List<Saving>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
        _savings.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun getSavingById(id: Long): Saving? =
        _savings.value.find { it.id == id }

    override suspend fun insertSaving(saving: Saving): Long {
        val id = (_savings.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _savings.value = _savings.value + saving.copy(id = id)
        return id
    }

    override suspend fun updateSaving(saving: Saving) {
        _savings.value = _savings.value.map { if (it.id == saving.id) saving else it }
    }

    override suspend fun deleteSaving(id: Long) {
        _savings.value = _savings.value.filter { it.id != id }
    }

    override suspend fun countSavingsForCategory(categoryName: String): Int =
        _savings.value.count { it.category == categoryName }

    override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int =
        _savings.value.count { it.category == categoryName && it.subcategory == subcategoryName }

    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map {
            if (it.name == categoryName) it.copy(subcategories = it.subcategories + subcategory) else it
        }
    }

    override suspend fun deleteCategory(categoryName: String) {
        _categories.value = _categories.value.filter { it.name != categoryName }
    }

    override suspend fun updateCategory(oldName: String, newName: String, color: String, icon: String) {
        _categories.value = _categories.value.map {
            if (it.name == oldName) it.copy(name = newName, color = color, icon = icon) else it
        }
        _savings.value = _savings.value.map {
            if (it.category.equals(oldName, ignoreCase = true)) it.copy(category = newName) else it
        }
    }

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map {
            if (it.name == categoryName) {
                it.copy(subcategories = it.subcategories.filter { sub -> sub.name != subcategoryName })
            } else it
        }
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = defaultCategories
        }
    }
}

class SavingsUseCaseTest {

    private lateinit var savingsRepository: FakeSavingsRepository
    private lateinit var incomeRepository: FakeIncomeRepositoryForBudget
    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var useCase: SavingsUseCase

    @Before
    fun setup() {
        savingsRepository = FakeSavingsRepository()
        incomeRepository = FakeIncomeRepositoryForBudget()
        budgetRepository = FakeBudgetRepository()
        useCase = SavingsUseCase(savingsRepository, incomeRepository, budgetRepository)
    }

    @Test
    fun saveSaving_withValidFieldsAndSourceIncome_persistsAllFieldsAndUpdatesTotals() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(
                amount = 1000.0,
                dateTime = "2026-11-01 09:00",
                category = "Employment",
                subcategory = "Salary",
                periodId = periodId
            )
        )
        budgetRepository.insertBudget(Budget(amount = 400.0, category = "Food", periodId = periodId))

        val result = useCase.saveSaving(
            amountInput = "250",
            categoryInput = "Emergency",
            subcategoryInput = "Health",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-22 10:30",
            noteInput = "  Fondo médico mensual  "
        )

        assertTrue(result is SavingsResult.Success)
        val savings = useCase.getSavingsByPeriod(periodId).first()
        assertEquals(1, savings.size)
        val saved = savings.first()
        assertEquals(250.0, saved.amount, 0.001)
        assertEquals("Emergency", saved.category)
        assertEquals("Health", saved.subcategory)
        assertEquals(incomeId, saved.incomeId)
        assertEquals("Employment • Salary", saved.incomeCategory)
        assertEquals("2026-11-22 10:30", saved.dateTime)
        assertEquals("Fondo médico mensual", saved.note)
        assertEquals(250.0, useCase.calculateTotalSaved(savings), 0.001)
        assertEquals(350.0, useCase.calculateAvailableIncome(1000.0, 400.0, 250.0), 0.001)
    }

    @Test
    fun saveSaving_exceedingAvailableIncomeAfterBudgetsAndPriorSavings_returnsError() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )
        budgetRepository.insertBudget(Budget(amount = 600.0, category = "Food", periodId = periodId))
        savingsRepository.insertSaving(
            Saving(amount = 200.0, category = "Travel", periodId = periodId, incomeId = incomeId, dateTime = "2026-11-10 08:00")
        )

        val result = useCase.saveSaving(
            amountInput = "250",
            categoryInput = "Emergency",
            subcategoryInput = "",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-22 10:30",
            noteInput = ""
        )

        assertTrue(result is SavingsResult.Error)
        assertEquals(1, useCase.getSavingsByPeriod(periodId).first().size)
    }

    @Test
    fun saveSaving_exceedingSelectedSourceIncomeRemaining_returnsErrorEvenIfPeriodHasMoreIncome() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )
        val bonusId = incomeRepository.insertIncome(
            Income(amount = 200.0, dateTime = "2026-11-05 12:00", category = "Bonus", periodId = periodId)
        )
        savingsRepository.insertSaving(
            Saving(amount = 120.0, category = "Travel", periodId = periodId, incomeId = bonusId, dateTime = "2026-11-10 08:00")
        )

        val exceeded = useCase.saveSaving(
            amountInput = "100",
            categoryInput = "Emergency",
            periodId = periodId,
            incomeId = bonusId,
            dateTimeInput = "2026-11-22 10:30"
        )
        val allowed = useCase.saveSaving(
            amountInput = "80",
            categoryInput = "Emergency",
            periodId = periodId,
            incomeId = bonusId,
            dateTimeInput = "2026-11-22 10:30"
        )

        assertTrue(exceeded is SavingsResult.Error)
        assertTrue(allowed is SavingsResult.Success)
    }

    @Test
    fun saveSaving_missingOrWrongPeriodSourceIncome_returnsError() = runBlocking {
        val period1IncomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = 1L)
        )

        val missingIncome = useCase.saveSaving(
            amountInput = "100",
            categoryInput = "Emergency",
            periodId = 1L,
            incomeId = null,
            dateTimeInput = "2026-11-22 10:30"
        )
        val wrongPeriodIncome = useCase.saveSaving(
            amountInput = "100",
            categoryInput = "Emergency",
            periodId = 2L,
            incomeId = period1IncomeId,
            dateTimeInput = "2026-11-22 10:30"
        )

        assertTrue(missingIncome is SavingsResult.Error)
        assertTrue(wrongPeriodIncome is SavingsResult.Error)
    }

    @Test
    fun saveSaving_invalidOrBlankDateTime_returnsError() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )

        assertTrue(useCase.saveSaving("100", "Emergency", "", periodId, incomeId, "", "note") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("100", "Emergency", "", periodId, incomeId, "2026-02-30 10:00", "note") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("100", "Emergency", "", periodId, incomeId, "not-a-date", "note") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("100", "Emergency", "", periodId, incomeId, "2026-11-22 25:00", "note") is SavingsResult.Error)
        assertEquals(0, useCase.getSavingsByPeriod(periodId).first().size)
    }

    @Test
    fun saveSaving_zeroOrNegativeOrInvalidAmount_returnsError() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )

        assertTrue(useCase.saveSaving("0", "Emergency", "", periodId, incomeId, "2026-11-22 10:30") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("-50", "Emergency", "", periodId, incomeId, "2026-11-22 10:30") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("", "Emergency", "", periodId, incomeId, "2026-11-22 10:30") is SavingsResult.Error)
        assertEquals(0, useCase.getSavingsByPeriod(periodId).first().size)
    }

    @Test
    fun saveSaving_missingCategoryOrPeriod_returnsError() = runBlocking {
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = 1L)
        )
        assertTrue(useCase.saveSaving("100", "", "", 1L, incomeId, "2026-11-22 10:30") is SavingsResult.Error)
        assertTrue(useCase.saveSaving("100", "Emergency", "", null, incomeId, "2026-11-22 10:30") is SavingsResult.Error)
    }

    @Test
    fun deleteCategory_blockedWhenSavingsAssociated() = runBlocking {
        useCase.createCategory("Emergency", "#3B82F6")
        savingsRepository.insertSaving(Saving(amount = 100.0, category = "Emergency", periodId = 1L))

        val result = useCase.deleteCategory("Emergency")

        assertTrue(result is SavingsResult.Error)
        assertEquals(1, useCase.getCategories().first().size)
    }

    @Test
    fun budgetUseCase_deductsSavingsFromAvailableIncome() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 1000.0, dateTime = "2026-11-01", category = "Employment", periodId = periodId))
        savingsRepository.insertSaving(Saving(amount = 700.0, category = "Emergency", periodId = periodId))
        val budgetUseCase = BudgetUseCase(budgetRepository, incomeRepository, savingsRepository)

        val exceeded = budgetUseCase.saveBudget("400", "Food", "", periodId)
        val allowed = budgetUseCase.saveBudget("300", "Food", "", periodId)

        assertTrue(exceeded is BudgetResult.Error)
        assertTrue(allowed is BudgetResult.Success)
    }

    @Test
    fun updateSaving_modifiesExistingSavingWithoutDoubleCountingPreviousAmount() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )
        budgetRepository.insertBudget(Budget(amount = 400.0, category = "Food", periodId = periodId))
        val savingId = savingsRepository.insertSaving(
            Saving(
                amount = 500.0,
                category = "Emergency",
                periodId = periodId,
                incomeId = incomeId,
                incomeCategory = "Employment",
                dateTime = "2026-11-10 10:00",
                note = "Inicial"
            )
        )

        val updateOk = useCase.updateSaving(
            id = savingId,
            amountInput = "600",
            categoryInput = "Travel",
            subcategoryInput = "Flights",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-12 15:30",
            noteInput = "Actualizado"
        )
        val updateExceeds = useCase.updateSaving(
            id = savingId,
            amountInput = "650",
            categoryInput = "Travel",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-12 15:30"
        )

        assertTrue(updateOk is SavingsResult.Success)
        assertTrue(updateExceeds is SavingsResult.Error)
        val updated = useCase.getSavingsByPeriod(periodId).first().single()
        assertEquals(600.0, updated.amount, 0.001)
        assertEquals("Travel", updated.category)
        assertEquals("Flights", updated.subcategory)
        assertEquals("2026-11-12 15:30", updated.dateTime)
        assertEquals("Actualizado", updated.note)
    }

    @Test
    fun deleteSaving_removesSavingAndRestoresAvailableIncome() = runBlocking {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )
        val savingId = savingsRepository.insertSaving(
            Saving(amount = 300.0, category = "Emergency", periodId = periodId, incomeId = incomeId, dateTime = "2026-11-10 10:00")
        )

        val result = useCase.deleteSaving(savingId)

        assertTrue(result is SavingsResult.Success)
        assertTrue(useCase.getSavingsByPeriod(periodId).first().isEmpty())
    }

    @Test
    fun updateCategory_validatesAndReassignsExpenseSavingCategory() = runBlocking {
        savingsRepository.insertCategory(Category(name = "Emergency", icon = "🛟", color = "#4E73DF", subcategories = emptyList()))
        savingsRepository.insertCategory(Category(name = "Travel", icon = "✈️", color = "#1CC88A", subcategories = emptyList()))

        val reassignedPairs = mutableListOf<Pair<String, String>>()
        val trackingExpenseRepo = object : com.example.money_saver_android.data.repository.ExpenseRepository {
            override fun getAllExpenses(): Flow<List<com.example.money_saver_android.domain.model.Expense>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun insertExpense(expense: com.example.money_saver_android.domain.model.Expense) {}
            override suspend fun updateExpense(expense: com.example.money_saver_android.domain.model.Expense) {}
            override suspend fun deleteExpense(id: Long) {}
            override suspend fun reassignSavingCategory(oldName: String, newName: String) {
                reassignedPairs.add(oldName to newName)
            }
        }
        val uc = SavingsUseCase(savingsRepository, incomeRepository, budgetRepository, expenseRepository = trackingExpenseRepo)

        assertTrue(uc.updateCategory("Emergency", "   ", "#F6C23E") is SavingsResult.Error)
        assertTrue(uc.updateCategory("Missing", "Other", "#F6C23E") is SavingsResult.Error)
        assertTrue(uc.updateCategory("Emergency", "travel", "#F6C23E") is SavingsResult.Error)

        val ok = uc.updateCategory("Emergency", "Emergencias", "#F6C23E")
        assertTrue(ok is SavingsResult.Success)
        assertEquals(listOf("Emergency" to "Emergencias"), reassignedPairs)
    }
}
