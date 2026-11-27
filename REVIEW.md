# Reporte de Revisión Adversarial — Módulo de Préstamos (#15) y Saldo Neto de Ahorros

**Fecha:** 2026-11-26  
**Alcance analizado (vía `git status` y `git diff`):**
- [`app/src/main/java/com/example/money_saver_android/domain/model/Loan.kt`](app/src/main/java/com/example/money_saver_android/domain/model/Loan.kt)
- [`app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt`](app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt)
- [`app/src/main/java/com/example/money_saver_android/data/repository/LoanRepository.kt`](app/src/main/java/com/example/money_saver_android/data/repository/LoanRepository.kt)
- [`app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt)
- [`app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt)
- [`app/src/main/java/com/example/money_saver_android/domain/usecase/ExpenseUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/ExpenseUseCase.kt)
- [`app/src/main/java/com/example/money_saver_android/domain/usecase/SavingsUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/SavingsUseCase.kt)
- [`app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt)
- [`app/src/main/java/com/example/money_saver_android/viewmodel/ExpenseViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/ExpenseViewModel.kt)
- [`app/src/main/java/com/example/money_saver_android/viewmodel/SavingsViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/SavingsViewModel.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/LoansView.kt`](app/src/main/java/com/example/money_saver_android/ui/LoansView.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/components/loans/AddLoanDialog.kt`](app/src/main/java/com/example/money_saver_android/ui/components/loans/AddLoanDialog.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/components/loans/LoanCardItem.kt`](app/src/main/java/com/example/money_saver_android/ui/components/loans/LoanCardItem.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/components/loans/LoansSummaryCard.kt`](app/src/main/java/com/example/money_saver_android/ui/components/loans/LoansSummaryCard.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/MainContainer.kt`](app/src/main/java/com/example/money_saver_android/ui/MainContainer.kt)
- [`app/src/main/java/com/example/money_saver_android/ui/components/navigation/FloatingBottomNavigationBar.kt`](app/src/main/java/com/example/money_saver_android/ui/components/navigation/FloatingBottomNavigationBar.kt)
- [`app/src/main/java/com/example/money_saver_android/MainActivity.kt`](app/src/main/java/com/example/money_saver_android/MainActivity.kt)
- Traducciones: `app/src/main/assets/{es,en,de,pt}.json`

**Suite adversarial generada:**
- [`app/src/test/java/com/example/money_saver_android/adversarial/LoansAdversarialTest.kt`](app/src/test/java/com/example/money_saver_android/adversarial/LoansAdversarialTest.kt)

> **Nota de operación:** El código productivo en `app/src/main` no fue modificado. Los tests fueron diseñados y escritos listos para ejecutarse localmente o en CI/CD (sin ejecución automática, respetando la directriz `NO EJECUTAR` de `adversarial-review-test-android`).

---

## 1. Hallazgos y Fallos Detectados

### Bug #1 (Alto) — `filterDecimalInput` destruye la coma decimal e infla montos ×100 (y permite dígitos no-ASCII)

- **Ubicación:** [`AddLoanDialog.kt`](app/src/main/java/com/example/money_saver_android/ui/components/loans/AddLoanDialog.kt) (líneas 369–389: `filterDecimalInput`)
- **Entradas que lo reproducen:**
  - `filterDecimalInput("12,50", allowsDecimals = true, maxDecimals = 2)`
  - `filterDecimalInput("0,75", allowsDecimals = true, maxDecimals = 2)`
  - `filterDecimalInput("100,", allowsDecimals = true, maxDecimals = 2)`
  - `filterDecimalInput("١٢٣", allowsDecimals = true, maxDecimals = 2)`
- **Resultado obtenido:** La línea `for (char in raw.replace(",", ""))` elimina todas las comas antes de iterar, haciendo inalcanzable el bloque `else if ((char == '.' || char == ',') && allowsDecimals && !hasDot)`. Como resultado, `"12,50"` se convierte en `"1250"` (×100) y `"0,75"` en `"075"`. Además, `char.isDigit()` deja pasar dígitos Unicode no-ASCII (`'١'`, `'٢'`, `'٣'`) que luego `parseAmountInput` no puede parsear.
- **Comportamiento esperado:** En teclados decimales de locales `es`/`de`/`pt`, una coma decimal sin punto previo debe normalizarse a `.` (`"12,50"` → `"12.50"`, `"0,75"` → `"0.75"`, `"100,"` → `"100."`) y solo deben aceptarse dígitos ASCII (`'0'..'9'`).
- **Test que lo expone:** `addLoanDialog_filterDecimalInput_preservesCommaAsDecimalSeparatorAndEnforcesFormatProperty`

---

