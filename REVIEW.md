# Adversarial Security & Edge-Case Review — Story #14

**Feature:** Protección de acceso con desbloqueo nativo del teléfono (huella y/o PIN)  
**Suite adversarial:** `app/src/test/java/com/example/money_saver_android/adversarial/AppLockSecurityAdversarialTest.kt`  
**Comando de ejecución:**

```bash
./gradlew testDebugUnitTest --offline --tests "com.example.money_saver_android.adversarial.AppLockSecurityAdversarialTest"
```

**Resumen de ejecución:** 17 tests ejecutados — **10 pasaron**, **7 fallaron** (exponiendo 7 defectos reales en el código productivo, sin modificar el código fuente).

---

## Fallos encontrados (7 bugs confirmados por tests)

### 1. [CRÍTICO — Bypass de seguridad] Callback `onSuccess` obsoleto desbloquea una sesión bloqueada posterior
- **Test:** `adversarial_staleOnSuccessFromPreviousPrompt_mustNotUnlockNewLockedSession` (`AppLockSecurityAdversarialTest.kt:194-231`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/security/AppLockController.kt` (`L92-L109`)
- **Entradas / Secuencia:**
  1. `onAppLaunch()` inicia Prompt #1 y captura `onSuccess#1`.
  2. `onSuccess#1` desbloquea la sesión 1.
  3. `onAppBackgrounded()` → `onAppForegrounded()` bloquea la sesión 2 y lanza Prompt #2.
  4. Se invoca nuevamente el callback obsoleto `onSuccess#1`.
- **Resultado obtenido:** `uiState.value.isLocked` pasa a `false`, desbloqueando la sesión 2 sin validar Prompt #2.
- **Comportamiento esperado:** Los callbacks deben ligarse a un token/generación de solicitud de un solo uso; cualquier callback de una generación previa o ya consumida debe ignorarse y mantener `isLocked == true`.

### 2. [ALTO — Corrupción de estado] Callback `onFailureOrCancel` tardío rebloquea una sesión ya autenticada
- **Test:** `adversarial_staleOnFailureFromCompletedPrompt_mustNotRelockAuthenticatedSession` (`AppLockSecurityAdversarialTest.kt:238-270`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/security/AppLockController.kt` (`L92-L109`)
- **Entradas / Secuencia:** Prompt #1 dispara `onSuccess` (app desbloqueada) y posteriormente llega un `onFailureOrCancel` tardío del mismo prompt.
- **Resultado obtenido:** `isLocked = true` y `showAuthError = true` sobre una sesión que ya había sido autenticada.
- **Comportamiento esperado:** Ignorar el callback tardío (`isLocked == false`, `showAuthError == false`).

### 3. [ALTO — Bloqueo permanente / Liveness] Excepción síncrona en `launchDeviceAuth` deja `isAuthenticating = true` indefinidamente
- **Test:** `adversarial_exceptionInLaunchDeviceAuth_mustTransitionToRetryableErrorState` (`AppLockSecurityAdversarialTest.kt:278-315`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/security/AppLockController.kt` (`L92-L109`)
- **Entradas / Secuencia:** `launchDeviceAuth` lanza `IllegalStateException("FragmentManager state already saved")` durante `onAppLaunch()`.
- **Resultado obtenido:** `isAuthenticating` queda atascado en `true` y `showAuthError` en `false`; `retryAuthentication()` retorna temprano sin relanzar autenticación y `AppLockScreen` no muestra el botón de reintento.
- **Comportamiento esperado:** Capturar fallos al lanzar el autenticador y transicionar a `isLocked = true`, `isAuthenticating = false`, `showAuthError = true` para permitir reintentar.

### 4. [ALTO — Bloqueo permanente / Liveness] Ir a segundo plano mientras `isAuthenticating == true` deja la pantalla de bloqueo sin prompt ni botón de reintento
- **Test:** `adversarial_backgroundingWhileAuthenticatingAndReturning_mustNotStrandUserWithoutPromptOrRetry` (`AppLockSecurityAdversarialTest.kt:325-355`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/security/AppLockController.kt` (`L37-L63`)
- **Entradas / Secuencia:** `onAppLaunch()` (`isAuthenticating = true`) → `onAppBackgrounded()` → `onAppForegrounded()` cuando el sistema descartó el diálogo sin invocar `onAuthenticationError`.
- **Resultado obtenido:** `launchCount == 1`, `isAuthenticating == true`, `showAuthError == false`: el usuario queda atrapado en la vista de bloqueo sin prompt activo ni botón de reintento.
- **Comportamiento esperado:** Al volver a primer plano tras haber pasado por segundo plano, relanzar la autenticación (`launchCount >= 2`) o exponer el error con botón de reintento (`showAuthError == true`).

### 5. [ALTO — Crash] `NativeBiometricAuthenticator` lanza `IllegalArgumentException` no capturada si el título está vacío
- **Test:** `adversarial_nativeBiometricAuthenticator_emptyTitleMustInvokeFailureCallbackInsteadOfCrashing` (`AppLockSecurityAdversarialTest.kt:427-452`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/security/NativeBiometricAuthenticator.kt` (`L53-L65`)
- **Entradas / Secuencia:** `titleProvider = { "" }` con `DeviceAuthCapability.BIOMETRIC_AND_PIN`.
- **Resultado obtenido:** `BiometricPrompt.PromptInfo.Builder.build()` se ejecuta fuera del `try/catch` y lanza `java.lang.IllegalArgumentException: Title must be set and non-empty.`, provocando crash.
- **Comportamiento esperado:** Evitar el crash (aplicando título fallback no vacío o moviendo `build()` dentro del `try/catch`) e invocar `onFailureOrCancel()` de forma segura.

### 6. [MEDIO — Inconsistencia UI/Estado] Switch atascado en `On` y `Disabled` si se elimina el bloqueo de pantalla del sistema
- **Test:** `adversarial_removingSystemScreenLock_mustNotLeaveSwitchStuckCheckedAndDisabled` (`AppLockSecurityAdversarialTest.kt:506-539`)
- **Archivos afectados:**
  - `app/src/main/java/com/example/money_saver_android/viewmodel/SettingsViewModel.kt` (`L135-L138`)
  - `app/src/main/java/com/example/money_saver_android/ui/components/settings/SecuritySectionCard.kt` (`L91-L95`)
- **Entradas / Secuencia:** Usuario activa protección (`isAppLockEnabled = true`) con `PIN_ONLY` → elimina el PIN en ajustes del sistema (`DeviceAuthCapability.NONE`) → `refreshDeviceAuthCapability()` y se renderiza `SettingsView`.
- **Resultado obtenido:** El nodo `switch_app_lock` muestra `ToggleableState = 'On'` y `[Disabled]` simultáneamente, dejando un estado visual contradictorio que el usuario no puede desactivar.
- **Comportamiento esperado:** Cuando `deviceAuthCapability == NONE`, el switch deshabilitado debe mostrarse en `Off` (reflejando que la protección no está activa).

### 7. [CRÍTICO — Bypass de seguridad en API 24–27] Muerte de proceso en segundo plano recrea `MainActivity` desbloqueada
- **Test:** `adversarial_api27_processDeathAfterBackground_mustNotBypassAppLockOnRecreation` (`AppLockSecurityAdversarialTest.kt:556-590`)
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/MainActivity.kt` (`L334-L361`)
- **Entradas / Secuencia:** `@Config(sdk = [27])` (dentro de `minSdk = 24`), `app_lock_enabled = true`. En API 24–27 Android ejecuta `onSaveInstanceState()` **antes** que `onStop()`, guardando `app_lock_is_locked = false`. Tras muerte de proceso en segundo plano, Android recrea `MainActivity` con ese `Bundle`.
- **Resultado obtenido:** `restoreLockedState(wasLocked = false)` deja `isLocked == false` y expone los datos financieros sin pedir autenticación.
- **Comportamiento esperado:** Distinguir rotación dentro del mismo proceso de una recreación tras muerte de proceso (p. ej. estado en memoria a nivel de proceso / `ViewModel` no persistido entre procesos) para que toda recreación tras muerte de proceso inicie bloqueada cuando `app_lock_enabled == true`.

---

## Hallazgo adicional de auditoría (sin test dedicado de diálogo de permisos del SO)

### 8. [ALTO — Riesgo de bypass] Fuga de `suppressNextBackgroundLock()` al solicitar permiso `POST_NOTIFICATIONS`
- **Archivo afectado:** `app/src/main/java/com/example/money_saver_android/MainActivity.kt` (`L294-L300`)
- **Detalle:** `requestNotificationPermissionIfNeeded()` invoca `appLockController.suppressNextBackgroundLock()` antes de lanzar `ActivityResultContracts.RequestPermission`. En Android 13+, el diálogo del sistema es translúcido y normalmente dispara `onPause()`/`onResume()` **sin** disparar `onStop()`/`onStart()`. Como la bandera solo se limpia en `onAppForegrounded()` (`onStart()`), `suppressNextBackgroundLock` puede quedar en `true` hasta el próximo paso real a segundo plano del usuario, omitiendo indebidamente el bloqueo.
- **Recomendación:** Limpiar la supresión también en el callback de resultado de los launchers (`requestNotificationPermissionLauncher`, `createBackupLauncher`, `openBackupLauncher`) o en `onResume()` cuando no ocurrió `onStop()`.

---

## Invariantes y pruebas que pasaron (10/17)

1. `repository_corruptedSharedPreferencesTypes_safelyDefaultToFalseAndRecoverOnWrite` — Corrupción de tipos en `SharedPreferences` (`String`, `Int`, `Long`, `Float`, `StringSet`) retorna `false` de forma segura y recupera tras escritura válida.
2. `property_repository_lastWriteAlwaysWinsAcross200RandomToggles` — PBT (200 iteraciones): persistencia consistente en `SharedPrefsAppLockPreferenceRepository`.
3. `property_deviceAuthCapability_invariantsHoldAcross300RandomStatusCombinations` — PBT (300 combinaciones de códigos `BiometricManager` + valores extremos + `isDeviceSecure` `true`/`false`/`null`).
4. `property_appLockController_neverLocksWhenDisabled_andAlwaysLocksOnUnsuppressedBackground` — PBT (500 transiciones aleatorias de la máquina de estados).
5. `adversarial_nativeBiometricAuthenticator_pinOnlyOnApi24_doesNotThrowUnsupportedCombination` — Compatibilidad de `PIN_ONLY` en API 24 sin error de `AuthenticatorUtils`.
6. `adversarial_nativeBiometricAuthenticator_noneCapabilityImmediatelyFails` — Capacidad `NONE` invoca `onFailureOrCancel` inmediatamente sin desbloquear.
7. `adversarial_backupExportAndRestore_neverIncludeOrOverwriteAppLockPreference` — Garantía fuera de alcance: `BackupUseCase` no exporta ni sobrescribe `app_lock_enabled`.
8. `property_appLockGate_neverExposesFinancialContentWhileLockedAcrossAllLocalesAndStates` — PBT Compose en los 4 idiomas (`es`, `en`, `de`, `pt`): contenido financiero nunca existe en el árbol de semántica mientras `isLocked == true`.
9. `adversarial_rapidDoubleClickOnRetryButton_triggersOnlySingleAuthAttempt` — Doble clic rápido en `btn_app_lock_retry` dispara una única solicitud de autenticación.
10. `adversarial_suppressNextBackgroundLock_whileLockedMustNotBypassLockOnForeground` — `suppressNextBackgroundLock()` invocado mientras ya está bloqueado no desbloquea la app.
