# 🛡️ Revisión Adversarial y Suite de Pruebas: Story #10 — Apartar Dinero hacia Ahorros Clasificados por Categoría

**Target:** Cambios correspondientes a la historia de usuario **#10: Apartar dinero de los ingresos hacia ahorros clasificados por categoría, persistencia en `SavingsDatabase`, descuento en ingreso disponible junto con presupuestos, e integración en Contabilidad (`FinanceTopTabs` y `SavingsView`)**.  
**Archivos inspeccionados vía Git:**
- `app/src/main/java/com/example/money_saver_android/MainActivity.kt`
- `app/src/main/java/com/example/money_saver_android/data/local/SavingsDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/SavingsRepository.kt`
- `app/src/main/java/com/example/money_saver_android/domain/model/Saving.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/SavingsUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/BudgetUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/SavingsViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/IncomeViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/ui/SavingsView.kt`
- `app/src/main/java/com/example/money_saver_android/ui/IncomesView.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/FinanceTopTabs.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/IncomeAllocationCard.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/savings/AddSavingDialog.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/savings/DeleteSavingCategoryConfirmationDialog.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/savings/DeleteSavingConfirmationDialog.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/savings/SavingListItem.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/savings/SavingsOverviewCard.kt`
- `app/src/main/assets/{de,en,es,pt}.json`

---

## 1. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: Ruptura de Conservación Financiera al Eliminar o Reducir un Ingreso Comprometido en Ahorros/Presupuestos
- **Severidad:** Crítica (Inconsistencia Financiera / Violación de Invariante de Conservación)
- **Ubicación:** `IncomeUseCase.kt` (líneas 167-228)
- **Causa Raíz:** `IncomeUseCase.updateIncome` y `IncomeUseCase.deleteIncome` modifican o borran el ingreso en `IncomeRepository` sin verificar si ya existen ahorros vinculados a su `incomeId` en `SavingsRepository` o si el nuevo `totalIncome` del período queda por debajo de `totalBudgeted + totalSaved`.
- **Impacto:** Si un ingreso de `$1,000` ya tiene `$600` apartados en ahorros y `$300` en presupuestos, y el usuario edita el ingreso a `$400` o lo elimina, el período queda sobre-asignado (`totalBudgeted + totalSaved > totalIncome`) y los ahorros quedan huérfanos apuntando a un `incomeId` inexistente o con monto menor al ahorrado.

---

### 🔴 Bug 2: Condición de Carrera Cruzada entre `BudgetUseCase` y `SavingsUseCase` (Doble `Mutex` Independiente)
- **Severidad:** Crítica (Concurrencia / Sobre-asignación de Ingreso Disponible)
- **Ubicación:** `BudgetUseCase.kt` (línea 22) y `SavingsUseCase.kt` (línea 30)
- **Causa Raíz:** `BudgetUseCase` protege sus validaciones con `budgetMutex` y `SavingsUseCase` con `savingMutex` (dos instancias distintas e independientes de `Mutex`).
- **Impacto:** Si se invocan concurrentemente `saveBudget("700")` y `saveSaving("700")` sobre un período con `$1,000` de ingreso disponible, ambas corrutinas leen `available = 1000.0` antes de que la otra inserte y ambas tienen éxito, comprometiendo `$1,400` sobre `$1,000` de ingresos.

---

### 🔴 Bug 3: Violación de `ForeignKey` y Bypass de Protección de Borrado por Case-Sensitivity en `SavingsDatabase` (Room / SQLite)
- **Severidad:** Alta (Persistencia / Integridad Referencial en SQLite)
- **Ubicación:** `SavingsDatabase.kt` (líneas 49-79) y `SavingsUseCase.kt` (líneas 264-304)
- **Causa Raíz:**
  1. `SavingsUseCase.createSubcategory(categoryName, subcategoryNameInput)` busca `target` con `equals(..., ignoreCase = true)`, pero inserta en Room usando el parámetro crudo `categoryName` en vez de `target.name`. En SQLite, `FOREIGN KEY(categoryName) REFERENCES saving_category(name)` es **case-sensitive**, lanzando `SQLiteConstraintException` si difiere en mayúsculas/minúsculas o espacios laterales.
  2. `SavingDao.countSavingsForCategory` y `countSavingsForSubcategory` comparan con `=` sin `COLLATE NOCASE`, permitiendo eliminar categorías o subcategorías con ahorros asociados si el nombre se pasa con distinto casing.
