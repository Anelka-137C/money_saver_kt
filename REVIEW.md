# Reporte de Testing Adversarial — Pagos de Préstamos, Historial, Reajuste de Calendario y Migraciones (`v1 -> v4`)

- **Fecha:** 2026-11-27
- **Suites adversariales:**
  - `app/src/test/java/com/example/money_saver_android/adversarial/LoanPaymentsAdversarialTest.kt`
  - `app/src/test/java/com/example/money_saver_android/adversarial/LoanScheduleAdversarialTest.kt`
- **Comandos de ejecución:**
  ```bash
  # Ejecutar la suite adversarial de pagos, historial, concurrencia y migraciones
  ./gradlew :app:testDebugUnitTest --tests "com.example.money_saver_android.adversarial.LoanPaymentsAdversarialTest"

  # Ejecutar ambas suites adversariales de préstamos
  ./gradlew :app:testDebugUnitTest --tests "com.example.money_saver_android.adversarial.*"
  ```
- **Resumen consolidado:** **24 defectos confirmados y expuestos con tests automatizados** (**17** en la Primera Pasada + **7** nuevos en la Segunda Pasada), además de **4 suites Property-Based** (`150–200` iteraciones aleatorias con semilla fija), pruebas de carrera concurrente inter-módulo (`30` corrutinas), migraciones Room `1 -> 2 -> 3 -> 4`, `ON DELETE CASCADE`, ViewModel idempotency, UI en Compose y paridad i18n en `4` idiomas (`es`, `en`, `de`, `pt`).
- **Restricción cumplida:** Ningún archivo de código fuente/productivo fue modificado y los tests no fueron ejecutados por terminal (directiva `adversarial-review-test-android`).

---

## Parte I — Primera Pasada de Análisis Adversarial (17 Bugs Expuestos)

### 1. Colisión por igualdad de valor en `previewLoanInstallmentsAfterPayment` descarta abonos
- **Severidad:** Alta (pérdida de abono aplicado en cuotas)
- **Test que expone el fallo:** `previewLoanInstallmentsAfterPayment_identicalUnpersistedInstallments_mustNotDropOrDoubleApplyPayment` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (líneas `119-137`)
- **Entradas utilizadas:** Préstamo con dos cuotas no persistidas idénticas en valor `(id = 0L, dueDate = "2026-12-01", expectedAmount = 100.0, paymentAmount = 0.0)` y `paymentAmount = 50.0`.
- **Resultado obtenido:** `totalApplied == 0.0` (ambas cuotas quedan con `paymentAmount = 0.0`).
- **Comportamiento esperado:** Una cuota debe quedar con `paymentAmount = 50.0` y la otra con `0.0` (`totalApplied == 50.0`).
- **Causa raíz:** `updatedByIdentity` usa `mutableMapOf<LoanInstallment, LoanInstallment>()` sobre una `data class` (igualdad por valor `equals`/`hashCode`, no identidad de referencia). La segunda cuota sin fondos sobrescribe la entrada de la primera en el mapa.
- **Corrección sugerida:** Usar `java.util.IdentityHashMap<LoanInstallment, LoanInstallment>()` o indexar las cuotas por posición en la lista ordenada.

---

### 2. Doble descuento al eliminar un pago posterior tras usar `SetNewDueDate` en préstamo sin calendario previo
- **Severidad:** Alta (corrupción del saldo pendiente del calendario)
- **Test que expone el fallo:** `deleteLoanPayment_afterSetNewDueDateOnUnscheduledLoan_mustNotDoubleCountInitialPayment` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`recomputeInstallmentsWithoutPayment`, líneas `462-484` y `SetNewDueDate`, líneas `654-676`)
- **Entradas utilizadas:**
  1. Préstamo de `$300` sin cuotas iniciales (`installments = []`).
  2. Pago #1 de `$100` con `SetNewDueDate("2026-12-20")` (crea una única cuota con `expectedAmount = 200.0`, ya neta del Pago #1).
  3. Pago #2 de `$50` (`KeepCurrent`) aplicado sobre la cuota de `$200`.
  4. Eliminación del Pago #2 mediante `deleteLoanPayment`.
- **Resultado obtenido:** `loan.pendingAmount == 200.0`, pero `installments.remainingAmount == 100.0` (desfase de `$100`).
- **Comportamiento esperado:** `sum(installments.remainingAmount)` debe conservarse exactamente igual a `loan.pendingAmount` (`$200.0`).
- **Causa raíz:** `SetNewDueDate` crea una cuota sólo por el saldo restante sin registrar el Pago #1 como cuota saldada (`expectedAmount = 100, paymentAmount = 100`), por lo que `recomputeInstallmentsWithoutPayment` vuelve a descontar el Pago #1 sobre la cuota de `$200`.
- **Corrección sugerida:** Al aplicar `SetNewDueDate` sobre préstamos sin cuotas previas (o con pagos previos no cubiertos por `settledOnes`), materializar el monto ya cubierto o ajustar el algoritmo de reconstrucción para que `sum(expectedAmount) == loan.amountToReceive`.

