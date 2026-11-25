# 🛡️ Revisión Adversarial y Suite de Pruebas: Gastos Financiados con Ahorros y Saldo Neto Acumulado

## 1. Objetivo y Alcance
Diseñar y documentar pruebas automatizadas adversariales y basadas en propiedades (*Property-Based Testing*) orientadas a desafiar los límites de la lógica implementada para el registro de **gastos con cargo a categorías de ahorro acumulado** y el **resumen visual de saldo neto en ahorros**, sin alterar el código productivo.

### Archivos inspeccionados vía Git (`git status` / `git diff`)
- **Dominio y Modelos:**
  - `app/src/main/java/com/example/money_saver_android/domain/model/Expense.kt` (`ExpenseFundingSource`)
  - `app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt` (`SavingsCategoryBalance`, `calculateSavingsNetBalances`, `buildSavingsNetSummary`)
  - `app/src/main/java/com/example/money_saver_android/domain/usecase/ExpenseUseCase.kt` (`withValidatedSavingsExpense`, exclusión de gastos `SAVINGS` del resumen y desglose de presupuesto)
  - `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt` (`matchesBudgetGroup`)
  - `app/src/main/java/com/example/money_saver_android/domain/usecase/SavingsUseCase.kt` (`calculateNetSavingsSummary`, acceso global a ahorros y gastos)
- **Persistencia (Room / SQLite):**
  - `app/src/main/java/com/example/money_saver_android/data/local/ExpenseDatabase.kt` (`ExpenseEntity`, `AppDatabase` v6)
  - `app/src/main/java/com/example/money_saver_android/data/local/SavingsDatabase.kt` (`SavingDao.getAllSavings`)
  - `app/src/main/java/com/example/money_saver_android/data/repository/RoomExpenseRepository.kt`
  - `app/src/main/java/com/example/money_saver_android/data/repository/SavingsRepository.kt`
- **Gestión de Estado (ViewModels) e Integración:**
  - `app/src/main/java/com/example/money_saver_android/viewmodel/ExpenseViewModel.kt`
  - `app/src/main/java/com/example/money_saver_android/viewmodel/SavingsViewModel.kt`
  - `app/src/main/java/com/example/money_saver_android/MainActivity.kt`
- **Interfaz de Usuario (Jetpack Compose) e Internacionalización:**
  - `app/src/main/java/com/example/money_saver_android/ui/ExpensesView.kt`
  - `app/src/main/java/com/example/money_saver_android/ui/SavingsView.kt`
  - `app/src/main/java/com/example/money_saver_android/ui/components/expenses/AddEditExpenseDialog.kt`
  - `app/src/main/java/com/example/money_saver_android/ui/components/expenses/ExpenseCardItem.kt`
  - `app/src/main/assets/{de,en,es,pt}.json`

---

## 2. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: Sobregiro / Saldo Neto Negativo al Eliminar, Reducir o Cambiar de Categoría un Ahorro ya Consumido en Gastos
- **Severidad:** Crítica (Inconsistencia Financiera / Violación de Invariante de No-Sobregiro)
- **Ubicación:** `SavingsUseCase.kt` (líneas 160–251: `updateSaving` y `deleteSaving`)
- **Causa Raíz:** Aunque `MainActivity.kt` ahora inyecta `expenseRepository` en `SavingsUseCase`, ni `deleteSaving(id)` ni `updateSaving(...)` consultan `expenseRepository.getAllExpenses()` para verificar si existen gastos con `fundingSource == ExpenseFundingSource.SAVINGS` respaldados por esa categoría de ahorro.
- **Entradas reproducibles:**
  - Aporte histórico de `$500` en `"Emergencias"` (Período 1) + gasto financiado con ahorros de `$400` en `"Emergencias"` (Período 2), seguido de:
    1. `savingsUseCase.deleteSaving(savingId)`
    2. `savingsUseCase.updateSaving(..., amountInput = "200", categoryInput = "Emergencias")`
    3. `savingsUseCase.updateSaving(..., amountInput = "500", categoryInput = "Viajes")`
