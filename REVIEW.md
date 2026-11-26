# Reporte de Revisión Adversarial — `PeriodSummaryDonutChart`

**Fecha:** 2026-11-26  
**Alcance analizado (vía `git diff`):**
- [`app/src/main/java/com/example/money_saver_android/ui/components/atom/PeriodSummaryDonutChart.kt`](app/src/main/java/com/example/money_saver_android/ui/components/atom/PeriodSummaryDonutChart.kt)
- Dependencia directa: [`app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt`](app/src/main/java/com/example/money_saver_android/domain/usecase/CategorySummary.kt)

**Suite adversarial generada:**
- [`app/src/test/java/com/example/money_saver_android/adversarial/PeriodSummaryDonutChartAdversarialTest.kt`](app/src/test/java/com/example/money_saver_android/adversarial/PeriodSummaryDonutChartAdversarialTest.kt)

> **Nota de operación:** El código productivo en `app/src/main` no fue modificado. Los tests fueron diseñados y escritos listos para ejecutarse localmente o en CI/CD (sin ejecución automática, respetando la directriz `NO EJECUTAR` de `adversarial-review-test-android`).

---

## 1. Hallazgos y Fallos Detectados

### Bug #1 (Alto) — `totalAmount` no finito (`Double.NaN` / `Double.POSITIVE_INFINITY`) evade `summary.isEmpty`

- **Ubicación:**
  - `PeriodSummaryDonutChart.kt` (línea 53: `if (summary.isEmpty || !progress.isFinite()) return emptyList()`)
  - `CategorySummary.kt` (líneas 30–31: `val isEmpty: Boolean get() = items.isEmpty() || totalAmount <= 0.0`)
- **Entradas que lo reproducen:**
  - `PeriodCategorySummary(totalAmount = Double.NaN, items = listOf(CategorySummaryItem("CorruptNaN", "#EF4444", 100.0, 1.0f, 100)))`, `progress = 1f`
  - `PeriodCategorySummary(totalAmount = Double.POSITIVE_INFINITY, items = listOf(CategorySummaryItem("CorruptInf", "#EF4444", Double.MAX_VALUE, 1.0f, 100)))`, `progress = 1f`
- **Resultado obtenido:** En IEEE-754 tanto `Double.NaN <= 0.0` como `Double.POSITIVE_INFINITY <= 0.0` evalúan a `false`. Por lo tanto, `summary.isEmpty` es `false` y `calculateAnimatedDonutSegments` emite un segmento de `360°`.
- **Comportamiento esperado:** Un resumen con `totalAmount` no finito (`!summary.totalAmount.isFinite()`) debe tratarse como inválido/vacío y devolver `emptyList()`.
- **Test que lo expone:** `adversarial_corrupt_nan_and_infinite_totalAmount_must_return_empty_segments`

---

### Bug #2 (Alto) — El último ítem ignora `proportion` y se apropia de todo `remainingCircle` aunque su proporción sea `0f`, negativa o `Float.NaN`

- **Ubicación:** `PeriodSummaryDonutChart.kt` (líneas 63–68)
- **Entradas que lo reproducen:**
  - `PeriodCategorySummary(totalAmount = 100.0, items = listOf(CategorySummaryItem("ZeroSlice", "#EF4444", 0.0, 0f, 0)))`
  - `PeriodCategorySummary(totalAmount = 100.0, items = listOf(CategorySummaryItem("NaNSlice", "#EF4444", 100.0, Float.NaN, 0)))`
  - `PeriodCategorySummary(totalAmount = 100.0, items = listOf(CategorySummaryItem("ValidHalf", "#1CC88A", 50.0, 0.5f, 50), CategorySummaryItem("NegativeTrailing", "#EF4444", 0.0, -1.0f, 0)))`
- **Resultado obtenido:** Cuando `index == summary.items.lastIndex`, se asigna `fullSweep = remainingCircle` sin validar `item.proportion`. Esto permite que una categoría con proporción `0f`, negativa o `Float.NaN` se dibuje ocupando hasta `180°` o `360°` del gráfico.
- **Comportamiento esperado:** Ningún ítem con `!item.proportion.isFinite()` o `item.proportion <= 0f` debe renderizarse con un barrido positivo, incluso si ocupa la última posición de `summary.items`.
- **Test que lo expone:** `adversarial_last_item_with_zero_negative_or_nan_proportion_must_not_steal_remaining_circle`

---

### Bug #3 (Medio) — Deriva acumulativa de precisión `Float` entre `startAngle` y `accumulatedSweep`

