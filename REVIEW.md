# 🛡️ Revisión Adversarial y Suite de Pruebas: Story #9 — Condicionamiento de Gastos a Presupuestos Activos

**Target:** Cambios correspondientes a la historia de usuario **#9: Registro de gastos condicionado a presupuestos activos en el período, integración en Contabilidad (`FinanceTopTabs`), selectores separados de presupuesto origen y categoría, tarjetas de resumen/distribución y migración Room v5**.  
**Archivos inspeccionados vía Git:**
- `app/src/main/java/com/example/money_saver_android/MainActivity.kt`
- `app/src/main/java/com/example/money_saver_android/data/local/ExpenseDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/ExpenseRepository.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/RoomExpenseRepository.kt`
- `app/src/main/java/com/example/money_saver_android/domain/model/Expense.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/ExpenseUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/ExpenseViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/IncomeViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/ui/MainContainer.kt`
- `app/src/main/java/com/example/money_saver_android/ui/IncomesView.kt`
- `app/src/main/java/com/example/money_saver_android/ui/ExpensesView.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/budget/BudgetOverviewCard.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/expenses/AddEditExpenseDialog.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/expenses/ExpenseAllocationCard.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/expenses/ExpenseOverviewCard.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/FinanceTopTabs.kt`
- `app/src/main/assets/{de,en,es,pt}.json`

---

## 1. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: Desincronización entre Presupuesto y Gastos al Cambiar de Período en Contabilidad
- **Severidad:** Crítica (Inconsistencia Financiera entre Pestañas / Datos Erróneos en UI)
- **Ubicación:** `MainActivity.kt` (líneas 159-168), `IncomesView.kt` (líneas 162-175), `IncomeViewModel.kt` (líneas 693-719)
- **Causa Raíz:** `MainActivity` pasa `selectedPeriodId = periodState.activePeriod?.id` (que permanece fijo en el período activo inicial) y el callback `onPeriodSelected` solo actualiza `incomeViewModel` y `viewModel` (`ExpenseViewModel`). Sin embargo, `IncomesView` calcula `val activePeriod = allPeriods.find { it.id == selectedPeriodId } ?: allPeriods.firstOrNull()` usando el parámetro fijo en lugar de `state.selectedPeriodId`, y `IncomeViewModel` pasa `periodId = activePeriod?.id ?: current.selectedPeriodId` en `calculateCategoryBudgets` y `calculateTotalBudgetSpent`.
- **Impacto:** Si el usuario cambia al Período 2 desde el selector superior de `Contabilidad`, la pestaña **Gastos** muestra los gastos del Período 2, `IncomeViewModel` carga los presupuestos del Período 2, pero la pestaña **Presupuesto** evalúa el gasto usando el `id` del Período 1. Como resultado, todos los gastos del Período 2 se ignoran en **Presupuesto** (`Gastado = $0.00`), contradiciendo el total de la pestaña **Gastos**.

---

### 🔴 Bug 2: Renombrar la Categoría de un Presupuesto Rompe la Deducción y Bloquea Ediciones del Gasto
- **Severidad:** Crítica (Corrupción de Vínculo Relacional / Bloqueo de Edición)
- **Ubicación:** `IncomeUseCase.kt` (líneas 418-425) y `ExpenseUseCase.kt` (líneas 119-121)
- **Causa Raíz:** Cada `Expense` persiste `budgetId` junto a una copia desnormalizada `budgetCategory`. Cuando el usuario edita un presupuesto existente cambiando su categoría (por ejemplo, de `"Food & Dining"` a `"Groceries"` manteniendo el mismo `id`):
  1. `Expense.matchesBudgetGroup` exige coincidencia simultánea de `budgetCategory.trim().equals(categoryName, ignoreCase = true)` y `catBudgets.any { it.id == budgetId }`, por lo que el gasto deja de descontarse en `calculateTotalBudgetSpent` y `calculateCategoryBudgets`.
  2. `ExpenseUseCase` (`periodBudgets.find { it.id == budgetId && it.category.trim().equals(trimmedBudgetCategory, ignoreCase = true) }`) rechaza futuras ediciones de ese gasto con `"El presupuesto seleccionado no corresponde al período activo"`.
- **Impacto:** Editar la categoría de un presupuesto vuelve invisibles los gastos ya vinculados a él e impide modificar o corregir dichos gastos después.