- **Resultado obtenido vs esperado:**
  - **Obtenido:** Las tres operaciones retornan `SavingsResult.Success`, dejando `$400` gastados contra `$0` o `$200` ahorrados (sobregiro real de `-$400` o `-$200`, enmascarado por `.max(BigDecimal.ZERO)` o por la desaparición de `"Emergencias"` cuando `validSavings` queda vacío).
  - **Esperado:** Retornar `SavingsResult.Error` impidiendo eliminar, reducir por debajo del acumulado gastado o mover de categoría fondos que ya respaldan gastos registrados.
- **Tests que exponen el fallo:**
  - `test_adversarial_delete_saving_backed_by_savings_expense_must_be_blocked`
  - `test_adversarial_update_saving_reducing_below_spent_or_changing_category_must_be_blocked`

---

### 🔴 Bug 2: Descuento Doble del Propio Gasto al Editar en `AddEditExpenseDialog` (`excludeExpenseId` nunca invocado)
- **Severidad:** Alta (Estado Inconsistente en Edición / Bloqueo Visual Engañoso)
- **Ubicación:** `CategorySummary.kt` (línea 91), `ExpenseViewModel.kt` (líneas 128–134) y `ExpensesView.kt` (líneas 301–316)
- **Causa Raíz:** `calculateSavingsNetBalances` declara el parámetro `excludeExpenseId: Long? = null` para excluir el gasto en edición al calcular el disponible, pero `ExpenseViewModel` calcula `savingsBalances` únicamente con `excludeExpenseId = null` y `ExpensesTabContent` pasa esa misma lista al diálogo de edición (`isEditing = true`).
- **Entradas reproducibles:** Ahorro de `$500` en `"Viajes"`, gasto registrado de `$350` financiado con `"Viajes"`, y apertura de edición (`startEditing(expense)`).
- **Resultado obtenido vs esperado:**
  - **Obtenido:** El selector muestra `"Viajes • $ 150.00"` (descontando los `$350` del propio gasto editado; si se hubiese gastado el 100%, muestra `"$ 0.00"`).
  - **Esperado:** Mostrar `"Viajes • $ 500.00"`, correspondiente al saldo real disponible para editar dicho gasto.
- **Test que expone el fallo:**
  - `test_adversarial_ui_edit_expense_dialog_shows_undiscounted_available_balance_for_edited_expense`

---

### 🔴 Bug 3: Categorías de Ahorro Agotadas (`$ 0.00`) Aparecen Seleccionables y Ocultan `"No hay ahorros disponibles"`
- **Severidad:** Alta (UX / Validación en Selector de Fuente de Fondos)
- **Ubicación:** `AddEditExpenseDialog.kt` (líneas 280–310) y `CategorySummary.kt` (líneas 108–126)
- **Causa Raíz:** `calculateSavingsNetBalances` devuelve todas las categorías con aportes históricos aunque su `netBalance` sea `0.0`. En `AddEditExpenseDialog`, `if (savingsBalances.isEmpty())` solo se cumple cuando jamás existió ningún ahorro en toda la historia de la aplicación.
- **Entradas reproducibles:** Categoría `"Emergencias"` con `$200` ahorrados en Período 1 y `$200` gastados (`netBalance == 0.0`), presupuesto activo en Período 2, abrir diálogo de nuevo gasto, elegir fuente **Ahorros** y abrir `selector_expense_saving_category`.
- **Resultado obtenido vs esperado:**
  - **Obtenido:** El mensaje `"No hay ahorros disponibles"` no se muestra; aparece `"Emergencias • $ 0.00"` habilitado y seleccionable, provocando fallo garantizado al guardar.
  - **Esperado:** Filtrar categorías agotadas (`netBalance <= 0.0`) al crear gastos nuevos y mostrar `"No hay ahorros disponibles"` cuando todas las categorías estén en cero.
- **Test que expone el fallo:**
  - `test_adversarial_ui_add_dialog_depleted_savings_shows_empty_message_and_dynamic_button_state`

---

### 🟡 Bug 4: Ausencia de Guarda de Concurrencia / Idempotencia en `ExpenseViewModel.confirmEdit()`
- **Severidad:** Media (Concurrencia en ViewModel / Doble Escritura)
- **Ubicación:** `ExpenseViewModel.kt` (líneas 377–413)
- **Causa Raíz:** Mientras `saveExpense()` verifica `if (isSavingExpense) return` y `SavingsViewModel.confirmEditSaving()` verifica `if (isSaving) return`, `ExpenseViewModel.confirmEdit()` carece de bandera de operación en vuelo.
- **Entradas reproducibles:** Doble toque rápido en el botón Guardar durante la edición de un gasto (`confirmEdit()` invocado dos veces antes de completar la corrutina).
- **Resultado obtenido vs esperado:**
  - **Obtenido:** Se ejecutan 2 actualizaciones concurrentes (`updateCallCount == 2`).
  - **Esperado:** Ejecutar `updateExpense` una única vez (`updateCallCount == 1`).
