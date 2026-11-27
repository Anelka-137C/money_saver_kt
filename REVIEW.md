# Reporte de Testing Adversarial — Historia #16 (Programación de Fechas de Pago de Préstamos)

- **Fecha:** 2026-11-26
- **Suite adversarial:** `app/src/test/java/com/example/money_saver_android/adversarial/LoanScheduleAdversarialTest.kt`
- **Comando de ejecución:**
  ```bash
  ./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.LoanScheduleAdversarialTest"
  ```
- **Resumen de resultados:** **15 tests ejecutados — 11 aprobados, 4 fallidos (4 bugs reales expuestos en código productivo)**.

---

## Bugs encontrados

### 1. Bypass de la regla "Sin fechas pasadas" al duplicar `persistedId` en `updateLoanInstallments`
- **Severidad:** Alta
- **Test que expone el fallo:** `updateLoanInstallments_duplicatePersistedId_mustNotBypassNoPastDatesRule` (`LoanScheduleAdversarialTest.kt:430-460`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`validateScheduleForLoan`, líneas ~261-273)
- **Entradas utilizadas:**
  - Préstamo existente con `amountToReceive = 300.0`, fecha actual `today = "2026-11-26"` y una cuota vencida persistida `(id = 1, dueDate = "2026-11-20", expectedAmount = 100.0)` más una futura `(dueDate = "2026-12-10", expectedAmount = 200.0)`.
  - Se invoca `updateLoanInstallments` enviando **dos** entradas con el mismo `persistedId = 1` y `dueDate = "2026-11-20"` (`"150"` + `"150"`).
- **Resultado obtenido:** `LoanResult.Success`. El préstamo reemplaza su calendario con dos cuotas en la fecha pasada `"2026-11-20"`.
- **Comportamiento esperado:** Debe retornar `LoanResult.Error` y mantener intactas las cuotas originales.
- **Causa raíz:** `existingById[input.persistedId]` no valida unicidad/consumo de cada `persistedId`, permitiendo clonar una cuota vencida existente múltiples veces para introducir nuevas cuotas en fechas pasadas.
- **Corrección sugerida:** Llevar un conjunto `consumedPersistedIds` en `validateScheduleForLoan` (y opcionalmente exigir también coincidencia o no duplicación de `persistedId`) para que cada cuota vencida persistida solo pueda conservarse una vez.

---

### 2. Validación *fail-open* cuando `currentDateProvider` devuelve una fecha vacía o inválida
- **Severidad:** Media
- **Test que expone el fallo:** `validateInstallmentDraft_corruptCurrentDateProvider_mustFailClosedNotAcceptAncientPastDate` (`LoanScheduleAdversarialTest.kt:409-416`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`parseAndValidateInstallment`, líneas ~294-296)
- **Entradas utilizadas:** `currentDateProvider = { "" }`, `dueDateInput = "1999-01-01"`, `expectedAmountInput = "100"`.
- **Resultado obtenido:** `LoanInstallmentValidationResult.Valid(LoanInstallment(dueDate = "1999-01-01", expectedAmount = 100.0))`.
- **Comportamiento esperado:** Debe fallar de forma segura (`LoanInstallmentValidationResult.Error`) o usar la fecha actual del sistema en lugar de desactivar la restricción de fechas pasadas.
- **Causa raíz:** La condición `if (!allowPersistedPastDate && isValidLoanIsoDate(today) && trimmedDate < today)` omite la comparación cuando `isValidLoanIsoDate(today)` es `false`.
- **Corrección sugerida:** Si `!allowPersistedPastDate && !isValidLoanIsoDate(today)`, retornar error explícito (o resolver un fallback seguro a la fecha del sistema si se prefiere resiliencia).

---