---

### 3. `deleteLoan` ignora compromisos de ahorro respaldados por los abonos del préstamo
- **Severidad:** Alta (integridad contable inter-módulo)
- **Test que expone el fallo:** `deleteLoan_whoseRepaymentsBackRegisteredExpense_mustBeRejected` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`deleteLoan`, líneas `936-945`)
- **Entradas utilizadas:** Préstamo personal de `$500` cobrado al 100% en `"Emergencias"` (`+$500`), seguido de un gasto financiado con ahorros de `"Emergencias"` por `$400`, y llamada a `deleteLoan`.
- **Resultado obtenido:** `LoanResult.Success`; el préstamo y su pago se eliminan en cascada dejando `"Emergencias"` con salidas (`$400`) mayores que entradas (`$0`).
- **Comportamiento esperado:** Retornar `LoanResult.Error` y conservar el préstamo mientras sus cobros respalden gastos o préstamos registrados.
- **Causa raíz:** `deleteLoan` sólo verifica existencia del préstamo y omite la validación `outflows > inflowsAfter` que sí implementa `deleteLoanPayment`.
- **Corrección sugerida:** Simular el estado de `allLoans` excluyendo el préstamo a eliminar y validar para cada categoría afectada por `loan.payments` que `calculateCategorySavingsOutflows(...) <= calculateCategorySavingsInflows(...)`.

---

### 4. `updateLoan` permite reducir `amountToReceive` por debajo de `paidAmount`
- **Severidad:** Alta (estado financiero inválido / sobrepago)
- **Test que expone el fallo:** `updateLoan_amountToReceiveBelowAlreadyPaidAmount_mustBeRejected` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`updateLoan`, líneas `855-886`)
- **Entradas utilizadas:** Préstamo con `amountLent = 100`, `amountToReceive = 200` y un pago registrado de `$150`; se invoca `updateLoan` con `amountToReceiveInput = "120"`.
- **Resultado obtenido:** `LoanResult.Success` con `paidAmount = 150.0 > amountToReceive = 120.0`.
- **Comportamiento esperado:** Retornar `LoanResult.Error` impidiendo fijar `amountToReceive` por debajo del total ya cobrado.
- **Causa raíz:** Sólo se valida `amountToReceive >= amountLent` y luego se trunca `pendingTarget` con `.max(BigDecimal.ZERO)`.
- **Corrección sugerida:** Validar dentro de `loanMutex.withLock` que `BigDecimal.valueOf(amountToReceive) >= BigDecimal.valueOf(existingLoan.paidAmount)`.

---

### 5. `validateScheduleForLoan` acepta `paymentAmount` forjado en cuotas nuevas y permite borrar cuotas ya pagadas
- **Severidad:** Alta (integridad de pagos y referencias `installmentId`)
- **Test que expone el fallo:** `updateLoanInstallments_forgedPaymentAmountAndDroppingPaidInstallments_mustBeRejected` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`validateScheduleForLoan`, líneas `952-977`)
- **Entradas utilizadas:**
  - *Caso A:* Cuota nueva `(persistedId = 0L, expectedAmountInput = "500", paymentAmount = 500.0)` sobre préstamo sin pagos.
  - *Caso B:* Préstamo de `$300` con cuota #1 (`$100`) ya pagada y cuota #2 (`$200`) pendiente; `updateLoanInstallments` enviando únicamente la cuota #2 (`$200`).
- **Resultado obtenido:** Ambos casos retornan `LoanResult.Success`, creando cuotas falsamente pagadas o eliminando cuotas pagadas y dejando `LoanPayment.installmentId` huérfano.
- **Comportamiento esperado:** Retornar `LoanResult.Error` en ambos escenarios.
- **Causa raíz:** `existing?.paymentAmount ?: input.paymentAmount` confía en `input.paymentAmount` cuando `existing == null`, y `validateScheduleForLoan` no exige conservar las cuotas persistidas con `paymentAmount > 0`.
- **Corrección sugerida:** Si `existing == null`, rechazar `input.paymentAmount > 0` (o forzar `0.0`), y exigir que toda cuota existente con `paymentAmount > 0.0` permanezca presente en `inputs` (sin duplicar `persistedId`).

---

