# 🛡️ Revisión Adversarial y Suite de Pruebas: Subcategoría Opcional en Gastos, Ingresos y Presupuestos

**Target:** Cambios correspondientes a la funcionalidad de **subcategoría opcional** en Gastos, Ingresos y Presupuestos.  
**Archivos inspeccionados vía Git:**
- `app/src/main/java/com/example/money_saver_android/data/local/BudgetDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/local/ExpenseDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/local/IncomesDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/BudgetRepository.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/IncomeRepository.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/RoomExpenseRepository.kt`
- `app/src/main/java/com/example/money_saver_android/domain/model/Expense.kt`
- `app/src/main/java/com/example/money_saver_android/domain/model/Income.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/ExpenseUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/ui/CategorySelectionSheet.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/expenses/ExpenseCardItem.kt`
- `app/src/main/java/com/example/money_saver_android/ui/components/incomes/DeleteIncomeConfirmationDialog.kt`

---

## 1. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: Evasión de Validación Numérica con `NaN` e `Infinity` en `ExpenseUseCase` e `IncomeUseCase`
- **Severidad:** Alta (Corrupción de Datos Financieros)
- **Ubicación:** `ExpenseUseCase.kt` (líneas 26-29, 54-57) e `IncomeUseCase.kt` (líneas 151-154, 186-189)
- **Causa Raíz:** A diferencia de `BudgetUseCase.kt` (que valida `amount.isNaN() || amount.isInfinite() || amount <= 0.0`), tanto `ExpenseUseCase` como `IncomeUseCase` solo verifican `if (amount <= 0.0)`. En IEEE 754:
  - `"NaN".toDoubleOrNull()` retorna `Double.NaN`, y `Double.NaN <= 0.0` evalúa a `false`.
  - `"Infinity".toDoubleOrNull()` o `"1e309".toDoubleOrNull()` retorna `Double.POSITIVE_INFINITY`, y `Infinity <= 0.0` evalúa a `false`.
- **Impacto:** Permite persistir gastos e ingresos sin subcategoría con montos `NaN` o `Infinity`, contaminando permanentemente `totalIncome`, `availableIncome` y el cálculo de presupuestos por categoría (`calculateCategoryBudgets`).

---

### 🔴 Bug 2: Texto de Categoría Duplicado en `ExpenseCardItem` bajo Condiciones de Frontera
- **Severidad:** Media (Defecto Visual / UI)
- **Ubicación:** `ExpenseCardItem.kt` (líneas 90-106)
- **Causa Raíz:**
  ```kotlin
  Text(text = expense.subcategory.ifBlank { expense.category.ifBlank { "Gasto" } })
  val subtitle = when {
      expense.description.isNotBlank() -> expense.description
      expense.subcategory.isNotBlank() -> expense.category
      else -> ""
  }
  ```
  1. Si `expense.subcategory == expense.category` y `expense.description` está vacío, el título muestra `subcategory` y el subtítulo muestra `category` (a diferencia de `DeleteIncomeConfirmationDialog.kt` e `IncomeListItem`, que verifican `subcategory != category`).
  2. Si el gasto se registra sin subcategoría (`subcategory = ""`) y el usuario escribe en `description` el mismo nombre de la categoría (`description == category`), tanto el título (fallback a `category`) como el subtítulo (`description`) renderizan el mismo texto dos veces.

---

### 🟡 Bug 3: Comparación Literal sin `.trim()` en `DeleteIncomeConfirmationDialog`
- **Severidad:** Baja-Media (UI / Normalización)
- **Ubicación:** `DeleteIncomeConfirmationDialog.kt` (línea 114)
- **Causa Raíz:** `if (income.subcategory.isNotBlank() && income.subcategory != income.category)` compara cadenas sin `.trim()` ni `ignoreCase = true`. Si `subcategory` tiene un espacio circundante (`"Freelance "`) respecto a `category` (`"Freelance"`), se renderizan ambas líneas.

---