- **Impacto:** Excepción no controlada (`SQLiteConstraintException`) al crear subcategorías con variación de mayúsculas/espacios y eliminación indebida de categorías en uso.

---

### 🔴 Bug 4: Falso Rechazo por Deriva de Punto Flotante IEEE 754 al Apartar el Saldo Exacto en Centavos
- **Severidad:** Alta (Precisión Numérica / Bloqueo de Ahorro Válido)
- **Ubicación:** `SavingsUseCase.kt` (líneas 95-104 y 176-187) y `BudgetUseCase.kt` (líneas 61-69 y 109-116)
- **Causa Raíz:** En aritmética binaria IEEE 754 (`Double`), `0.10 + 0.20 = 0.30000000000000004`, por lo que `1.00 - (0.10 + 0.20) = 0.69999999999999995559`. Al evaluar `if (amount > incomeRemaining)` o `if (amount > available)` con `amount = 0.70`, la comparación `0.70 > 0.69999999999999995559` evalúa a `true`.
- **Impacto:** El usuario intenta apartar exactamente los `$0.70` restantes de su ingreso de `$1.00` y el sistema rechaza la operación con `"El monto supera el disponible del ingreso seleccionado"`.

---

### 🟡 Bug 5: Fuga de Estado del Formulario y Diálogo de Eliminación de Categoría al Cambiar de Período en `SavingsViewModel`
- **Severidad:** Media (Ciclo de Vida / Fuga de Estado entre Períodos)
- **Ubicación:** `SavingsViewModel.kt` (líneas 95-120)
- **Causa Raíz:** `onPeriodSelected` limpia `savingIncomeIdInput`, `savingErrorMessage`, `editingSaving` y `savingToDelete`, pero **no resetea** `savingAmountInput`, `savingCategoryInput`, `savingSubcategoryInput`, `savingDateTimeInput`, `savingNoteInput` ni `savingCategoryToDelete`.
- **Impacto:** Los datos ingresados en el formulario o un diálogo de confirmación de borrado de categoría del Período 1 permanecen activos al cambiar al Período 2.

---

### 🟡 Bug 6: Inflación Silenciosa `x100` con Coma Decimal (`"10,50"` -> `1050.0`) y Truncamiento IEEE 754 en Porcentajes de UI (`"28%"` / `"56%"`)
- **Severidad:** Media (Validación de Entrada / Precisión en UI Compose)
- **Ubicación:** `SavingsUseCase.kt` (líneas 66 y 142), `IncomeAllocationCard.kt` (líneas 48-51) y `SavingsOverviewCard.kt` (líneas 56-57)
- **Causa Raíz:**
  1. `amountInput.replace(",", "").toDoubleOrNull()` elimina la coma antes de parsear, convirtiendo `"10,50"` (coma decimal) en `1050.0`.
  2. En `IncomeAllocationCard` y `SavingsOverviewCard`, `((29.0 / 100.0) * 100).toInt()` produce `28` (`"28%"`) y `((57.0 / 100.0) * 100).toInt()` produce `56` (`"56%"`) debido a que en IEEE 754 `0.29 * 100.0 == 28.999999999999996` y `0.57 * 100.0 == 56.99999999999999`.
- **Impacto:** Multiplicación por 100 de montos con coma decimal y porcentajes visualmente truncados en las tarjetas de resumen.

---

## 2. Suite de Pruebas Adversariales Creada

Se ha creado la suite completa y autocontenida en:  
📁 `app/src/test/java/com/example/money_saver_android/adversarial/SavingsAllocationAdversarialTest.kt`