### 6. `AddInstallment` sobre préstamo sin calendario previo crea un calendario incompleto
- **Severidad:** Media-Alta (ruptura de invariante `sum(remainingAmount) == pendingAmount`)
- **Test que expone el fallo:** `recordLoanPayment_addInstallmentWithoutExistingSchedule_mustNotCreateUnderfundedSchedule` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (líneas `678-719`)
- **Entradas utilizadas:** Préstamo de `$300` con `installments = []`, pago parcial de `$100` (`remaining = $200`) y `AddInstallment("2026-12-20", "50")`.
- **Resultado obtenido:** Se persiste una única cuota de `$50` mientras el préstamo tiene `$200` pendientes.
- **Comportamiento esperado:** Rechazar `AddInstallment` cuando no existen cuotas pendientes de las cuales deducir `extraDec` (a menos que `extraDec == remainingAfter`).
- **Causa raíz:** El bucle de deducción sobre `unpaidAfter` no valida que `toDeduct` haya sido absorbido completamente (`toDeduct == 0`) cuando `unpaidAfter` está vacío.
- **Corrección sugerida:** Exigir `unpaidAfter.isNotEmpty()` (o verificar `toDeduct.compareTo(BigDecimal.ZERO) == 0` salvo que cubra el 100% de `remainingAfter`).

---

### 7. `Loan.paidAmount` colapsa a `0.0` ante desbordamiento de `Double.MAX_VALUE`
- **Severidad:** Baja-Media (inconsistencia numérica extrema)
- **Test que expone el fallo:** `loanModel_hugePaymentsExceedingDoubleMaxValue_mustCapNotCollapseToZero` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/model/Loan.kt` (líneas `69-70`)
- **Entradas utilizadas:** Dos pagos de `1e308` sobre un préstamo de `1e308`.
- **Resultado obtenido:** `isSettled == true`, `progressFraction == 1f`, pero `paidAmount == 0.0`.
- **Comportamiento esperado:** Acotar en `Double.MAX_VALUE` (consistente con `LoanUseCase.calculateTotalPaid`).
- **Causa raíz:** `paidDecimal.toDouble()` retorna `Double.POSITIVE_INFINITY` y `.takeIf { it.isFinite() } ?: 0.0` lo convierte en `0.0`.
- **Corrección sugerida:** Si `paidDecimal > BigDecimal.ZERO` y `!toDouble().isFinite()`, retornar `Double.MAX_VALUE`.

---

### 8. `SavingsUseCase.deleteCategory` elimina categorías con saldo activo proveniente de cobros de préstamos
- **Severidad:** Alta (integridad del catálogo de ahorros)
- **Test que expone el fallo:** `savingsUseCase_deleteCategoryWithActiveLoanRepaymentBalance_mustBeRejected` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/SavingsUseCase.kt` (`deleteCategory`, líneas `440-453`)
- **Entradas utilizadas:** Categoría `"Viajes"` sin registros en la tabla `savings`, pero con `$300` acreditados desde `loan_payments`; llamada a `savingsUseCase.deleteCategory("Viajes")`.
- **Resultado obtenido:** `SavingsResult.Success`; la categoría se elimina del catálogo pese a tener `$300` de saldo neto disponible.
- **Comportamiento esperado:** Retornar `SavingsResult.Error` mientras préstamos o pagos de préstamos estén asociados a la categoría.
- **Causa raíz:** `deleteCategory` sólo consulta `savingsRepository.countSavingsForCategory(canonicalName)` e ignora `loanRepository`.
- **Corrección sugerida:** Verificar también si existen préstamos o `loan_payments` vinculados a `canonicalName` antes de permitir el borrado.

---

### 9. Rechazo falso por horario de verano (DST) a medianoche en `isValidLoanPaymentDateTime`
- **Severidad:** Media (internacionalización / zonas horarias)
- **Test que expone el fallo:** `recordLoanPayment_dstMidnightDateOnlyInLocalTimezone_mustBeAccepted` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (líneas `66-81`, y `isValidLoanIsoDate` / `formatLoanShortDate`)
- **Entradas utilizadas:** Zona horaria `America/Santiago` y `paidAtInput = "2026-09-06"` (día en que el reloj salta de `00:00` a `01:00`).
- **Resultado obtenido:** `LoanResult.Error("La fecha y hora del pago no son válidas")`.
- **Comportamiento esperado:** `LoanResult.Success`.
- **Causa raíz:** `SimpleDateFormat` con `isLenient = false` usa la zona horaria local del dispositivo, donde `00:00:00` no existe ese día.
- **Corrección sugerida:** Fijar `timeZone = TimeZone.getTimeZone("UTC")` en los validadores/formateadores de fechas calendario (`yyyy-MM-dd`) o usar `java.time.LocalDate`.

---

