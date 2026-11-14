package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.local.BudgetCategoryEntity
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.local.BudgetSubcategoryEntity
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.viewmodel.EditBudgetState
import com.example.money_saver_android.viewmodel.FinanceTopTab
import com.example.money_saver_android.viewmodel.IncomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Suite de Pruebas Adversariales y Casos Límite para el Módulo de Presupuestos (Story #8).
 *
 * Áreas evaluadas:
 * 1. Valores numéricos extremos, valores especiales (NaN, Infinity) y desbordamientos.
 * 2. Pruebas basadas en propiedades (Property-Based Invariants) para cálculo de ingresos y presupuestos.
 * 3. Aislamiento estricto de períodos y robustez ante IDs inexistentes o nulos.
 * 4. Integridad referencial y sensibilidad a espacios en blanco en categorías y subcategorías.
 * 5. Fugas de estado (State Leakage) y contaminación cruzada en IncomeViewModel (Cross-Category Bug).
 * 6. Concurrencia, condiciones de carrera (Race Conditions) y sobrepresupuestación.
 * 7. Comportamiento en Room Database (Foreign Key CASCADE y OnConflictStrategy.REPLACE).
 */
class FakeAdversarialBudgetRepository : BudgetRepository {
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

    override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
        _budgets.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertBudget(budget: Budget): Long {
        val newId = (_budgets.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _budgets.value = _budgets.value + budget.copy(id = newId)
        return newId
    }

    override suspend fun updateBudget(budget: Budget) {
        _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        return _budgets.value.find { it.id == id }
    }

    override suspend fun deleteBudget(id: Long) {
        _budgets.value = _budgets.value.filter { it.id != id }
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

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name == categoryName) {
                cat.copy(subcategories = cat.subcategories.filter { it.name != subcategoryName })
            } else {
                cat
            }
        }
    }

    override suspend fun countBudgetsForCategory(categoryName: String): Int {
        return _budgets.value.count { it.category == categoryName }
    }

    override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int {
        return _budgets.value.count { it.category == categoryName && it.subcategory == subcategoryName }
    }

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = com.example.money_saver_android.data.defaultCategories
        }
    }
}