### 🔴 Bug 4: Ruptura de Integridad Referencial al Eliminar Categorías cuando `categoryInput` Contiene Espacios
- **Severidad:** Alta (Integridad Referencial en Dominio / Persistencia)
- **Ubicación:** `IncomeUseCase.kt` (líneas 164-173 vs líneas 116-127)
- **Causa Raíz:** `saveIncome` y `updateIncome` guardan `categoryInput` sin aplicar `.trim()`, mientras que `deleteCategory` aplica `val trimmedCategory = categoryName.trim()` antes de consultar `repository.countIncomesForCategory(trimmedCategory)`.
- **Impacto:** Si un ingreso sin subcategoría se guarda con `categoryInput = "  Employment  "`, `countIncomesForCategory("Employment")` devuelve `0` y permite eliminar la categoría `"Employment"` dejando ingresos huérfanos.

---

### 🟡 Bug 5: Inconsistencia de Normalización de `subcategoryInput` en Blanco entre Caso de Uso y Repositorio Room
- **Severidad:** Media (Invariante de Dominio)
- **Ubicación:** `ExpenseUseCase.kt` (líneas 34-42), `IncomeUseCase.kt` (líneas 164-173), `RoomExpenseRepository.kt` (línea 33)
- **Causa Raíz:** Los repositorios Room transforman `subcategory.takeIf { it.isNotBlank() }` a `null` y al leer devuelven `""`. Sin embargo, `ExpenseUseCase` e `IncomeUseCase` no normalizan `subcategoryInput.trim()`, por lo que una entrada `"   \t\n  "` persiste en memoria como `"   \t\n  "` en lugar de `""`.

---

### 🔴 Bug 6: Destrucción en Cascada de Subcategorías en `IncomesDatabase` por `OnConflictStrategy.REPLACE`
- **Severidad:** Alta (Pérdida de Datos en SQLite / Room)
- **Ubicación:** `IncomesDatabase.kt` (líneas 37-47 y 91-92)
- **Causa Raíz:** `IncomeSubcategoryEntity` define `ForeignKey(onDelete = ForeignKey.CASCADE)` hacia `IncomeCategoryEntity`, y `IncomeCategoryDao.insertCategory` utiliza `@Insert(onConflict = OnConflictStrategy.REPLACE)`. A diferencia de `BudgetRepository.kt` (que usa `OnConflictStrategy.IGNORE` seguido de `updateCategory`), reinsertar una categoría existente en `RoomIncomeRepository.insertCategory` ejecuta un `DELETE` previo en SQLite que borra en cascada todas sus subcategorías.

---

### 🟡 Bug 7: `ExpenseViewModel` No Limpia `errorMessage` al Seleccionar Categoría Sin Subcategoría
- **Severidad:** Media (Estado de UI en ViewModel)
- **Ubicación:** `ExpenseViewModel.kt` (líneas 70-84)
- **Causa Raíz:** Cuando `saveExpense()` falla por falta de categoría (`errorMessage = "Debes seleccionar una categoría"`) y el usuario selecciona inmediatamente una categoría con `"Continuar sin subcategoría"` (`onCategoryChange("Alimentación", "")`), `ExpenseViewModel` no limpia `errorMessage = null` (a diferencia de `IncomeViewModel.onCategorySelected`).

---

### 🔴 Bug 8: Fuga de Suscripciones (`Flow.collect`) en `IncomeViewModel.onPeriodSelected`
- **Severidad:** Alta (Concurrencia / Contaminación de Estado entre Períodos)
- **Ubicación:** `IncomeViewModel.kt` (líneas 134-162)
- **Causa Raíz:** Cada invocación a `onPeriodSelected(periodId)` lanza dos corrutinas `viewModelScope.launch { ... collect { ... } }` sin cancelar los `Job`s de suscripciones previas.
- **Impacto:** Al cambiar del Período `1L` al Período `2L`, el collector del Período `1L` sigue activo; cualquier cambio en los ingresos o presupuestos del Período `1L` sobrescribe `uiState.incomes` y `uiState.totalIncome` mientras el usuario visualiza el Período `2L`.

---

## 2. Suite de Pruebas Adversariales Creada

Se ha creado la suite completa y autocontenida en:  
📁 `app/src/test/java/com/example/money_saver_android/adversarial/OptionalSubcategoryAdversarialTest.kt`