### 10. Condición de carrera en `updateLoanInstallments` por no adquirir `loanMutex`
- **Severidad:** Media-Alta (concurrencia Coroutines)
- **Test que expone el fallo:** `concurrency_recordPaymentAndUpdateLoanInstallments_mustSerializeViaMutex` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`updateLoanInstallments`, líneas `809-834`)
- **Entradas utilizadas:** `recordLoanPayment` suspendido dentro del repositorio sosteniendo `loanMutex`, mientras otra corrutina invoca `updateLoanInstallments`.
- **Resultado obtenido:** `updateLoanInstallments` se ejecuta inmediatamente sin esperar `loanMutex`, leyendo `pendingAmount` obsoleto y sobrescribiendo cuotas.
- **Comportamiento esperado:** `updateLoanInstallments` debe serializarse mediante `loanMutex.withLock`.
- **Causa raíz:** Es el único método mutador de `LoanUseCase` que no envuelve su lectura-validación-escritura en `loanMutex.withLock`.
- **Corrección sugerida:** Envolver el cuerpo de `updateLoanInstallments` en `return loanMutex.withLock { ... }`.

---

### 11. `MIGRATION_3_4` no rellena `paymentAmount` en cuotas para préstamos con pagos registrados en v3
- **Severidad:** Alta (migración de datos Room `v3 -> v4`)
- **Test que expone el fallo:** `room_migration3To4_preExistingPayments_mustBackfillInstallmentPaymentAmounts` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt` (`MIGRATION_3_4`, líneas `288-297`)
- **Entradas utilizadas:** Base SQLite v3 con préstamo de `$300`, dos cuotas (`$100`, `$200`) y un pago registrado en v3 por `$100`; apertura con Room v4 aplicando `MIGRATION_3_4`.
- **Resultado obtenido:** `loan.pendingAmount == 200.0`, pero ambas cuotas quedan con `paymentAmount = 0.0` (`sum(remainingAmount) == 300.0 != 200.0`) y la cuota pagada sigue apareciendo pendiente.
- **Comportamiento esperado:** Distribuir cronológicamente los pagos existentes en v3 sobre las cuotas de cada préstamo durante `MIGRATION_3_4` para preservar `sum(remainingAmount) == pendingAmount`.
- **Causa raíz:** `MIGRATION_3_4` sólo ejecuta `ALTER TABLE ... ADD COLUMN paymentAmount REAL NOT NULL DEFAULT 0.0` sin migrar los montos ya cobrados en `loan_payments`.
- **Corrección sugerida:** En `MIGRATION_3_4.migrate(db)`, recorrer los préstamos con filas en `loan_payments` y repartir el acumulado cobrado en orden `(dueDate ASC, id ASC)` actualizando `loan_installments.paymentAmount`.

---

### 12. Pérdida silenciosa de filas en `LoanDao.syncInstallments` cuando llegan `id` duplicados
- **Severidad:** Alta (pérdida de datos en SQLite)
- **Test que expone el fallo:** `room_syncInstallments_duplicatePersistedId_mustNotSilentlyDropInstallment` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt` (`syncInstallments`, líneas `155-170`)
- **Entradas utilizadas:** Préstamo con cuota `id = 1` (`$300`); `updateLoanInstallments` con dos entradas compartiendo `persistedId = 1` (`$100` y `$200`).
- **Resultado obtenido:** `LoanResult.Success`, pero en Room sólo queda **1 fila** de `$200` (se pierden `$100` del calendario).
- **Comportamiento esperado:** Rechazar IDs duplicados en validación y evitar colisiones en `syncInstallments`.
- **Causa raíz:** Ambas entradas entran en `toUpdate` con el mismo `id = 1`, ejecutando dos `UPDATE ... WHERE id = 1` donde el segundo pisa al primero.
- **Corrección sugerida:** Deduplicar/rechazar `persistedId > 0` repetidos en `validateScheduleForLoan` y en `syncInstallments` tratar cualquier repetición de `id` como inserción (`id = 0`) o error.

---

### 13. Editar la fecha de una cuota parcialmente pagada resetea `persistedId = 0L` y rompe `LoanPayment.installmentId`
- **Severidad:** Media-Alta (pérdida de trazabilidad en historial de pagos)
- **Test que expone el fallo:** `loansViewModel_editScheduleInstallmentDueDate_mustPreservePersistedIdForPartiallyPaidInstallment` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (`addScheduleInstallment`, líneas `382-393`)
- **Entradas utilizadas:** Cuota persistida `(id = 10L, dueDate = "2026-12-01", expectedAmount = 100.0, paymentAmount = 40.0)` vinculada a un pago con `installmentId = 10L`; se edita su fecha a `"2026-12-20"`.
- **Resultado obtenido:** `editedDraft.persistedId == 0L`, lo que al guardar borra la fila `10` e inserta un nuevo ID, dejando `installmentId = 10L` huérfano.
- **Comportamiento esperado:** Conservar `persistedId = 10L` al editar una cuota existente hacia una fecha futura válida.
- **Causa raíz:** `keptPersistedId` exige `editingTarget.dueDate == validation.installment.dueDate`; esa restricción sólo aplica para permitir fechas pasadas, no para destruir la identidad de la fila cuando se mueve a una fecha futura.
- **Corrección sugerida:** Mantener siempre `editingTarget.persistedId` cuando `editingTarget.persistedId > 0L` (ya que `validateInstallmentDraft` bloqueó previamente cualquier cambio hacia una fecha pasada).