- **Test que expone el fallo:**
  - `test_adversarial_viewmodel_double_confirmEdit_must_be_idempotent`

---

### 🟡 Bug 5: Estado Residual en `fundingSourceInput` al Agotarse los Ahorros o al Cambiar de Período sin `budgetRepository`
- **Severidad:** Media (Máquina de Estados en `ExpenseViewModel`)
- **Ubicación:** `ExpenseViewModel.kt` (líneas 135–147 y 156–222)
- **Causa Raíz:**
  1. En el `combine` de `init`, si `hasActiveBudget == true` y el usuario tenía seleccionado `SAVINGS` (`"Emergencias"`), cuando los ahorros pasan a `$0.00` (`hasAvailableSavings = false`), `fundingSourceInput` no revierte a `BUDGET` ni limpia `savingCategoryInput`.
  2. En `onPeriodSelected(periodId)`, `fundingSourceInput` se reinicia incondicionalmente a `BUDGET`; cuando `budgetRepository == null`, `getBudgetsByPeriod` retorna `emptyFlow()` (no emite elementos), dejando `fundingSourceInput` en `BUDGET` pese a que `hasActiveBudget == false` y `hasAvailableSavings == true`.
- **Test que expone el fallo:**
  - `test_adversarial_viewmodel_funding_source_recovers_when_savings_deplete_or_period_switches_without_budget_repo`

---

### 🟡 Bug 6: Contaminación por `NaN` y Discrepancia de Redondeo en `ExpenseUseCase.calculateCategoryExpenses`
- **Severidad:** Media (Sanitización Numérica y Consistencia Visual entre Tarjetas)
- **Ubicación:** `ExpenseUseCase.kt` (líneas 342–370)
- **Causa Raíz:** A diferencia de `calculateExpenseCategorySummary` (que usa `buildPeriodCategorySummary` con filtro `isFinite() && > 0.0`, `sumBigDecimal()` y `.roundToInt()`), `calculateCategoryExpenses` suma directamente `budgetExpenses.sumOf { it.amount }` y trunca el porcentaje con `(progress * 100).toInt()`.
- **Entradas reproducibles:** `$200` en `"Comida"`, `$100` en `"Transporte"`, `$900` en ahorro (`SAVINGS`) y un registro corrupto `Double.NaN` (`BUDGET`).
- **Resultado obtenido vs esperado:**
  - **Obtenido:** `totalSpent` se vuelve `NaN` colapsando todas las categorías válidas a `0%` (y aún sin `NaN`, trunca `200/300` a `66%` mientras la tarjeta superior muestra `67%`).
  - **Esperado:** Ignorar entradas no finitas o inválidas y mantener consistencia de porcentaje (`67%`) con `PeriodSummaryCard`.
- **Test que expone el fallo:**
  - `test_adversarial_corrupted_nan_budget_expense_and_percentage_consistency_in_calculateCategoryExpenses`

---

## 3. Suite de Pruebas Implementada

Archivo creado:
- `app/src/test/java/com/example/money_saver_android/adversarial/SavingsFundedExpensesAdversarialTest.kt`