---

### 🔴 Bug 3: Fuga y Divergencia de Gastos Legacy (`periodId = 0L`) cuando `periodStartDate == null`
- **Severidad:** Alta (Aislamiento de Períodos / Discrepancia entre Pestañas)
- **Ubicación:** `IncomeUseCase.kt` (líneas 406-416), `IncomeViewModel.kt`, `ExpenseViewModel.kt`
- **Causa Raíz:** En `Expense.matchesPeriod`:
  ```kotlin
  if (this.periodId > 0L && periodId != null) {
      return this.periodId == periodId
  }
  return periodStartDate == null || periodEndDate == null ||
      (date >= periodStartDate && date <= periodEndDate)
  ```
  1. Cuando `expense.periodId <= 0L` y `periodStartDate == null` o `periodEndDate == null`, retorna `true` incondicionalmente para cualquier `periodId`.
  2. `IncomeViewModel` recolecta todos los gastos (`getExpenses()`), mientras `ExpenseViewModel` consulta `getExpensesByPeriod(periodId)`. Un gasto legacy con `periodId = 0L` dentro del rango de fechas del Período 1 se descuenta en `BudgetOverviewCard`, pero no existe en `ExpenseOverviewCard`.
- **Impacto:** Contaminación silenciosa de cálculos entre períodos sin rango de fechas explícito y totales inconsistentes entre **Presupuesto** y **Gastos**.

---

### 🔴 Bug 4: Fuga de Estado en `ExpenseViewModel` al Cambiar de Período, `init` Expuesto y Deselección a `null`
- **Severidad:** Alta (State Leakage / Inconsistencia de Ciclo de Vida)
- **Ubicación:** `ExpenseViewModel.kt` (líneas 74-78 y 89-135), `IncomesView.kt` (líneas 168-171)
- **Causa Raíz:**
  1. En `init`, `ExpenseViewModel` recolecta `expenseUseCase.getExpenses()` antes de seleccionar ningún período, exponiendo gastos de todos los períodos cuando `selectedPeriodId == null`.
  2. En `onPeriodSelected`, el ViewModel resetea `expenses`, `budgets`, `hasActiveBudget`, `totalBudgeted`, `totalSpent`, `budgetIdInput`, `budgetCategoryInput` y `budgetSubcategoryInput`, pero **no limpia** `errorMessage`, `editingExpense`, `expenseToDelete`, `amountInput`, `categoryInput`, `subcategoryInput` ni `descriptionInput`.
  3. En `IncomesView`, `LaunchedEffect(state.selectedPeriodId)` solo llama `expenseViewModel?.onPeriodSelected(state.selectedPeriodId)` cuando `state.selectedPeriodId != null`; si el período activo pasa a `null`, `ExpenseViewModel` retiene el período anterior.
- **Impacto:** Un error o diálogo de edición del Período A permanece activo al cambiar al Período B (con riesgo de guardar o mostrar mensajes falsos de otro período).

---

### 🔴 Bug 5: Condición de Carrera por Doble Envío en `ExpenseViewModel.saveExpense`
- **Severidad:** Alta (Concurrencia / Duplicación de Gastos)
- **Ubicación:** `ExpenseViewModel.kt` (líneas 305-338)
- **Causa Raíz:** `saveExpense()` lee una instantánea de `_uiState.value` y lanza una corrutina sin `Mutex` ni bandera `isSaving`, limpiando los campos del formulario únicamente cuando finaliza `ExpenseResult.Success`.
- **Impacto:** Dos pulsaciones rápidas concurrentes sobre Guardar antes de terminar la inserción persisten gastos duplicados.

---

### 🟡 Bug 6: Persistencia de Errores Residuales al Cerrar `AddEditExpenseDialog` y Cambiar de Categoría en `ExpenseCategorySheet`
- **Severidad:** Media (Estado de UI / UX)
- **Ubicación:** `ExpensesView.kt` (línea 276 y líneas 335-360)
- **Causa Raíz:**
  1. `onCloseClick = { showAddDialog = false }` cierra el diálogo sin limpiar `state.errorMessage` (`ExpenseViewModel` carece de `clearErrorMessage()`), por lo que al reabrir el diálogo aparece el error anterior.
  2. `ExpenseCategorySheet` invoca `CategorySelectionSheet` sin pasar `onClearSubcategoryError = viewModel::clearSubcategoryError`; un error al crear una subcategoría en la categoría *A* persiste al volver atrás y entrar en la categoría *B*.