---

### 14. Guardar un préstamo en modo `NO_DATE` persiste `draftInstallments` residuales
- **Severidad:** Media (estado sucio en `LoansViewModel`)
- **Test que expone el fallo:** `loansViewModel_saveLoanInNoDateMode_mustIgnoreStaleDraftInstallments` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (`saveLoan`, líneas `902-926`)
- **Entradas utilizadas:** Se agrega un borrador en `draftInstallments`, luego se selecciona `LoanCreationPaymentMode.NO_DATE` y se invoca `saveLoan()`.
- **Resultado obtenido:** El préstamo creado se guarda con la cuota en borrador pese a haber elegido *"Sin fecha de pago prevista"*.
- **Comportamiento esperado:** En creación con `LoanCreationPaymentMode.NO_DATE`, persistir `emptyList()`.
- **Causa raíz:** El `when` maneja `editingId != null`, `SINGLE_DATE` e `INSTALLMENTS`, dejando `NO_DATE` en la rama `else -> state.draftInstallments`.
- **Corrección sugerida:** Manejar explícitamente `selectedCreationMode == LoanCreationPaymentMode.NO_DATE -> emptyList()`.

---

### 15. Bloqueo de usuario al editar `amountToReceive` de un préstamo que tiene cuotas programadas
- **Severidad:** Alta (bloqueo funcional en flujo de edición)
- **Test que expone el fallo:** `loansViewModel_editScheduledLoanAmountToReceive_mustNotTrapUserWithHiddenScheduleMismatch` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (líneas `774-815`, `902-910`) y `app/src/main/java/com/example/money_saver_android/ui/components/loans/AddLoanDialog.kt` (línea `219`)
- **Entradas utilizadas:** Préstamo con `amountLent = 200`, `amountToReceive = 200` y 1 cuota de `$200`; `startEditingLoan`, cambio de montos a `"250"` y `saveLoan()`.
- **Resultado obtenido:** Falla siempre con `"La suma de los montos esperados debe ser exactamente igual al monto a recibir"`, y `AddLoanDialog(isEditing = true)` oculta la edición de cuotas, atrapando al usuario.
- **Comportamiento esperado:** Reajustar automáticamente (ej. cuando hay una única cuota pendiente o ajustando la última cuota pendiente) o exponer/abrir el ajuste de calendario al editar el monto a recibir.
- **Causa raíz:** `startEditingLoan` carga las cuotas antiguas en `draftInstallments` y `saveLoan` las reenvía intactas sin permitir editarlas en la UI.
- **Corrección sugerida:** Si sólo existe 1 cuota no pagada (o ajustar la última cuota pendiente cuando `newPending >= sumOtherUnpaid`), actualizar su monto automáticamente o abrir `LoanScheduleDialog` para confirmar el nuevo reparto.

---