| Test Case | Categoría | Propiedad / Escenario Evaluado |
| :--- | :--- | :--- |
| `property_based_invariants_savings_net_balances_and_net_summary_500_iterations` | Property-Based Testing | **500 iteraciones aleatorias**: no-negatividad, finitud, conservación exacta `BigDecimal`, exclusión de saldos `0.0` en la dona, suma de proporciones `~1.0`, orden determinista y consolidación case/whitespace. |
| `property_based_invariant_budget_and_period_expense_isolation_from_savings_expenses` | Property-Based Testing | **300 escenarios multi-período**: invariabilidad total de presupuestos y resúmenes de gastos del período ante gastos `SAVINGS` con categorías colisionantes. |
| `property_based_stateful_ledger_invariant_random_save_and_update_operations_never_overdraw` | Stateful Property Test | **200 pasos aleatorios** de depósitos, gastos, ediciones y borrados en `ExpenseUseCase`: `spent <= saved` en cada paso. |
| `test_adversarial_delete_saving_backed_by_savings_expense_must_be_blocked` | Integridad Cruzada | **Expone Bug #1a**: bloqueo de borrado de ahorro respaldando gastos existentes. |
| `test_adversarial_update_saving_reducing_below_spent_or_changing_category_must_be_blocked` | Integridad Cruzada | **Expone Bug #1b/#1c**: bloqueo de reducción por debajo de lo gastado o cambio de categoría en `updateSaving`. |
| `test_adversarial_ieee754_cent_drift_and_localized_decimal_formats_on_savings_expenses` | Precisión Numérica | Deriva IEEE-754 (`0.10 + 0.20`), consumo del último centavo exacto y formatos `"1.234,56"` / `"1,234.56"`. |
| `test_adversarial_malformed_inputs_calendar_boundaries_overflow_and_missing_dependencies` | Valores Frontera | `savingsRepository = null`, `periodId` inválido, montos corruptos (`NaN`, `Infinity`, `-0.0`), fechas imposibles (`2026-02-29`, `24:00`) y `Double.MAX_VALUE` overflow. |
| `test_adversarial_update_expense_switching_funding_source_bidirectional_state_and_balance_integrity` | Dominio / Transiciones | Edición bidireccional `BUDGET -> SAVINGS -> BUDGET`, limpieza de metadatos y restauración exacta de saldos. |
| `test_adversarial_corrupted_nan_budget_expense_and_percentage_consistency_in_calculateCategoryExpenses` | Sanitización / Dominio | **Expone Bug #6**: resistencia a `NaN` y consistencia de redondeo entre tarjetas. |
| `test_adversarial_concurrent_20_coroutines_spending_shared_savings_never_overdraws` | Concurrencia (`Mutex`) | 20 corrutinas paralelas con `yield()` compitiendo por `$500`: exactamente 5 éxitos y 15 rechazos sin sobregiro. |
| `test_adversarial_viewmodel_double_confirmEdit_must_be_idempotent` | Concurrencia ViewModel | **Expone Bug #4**: idempotencia ante doble clic en `confirmEdit()`. |
| `test_adversarial_viewmodel_funding_source_recovers_when_savings_deplete_or_period_switches_without_budget_repo` | Ciclo de Vida ViewModel | **Expone Bug #5**: recuperación automática de `fundingSourceInput` al agotarse ahorros o cambiar de período. |
| `test_adversarial_room_persistence_legacy_nulls_corrupt_funding_source_and_extreme_unicode_roundtrip` | Persistencia Room | `AppDatabase` v6 + `SavingsDatabase` v2 con `fundingSource = null`, enums corruptos, espacios/casing mixto y Unicode/RTL/Emoji/SQL de +300 caracteres. |
| `test_adversarial_ui_edit_expense_dialog_shows_undiscounted_available_balance_for_edited_expense` | UI Compose | **Expone Bug #2**: saldo editable sin doble descuento en `AddEditExpenseDialog(isEditing = true)`. |
| `test_adversarial_ui_add_dialog_depleted_savings_shows_empty_message_and_dynamic_button_state` | UI Compose | **Expone Bug #3**: habilitación/deshabilitación reactiva de `btn_open_add_expense` y mensaje vacío cuando las categorías quedan en `$0.00`. |
| `test_adversarial_ui_expense_card_blank_saving_category_renders_clean_prefix_badge` | UI Compose | Fallback limpio `"Ahorro"` sin dos puntos colgantes en `ExpenseCardItem` cuando `savingCategory` está en blanco. |
| `test_adversarial_ui_savings_tab_preserves_cumulative_net_card_when_switching_to_empty_period` | UI Compose / Períodos | Al cambiar al Período 99 vacío, `card_savings_overview` muestra `$ 0.00` mientras `card_savings_net_overview` conserva `$ 450.00` acumulados. |

---

## 4. Instrucciones de Ejecución Local y CI/CD

```bash
# Ejecutar únicamente la nueva suite adversarial de gastos financiados con ahorros:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.SavingsFundedExpensesAdversarialTest"

# Ejecutar todas las suites adversariales del proyecto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```
