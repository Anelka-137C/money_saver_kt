# Adversarial Code Review Report — Google Drive Backup, Restore & Reminder Notifications

**Fecha:** 2026-11-26  
**Alcance (detectado vía `git status` / `git diff`):** Respaldo en Google Drive (`money_saver_backup`), restauración completa desde JSON y recordatorios periódicos cada 7 días / 8 horas diurnas.  
**Suite generada:** `app/src/test/java/com/example/money_saver_android/adversarial/BackupAndRestoreAdversarialTest.kt`  
**Código productivo modificado:** Ninguno (solo lectura y generación de pruebas).

---

## Resumen Ejecutivo

Se identificaron **16 defectos / vulnerabilidades de borde** clasificados por severidad:
- **Críticos (2):** Corrupción *split-brain* ante fallo parcial de restauración multi-BD; sobreescritura de moneda con literal `"null"` por `org.json.JSONObject.optString`.
- **Altos (6):** Montos negativos/cero aceptados al restaurar; overflow de `Long` en cálculo de recordatorios; falta de exclusión mutua Backup/Restore en `SettingsViewModel`; corrupción de estado activo con `clear*`/`cancel*`; excepción no capturada del scheduler dejando la UI bloqueada en `IN_PROGRESS`; crash `ClassCastException` ante tipo corrupto en `SharedPreferences`.
- **Medios (6):** Moneda no soportada aceptada en restore; campos requeridos en blanco y fechas invertidas aceptados; duplicación de registros ante períodos duplicados en export; deriva de 8 horas en reprogramación nocturna tras 7 días; falta de validación de `Intent.action` en `BackupReminderReceiver`; duplicación de subcategorías en Room `replaceAllCategories`.
- **Bajos (2):** Timestamps `<= 0L` aceptados por `BackupReminderScheduleCalculator`; renderizado de fecha epoch `1970-01-01` en `BackupSectionCard` para `lastBackupTimestamp <= 0L`.

---

## Defectos Expuestos por la Suite Adversarial

### Críticos

#### 1. Restauración no atómica entre 4 bases de datos Room (Pérdida parcial / Split-Brain)
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L221-L248`)
- **Entrada adversarial:** Fallo de escritura (ej. `SQLiteFullException`) en `expenseRepository.replaceAllExpenses` durante `restoreBackup`.
- **Comportamiento actual:** `ConfigDatabase` (períodos), `AppDatabase` (categorías e ingresos) y `BudgetDatabase` (presupuestos) ya fueron borrados y reemplazados, mientras que gastos, ahorros y preferencias conservan los datos antiguos, dejando referencias huérfanas (`periodId`, `budgetId`, `incomeId`).
- **Comportamiento esperado:** Realizar snapshot previo / rollback ante fallo parcial para preservar consistencia total.
- **Test:** `adversarial_restoreBackup_midFlightFailure_mustRollbackAlreadyReplacedRepositories`

#### 2. `org.json.JSONObject.optString` convierte `null` JSON en el literal `"null"`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L107-L186`, `L238-L240`, `L265-L266`)
- **Entrada adversarial:** Respaldo JSON con `"currency": null`, `"language": null`, `"subcategory": null`, `"description": null`, `"icon": null`, `"color": null`.
- **Comportamiento actual:** En Android `org.json`, `optString("currency", "")` retorna `"null"` (4 caracteres). Como `"null".isNotBlank()` es `true`, `restoreBackup` guarda `"null"` como moneda del usuario y propaga `"null"` en campos opcionales.
- **Comportamiento esperado:** Verificar `isNull(key)` antes de leer `optString`, tratando valores `JSONObject.NULL` como cadenas vacías o valores por defecto y conservando la moneda actual.
- **Test:** `adversarial_parseBackupJson_explicitJsonNullFields_mustNotBecomeLiteralNullString`

---

### Altos

#### 3. Montos negativos y cero aceptados en `parseBackupJson`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L123-L174`)
- **Entrada adversarial:** `amount = -500.0`, `0.0`, `-0.01` en ingresos, presupuestos, gastos o ahorros.
- **Comportamiento actual:** Solo se valida `amount.isFinite()`, devolviendo `RestoreParseResult.Valid` y corrompiendo balances contables tras restaurar.
- **Comportamiento esperado:** Exigir `amount.isFinite() && amount > 0.0` (o rechazar como `RestoreParseResult.Invalid`).
- **Test:** `adversarial_parseBackupJson_rejectsNegativeAndZeroFinancialAmounts`