---

### 🟡 Bug 7: Validación de Entradas, Desbordamiento a `NaN`, Precisión en `startEditing` y Truncamiento IEEE 754
- **Severidad:** Media (Integridad Numérica / UI / Lógica Defensiva)
- **Ubicación:** `ExpenseUseCase.kt` (líneas 64-86, 109, 164), `ExpenseViewModel.kt` (líneas 165-172), `ExpenseOverviewCard.kt` (líneas 54-59), `IncomeUseCase.kt`
- **Causa Raíz:**
  1. `ExpenseUseCase` (`línea 109`) ejecuta `amountInput.replace(",", "").toDoubleOrNull() ?: 0.0`, convirtiendo silenciosamente `"10,50"` (coma decimal) en `1050.0`.
  2. `ExpenseUseCase.updateExpense` (`líneas 64-86`) no verifica si el `id` existe en repositorio ni valida el formato de `dateInput` (en creación o edición).
  3. Cuando dos montos finitos grandes (`1e308 + 1e308`) desbordan a `Double.POSITIVE_INFINITY`, las divisiones `spent / totalSpent` (`ExpenseUseCase.kt` línea 164) y `remaining / totalBudget` (`IncomeUseCase.kt` línea 371) producen `Double.NaN`, y `Float.NaN.coerceIn(0f, 1f)` devuelve `Float.NaN`.
  4. `ExpenseViewModel.startEditing` (`líneas 165-172`) usa `if (expense.amount % 1.0 == 0.0) expense.amount.toLong().toString() else expense.amount.toString()`, saturando `1e20` a `Long.MAX_VALUE` y emitiendo notación científica (`"1.0E-4"`) para `0.0001`.
  5. En `ExpenseOverviewCard` (`líneas 54-59`), `((totalSpent / totalBudgeted) * 100).toInt()` trunca por precisión binaria IEEE 754: `(29.0 / 100.0) * 100.0 == 28.999999999999996`, renderizando `"28%"` en lugar de `"29%"`.

---

### 🟡 Bug 8: Divergencia en Room v5 para `getExpensesByPeriod(0L)` (`NULL` vs `= 0`)
- **Severidad:** Media (Persistencia / Contrato de Repositorio)
- **Ubicación:** `RoomExpenseRepository.kt` (líneas 171-181) y `ExpenseDatabase.kt` (líneas 71-72)
- **Causa Raíz:** `toEntity()` mapea `periodId = periodId.takeIf { it > 0L }` guardando `NULL` cuando `periodId <= 0L`, mientras `ExpenseDao.getByPeriod(periodId)` ejecuta `SELECT * FROM expenses WHERE periodId = :periodId ORDER BY id DESC`. En SQLite, `NULL = 0` evalúa a falso, por lo que `roomRepo.getExpensesByPeriod(0L)` retorna 0 registros, divergiendo de la implementación por defecto de `ExpenseRepository.getExpensesByPeriod(0L)` (`filter { it.periodId == periodId }`).

---

## 2. Suite de Pruebas Adversariales Creada

Se ha creado la suite completa y autocontenida en:  
📁 `app/src/test/java/com/example/money_saver_android/adversarial/BudgetConditionedExpensesAdversarialTest.kt`

### Casos de prueba incluidos (15 escenarios):

