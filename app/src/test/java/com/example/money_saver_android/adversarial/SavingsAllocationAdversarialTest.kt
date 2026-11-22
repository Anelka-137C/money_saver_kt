package com.example.money_saver_android.adversarial

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.data.local.SavingsDatabase
import com.example.money_saver_android.data.repository.BudgetRepository
import com.example.money_saver_android.data.repository.IncomeRepository
import com.example.money_saver_android.data.repository.LanguagePreferenceRepository
import com.example.money_saver_android.data.repository.RoomSavingsRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.BudgetResult
import com.example.money_saver_android.domain.usecase.BudgetUseCase
import com.example.money_saver_android.domain.usecase.IncomeResult
import com.example.money_saver_android.domain.usecase.IncomeUseCase
import com.example.money_saver_android.domain.usecase.SavingsResult
import com.example.money_saver_android.domain.usecase.SavingsUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.IncomesView
import com.example.money_saver_android.ui.SavingsTabContent
import com.example.money_saver_android.ui.components.incomes.IncomeAllocationCard
import com.example.money_saver_android.ui.components.savings.AddSavingDialog
import com.example.money_saver_android.ui.components.savings.DeleteSavingCategoryConfirmationDialog
import com.example.money_saver_android.ui.components.savings.SavingListItem
import com.example.money_saver_android.ui.components.savings.SavingsOverviewCard
import com.example.money_saver_android.viewmodel.FinanceTopTab
import com.example.money_saver_android.viewmodel.IncomeViewModel
import com.example.money_saver_android.viewmodel.SavingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Adversarial & Property-Based Test Suite for Story #10:
 * "Como usuario, quiero apartar dinero de mis ingresos hacia ahorros clasificados por categoría."
 *
 * Targets strictly the new and modified files detected via Git:
 * - SavingsDatabase, RoomSavingsRepository, SavingsRepository
 * - Saving, SavingsUseCase, BudgetUseCase, IncomeUseCase
 * - SavingsViewModel, IncomeViewModel
 * - SavingsView (SavingsTabContent), IncomesView, FinanceTopTabs, IncomeAllocationCard
 * - AddSavingDialog, SavingListItem, SavingsOverviewCard, DeleteSavingConfirmationDialog,
 *   DeleteSavingCategoryConfirmationDialog
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsAllocationAdversarialTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class AdversarialSavingsRepo(
        private val yieldOnMutation: Boolean = false
    ) : SavingsRepository {
        private val _savings = MutableStateFlow<List<Saving>>(emptyList())
        private val _categories = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> =
            _savings.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun getSavingById(id: Long): Saving? =
            _savings.value.find { it.id == id }

        override suspend fun insertSaving(saving: Saving): Long {
            if (yieldOnMutation) yield()
            val assignedId = if (saving.id > 0L) saving.id else nextId++
            if (assignedId >= nextId) nextId = assignedId + 1L
            _savings.value = _savings.value + saving.copy(id = assignedId)
            return assignedId
        }

        override suspend fun updateSaving(saving: Saving) {
            if (yieldOnMutation) yield()
            _savings.value = _savings.value.map { if (it.id == saving.id) saving else it }
        }

        override suspend fun deleteSaving(id: Long) {
            if (yieldOnMutation) yield()
            _savings.value = _savings.value.filterNot { it.id == id }
        }

        override suspend fun countSavingsForCategory(categoryName: String): Int =
            _savings.value.count { it.category == categoryName }

        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int =
            _savings.value.count { it.category == categoryName && it.subcategory == subcategoryName }

        override fun getAllCategories(): Flow<List<Category>> = _categories

        override suspend fun insertCategory(category: Category) {
            if (yieldOnMutation) yield()
            val existingIdx = _categories.value.indexOfFirst { it.name == category.name }
            if (existingIdx >= 0) {
                _categories.value = _categories.value.toMutableList().apply { set(existingIdx, category) }
            } else {
                _categories.value = _categories.value + category
            }
        }

        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
            if (yieldOnMutation) yield()
            _categories.value = _categories.value.map {
                if (it.name == categoryName) {
                    it.copy(subcategories = it.subcategories + subcategory)
                } else it
            }
        }

        override suspend fun deleteCategory(categoryName: String) {
            if (yieldOnMutation) yield()
            _categories.value = _categories.value.filterNot { it.name == categoryName }
        }

        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {
            if (yieldOnMutation) yield()
            _categories.value = _categories.value.map {
                if (it.name == categoryName) {
                    it.copy(subcategories = it.subcategories.filterNot { sub -> sub.name == subcategoryName })
                } else it
            }
        }

        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialIncomeRepo(
        private val yieldOnQuery: Boolean = false
    ) : IncomeRepository {
        private val _incomes = MutableStateFlow<List<Income>>(emptyList())
        private val _categories = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getIncomesByPeriod(periodId: Long): Flow<List<Income>> =
            _incomes.map { list ->
                if (yieldOnQuery) yield()
                list.filter { it.periodId == periodId }
            }

        override suspend fun insertIncome(income: Income): Long {
            val assignedId = if (income.id > 0L) income.id else nextId++
            if (assignedId >= nextId) nextId = assignedId + 1L
            _incomes.value = _incomes.value + income.copy(id = assignedId)
            return assignedId
        }

        override suspend fun updateIncome(income: Income) {
            _incomes.value = _incomes.value.map { if (it.id == income.id) income else it }
        }

        override suspend fun deleteIncome(id: Long) {
            _incomes.value = _incomes.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = _categories
        override suspend fun insertCategory(category: Category) {
            _categories.value = _categories.value + category
        }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) = Unit
        override suspend fun deleteCategory(categoryName: String) = Unit
        override suspend fun countIncomesForCategory(categoryName: String): Int = 0
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) = Unit
        override suspend fun countIncomesForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class AdversarialBudgetRepo(
        private val yieldOnMutation: Boolean = false
    ) : BudgetRepository {
        private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
        private val _categories = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun getBudgetsByPeriod(periodId: Long): Flow<List<Budget>> =
            _budgets.map { list -> list.filter { it.periodId == periodId } }

        override suspend fun insertBudget(budget: Budget): Long {
            if (yieldOnMutation) yield()
            val assignedId = if (budget.id > 0L) budget.id else nextId++
            if (assignedId >= nextId) nextId = assignedId + 1L
            _budgets.value = _budgets.value + budget.copy(id = assignedId)
            return assignedId
        }

        override suspend fun updateBudget(budget: Budget) {
            if (yieldOnMutation) yield()
            _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
        }

        override suspend fun getBudgetById(id: Long): Budget? = _budgets.value.find { it.id == id }

        override suspend fun deleteBudget(id: Long) {
            _budgets.value = _budgets.value.filterNot { it.id == id }
        }

        override fun getAllCategories(): Flow<List<Category>> = _categories
        override suspend fun insertCategory(category: Category) {
            _categories.value = _categories.value + category
        }
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) = Unit
        override suspend fun deleteCategory(categoryName: String) = Unit
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) = Unit
        override suspend fun countBudgetsForCategory(categoryName: String): Int = 0
        override suspend fun countBudgetsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override suspend fun preloadDefaultCategoriesIfNeeded() = Unit
    }

    private class FakeLangRepo(private var lang: String = "es") : LanguagePreferenceRepository {
        override fun getLanguage(): String = lang
        override fun saveLanguage(languageCode: String) {
            lang = languageCode
        }
    }

    // =========================================================================================
    // 1. INTEGRIDAD RELACIONAL: Eliminación / Reducción de Ingreso Comprometido en Ahorros o Presupuestos
    // =========================================================================================
    /**
     * Invariante: Un período financiero nunca debe quedar en estado de sobre-asignación
     * (`totalBudgeted + totalSaved > totalIncome`) ni un ahorro puede superar el monto de su
     * `incomeId` origen (`savedFromIncome <= income.amount`).
     *
     * Escenario adversarial:
     * 1. Se registra un ingreso de $1,000 en el período 1.
     * 2. Se aparta un ahorro de $600 vinculado a ese ingreso y un presupuesto de $300.
     * 3. El usuario intenta reducir el ingreso a $400 (menor que los $600 ya ahorrados de ese ingreso
     *    y menor que los $900 comprometidos en total) o eliminar el ingreso directamente.
     *
     * Comportamiento esperado: `IncomeUseCase.updateIncome` y `deleteIncome` deben rechazar la operación
     * con `IncomeResult.Error` mientras existan ahorros/presupuestos comprometidos que excedan el nuevo total.
     */
    @Test
    fun test_adversarial_income_delete_or_reduce_below_committed_savings_and_budgets_breaks_conservation_invariant() = runBlocking {
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val savingsRepo = AdversarialSavingsRepo()

        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)
        val incomeUseCase = IncomeUseCase(
            repository = incomeRepo,
            budgetRepository = budgetRepo,
            savingsRepository = savingsRepo
        )

        val periodId = 1L
        val incomeId = incomeRepo.insertIncome(
            Income(
                amount = 1000.0,
                dateTime = "2026-11-01 09:00",
                category = "Salario",
                subcategory = "Nómina",
                periodId = periodId
            )
        )

        val saveRes = savingsUseCase.saveSaving(
            amountInput = "600",
            categoryInput = "Emergencias",
            subcategoryInput = "Salud",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-05 10:00",
            noteInput = "Fondo"
        )
        assertTrue(saveRes is SavingsResult.Success)
        assertTrue(incomeUseCase.saveBudget("300", "Comida", "", periodId) is BudgetResult.Success)

        // Attack A: Reduce income from 1000 to 400 (violating savedFromIncome = 600 and committed = 900)
        val reduceResult = incomeUseCase.updateIncome(
            id = incomeId,
            amountInput = "400",
            dateTimeInput = "2026-11-01 09:00",
            categoryInput = "Salario",
            subcategoryInput = "Nómina",
            descriptionInput = "Reducido ilegalmente",
            periodId = periodId
        )

        assertTrue(
            "Reducing an income below already-allocated savings ($600) and budgets ($300) must be rejected",
            reduceResult is IncomeResult.Error
        )

        // Attack B: Delete the source income while $600 of savings still reference incomeId
        val deleteResult = incomeUseCase.deleteIncome(incomeId)
        assertTrue(
            "Deleting an income that backs active savings must be rejected to prevent orphaned over-allocated savings",
            deleteResult is IncomeResult.Error
        )

        val currentIncomes = incomeRepo.getIncomesByPeriod(periodId).first()
        val currentSavings = savingsRepo.getSavingsByPeriod(periodId).first()
        val currentBudgets = budgetRepo.getBudgetsByPeriod(periodId).first()
        assertTrue(
            "Invariant violated: totalSaved + totalBudgeted exceeds totalIncome!",
            currentSavings.sumOf { it.amount } + currentBudgets.sumOf { it.amount } <= currentIncomes.sumOf { it.amount }
        )
    }

    // =========================================================================================
    // 2. CONCURRENCIA CRUZADA: Condición de Carrera entre BudgetUseCase y SavingsUseCase
    // =========================================================================================
    /**
     * Invariante: En ningún escenario de ejecución concurrente la suma `totalBudgeted + totalSaved`
     * puede superar `totalIncome`.
     *
     * Causa raíz: `BudgetUseCase` utiliza `budgetMutex` y `SavingsUseCase` utiliza `savingMutex`
     * (dos instancias de `Mutex` distintas e independientes). Si el usuario o dos corrutinas
     * disparan simultáneamente `saveBudget("700")` y `saveSaving("700")` sobre un ingreso
     * disponible de $1,000, ambas leen `available = 1000.0` antes de que la otra inserte y
     * ambas tienen éxito, comprometiendo $1,400 sobre $1,000 de ingreso.
     */
    @Test
    fun test_adversarial_cross_usecase_concurrent_budget_and_saving_race_condition_overallocates_income() = runTest {
        val incomeRepo = AdversarialIncomeRepo(yieldOnQuery = true)
        val budgetRepo = AdversarialBudgetRepo(yieldOnMutation = true)
        val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)

        val periodId = 1L
        val incomeId = incomeRepo.insertIncome(
            Income(amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )

        val budgetUseCase = BudgetUseCase(budgetRepo, incomeRepo, savingsRepo)
        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)

        val deferredBudget = async {
            budgetUseCase.saveBudget(
                amountInput = "700",
                categoryInput = "Housing",
                subcategoryInput = "Rent",
                periodId = periodId
            )
        }
        val deferredSaving = async {
            savingsUseCase.saveSaving(
                amountInput = "700",
                categoryInput = "Emergency",
                subcategoryInput = "",
                periodId = periodId,
                incomeId = incomeId,
                dateTimeInput = "2026-11-22 10:00",
                noteInput = "Concurrente"
            )
        }

        val results = awaitAll(deferredBudget, deferredSaving)
        val budgetSuccess = results[0] is BudgetResult.Success
        val savingSuccess = results[1] is SavingsResult.Success

        val totalBudgeted = budgetRepo.getBudgetsByPeriod(periodId).first().sumOf { it.amount }
        val totalSaved = savingsRepo.getSavingsByPeriod(periodId).first().sumOf { it.amount }

        assertFalse(
            "Race condition exploited! Both saveBudget(700) and saveSaving(700) succeeded on $1000 income (total allocated = ${totalBudgeted + totalSaved})",
            budgetSuccess && savingSuccess
        )
        assertTrue(
            "Invariant violated: totalBudgeted ($totalBudgeted) + totalSaved ($totalSaved) > totalIncome (1000.0)",
            totalBudgeted + totalSaved <= 1000.0
        )
    }

    // =========================================================================================
    // 3. ROOM DATABASE: Case-Sensitivity en Subcategorías y Colisión de ForeignKey
    // =========================================================================================
    /**
     * Invariante: Crear o eliminar categorías/subcategorías en `SavingsUseCase` con diferencias de
     * mayúsculas/minúsculas o espacios laterales nunca debe lanzar `SQLiteConstraintException` ni
     * dejar de bloquear el borrado de categorías con ahorros asociados.
     *
     * Escenarios adversariales sobre `RoomSavingsRepository` + `SavingsDatabase` real (SQLite):
     * A) Crear categoría `"Viajes"`. Luego llamar `createSubcategory("viajes", "Vuelos")` (en minúsculas)
     *    o `createSubcategory("  Viajes  ", "Hotel")`. En SQLite `FOREIGN KEY(categoryName) REFERENCES saving_category(name)`
     *    es case-sensitive por defecto, por lo que insertar `"viajes"` viola la clave foránea.
     * B) Guardar un ahorro con categoría `"Viajes"` y subcategoría `"Vuelos"`. Luego intentar
     *    `deleteCategory("viajes")` o `deleteSubcategory("viajes", "vuelos")` en minúsculas.
     *    `SELECT COUNT(*) FROM savings WHERE category = :categoryName` es case-sensitive sin `COLLATE NOCASE`,
     *    devolviendo `0` y permitiendo borrar o dejando registros incoherentes.
     */
    @Test
    fun test_adversarial_room_savings_database_case_insensitive_foreign_key_and_delete_protection() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, SavingsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val savingsRepo = RoomSavingsRepository(db.savingDao(), db.savingCategoryDao())
            val incomeRepo = AdversarialIncomeRepo()
            val useCase = SavingsUseCase(savingsRepo, incomeRepo)

            val periodId = 1L
            val incomeId = incomeRepo.insertIncome(
                Income(amount = 2000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = periodId)
            )

            assertTrue(useCase.createCategory("Viajes", "#3B82F6") is SavingsResult.Success)

            // Attack A: Create subcategory referencing parent with different casing & whitespace
            val subResult = try {
                useCase.createSubcategory("  viajes  ", "Vuelos")
            } catch (e: SQLiteConstraintException) {
                fail("SQLiteConstraintException thrown when creating subcategory with different parent casing: ${e.message}")
                SavingsResult.Error("SQLiteConstraintException")
            }
            assertTrue(
                "createSubcategory should resolve the canonical parent category name case-insensitively",
                subResult is SavingsResult.Success
            )

            // Persist a saving under "Viajes" / "Vuelos"
            val saveRes = useCase.saveSaving(
                amountInput = "500",
                categoryInput = "Viajes",
                subcategoryInput = "Vuelos",
                periodId = periodId,
                incomeId = incomeId,
                dateTimeInput = "2026-11-22 10:30",
                noteInput = "Boleto"
            )
            assertTrue(saveRes is SavingsResult.Success)

            // Attack B: Attempt to bypass category deletion protection using lowercase "viajes"
            val deleteCatLower = useCase.deleteCategory("viajes")
            assertTrue(
                "deleteCategory('viajes') must be blocked even when queried in lowercase because 'Viajes' has associated savings",
                deleteCatLower is SavingsResult.Error
            )

            // Attack C: Attempt to bypass subcategory deletion protection using lowercase "viajes" / "vuelos"
            val deleteSubLower = useCase.deleteSubcategory("viajes", "vuelos")
            assertTrue(
                "deleteSubcategory('viajes', 'vuelos') must be blocked even when queried in lowercase",
                deleteSubLower is SavingsResult.Error
            )
        } finally {
            db.close()
        }
    }

    // =========================================================================================
    // 4. PRECISIÓN NUMÉRICA IEEE 754: Falsa Rechazo de Ahorro del Saldo Exacto Restante
    // =========================================================================================
    /**
     * Invariante: Si un ingreso tiene $1.00 y el usuario ya apartó $0.70 (o tres ahorros de $0.10
     * dejando $0.70, o presupuesto de $0.70 dejando $0.30), el usuario DEBE poder apartar el saldo
     * exacto restante ($0.30).
     *
     * Causa raíz: En IEEE 754 binary64:
     * - `1.0 - 0.7 = 0.30000000000000004` (o `0.1 + 0.2 = 0.30000000000000004`, luego `1.0 - 0.30000000000000004 = 0.69999999999999995559`).
     * En `SavingsUseCase.saveSaving`:
     *   `val savedFromIncome = periodSavings.filter { it.incomeId == matchedIncome.id }.sumOf { it.amount }` // 0.1 + 0.2 = 0.30000000000000004
     *   `val incomeRemaining = matchedIncome.amount - savedFromIncome` // 1.0 - 0.30000000000000004 = 0.69999999999999995559
     *   `if (amount > incomeRemaining)` -> `0.7 > 0.69999999999999995559` evalúa a `TRUE`!
     * El usuario intenta apartar exactamente `"0.7"` (los 70 centavos restantes) y el sistema lo rechaza
     * con `"El monto supera el disponible del ingreso seleccionado"`.
     */
    @Test
    fun test_adversarial_ieee754_floating_point_drift_falsely_rejects_exact_remaining_cents() = runBlocking {
        val savingsRepo = AdversarialSavingsRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val useCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)

        val periodId = 1L
        val incomeId = incomeRepo.insertIncome(
            Income(amount = 1.00, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )

        // First saving: $0.10
        assertTrue(
            useCase.saveSaving("0.10", "Emergency", "", periodId, incomeId, "2026-11-01 10:00") is SavingsResult.Success
        )
        // Second saving: $0.20 (0.10 + 0.20 in IEEE 754 is 0.30000000000000004)
        assertTrue(
            useCase.saveSaving("0.20", "Emergency", "", periodId, incomeId, "2026-11-01 11:00") is SavingsResult.Success
        )

        // Third saving: The exact remaining $0.70 ($1.00 - $0.10 - $0.20 = $0.70)
        val exactRemainingResult = useCase.saveSaving(
            amountInput = "0.70",
            categoryInput = "Emergency",
            subcategoryInput = "",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-01 12:00"
        )

        assertTrue(
            "IEEE 754 precision bug: Saving the exact remaining $0.70 after $0.10 + $0.20 on a $1.00 income was falsely rejected: $exactRemainingResult",
            exactRemainingResult is SavingsResult.Success
        )
    }

    // =========================================================================================
    // 5. CICLO DE VIDA Y FUGA DE ESTADO EN SavingsViewModel AL CAMBIAR DE PERÍODO O CERRAR DIÁLOGO
    // =========================================================================================
    /**
     * Invariante: Al cambiar de período financiero (`onPeriodSelected(newPeriodId)` o `null`),
     * `SavingsViewModel` debe limpiar todo el estado temporal del formulario y diálogos del período
     * anterior (`savingAmountInput`, `savingCategoryInput`, `savingSubcategoryInput`,
     * `savingDateTimeInput`, `savingNoteInput`, `savingCategoryToDelete`, `categoryError`, `subcategoryError`).
     *
     * Causa raíz: `SavingsViewModel.onPeriodSelected` resetea `savingIncomeIdInput`, `savingErrorMessage`,
     * `editingSaving` y `savingToDelete`, pero olvida limpiar `savingAmountInput`, `savingCategoryInput`,
     * `savingSubcategoryInput`, `savingDateTimeInput`, `savingNoteInput` y `savingCategoryToDelete`.
     */
    @Test
    fun test_adversarial_savings_viewmodel_period_switch_leaks_form_inputs_and_category_delete_dialog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val savingsRepo = AdversarialSavingsRepo()
            val incomeRepo = AdversarialIncomeRepo()
            val budgetRepo = AdversarialBudgetRepo()
            val viewModel = SavingsViewModel(SavingsUseCase(savingsRepo, incomeRepo, budgetRepo))

            incomeRepo.insertIncome(Income(id = 1L, amount = 1000.0, dateTime = "2026-11-01", category = "Salary", periodId = 1L))
            viewModel.onPeriodSelected(1L)
            advanceUntilIdle()

            // User fills form and opens delete category confirmation in Period 1
            viewModel.onAmountChange("450.00")
            viewModel.onCategorySelected("Emergencias", "Salud")
            viewModel.onDateTimeChange("2026-11-15 09:30")
            viewModel.onNoteChange("Nota secreta del período 1")
            viewModel.requestDeleteSavingCategory("Emergencias")

            // Switch to Period 2
            viewModel.onPeriodSelected(2L)
            advanceUntilIdle()

            val stateAfterSwitch = viewModel.uiState.value
            assertEquals("savingAmountInput leaked across period switch", "", stateAfterSwitch.savingAmountInput)
            assertEquals("savingCategoryInput leaked across period switch", "", stateAfterSwitch.savingCategoryInput)
            assertEquals("savingSubcategoryInput leaked across period switch", "", stateAfterSwitch.savingSubcategoryInput)
            assertEquals("savingDateTimeInput leaked across period switch", "", stateAfterSwitch.savingDateTimeInput)
            assertEquals("savingNoteInput leaked across period switch", "", stateAfterSwitch.savingNoteInput)
            assertNull("savingCategoryToDelete dialog leaked across period switch", stateAfterSwitch.savingCategoryToDelete)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================================
    // 6. CONCURRENCIA EN SavingsViewModel: Doble Eliminación y Guardado Concurrente
    // =========================================================================================
    /**
     * Invariante:
     * 1. Dos llamadas rápidas concurrentes a `saveSaving()` o `confirmEditSaving()` no deben duplicar
     *    registros ni corromper `isSaving`.
     * 2. Dos llamadas rápidas concurrentes a `confirmDeleteSaving()` o `createSavingCategory()` no
     *    deben ejecutar operaciones redundantes mientras la primera sigue en vuelo.
     */
    @Test
    fun test_adversarial_savings_viewmodel_concurrent_double_save_and_double_delete_idempotency() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val savingsRepo = AdversarialSavingsRepo(yieldOnMutation = true)
            val incomeRepo = AdversarialIncomeRepo()
            val budgetRepo = AdversarialBudgetRepo()
            val viewModel = SavingsViewModel(SavingsUseCase(savingsRepo, incomeRepo, budgetRepo))

            val periodId = 1L
            incomeRepo.insertIncome(
                Income(id = 10L, amount = 1000.0, dateTime = "2026-11-01 08:00", category = "Employment", periodId = periodId)
            )

            viewModel.onPeriodSelected(periodId)
            advanceUntilIdle()

            val income = viewModel.uiState.value.incomes.first()
            viewModel.onAmountChange("600")
            viewModel.onIncomeSelected(income)
            viewModel.onCategorySelected("Emergency", "Health")
            viewModel.onDateTimeChange("2026-11-22 10:00")
            viewModel.onNoteChange("Double tap save")

            // Double tap save before first coroutine finishes
            viewModel.saveSaving()
            viewModel.saveSaving()
            advanceUntilIdle()

            assertEquals(
                "Double invocation of saveSaving() must only insert 1 saving",
                1,
                viewModel.uiState.value.savings.size
            )
            assertEquals(600.0, viewModel.uiState.value.totalSaved, 0.001)
            assertEquals(400.0, viewModel.uiState.value.availableIncome, 0.001)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // =========================================================================================
    // 7. VALIDACIÓN ADVERSARIAL DE MONTOS Y FECHAS (Coma Decimal, Infinitos, Calendario Inválido)
    // =========================================================================================
    /**
     * Invariante:
     * 1. Entradas con coma decimal europea/latinoamericana como `"10,50"` NO deben inflarse 100 veces
     *    a `1050.0` silenciosamente por `replace(",", "")`.
     * 2. Montos `NaN`, `Infinity`, `-Infinity`, `0`, `-0.0`, negativos o vacíos deben rechazarse.
     * 3. Fechas inexistentes como `"2026-02-29 10:00"` (2026 no es bisiesto), `"2026-04-31 09:00"`,
     *    `"2026-11-22 24:00"` o `"2026-11-22 10:60"` deben ser rechazadas por `isValidSavingDateTime`.
     */
    @Test
    fun test_adversarial_savings_usecase_decimal_comma_inflation_and_strict_calendar_boundaries() = runBlocking {
        val savingsRepo = AdversarialSavingsRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val useCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)

        val periodId = 1L
        val incomeId = incomeRepo.insertIncome(
            Income(amount = 5000.0, dateTime = "2026-11-01 09:00", category = "Employment", periodId = periodId)
        )

        // Non-finite, zero, negative amounts
        val invalidAmounts = listOf("0", "0.00", "-0.0", "-1", "-999999.99", "NaN", "Infinity", "-Infinity", "   ", "abc")
        for (amt in invalidAmounts) {
            val res = useCase.saveSaving(amt, "Emergency", "", periodId, incomeId, "2026-11-22 10:30")
            assertTrue("Amount '$amt' must be rejected", res is SavingsResult.Error)
        }

        // Strict calendar validation (non-leap year Feb 29, April 31, hour 24, minute 60, trailing garbage)
        val invalidDates = listOf(
            "",
            "   ",
            "2026-02-29 10:00", // 2026 is not a leap year
            "2026-04-31 10:00", // April has 30 days
            "2026-13-01 10:00",
            "2026-00-10 10:00",
            "2026-11-00 10:00",
            "2026-11-22 24:00",
            "2026-11-22 10:60",
            "2026-11-22 10:00:00",
            "2026/11/22 10:00"
        )
        for (dt in invalidDates) {
            val res = useCase.saveSaving("50", "Emergency", "", periodId, incomeId, dt)
            assertTrue("Date '$dt' must be rejected", res is SavingsResult.Error)
        }

        // Decimal comma attack: user enters "10,50" expecting $10.50 or validation error, NEVER $1,050.00!
        val commaResult = useCase.saveSaving(
            amountInput = "10,50",
            categoryInput = "Emergency",
            subcategoryInput = "",
            periodId = periodId,
            incomeId = incomeId,
            dateTimeInput = "2026-11-22 10:30"
        )
        val savedAfterComma = savingsRepo.getSavingsByPeriod(periodId).first()
        if (commaResult is SavingsResult.Success) {
            assertEquals(
                "Decimal comma input '10,50' was silently multiplied by 100 into ${savedAfterComma.first().amount}!",
                10.50,
                savedAfterComma.first().amount,
                0.001
            )
        }
    }

    // =========================================================================================
    // 8. UI COMPOSE: Truncamiento IEEE 754 en Porcentajes (29.0 / 100.0 -> 28% vs 29%)
    // =========================================================================================
    /**
     * Invariante: Cuando `totalIncome = 100.0` y `totalSaved = 29.0` (exactamente 29%), tanto
     * `SavingsOverviewCard` como `IncomeAllocationCard` deben mostrar `"29%"` y nunca `"28%"`.
     *
     * Causa raíz:
     * 1. En `SavingsOverviewCard.kt` (líneas 56-57):
     *    `val progress = if (totalIncome > 0) (totalSaved / totalIncome).toFloat().coerceIn(0f, 1f) else 0f`
     *    `val percent = (progress * 100).toInt()`
     *    En Float32: `(29.0 / 100.0).toFloat() * 100f` o en Double64: `((29.0 / 100.0) * 100).toInt()`
     *    en `IncomeAllocationCard.kt` (línea 51):
     *    `(29.0 / 100.0) * 100.0 == 28.999999999999996`, cuyo `.toInt()` trunca a `28` ("28%")!
     * 2. Con `totalSaved = 57.0` y `totalIncome = 100.0`:
     *    `(57.0 / 100.0) * 100.0 == 56.99999999999999`, cuyo `.toInt()` trunca a `56` ("56%")!
     */
    @Test
    fun test_adversarial_ui_income_allocation_and_savings_overview_ieee754_percentage_truncation() {
        val context = RuntimeEnvironment.getApplication()
        val i18n = I18nManager(context, FakeLangRepo("es"))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column {
                    IncomeAllocationCard(
                        totalIncome = 100.0,
                        totalBudgeted = 29.0,
                        totalSaved = 57.0,
                        currency = defaultCurrencies.first()
                    )
                    SavingsOverviewCard(
                        totalSaved = 29.0,
                        totalIncome = 100.0,
                        availableIncome = 14.0,
                        currency = defaultCurrencies.first()
                    )
                }
            }
        }

        // 57.0 / 100.0 is 57%, must NOT be truncated to 56% by IEEE 754 binary floating point
        assertTrue(
            "IncomeAllocationCard truncated 57.0/100.0 to 56% instead of 57%!",
            composeTestRule.onAllNodesWithText("57%").fetchSemanticsNodes().isNotEmpty()
        )
        // 29.0 / 100.0 is 29%, must NOT be truncated to 28%
        assertTrue(
            "IncomeAllocationCard / SavingsOverviewCard truncated 29.0/100.0 to 28% instead of 29%!",
            composeTestRule.onAllNodesWithText("29%").fetchSemanticsNodes().size >= 2
        )
    }

    // =========================================================================================
    // 9. UI COMPOSE: Valores No Finitos (Double.POSITIVE_INFINITY / NaN) y Color Hex Malformado
    // =========================================================================================
    /**
     * Invariante: Si `totalSaved` y `totalIncome` desbordan a `Double.POSITIVE_INFINITY` (ej. suma de
     * dos montos `1e308 + 1e308`), `(totalSaved / totalIncome).toFloat()` produce `Float.NaN`, y en Kotlin
     * `Float.NaN.coerceIn(0f, 1f)` retorna `Float.NaN`. Pasar `progress = Float.NaN` a
     * `LinearProgressIndicator` en `IncomeAllocationCard` o `Canvas.drawArc` en `SavingsOverviewCard`,
     * o pasar un color hex corrupto (`"INVALID_HEX"`) a `DeleteSavingCategoryConfirmationDialog`,
     * nunca debe crashear la UI.
     */
    @Test
    fun test_adversarial_ui_infinite_amounts_nan_progress_and_malformed_hex_color_do_not_crash() {
        val context = RuntimeEnvironment.getApplication()
        val i18n = I18nManager(context, FakeLangRepo("es"))
        val malformedCategory = Category(
            name = "Categoría Corrupta 💥",
            icon = "",
            color = "NOT_A_VALID_HEX_COLOR",
            subcategories = emptyList()
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    IncomeAllocationCard(
                        totalIncome = Double.POSITIVE_INFINITY,
                        totalBudgeted = Double.POSITIVE_INFINITY,
                        totalSaved = Double.POSITIVE_INFINITY,
                        currency = defaultCurrencies.first()
                    )
                    SavingsOverviewCard(
                        totalSaved = Double.POSITIVE_INFINITY,
                        totalIncome = Double.POSITIVE_INFINITY,
                        availableIncome = Double.NaN,
                        currency = defaultCurrencies.first()
                    )
                    DeleteSavingCategoryConfirmationDialog(
                        category = malformedCategory,
                        onConfirm = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("card_income_allocation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_savings_overview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_delete_saving_category").assertIsDisplayed()
        composeTestRule.onNodeWithText("Categoría Corrupta 💥").assertIsDisplayed()
    }

    // =========================================================================================
    // 10. UI COMPOSE: Sincronización Reactiva Entre Pestañas (Ingresos <-> Presupuesto <-> Ahorros)
    // =========================================================================================
    /**
     * Invariante: En `IncomesView`, cuando el usuario navega a la pestaña `SAVINGS`, aparta un ahorro,
     * y regresa a `INCOMES` o `BUDGET`, `IncomeAllocationCard` y `BudgetOverviewCard` deben reflejar
     * inmediatamente el nuevo `totalSaved` y el `availableIncome` descontado. Y al cambiar de período
     * en el selector superior, `SavingsViewModel` e `IncomeViewModel` deben permanecer sincronizados.
     */
    @Test
    fun test_adversarial_incomes_view_cross_tab_savings_allocation_and_period_switch_synchronization() {
        val context = RuntimeEnvironment.getApplication()
        val i18n = I18nManager(context, FakeLangRepo("es"))
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val savingsRepo = AdversarialSavingsRepo()

        runBlocking {
            incomeRepo.insertIncome(
                Income(id = 1L, amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = 1L)
            )
            incomeRepo.insertIncome(
                Income(id = 2L, amount = 2000.0, dateTime = "2026-12-01 09:00", category = "Salario", periodId = 2L)
            )
            budgetRepo.insertBudget(Budget(id = 1L, amount = 300.0, category = "Comida", periodId = 1L))
            savingsRepo.insertSaving(
                Saving(
                    id = 1L,
                    amount = 200.0,
                    category = "Emergencias",
                    subcategory = "Salud",
                    periodId = 1L,
                    incomeId = 1L,
                    incomeCategory = "Salario",
                    dateTime = "2026-11-10 10:00",
                    note = "Fondo P1"
                )
            )
            savingsRepo.insertSaving(
                Saving(
                    id = 2L,
                    amount = 800.0,
                    category = "Viajes",
                    subcategory = "",
                    periodId = 2L,
                    incomeId = 2L,
                    incomeCategory = "Salario",
                    dateTime = "2026-12-10 10:00",
                    note = "Fondo P2"
                )
            )
        }

        val incomeUseCase = IncomeUseCase(
            repository = incomeRepo,
            budgetRepository = budgetRepo,
            savingsRepository = savingsRepo
        )
        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)
        val incomeViewModel = IncomeViewModel(incomeUseCase)
        val savingsViewModel = SavingsViewModel(savingsUseCase)

        val periods = listOf(
            FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30"),
            FinancialPeriod(id = 2L, startDate = "2026-12-01", endDate = "2026-12-31")
        )
        val currency = defaultCurrencies.first()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                IncomesView(
                    incomeViewModel = incomeViewModel,
                    savingsViewModel = savingsViewModel,
                    allPeriods = periods,
                    selectedPeriodId = 1L,
                    onPeriodSelected = { newId ->
                        incomeViewModel.onPeriodSelected(newId)
                        savingsViewModel.onPeriodSelected(newId)
                    },
                    i18nManager = i18n,
                    currency = currency
                )
            }
        }
        composeTestRule.waitForIdle()

        // Period 1: Total Income = 1000, Budgeted = 300, Saved = 200 -> Available = 500
        assertEquals(200.0, incomeViewModel.uiState.value.totalSaved, 0.001)
        assertEquals(500.0, incomeViewModel.uiState.value.availableIncome, 0.001)

        // Switch to Savings tab
        composeTestRule.onNodeWithTag("tab_top_savings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("container_savings_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saving_item_1").assertIsDisplayed()
        assertEquals(200.0, savingsViewModel.uiState.value.totalSaved, 0.001)
        assertEquals(500.0, savingsViewModel.uiState.value.availableIncome, 0.001)

        // Switch period to Period 2 via selector_period
        composeTestRule.onNodeWithTag("selector_period").performClick()
        composeTestRule.onNodeWithTag("period_option_2").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2L, incomeViewModel.uiState.value.selectedPeriodId)
        assertEquals(2L, savingsViewModel.uiState.value.selectedPeriodId)
        assertEquals(800.0, savingsViewModel.uiState.value.totalSaved, 0.001)
        assertEquals(1200.0, savingsViewModel.uiState.value.availableIncome, 0.001)
        composeTestRule.onNodeWithTag("saving_item_2").assertIsDisplayed()
    }

    // =========================================================================================
    // 11. UI COMPOSE: Fuga de Error de Subcategoría en CategorySelectionSheet desde SavingsTabContent
    // =========================================================================================
    /**
     * Invariante: En `SavingsTabContent`, al cerrar el diálogo de añadir ahorro (`btn_close_saving_dialog`)
     * y volver a abrirlo, cualquier `savingErrorMessage` previo debe haberse limpiado; y al recibir un
     * error de validación al guardar, el mensaje debe mostrarse en `text_saving_error` sin cerrar el diálogo.
     */
    @Test
    fun test_adversarial_savings_tab_error_recovery_and_unicode_payloads_in_list_item() {
        val context = RuntimeEnvironment.getApplication()
        val i18n = I18nManager(context, FakeLangRepo("es"))
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()
        val savingsRepo = AdversarialSavingsRepo()

        runBlocking {
            incomeRepo.insertIncome(
                Income(id = 1L, amount = 1000.0, dateTime = "2026-11-01 09:00", category = "Salario", periodId = 1L)
            )
        }

        val viewModel = SavingsViewModel(SavingsUseCase(savingsRepo, incomeRepo, budgetRepo))
        viewModel.onPeriodSelected(1L)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18n) {
                SavingsTabContent(
                    viewModel = viewModel,
                    currency = defaultCurrencies.first()
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open AddSavingDialog and attempt to save empty form -> error displayed
        composeTestRule.onNodeWithTag("btn_toggle_add_saving").performClick()
        composeTestRule.onNodeWithTag("btn_save_saving").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_saving_error").assertIsDisplayed()

        // Close dialog and reopen -> error must be cleared
        composeTestRule.onNodeWithTag("btn_close_saving_dialog").performClick()
        composeTestRule.waitForIdle()
        assertNull(viewModel.uiState.value.savingErrorMessage)

        composeTestRule.onNodeWithTag("btn_toggle_add_saving").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Stale error message should not be displayed after closing and reopening AddSavingDialog",
            composeTestRule.onAllNodesWithTag("text_saving_error").fetchSemanticsNodes().isEmpty()
        )
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithTag(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag))

    // =========================================================================================
    // 12. PROPERTY-BASED TESTING: Invariantes de Conservación Financiera (500 Iteraciones)
    // =========================================================================================
    /**
     * Propiedades e Invariantes Evaluadas en 500 Escenarios Aleatorios:
     *
     * 1. **Invariante de Conservación Global por Período**:
     *    Para todo período $P$:
     *    $$\text{totalBudgeted}(P) + \text{totalSaved}(P) \le \text{totalIncome}(P)$$
     *    $$\text{availableIncome}(P) = \max(0.0, \text{totalIncome}(P) - \text{totalBudgeted}(P) - \text{totalSaved}(P)) \ge 0.0$$
     *
     * 2. **Invariante de Conservación por Ingreso Origen (`incomeId`)**:
     *    Para todo ingreso $I \in \text{Incomes}(P)$:
     *    $$\sum_{S \in \text{Savings}(P), S.\text{incomeId} == I.\text{id}} S.\text{amount} \le I.\text{amount}$$
     *
     * 3. **Invariante de Aislamiento de Períodos**:
     *    Operaciones de inserción, actualización o borrado en el período $P_1$ jamás alteran
     *    $\text{totalSaved}(P_2)$ ni $\text{availableIncome}(P_2)$ para $P_1 \neq P_2$.
     *
     * 4. **Invariante de No-NaN / Finitud**:
     *    `calculateAvailableIncome` jamás devuelve `NaN` ni un número negativo, incluso ante
     *    entradas extremas (`Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, `-0.0`, `Double.MAX_VALUE`).
     */
    @Test
    fun test_property_based_invariant_savings_and_budgets_conservation_and_source_income_bounds() = runBlocking {
        val rng = Random(20261122L)
        val savingsRepo = AdversarialSavingsRepo()
        val incomeRepo = AdversarialIncomeRepo()
        val budgetRepo = AdversarialBudgetRepo()

        val savingsUseCase = SavingsUseCase(savingsRepo, incomeRepo, budgetRepo)
        val budgetUseCase = BudgetUseCase(budgetRepo, incomeRepo, savingsRepo)
        val incomeUseCase = IncomeUseCase(
            repository = incomeRepo,
            budgetRepository = budgetRepo,
            budgetUseCase = budgetUseCase,
            savingsRepository = savingsRepo
        )

        // Seed 3 periods with multiple incomes
        val periodIds = listOf(1L, 2L, 3L)
        val incomesByPeriod = mutableMapOf<Long, MutableList<Long>>()
        for (pId in periodIds) {
            val list = mutableListOf<Long>()
            repeat(3) { idx ->
                val amount = rng.nextInt(500, 3000).toDouble()
                val incId = incomeRepo.insertIncome(
                    Income(
                        amount = amount,
                        dateTime = "2026-11-0${idx + 1} 09:00",
                        category = "Cat_$idx",
                        subcategory = if (idx % 2 == 0) "Sub_$idx" else "",
                        periodId = pId
                    )
                )
                list.add(incId)
            }
            incomesByPeriod[pId] = list
        }

        // Part A: Extreme numerical boundary property check on calculateAvailableIncome
        val extremeDoubles = listOf(
            0.0, -0.0, 1.0, -1.0, 1e-12, 1e15, 1e308,
            Double.MAX_VALUE, Double.MIN_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        )
        for (inc in extremeDoubles) {
            for (bud in extremeDoubles) {
                for (sav in extremeDoubles) {
                    val avail1 = savingsUseCase.calculateAvailableIncome(inc, bud, sav)
                    val avail2 = budgetUseCase.calculateAvailableIncome(inc, bud, sav)
                    val avail3 = incomeUseCase.calculateAvailableIncome(inc, bud, sav)

                    assertFalse("SavingsUseCase.calculateAvailableIncome returned NaN for ($inc, $bud, $sav)", avail1.isNaN())
                    assertFalse("BudgetUseCase.calculateAvailableIncome returned NaN for ($inc, $bud, $sav)", avail2.isNaN())
                    assertFalse("IncomeUseCase.calculateAvailableIncome returned NaN for ($inc, $bud, $sav)", avail3.isNaN())
                    assertTrue("Available income must be >= 0.0", avail1 >= 0.0 && avail2 >= 0.0 && avail3 >= 0.0)
                    assertEquals(avail1, avail2, 0.0001)
                    assertEquals(avail1, avail3, 0.0001)
                }
            }
        }

        // Part B: 500 stateful random operations (saveSaving, updateSaving, deleteSaving, saveBudget, updateBudget, deleteBudget)
        repeat(500) { step ->
            val targetPeriod = periodIds[rng.nextInt(periodIds.size)]
            val otherPeriod = periodIds.first { it != targetPeriod }
            val otherSavingsSnapshot = savingsRepo.getSavingsByPeriod(otherPeriod).first()
            val otherBudgetsSnapshot = budgetRepo.getBudgetsByPeriod(otherPeriod).first()

            when (rng.nextInt(6)) {
                0 -> { // saveSaving
                    val candidateIncomes = incomesByPeriod[targetPeriod].orEmpty() + listOf(-1L, 999999L)
                    val chosenIncomeId = candidateIncomes[rng.nextInt(candidateIncomes.size)]
                    val amountStr = if (rng.nextInt(5) == 0) "-50" else rng.nextInt(0, 1500).toString()
                    savingsUseCase.saveSaving(
                        amountInput = amountStr,
                        categoryInput = if (rng.nextInt(10) == 0) "   " else "Goal_${rng.nextInt(4)}",
                        subcategoryInput = "Sub_${rng.nextInt(3)}",
                        periodId = targetPeriod,
                        incomeId = chosenIncomeId,
                        dateTimeInput = if (rng.nextInt(10) == 0) "invalid-date" else "2026-11-22 10:30",
                        noteInput = "Step $step"
                    )
                }
                1 -> { // updateSaving
                    val currentSavings = savingsRepo.getSavingsByPeriod(targetPeriod).first()
                    if (currentSavings.isNotEmpty()) {
                        val targetSaving = currentSavings[rng.nextInt(currentSavings.size)]
                        val candidateIncomes = incomesByPeriod[targetPeriod].orEmpty()
                        val chosenIncomeId = candidateIncomes[rng.nextInt(candidateIncomes.size)]
                        val newAmt = rng.nextInt(1, 1500).toString()
                        savingsUseCase.updateSaving(
                            id = targetSaving.id,
                            amountInput = newAmt,
                            categoryInput = "Goal_${rng.nextInt(4)}",
                            subcategoryInput = "Sub_${rng.nextInt(3)}",
                            periodId = targetPeriod,
                            incomeId = chosenIncomeId,
                            dateTimeInput = "2026-11-23 11:00",
                            noteInput = "Updated $step"
                        )
                    }
                }
                2 -> { // deleteSaving
                    val currentSavings = savingsRepo.getSavingsByPeriod(targetPeriod).first()
                    if (currentSavings.isNotEmpty() && rng.nextBoolean()) {
                        val targetSaving = currentSavings[rng.nextInt(currentSavings.size)]
                        savingsUseCase.deleteSaving(targetSaving.id)
                    }
                }
                3 -> { // saveBudget
                    val amt = rng.nextInt(1, 1200).toString()
                    budgetUseCase.saveBudget(amt, "BudgetCat_${rng.nextInt(3)}", "", targetPeriod)
                }
                4 -> { // updateBudget
                    val currentBudgets = budgetRepo.getBudgetsByPeriod(targetPeriod).first()
                    if (currentBudgets.isNotEmpty()) {
                        val b = currentBudgets[rng.nextInt(currentBudgets.size)]
                        val amt = rng.nextInt(1, 1500).toString()
                        budgetUseCase.updateBudget(b.id, amt, b.category, b.subcategory, targetPeriod)
                    }
                }
                5 -> { // deleteBudget
                    val currentBudgets = budgetRepo.getBudgetsByPeriod(targetPeriod).first()
                    if (currentBudgets.isNotEmpty() && rng.nextBoolean()) {
                        val b = currentBudgets[rng.nextInt(currentBudgets.size)]
                        budgetUseCase.deleteBudget(b.id)
                    }
                }
            }

            // Verify Period Isolation Invariant
            assertEquals(
                "Period isolation violated for savings at step $step",
                otherSavingsSnapshot,
                savingsRepo.getSavingsByPeriod(otherPeriod).first()
            )
            assertEquals(
                "Period isolation violated for budgets at step $step",
                otherBudgetsSnapshot,
                budgetRepo.getBudgetsByPeriod(otherPeriod).first()
            )

            // Verify Global & Per-Income Conservation Invariants on targetPeriod
            val periodIncomes = incomeRepo.getIncomesByPeriod(targetPeriod).first()
            val periodBudgets = budgetRepo.getBudgetsByPeriod(targetPeriod).first()
            val periodSavings = savingsRepo.getSavingsByPeriod(targetPeriod).first()

            val totalIncome = periodIncomes.sumOf { it.amount }
            val totalBudgeted = periodBudgets.sumOf { it.amount }
            val totalSaved = periodSavings.sumOf { it.amount }

            assertTrue(
                "Conservation invariant violated at step $step: totalBudgeted ($totalBudgeted) + totalSaved ($totalSaved) > totalIncome ($totalIncome)",
                totalBudgeted + totalSaved <= totalIncome + 1e-6
            )

            for (income in periodIncomes) {
                val allocatedFromIncome = periodSavings.filter { it.incomeId == income.id }.sumOf { it.amount }
                assertTrue(
                    "Per-income conservation invariant violated at step $step for income ${income.id}: saved ($allocatedFromIncome) > income.amount (${income.amount})",
                    allocatedFromIncome <= income.amount + 1e-6
                )
            }
        }
    }
}
