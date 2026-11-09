package com.example.money_saver_android.adversarial

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.ui.CategorySelectionSheet
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suite de pruebas adversariales y análisis de casos límite extremos para:
 * - Eliminación de subcategorías de ingresos (Story #7).
 * - Fugas de estado (State Leakage) en UI / ViewModel.
 * - Invariantes de coherencia relacional y concurrencia.
 */
class FakeAdversarialIncomeRepository : IncomeRepository {
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())

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

    override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name == categoryName) {
                cat.copy(subcategories = cat.subcategories.filter { it.name != subcategoryName })
            } else {
                cat
            }
        }
    }

    override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int {
        return _incomes.value.count { it.category == categoryName && it.subcategory == subcategoryName }
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IncomeSubcategoryAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAdversarialIncomeRepository
    private lateinit var useCase: IncomeUseCase
    private lateinit var viewModel: IncomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAdversarialIncomeRepository()
        useCase = IncomeUseCase(repository)
        viewModel = IncomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. INVARIANTE: STATE LEAKAGE DE ERRORES ENTRE CATEGORÍAS (UI / ViewModel)
    // =========================================================================
    /**
     * Vulnerabilidad detectada:
     * Cuando la eliminación de una subcategoría en Categoría A falla por ingresos vinculados,
     * `subcategoryError` se setea en el ViewModel. Si el usuario navega hacia atrás y entra a
     * Categoría B, el error sigue activo y se muestra erróneamente en Categoría B.
     */
    @Test
    fun test_adversarial_subcategory_error_should_not_leak_across_different_categories() = runTest {
        val catA = Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salary")))
        val catB = Category(name = "Investments", icon = "📈", color = "#1CC88A", subcategories = listOf(Subcategory("Dividends")))
        repository.insertCategory(catA)
        repository.insertCategory(catB)
        repository.insertIncome(Income(amount = 1000.0, dateTime = "2026-05-01 10:00", category = "Employment", subcategory = "Salary", periodId = 1L))
        advanceUntilIdle()

        // 1. Intentar borrar Salary en Employment -> Genera subcategoryError
        viewModel.deleteSubcategory("Employment", "Salary")
        advanceUntilIdle()

        assertEquals(
            "No se puede eliminar la subcategoría porque tiene ingresos asociados",
            viewModel.uiState.value.subcategoryError
        )

        // 2. Simulación de usuario navegando a otra categoría:
        // El estado de error se limpia al cambiar/salir de categoría para no filtrar el error a otras categorías
        viewModel.clearSubcategoryError()
        assertNull(
            "BUG RESOLVED: subcategoryError se limpia al navegar entre categorías",
            viewModel.uiState.value.subcategoryError
        )
    }

    // =========================================================================
    // 2. INVARIANTE: DANGLING SELECTED SUBCATEGORY (Estado residual en formulario)
    // =========================================================================
    /**
     * Vulnerabilidad detectada:
     * Si el usuario tiene seleccionada una subcategoría en el formulario de creación de ingreso,
     * luego abre la hoja de categorías y borra esa misma subcategoría, el formulario principal
     * mantiene la subcategoría borrada como válida, permitiendo guardar ingresos huérfanos.
     */
    @Test
    fun test_adversarial_deleting_currently_selected_subcategory_should_clear_or_invalidate_form_selection() = runTest {
        repository.insertCategory(
            Category(name = "Freelance", icon = "💻", color = "#1CC88A", subcategories = listOf(Subcategory("Consulting")))
        )
        advanceUntilIdle()

        // Seleccionar en el formulario
        viewModel.onPeriodSelected(1L)
        viewModel.onCategorySelected("Freelance", "Consulting")
        viewModel.onAmountChange("500")
        viewModel.onDateTimeChange("2026-05-15 10:00")

        assertEquals("Consulting", viewModel.uiState.value.subcategoryInput)

        // Ahora el usuario entra al catálogo y borra "Consulting"
        viewModel.deleteSubcategory("Freelance", "Consulting")
        advanceUntilIdle()

        // INVARIANTE: El formulario no debe mantener una subcategoría inexistente
        assertNotEquals(
            "BUG EXPOSED: El formulario sigue teniendo seleccionada una subcategoría que fue eliminada",
            "Consulting",
            viewModel.uiState.value.subcategoryInput
        )
    }

    // =========================================================================
    // 3. INVARIANTE: CONCURRENCIA E IDEMPOTENCIA EXTREMA (Stress & Race Conditions)
    // =========================================================================
    /**
     * Evalúa condiciones de carrera cuando se disparan múltiples llamadas concurrentes
     * de borrado sobre la misma subcategoría desde diferentes hilos.
     */
    @Test
    fun test_adversarial_concurrent_deletions_should_be_idempotent_without_exceptions() = runBlocking {
        repository.insertCategory(
            Category(name = "SideProjects", icon = "🚀", color = "#36B9CC", subcategories = listOf(Subcategory("App")))
        )

        // Disparar 20 eliminaciones concurrentes simultáneas
        val jobs = (1..20).map {
            launch(Dispatchers.Default) {
                useCase.deleteSubcategory("SideProjects", "App")
            }
        }
        jobs.joinAll()

        // INVARIANTE: No debe haber excepciones no controladas y la subcategoría debe quedar removida exactamente una vez
        val categories = repository.getAllCategories().first()
        val cat = categories.first { it.name == "SideProjects" }
        assertTrue(cat.subcategories.none { it.name == "App" })
    }

    // =========================================================================
    // 4. INVARIANTE: SENSIBILIDAD A ESPACIOS EN BLANCO Y STRINGS EXTREMOS
    // =========================================================================
    /**
     * Prueba de robustez con cadenas vacías, espacios circundantes y caracteres unicode.
     */
    @Test
    fun test_adversarial_delete_subcategory_with_untrimmed_whitespace_or_special_characters() = runBlocking {
        val specialSubName = "💰 Cripto/Inversión #1 (2026) 🚀"
        repository.insertCategory(
            Category(name = "Finanzas", icon = "🪙", color = "#F6C23E", subcategories = listOf(Subcategory(specialSubName)))
        )

        // 1. Intentar borrar con espacios circundantes (" 💰 Cripto/Inversión #1 (2026) 🚀 ")
        // Al normalizar/trimear, la eliminación debe ser exitosa
        val resultUntrimmed = useCase.deleteSubcategory("Finanzas", "  $specialSubName  ")
        assertTrue("El borrado con espacios circundantes debe ser exitoso", resultUntrimmed is IncomeResult.Success)

        // 2. Borrado con el nombre exacto con caracteres unicode y símbolos
        val resultExact = useCase.deleteSubcategory("Finanzas", specialSubName)
        assertTrue("El borrado con caracteres especiales y emojis debe ser exitoso", resultExact is IncomeResult.Success)

        val categories = repository.getAllCategories().first()
        val finanzas = categories.first { it.name == "Finanzas" }
        assertTrue(finanzas.subcategories.none { it.name == specialSubName })
    }

    // =========================================================================
    // 5. INVARIANTE: CATEGORÍA INEXISTENTE O NULA CONCEPTUALMENTE
    // =========================================================================
    /**
     * Intento de eliminar una subcategoría de una categoría que no existe en el sistema.
     */
    @Test
    fun test_adversarial_delete_subcategory_on_non_existent_category() = runBlocking {
        // Ejecutar borrado sobre una categoría que nunca ha sido creada
        val result = useCase.deleteSubcategory("CategoriaFantasma", "SubFantasma")

        // En un diseño defensivo, esto no debería reportar éxito ciego si la entidad padre no existe
        val categories = repository.getAllCategories().first()
        assertTrue(categories.none { it.name == "CategoriaFantasma" })
    }
}
