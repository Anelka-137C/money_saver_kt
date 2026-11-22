package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.domain.FakeBudgetRepository
import com.example.money_saver_android.domain.FakeIncomeRepositoryForBudget
import com.example.money_saver_android.domain.FakeSavingsRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var savingsRepository: FakeSavingsRepository
    private lateinit var incomeRepository: FakeIncomeRepositoryForBudget
    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var viewModel: SavingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savingsRepository = FakeSavingsRepository()
        incomeRepository = FakeIncomeRepositoryForBudget()
        budgetRepository = FakeBudgetRepository()
        viewModel = SavingsViewModel(
            SavingsUseCase(savingsRepository, incomeRepository, budgetRepository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_preloadsDefaultSavingCategories() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.savingCategories.isNotEmpty())
    }

    @Test
    fun saveSaving_withSourceIncomeDateTimeAndNote_updatesTotalsAndResetsForm() = runTest(testDispatcher) {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 08:00", category = "Employment", subcategory = "Payroll", periodId = periodId)
        )
        budgetRepository.insertBudget(Budget(amount = 300.0, category = "Food", periodId = periodId))

        viewModel.onPeriodSelected(periodId)
        advanceUntilIdle()
        assertEquals(700.0, viewModel.uiState.value.availableIncome, 0.001)
        assertEquals(1, viewModel.uiState.value.incomes.size)

        val sourceIncome = viewModel.uiState.value.incomes.first()
        viewModel.onAmountChange("200")
        viewModel.onIncomeSelected(sourceIncome)
        viewModel.onCategorySelected("Other", "General")
        viewModel.onDateTimeChange("2026-11-22 14:15")
        viewModel.onNoteChange("Ahorro para viaje")
        viewModel.saveSaving()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.savings.size)
        val saved = state.savings.first()
        assertEquals(incomeId, saved.incomeId)
        assertEquals("Employment • Payroll", saved.incomeCategory)
        assertEquals("2026-11-22 14:15", saved.dateTime)
        assertEquals("Ahorro para viaje", saved.note)
        assertEquals(200.0, state.totalSaved, 0.001)
        assertEquals(500.0, state.availableIncome, 0.001)
        assertEquals("", state.savingAmountInput)
        assertNull(state.savingIncomeIdInput)
        assertEquals("", state.savingIncomeCategoryInput)
        assertEquals("", state.savingCategoryInput)
        assertEquals("", state.savingDateTimeInput)
        assertEquals("", state.savingNoteInput)
        assertNull(state.savingErrorMessage)
        assertEquals(SavingsViewModel.MSG_SAVING_SAVED, state.savingToastMessage)
    }

    @Test
    fun saveSaving_withoutSourceIncome_setsErrorMessageAndDoesNotSave() = runTest(testDispatcher) {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 500.0, dateTime = "2026-11-01 08:00", category = "Employment", periodId = periodId))
        viewModel.onPeriodSelected(periodId)
        advanceUntilIdle()

        viewModel.onAmountChange("100")
        viewModel.onCategorySelected("Other", "")
        viewModel.onDateTimeChange("2026-11-22 14:15")
        viewModel.saveSaving()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.savings.size)
        assertNotNull(state.savingErrorMessage)
    }

    @Test
    fun saveSaving_exceedingAvailableIncome_setsErrorMessageAndDoesNotSave() = runTest(testDispatcher) {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 500.0, dateTime = "2026-11-01 08:00", category = "Employment", periodId = periodId))
        viewModel.onPeriodSelected(periodId)
        advanceUntilIdle()

        viewModel.onAmountChange("600")
        viewModel.onIncomeSelected(viewModel.uiState.value.incomes.first())
        viewModel.onCategorySelected("Other", "")
        viewModel.onDateTimeChange("2026-11-22 14:15")
        viewModel.saveSaving()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.savings.size)
        assertEquals(0.0, state.totalSaved, 0.001)
        assertNotNull(state.savingErrorMessage)
    }

    @Test
    fun onPeriodSelected_clearsSelectedSourceIncomeWhenSwitchingPeriods() = runTest(testDispatcher) {
        incomeRepository.insertIncome(Income(amount = 500.0, dateTime = "2026-11-01 08:00", category = "Employment", periodId = 1L))
        viewModel.onPeriodSelected(1L)
        advanceUntilIdle()

        viewModel.onIncomeSelected(viewModel.uiState.value.incomes.first())
        assertNotNull(viewModel.uiState.value.savingIncomeIdInput)

        viewModel.onPeriodSelected(2L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.savingIncomeIdInput)
        assertEquals("", viewModel.uiState.value.savingIncomeCategoryInput)
        assertTrue(viewModel.uiState.value.incomes.isEmpty())
    }

    @Test
    fun createAndDeleteSavingCategory_updatesSavingCategoriesAndClearsSelection() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onNewCategoryNameChange("Vacation")
        viewModel.onColorSelect("#3B82F6")
        viewModel.createSavingCategory()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.savingCategories.any { it.name == "Vacation" })
        assertEquals(SavingsViewModel.MSG_CATEGORY_CREATED, viewModel.uiState.value.savingToastMessage)

        viewModel.onCategorySelected("Vacation", "")
        viewModel.requestDeleteSavingCategory("Vacation")
        viewModel.confirmDeleteSavingCategory()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.savingCategories.none { it.name == "Vacation" })
        assertEquals("", viewModel.uiState.value.savingCategoryInput)
        assertEquals(SavingsViewModel.MSG_CATEGORY_DELETED, viewModel.uiState.value.savingToastMessage)
    }

    @Test
    fun editAndDeleteSaving_updatesStateAndTotals() = runTest(testDispatcher) {
        val periodId = 1L
        val incomeId = incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 08:00", category = "Employment", periodId = periodId)
        )
        viewModel.onPeriodSelected(periodId)
        advanceUntilIdle()

        viewModel.onAmountChange("200")
        viewModel.onIncomeSelected(viewModel.uiState.value.incomes.first())
        viewModel.onCategorySelected("Emergency", "Health")
        viewModel.onDateTimeChange("2026-11-22 10:00")
        viewModel.onNoteChange("Nota inicial")
        viewModel.saveSaving()
        advanceUntilIdle()

        val created = viewModel.uiState.value.savings.single()
        viewModel.startEditingSaving(created)
        assertNotNull(viewModel.uiState.value.editingSaving)
        assertEquals(incomeId, viewModel.uiState.value.editingSaving?.incomeId)

        viewModel.onEditAmountChange("350")
        viewModel.onEditNoteChange("Nota editada")
        viewModel.onEditDateTimeChange("2026-11-23 11:45")
        viewModel.confirmEditSaving()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingSaving)
        assertEquals(350.0, viewModel.uiState.value.totalSaved, 0.001)
        assertEquals(650.0, viewModel.uiState.value.availableIncome, 0.001)
        assertEquals("Nota editada", viewModel.uiState.value.savings.single().note)
        assertEquals(SavingsViewModel.MSG_SAVING_UPDATED, viewModel.uiState.value.savingToastMessage)

        val updated = viewModel.uiState.value.savings.single()
        viewModel.requestDeleteSaving(updated)
        assertNotNull(viewModel.uiState.value.savingToDelete)
        viewModel.confirmDeleteSaving()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.savingToDelete)
        assertTrue(viewModel.uiState.value.savings.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.totalSaved, 0.001)
        assertEquals(1000.0, viewModel.uiState.value.availableIncome, 0.001)
        assertEquals(SavingsViewModel.MSG_SAVING_DELETED, viewModel.uiState.value.savingToastMessage)
    }
}