### Casos de prueba incluidos (12 escenarios):

| Test Case | Categoría | Invariante / Vulnerabilidad Evaluada |
| :--- | :--- | :--- |
| `test_adversarial_income_delete_or_reduce_below_committed_savings_and_budgets_breaks_conservation_invariant` | Integridad Relacional / Dominio | **Expone Bug #1**: bloqueo de reducción o borrado de ingresos ya comprometidos en ahorros/presupuestos. |
| `test_adversarial_cross_usecase_concurrent_budget_and_saving_race_condition_overallocates_income` | Concurrencia Cruzada | **Expone Bug #2**: condición de carrera concurrente entre `BudgetUseCase.saveBudget` y `SavingsUseCase.saveSaving`. |
| `test_adversarial_room_savings_database_case_insensitive_foreign_key_and_delete_protection` | Persistencia Room (`SavingsDatabase`) | **Expone Bug #3**: prueba SQLite/Room real con `createSubcategory`, `deleteCategory` y `deleteSubcategory` con variaciones de mayúsculas/espacios. |
| `test_adversarial_ieee754_floating_point_drift_falsely_rejects_exact_remaining_cents` | Precisión Numérica IEEE 754 | **Expone Bug #4**: apartar `$0.10 + $0.20 + $0.70` exactos sobre un ingreso de `$1.00`. |
| `test_adversarial_savings_viewmodel_period_switch_leaks_form_inputs_and_category_delete_dialog` | Ciclo de Vida / ViewModel | **Expone Bug #5**: limpieza completa de estado temporal al cambiar de período en `SavingsViewModel`. |
| `test_adversarial_savings_viewmodel_concurrent_double_save_and_double_delete_idempotency` | Concurrencia / Idempotencia | Idempotencia ante doble clic concurrente en `saveSaving()` en `SavingsViewModel`. |
| `test_adversarial_savings_usecase_decimal_comma_inflation_and_strict_calendar_boundaries` | Valores Frontera / Validación | **Expone Bug #6 (Coma decimal)** y verifica fronteras de calendario (`2026-02-29`, `2026-04-31`, `24:00`, `NaN`, `Infinity`, `-0.0`). |
| `test_adversarial_ui_income_allocation_and_savings_overview_ieee754_percentage_truncation` | UI Compose / IEEE 754 | **Expone Bug #6 (Porcentajes UI)**: exige `"29%"` y `"57%"` sin truncamiento binario en `IncomeAllocationCard` y `SavingsOverviewCard`. |
| `test_adversarial_ui_infinite_amounts_nan_progress_and_malformed_hex_color_do_not_crash` | UI Compose (Frontera) | Resiliencia de Compose ante `Double.POSITIVE_INFINITY` (`progress = Float.NaN`) y colores hex corruptos. |
| `test_adversarial_incomes_view_cross_tab_savings_allocation_and_period_switch_synchronization` | Integración UI (`IncomesView`) | Sincronización reactiva entre `INCOMES`, `BUDGET` y `SAVINGS` y cambio de período en `IncomesView`. |
| `test_adversarial_savings_tab_error_recovery_and_unicode_payloads_in_list_item` | UI Compose (`SavingsTabContent`) | Limpieza de `savingErrorMessage` al cerrar y reabrir `AddSavingDialog` en `SavingsTabContent`. |
| `test_property_based_invariant_savings_and_budgets_conservation_and_source_income_bounds` | Property-Based Testing | **500 iteraciones aleatorias + matriz de extremos numéricos**: invariantes de conservación global (`totalBudgeted + totalSaved <= totalIncome`), límite por ingreso origen, aislamiento de períodos y no-`NaN`. |

---

## 3. Instrucciones de Ejecución Local

Siguiendo la restricción estricta de no ejecutar comandos de compilación/terminal desde este agente, puedes ejecutar la suite localmente o en CI/CD mediante:

```bash
# Ejecutar la nueva suite adversarial de Story #10:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.SavingsAllocationAdversarialTest"

# O ejecutar todas las suites adversariales del proyecto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```