#### 4. Overflow aritmético de `Long` en `BackupReminderScheduleCalculator`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupReminderScheduleCalculator.kt` (`L32`, `L41`, `L45`)
- **Entrada adversarial:** `lastBackupTimestamp = Long.MAX_VALUE - 1000L` (en `shouldNotifyNow`) o `nowMillis = Long.MAX_VALUE - 1000L` (en `nextReminderTimeMillis`).
- **Comportamiento actual:** `lastBackupTimestamp + SEVEN_DAYS_MILLIS` desborda a un `Long` negativo, haciendo que `shouldNotifyNow` retorne `true` pese a estar en el futuro; `nowMillis + REMINDER_INTERVAL_MILLIS` programa una alarma en el pasado.
- **Comportamiento esperado:** Evitar overflow mediante comparaciones por sustracción segura o saturación.
- **Test:** `adversarial_scheduleCalculator_nearLongMaxValue_mustNotOverflow`

#### 5. Falta de exclusión mutua entre Backup y Restore en `SettingsViewModel`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/viewmodel/SettingsViewModel.kt` (`L114-L157`, `L201-L208`)
- **Entrada adversarial:** Invocar `prepareRestore` mientras `backupStatus == BackupStatus.IN_PROGRESS`, o `performBackup` mientras `restoreStatus == RestoreStatus.READING` / `RESTORING`.
- **Comportamiento actual:** Cada método solo valida su propio estado, permitiendo que Backup y Restore corran en paralelo dentro del mismo `SettingsUiState`.
- **Comportamiento esperado:** Bloquear cualquier inicio de backup o restore si la otra operación está activa.
- **Test:** `adversarial_settingsViewModel_backupAndRestoreMustBeMutuallyExclusive`

#### 6. `clear*` / `cancel*` corrompen operaciones en curso en `SettingsViewModel`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/viewmodel/SettingsViewModel.kt` (`L140-L195`)
- **Entrada adversarial:** Llamar `clearBackupStatus()` / `onBackupCancelled()` durante `BackupStatus.IN_PROGRESS`, o `cancelPendingRestore()` durante `RestoreStatus.READING`.
- **Comportamiento actual:** Sobrescribe `IN_PROGRESS` a `IDLE`/`CANCELLED` mientras la corrutina sigue escribiendo (rehabilitando botones y permitiendo doble ejecución); una lectura cancelada sigue poblando `pendingRestoreSnapshot` cuando finaliza, reabriendo el diálogo de confirmación.
- **Comportamiento esperado:** Ignorar resets/cancelaciones no válidos sobre estados activos y descartar resultados obsoletos si la lectura fue cancelada.
- **Test:** `adversarial_settingsViewModel_cancelOrClearWhileBusy_mustNotCorruptActiveOperation`

#### 7. Excepción en `BackupReminderScheduler` bloquea la UI permanentemente en `IN_PROGRESS`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/viewmodel/SettingsViewModel.kt` (`L124-L131`)
- **Entrada adversarial:** `backupReminderScheduler.scheduleNextReminder(...)` lanza `SecurityException` o `RuntimeException` tras un `BackupResult.Success`.
- **Comportamiento actual:** Al ejecutarse sin `try/catch` antes de `_uiState.update`, la corrutina falla de forma no controlada y `backupStatus` queda permanentemente en `IN_PROGRESS` (botones deshabilitados indefinidamente).
- **Comportamiento esperado:** Encapsular la programación del recordatorio en `runCatching` / `try-catch` (sin cancelar `CancellationException`) y completar la transición a `BackupStatus.SUCCESS`.
- **Test:** `adversarial_settingsViewModel_schedulerThrowsOnBackupSuccess_mustNotLeaveUiStuckInProgress`

#### 8. Borrado silencioso con `<= 0L` y `ClassCastException` en `SharedPrefsBackupPreferenceRepository`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/data/repository/BackupPreferenceRepository.kt` (`L21-L29`)
- **Entrada adversarial:** `saveLastBackupTimestamp(0L)` tras un valor válido; entrada corrupta tipo `String` en `SharedPreferences` bajo la clave `"last_backup_timestamp"`.
- **Comportamiento actual:** Guardar `0L` borra silenciosamente un timestamp válido previo; leer una entrada corrupta lanza `ClassCastException` no controlada.
- **Comportamiento esperado:** Ignorar/rechazar guardados con `timestampMillis <= 0L` y manejar `ClassCastException` devolviendo `null`.
- **Test:** `adversarial_backupPreferenceRepository_boundaryValuesAndCorruptedTypeHandling`