### 3. Estado obsoleto en `DatePickerField` (`rememberDatePickerState`) ante actualizaciones externas de `value`
- **Severidad:** Alta (UX / integridad de fecha seleccionada)
- **Test que expone el fallo:** `compose_datePickerField_externalValueUpdate_confirmEmitsUpdatedValueNotStaleInitialDate` (`LoanScheduleAdversarialTest.kt:668-705`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/ui/components/common/DatePickerField.kt` (líneas ~75-96)
- **Entradas utilizadas:**
  - `DatePickerField` se compone inicialmente con `value = "2026-11-26"` y `minDateIso = "2026-11-26"`.
  - El estado externo actualiza `value` a `"2026-12-15"`.
  - El usuario abre el selector de fecha y pulsa `OK` sin volver a tocar el día en el calendario.
- **Resultado obtenido:** `onValueChange` emite `"2026-11-26"` (valor inicial obsoleto), sobrescribiendo `"2026-12-15"`.
- **Comportamiento esperado:** `onValueChange` debe emitir `"2026-12-15"`.
- **Causa raíz:** `rememberDatePickerState(initialSelectedDateMillis = ..., selectableDates = ...)` usa `rememberSaveable` interno y solo lee los valores iniciales en la primera composición; no sincroniza `selectedDateMillis` ni `selectableDates` cuando `value` o `minDateIso` cambian, ni al reabrir el diálogo.
- **Corrección sugerida:** Instanciar `rememberDatePickerState` dentro del bloque `if (showDatePicker)` (o usar `key(value, minDateIso)` / `LaunchedEffect(showDatePicker, initialSelectedMillis)`) para que cada apertura del diálogo refleje siempre el `value` y `minDateIso` actuales.

---

### 4. Fecha por defecto vencida en `AddLoanDialog` tras cruce de medianoche con la app abierta
- **Severidad:** Media (UX / ciclo de vida)
- **Test que expone el fallo:** `compose_loansView_dayRolloverBeforeOpeningAddDialog_prefillsNewTodayNotYesterday` (`LoanScheduleAdversarialTest.kt:747-780`)
- **Ubicación en código productivo:**
  - `app/src/main/java/com/example/money_saver_android/ui/LoansView.kt` (apertura de `AddLoanDialog` en `btn_add_loan_top` y `btn_add_loan_bottom`)
  - `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (captura de `initialToday` al iniciar el ViewModel)
- **Entradas utilizadas:**
  - `LoansViewModel` se inicializa con fecha `"2026-11-26"`.
  - El reloj del sistema avanza a `"2026-11-27"`.
  - El usuario toca `btn_add_loan_bottom`, escribe `"100"` en el monto esperado de la cuota y pulsa `btn_add_loan_installment`.
- **Resultado obtenido:** `draftInstallments.size == 0` y error de fecha pasada, porque `todayIso` y `draftInstallmentDateInput` quedaron congelados en `"2026-11-26"`.
- **Comportamiento esperado:** Al abrir `AddLoanDialog`, `todayIso` y `draftInstallmentDateInput` deben actualizarse a `"2026-11-27"` y permitir agregar la cuota (`draftInstallments.size == 1`, `dueDate == "2026-11-27"`).
- **Corrección sugerida:** Invocar un método `openAddLoanDialog()` / `refreshToday()` en `LoansViewModel` al abrir el diálogo (y al reanudar la vista) para refrescar `todayIso` y actualizar `draftInstallmentDateInput` si quedó anterior a hoy.

---

## Cobertura adversarial aprobada (11/15 tests)

- **Property-Based (`calculateLoanDueBadgeInfo`, 200 iteraciones):** filtrado de fechas malformadas, selección de mínima fecha cronológica, `additionalCount == validCount - 1` y estado `isOverdue == (nextDueDate < todayIso)`.
- **Property-Based (`saveLoan`, 150 iteraciones):** conservación exacta `BigDecimal` en particiones aleatorias de centavos, orden cronológico garantizado y rechazo ante desviaciones de `±0.01`.
- **Property-Based (`formatLoanShortDate`, 400 combinaciones + corruptas):** formateo estable sin excepciones en `es`, `en`, `de` y `pt`.
- **Fronteras de calendario y parsing:** años bisiestos (`2028-02-29` válido, `2026-02-29` rechazado), dígitos árabe-índicos (`٢٠٢٦-١٢-٠١`), `\u200B`, sufijos numéricos Java (`100d`, `100f`, `0x1.0p10`), `NaN`/`Infinity`, decimales con coma europea (`125,50 + 124,50 == 250`).
- **Zonas horarias DST:** fechas de transición en `America/Havana`, `America/Santiago` y `America/Asuncion`.
- **Manipulación de IDs inexistentes y fechas alteradas:** rechazo de `loanId` inexistente, `persistedId` falsificado y cambio de fecha en cuota vencida hacia otra fecha pasada.
- **Persistencia Room y migración `1 -> 2`:** preservación de préstamos con payload SQLi y acentos, carga masiva de 500 cuotas en orden cronológico, preservación de cuotas al renombrar categoría de ahorro (`reassignSavingCategory`) y eliminación en cascada (`ON DELETE CASCADE`).
- **Concurrencia e idempotencia en `LoansViewModel`:** triple tap en `saveLoanSchedule()` ejecuta una sola escritura; recuperación limpia tras fallo de SQLite conservando estado; rechazo tras modificar `amountToReceive` luego de agregar cuotas en borrador.
- **UI y paridad i18n:** nombre de 300 caracteres junto a badge `+24`, scroll completo en `AddLoanDialog` manteniendo `btn_close_loan_dialog` accesible, y presencia/placeholders `%s` válidos en `es`, `en`, `de` y `pt`.