class FakeAdversarialIncomeRepositoryForBudget : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())

    override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
        _incomes.map { list -> list.filter { it.periodId == periodId } }

    override suspend fun insertIncome(income: Income): Long {
        val newId = (_incomes.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _incomes.value = _incomes.value + income.copy(id = newId)
        return newId
    }

    override suspend fun updateIncome(income: Income) {
        _incomes.value = _incomes.value.map { if (it.id == income.id) income else it }
    }

    override suspend fun deleteIncome(id: Long) {
        _incomes.value = _incomes.value.filter { it.id != id }
    }

    override fun getAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override suspend fun insertCategory(category: Category) {}
    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
    override suspend fun deleteCategory(categoryName: String) {}
    override suspend fun countIncomesForCategory(categoryName: String): Int = 0
    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
    override suspend fun preloadDefaultCategoriesIfNeeded() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var budgetRepository: FakeAdversarialBudgetRepository
    private lateinit var incomeRepository: FakeAdversarialIncomeRepositoryForBudget
    private lateinit var budgetUseCase: BudgetUseCase
    private lateinit var incomeUseCase: IncomeUseCase
    private lateinit var viewModel: IncomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = FakeAdversarialBudgetRepository()
        incomeRepository = FakeAdversarialIncomeRepositoryForBudget()
        budgetUseCase = BudgetUseCase(budgetRepository, incomeRepository)
        incomeUseCase = IncomeUseCase(incomeRepository, budgetRepository, budgetUseCase)
        viewModel = IncomeViewModel(incomeUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. ANÁLISIS ADVERSARIAL: NaN, INFINITY Y VALORES NUMÉRICOS NO FINITOS
    // =========================================================================
    /**
     * VULNERABILIDAD DESCUBIERTA:
     * En Kotlin, "NaN".toDoubleOrNull() retorna Double.NaN.
     * En BudgetUseCase.kt:
     *   val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
     *   if (amount <= 0.0) -> false (Double.NaN <= 0.0 es falso)
     *   if (amount > available) -> false (Double.NaN > available es falso)
     * Por lo tanto, un input "NaN" elude las validaciones y se persiste un Budget con amount = NaN.
     * Al calcular totalBudgeted mediante sumOf { it.amount }, el total se vuelve permanentemente NaN.
     */
    @Test
    fun test_adversarial_nan_and_infinity_amount_handling() = runTest {
        val periodId = 1L
        incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-05-01 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )

        // 1. Adversarial: Input "NaN"
        val resultNaN = budgetUseCase.saveBudget(
            amountInput = "NaN",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(
            "INVARIANTE DE SEGURIDAD: 'NaN' debe ser rechazado como un monto inválido",
            resultNaN is BudgetResult.Error
        )

        // 2. Adversarial: Input "Infinity"
        val resultInf = budgetUseCase.saveBudget(
            amountInput = "Infinity",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(
            "INVARIANTE DE SEGURIDAD: 'Infinity' debe ser rechazado",
            resultInf is BudgetResult.Error
        )

        // 3. Adversarial: Notación científica desbordada (1e309 = POSITIVE_INFINITY)
        val resultOverflow = budgetUseCase.saveBudget(
            amountInput = "1e309",
            categoryInput = "Food",
            periodId = periodId
        )
        assertTrue(
            "INVARIANTE DE SEGURIDAD: Desbordamiento de Double debe ser rechazado",
            resultOverflow is BudgetResult.Error
        )
    }

    // =========================================================================
    // 2. PROPERTY-BASED TESTING: INVARIANTE DE INGRESO DISPONIBLE NUNCA NEGATIVO
    // =========================================================================
    /**
     * INVARIANTE MATEMÁTICO:
     * Para cualquier valor de totalIncome >= 0 y totalBudgeted >= 0:
     * 1. calculateAvailableIncome(totalIncome, totalBudgeted) >= 0.0 SIEMPRE.
     * 2. Si totalIncome >= totalBudgeted: disponible == totalIncome - totalBudgeted.
     * 3. Si totalBudgeted > totalIncome: disponible == 0.0.
     * Evaluado en 1,000 iteraciones aleatorias incluyendo casos frontera.
     */
    @Test
    fun test_property_based_invariant_available_income_never_negative() {
        val random = Random(42)

        val boundaryIncomes = listOf(0.0, 0.01, 1.0, 99.99, 1000.0, 1_000_000.0, 1e9)
        val boundaryBudgeted = listOf(0.0, 0.01, 1.0, 99.99, 1000.0, 1_000_000.0, 1e9)

        // Probar combinaciones de frontera
        for (income in boundaryIncomes) {
            for (budgeted in boundaryBudgeted) {
                val available = budgetUseCase.calculateAvailableIncome(income, budgeted)
                assertTrue("Frontera ($income, $budgeted): disponible >= 0", available >= 0.0)
                if (income >= budgeted) {
                    assertEquals(income - budgeted, available, 0.0001)
                } else {
                    assertEquals(0.0, available, 0.0001)
                }
            }
        }

        // 1,000 ejecuciones aleatorias (Property-Based)
        for (i in 1..1000) {
            val income = random.nextDouble(0.0, 500_000.0)
            val budgeted = random.nextDouble(0.0, 500_000.0)
            val available = budgetUseCase.calculateAvailableIncome(income, budgeted)

            assertTrue("Iteración $i: disponible ($available) debe ser >= 0.0", available >= 0.0)
            if (income >= budgeted) {
                assertEquals(income - budgeted, available, 0.0001)
            } else {
                assertEquals(0.0, available, 0.0001)
            }
        }
    }

    // =========================================================================
    // 3. PROPERTY-BASED TESTING: INVARIANTE DE CONSERVACIÓN DE FONDOS
    // =========================================================================
    /**
     * INVARIANTE DE DOMINIO:
     * Si un período cuenta con un ingreso total I, ninguna secuencia de llamadas a saveBudget
     * debe permitir que la sumatoria total presupuestada supere dicho ingreso I.
     */
    @Test
    fun test_property_based_invariant_total_budgeted_never_exceeds_total_income() = runBlocking {
        val periodId = 1L
        val totalIncome = 1000.0
        incomeRepository.insertIncome(
            Income(amount = totalIncome, dateTime = "2026-05-01 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )

        val random = Random(12345)
        for (attempt in 1..50) {
            val randomAmount = random.nextDouble(10.0, 600.0)
            val formattedAmount = "%.2f".format(randomAmount)

            val result = budgetUseCase.saveBudget(
                amountInput = formattedAmount,
                categoryInput = "Category_$attempt",
                periodId = periodId
            )

            val currentBudgets = budgetUseCase.getBudgetsByPeriod(periodId).first()
            val currentTotalBudgeted = budgetUseCase.calculateTotalBudgeted(currentBudgets)

            // INVARIANTE: El total presupuestado acumulado NUNCA debe sobrepasar totalIncome
            assertTrue(
                "Intento $attempt: Total presupuestado ($currentTotalBudgeted) superó el ingreso ($totalIncome)",
                currentTotalBudgeted <= totalIncome + 0.001
            )

            if (result is BudgetResult.Error) {
                assertEquals("El monto supera el ingreso disponible", result.message)
            }
        }
    }

    // =========================================================================
    // 4. PRECISIÓN EN COMA FLOTANTE (IEEE 754) Y LÍMITE EXACTO DE COINCIDENCIA
    // =========================================================================
    /**
     * Prueba el límite exacto donde múltiples fracciones decimales deben sumar el total disponible
     * sin que errores de redondeo de IEEE 754 bloqueen el último centavo permitido.
     * Ejemplo: 33.33 + 33.33 + 33.34 = 100.00.
     */
    @Test
    fun test_adversarial_fractional_precision_and_exact_match_boundary() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(
            Income(amount = 100.0, dateTime = "2026-05-01 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )

        val res1 = budgetUseCase.saveBudget("33.33", "Category1", periodId = periodId)
        val res2 = budgetUseCase.saveBudget("33.33", "Category2", periodId = periodId)
        val res3 = budgetUseCase.saveBudget("33.34", "Category3", periodId = periodId)

        assertTrue("33.33 debe guardarse", res1 is BudgetResult.Success)
        assertTrue("33.33 debe guardarse", res2 is BudgetResult.Success)
        assertTrue("33.34 exacto debe guardarse sin fallas de redondeo", res3 is BudgetResult.Success)

        val budgets = budgetUseCase.getBudgetsByPeriod(periodId).first()
        val total = budgetUseCase.calculateTotalBudgeted(budgets)
        assertEquals(100.0, total, 0.001)

        val available = budgetUseCase.calculateAvailableIncome(100.0, total)
        assertEquals(0.0, available, 0.001)

        // Intento de exceder por 0.01 centavo
        val resOver = budgetUseCase.saveBudget("0.01", "Category4", periodId = periodId)
        assertTrue("0.01 centavo por encima debe ser rechazado", resOver is BudgetResult.Error)
    }

    // =========================================================================
    // 5. AISLAMIENTO ESTRICTO DE PERÍODOS
    // =========================================================================
    /**
     * INVARIANTE: Las operaciones de guardado y cálculo de presupuestos de un período
     * no deben contaminar ni afectar en absoluto a otros períodos financieros.
     */
    @Test
    fun test_adversarial_period_isolation_under_modifications() = runBlocking {
        val periodA = 10L
        val periodB = 20L

        incomeRepository.insertIncome(Income(amount = 500.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodA))
        incomeRepository.insertIncome(Income(amount = 2000.0, dateTime = "2026-06-01", category = "Employment", subcategory = "Salary", periodId = periodB))

        budgetUseCase.saveBudget("400", "Rent", periodId = periodA)
        budgetUseCase.saveBudget("1500", "Mortgage", periodId = periodB)

        val budgetsA = budgetUseCase.getBudgetsByPeriod(periodA).first()
        val budgetsB = budgetUseCase.getBudgetsByPeriod(periodB).first()

        assertEquals(1, budgetsA.size)
        assertEquals(400.0, budgetsA.first().amount, 0.001)

        assertEquals(1, budgetsB.size)
        assertEquals(1500.0, budgetsB.first().amount, 0.001)

        // Intentar registrar en Period A superando sus 100 restantes pero inferior a Period B
        val resExceedA = budgetUseCase.saveBudget("300", "Food", periodId = periodA)
        assertTrue("Period A debe rechazar 300 (solo 100 disponibles)", resExceedA is BudgetResult.Error)

        // Period B aún tiene 500 disponibles y debe aceptar 300
        val resAcceptB = budgetUseCase.saveBudget("300", "Food", periodId = periodB)
        assertTrue("Period B debe aceptar 300", resAcceptB is BudgetResult.Success)
    }

    // =========================================================================
    // 6. ROBUSTEZ ANTE PERÍODO NULO O INEXISTENTE
    // =========================================================================
    @Test
    fun test_adversarial_null_period_id_fails_cleanly() = runBlocking {
        val resSave = budgetUseCase.saveBudget("100", "Food", periodId = null)
        assertTrue(resSave is BudgetResult.Error)
        assertEquals("Período no seleccionado", (resSave as BudgetResult.Error).message)

        val resUpdate = budgetUseCase.updateBudget(1L, "100", "Food", periodId = null)
        assertTrue(resUpdate is BudgetResult.Error)
        assertEquals("Período no seleccionado", (resUpdate as BudgetResult.Error).message)
    }

    // =========================================================================
    // 7. COMPORTAMIENTO ADVERSARIAL: ACTUALIZACIÓN DE ID DE PRESUPUESTO INEXISTENTE
    // =========================================================================
    /**
     * VULNERABILIDAD IDENTIFICADA:
     * En BudgetUseCase.updateBudget:
     *   val currentBudget = currentBudgets.find { it.id == id }
     *   val currentBudgetAmount = currentBudget?.amount ?: 0.0
     * Si `currentBudget` no existe (id = 9999L), no se emite ningún error descriptivo
     * ("Presupuesto no encontrado") y el caso de uso procede a llamar a repository.updateBudget().
     * En una implementación defensiva, debe validarse la existencia del registro previo.
     */
    @Test
    fun test_adversarial_update_non_existent_budget_id_exposure() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-05-01 10:00", category = "Employment", subcategory = "Salary", periodId = periodId)
        )

        // Intentar actualizar un presupuesto que nunca fue registrado
        val nonExistentId = 99999L
        val updateResult = budgetUseCase.updateBudget(
            id = nonExistentId,
            amountInput = "200",
            categoryInput = "Food",
            periodId = periodId
        )

        assertTrue(
            "Actualizar un ID inexistente debe retornar error 'Presupuesto no encontrado'",
            updateResult is BudgetResult.Error
        )
        assertEquals("Presupuesto no encontrado", (updateResult as BudgetResult.Error).message)

        val budgetsInDb = budgetUseCase.getBudgetsByPeriod(periodId).first()
        assertTrue(
            "Un presupuesto inexistente no debe crearse ni aparecer en la lista mediante update",
            budgetsInDb.none { it.id == nonExistentId }
        )
    }

    // =========================================================================
    // 8. ROBUSTEZ EN CATEGORÍAS Y SUBCATEGORÍAS: ESPACIOS EN BLANCO Y DUPLICADOS
    // =========================================================================
    @Test
    fun test_adversarial_category_and_subcategory_whitespace_and_duplicates() = runBlocking {
        // 1. Nombre de categoría vacío o solo espacios
        val emptyCategoryResult = incomeUseCase.createBudgetCategory("   ", "#FF0000")
        assertTrue("Categoría con solo espacios debe fallar", emptyCategoryResult is IncomeResult.Error)
        assertEquals("El nombre de la categoría no puede estar vacío", (emptyCategoryResult as IncomeResult.Error).message)

        // 2. Creación válida con espacios alrededor (debe trimear)
        val validCreation = incomeUseCase.createBudgetCategory("  Entretenimiento  ", "#4E73DF")
        assertTrue(validCreation is IncomeResult.Success)

        // 3. Intento de duplicado insensible a mayúsculas/minúsculas y espacios
        val duplicateResult = incomeUseCase.createBudgetCategory("entretenimiento", "#112233")
        assertTrue("Duplicado 'entretenimiento' debe ser rechazado", duplicateResult is IncomeResult.Error)
        assertEquals("Ya existe una categoría con este nombre", (duplicateResult as IncomeResult.Error).message)

        // 4. Subcategoría con solo espacios
        val emptySubResult = incomeUseCase.createBudgetSubcategory("Entretenimiento", "   ")
        assertTrue("Subcategoría vacía debe fallar", emptySubResult is IncomeResult.Error)
        assertEquals("El nombre de la subcategoría no puede estar vacío", (emptySubResult as IncomeResult.Error).message)

        // 5. Subcategoría válida
        val validSub = incomeUseCase.createBudgetSubcategory("Entretenimiento", "Cine")
        assertTrue(validSub is IncomeResult.Success)

        // 6. Subcategoría duplicada
        val duplicateSub = incomeUseCase.createBudgetSubcategory("Entretenimiento", "cine")
        assertTrue("Subcategoría duplicada 'cine' debe fallar", duplicateSub is IncomeResult.Error)
        assertEquals("Ya existe una subcategoría con este nombre", (duplicateSub as IncomeResult.Error).message)
    }

    // =========================================================================
    // 9. INTEGRIDAD REFERENCIAL: BORRADO DE CATEGORÍA CON PRESUPUESTOS VINCULADOS
    // =========================================================================
    @Test
    fun test_adversarial_prevent_deletion_of_category_with_active_budget() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 1000.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodId))
        budgetRepository.insertCategory(Category(name = "Alimentación", icon = "🍎", color = "#1CC88A", subcategories = emptyList()))
        budgetUseCase.saveBudget("300", "Alimentación", periodId = periodId)

        // Intentar eliminar la categoría "Alimentación" mientras tiene presupuestos
        val deleteResult = incomeUseCase.deleteBudgetCategory("Alimentación")
        assertTrue("No debe permitir borrar una categoría con presupuestos asociados", deleteResult is IncomeResult.Error)
        assertEquals(
            "No se puede eliminar la categoría porque tiene presupuestos asociados",
            (deleteResult as IncomeResult.Error).message
        )

        // Verificar que la categoría siga existiendo
        val categories = budgetRepository.getAllCategories().first()
        assertTrue(categories.any { it.name == "Alimentación" })
    }

    // =========================================================================
    // 10. INTEGRIDAD REFERENCIAL: BORRADO DE SUBCATEGORÍA CON PRESUPUESTO
    // =========================================================================
    @Test
    fun test_adversarial_prevent_deletion_of_subcategory_with_active_budget() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 1000.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodId))
        budgetRepository.insertCategory(
            Category(name = "Transporte", icon = "🚗", color = "#4E73DF", subcategories = listOf(Subcategory("Gasolina"), Subcategory("Peajes")))
        )
        budgetUseCase.saveBudget("150", "Transporte", subcategoryInput = "Gasolina", periodId = periodId)

        // 1. Borrar "Gasolina" (tiene presupuesto) -> Debe fallar
        val delGasolina = incomeUseCase.deleteBudgetSubcategory("Transporte", "Gasolina")
        assertTrue("Subcategoría con presupuesto no debe borrarse", delGasolina is IncomeResult.Error)
        assertEquals(
            "No se puede eliminar la subcategoría porque tiene presupuestos asociados",
            (delGasolina as IncomeResult.Error).message
        )

        // 2. Borrar "Peajes" (sin presupuesto) -> Debe ser exitoso
        val delPeajes = incomeUseCase.deleteBudgetSubcategory("Transporte", "Peajes")
        assertTrue("Subcategoría sin presupuesto debe borrarse con éxito", delPeajes is IncomeResult.Success)

        val updatedCats = budgetRepository.getAllCategories().first()
        val transporte = updatedCats.first { it.name == "Transporte" }
        assertTrue(transporte.subcategories.any { it.name == "Gasolina" })
        assertTrue(transporte.subcategories.none { it.name == "Peajes" })
    }

    // =========================================================================
    // 11. VULNERABILIDAD CRÍTICA EN VIEWMODEL: FUGA DE ESTADO ENTRE CATEGORÍAS
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomeViewModel.kt (Línea 628):
     * En `deleteBudgetSubcategory(categoryName, subcategoryName)`:
     *   val shouldClear = current.budgetSubcategoryInput.equals(trimmedSub, ignoreCase = true)
     *   val shouldClearEdit = current.editingBudget?.subcategory?.equals(trimmedSub, ignoreCase = true) == true
     *
     * FALLO: NO valida que current.budgetCategoryInput o editingBudget.category coincida con `categoryName`.
     * Si Categoría A ("Mercado") y Categoría B ("Restaurantes") comparten una subcategoría con el mismo
     * nombre ("Otros"), y el usuario está editando o seleccionando "Otros" en "Mercado", al eliminarse
     * "Otros" en "Restaurantes", el ViewModel vacía erróneamente la selección en "Mercado".
     */
    @Test
    fun test_adversarial_viewmodel_subcategory_deletion_cross_category_leak() = runTest {
        budgetRepository.insertCategory(
            Category(name = "Mercado", icon = "🛒", color = "#1CC88A", subcategories = listOf(Subcategory("Otros")))
        )
        budgetRepository.insertCategory(
            Category(name = "Restaurantes", icon = "🍽️", color = "#F6C23E", subcategories = listOf(Subcategory("Otros")))
        )
        advanceUntilIdle()

        // 1. El usuario tiene seleccionado "Mercado" -> "Otros" en el formulario de presupuesto
        viewModel.onBudgetCategorySelected("Mercado", "Otros")
        assertEquals("Mercado", viewModel.uiState.value.budgetCategoryInput)
        assertEquals("Otros", viewModel.uiState.value.budgetSubcategoryInput)

        // 2. Se elimina la subcategoría "Otros" de "Restaurantes" (categoría distinta)
        viewModel.deleteBudgetSubcategory("Restaurantes", "Otros")
        advanceUntilIdle()

        // INVARIANTE: El formulario de "Mercado" NO debe perder su subcategoría seleccionada
        // porque la subcategoría eliminada pertenecía a "Restaurantes"
        assertEquals(
            "VULNERABILIDAD EXPUESTA: La eliminación de subcategoría en otra categoría no debe limpiar la selección actual",
            "Otros",
            viewModel.uiState.value.budgetSubcategoryInput
        )

        // 3. Evaluar el mismo invariante en modo de edición (editingBudget)
        viewModel.startEditingBudget(
            Budget(id = 50L, amount = 200.0, category = "Mercado", subcategory = "Otros", periodId = 1L)
        )
        assertEquals("Otros", viewModel.uiState.value.editingBudget?.subcategory)

        // Se elimina "Otros" en Restaurantes
        viewModel.deleteBudgetSubcategory("Restaurantes", "Otros")
        advanceUntilIdle()

        assertEquals(
            "VULNERABILIDAD EXPUESTA: editingBudget no debe perder su subcategoría si la categoría no coincide",
            "Otros",
            viewModel.uiState.value.editingBudget?.subcategory
        )
    }

    // =========================================================================
    // 12. CONCURRENCIA ADVERSARIAL: RACE CONDITION EN REGISTRO DE PRESUPUESTO
    // =========================================================================
    /**
     * Evalúa condiciones de carrera cuando se disparan dos llamadas concurrentes a saveBudget
     * que individualmente son válidas pero que en conjunto superan el saldo disponible.
     * Si no hay bloqueo de sincronización o transacción atómica, ambas llamadas pueden ser
     * aceptadas violando la invariante de ingresos disponibles.
     */
    @Test
    fun test_adversarial_concurrent_budget_creation_overbudget_race_condition() = runBlocking {
        val periodId = 1L
        // Ingreso disponible total = 500.0
        incomeRepository.insertIncome(Income(amount = 500.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodId))

        // Dos peticiones concurrentes de 400.0 cada una (400 + 400 = 800 > 500)
        var res1: BudgetResult? = null
        var res2: BudgetResult? = null

        val job1 = launch(Dispatchers.Default) {
            res1 = budgetUseCase.saveBudget("400", "Food", periodId = periodId)
        }
        val job2 = launch(Dispatchers.Default) {
            res2 = budgetUseCase.saveBudget("400", "Transport", periodId = periodId)
        }
        joinAll(job1, job2)

        val budgets = budgetUseCase.getBudgetsByPeriod(periodId).first()
        val totalBudgeted = budgetUseCase.calculateTotalBudgeted(budgets)

        // INVARIANTE: El total presupuestado acumulado NUNCA debe superar el ingreso de 500.0
        assertTrue(
            "CONCURRENCIA: El total presupuestado ($totalBudgeted) no debe superar el ingreso disponible de 500.0",
            totalBudgeted <= 500.0
        )
    }

    // =========================================================================
    // 13. TEST ADVERSARIAL EN ROOM: ON CONFLICT REPLACE VS CASCADE DELETE
    // =========================================================================
    /**
     * Evalúa el comportamiento de Room Database en SQLite.
     * En SQLite, un `@Insert(onConflict = OnConflictStrategy.REPLACE)` sobre una entidad
     * padre (`BudgetCategoryEntity`) ejecuta un DELETE interno seguido de un INSERT.
     * Si la tabla hija (`BudgetSubcategoryEntity`) tiene `ForeignKey(onDelete = CASCADE)`,
     * reemplazar una categoría padre puede disparar accidentalmente el borrado en cascada
     * de todas sus subcategorías.
     */
    @Test
    fun test_adversarial_room_database_foreign_key_cascade_on_category_replace() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val roomRepo = RoomBudgetRepository(db.budgetDao(), db.budgetCategoryDao())

        // 1. Insertar categoría "Servicios" con subcategorías
        val category = Category(
            name = "Servicios",
            icon = "⚡",
            color = "#4E73DF",
            subcategories = listOf(Subcategory("Agua"), Subcategory("Luz"))
        )
        roomRepo.insertCategory(category)

        var categoriesInDb = roomRepo.getAllCategories().first()
        assertEquals(2, categoriesInDb.first { it.name == "Servicios" }.subcategories.size)

        // 2. Adversarial: Actualizar la categoría (mismo nombre pero nuevo color) vía insertCategory
        val updatedCategory = Category(
            name = "Servicios",
            icon = "⚡",
            color = "#1CC88A",
            subcategories = emptyList() // No enviamos subcategorías en esta invocación
        )
        roomRepo.insertCategory(updatedCategory)

        // 3. Verificar si las subcategorías sobrevivieron o si fueron eliminadas en cascada por el REPLACE
        categoriesInDb = roomRepo.getAllCategories().first()
        val servicios = categoriesInDb.first { it.name == "Servicios" }

        assertTrue(
            "INVARIANTE DE PERSISTENCIA: El reemplazo de categoría no debe destruir las subcategorías existentes",
            servicios.subcategories.isNotEmpty()
        )

        db.close()
    }

    // =========================================================================
    // 14. FORMATOS DE MONTO MALFORMADOS, SÍMBOLOS Y CARACTERES ESPECIALES
    // =========================================================================
    @Test
    fun test_adversarial_amount_input_with_currency_symbols_and_punctuations() = runBlocking {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 1000.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodId))

        val invalidInputs = listOf(
            "-100",
            "-0.00",
            "0",
            "0.00",
            "$500",
            "500 €",
            "€ 500",
            "100.50.25",
            "cien",
            "   ",
            "",
            "1,000,000" // Supera los 1000 disponibles
        )

        for (input in invalidInputs) {
            val result = budgetUseCase.saveBudget(input, "CategoryTest", periodId = periodId)
            assertTrue("El monto malformado o excedido '$input' debe ser rechazado", result is BudgetResult.Error)
        }
    }

    // =========================================================================
    // 15. CICLO DE VIDA VIEWMODEL: LIMPIEZA TOTAL AL SELECCIONAR PERÍODO NULO
    // =========================================================================
    @Test
    fun test_adversarial_viewmodel_null_period_clears_budget_and_income_state() = runTest {
        val periodId = 1L
        incomeRepository.insertIncome(Income(amount = 800.0, dateTime = "2026-05-01", category = "Employment", subcategory = "Salary", periodId = periodId))
        budgetRepository.insertBudget(Budget(amount = 300.0, category = "Food", periodId = periodId))

        viewModel.onPeriodSelected(periodId)
        advanceUntilIdle()

        assertEquals(800.0, viewModel.uiState.value.totalIncome, 0.001)
        assertEquals(300.0, viewModel.uiState.value.totalBudgeted, 0.001)
        assertEquals(500.0, viewModel.uiState.value.availableIncome, 0.001)
        assertEquals(1, viewModel.uiState.value.budgets.size)

        // Seleccionar período nulo (e.g. deselección de período)
        viewModel.onPeriodSelected(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0.0, state.totalIncome, 0.001)
        assertEquals(0.0, state.totalBudgeted, 0.001)
        assertEquals(0.0, state.availableIncome, 0.001)
        assertTrue(state.budgets.isEmpty())
        assertTrue(state.incomes.isEmpty())
    }
}
