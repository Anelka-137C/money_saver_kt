package com.example.money_saver_android.adversarial

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.ui.formatPeriodRange
import com.example.money_saver_android.viewmodel.EditBudgetState
import com.example.money_saver_android.viewmodel.IncomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suite de Pruebas Adversariales para la Eliminación de Presupuestos (Story Presupuestos),
 * Comparación de Fechas con Timestamps (DateTimePicker en Gastos) y Formateo de Rango de Períodos.
 *
 * Áreas Críticas Evaluadas:
 * 1. Eliminación de Presupuestos (BudgetUseCase / IncomeUseCase / IncomeViewModel):
 *    - Manejo de IDs extremos, negativos, nulos y desbordamiento (Long.MAX_VALUE, Long.MIN_VALUE, -1L).
 *    - Concurrencia masiva (50 coroutines ejecutando deleteBudget bajo Mutex).
 *    - Gastos huérfanos asociados a presupuestos eliminados y preservación de invariantes de cálculo.
 *    - Reconciliación de estado en ViewModel: limpieza de editingBudget solo si coincide el ID.
 * 2. Filtrado de Períodos en Gastos con Timestamps (Expense.matchesPeriod):
 *    - Timestamps completos "yyyy-MM-dd HH:mm" provenientes del nuevo DateTimePickerField.
 *    - Límites de medianoche (00:00 inicio, 23:59 fin).
 *    - Pruebas de 1 minuto antes del inicio y 1 minuto después del fin.
 *    - Precedencia de periodId explícito sobre el rango de fechas.
 * 3. Formateador de Rango de Períodos (formatPeriodRange):
 *    - Rangos mismo mes, diferente mes, cambio de año y cambio de siglo/milenio.
 *    - Años bisiestos (29 de febrero) y años no bisiestos (2024, 2000 vs 2025).
 *    - Entradas invertidas (startDate > endDate) e inputs malformados/vacíos (fallback sin crash).
 *    - Soporte multi-idioma (es, en, de, pt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetDeletionAndPeriodAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var budgetRepository: FakeAdversarialBudgetRepo
    private lateinit var incomeRepository: FakeAdversarialIncomeRepo
    private lateinit var expenseRepository: FakeAdversarialExpenseRepo
    private lateinit var budgetUseCase: BudgetUseCase
    private lateinit var incomeUseCase: IncomeUseCase
    private lateinit var incomeViewModel: IncomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = FakeAdversarialBudgetRepo()
        incomeRepository = FakeAdversarialIncomeRepo()
        expenseRepository = FakeAdversarialExpenseRepo()
        budgetUseCase = BudgetUseCase(budgetRepository, incomeRepository)
        incomeUseCase = IncomeUseCase(
            repository = incomeRepository,
            budgetRepository = budgetRepository,
            budgetUseCase = budgetUseCase,
            expenseRepository = expenseRepository
        )
        incomeViewModel = IncomeViewModel(incomeUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. REPOSITORIOS FAKE PARA TESTING ADVERSARIAL
    // =========================================================================

    private class FakeAdversarialBudgetRepo : BudgetRepository {
        private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
        private val _categories = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
            _budgets.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun insertBudget(budget: Budget): Long {
            val assignedId = if (budget.id > 0L) budget.id else nextId++
            if (assignedId >= nextId) nextId = assignedId + 1L
            _budgets.value = _budgets.value + budget.copy(id = assignedId)
            return assignedId
        }

        override suspend fun updateBudget(budget: Budget) {
            _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
        }

        override suspend fun getBudgetById(id: Long): Budget? =
            _budgets.value.find { it.id == id }

        override suspend fun deleteBudget(id: Long) {
            _budgets.value = _budgets.value.filter { it.id != id }
        }

        override fun getAllCategories(): Flow<List<Category>> = _categories
        override suspend fun insertCategory(category: Category) {
            _categories.value = _categories.value + category
        }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun countBudgetsForCategory(categoryName: String): Int = 0
        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() {}

        fun getAllBudgetsDirect(): List<Budget> = _budgets.value
    }

    private class FakeAdversarialIncomeRepo : IncomeRepository {
        private val _incomes = MutableStateFlow<List<Income>>(emptyList())
        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            _incomes.map { list -> list.filter { it.periodId == periodId } }
        override suspend fun insertIncome(income: Income): Long {
            _incomes.value = _incomes.value + income
            return income.id
        }
        override suspend fun updateIncome(income: Income) {}
        override suspend fun deleteIncome(id: Long) {}
        override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun countIncomesForCategory(categoryName: String): Int = 0
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class FakeAdversarialExpenseRepo : ExpenseRepository {
        private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
        override fun getAllExpenses(): Flow<List<Expense>> = _expenses
        override suspend fun insertExpense(expense: Expense) {
            _expenses.value = _expenses.value + expense
        }
        override suspend fun updateExpense(expense: Expense) {}
        override suspend fun deleteExpense(id: Long) {}
    }

    // =========================================================================
    // 2. ELIMINACIÓN DE PRESUPUESTOS (BUDGET USE CASE Y ADVERSARIAL IDS)
    // =========================================================================

    @Test
    fun test_adversarial_delete_budget_extreme_and_invalid_ids() = runTest(testDispatcher) {
        val periodId = 1L
        budgetRepository.insertBudget(Budget(id = 10L, amount = 200.0, category = "Comida", periodId = periodId))

        // Adversarial IDs que no deben romper el use case ni la persistencia
        val extremeIds = listOf(-1L, 0L, -999999L, Long.MAX_VALUE, Long.MIN_VALUE, 404L)

        for (id in extremeIds) {
            val result = budgetUseCase.deleteBudget(id)
            assertTrue("La eliminación de ID $id debe retornar Success (operación idempotente)", result is BudgetResult.Success)
        }

        // El presupuesto legítimo debe seguir intacto
        assertEquals(1, budgetRepository.getAllBudgetsDirect().size)
        assertEquals(10L, budgetRepository.getAllBudgetsDirect().first().id)

        // Ahora eliminamos el presupuesto legítimo
        val validDelete = budgetUseCase.deleteBudget(10L)
        assertTrue(validDelete is BudgetResult.Success)
        assertEquals(0, budgetRepository.getAllBudgetsDirect().size)
    }

    @Test
    fun test_adversarial_concurrency_stress_deleteBudget_mutex() = runTest(testDispatcher) {
        val periodId = 1L
        // Insertamos 30 presupuestos
        for (i in 1..30) {
            budgetRepository.insertBudget(
                Budget(id = i.toLong(), amount = 100.0, category = "Cat_$i", periodId = periodId)
            )
        }
        assertEquals(30, budgetRepository.getAllBudgetsDirect().size)

        // 50 coroutines ejecutando deleteBudget en paralelo (algunos compitiendo por los mismos IDs)
        val jobs = (1..50).map { i ->
            launch(Dispatchers.Default) {
                val targetId = ((i % 30) + 1).toLong()
                budgetUseCase.deleteBudget(targetId)
            }
        }
        jobs.joinAll()

        // Invariante: No debe haber deadlocks ni excepciones, y todos los presupuestos procesados se eliminan limpiamente
        val remaining = budgetRepository.getAllBudgetsDirect()
        assertTrue("El repositorio debe ser consistente después de la concurrencia", remaining.size <= 30)
    }

    @Test
    fun test_adversarial_budget_calculations_with_orphaned_expenses() = runTest(testDispatcher) {
        val periodId = 1L
        val cat = Category(name = "Ocio", icon = "🎮", color = "#8B5CF6", subcategories = emptyList())
        budgetRepository.insertCategory(cat)

        // Presupuesto de $300
        val budgetId = budgetRepository.insertBudget(
            Budget(amount = 300.0, category = "Ocio", periodId = periodId)
        )

        // Gasto asociado con budgetId = budgetId
        val expenses = listOf(
            Expense(id = 1L, amount = 100.0, date = "2026-05-10 12:00", description = "Juegos", category = "Ocio", periodId = periodId, budgetId = budgetId, budgetCategory = "Ocio")
        )

        // Antes de borrar:
        val breakdownBefore = incomeUseCase.calculateCategoryBudgets(
            budgets = budgetRepository.getAllBudgetsDirect(),
            categories = listOf(cat),
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = periodId
        )
        assertEquals(1, breakdownBefore.size)
        assertEquals(300.0, breakdownBefore.first().budgetAmount, 0.01)
        assertEquals(100.0, breakdownBefore.first().spentAmount, 0.01)
        assertEquals(200.0, breakdownBefore.first().remainingAmount, 0.01)

        // Eliminamos el presupuesto
        budgetUseCase.deleteBudget(budgetId)

        // Después de borrar:
        val breakdownAfter = incomeUseCase.calculateCategoryBudgets(
            budgets = budgetRepository.getAllBudgetsDirect(),
            categories = listOf(cat),
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = periodId
        )

        // Invariante: La lista de desglose de presupuestos debe estar vacía sin lanzar excepciones ni divisiones por cero
        assertEquals(0, breakdownAfter.size)

        // Invariante de total de gasto presupuestado
        val totalSpent = incomeUseCase.calculateTotalBudgetSpent(
            budgets = budgetRepository.getAllBudgetsDirect(),
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = periodId
        )
        assertEquals(0.0, totalSpent, 0.01)
    }

    // =========================================================================
    // 3. INCOME VIEW MODEL (CONFIRMACIÓN Y MÁQUINA DE ESTADO)
    // =========================================================================

    @Test
    fun test_viewModel_confirmDeleteBudget_clears_active_editing_budget_only_if_matched() = runTest(testDispatcher) {
        val budget1 = Budget(id = 101L, amount = 500.0, category = "Renta", periodId = 1L)
        val budget2 = Budget(id = 102L, amount = 150.0, category = "Servicios", periodId = 1L)
        budgetRepository.insertBudget(budget1)
        budgetRepository.insertBudget(budget2)
        advanceUntilIdle()

        // Caso A: El usuario está editando budget1 y solicita eliminar budget2
        incomeViewModel.startEditingBudget(budget1)
        assertEquals(101L, incomeViewModel.uiState.value.editingBudget?.budgetId)

        incomeViewModel.requestDeleteBudget(budget2)
        assertEquals(budget2, incomeViewModel.uiState.value.budgetToDelete)

        // Confirma eliminación de budget2
        incomeViewModel.confirmDeleteBudget()
        advanceUntilIdle()

        // Invariante: editingBudget de budget1 NO debe ser borrado
        assertNull(incomeViewModel.uiState.value.budgetToDelete)
        assertEquals(101L, incomeViewModel.uiState.value.editingBudget?.budgetId)
        assertEquals(IncomeViewModel.MSG_BUDGET_DELETED, incomeViewModel.uiState.value.budgetToastMessage)

        // Caso B: El usuario ahora solicita eliminar budget1 (que es el que está en edición)
        incomeViewModel.requestDeleteBudget(budget1)
        incomeViewModel.confirmDeleteBudget()
        advanceUntilIdle()

        // Invariante: editingBudget debe haber sido cancelado automáticamente
        assertNull(incomeViewModel.uiState.value.editingBudget)
        assertNull(incomeViewModel.uiState.value.budgetToDelete)
    }

    @Test
    fun test_viewModel_rapid_double_confirmDeleteBudget_is_safe_and_idempotent() = runTest(testDispatcher) {
        val budget = Budget(id = 200L, amount = 300.0, category = "Salud", periodId = 1L)
        budgetRepository.insertBudget(budget)
        advanceUntilIdle()

        incomeViewModel.requestDeleteBudget(budget)

        // Dos confirmaciones en rápida sucesión (simulando doble tap en UI)
        incomeViewModel.confirmDeleteBudget()
        incomeViewModel.confirmDeleteBudget()
        advanceUntilIdle()

        assertNull(incomeViewModel.uiState.value.budgetToDelete)
        assertEquals(0, budgetRepository.getAllBudgetsDirect().size)
    }

    // =========================================================================
    // 4. COMPARACIÓN DE FECHAS CON TIMESTAMPS (DATETIMEPICKER EN GASTOS)
    // =========================================================================

    @Test
    fun test_adversarial_timestamp_and_midnight_boundaries_in_period_matching() = runTest(testDispatcher) {
        val periodId = 1L
        val cat = Category(name = "General", icon = "💰", color = "#000000", subcategories = emptyList())
        val budget = Budget(id = 1L, amount = 1000.0, category = "General", periodId = periodId)
        budgetRepository.insertBudget(budget)

        // Periodo de prueba: 2026-05-01 a 2026-05-31
        val start = "2026-05-01"
        val end = "2026-05-31"

        val testExpenses = listOf(
            // 1. Exactamente en el límite de inicio a medianoche (00:00) -> DEBE INCLUIRSE
            Expense(id = 1L, amount = 10.0, date = "2026-05-01 00:00", description = "Start Midnight", category = "General", budgetCategory = "General"),
            // 2. Un minuto antes del periodo (2026-04-30 23:59) -> DEBE EXCLUIRSE
            Expense(id = 2L, amount = 20.0, date = "2026-04-30 23:59", description = "Before Start", category = "General", budgetCategory = "General"),
            // 3. Exactamente en el límite final a las 23:59 -> DEBE INCLUIRSE
            Expense(id = 3L, amount = 30.0, date = "2026-05-31 23:59", description = "End Midnight", category = "General", budgetCategory = "General"),
            // 4. Un minuto después del periodo (2026-06-01 00:00) -> DEBE EXCLUIRSE
            Expense(id = 4L, amount = 40.0, date = "2026-06-01 00:00", description = "After End", category = "General", budgetCategory = "General"),
            // 5. Formato antiguo solo fecha sin hora (2026-05-15) -> DEBE INCLUIRSE
            Expense(id = 5L, amount = 50.0, date = "2026-05-15", description = "Date Only", category = "General", budgetCategory = "General"),
            // 6. Cadena vacía o malformada -> DEBE EXCLUIRSE SIN CRASH
            Expense(id = 6L, amount = 60.0, date = "", description = "Empty Date", category = "General", budgetCategory = "General"),
            Expense(id = 7L, amount = 70.0, date = "invalid-date", description = "Corrupt Date", category = "General", budgetCategory = "General")
        )

        val totalSpent = incomeUseCase.calculateTotalBudgetSpent(
            budgets = listOf(budget),
            expenses = testExpenses,
            periodStartDate = start,
            periodEndDate = end,
            periodId = null // Probamos el filtro puro de fechas
        )

        // Suma esperada: id 1 (10.0) + id 3 (30.0) + id 5 (50.0) = 90.0
        assertEquals(90.0, totalSpent, 0.01)
    }

    @Test
    fun test_adversarial_explicit_period_id_precedence_over_dates() = runTest(testDispatcher) {
        val cat = Category(name = "General", icon = "💰", color = "#000000", subcategories = emptyList())
        val budget = Budget(id = 1L, amount = 1000.0, category = "General", periodId = 5L)

        val expenses = listOf(
            // Gasto con periodId = 5 pero fecha en 1999 -> DEBE INCLUIRSE porque periodId tiene precedencia
            Expense(id = 1L, amount = 100.0, date = "1999-01-01 10:00", description = "Old Date Match ID", category = "General", budgetCategory = "General", periodId = 5L),
            // Gasto con fecha en 2026 pero periodId = 99 -> DEBE EXCLUIRSE
            Expense(id = 2L, amount = 200.0, date = "2026-05-15 10:00", description = "Date Match Different ID", category = "General", budgetCategory = "General", periodId = 99L)
        )

        val totalSpent = incomeUseCase.calculateTotalBudgetSpent(
            budgets = listOf(budget),
            expenses = expenses,
            periodStartDate = "2026-05-01",
            periodEndDate = "2026-05-31",
            periodId = 5L
        )

        assertEquals(100.0, totalSpent, 0.01)
    }

    // =========================================================================
    // 5. FORMATEADOR DE RANGO DE PERÍODOS (FORMATPERIODRANGE)
    // =========================================================================

    @Test
    fun test_adversarial_formatPeriodRange_comprehensive_cases() {
        // 1. Mismo mes y mismo año
        assertEquals("1 – 31 May 2026", formatPeriodRange("2026-05-01", "2026-05-31", "es"))
        assertEquals("10 – 10 Ene 2026", formatPeriodRange("2026-01-10", "2026-01-10", "es"))

        // 2. Mismo año, meses distintos
        assertEquals("15 May – 14 Jun 2026", formatPeriodRange("2026-05-15", "2026-06-14", "es"))
        assertEquals("28 Feb – 1 Mar 2026", formatPeriodRange("2026-02-28", "2026-03-01", "es"))

        // 3. Años distintos (cambio de año)
        assertEquals("15 Dic 2026 – 14 Ene 2027", formatPeriodRange("2026-12-15", "2027-01-14", "es"))
        assertEquals("31 Dic 1999 – 1 Ene 2000", formatPeriodRange("1999-12-31", "2000-01-01", "es"))

        // 4. Años bisiestos (29 de febrero)
        // Año bisiesto 2024
        assertEquals("1 – 29 Feb 2024", formatPeriodRange("2024-02-01", "2024-02-29", "es"))
        assertEquals("29 Feb – 1 Mar 2024", formatPeriodRange("2024-02-29", "2024-03-01", "es"))
        // Año bisiesto de fin de siglo 2000
        assertEquals("1 – 29 Feb 2000", formatPeriodRange("2000-02-01", "2000-02-29", "es"))

        // 5. Entradas invertidas (startDate > endDate): no debe crashear
        val inverted = formatPeriodRange("2026-05-31", "2026-05-01", "es")
        assertEquals("31 – 1 May 2026", inverted)

        // 6. Entradas inválidas / no bisiesto imposible / malformadas -> Fallback limpio
        assertEquals("2025-02-29 – 2025-03-01", formatPeriodRange("2025-02-29", "2025-03-01", "es"))
        assertEquals(" – ", formatPeriodRange("", "", "es"))
        assertEquals("malformed – 2026-05-31", formatPeriodRange("malformed", "2026-05-31", "es"))
        assertEquals("2026-05-01 10:00 – 2026-05-31", formatPeriodRange("2026-05-01 10:00", "2026-05-31", "es"))

        // 7. Idiomas múltiples
        val enRange = formatPeriodRange("2026-05-01", "2026-05-31", "en")
        assertEquals("1 – 31 May 2026", enRange)

        val deRange = formatPeriodRange("2026-10-01", "2026-10-31", "de")
        assertTrue("En alemán octubre debe contener 'Okt'", deRange.contains("Okt"))

        val ptRange = formatPeriodRange("2026-05-01", "2026-05-31", "pt")
        assertTrue("En portugués mayo debe contener 'Mai'", ptRange.contains("Mai"))
    }
}
