# 🛡️ Adversarial Code Review & Test Report

**Target:** Últimos cambios correspondientes a la historia de usuario **#7: Eliminación de subcategorías de ingresos**.  
**Archivos inspeccionados vía Git:**
- `app/src/main/java/com/example/money_saver_android/data/local/IncomesDatabase.kt`
- `app/src/main/java/com/example/money_saver_android/data/repository/IncomeRepository.kt`
- `app/src/main/java/com/example/money_saver_android/domain/usecase/IncomeUseCase.kt`
- `app/src/main/java/com/example/money_saver_android/viewmodel/IncomeViewModel.kt`
- `app/src/main/java/com/example/money_saver_android/ui/CategorySelectionSheet.kt`
- `app/src/main/java/com/example/money_saver_android/ui/IncomesView.kt`

---

## 1. Hallazgos y Vulnerabilidades Detectadas (Adversarial Review)

### 🔴 Bug 1: State Leakage de `subcategoryError` entre Categorías
- **Severidad:** Media-Alta (UI / UX / Inconsistencia de Estado)
- **Causa Raíz:** En `IncomeViewModel.kt`, `deleteSubcategory` almacena el fallo en `IncomeUiState.subcategoryError`. Si el borrado falla en la categoría *A* (ej. "Employment") por ingresos vinculados, ese error permanece en el estado global. Si el usuario presiona "Volver" en el modal y entra a la categoría *B* (ej. "Investments"), la interfaz de *B* muestra inmediatamente el mensaje de error en color rojo de la categoría anterior, generando confusión y falsos positivos.
- **Invariante rota:** El estado de error de subcategoría debe pertenecer al ámbito de la categoría seleccionada o resetearse al cambiar/salir de categoría.

---

### 🔴 Bug 2: Dangling State / Selección Huérfana en el Formulario Principal
- **Severidad:** Alta (Integridad de Datos)
- **Causa Raíz:** En `IncomeViewModel.kt`, el formulario de ingreso almacena `categoryInput` y `subcategoryInput`. Si el usuario tiene seleccionado `categoryInput = "Freelance"` y `subcategoryInput = "Consulting"` y posteriormente abre el selector para borrar la subcategoría `"Consulting"`, la subcategoría se elimina de Room, pero `subcategoryInput` **mantiene el valor borrado**.
- **Impacto:** El usuario puede pulsar "Guardar" y persistir un ingreso asociado a una subcategoría que ya no existe en el catálogo.

---

### 🟡 Bug 3: Condición de Carrera TOCTOU (Time of Check to Time of Use)
- **Severidad:** Media (Concurrencia)
- **Causa Raíz:** En `IncomeUseCase.deleteSubcategory`:
  ```kotlin
  val count = repository.countIncomesForSubcategory(categoryName, subcategoryName)
  if (count > 0) { ... }
  repository.deleteSubcategory(categoryName, subcategoryName)
  ```
  La verificación y la eliminación ocurren en dos transacciones independientes. Si un hilo concurrentemente inserta un ingreso para esa subcategoría entre el `count` y el `delete`, la subcategoría será eliminada a pesar de que ahora tiene un ingreso asociado recién insertado, rompiendo la restricción de integridad relacional.

---

### 🟡 Bug 4: Asimetría en Normalización de Strings (Trimming & Collation)
- **Severidad:** Baja-Media (Robustez de Entradas)
- **Causa Raíz:** Mientras que `createSubcategory` aplica `.trim()` y comparaciones case-insensitive, `deleteSubcategory` envía los parámetros directamente a la query SQL:
  ```sql
  WHERE category = :categoryName AND subcategory = :subcategoryName
  ```
  Si se envían espacios accidentales o variaciones tipográficas en llamadas de eliminación, el `count` resulta en `0`, reportando éxito o no eliminando el registro real.

---

## 2. Suite de Pruebas Adversariales Creada

Se ha creado la suite de tests en:
`app/src/test/java/com/example/money_saver_android/adversarial/IncomeSubcategoryAdversarialTest.kt`

### Casos de prueba incluidos:
1. `test_adversarial_subcategory_error_should_not_leak_across_different_categories`:
   - Exige que al fallar la eliminación en una categoría con ingresos, el error no se traslade a otras categorías.
2. `test_adversarial_deleting_currently_selected_subcategory_should_clear_or_invalidate_form_selection`:
   - Exige que eliminar una subcategoría activa en el formulario limpie o invalide la selección actual.
3. `test_adversarial_concurrent_deletions_should_be_idempotent_without_exceptions`:
   - Somete el UseCase a 20 eliminaciones concurrentes simultáneas (`Dispatchers.Default`) evaluando idempotencia y ausencia de crashes.
4. `test_adversarial_delete_subcategory_with_untrimmed_whitespace_or_special_characters`:
   - Evalúa resiliencia ante caracteres Unicode, emojis (`💰`, `🚀`), símbolos (`#`, `/`) y espacios.
5. `test_adversarial_delete_subcategory_on_non_existent_category`:
   - Evalúa el comportamiento defensivo al intentar eliminar subcategorías de categorías fantasma.

---

## 3. Instrucciones de Ejecución

Siguiendo la restricción estricta de no ejecutar comandos de compilación/terminal desde este agente, puedes ejecutar los tests unitarios y adversariales localmente o en tu entorno de CI/CD ejecutando:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
```

O para ejecutar la suite completa del proyecto:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest
```