### Bug #2 (Alto) — `LoanDao.reassignSavingCategory` sobrescribe todos los préstamos personales si `oldCategoryName` está en blanco

- **Ubicación:** [`LoansDatabase.kt`](app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt) (líneas 31–32)
- **Entradas que lo reproducen:**
  - Un préstamo personal persistido con `savingCategory = ""` (guardado como `NULL` en SQLite).
  - Invocación `repo.reassignSavingCategory(oldName = "   ", newName = "Secuestrado")`.
- **Resultado obtenido:** La cláusula `WHERE LOWER(TRIM(COALESCE(savingCategory, ''))) = LOWER(TRIM(:oldCategoryName))` transforma `NULL` en `''`, por lo que `'' = ''` evalúa a verdadero y muta **todos** los préstamos personales a `savingCategory = "Secuestrado"`, drenando el saldo de esa categoría.
- **Comportamiento esperado:** Ignorar `oldCategoryName` en blanco (y excluir filas con `savingCategory IS NULL` o vacío en el `WHERE`) para nunca alterar préstamos personales.
- **Test que lo expone:** `room_reassignSavingCategory_blankOldCategory_mustNotOverwritePersonalLoans`

---

### Bug #3 (Alto) — `LOWER()` de SQLite es ASCII-only y desvincula préstamos con categorías acentuadas al renombrar

- **Ubicación:** [`LoansDatabase.kt`](app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt) (líneas 31–32)
- **Entradas que lo reproducen:**
  - Préstamo guardado con `savingCategory = "EDUCACIÓN"`.
  - Renombre `repo.reassignSavingCategory(oldName = "Educación", newName = "Universidad")`.
- **Resultado obtenido:** En SQLite, `LOWER('EDUCACIÓN')` produce `'educaciÓn'` (no convierte `'Ó'` a `'ó'`), mientras `LOWER('Educación')` produce `'educación'`. La fila del préstamo no se actualiza, quedando huérfana y dejando de descontarse del saldo neto de `"Universidad"`.
- **Comportamiento esperado:** La reasignación case-insensitive debe soportar Unicode completo (p. ej. normalización/comparación en Kotlin o consulta que no dependa de `LOWER()` ASCII de SQLite).
- **Test que lo expone:** `room_reassignSavingCategory_accentedCaseInsensitiveCategory_updatesLinkedLoans`

---

### Bug #4 (Alto) — `LoansViewModel` convierte silenciosamente un préstamo de ahorros en préstamo personal cuando se agota la categoría

- **Ubicación:** [`LoansViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt) (líneas 86–94)
- **Entradas que lo reproducen:**
  - Usuario llena el formulario con `selectedSavingCategory = "Emergencias"` (saldo `$500`) y monto `$300`.
  - Un gasto concurrente o de otra pestaña drena `"Emergencias"` a `$0` antes de pulsar Guardar.
  - Usuario pulsa `saveLoan()`.
- **Resultado obtenido:** El colector `combine` de `LoansViewModel` limpia silenciosamente `selectedSavingCategory` a `""`. Como `""` representa un préstamo personal válido, `saveLoan()` registra un **préstamo personal** sin notificar al usuario ni rechazar por saldo insuficiente.
- **Comportamiento esperado:** No convertir silenciosamente la intención del usuario a préstamo personal; preservar la categoría seleccionada (o invalidar con error explícito) para que `saveLoan()` rechace con `"Saldo insuficiente en la categoría de ahorro"`.
- **Test que lo expone:** `loansViewModel_selectedSavingCategoryExhausted_doesNotSilentlyConvertToPersonalLoan`

---

### Bug #5 (Alto) — Excepción de persistencia no capturada en `LoansViewModel.saveLoan()`

- **Ubicación:** [`LoansViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt) (líneas 154–189)
- **Entradas que lo reproducen:** `LoanRepository.insertLoan` lanza `IllegalStateException("SQLite disk full")` (o cualquier `RuntimeException` de Room/I/O).
- **Resultado obtenido:** El bloque `try { ... } finally { isSaving = false }` carece de `catch`; la excepción se propaga sin manejo fuera de `viewModelScope.launch`, provocando crash en lugar de mostrar error en el diálogo.
- **Comportamiento esperado:** Capturar excepciones no cancelatorias (re-lanzando `CancellationException`), asignar un `errorMessage` amigable, preservar los campos ingresados y permitir reintentar el guardado.
- **Test que lo expone:** `loansViewModel_repositoryThrowsException_setsErrorAndRecoversWithoutCrashing`

---

### Bug #6 (Alto) — `SavingsViewModel.confirmDeleteSaving()` descarta silenciosamente el error cuando un préstamo protege el ahorro