---

### Medios

#### 9. Código de moneda no validado al restaurar
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L238-L240`) y `SettingsViewModel.kt` (`L221`)
- **Entrada adversarial:** `currency = "FAKE_COIN_999"`.
- **Comportamiento actual:** Se persiste y se asigna a `selectedCurrency` cualquier cadena no vacía aunque no pertenezca a las divisas soportadas.
- **Comportamiento esperado:** Validar el código contra las monedas soportadas y conservar la moneda actual si no es válida.
- **Test:** `adversarial_restoreBackup_unsupportedCurrencyCode_mustNotOverwriteValidPreference`

#### 10. Campos obligatorios en blanco y fechas de período invertidas aceptados en `parseBackupJson`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L113-L186`)
- **Entrada adversarial:** `category = "   "` en gastos/ingresos/presupuestos/ahorros; período con `startDate = "2026-12-31"` y `endDate = "2026-01-01"`.
- **Comportamiento actual:** Se aceptan como `RestoreParseResult.Valid`.
- **Comportamiento esperado:** Rechazar campos obligatorios vacíos/en blanco y rangos `startDate > endDate` como `RestoreParseResult.Invalid`.
- **Test:** `adversarial_parseBackupJson_rejectsSchemaTamperingAndBlankRequiredFields`

#### 11. Duplicación de ingresos y presupuestos en `buildBackupJson` ante IDs de período repetidos
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupUseCase.kt` (`L274-L279`)
- **Entrada adversarial:** `periodRepository.getAllPeriods()` retorna dos entradas con el mismo `id = 1L`.
- **Comportamiento actual:** `periods.flatMap { ... }` duplica todos los ingresos y presupuestos de ese período en el JSON exportado.
- **Comportamiento esperado:** Deduplicar los IDs de período (`.distinctBy { it.id }`) antes de consultar ingresos/presupuestos.
- **Test:** `adversarial_buildBackupJson_duplicatePeriodIds_mustNotDuplicateIncomesOrBudgets`

#### 12. Deriva de 8 horas al reprogramar en horario nocturno tras superar 7 días
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupReminderScheduleCalculator.kt` (`L41-L47`)
- **Entrada adversarial:** Último backup a las `02:00 UTC`; reinicio (`ACTION_BOOT_COMPLETED`) 7 días después a las `07:55 UTC` (noche, 5 min antes de abrir la ventana diurna).
- **Comportamiento actual:** Al cumplirse `nowMillis >= sevenDaysAfter`, suma `8 horas` (`15:55 UTC`) en lugar de ajustar al inicio diurno (`08:00 UTC`).
- **Comportamiento esperado:** Si es horario nocturno y el recordatorio está vencido (`!isDaytime(nowMillis)`), programar al próximo inicio diurno (`08:00`).
- **Test:** `adversarial_scheduleCalculator_nighttimeRescheduleAfterSevenDays_targets0800InsteadOfDelaying8Hours`

#### 13. `BackupReminderReceiver` no valida que `intent?.action == ACTION_BACKUP_REMINDER`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/notifications/BackupReminderReceiver.kt` (`L37-L40`)
- **Entrada adversarial:** `onReceive(context, null)` o `Intent("com.example.UNRELATED_SPOOFED_ACTION")`.
- **Comportamiento actual:** Solo excluye `Intent.ACTION_BOOT_COMPLETED`, por lo que cualquier acción desconocida o `null` publica la notificación.
- **Comportamiento esperado:** Mostrar notificación únicamente cuando `intent?.action == AndroidBackupReminderScheduler.ACTION_BACKUP_REMINDER`.
- **Test:** `adversarial_reminderReceiver_nullOrUnexpectedIntentAction_mustNotPostNotification`

#### 14. Subcategorías duplicadas al reemplazar categorías repetidas en Room
- **Ubicación:** `RoomCategoryRepository.kt` (`L105-L115`), `IncomeRepository.kt` (`L65-L75`), `BudgetRepository.kt` (`L67-L78`), `SavingsRepository.kt` (`L74-L85`)
- **Entrada adversarial:** Lista con categorías repetidas (`"Comida"` dos veces) y/o subcategorías repetidas (`"Super"`).
- **Comportamiento actual:** `insertCategories` reemplaza la PK `"Comida"`, pero `subEntities` inserta todas las filas de subcategorías duplicadas (porque `SubcategoryEntity` usa PK autogenerada sin unicidad `(categoryName, name)`).
- **Comportamiento esperado:** Deduplicar categorías por nombre y subcategorías por par `(categoryName, name)` antes de insertar.
- **Test:** `adversarial_roomReplaceAll_emptyWipe_sqlInjection_andDuplicateCategoryDeduplication`