- **Ubicación:** `PeriodSummaryDonutChart.kt` (líneas 80–81: `startAngle += fullSweep` y `accumulatedSweep += fullSweep`)
- **Entradas que lo reproducen:** `10_000` categorías iguales con `proportion = 1f / 10_000f` y `progress = 1f`.
- **Resultado obtenido:** `startAngle` acumula incrementos partiendo desde `-90f` mientras `accumulatedSweep` acumula desde `0f` (con distinta resolución ULP en IEEE-754 de 32 bits). Al cerrar el último segmento con `fullSweep = 360f - accumulatedSweep` desde `startAngle`, el ángulo final `last.startAngle + last.sweepAngle` deriva respecto de `270f` (`-90f + 360f`).
- **Comportamiento esperado:** Calcular `startAngle = -90f + accumulatedSweep` (o acumular en `Double`) para garantizar cierre preciso en `270f` dentro de tolerancia `<= 0.001f`.
- **Test que lo expone:** `adversarial_massive_10000_tiny_segments_must_close_precisely_at_270_degrees`

---

### Riesgo #4 (Bajo / Layout Compose) — Diámetro negativo en `Canvas` bajo restricciones menores a `16.dp`

- **Ubicación:** `PeriodSummaryDonutChart.kt` (líneas 121–123: `val diameter = size.minDimension - strokeWidth`)
- **Escenario:** Si el contenedor padre impone un tamaño estricto menor que `strokeWidth` (`16.dp`), por ejemplo `Modifier.size(8.dp)` o `Modifier.size(0.dp)`, `diameter` resulta negativo y se invoca `drawArc` con `Size(diameter, diameter)` negativo.
- **Recomendación:** Proteger `if (diameter <= 0f) return@Canvas` (o acotar `strokeWidth`).
- **Test de cobertura:** `compose_tight_constraints_smaller_than_stroke_width_does_not_crash_canvas`

---

## 2. Cobertura de la Suite Adversarial (`PeriodSummaryDonutChartAdversarialTest`)

### Property-Based Testing (Invariantes)
- `property_simultaneous_expansion_invariants_across_500_randomized_summaries` (500 resúmenes aleatorios de 1 a 24 categorías × 7 puntos de progreso):
  - Finitud y acotación: `startAngle in -90f..270f`, `sweepAngle in (0f, 360f]`.
  - Anclaje fijo simultáneo: `startAngle(p) == startAngle(1f)` para todo `p in (0, 1]`.
  - Escalado lineal proporcional: `sweep(p) == sweep(1f) * p`.
  - No solapamiento estricto entre segmentos adyacentes en todo `p`.
  - Suma visible igual a `360f * p`.
  - Monotonicidad no decreciente conforme crece `p`.
  - Cierre contiguo completo de `360°` (`-90f` a `270f`) en `p = 1f`.
- `property_clamping_and_invalid_progress_invariants_across_200_trials` (200 iteraciones):
  - `progress <= 0f`, `-0.0f`, `-Float.MIN_VALUE`, `-Float.MAX_VALUE`, `Float.NaN`, `±Infinity` → siempre `emptyList()`.
  - `progress > 1f` finito (hasta `Float.MAX_VALUE`) → resultado idéntico a `progress = 1f`.

### Casos Límite Numéricos
- `adversarial_corrupt_nan_and_infinite_totalAmount_must_return_empty_segments`
- `adversarial_last_item_with_zero_negative_or_nan_proportion_must_not_steal_remaining_circle`
- `adversarial_massive_10000_tiny_segments_must_close_precisely_at_270_degrees`
- `adversarial_extreme_float_proportions_do_not_overflow_or_exceed_360`
- `adversarial_subnormal_progress_and_proportions_exceeding_one`

### Ciclo de Vida Android, Compose y Concurrencia (`LaunchedEffect` / `Animatable`)
- `compose_rapid_summary_churn_cancels_inflight_animations_and_converges_cleanly`: 10 cambios rápidos cada `50 ms` cancelando animaciones en curso (`CancellationException`) y verificando convergencia final tras `1000 ms`.
- `compose_populated_to_empty_mid_animation_resets_and_hides_percent_badge`: transición poblado → vacío a mitad de animación (`500 ms`) y posterior repoblado.
- `compose_unrelated_recomposition_with_structurally_equal_summary_remains_stable`: recomposiciones por props ajenos con instancias estructuralmente iguales de `PeriodCategorySummary`.
- `compose_tight_constraints_smaller_than_stroke_width_does_not_crash_canvas`: `Modifier.size(8.dp)` y `Modifier.size(0.dp)`.
- `compose_adversarial_strings_malformed_hex_and_200_categories_render_safely`: hexadecimales malformados/duplicados, 200 categorías simultáneas y textos extremos Unicode/RTL/emojis/XSS a través de `0 ms`, `500 ms` y `1000 ms`.

---

## 3. Comando de Ejecución

Para ejecutar la suite adversarial en local o CI/CD:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.PeriodSummaryDonutChartAdversarialTest"
```
