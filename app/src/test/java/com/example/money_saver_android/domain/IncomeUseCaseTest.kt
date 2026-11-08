package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeIncomeRepoForUseCase : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        _incomes.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertIncome(income: Income): Long {
        val id = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _incomes.value = _incomes.value + income.copy(id = id)
        return id
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

class IncomeUseCaseTest {

    private lateinit var repository: FakeIncomeRepoForUseCase
    private lateinit var useCase: IncomeUseCase

    @Before
    fun setup() {
        repository = FakeIncomeRepoForUseCase()
        useCase = IncomeUseCase(repository)
    }

    @Test
    fun saveIncome_withValidData_returnsSuccessAndInserts() = runBlocking {
        val result = useCase.saveIncome(
            amountInput = "2323.0",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "Main salary",
            periodId = 1L
        )

        assertTrue(result is IncomeResult.Success)
        val list = useCase.getIncomesByPeriod(1L).first()
        assertEquals(1, list.size)
        assertEquals(2323.0, list.first().amount, 0.0)
    }

    @Test
    fun saveIncome_withInvalidAmount_returnsError() = runBlocking {
        val resultEmpty = useCase.saveIncome(
            amountInput = "",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultEmpty is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultEmpty as IncomeResult.Error).message)

        val resultZero = useCase.saveIncome(
            amountInput = "0",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultZero is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultZero as IncomeResult.Error).message)

        val resultNegative = useCase.saveIncome(
            amountInput = "-100",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNegative is IncomeResult.Error)
        assertEquals("El monto debe ser mayor a cero", (resultNegative as IncomeResult.Error).message)
    }

    @Test
    fun saveIncome_withoutCategoryOrSubcategory_returnsError() = runBlocking {
        val resultNoCategory = useCase.saveIncome(
            amountInput = "500",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNoCategory is IncomeResult.Error)
        assertEquals("Debes seleccionar una categoría y subcategoría", (resultNoCategory as IncomeResult.Error).message)

        val resultNoSubcategory = useCase.saveIncome(
            amountInput = "500",
            dateTimeInput = "2026-05-15 10:00",
            categoryInput = "Employment",
            subcategoryInput = "",
            descriptionInput = "",
            periodId = 1L
        )
        assertTrue(resultNoSubcategory is IncomeResult.Error)
        assertEquals("Debes seleccionar una categoría y subcategoría", (resultNoSubcategory as IncomeResult.Error).message)
    }

    @Test
    fun calculateTotalIncome_sumsAllAmountsCorrectly() {
        val incomes = listOf(
            Income(amount = 1000.0, dateTime = "", category = "", subcategory = "", periodId = 1L),
            Income(amount = 1323.0, dateTime = "", category = "", subcategory = "", periodId = 1L)
        )
        val total = useCase.calculateTotalIncome(incomes)
        assertEquals(2323.0, total, 0.0)
    }

    @Test
    fun deleteCategory_whenIncomesAssociated_returnsErrorAndDoesNotDelete() = runBlocking {
        useCase.insertCategory(Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList()))
        useCase.saveIncome(
            amountInput = "1000",
            dateTimeInput = "2026-05-01 10:00",
            categoryInput = "Employment",
            subcategoryInput = "Salary",
            descriptionInput = "",
            periodId = 1L
        )

        val errorResult = useCase.deleteCategory("Employment")
        assertTrue(errorResult is IncomeResult.Error)
        assertEquals("No se puede eliminar la categoría porque tiene ingresos asociados", (errorResult as IncomeResult.Error).message)

        val categories = useCase.getCategories().first()
        assertTrue(categories.any { it.name == "Employment" })
    }

    @Test
    fun deleteCategory_whenNoIncomesAssociated_deletesSuccessfully() = runBlocking {
        useCase.insertCategory(Category(name = "Freelance", icon = "💻", color = "#1CC88A", subcategories = emptyList()))
        var categories = useCase.getCategories().first()
        assertTrue(categories.any { it.name == "Freelance" })

        val successResult = useCase.deleteCategory("Freelance")
        assertTrue(successResult is IncomeResult.Success)

        categories = useCase.getCategories().first()
        assertTrue(categories.none { it.name == "Freelance" })
    }
}