### 16. Crash (`NumberFormatException`) en `LoanPaymentHistorySheet` ante montos corruptos (`NaN` / `Infinity`)
- **Severidad:** Alta (crash de UI Compose)
- **Test que expone el fallo:** `compose_paymentHistorySheet_nonFiniteCorruptPaymentAmount_mustRenderWithoutCrashing` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/ui/components/loans/LoanPaymentHistorySheet.kt` (`PaymentHistoryRow`, líneas `463-467`)
- **Entradas utilizadas:** Renderizado de `LoanPaymentHistorySheet` con `LoanPayment(amount = Double.NaN, ...)`.
- **Resultado obtenido:** `java.lang.NumberFormatException: Infinite or NaN` en `BigDecimal.valueOf(payment.amount)`.
- **Comportamiento esperado:** Renderizar sin lanzar excepción tratando valores no finitos como `0.0` (igual que en `Loan.kt`).
- **Causa raíz:** Llamadas directas a `BigDecimal.valueOf(payment.amount)` y `BigDecimal.valueOf(loan.amountToReceive)` sin guarda `isFinite()`.
- **Corrección sugerida:** Extraer un helper `Double.toSafeBigDecimal()` que retorne `BigDecimal.ZERO` cuando `!isFinite()`.

---

### 17. Error silencioso en UI al fallar el borrado de un pago protegido desde `LoanPaymentHistorySheet`
- **Severidad:** Media-Alta (UX / feedback de error invisible)
- **Test que expone el fallo:** `compose_loansView_deleteProtectedPaymentFromHistory_mustDisplayErrorToUser` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/ui/LoansView.kt` (líneas `358-387`) y `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (líneas `548-556`)
- **Entradas utilizadas:** Abrir `LoanPaymentHistorySheet`, pulsar menú `⋮ -> Eliminar -> Eliminar` sobre un pago cuyo ahorro ya respalda un gasto registrado.
- **Resultado obtenido:** El diálogo de confirmación desaparece y el usuario no recibe ningún mensaje en pantalla ni Toast.
- **Comportamiento esperado:** Mostrar claramente el motivo del rechazo en `LoanPaymentHistorySheet` (y/o mediante banner/Toast).
- **Causa raíz:** `LoansViewModel` guarda el error en `errorMessage` y `paymentErrorMessage`, pero ni `LoansView` ni `LoanPaymentHistorySheet` renderizan esos campos cuando los diálogos de alta/pago están cerrados.
- **Corrección sugerida:** Pasar `errorMessage = state.paymentErrorMessage` a `LoanPaymentHistorySheet` (limpiándolo al abrir/cerrar el historial) o emitir un mensaje visible en pantalla/Toast.

---

## Parte II — Segunda Pasada de Análisis Adversarial (7 Nuevos Bugs Expuestos)

### 18. `updateLoanPayment` tras `SetNewDueDate` desincroniza el calendario y oculta el badge de vencimiento con saldo pendiente
- **Severidad:** Alta (pérdida de alerta de cobro y desincronización de saldo)
- **Test que expone el fallo:** `updateLoanPayment_afterSetNewDueDateOnUnscheduledLoan_mustKeepScheduleAlignedWithPendingBalance` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (`updateLoanPayment`, líneas `564-595`)
- **Entradas utilizadas:**
  1. Préstamo de `$300` sin calendario inicial.
  2. Pago #1 de `$100` con `SetNewDueDate("2026-12-20")` (genera 1 cuota de `$200`).
  3. Edición del Pago #1 de `$100` a `$200` (`updateLoanPayment` con `KeepCurrent`).
- **Resultado obtenido:** `loan.pendingAmount == 100.0`, pero la única cuota queda con `expectedAmount = 200, paymentAmount = 200` (`isPaid == true`, `remainingAmount == 0.0`) y `calculateLoanDueBadgeInfo` devuelve `null` (desaparece el badge de vencimiento pese a deberse `$100`).
- **Comportamiento esperado:** `sum(installments.remainingAmount)` debe ser `$100.0` y el badge de vencimiento para `"2026-12-20"` debe seguir activo.
- **Causa raíz:** `recomputeInstallmentsWithoutPayment` deja la cuota en `expectedAmount = 200` en lugar de restaurar la deuda total `$300` (o ajustar la cuota al nuevo remanente `$100`), y luego aplica los `$200` completos sobre esa cuota.
- **Corrección sugerida:** Cuando la suma de `expectedAmount` de las cuotas base es menor que `amountToReceive` (producto de un `SetNewDueDate` previo), reconciliar el `expectedAmount` de la cuota remanente frente a `remainingAfter`.

---

### 19. `updateLoan` con `installmentInputs = emptyList()` borra cuotas ya pagadas y deja `LoanPayment.installmentId` huérfano
- **Severidad:** Media-Alta (integridad referencial lógica de historial)
- **Test que expone el fallo:** `updateLoan_withEmptyInstallmentsOnPartiallyPaidLoan_mustNotWipePaidInstallments` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/domain/usecase/LoanUseCase.kt` (líneas `879-931` y `952`)
- **Entradas utilizadas:** Préstamo de `$300` con cuota #1 (`$100`) ya pagada y referenciada por `LoanPayment.installmentId`; llamada a `updateLoan(..., installmentInputs = emptyList())`.
- **Resultado obtenido:** `LoanResult.Success` y eliminación total de todas las filas en `loan_installments`, dejando el pago apuntando a un `installmentId` inexistente.
- **Comportamiento esperado:** Conservar las cuotas ya pagadas (o rechazar vaciar cuotas que tienen pagos aplicados).
- **Causa raíz:** `if (inputs.isEmpty()) return emptyList() to null` en `validateScheduleForLoan` ignora si `existingById` contiene cuotas con `paymentAmount > 0`.
- **Corrección sugerida:** Si `inputs.isEmpty()`, conservar las cuotas existentes con `paymentAmount > 0` ajustadas a su monto pagado (o rechazar eliminar cuotas con pagos asociados).

---

