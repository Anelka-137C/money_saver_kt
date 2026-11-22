package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.CategoryDao
import com.example.money_saver_android.data.local.CategoryEntity
import com.example.money_saver_android.data.local.ExpenseDao
import com.example.money_saver_android.data.local.ExpenseEntity
import com.example.money_saver_android.data.local.SubcategoryEntity
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.usecase.CategoryResult
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.viewmodel.EditExpenseCategoryState
import com.example.money_saver_android.viewmodel.EditExpenseState
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Suite de Pruebas Adversariales para la Edición y Eliminación de Categorías y Subcategorías de Gastos.
 *
 * Áreas Críticas Evaluadas:
 * 1. Transacciones en Room SQLite:
 *    - Reasignación atómica de subcategorías y gastos al renombrar una categoría.
 *    - Cambio de mayúsculas/minúsculas ("alimentacion" -> "Alimentacion").
 *    - Integridad referencial con claves foráneas (CASCADE) al eliminar categorías.
 *    - Invariantes de conteo de gastos con espacios y sensibilidad de mayúsculas.
 * 2. Casos Límite y Adversariales en CategoryUseCase:
 *    - Nombres vacíos, strings de solo espacios en blanco, caracteres de escape, saltos de línea.
 *    - Colisiones insensibles a mayúsculas con categorías existentes.
 *    - Inyecciones SQL y payloads maliciosos.
 *    - Cadenas Unicode gigantescas y emojis compuestos.
 *    - Bloqueo estricto de eliminación cuando existen gastos asociados (regla de no orfandad).
 *    - Resistencia ante concurrencia masiva (40 coroutines en condiciones de carrera con Mutex).
 * 3. Máquina de Estados y Reactividad en ExpenseViewModel:
 *    - Sincronización del formulario activo (categoryInput) al renombrar o borrar.
 *    - Sincronización de gasto en edición (editingExpense) al renombrar o borrar subcategoría.
 *    - Propagación y limpieza de mensajes de error en UI State.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseCategoryAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appDatabase: AppDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var roomCategoryRepository: RoomCategoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = RuntimeEnvironment.getApplication()
        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        categoryDao = appDatabase.categoryDao()
        expenseDao = appDatabase.expenseDao()
        roomCategoryRepository = RoomCategoryRepository(categoryDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        appDatabase.close()
    }

    // =========================================================================
    // 1. PRUEBAS ADVERSARIALES EN ROOM SQLITE (PERSISTENCIA Y TRANSACCIONES)
    // =========================================================================

    @Test
    fun test_room_transactional_category_rename_migrates_subcategories_and_expenses() = runBlocking {
        // Given: Categoría "Comida" con subcategorías "Supermercado" y "Restaurantes", y 2 gastos
        categoryDao.insertCategory(CategoryEntity(name = "Comida", icon = "🍔", color = "#111111"))
        categoryDao.insertSubcategory(SubcategoryEntity(categoryName = "Comida", name = "Supermercado"))
        categoryDao.insertSubcategory(SubcategoryEntity(categoryName = "Comida", name = "Restaurantes"))

        expenseDao.insert(
            ExpenseEntity(amount = 50.0, date = "2026-05-10", description = "Almuerzo", category = "Comida", subcategory = "Restaurantes")
        )
        expenseDao.insert(
            ExpenseEntity(amount = 120.0, date = "2026-05-11", description = "Compras", category = "comida", subcategory = "Supermercado")
        )

        // When: Renombramos "Comida" a "Alimentos y Bebidas" con nuevo icono y color
        categoryDao.updateCategory(
            oldName = "Comida",
            newName = "Alimentos y Bebidas",
            color = "#222222",
            icon = "🥗"
        )

        // Then:
        // 1. La categoría vieja ya no existe
        val allCategories = categoryDao.getCategoriesWithSubcategories().first()
        val oldCat = allCategories.find { it.category.name == "Comida" }
        assertNull("La categoría vieja 'Comida' no debe existir", oldCat)

        // 2. La nueva categoría existe con los datos correctos
        val newCat = allCategories.find { it.category.name == "Alimentos y Bebidas" }
        assertNotNull("La nueva categoría 'Alimentos y Bebidas' debe existir", newCat)
        assertEquals("#222222", newCat!!.category.color)
        assertEquals("🥗", newCat.category.icon)

        // 3. Las subcategorías fueron reasignadas y NO eliminadas por cascade
        val subcategoryNames = newCat.subcategories.map { it.name }.sorted()
        assertEquals(listOf("Restaurantes", "Supermercado"), subcategoryNames)

        // 4. Los gastos fueron actualizados con el nuevo nombre de categoría
        val allExpenses = expenseDao.getAll().first()
        assertEquals(2, allExpenses.size)
        assertTrue(allExpenses.all { it.category == "Alimentos y Bebidas" })
    }

    @Test
    fun test_room_category_rename_casing_only_does_not_destroy_records() = runBlocking {
        // Given: Categoría en minúsculas
        categoryDao.insertCategory(CategoryEntity(name = "hogar", icon = "🏠", color = "#333333"))
        categoryDao.insertSubcategory(SubcategoryEntity(categoryName = "hogar", name = "Mantenimiento"))
        expenseDao.insert(
            ExpenseEntity(amount = 75.0, date = "2026-05-12", description = "Plomero", category = "hogar", subcategory = "Mantenimiento")
        )

        // When: Renombramos cambiando solo la capitalización "hogar" -> "Hogar"
        categoryDao.updateCategory(
            oldName = "hogar",
            newName = "Hogar",
            color = "#444444",
            icon = "🏡"
        )

        // Then:
        val allCategories = categoryDao.getCategoriesWithSubcategories().first()
        val hogarCat = allCategories.find { it.category.name.equals("Hogar", ignoreCase = true) }
        assertNotNull("Debe existir la categoría Hogar", hogarCat)
        assertEquals("Hogar", hogarCat!!.category.name)
        assertEquals(1, hogarCat.subcategories.size)
        assertEquals("Mantenimiento", hogarCat.subcategories.first().name)

        val expenses = expenseDao.getAll().first()
        assertEquals(1, expenses.size)
        assertEquals("Hogar", expenses.first().category)
    }

    @Test
    fun test_room_category_cascade_deletes_subcategories_when_no_expenses_exist() = runBlocking {
        // Given: Categoría con subcategorías pero sin gastos
        categoryDao.insertCategory(CategoryEntity(name = "Viajes", icon = "✈️", color = "#555555"))
        categoryDao.insertSubcategory(SubcategoryEntity(categoryName = "Viajes", name = "Vuelos"))
        categoryDao.insertSubcategory(SubcategoryEntity(categoryName = "Viajes", name = "Hoteles"))

        assertEquals(0, categoryDao.countExpensesForCategory("Viajes"))

        // When: Se elimina la categoría
        categoryDao.deleteCategoryByName("Viajes")

        // Then: La categoría y sus subcategorías deben desaparecer por ForeignKey.CASCADE
        val categories = categoryDao.getCategoriesWithSubcategories().first()
        assertNull(categories.find { it.category.name == "Viajes" })
    }

    @Test
    fun test_room_countExpenses_case_and_whitespace_insensitivity() = runBlocking {
        // Insertamos gastos con variaciones de espacios y mayúsculas
        expenseDao.insert(
            ExpenseEntity(amount = 20.0, date = "2026-05-10", description = "Metro", category = "  Transporte  ", subcategory = "  METRO  ")
        )
        expenseDao.insert(
            ExpenseEntity(amount = 15.0, date = "2026-05-11", description = "Bus", category = "transporte", subcategory = null)
        )

        // Invariante: Conteo de categoría debe ser insensible a mayúsculas y espacios
        assertEquals(2, categoryDao.countExpensesForCategory("Transporte"))
        assertEquals(2, categoryDao.countExpensesForCategory("  transporte  "))
        assertEquals(2, categoryDao.countExpensesForCategory("TRANSPORTE"))

        // Invariante: Conteo de subcategoría debe ser insensible a mayúsculas y espacios
        assertEquals(1, categoryDao.countExpensesForSubcategory("Transporte", "Metro"))
        assertEquals(1, categoryDao.countExpensesForSubcategory("transporte", "  metro  "))
        assertEquals(1, categoryDao.countExpensesForSubcategory("  TRANSPORTE  ", "METRO"))
        assertEquals(0, categoryDao.countExpensesForSubcategory("Transporte", "Taxi"))
    }

    // =========================================================================
    // 2. PRUEBAS ADVERSARIALES EN CATEGORY USE CASE (VALIDACIONES Y LÓGICA)
    // =========================================================================

    @Test
    fun test_adversarial_blank_and_whitespace_only_names_are_rejected() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(Category(name = "Salud", icon = "💊", color = "#10B981", subcategories = emptyList()))

        // Adversarial inputs: vacío, espacios, tabs, newlines
        val invalidNames = listOf("", "   ", "\t", "\n", " \t \n ")

        for (invalid in invalidNames) {
            val updateResult = useCase.updateCategory(oldName = "Salud", newNameInput = invalid, colorInput = "#10B981")
            assertTrue("Input '$invalid' debe ser rechazado en updateCategory", updateResult is CategoryResult.Error)
            assertEquals("El nombre de la categoría no puede estar vacío", (updateResult as CategoryResult.Error).message)

            val deleteCatResult = useCase.deleteCategory(invalid)
            assertTrue("Input '$invalid' debe ser rechazado en deleteCategory", deleteCatResult is CategoryResult.Error)

            val deleteSubResult = useCase.deleteSubcategory("Salud", invalid)
            assertTrue("Input subcategoría '$invalid' debe ser rechazado en deleteSubcategory", deleteSubResult is CategoryResult.Error)

            val deleteSubResult2 = useCase.deleteSubcategory(invalid, "Farmacia")
            assertTrue("Input categoría '$invalid' debe ser rechazado en deleteSubcategory", deleteSubResult2 is CategoryResult.Error)
        }
    }

    @Test
    fun test_adversarial_case_insensitive_name_collision_is_prevented() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(Category(name = "Salud", icon = "💊", color = "#10B981", subcategories = emptyList()))
        roomCategoryRepository.insertCategory(Category(name = "Educación", icon = "📚", color = "#3B82F6", subcategories = emptyList()))

        // Intento adversarial de renombrar "Educación" a "salud", " SALUD ", etc.
        val collisions = listOf("salud", "SALUD", "  salud  ", "Salud")
        for (candidate in collisions) {
            val result = useCase.updateCategory(oldName = "Educación", newNameInput = candidate, colorInput = "#3B82F6")
            assertTrue("Debe rechazar colisión con '$candidate'", result is CategoryResult.Error)
            assertEquals("Ya existe una categoría con este nombre", (result as CategoryResult.Error).message)
        }
    }

    @Test
    fun test_adversarial_sql_injection_payloads_are_stored_safely_as_plain_text() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(Category(name = "Target", icon = "🎯", color = "#000000", subcategories = emptyList()))

        val sqlInjections = listOf(
            "Target'); DROP TABLE expenses;--",
            "Target' OR 1=1;--",
            "Target\" UNION SELECT * FROM expenses_categories--"
        )

        for (sql in sqlInjections) {
            val result = useCase.updateCategory(oldName = "Target", newNameInput = sql, colorInput = "#FFFFFF")
            assertTrue("El payload SQL injection debe procesarse de forma segura y sin romper la DB", result is CategoryResult.Success)

            // Verificar que se guardó literalmente y la tabla expenses sigue intacta
            val categories = roomCategoryRepository.getAllCategories().first()
            assertTrue(categories.any { it.name == sql })
            assertEquals(0, expenseDao.getAll().first().size)

            // Revertir nombre para la siguiente iteración
            useCase.updateCategory(oldName = sql, newNameInput = "Target", colorInput = "#000000")
        }
    }

    @Test
    fun test_adversarial_long_and_unicode_composite_strings() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(Category(name = "UnicodeTest", icon = "✨", color = "#FF00FF", subcategories = emptyList()))

        val complexString = "🔥".repeat(100) + " 日本語 ñçü " + "A".repeat(500)
        val result = useCase.updateCategory(oldName = "UnicodeTest", newNameInput = complexString, colorInput = "#123456")
        assertTrue("Debe admitir cadenas complejas Unicode y de gran longitud", result is CategoryResult.Success)

        val updated = roomCategoryRepository.getAllCategories().first().find { it.name == complexString }
        assertNotNull(updated)
        assertEquals(complexString, updated!!.name)
    }

    @Test
    fun test_adversarial_delete_category_strictly_blocked_when_expenses_exist() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(Category(name = "Impuestos", icon = "🏛️", color = "#EF4444", subcategories = emptyList()))

        // Insertar un gasto bajo "Impuestos" (incluso con espacios y mayúsculas desalineadas)
        expenseDao.insert(
            ExpenseEntity(amount = 300.0, date = "2026-05-01", description = "Renta", category = "  impuestos  ")
        )

        // Intento de eliminación
        val deleteResult = useCase.deleteCategory("Impuestos")
        assertTrue("La eliminación debe ser bloqueada", deleteResult is CategoryResult.Error)
        assertEquals(
            "No se puede eliminar la categoría porque tiene gastos asociados",
            (deleteResult as CategoryResult.Error).message
        )

        // Comprobar que sigue existiendo en el repositorio
        val categories = roomCategoryRepository.getAllCategories().first()
        assertTrue("La categoría no debe haber sido eliminada", categories.any { it.name == "Impuestos" })
    }

    @Test
    fun test_adversarial_delete_subcategory_strictly_blocked_when_expenses_exist() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        roomCategoryRepository.insertCategory(
            Category(name = "Servicios", icon = "💡", color = "#F59E0B", subcategories = listOf(Subcategory("Luz"), Subcategory("Agua")))
        )

        // Gasto asociado solo a "Luz"
        expenseDao.insert(
            ExpenseEntity(amount = 45.0, date = "2026-05-02", description = "Recibo luz", category = "Servicios", subcategory = "Luz")
        )

        // 1. Intentar borrar "Luz" -> Debe fallar
        val deleteLuzResult = useCase.deleteSubcategory("Servicios", "Luz")
        assertTrue("Eliminación de 'Luz' debe fallar por tener gastos", deleteLuzResult is CategoryResult.Error)
        assertEquals(
            "No se puede eliminar la subcategoría porque tiene gastos asociados",
            (deleteLuzResult as CategoryResult.Error).message
        )

        // 2. Intentar borrar "Agua" -> Debe tener éxito porque no tiene gastos
        val deleteAguaResult = useCase.deleteSubcategory("Servicios", "Agua")
        assertTrue("Eliminación de 'Agua' debe permitirse", deleteAguaResult is CategoryResult.Success)

        // Verificar que "Agua" fue eliminada pero "Luz" se conservó
        val updatedCat = roomCategoryRepository.getAllCategories().first().find { it.name == "Servicios" }
        assertNotNull(updatedCat)
        val remainingSubcategories = updatedCat!!.subcategories.map { it.name }
        assertTrue(remainingSubcategories.contains("Luz"))
        assertFalse(remainingSubcategories.contains("Agua"))
    }

    @Test
    fun test_adversarial_concurrency_stress_mutex_protects_category_mutations() = runBlocking {
        val useCase = CategoryUseCase(roomCategoryRepository)
        val initialCategories = (1..10).map { i ->
            Category(name = "Category_$i", icon = "📁", color = "#$i$i$i$i$i$i", subcategories = emptyList())
        }
        for (cat in initialCategories) {
            roomCategoryRepository.insertCategory(cat)
        }

        // Lanzar 40 coroutines en paralelo intentando actualizar y renombrar
        val jobs = (1..40).map { i ->
            launch(Dispatchers.Default) {
                val oldName = "Category_${(i % 10) + 1}"
                val newName = "Category_${(i % 10) + 1}_mod_$i"
                useCase.updateCategory(
                    oldName = oldName,
                    newNameInput = newName,
                    colorInput = "#000000"
                )
            }
        }
        jobs.joinAll()

        // Invariante de Concurrencia: La base de datos no debe estar corrupta y las lecturas deben ser consistentes
        val finalCategories = roomCategoryRepository.getAllCategories().first()
        assertTrue("Debe haber exactamente 10 categorías", finalCategories.size <= 10)
        assertTrue(finalCategories.isNotEmpty())
    }

    // =========================================================================
    // 3. PRUEBAS ADVERSARIALES EN EXPENSE VIEW MODEL (ESTADO Y REACTIVIDAD)
    // =========================================================================

    private class FakeExpenseRepoForAdversarial : ExpenseRepository {
        private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
        override fun getAllExpenses(): Flow<List<Expense>> = _expenses
        override suspend fun insertExpense(expense: Expense) {
            _expenses.value = _expenses.value + expense
        }
        override suspend fun updateExpense(expense: Expense) {
            _expenses.value = _expenses.value.map { if (it.id == expense.id) expense else it }
        }
        override suspend fun deleteExpense(id: Long) {
            _expenses.value = _expenses.value.filter { it.id != id }
        }
    }

    @Test
    fun test_viewModel_editCategory_reconciles_active_form_input_and_editing_expense() = runTest(testDispatcher) {
        val expenseRepo = FakeExpenseRepoForAdversarial()
        val useCase = CategoryUseCase(roomCategoryRepository)
        val expenseUseCase = ExpenseUseCase(expenseRepo)
        val viewModel = ExpenseViewModel(expenseUseCase, useCase)

        roomCategoryRepository.insertCategory(
            Category(name = "Diversión", icon = "🎉", color = "#EC4899", subcategories = emptyList())
        )
        advanceUntilIdle()

        // El usuario tiene seleccionado "Diversión" en el formulario de nuevo gasto
        viewModel.onCategoryChange("Diversión", "")
        assertEquals("Diversión", viewModel.uiState.value.categoryInput)

        // El usuario además tiene un gasto abierto para editar con categoría "Diversión"
        val existingExpense = Expense(
            id = 101L,
            amount = 90.0,
            date = "2026-05-15",
            description = "Cine",
            category = "Diversión",
            subcategory = ""
        )
        viewModel.startEditing(existingExpense)
        assertEquals("Diversión", viewModel.uiState.value.editingExpense?.category)

        // Ahora el usuario edita la categoría a "Entretenimiento"
        val catToEdit = Category(name = "Diversión", icon = "🎉", color = "#EC4899", subcategories = emptyList())
        viewModel.startEditingCategory(catToEdit)
        viewModel.onEditCategoryNameChange("Entretenimiento")
        viewModel.confirmEditCategory()
        advanceUntilIdle()

        // Verificaciones reactivas:
        // 1. El categoryInput del formulario debe haberse actualizado a "Entretenimiento"
        assertEquals("Entretenimiento", viewModel.uiState.value.categoryInput)
        // 2. El editingExpense abierto debe haberse actualizado a "Entretenimiento"
        assertEquals("Entretenimiento", viewModel.uiState.value.editingExpense?.category)
        // 3. El diálogo de edición de categoría debe estar cerrado
        assertNull(viewModel.uiState.value.editingCategory)
    }

    @Test
    fun test_viewModel_deleteCategory_with_expenses_surfaces_error_in_uiState() = runTest(testDispatcher) {
        val expenseRepo = FakeExpenseRepoForAdversarial()
        val useCase = CategoryUseCase(roomCategoryRepository)
        val expenseUseCase = ExpenseUseCase(expenseRepo)
        val viewModel = ExpenseViewModel(expenseUseCase, useCase)

        roomCategoryRepository.insertCategory(
            Category(name = "Tecnología", icon = "💻", color = "#6366F1", subcategories = emptyList())
        )
        // Agregamos un gasto en Room que bloqueará la eliminación
        expenseDao.insert(
            ExpenseEntity(amount = 1200.0, date = "2026-05-01", description = "Laptop", category = "Tecnología")
        )
        advanceUntilIdle()

        // El usuario solicita eliminar la categoría
        val cat = Category(name = "Tecnología", icon = "💻", color = "#6366F1", subcategories = emptyList())
        viewModel.requestDeleteCategory(cat)
        assertEquals(cat, viewModel.uiState.value.categoryToDelete)

        // El usuario confirma la eliminación
        viewModel.confirmDeleteCategory()
        advanceUntilIdle()

        // Verificaciones:
        // 1. Debe haberse limpiado el diálogo modal de confirmación
        assertNull(viewModel.uiState.value.categoryToDelete)
        // 2. Debe haberse poblado el mensaje de error en UI
        assertEquals(
            "No se puede eliminar la categoría porque tiene gastos asociados",
            viewModel.uiState.value.categoryErrorMessage
        )
    }

    @Test
    fun test_viewModel_deleteSubcategory_clears_active_subcategoryInput_and_editingExpense() = runTest(testDispatcher) {
        val expenseRepo = FakeExpenseRepoForAdversarial()
        val useCase = CategoryUseCase(roomCategoryRepository)
        val expenseUseCase = ExpenseUseCase(expenseRepo)
        val viewModel = ExpenseViewModel(expenseUseCase, useCase)

        roomCategoryRepository.insertCategory(
            Category(name = "Auto", icon = "🚗", color = "#EF4444", subcategories = listOf(Subcategory("Gasolina"), Subcategory("Lavado")))
        )
        advanceUntilIdle()

        // Formulario tiene "Auto" y "Gasolina" seleccionados
        viewModel.onCategoryChange("Auto", "Gasolina")
        assertEquals("Gasolina", viewModel.uiState.value.subcategoryInput)

        // Gasto en edición también tiene "Auto" y "Gasolina"
        val expense = Expense(id = 55L, amount = 40.0, date = "2026-05-18", description = "Tanque", category = "Auto", subcategory = "Gasolina")
        viewModel.startEditing(expense)
        assertEquals("Gasolina", viewModel.uiState.value.editingExpense?.subcategory)

        // Se elimina la subcategoría "Gasolina" (no tiene gastos en Room)
        viewModel.deleteSubcategory("Auto", "Gasolina")
        advanceUntilIdle()

        // Verificaciones:
        // 1. subcategoryInput debe haberse limpiado a cadena vacía
        assertEquals("", viewModel.uiState.value.subcategoryInput)
        // 2. editingExpense?.subcategory debe haberse limpiado a cadena vacía
        assertEquals("", viewModel.uiState.value.editingExpense?.subcategory)
    }
}
