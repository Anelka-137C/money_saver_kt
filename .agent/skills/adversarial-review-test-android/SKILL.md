---
name: adversarial-review-test-android
description: Revisa código Android en busca de edge cases y genera pruebas basadas en propiedades (property-based testing) y tests unitarios adversariales en Kotlin/Android sin ejecutarlos.
---

# Adversarial Review & Test for Android

Skill especializada en realizar revisiones de código adversariales y generación de pruebas basadas en propiedades (Property-Based Testing) y casos límite extremos para componentes y lógica de negocio en Android (Kotlin/Java).

## Cuándo usar esta Skill
- Cuando se requiere revisar código de Android (Kotlin/Java) para detectar edge cases, vulnerabilidades, fugas de memoria o errores de concurrencia.
- Cuando se necesita diseñar pruebas de propiedades (invariantes) para lógica de dominio, transformaciones de datos o máquinas de estado.
- Cuando se busca complementar la suite de pruebas unitarias con casos extremos antes de integrar cambios en producción.

## Instrucciones de Operación

### 1. Revisión de Código Adversarial (Android-Specific)
Analiza agresivamente el código buscando fallos en:
- **Ciclo de vida y Estado de Android**: Pérdida de estado en recreación (`onSaveInstanceState`, `SavedStateHandle`), memory leaks (referencias a `Context`/`View`), inconsistencias en Compose o ViewModels.
- **Coroutines y Concurrencia**: Manejo de cancelaciones (`CancellationException`), `Dispatchers` inadecuados, condiciones de carrera (`Mutex`, `StateFlow`, `SharedFlow`), concurrencia en colecciones mutables.
- **Límites y Tipos de Datos**: Nulos no contemplados (`nullability` en interoperabilidad con Java/plataforma), desbordamientos numéricos, colecciones vacías o masivas, strings con caracteres especiales o formatos inesperados.
- **Manejo de Errores y Excepciones**: Fallas no controladas en llamadas de red, parsing de JSON/modelos, desincronización de bases de datos locales (Room).

### 2. Diseño de Pruebas Basadas en Propiedades (Property-Based Testing)
- Genera tests orientados a **invariantes** (reglas que deben cumplirse siempre, sin importar la entrada generada).
- Utiliza frameworks estándar del ecosistema Kotlin/Android, preferentemente:
  - **Kotest Property Testing** (`checkAll`, `forAll`, generadores `Arb.*`).
  - **jqwik** (si el proyecto usa JUnit 5).
- Define generadores personalizados (`Arb`) para modelos de dominio de Android complejos cuando aplique.
- Asegúrate de cubrir generadores con casos límite (valores frontera como 0, negativos, máximos, mínimos, strings vacíos).

### 3. Restricción Estricta: NO EJECUTAR
- **PROHIBIDO** ejecutar los tests generados mediante comandos de terminal (Gradle, adb, emuladores, etc.).
- La función de esta skill es exclusivamente de **diseño, análisis y generación** de código de prueba limpio, documentado y listo para copiar/pegar o integrar.
- Entrega instrucciones claras al desarrollador para que ejecute los tests en su entorno local o CI/CD (ej. `./gradlew test`).

## Buenas Prácticas
- Proporciona código de prueba completamente autocontenido y listo para usar (imports incluidos).
- Documenta claramente qué propiedad o invariante protege cada test y cuál es el edge case evaluado.
