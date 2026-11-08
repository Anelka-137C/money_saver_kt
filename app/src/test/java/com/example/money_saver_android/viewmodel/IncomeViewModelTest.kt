package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeIncomeRepository : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> {
        return _incomes.map { list -> list.filter { it.periodId == periodId } }
    }

    override suspend fun insertIncome(income: Income): Long {
        val newId = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val saved = income.copy(id = newId)
        _incomes.value = _incomes.value + saved
        return newId
    }

    override suspend fun updateIncome(income: Income) {
        _incomes.value = _incomes.value.map { if (it.id == income.id) income else it }
    }

    override suspend fun deleteIncome(id: Long) {
        _incomes.value = _incomes.value.filter { it.id != id }
    }

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

    override suspend fun countIncomesForCategory(categoryName: String): Int {
        return _incomes.value.count { it.category == categoryName }
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = listOf(
                Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salary")))
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeIncomeRepository
    private lateinit var viewModel: IncomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeIncomeRepository()
        viewModel = IncomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_income_validation_rejects_empty_or_non_positive_amount() = runTest {
        viewModel.onPeriodSelected(1L)
        viewModel.onCategorySelected("Employment", "Salary")
        viewModel.onDateTimeChange("2026-05-15 10:00")

        // 1. Empty amount
        viewModel.onAmountChange("")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 2. Zero amount
        viewModel.onAmountChange("0")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 3. Negative amount
        viewModel.onAmountChange("-150.0")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("El monto debe ser mayor a cero", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())
    }

    @Test
    fun test_income_validation_rejects_missing_category_or_subcategory() = runTest {
        viewModel.onPeriodSelected(1L)
        viewModel.onAmountChange("1500.0")
        viewModel.onDateTimeChange("2026-05-15 10:00")

        // 1. Without category or subcategory
        viewModel.onCategorySelected("", "")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Debes seleccionar una categoría y subcategoría", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())

        // 2. With category but without subcategory
        viewModel.onCategorySelected("Employment", "")
        viewModel.saveIncome()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Debes seleccionar una categoría y subcategoría", viewModel.uiState.value.errorMessage)
        assertTrue(repository.getIncomesByPeriod(1L).first().isEmpty())
    }

    @Test
    fun test_save_valid_income_persists_with_date_time_and_selected_period() = runTest {
        viewModel.onPeriodSelected(10L)
        advanceUntilIdle()

        viewModel.onAmountChange("2323.0")
        viewModel.onDateTimeChange("2026-05-15 14:30")
        viewModel.onCategorySelected("Employment", "Salary")
        viewModel.onDescriptionChange("Monthly salary payment")

        viewModel.saveIncome()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals("Ingreso guardado exitosamente", state.toastMessage)
        assertEquals("", state.amountInput)
        assertEquals("", state.descriptionInput)

        val persisted = repository.getIncomesByPeriod(10L).first()
        assertEquals(1, persisted.size)
        val income = persisted.first()
        assertEquals(2323.0, income.amount, 0.0)
        assertEquals("2026-05-15 14:30", income.dateTime)
        assertEquals("Employment", income.category)
        assertEquals("Salary", income.subcategory)
        assertEquals("Monthly salary payment", income.description)
        assertEquals(10L, income.periodId)

        assertEquals(1, state.incomes.size)
        assertEquals(2323.0, state.totalIncome, 0.0)
    }

    @Test
    fun test_filter_incomes_by_selected_period_updates_list_and_totals() = runTest {
        repository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-04-01 10:00", category = "Employment", subcategory = "Salary", periodId = 1L)
        )
        repository.insertIncome(
            Income(amount = 500.0, dateTime = "2026-04-15 11:00", category = "Employment", subcategory = "Bonus", periodId = 1L)
        )
        repository.insertIncome(
            Income(amount = 2323.0, dateTime = "2026-05-15 12:00", category = "Employment", subcategory = "Salary", periodId = 2L)
        )

        // Select Period 1
        viewModel.onPeriodSelected(1L)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(2, state.incomes.size)
        assertEquals(1500.0, state.totalIncome, 0.0)
        assertTrue(state.incomes.all { it.periodId == 1L })

        // Switch to Period 2
        viewModel.onPeriodSelected(2L)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.incomes.size)
        assertEquals(2323.0, state.totalIncome, 0.0)
        assertEquals(2L, state.incomes.first().periodId)

        // Switch to Period with no incomes (3)
        viewModel.onPeriodSelected(3L)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0, state.incomes.size)
        assertEquals(0.0, state.totalIncome, 0.0)
    }

    @Test
    fun test_delete_income_category_rejected_when_incomes_associated_shows_error() = runTest {
        repository.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList()))
        repository.insertIncome(Income(amount = 2323.0, dateTime = "2026-05-15 10:00", category = "Employment", subcategory = "Salary", periodId = 1L))

        viewModel.deleteCategory("Employment")
        advanceUntilIdle()

        assertEquals("No se puede eliminar la categoría porque tiene ingresos asociados", viewModel.uiState.value.errorMessage)
        val categories = repository.getAllCategories().first()
        assertTrue(categories.any { it.name == "Employment" })
    }

    @Test
    fun test_delete_income_category_succeeds_when_no_incomes_associated() = runTest {
        repository.insertCategory(Category(name = "Investments", icon = "📈", color = "#1CC88A", subcategories = emptyList()))

        viewModel.deleteCategory("Investments")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals("Categoría eliminada exitosamente", viewModel.uiState.value.toastMessage)
        val categories = repository.getAllCategories().first()
        assertTrue(categories.none { it.name == "Investments" })
    }
}