---

### Bajos

#### 15. `BackupReminderScheduleCalculator` acepta `lastBackupTimestamp <= 0L`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/domain/usecase/BackupReminderScheduleCalculator.kt` (`L26-L48`)
- **Entrada adversarial:** `lastBackupTimestamp` en `{0L, -1L, Long.MIN_VALUE}`.
- **Comportamiento actual:** Evalúa como respaldo de 1970 o anterior, retornando `shouldNotifyNow = true` y programando alarma.
- **Comportamiento esperado:** Tratar `lastBackupTimestamp == null || lastBackupTimestamp <= 0L` como sin respaldo (`false` / `null`).
- **Test:** `adversarial_scheduleCalculator_nonPositiveLastBackupTimestamp_treatedAsUnbackedUp`

#### 16. `BackupSectionCard` muestra fecha epoch `1970-01-01` para `lastBackupTimestamp <= 0L`
- **Ubicación:** `app/src/main/java/com/example/money_saver_android/ui/components/settings/BackupSectionCard.kt` (`L67-L71`)
- **Entrada adversarial:** `lastBackupTimestamp = 0L` o negativo.
- **Comportamiento actual:** Formatea `Date(0L)` (`1970-01-01` / `1969-12-31`), a diferencia de `RestoreBackupConfirmationDialog` que valida `> 0L`.
- **Comportamiento esperado:** Validar `it > 0L` y mostrar `neverBackedUpText` en caso contrario.
- **Test:** `adversarial_backupSectionCard_zeroOrNegativeTimestamp_displaysNeverBackedUpInsteadOf1970`

---

## Cobertura de Property-Based Testing e Invariantes Verificadas

1. **`pbt_nextReminderTimeMillis_alwaysDaytime_strictlyAfterNow_andAtLeastSevenDaysAfterBackup`** (500 iteraciones en 9 zonas horarias incluyendo `UTC+05:30`, `UTC+05:45`, `UTC+14:00` y zonas DST):
   - Invariante 1: El resultado siempre cae en ventana diurna `[08:00, 22:00)`.
   - Invariante 2: El resultado es siempre `>= lastBackupTimestamp + 7 días`.
   - Invariante 3: El resultado es siempre estrictamente mayor que `nowMillis`.
2. **`pbt_shouldNotifyNow_matchesSevenDayThresholdAndDaytimeWindow`** (500 iteraciones): equivalencia exacta con el umbral de 7 días y horario diurno.
3. **`pbt_buildAndParseBackupJson_roundTripPreservesAllRecordsAndAdversarialStrings`** (80 iteraciones): fidelidad ida y vuelta con emojis compuestos, texto RTL árabe/hebreo, comillas escapadas, saltos de línea y payloads tipo SQL injection.
4. **`pbt_exportBackup_neverMutatesLastBackupTimestampOnWriteFailure`** (50 iteraciones): invariante de preservación de `lastBackupTimestamp` ante cualquier excepción de escritura.
5. **Concurrencia, Android y Compose:**
   - Transiciones DST primavera/otoño (`America/New_York`).
   - Propagación de `CancellationException` sin tragarla en `exportBackup` y `restoreBackup`.
   - Serialización estricta bajo `allocationMutex` entre export y restore concurrentes.
   - Manejo seguro de `Double.NaN` durante exportación.
   - Doble confirmación rápida de restore (`confirmRestore`) idempotente.
   - `ACTION_BOOT_COMPLETED`, falta de permiso `POST_NOTIFICATIONS` en API 34 e idempotencia de `AndroidBackupReminderScheduler`.
   - Bloqueo mutuo de botones en `BackupSectionCard`, plantillas `String.format` corruptas en `RestoreBackupConfirmationDialog` y navegación por deep-link `requestedTab` en `MainContainer` con período bloqueado.

---

## Instrucciones de Ejecución Local / CI

```bash
# Ejecutar solo la suite adversarial de Backup & Restore
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.BackupAndRestoreAdversarialTest"

# Ejecutar toda la suite unitaria del proyecto
./gradlew testDebugUnitTest
```