| Test Case | Categoría | Invariante / Vulnerabilidad Evaluada |
| :--- | :--- | :--- |
| `test_adversarial_incomes_view_period_switch_desyncs_budget_spent_and_remaining_from_expenses_tab` | Integración UI (`IncomesView`) | **Expone Bug #1**: al cambiar al Período 2, `Presupuesto` y `Gastos` deben coincidir en gastado y restante. |
| `test_adversarial_budget_category_rename_breaks_expense_deduction_and_subsequent_edits` | Integridad Relacional / Dominio | **Expone Bug #2**: renombrar la categoría de un `Budget` no debe ocultar sus gastos ni bloquear futuras ediciones. |
| `test_adversarial_legacy_zero_period_expenses_diverge_between_budget_and_expenses_tabs_and_leak_across_null_dates` | Aislamiento / Legacy | **Expone Bug #3**: evita fuga de gastos `periodId = 0L` con fechas nulas y divergencia entre `IncomeViewModel` y `ExpenseViewModel`. |
| `test_adversarial_expense_viewmodel_period_switch_leaks_error_and_editing_state_and_init_exposes_all_periods` | Ciclo de Vida / ViewModel | **Expone Bug #4**: evita exposición global en `init` y fuga de `errorMessage`/`editingExpense` al cambiar de período. |
| `test_adversarial_expense_viewmodel_concurrent_double_submit_inserts_duplicate_expenses` | Concurrencia / Idempotencia | **Expone Bug #5**: dos llamadas concurrentes a `saveExpense()` deben persistir un único gasto. |
| `test_adversarial_expenses_tab_closing_add_dialog_clears_stale_error_message` | UI Compose (`ExpensesTabContent`) | **Expone Bug #6 (Caso A)**: cerrar y reabrir `AddEditExpenseDialog` debe limpiar errores residuales. |
| `test_adversarial_expenses_tab_category_sheet_leaks_subcategory_error_across_categories` | UI Compose (`ExpenseCategorySheet`) | **Expone Bug #6 (Caso B)**: `subcategoryError` de una categoría no debe filtrarse hacia otra al navegar atrás. |
| `test_adversarial_expense_usecase_numeric_comma_locale_nonfinite_update_id_and_date_validation` | Valores Frontera / Validación | **Expone Bug #7 (Entradas)**: rechazo de `NaN`/`Infinity`/`0`/negativos, período inválido/cruzado, coma decimal `"10,50"`, fechas inválidas e `id` inexistente en `updateExpense`. |
| `test_adversarial_overflow_double_infinity_and_start_editing_scientific_notation_and_precision_loss` | Desbordamiento y Precisión | **Expone Bug #7 (`NaN` / `startEditing`)**: evita `progress = Float.NaN`, saturación a `Long.MAX_VALUE` (`1e20`) y notación científica (`0.0001`). |
| `test_adversarial_expense_overview_over_budget_and_allocation_malformed_hex_unicode` | UI Compose (Frontera) | Sobregiro (`250 / 100 -> 250%`, restante `$0.00`), color hex corrupto (`"NOT_A_HEX"`), icono vacío y Unicode/emojis extremos. |
| `test_adversarial_expense_overview_ieee754_percent_truncation` | UI Compose / IEEE 754 | **Expone Bug #7 (Porcentaje)**: exige `"29%"` en lugar de `"28%"` para `29.0 / 100.0`. |
| `test_adversarial_room_v5_expense_period_and_budget_roundtrip_isolation_and_zero_period_query` | Persistencia Room (`AppDatabase` v5) | **Expone Bug #8**: roundtrip SQLite real de `periodId`/`budgetId`/`budgetCategory`, aislamiento por período, actualización de vínculo y consulta `periodId = 0L`. |
| `test_adversarial_navigation_and_accounting_tabs_state_preservation_and_null_period_sync` | Navegación / i18n / Sincronización | **Expone Bug #4 (`null` sync)** y verifica ausencia de `tab_expenses` en barra inferior, cambio de idioma preservando `FinanceTopTabs` en `EXPENSES`. |
| `test_adversarial_expenses_tab_reactively_blocks_save_and_disables_button_when_last_budget_deleted` | Reactividad / Concurrencia UI | Eliminar el último presupuesto mientras el diálogo está abierto bloquea el guardado, muestra banner sin presupuesto y deshabilita `btn_open_add_expense`. |
| `test_property_based_invariant_budget_conditioned_expenses_and_remaining_balances` | Property-Based Testing | **500 iteraciones aleatorias**: invariantes de agrupación por categoría principal, aislamiento por `periodId`, deducción por `budgetId`/`budgetCategory`, `progress` en `[0f, 1f]` y saldos restantes `>= 0.0`. |

---

## 3. Instrucciones de Ejecución Local

Siguiendo la restricción estricta de no ejecutar comandos de compilación/terminal desde este agente, puedes ejecutar la suite localmente o en CI/CD mediante:

```bash
# Ejecutar la nueva suite adversarial de Story #9:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.BudgetConditionedExpensesAdversarialTest"

# O ejecutar todas las suites adversariales del proyecto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```
