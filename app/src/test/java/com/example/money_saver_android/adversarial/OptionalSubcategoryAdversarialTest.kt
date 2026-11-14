package com.example.money_saver_android.adversarial

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import com.example.money_saver_android.FakeExpenseRepository
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.local.BudgetDatabase
import com.example.money_saver_android.data.local.IncomesDatabase
import com.example.money_saver_android.data.repository.RoomBudgetRepository
import com.example.money_saver_android.data.repository.RoomExpenseRepository
import com.example.money_saver_android.data.repository.RoomIncomeRepository
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.ExpenseResult
import com.example.money_saver_android.domain.usecase.ExpenseUseCase
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.ui.CategorySelectionSheet
import com.example.money_saver_android.ui.components.expenses.ExpenseCardItem
import com.example.money_saver_android.ui.components.incomes.DeleteIncomeConfirmationDialog
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.viewmodel.IncomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Suite de Pruebas Adversariales y Property-Based Testing para:
 * Subcategoría Opcional en Gastos, Ingresos y Presupuestos.
 *
 * Objetivo: Desafiar los límites de la lógica implementada en los cambios recientes
 * detectados vía Git (modelos, casos de uso, repositorios Room v2/v3/v4, ViewModels y Compose UI)
 * sin modificar el código productivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OptionalSubcategoryAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var incomeRepository: FakeAdversarialIncomeRepository
    private lateinit var budgetRepository: FakeAdversarialBudgetRepository
    private lateinit var expenseUseCase: ExpenseUseCase
    private lateinit var budgetUseCase: BudgetUseCase
    private lateinit var incomeUseCase: IncomeUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        expenseRepository = FakeExpenseRepository()
        incomeRepository = FakeAdversarialIncomeRepository()
        budgetRepository = FakeAdversarialBudgetRepository()
        expenseUseCase = ExpenseUseCase(expenseRepository)
        budgetUseCase = BudgetUseCase(budgetRepository, incomeRepository)
        incomeUseCase = IncomeUseCase(
            repository = incomeRepository,
            budgetRepository = budgetRepository,
            budgetUseCase = budgetUseCase,
            expenseRepository = expenseRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. VULNERABILIDAD NUMÉRICA: NaN E INFINITY EN GASTOS E INGRESOS SIN SUBCATEGORÍA
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpenseUseCase.kt E IncomeUseCase.kt:
     * A diferencia de BudgetUseCase (que verifica `amount.isNaN() || amount.isInfinite()`),
     * tanto `ExpenseUseCase.saveExpense`/`updateExpense` como `IncomeUseCase.saveIncome`/`updateIncome`
     * solo validan `if (amount <= 0.0)`.
     * En IEEE 754:
     *   - `"NaN".toDoubleOrNull()` -> `Double.NaN`, y `Double.NaN <= 0.0` es `false`.
     *   - `"Infinity".toDoubleOrNull()` -> `Double.POSITIVE_INFINITY`, y `Infinity <= 0.0` es `false`.
     *   - `"1e309".toDoubleOrNull()` -> `Double.POSITIVE_INFINITY`.
     * Como resultado, se insertan ingresos y gastos corruptos con `NaN` o `Infinity`,
     * envenenando `totalIncome`, `availableIncome` y el cálculo de presupuestos por categoría.
     */
    @Test
    fun test_adversarial_income_and_expense_nan_and_infinity_amount_bypass() = runBlocking {
        val nonFiniteInputs = listOf("NaN", "Infinity", "+Infinity", "1e309")

        for (input in nonFiniteInputs) {
            val expenseSaveResult = expenseUseCase.saveExpense(
                amountInput = input,
                dateInput = "2026-11-14",
                descriptionInput = "Gasto sin subcategoría",
                categoryInput = "Alimentación",
                subcategoryInput = ""
            )
            assertTrue(
                "VULNERABILIDAD EXPUESTA: ExpenseUseCase.saveExpense aceptó el monto no finito '$input'",
                expenseSaveResult is ExpenseResult.Error
            )

            val incomeSaveResult = incomeUseCase.saveIncome(
                amountInput = input,
                dateTimeInput = "2026-11-14 10:00",
                categoryInput = "Employment",
                subcategoryInput = "",
                descriptionInput = "Ingreso sin subcategoría",
                periodId = 1L
            )
            assertTrue(
                "VULNERABILIDAD EXPUESTA: IncomeUseCase.saveIncome aceptó el monto no finito '$input'",
                incomeSaveResult is IncomeResult.Error
            )
        }

        assertTrue(
            "Ningún gasto con monto NaN/Infinity debe persistirse",
            expenseUseCase.getExpenses().first().isEmpty()
        )
        assertTrue(
            "Ningún ingreso con monto NaN/Infinity debe persistirse",
            incomeUseCase.getIncomesByPeriod(1L).first().isEmpty()
        )
    }

    // =========================================================================
    // 2. VULNERABILIDAD UI: TEXTO DUPLICADO EN ExpenseCardItem
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpenseCardItem.kt (líneas 90-106):
     * ```kotlin
     * Text(text = expense.subcategory.ifBlank { expense.category.ifBlank { "Gasto" } })
     * val subtitle = when {
     *     expense.description.isNotBlank() -> expense.description
     *     expense.subcategory.isNotBlank() -> expense.category
     *     else -> ""
     * }
     * ```
     * Caso A: Si `expense.subcategory == expense.category` (ej. "Alimentación") y `description` está vacío,
     *         el título muestra "Alimentación" y el subtítulo también muestra "Alimentación"
     *         (a diferencia de DeleteIncomeConfirmationDialog e IncomeListItem que verifican `subcategory != category`).
     * Caso B: Si el gasto se guarda sin subcategoría (`subcategory = ""`) pero el usuario escribe como
     *         descripción el mismo nombre de la categoría (`description = "Alimentación"`), el título
     *         usa el fallback `expense.category` ("Alimentación") y el subtítulo usa `expense.description`
     *         ("Alimentación"), duplicando nuevamente el texto en la tarjeta.
     */
    @Test
    fun test_adversarial_expense_card_duplicate_category_text_when_subcategory_or_description_matches_category() {
        val expenseSameSubAndCat = Expense(
            id = 10L,
            amount = 45.0,
            date = "2026-11-14",
            description = "",
            category = "Alimentación",
            subcategory = "Alimentación"
        )

        composeTestRule.setContent {
            ExpenseCardItem(expense = expenseSameSubAndCat)
        }

        // INVARIANTE UI: El nombre de la categoría no debe renderizarse duplicado en título y subtítulo
        composeTestRule.onAllNodesWithText("Alimentación").assertCountEquals(1)
    }

    @Test
    fun test_adversarial_expense_card_without_subcategory_duplicates_text_when_description_equals_category() {
        val expenseDescSameAsCat = Expense(
            id = 11L,
            amount = 80.0,
            date = "2026-11-14",
            description = "Transporte",
            category = "Transporte",
            subcategory = ""
        )

        composeTestRule.setContent {
            ExpenseCardItem(expense = expenseDescSameAsCat)
        }

        // INVARIANTE UI: Cuando no hay subcategoría y la descripción es idéntica a la categoría,
        // no debe duplicarse la misma línea de texto en la tarjeta.
        composeTestRule.onAllNodesWithText("Transporte").assertCountEquals(1)
    }

    // =========================================================================
    // 3. VULNERABILIDAD UI: DeleteIncomeConfirmationDialog CON DIFERENCIA DE CASE/ESPACIOS
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN DeleteIncomeConfirmationDialog.kt (línea 114):
     * `if (income.subcategory.isNotBlank() && income.subcategory != income.category)`
     * Utiliza `!=` literal sin `.trim()` ni `equals(..., ignoreCase = true)`.
     * Si `category = "Freelance"` y `subcategory = "Freelance "` (con espacio final),
     * la comparación `!=` evalúa a `true` y renderiza el texto dos veces.
     */
    @Test
    fun test_adversarial_delete_income_dialog_duplicate_text_on_whitespace_padded_subcategory() {
        val income = Income(
            id = 99L,
            amount = 500.0,
            dateTime = "2026-11-14 12:00",
            category = "Freelance",
            subcategory = "Freelance ",
            description = "",
            periodId = 1L
        )

        composeTestRule.setContent {
            DeleteIncomeConfirmationDialog(
                income = income,
                onConfirm = {},
                onDismiss = {}
            )
        }

        // Si se normalizan espacios, no debería mostrarse una línea secundaria redundante "Freelance"
        // además del encabezado "Freelance "
        composeTestRule.onNodeWithText("Freelance").assertDoesNotExist()
    }

    // =========================================================================
    // 4. VULNERABILIDAD DE INTEGRIDAD REFERENCIAL: CATEGORÍAS CON ESPACIOS Y SUBCATEGORÍA VACÍA
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomeUseCase.saveIncome Y BudgetUseCase.saveBudget:
     * Al guardar un ingreso o presupuesto sin subcategoría (`subcategoryInput = ""`),
     * `categoryInput` NO se normaliza con `.trim()`.
     * Si se guarda con `categoryInput = "  Employment  "`, la entidad persiste con espacios.
     * Posteriormente, `deleteCategory("Employment")` aplica `.trim()` y consulta
     * `countIncomesForCategory("Employment")`, obteniendo 0 coincidencias exactas en SQLite/Repositorio,
     * permitiendo eliminar una categoría que SÍ tiene ingresos/presupuestos activos asociados.
     */
    @Test
    fun test_adversarial_untrimmed_category_with_empty_subcategory_bypasses_referential_integrity_on_delete() = runBlocking {
        incomeRepository.insertCategory(
            Category(name = "Employment", icon = "💼", color = "#4E73DF", subcategories = emptyList())
        )

        // Guardar ingreso sin subcategoría pero con espacios accidentales en categoryInput
        val saveRes = incomeUseCase.saveIncome(
            amountInput = "1200",
            dateTimeInput = "2026-11-14 09:00",
            categoryInput = "  Employment  ",
            subcategoryInput = "",
            descriptionInput = "Pago mensual",
            periodId = 1L
        )
        assertTrue(saveRes is IncomeResult.Success)

        // Intentar borrar la categoría "Employment"
        val deleteRes = incomeUseCase.deleteCategory("Employment")

        // INVARIANTE DE INTEGRIDAD REFERENCIAL: No debe permitir eliminar la categoría porque tiene un ingreso activo
        assertTrue(
            "VULNERABILIDAD EXPUESTA: Se permitió eliminar la categoría 'Employment' pese a tener un ingreso asociado con espacios",
            deleteRes is IncomeResult.Error
        )
    }

    // =========================================================================
    // 5. INVARIANTE DE NORMALIZACIÓN: SUBCATEGORÍA CON SOLO ESPACIOS EN BLANCO
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpenseUseCase.kt E IncomeUseCase.kt:
     * Los repositorios Room convierten `subcategory.takeIf { it.isNotBlank() }` a `null` y al leer
     * devuelven `""`. Sin embargo, `ExpenseUseCase` e `IncomeUseCase` pasan `subcategoryInput` sin
     * normalizar (`subcategoryInput.trim()`) al modelo de dominio `Expense` / `Income`.
     * Si se invoca con `subcategoryInput = "   \t  "`, el modelo en memoria queda con `"   \t  "`
     * en lugar de `""`.
     */
    @Test
    fun test_adversarial_whitespace_only_subcategory_inconsistency_in_usecases() = runBlocking {
        val whitespaceSub = "   \t\n   "

        val expResult = expenseUseCase.saveExpense(
            amountInput = "50.0",
            dateInput = "2026-11-14",
            descriptionInput = "Café",
            categoryInput = "Alimentación",
            subcategoryInput = whitespaceSub
        )
        assertTrue(expResult is ExpenseResult.Success)

        val incResult = incomeUseCase.saveIncome(
            amountInput = "500.0",
            dateTimeInput = "2026-11-14 10:00",
            categoryInput = "Employment",
            subcategoryInput = whitespaceSub,
            descriptionInput = "Bono",
            periodId = 1L
        )
        assertTrue(incResult is IncomeResult.Success)

        val savedExpense = expenseUseCase.getExpenses().first().first()
        val savedIncome = incomeUseCase.getIncomesByPeriod(1L).first().first()

        // INVARIANTE DE DOMINIO: Una subcategoría en blanco siempre debe normalizarse a ""
        assertEquals(
            "VULNERABILIDAD EXPUESTA: ExpenseUseCase no normalizó una subcategoría compuesta solo por espacios",
            "",
            savedExpense.subcategory
        )
        assertEquals(
            "VULNERABILIDAD EXPUESTA: IncomeUseCase no normalizó una subcategoría compuesta solo por espacios",
            "",
            savedIncome.subcategory
        )
    }

    // =========================================================================
    // 6. PERSISTENCIA ROOM: ROUNDTRIP DE SUBCATEGORÍA NULA/VACÍA EN LAS 3 BASES DE DATOS
    // =========================================================================
    /**
     * Verifica en SQLite real (Room in-memory para AppDatabase v4, IncomesDatabase v2 y BudgetDatabase v3):
     * 1. Insertar con `subcategory = ""` o `"   "` almacena `NULL` en la entidad y recupera `""` en el dominio.
     * 2. Actualizar un registro que tenía subcategoría ("Salary") a sin subcategoría (`""`) limpia la columna a `NULL` y lee `""`.
     * 3. Actualizar un registro sin subcategoría (`""`) a una subcategoría válida ("Bonus") persiste el nuevo valor.
     */
    @Test
    fun test_adversarial_room_null_and_blank_subcategory_roundtrip_across_all_three_databases() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()

        val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val incomesDb = Room.inMemoryDatabaseBuilder(context, IncomesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val budgetDb = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val roomExpenseRepo = RoomExpenseRepository(appDb.expenseDao())
            val roomIncomeRepo = RoomIncomeRepository(incomesDb.incomeDao(), incomesDb.incomeCategoryDao())
            val roomBudgetRepo = RoomBudgetRepository(budgetDb.budgetDao(), budgetDb.budgetCategoryDao())

            // 1. Gastos (AppDatabase v4)
            roomExpenseRepo.insertExpense(
                Expense(amount = 30.0, date = "2026-11-14", description = "Sin sub", category = "Comida", subcategory = "   ")
            )
            val rawExpenseEntity = appDb.expenseDao().getAll().first().first()
            assertNull("En SQLite (expenses), una subcategoría en blanco debe persistirse como NULL", rawExpenseEntity.subcategory)

            var domainExpense = roomExpenseRepo.getAllExpenses().first().first()
            assertEquals("", domainExpense.subcategory)

            // Actualizar gasto de "" -> "Restaurante" -> ""
            roomExpenseRepo.updateExpense(domainExpense.copy(subcategory = "Restaurante"))
            assertEquals("Restaurante", roomExpenseRepo.getAllExpenses().first().first().subcategory)

            roomExpenseRepo.updateExpense(domainExpense.copy(subcategory = ""))
            assertNull(appDb.expenseDao().getAll().first().first().subcategory)
            assertEquals("", roomExpenseRepo.getAllExpenses().first().first().subcategory)

            // 2. Ingresos (IncomesDatabase v2)
            val incId = roomIncomeRepo.insertIncome(
                Income(amount = 1000.0, dateTime = "2026-11-14 08:00", category = "Employment", subcategory = "", periodId = 1L)
            )
            val rawIncomeEntity = incomesDb.incomeDao().getIncomesByPeriod(1L).first().first()
            assertNull("En SQLite (incomes), una subcategoría vacía debe persistirse como NULL", rawIncomeEntity.subcategory)

            val domainIncome = roomIncomeRepo.getIncomesByPeriod(1L).first().first()
            assertEquals(incId, domainIncome.id)
            assertEquals("", domainIncome.subcategory)

            roomIncomeRepo.updateIncome(domainIncome.copy(subcategory = "Bonus"))
            assertEquals("Bonus", roomIncomeRepo.getIncomesByPeriod(1L).first().first().subcategory)

            roomIncomeRepo.updateIncome(domainIncome.copy(subcategory = "   "))
            assertNull(incomesDb.incomeDao().getIncomesByPeriod(1L).first().first().subcategory)
            assertEquals("", roomIncomeRepo.getIncomesByPeriod(1L).first().first().subcategory)

            // 3. Presupuestos (BudgetDatabase v3)
            val budId = roomBudgetRepo.insertBudget(
                Budget(amount = 400.0, category = "Hogar", subcategory = "", periodId = 1L)
            )
            assertNull("En SQLite (budgets), una subcategoría vacía debe persistirse como NULL", budgetDb.budgetDao().getBudgetById(budId)?.subcategory)
            assertEquals("", roomBudgetRepo.getBudgetById(budId)?.subcategory)

            val domainBudget = roomBudgetRepo.getBudgetsByPeriod(1L).first().first()
            roomBudgetRepo.updateBudget(domainBudget.copy(subcategory = "Arriendo"))
            assertEquals("Arriendo", roomBudgetRepo.getBudgetById(budId)?.subcategory)

            roomBudgetRepo.updateBudget(domainBudget.copy(subcategory = ""))
            assertNull(budgetDb.budgetDao().getBudgetById(budId)?.subcategory)
            assertEquals("", roomBudgetRepo.getBudgetById(budId)?.subcategory)
        } finally {
            appDb.close()
            incomesDb.close()
            budgetDb.close()
        }
    }

    // =========================================================================
    // 7. SEMÁNTICA SQL NULL EN CONTEO DE CATEGORÍAS Y SUBCATEGORÍAS EN ROOM
    // =========================================================================
    /**
     * Verifica que cuando un ingreso o presupuesto se guarda con `subcategory = NULL` en SQLite:
     * - `countIncomesForCategory` y `countBudgetsForCategory` lo contabilizan (> 0), bloqueando el borrado de la categoría.
     * - `countIncomesForSubcategory` y `countBudgetsForSubcategory` retornan 0 tanto para `""` como para subcategorías existentes,
     *   permitiendo borrar subcategorías no utilizadas sin que `NULL` genere falsos positivos.
     */
    @Test
    fun test_adversarial_room_count_queries_with_null_subcategory_records() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val incomesDb = Room.inMemoryDatabaseBuilder(context, IncomesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val budgetDb = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val roomIncomeRepo = RoomIncomeRepository(incomesDb.incomeDao(), incomesDb.incomeCategoryDao())
            val roomBudgetRepo = RoomBudgetRepository(budgetDb.budgetDao(), budgetDb.budgetCategoryDao())

            roomIncomeRepo.insertIncome(
                Income(amount = 800.0, dateTime = "2026-11-14 10:00", category = "Employment", subcategory = "", periodId = 1L)
            )
            roomBudgetRepo.insertBudget(
                Budget(amount = 300.0, category = "Employment", subcategory = "", periodId = 1L)
            )

            assertEquals(1, roomIncomeRepo.countIncomesForCategory("Employment"))
            assertEquals(0, roomIncomeRepo.countIncomesForSubcategory("Employment", ""))
            assertEquals(0, roomIncomeRepo.countIncomesForSubcategory("Employment", "Salary"))

            assertEquals(1, roomBudgetRepo.countBudgetsForCategory("Employment"))
            assertEquals(0, roomBudgetRepo.countBudgetsForSubcategory("Employment", ""))
            assertEquals(0, roomBudgetRepo.countBudgetsForSubcategory("Employment", "Salary"))
        } finally {
            incomesDb.close()
            budgetDb.close()
        }
    }

    // =========================================================================
    // 8. VULNERABILIDAD EN ROOM: CASCADE DELETE EN IncomesDatabase POR REPLACE
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomesDatabase.kt (línea 91) E IncomeRepository.kt (línea 67):
     * En `IncomeCategoryDao`:
     *   `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategory(category: IncomeCategoryEntity)`
     * Mientras que `IncomeSubcategoryEntity` tiene `ForeignKey(onDelete = ForeignKey.CASCADE)`.
     * A diferencia de `BudgetCategoryDao` (que usa `OnConflictStrategy.IGNORE` + `updateCategory`),
     * reinsertar una categoría existente en `RoomIncomeRepository.insertCategory` ejecuta un `DELETE`
     * previo en SQLite que destruye en cascada todas sus subcategorías.
     */
    @Test
    fun test_adversarial_room_income_category_replace_destroys_subcategories_via_cascade() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val incomesDb = Room.inMemoryDatabaseBuilder(context, IncomesDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val roomIncomeRepo = RoomIncomeRepository(incomesDb.incomeDao(), incomesDb.incomeCategoryDao())

            roomIncomeRepo.insertCategory(
                Category(
                    name = "Employment",
                    icon = "💼",
                    color = "#4E73DF",
                    subcategories = listOf(Subcategory("Salary"), Subcategory("Bonus"))
                )
            )
            assertEquals(2, roomIncomeRepo.getAllCategories().first().first().subcategories.size)

            // Reinsertar la categoría padre (ej. actualización de color sin reenviar subcategorías)
            roomIncomeRepo.insertCategory(
                Category(
                    name = "Employment",
                    icon = "💼",
                    color = "#1CC88A",
                    subcategories = emptyList()
                )
            )

            val updatedCategory = roomIncomeRepo.getAllCategories().first().first { it.name == "Employment" }
            assertEquals(
                "VULNERABILIDAD EXPUESTA: OnConflictStrategy.REPLACE en IncomeCategoryDao borró en cascada las subcategorías existentes",
                2,
                updatedCategory.subcategories.size
            )
        } finally {
            incomesDb.close()
        }
    }

    // =========================================================================
    // 9. PROPERTY-BASED TESTING: CONSOLIDACIÓN DE PRESUPUESTOS Y GASTOS SIN SUBCATEGORÍA
    // =========================================================================
    /**
     * INVARIANTE DE PROPIEDAD (500 iteraciones aleatorias):
     * Para cualquier combinación de presupuestos CON y SIN subcategoría (`subcategory = ""`)
     * y gastos CON y SIN subcategoría dentro de un período activo:
     * 1. `budgetAmount` consolidado por categoría == suma exacta de todos los presupuestos de esa categoría.
     * 2. `spentAmount` consolidado == suma exacta de todos los gastos del período para esa categoría (con o sin subcategoría).
     * 3. `remainingAmount` == `maxOf(0.0, budgetAmount - spentAmount)` >= 0.0 siempre.
     * 4. `progress` pertenece a `[0.0f, 1.0f]` y `percent` pertenece a `0..100`.
     */
    @Test
    fun test_property_based_invariant_category_budgets_consolidation_with_and_without_subcategories() {
        val random = Random(20261114)
        val categories = listOf(
            Category(name = "Alimentación", icon = "🍔", color = "#1CC88A", subcategories = listOf(Subcategory("Mercado"))),
            Category(name = "Transporte", icon = "🚗", color = "#4E73DF", subcategories = emptyList())
        )

        for (iteration in 1..500) {
            val budgets = mutableListOf<Budget>()
            val expenses = mutableListOf<Expense>()

            var expectedFoodBudget = 0.0
            var expectedFoodSpentInPeriod = 0.0

            val budgetCount = random.nextInt(1, 8)
            for (b in 1..budgetCount) {
                val amt = random.nextDouble(10.0, 500.0)
                val withoutSub = random.nextBoolean()
                val catName = if (random.nextBoolean()) "Alimentación" else "  alimentación  "
                budgets.add(
                    Budget(
                        id = b.toLong(),
                        amount = amt,
                        category = catName,
                        subcategory = if (withoutSub) "" else "Mercado",
                        periodId = 1L
                    )
                )
                expectedFoodBudget += amt
            }

            val expenseCount = random.nextInt(0, 10)
            for (e in 1..expenseCount) {
                val amt = random.nextDouble(5.0, 300.0)
                val withoutSub = random.nextBoolean()
                val inPeriod = random.nextBoolean()
                val date = if (inPeriod) "2026-05-15" else "2026-06-15"
                expenses.add(
                    Expense(
                        id = e.toLong(),
                        amount = amt,
                        date = date,
                        description = if (withoutSub) "" else "Compra",
                        category = "Alimentación",
                        subcategory = if (withoutSub) "" else "Mercado"
                    )
                )
                if (inPeriod) {
                    expectedFoodSpentInPeriod += amt
                }
            }

            val items = incomeUseCase.calculateCategoryBudgets(
                budgets = budgets,
                categories = categories,
                expenses = expenses,
                periodStartDate = "2026-05-01",
                periodEndDate = "2026-05-31"
            )

            assertEquals("Iteración $iteration: debe consolidar en 1 sola categoría", 1, items.size)
            val item = items.first()
            assertEquals("Alimentación", item.categoryName)
            assertEquals(expectedFoodBudget, item.budgetAmount, 0.0001)
            assertEquals(expectedFoodSpentInPeriod, item.spentAmount, 0.0001)
            assertEquals(maxOf(0.0, expectedFoodBudget - expectedFoodSpentInPeriod), item.remainingAmount, 0.0001)
            assertTrue("Iteración $iteration: remainingAmount >= 0", item.remainingAmount >= 0.0)
            assertTrue("Iteración $iteration: progress en [0, 1]", item.progress in 0.0f..1.0f)
            assertTrue("Iteración $iteration: percent en 0..100", item.percent in 0..100)
        }
    }

    // =========================================================================
    // 10. EDGE CASES EN CategorySelectionSheet: CATEGORÍA SIN SUBCATEGORÍAS Y NAVEGACIÓN
    // =========================================================================
    /**
     * Verifica que:
     * 1. Una categoría con lista de subcategorías vacía (`emptyList()`) muestra igualmente el botón
     *    `"Continuar sin subcategoría"` (`btn_select_category_without_subcategory`).
     * 2. Si el usuario entra a Categoría A, vuelve atrás y entra a Categoría B (con color hex malformado
     *    y caracteres especiales), al pulsar `"Continuar sin subcategoría"` se emite Categoría B con `""`
     *    sin contaminar la selección ni lanzar excepción por el color inválido.
     */
    @Test
    fun test_adversarial_category_selection_sheet_empty_subcategories_and_back_navigation() {
        var selectedPair: Pair<String, String>? = null
        val categories = listOf(
            Category(name = "Categoría Vacía", icon = "📦", color = "#4E73DF", subcategories = emptyList()),
            Category(name = " Otros & Más 🚀 ", icon = "✨", color = "INVALID_HEX_COLOR", subcategories = emptyList())
        )

        composeTestRule.setContent {
            CategorySelectionSheet(
                categories = categories,
                newCategoryName = "",
                selectedColorHex = "BAD_COLOR",
                categoryError = null,
                newSubcategoryName = "",
                subcategoryError = null,
                onCategoryNameChange = {},
                onColorSelect = {},
                onCreateCategory = {},
                onSubcategoryNameChange = {},
                onCreateSubcategory = {},
                onDismiss = {},
                onCategorySelected = { cat, sub -> selectedPair = cat to sub }
            )
        }

        // 1. Entrar a "Categoría Vacía" (0 subcategorías)
        composeTestRule.onNodeWithText("Categoría Vacía").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_select_category_without_subcategory").assertIsDisplayed()
        composeTestRule.onNodeWithText("Usar solo Categoría Vacía").assertIsDisplayed()

        // 2. Volver a la lista principal de categorías
        composeTestRule.onNodeWithContentDescription("Volver").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // 3. Entrar a la segunda categoría con color malformado y confirmar sin subcategoría
        composeTestRule.onNodeWithText(" Otros & Más 🚀 ").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_select_category_without_subcategory")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(" Otros & Más 🚀 " to "", selectedPair)
    }

    // =========================================================================
    // 11. VULNERABILIDAD EN ExpenseViewModel: ERROR DE VALIDACIÓN NO SE LIMPIA AL SELECCIONAR CATEGORÍA
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN ExpenseViewModel.kt (líneas 70-84):
     * A diferencia de `IncomeViewModel.onCategorySelected` (que limpia `errorMessage = null`),
     * `ExpenseViewModel.onCategoryChange(category, subcategory)` y `onAmountChange(amount)`
     * NO limpian `errorMessage`.
     * Si el usuario intenta guardar sin categoría (`errorMessage = "Debes seleccionar una categoría"`)
     * y luego selecciona una categoría usando `"Continuar sin subcategoría"`, el mensaje de error
     * permanece visible en la UI diciendo que debe seleccionar una categoría.
     */
    @Test
    fun test_adversarial_expense_viewmodel_stale_error_message_not_cleared_on_category_selection() = runTest(testDispatcher) {
        val viewModel = ExpenseViewModel(expenseUseCase)

        viewModel.onAmountChange("120.0")
        viewModel.onDateChange("2026-11-14")
        viewModel.onCategoryChange("", "")

        // 1. Intentar guardar sin categoría -> dispara error de validación
        viewModel.saveExpense()
        advanceUntilIdle()
        assertEquals("Debes seleccionar una categoría", viewModel.uiState.value.errorMessage)

        // 2. Usuario corrige seleccionando categoría sin subcategoría
        viewModel.onCategoryChange("Alimentación", "")

        // INVARIANTE UX/ESTADO: Al seleccionar una categoría válida, el error "Debes seleccionar una categoría" debe limpiarse
        assertNull(
            "VULNERABILIDAD EXPUESTA: ExpenseViewModel mantiene el error 'Debes seleccionar una categoría' después de haber seleccionado una categoría",
            viewModel.uiState.value.errorMessage
        )
    }

    // =========================================================================
    // 12. VULNERABILIDAD DE CONCURRENCIA/FLOW EN IncomeViewModel: FUGA DE COLLECTORS AL CAMBIAR DE PERÍODO
    // =========================================================================
    /**
     * VULNERABILIDAD DETECTADA EN IncomeViewModel.kt (líneas 134-162):
     * En `onPeriodSelected(periodId: Long?)`:
     * Cada vez que se invoca `onPeriodSelected(periodId)` con un ID no nulo, se lanzan dos nuevas
     * corrutinas `viewModelScope.launch { incomeUseCase.getIncomesByPeriod(periodId).collect { ... } }`
     * sin cancelar los `Job`s de las suscripciones anteriores.
     *
     * Impacto:
     * Si el usuario selecciona el Período 1, luego deselecciona el período (`onPeriodSelected(null)`)
     * o cambia al Período 2 (`onPeriodSelected(2L)`), el collector del Período 1 sigue vivo.
     * Cualquier inserción o actualización en el Período 1 (ej. guardar un ingreso sin subcategoría)
     * hace que el collector huérfano del Período 1 sobrescriba `uiState.incomes` y `uiState.totalIncome`
     * con los datos del Período 1 aunque el período activo sea `null` o `2L`.
     */
    @Test
    fun test_adversarial_income_viewmodel_period_switching_collector_leak_overwrites_active_period_state() = runTest(testDispatcher) {
        val viewModel = IncomeViewModel(incomeUseCase)

        // 1. Seleccionar Período 1 y luego cambiar a Período 2
        viewModel.onPeriodSelected(1L)
        advanceUntilIdle()

        viewModel.onPeriodSelected(2L)
        advanceUntilIdle()
        assertEquals(2L, viewModel.uiState.value.selectedPeriodId)
        assertTrue(viewModel.uiState.value.incomes.isEmpty())

        // 2. Se inserta un ingreso sin subcategoría en el Período 1 (mientras el usuario está viendo el Período 2)
        incomeRepository.insertIncome(
            Income(
                amount = 999.0,
                dateTime = "2026-11-14 10:00",
                category = "Employment",
                subcategory = "",
                periodId = 1L
            )
        )
        advanceUntilIdle()

        // INVARIANTE DE ESTADO: Como el período seleccionado es el 2L, la UI NO debe mostrar ingresos ni totales del Período 1L
        assertEquals(2L, viewModel.uiState.value.selectedPeriodId)
        assertTrue(
            "VULNERABILIDAD EXPUESTA: El collector huérfano del Período 1 sobrescribió la lista de ingresos mientras el Período 2 estaba activo",
            viewModel.uiState.value.incomes.none { it.periodId == 1L }
        )
        assertEquals(
            "VULNERABILIDAD EXPUESTA: El collector huérfano del Período 1 contaminó totalIncome del Período 2",
            0.0,
            viewModel.uiState.value.totalIncome,
            0.001
        )
    }
}