### 20. Asimetría en `LoanDao.reassignSavingCategory` cuando `newCategoryName` está en blanco
- **Severidad:** Media (desincronización entre préstamos y pagos en Room)
- **Test que expone el fallo:** `room_reassignSavingCategoryWithBlankNewName_mustNotLeaveLoansAndPaymentsOutOfSync` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/data/local/LoansDatabase.kt` (`reassignSavingCategory`, líneas `219-237`)
- **Entradas utilizadas:** Préstamo financiado desde `"Emergencias"` con un pago acreditado a `"Emergencias"`; invocación de `reassignSavingCategory("Emergencias", "   ")`.
- **Resultado obtenido:** `loan.savingCategory` pasa a `""` (`null` en SQLite), mientras `payment.savingCategory` permanece en `"Emergencias"`.
- **Comportamiento esperado:** Si `newCategoryName` está en blanco, tratar la operación como no-op (igual que cuando `oldCategoryName` está en blanco) para no desincronizar préstamos y pagos.
- **Causa raíz:** `loans` se actualiza siempre con `normalizedNew` (incluso si es `null`), pero `loan_payments` sólo se actualiza dentro de `if (normalizedNew != null)`.
- **Corrección sugerida:** Retornar inmediatamente si `normalizedNew == null` (`val normalizedNew = newCategoryName.trim { it.isWhitespace() }; if (normalizedNew.isEmpty()) return`).

---

### 21. Prefill obsoleto en `paymentAdjustedNextAmountInput` rompe `saveLoanPayment` al modificar el monto del abono
- **Severidad:** Media (estado obsoleto en `LoansViewModel`)
- **Test que expone el fallo:** `loansViewModel_changingPaymentAmountAfterSelectingAdjustNextInstallment_mustRefreshStaleAdjustedPrefill` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (`onPaymentAmountChange`, líneas `574-576` y `onPartialScheduleActionSelected`, líneas `608-632`)
- **Entradas utilizadas:** Préstamo de `$300` con cuotas `$100` y `$200`; `onPaymentAmountChange("50")` -> `onPartialScheduleActionSelected(ADJUST_NEXT_INSTALLMENT)` (autocompleta `"50"`) -> `onPaymentAmountChange("260")` (quedan `$40` pendientes) -> `saveLoanPayment()`.
- **Resultado obtenido:** Falla con `"El monto de la siguiente cuota supera el saldo pendiente"` porque `paymentAdjustedNextAmountInput` quedó congelado en `"50"`.
- **Comportamiento esperado:** Recalcular (o acotar) el valor autocompletado cuando cambia `paymentAmountInput` o `paymentTargetInstallmentId` si el usuario no lo editó manualmente.
- **Causa raíz:** `onPartialScheduleActionSelected` calcula el prefill una sola vez cuando `paymentAdjustedNextAmountInput.isBlank()`, y `onPaymentAmountChange` nunca lo refresca.
- **Corrección sugerida:** Llevar un flag `isAdjustedNextAmountManuallyEdited` y recalcular `paymentAdjustedNextAmountInput` en `onPaymentAmountChange` / `onPaymentTargetInstallmentSelected` mientras no haya edición manual.

---

### 22. `confirmDeleteLoan()` no limpia `editingLoanId` al eliminar el préstamo en edición
- **Severidad:** Baja-Media (consistencia de estado en `LoansViewModel`)
- **Test que expone el fallo:** `loansViewModel_confirmDeleteLoanWhileEditingSameLoan_mustClearEditingLoanId` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (`confirmDeleteLoan`, líneas `834-844`)
- **Entradas utilizadas:** `startEditingLoan(loan)` seguido de `requestDeleteLoan(loan)` y `confirmDeleteLoan()`.
- **Resultado obtenido:** `uiState.value.editingLoanId` conserva el `id` del préstamo eliminado.
- **Comportamiento esperado:** `editingLoanId` debe limpiarse a `null` (junto con el formulario si correspondía a ese `loan.id`).
- **Causa raíz:** El `copy(...)` de limpieza en `confirmDeleteLoan` limpia `editingScheduleLoan`, `payingLoan`, `editingPaymentId`, `historyLoanId` y `paymentToDelete`, pero omitió `editingLoanId`.
- **Corrección sugerida:** Si `current.editingLoanId == loan.id`, limpiar `editingLoanId = null` y restablecer los campos de edición.

---

### 23. Split-brain en recreación de proceso Android: *"Editar préstamo"* muta silenciosamente a *"Registrar préstamo"*
- **Severidad:** Alta (ciclo de vida Android / duplicación accidental de registros)
- **Test que expone el fallo:** `compose_loansView_recreatingViewModelWhileEditingLoan_mustNotMorphEditDialogIntoCreateDuplicateLoan` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/ui/LoansView.kt` (línea `82`: `var showAddDialog by rememberSaveable`) y `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (sin `SavedStateHandle`)
- **Entradas utilizadas:** Abrir *"Editar deuda"* sobre un préstamo existente (`showAddDialog = true`, `editingLoanId = id`) y recrear `LoansViewModel` simulando muerte/recreación de proceso.
- **Resultado obtenido:** `rememberSaveable` mantiene `showAddDialog = true`, pero `LoansViewModel` nace con `editingLoanId = null`, mostrando un diálogo vacío de *"Registrar préstamo"* que crea un duplicado en vez de editar.
- **Comportamiento esperado:** Mover la visibilidad del diálogo al `LoansUiState` (y/o persistir `editingLoanId` e inputs en `SavedStateHandle`) para que UI y ViewModel jamás queden desincronizados.
- **Causa raíz:** Estado dividido entre `rememberSaveable` (sobrevive muerte de proceso) y memoria volátil del `ViewModel` (se pierde sin `SavedStateHandle`).
- **Corrección sugerida:** Gestionar `showLoanFormDialog: Boolean` dentro de `LoansUiState` (o respaldar el estado de edición con `SavedStateHandle`).

---

### 24. Error silencioso en pantalla principal cuando falla `confirmDeleteLoan()`
- **Severidad:** Media (UX / manejo de errores de almacenamiento o reglas de negocio)
- **Test que expone el fallo:** `compose_loansView_deleteLoanFailure_mustSurfaceErrorOnMainScreen` (`LoanPaymentsAdversarialTest.kt`)
- **Ubicación en código productivo:** `app/src/main/java/com/example/money_saver_android/ui/LoansView.kt` (líneas `279-286`) y `app/src/main/java/com/example/money_saver_android/viewmodel/LoansViewModel.kt` (líneas `846-865`)
- **Entradas utilizadas:** Fallo al ejecutar `deleteLoan` tras confirmar `DeleteLoanConfirmationDialog`.
- **Resultado obtenido:** Se cierra el diálogo de borrado y `state.errorMessage` nunca se muestra en `LoansView`.
- **Comportamiento esperado:** Renderizar un banner de error / Snackbar / Toast visible para el usuario o mostrar el error en el diálogo de confirmación.
- **Causa raíz:** `LoansView` sólo pasa `state.errorMessage` a `AddLoanDialog`, que está cerrado durante el borrado.
- **Corrección sugerida:** Mostrar un banner/Snackbar de error en `LoansView` cuando `state.errorMessage != null && !showAddDialog` (o emitir un mensaje visible).

---

## Parte III — Cobertura Adversarial y Property-Based Complementaria

Además de los 24 defectos documentados arriba, las suites incluyen cobertura exhaustiva para proteger invariantes del sistema:

1. **Property-Based (`Loan` & `LoanInstallment`, 200 iteraciones):** validación ante pagos en centavos, ceros, negativos, `NaN`, `Infinity`, `-Infinity`, liquidación exacta y sobrepagos (`paidAmount`, `pendingAmount`, `progressFraction in 0f..1f`, `isSettled`).
2. **Property-Based (`previewLoanInstallmentsAfterPayment`, 200 iteraciones):** conservación exacta `BigDecimal` (`deltaPaid == min(payment, totalRemainingBefore)`), inmutabilidad de cuotas ya pagadas, prohibición de sobrepago por cuota, orden cronológico y prioridad estricta de `targetInstallmentId`.
3. **Property-Based (`calculateSavingsNetBalances` / `calculateCategoryAvailableSavings` / `buildSavingsNetSummary`, 200 iteraciones):** equivalencia algebraica exacta inter-funciones con variantes Unicode, mayúsculas/minúsculas, espacios y datos corruptos.
4. **Property-Based (`PartialPaymentScheduleOption`, 150 iteraciones):** conservación exacta `sum(installments.remainingAmount) == loan.pendingAmount` tras pagos parciales bajo `KeepCurrent`, `SetNewDueDate`, `AddInstallment` y `AdjustNextInstallment`.
5. **Estrés de Concurrencia Inter-Módulo (`30` corrutinas simultáneas):** carrera entre `ExpenseUseCase.saveExpense`, `LoanUseCase.saveLoan` y `LoanUseCase.deleteLoanPayment` compitiendo por el mismo `Mutex` sobre ahorros provenientes exclusivamente de cobros de préstamos (`inflows >= outflows` garantizado).
6. **Persistencia Room y Migración `1 -> 2 -> 3 -> 4`:** preservación de payloads con comillas/SQLi y acentos, renombre Unicode case-insensitive en préstamos y pagos, y `ON DELETE CASCADE` sobre `loan_installments` y `loan_payments`.
7. **Idempotencia y Resiliencia en `LoansViewModel`:** triple tap en `saveLoanPayment()`, doble tap en `confirmDeleteLoanPayment()`, recuperación tras `IllegalStateException` de almacenamiento sin perder inputs, y cierre atómico de modales al eliminar un préstamo.
8. **UI Compose, Cruce de Medianoche e i18n (`4` idiomas):** flujo de `overflow_installment_hint` -> `btn_payment_full_amount` -> transición de tarjeta a `Completado`, estrés con `30` pagos y nombres largos en `LoanPaymentHistorySheet`, actualización de fecha tras cruce de medianoche en `LoanScheduleAdversarialTest`, y validación de todas las nuevas claves y plantillas `String.format` (`%s`, `%d`, `%d%%`) en `es`, `en`, `de` y `pt`.