### Casos de prueba incluidos (13 escenarios):

| Test Case | Categoría | Invariante / Vulnerabilidad Evaluada |
| :--- | :--- | :--- |
| `test_adversarial_income_and_expense_nan_and_infinity_amount_bypass` | Valores Frontera Numéricos | **Expone Bug #1**: rechazo de `"NaN"`, `"Infinity"`, `"+Infinity"` y `"1e309"` en gastos e ingresos sin subcategoría. |
| `test_adversarial_expense_card_duplicate_category_text_when_subcategory_or_description_matches_category` | UI Compose | **Expone Bug #2 (Caso A)**: evita duplicar el nombre de la categoría cuando `subcategory == category` y `description` es vacío. |
| `test_adversarial_expense_card_without_subcategory_duplicates_text_when_description_equals_category` | UI Compose | **Expone Bug #2 (Caso B)**: evita duplicar el texto cuando `subcategory == ""` y `description == category`. |
| `test_adversarial_delete_income_dialog_duplicate_text_on_whitespace_padded_subcategory` | UI Compose | **Expone Bug #3**: verifica que `DeleteIncomeConfirmationDialog` no duplique categoría cuando hay espacios circundantes. |
| `test_adversarial_untrimmed_category_with_empty_subcategory_bypasses_referential_integrity_on_delete` | Integridad Referencial | **Expone Bug #4**: impide eliminar una categoría que tiene ingresos sin subcategoría guardados con espacios en `categoryInput`. |
| `test_adversarial_whitespace_only_subcategory_inconsistency_in_usecases` | Normalización de Dominio | **Expone Bug #5**: exige que `subcategoryInput = "   \t\n   "` se normalice a `""` desde la capa de UseCase. |
| `test_adversarial_room_null_and_blank_subcategory_roundtrip_across_all_three_databases` | Persistencia Room (v4/v2/v3) | Verifica en SQLite real (`AppDatabase`, `IncomesDatabase`, `BudgetDatabase`) el mapeo bidireccional `""`/`"   "` $\leftrightarrow$ `NULL` $\leftrightarrow$ `""`. |
| `test_adversarial_room_count_queries_with_null_subcategory_records` | Consultas SQL `NULL` | Verifica `countIncomesForCategory`, `countIncomesForSubcategory`, `countBudgetsForCategory` y `countBudgetsForSubcategory` con registros `subcategory = NULL`. |
| `test_adversarial_room_income_category_replace_destroys_subcategories_via_cascade` | Integridad Relacional Room | **Expone Bug #6**: demuestra cómo `OnConflictStrategy.REPLACE` en `IncomeCategoryDao` elimina en cascada las subcategorías hijas. |
| `test_property_based_invariant_category_budgets_consolidation_with_and_without_subcategories` | Property-Based Testing | **500 iteraciones aleatorias**: verifica las invariantes matemáticas de consolidación de presupuestos y gastos con y sin subcategoría. |
| `test_adversarial_category_selection_sheet_empty_subcategories_and_back_navigation` | UI / Navegación | Verifica `"Continuar sin subcategoría"` en categorías con 0 subcategorías, navegación hacia atrás y colores hex malformados. |
| `test_adversarial_expense_viewmodel_stale_error_message_not_cleared_on_category_selection` | Estado ViewModel | **Expone Bug #7**: verifica que seleccionar una categoría sin subcategoría limpie el error previo de validación en `ExpenseViewModel`. |
| `test_adversarial_income_viewmodel_period_switching_collector_leak_overwrites_active_period_state` | Concurrencia / Flows | **Expone Bug #8**: demuestra la contaminación de estado por collectors huérfanos al cambiar de período en `IncomeViewModel`. |

---

## 3. Instrucciones de Ejecución Local

Siguiendo la restricción estricta de no ejecutar comandos de compilación/terminal desde este agente, puedes ejecutar la suite localmente mediante:

```bash
# Ejecutar la nueva suite adversarial de subcategoría opcional:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.OptionalSubcategoryAdversarialTest"

# O ejecutar todas las suites adversariales del proyecto:
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```