- **Ubicación:** [`SavingsViewModel.kt`](app/src/main/java/com/example/money_saver_android/viewmodel/SavingsViewModel.kt) (líneas 439–456)
- **Entradas que lo reproducen:** Ahorro de `$500` respaldando un préstamo de `$300` en `"Emergencias"`. El usuario ejecuta `requestDeleteSaving(saving)` y `confirmDeleteSaving()`.
- **Resultado obtenido:** `SavingsUseCase.deleteSaving` devuelve correctamente `SavingsResult.Error("No se puede eliminar el ahorro porque respalda gastos o préstamos registrados")`, pero `SavingsViewModel` solo limpia `savingToDelete = null` y deja `savingErrorMessage == null`, sin mostrar explicación al usuario.
- **Comportamiento esperado:** Propagar `result.message` a `savingErrorMessage` al recibir `SavingsResult.Error`.
- **Test que lo expone:** `savingsViewModel_confirmDeleteSavingBlockedByLoan_surfacesErrorMessage`

---

### Bug #7 (Medio) — Inconsistencia por `Locale.getDefault()` (p. ej. turco `tr_TR`) entre `calculateSavingsNetBalances` y `calculateCategoryAvailableSavings`

- **Ubicación:** [`CategorySummary.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt) (líneas 166–195)
- **Entradas que lo reproducen:** `Locale.setDefault(Locale("tr", "TR"))`, ahorro con categoría `"INVERSIÓN"` (`$500`) y préstamo con `savingCategory = "inversión"` (`$200`).
- **Resultado obtenido:** `calculateCategoryAvailableSavings` usa `equals(..., ignoreCase = true)` y calcula `$300`, pero `calculateSavingsNetBalances` agrupa con `.lowercase()` (sensible al locale por defecto), separando `"ınversión"` y `"inversión"` en claves distintas y reportando `netBalance = 500.0`, `totalLoaned = 0.0`.
- **Comportamiento esperado:** Usar `.lowercase(Locale.ROOT)` consistentemente para garantizar igualdad entre ambas funciones bajo cualquier configuración regional del dispositivo.
- **Test que lo expone:** `property_calculateSavingsNetBalances_and_calculateCategoryAvailableSavings_agreeUnderTurkishLocale`

---

### Bug #8 (Medio) — Desbordamiento `Double` en `LoanUseCase.calculateTotalReceivable` colapsa silenciosamente a `0.0`

- **Ubicación:** [`LoanUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt) (líneas 36–43)
- **Entradas que lo reproducen:** Dos préstamos registrados con `amountLentInput = "1e308"` y `amountToReceiveInput = "1e308"`.
- **Resultado obtenido:** Ambos pasan la validación de `saveLoan`, su suma en `BigDecimal` excede `Double.MAX_VALUE`, `.toDouble()` devuelve `Double.POSITIVE_INFINITY` y `if (total.isFinite() && total > 0.0) total else 0.0` muestra `$ 0.00` por cobrar.
- **Comportamiento esperado:** Acotar montos máximos permitidos en `saveLoan` o evitar que un desbordamiento reporte silenciosamente `0.0`.
- **Test que lo expone:** `calculateTotalReceivable_hugeFiniteLoansExceedingDoubleMax_doesNotSilentlyCollapseToZero`

---

### Bug #9 (Medio) — Validación permisiva de nombres Unicode en blanco y literales numéricos en `LoanUseCase` / `parseAmountInput`

- **Ubicación:**
  - [`LoanUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt) (líneas 52–67)
  - [`BudgetUseCase.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/BudgetUseCase.kt) (líneas 38–61: `parseAmountInput`)
- **Entradas que lo reproducen:**
  - Nombres compuestos solo por espacios Unicode (`"\u00A0"`, `"\u2003"`, `"\u3000"`).
  - Literales flotantes Java (`"100d"`, `"100f"`, `"0x1.0p10"`).
  - Coma decimal con cero inicial de tres decimales (`"0,500"`).
- **Resultado obtenido:**
  - `String.trim().isEmpty()` no detecta espacios Unicode (`> '\u0020'`), permitiendo guardar tarjetas con nombre visualmente vacío.
  - `toDoubleOrNull()` acepta `"100d"`, `"100f"` y `"0x1.0p10"` (`1024.0`).
  - El regex `^\d{1,3}(,\d{3})+$` trata `"0,500"` como separador de miles y lo parsea como `500.0` en lugar de `0.5`.
- **Comportamiento esperado:** Rechazar nombres Unicode en blanco (`isBlank()`), rechazar literales Java/hexadecimales en `parseAmountInput`, y exigir dígito inicial `[1-9]` para interpretar comas como separador de miles (`^[1-9]\d{0,2}(,\d{3})+$`).
- **Tests que lo exponen:** `saveLoan_unicodeWhitespacePersonName_isRejected`, `saveLoan_rejectsJavaFloatSuffixesAndHexFloats`, `saveLoan_europeanCommaDecimalInputs_andCanonicalCategoryTrimming`

