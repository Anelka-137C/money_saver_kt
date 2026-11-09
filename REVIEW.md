# 🛡️ Adversarial Code Review & Test Report: Story #8 - Definición y Modificación de Presupuesto por Categorías

**Target:** Últimos cambios correspondientes a la historia de usuario **#8: Definición y modificación de presupuesto por categorías a partir de ingresos**.  
**Archivos inspeccionados vía Git:**
- `app/src/main/java/com/example/money_saver_android/data/local/BudgetDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/BudgetRepository.kt`
- `app/src/main/java/com/example/money_saver_android/domain/model/Budget.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/BudgetUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/IncomeViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/FinanceTopTabs.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/budget/`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/IncomeAllocationCard.kt`
- `app/src/main/java/com/example/money_saver_android/ui/IncomesView.kt`

---

## 1. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: State Leakage y Limpieza Cruzada de Subcategorías en `IncomeViewModel.deleteBudgetSubcategory`
- **Severidad:** Alta (Inconsistencia de Estado en UI / Pérdida de Datos en Formulario)
- **Ubicación:** `IncomeViewModel.kt` (líneas 627-633):
  ```kotlin
  val shouldClear = current.budgetSubcategoryInput.equals(trimmedSub, ignoreCase = true)
  val shouldClearEdit = current.editingBudget?.subcategory?.equals(trimmedSub, ignoreCase = true) == true
  ```
- **Causa Raíz:** A diferencia de la implementación de ingresos (`deleteSubcategory`), la función de presupuesto no valida que la categoría padre (`categoryName`) coincida con `current.budgetCategoryInput` o `current.editingBudget?.category`.
- **Impacto:** Si la Categoría *A* ("Mercado") y la Categoría *B* ("Restaurantes") comparten una subcategoría con el mismo nombre ("Otros"), cuando el usuario elimina "Otros" en "Restaurantes", el ViewModel vacía erróneamente el formulario y la edición activa en "Mercado".

---

### 🔴 Bug 2: Evasión de Validación con `NaN` y Valores No Finitos en `BudgetUseCase.saveBudget`
- **Severidad:** Alta (Integridad de Datos / Corrupción Numérica Persistente)
- **Ubicación:** `BudgetUseCase.kt` (líneas 41-57):
  ```kotlin
  val amount = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0
  if (amount <= 0.0) return BudgetResult.Error(...)
  if (amount > available) return BudgetResult.Error(...)
  ```
- **Causa Raíz:** En Kotlin/Java, `"NaN".toDoubleOrNull()` retorna `Double.NaN`. Las comparaciones aritméticas con `NaN` retornan `false`:
  - `Double.NaN <= 0.0` $\rightarrow$ `false`
  - `Double.NaN > available` $\rightarrow$ `false`
- **Impacto:** Un input `"NaN"` burla las validaciones e inserta un presupuesto con `amount = NaN` en la base de datos. Cualquier sumatoria posterior (`budgets.sumOf { it.amount }`) se contamina permanentemente devolviendo `NaN`.

---

### 🔴 Bug 3: Condición de Carrera en Guardado Concurrente (Overbudgeting)
- **Severidad:** Alta (Concurrencia / Inconsistencia Financiera)
- **Ubicación:** `BudgetUseCase.saveBudget`
- **Causa Raíz:** La lectura del saldo disponible (`totalIncome - totalBudgeted`) y la posterior inserción en la base de datos no son una operación atómica ni están sincronizadas con un `Mutex`.
- **Impacto:** Dos peticiones concurrentes de \$400 cuando el saldo restante es de \$500 se aprueban simultáneamente, resultando en un total presupuestado de \$800 (\$300 por encima del ingreso real del período).

---

### 🟡 Bug 4: Actualización Ciega y Falso Éxito ante IDs Inexistentes en `updateBudget`
- **Severidad:** Media (Lógica Defensiva de Dominio)
- **Ubicación:** `BudgetUseCase.updateBudget`
- **Causa Raíz:** Si el ID del presupuesto no existe en el período, `currentBudget` es nulo, su monto se asume en `0.0` y la función procede a emitir `BudgetResult.Success` en lugar de reportar `"Presupuesto no encontrado"`.
- **Impacto:** El llamador asume que el presupuesto fue modificado cuando en realidad ninguna fila fue afectada en la base de datos.

---

### 🟡 Bug 5: Riesgo de Borrado en Cascada en Room por `OnConflictStrategy.REPLACE`
- **Severidad:** Media-Alta (Persistencia / Integridad Relacional)
- **Ubicación:** `BudgetDatabase.kt` & `BudgetRepository.kt`
- **Causa Raíz:** `BudgetCategoryEntity` tiene `@PrimaryKey val name: String` y `BudgetSubcategoryEntity` define `ForeignKey(onDelete = CASCADE)`. En Room, `@Insert(onConflict = OnConflictStrategy.REPLACE)` sobre la categoría padre ejecuta un `DELETE` interno seguido de un `INSERT`.
- **Impacto:** Si se reinserta una categoría existente para actualizar atributos como el color, SQLite puede disparar la eliminación en cascada de todas sus subcategorías asociadas.

---

## 2. Suite de Pruebas Adversariales Creada

Se ha creado la suite completa y autocontenida en:  
📁 `app/src/test/java/com/example/money_saver_android/adversarial/BudgetAdversarialTest.kt`

### Casos de prueba incluidos (15 escenarios):

| Test Case | Categoría | Invariante / Vulnerabilidad Evaluada |
| :--- | :--- | :--- |
| `test_adversarial_nan_and_infinity_amount_handling` | Casos Frontera Numéricos | Rechazo de `NaN`, `Infinity` y desbordamiento `1e309`. |
| `test_property_based_invariant_available_income_never_negative` | Property-Based Testing | Invariante matemático: `availableIncome >= 0.0` siempre (1,000 iteraciones aleatorias). |
| `test_property_based_invariant_total_budgeted_never_exceeds_total_income` | Property-Based Testing | Invariante de dominio: la suma presupuestada nunca sobrepasa los ingresos acumulados. |
| `test_adversarial_fractional_precision_and_exact_match_boundary` | Precisión IEEE 754 | Coincidencia fraccionaria exacta (33.33 + 33.33 + 33.34 = 100.00) y rechazo del centavo extra (+0.01). |
| `test_adversarial_period_isolation_under_modifications` | Aislamiento de Períodos | Garantiza que operaciones en el Período A no afecten los presupuestos del Período B. |
| `test_adversarial_null_period_id_fails_cleanly` | Robustez de Tipos | Validación y rechazo limpio cuando `periodId == null`. |
| `test_adversarial_update_non_existent_budget_id_exposure` | Lógica Defensiva | Exposición de la actualización de registros inexistentes sin validación de existencia. |
| `test_adversarial_category_and_subcategory_whitespace_and_duplicates` | Normalización de Strings | Trimming y rechazo de duplicados insensibles a mayúsculas/minúsculas y espacios. |
| `test_adversarial_prevent_deletion_of_category_with_active_budget` | Integridad Referencial | Bloqueo estricto del borrado de categorías con presupuestos activos vinculados. |
| `test_adversarial_prevent_deletion_of_subcategory_with_active_budget` | Integridad Referencial | Bloqueo del borrado de subcategorías con presupuestos y éxito en subcategorías libres. |
| `test_adversarial_viewmodel_subcategory_deletion_cross_category_leak` | State Leakage (ViewModel) | **Expone el Bug #1**: evita que borrar una subcategoría en Categoría B limpie la selección en Categoría A. |
| `test_adversarial_concurrent_budget_creation_overbudget_race_condition` | Concurrencia | **Expone el Bug #3**: evalúa condiciones de carrera en peticiones concurrentes de guardado. |
| `test_adversarial_room_database_foreign_key_cascade_on_category_replace` | Persistencia (Room) | **Expone el Bug #5**: verifica la supervivencia de subcategorías ante un REPLACE en la categoría padre. |
| `test_adversarial_amount_input_with_currency_symbols_and_punctuations` | Entradas Malformadas | Resiliencia ante símbolos de divisa (`$`, `€`), múltiples puntos decimales y letras. |
| `test_adversarial_viewmodel_null_period_clears_budget_and_income_state` | Ciclo de Vida | Reseteo completo a 0 y listas vacías al deseleccionar el período activo. |

---

## 3. Instrucciones de Ejecución

Siguiendo la restricción estricta de no ejecutar comandos de compilación/terminal desde este agente, puedes ejecutar la suite localmente mediante:

```bash
# Ejecutar únicamente las pruebas adversariales del módulo de presupuesto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.BudgetAdversarialTest"

# O ejecutar toda la suite adversarial del proyecto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```