---

### Bug #10 (Medio) — `buildPersonInitials` divide pares sustitutos UTF-16 produciendo strings corruptos

- **Ubicación:** [`LoanCardItem.kt`](app/src/main/java/com/example/money_saver_android/ui/components/loans/LoanCardItem.kt) (líneas 207–214)
- **Entradas que lo reproducen:** `"😀 Carlos"`, `"😀 😎"`, `"A😀"`.
- **Resultado obtenido:** `parts[0].first()` y `parts[0].take(2)` cortan por `Char` (UTF-16) en lugar de code points Unicode, devolviendo surrogates altos sin pareja (`"\uD83DC"`, `"\uD83D\uD83D"`, `"A\uD83D"`).
- **Comportamiento esperado:** Extraer iniciales por code points Unicode (`codePointAt` / `Character.toChars`) sin generar nunca pares sustitutos huérfanos.
- **Test que lo expone:** `property_buildPersonInitials_neverProducesUnpairedUtf16SurrogatesOrCrashes`

---

### Riesgo #11 (Bajo / Ciclo de vida Compose) — `showAddDialog` usa `remember` en lugar de `rememberSaveable`

- **Ubicación:** [`LoansView.kt`](app/src/main/java/com/example/money_saver_android/ui/LoansView.kt) (línea 77: `var showAddDialog by remember { mutableStateOf(false) }`)
- **Escenario:** Ante un cambio de configuración (rotación de pantalla o cambio de idioma/tema), el diálogo se cierra perdiendo `showAddDialog = true` mientras `LoansViewModel` conserva los valores parciales y posibles mensajes de error en `uiState` sin invocar `resetForm()`.
- **Recomendación:** Cambiar `remember` por `rememberSaveable` para conservar la apertura del diálogo en recreaciones de configuración.

---

## 2. Cobertura Adicional e Invariantes Verificados

Además de los tests que exponen los bugs anteriores, [`LoansAdversarialTest.kt`](app/src/test/java/com/example/money_saver_android/adversarial/LoansAdversarialTest.kt) valida:

- **Propiedades matemáticas (200 iteraciones aleatorias con semilla):** conservación exacta en `BigDecimal` (`netBalance == max(0, totalSaved - totalSpent - totalLoaned)`), equivalencia entre `calculateSavingsNetBalances`, `calculateCategoryAvailableSavings`, `calculateCategorySavingsOutflows` y `buildSavingsNetSummary`, ordenamiento determinístico e inmunidad ante entradas corruptas (`NaN`, `±Infinity`, negativos, categorías vacías).
- **Precisión de centavos IEEE-754:** préstamos de `0.10 + 0.20` contra ahorro de `0.30` sin falso rechazo ni sobre-giro de `0.01`.
- **Concurrencia, carreras y cancelación:**
  - 12 corrutinas paralelas en `LoanUseCase.saveLoan` contra un mismo ahorro (`FinancialAllocationLock.mutex`).
  - Competencia simultánea `LoanUseCase.saveLoan` + `ExpenseUseCase.saveExpense(SAVINGS)`.
  - Carreras entre creación de préstamo y edición/eliminación de ahorro (`SavingsUseCase`).
  - Liberación limpia del `Mutex` tras `CancellationException` sin deadlock.
  - Idempotencia de `LoansViewModel.saveLoan()` ante triple pulsación rápida.
- **Persistencia Room y reactividad cruzada:** roundtrip con SQL injection, RTL árabe, emojis y textos de 2,000 caracteres; propagación reactiva de préstamos a `ExpenseViewModel` (incluyendo `editSavingsBalances` y cambio automático de fuente a `BUDGET`) y `SavingsViewModel` (`netSavingsSummary`); resiliencia ante excepciones de `Flow` en los tres ViewModels.
- **UI Compose e i18n:** paridad completa de las 24 claves `loans.*` y `navigation.tab_loans` en `es`, `en`, `de`, `pt`; formato sin decimales (`JPY`); jerarquía de subtítulos (`note` > `savingCategory` > `"Préstamo personal"`); nombres extremos incluyendo `"polygenelubricants"` (`hashCode() == Int.MIN_VALUE`); y acceso a la pestaña Préstamos con período expirado y `requestedTab = AppTab.LOANS` en `MainContainer`.

---

## 3. Cómo Ejecutar la Suite

Ejecutar únicamente la suite adversarial de préstamos:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.money_saver_android.adversarial.LoansAdversarialTest"
```

Ejecutar todas las pruebas unitarias del módulo:

```bash
./gradlew :app:testDebugUnitTest
```
