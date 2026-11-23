# Reporte de Revisión Adversarial & Edge Cases

## 1. Objetivo y Alcance
Diseñar y ejecutar pruebas automatizadas adversariales orientadas a desafiar los límites de la lógica implementada para la **Story #11** ("*Consultar histórico de ingresos, presupuesto, gastos y ahorros filtrado por período financiero con un resumen visual por categorías al inicio de cada vista*"), poniendo a prueba la robustez del sistema bajo condiciones extremas sin alterar el código de producción.

---

## 2. Detección de Código Evaluado mediante Git
A partir de la inspección con `git status` y `git diff`, se identificaron los siguientes módulos y componentes impactados:

- **Dominio / Casos de Uso:**
  - `CategorySummary.kt` (`buildPeriodCategorySummary`): Agregación por categorías, cálculo de proporciones, porcentajes y resolución de colores.
  - `ExpenseUseCase.kt` (`calculateExpenseCategorySummary`): Consolidación de gastos bajo su categoría principal.
  - `IncomeUseCase.kt` (`calculateIncomeCategorySummary` y `calculateBudgetCategorySummary`): Consolidación de ingresos y presupuestos bajo su categoría principal.
  - `SavingsUseCase.kt` (`calculateSavingsCategorySummary`): Consolidación de ahorros bajo su categoría principal.
- **Gestión de Estado (ViewModels):**
  - `ExpenseViewModel.kt`: Manejo reactivo de `expenseSummary` asociado a `selectedPeriodId`.
  - `IncomeViewModel.kt`: Manejo reactivo de `incomeSummary` y `budgetSummary` asociado a `selectedPeriodId`.
  - `SavingsViewModel.kt`: Manejo reactivo de `savingsSummary` asociado a `selectedPeriodId`.
- **Componentes de Interfaz de Usuario (Compose Atoms):**
  - `PeriodSummaryCard.kt`: Tarjeta superior contenedora con formateo de moneda y estado vacío.
  - `PeriodSummaryDonutChart.kt`: Gráfica circular proporcional basada en Canvas con trazo de arcos acumulados.
  - `PeriodSummaryLegend.kt`: Leyenda interactiva con nombre, monto formateado, porcentaje y punto de color.

---

## 3. Suite de Pruebas Implementada

Se implementó la suite completa de pruebas adversariales en:
- `app/src/test/java/com/example/money_saver_android/adversarial/PeriodSummaryAdversarialTest.kt`

### Detalle de Escenarios y Cobertura:

### A. Property-Based Testing (500 iteraciones aleatorias con semilla fija `Random(42)`)
- **Invariante de Suma:** $\sum \text{items.amount} = \text{totalAmount}$ con precisión de punto flotante.
- **Invariante de Proporción y Porcentaje:** Para todo elemento, $\text{proportion} \in [0.0, 1.0]$ y $\text{percentage} \in [0, 100]$.
- **Invariante de Suma de Proporciones:** En resúmenes no vacíos, $\sum \text{proportion} \approx 1.0$ (tolerancia $[0.98, 1.02]$ por redondeo flotante).
- **Invariante de Ordenación:** Los elementos se ordenan de manera estrictamente descendente según su monto acumulado ($\text{amount}_i \ge \text{amount}_{i+1}$).
- **Invariante de Unicidad y Normalización:** No existen nombres de categoría duplicados en la salida; cualquier variante de mayúsculas/minúsculas o espaciado lateral se consolida en una única entrada.
- **Invariante de Sanitización:** Registros con montos no finitos, menores o iguales a cero, o categorías vacías son descartados sin excepción.

### B. Pruebas de Frontera y Fuzzing de Datos Corruptos
- **Desbordamiento acumulativo (`Double.MAX_VALUE`):** Entradas múltiples cuya suma supera el valor máximo representable resultando en `Double.POSITIVE_INFINITY` son contenidas y retornan un resumen seguro vacío en vez de propagar el infinito o causar desbordamiento aritmético no controlado.
- **Disparidad extrema de escalas:** Procesamiento conjunto de órdenes de magnitud astronómicos (1 billón / `1e12`) junto a fracciones infinitesimales (`1e-10`), verificando que los valores pequeños reciben 0% sin incurrir en divisiones por cero ni `NaN`.
- **Valores numéricos corruptos:** Filtrado estricto de `NaN`, `+Infinity`, `-Infinity`, `-0.0`, `0.0` y montos negativos.
- **Caracteres especiales y cadenas complejas:** Integración sin fallas de nombres con emojis (`🍔 Comida 🎉`), texto bidireccional árabe RTL (`رواتب`), inyecciones de código HTML/JS (`<script>alert('XSS')</script>`), saltos de línea (`\n`), tabulaciones (`\t`) y cadenas extensas de 500 caracteres.
- **Colores ausentes o corruptos en el catálogo:** Fallback controlado a `defaultColorHex` cuando el catálogo carece de color o contiene cadenas en blanco.

### C. Consolidación de Subcategorías en UseCases
- Validación en `ExpenseUseCase`, `IncomeUseCase` y `SavingsUseCase` garantizando que gastos, ingresos, presupuestos y ahorros registrados con subcategorías independientes se consoliden de forma exclusiva en su categoría padre con el color correspondiente.

### D. Aislamiento por Período y Manejo de Concurrencia
- **Aislamiento estricto entre períodos:** Validación de transiciones entre Período 1 ($300), Período 2 ($50), Período 3 (vacío, $0.00) y `periodId = null`, asegurando ausencia total de fugas o estados residuales.
- **Cancelación de coroutines en conmutación rápida:** Múltiples cambios consecutivos de período cancelan las emisiones en curso evitando condiciones de carrera donde respuestas lentas sobrescriban el período activo.
- **Reactividad ante mutaciones:** Inserción y eliminación de registros provocan la actualización atómica del resumen en el ViewModel.

### E. Estrés y Renderizado en Compose UI
- Renderizado de tarjeta con ítem único (arco completo de 360° y `100%`).
- Prueba de estrés con 50 categorías simultáneas para validar que ni el Canvas ni la leyenda sufren congelamientos de UI.
- Protección ante valores hexadecimales inválidos (`"MALFORMED_HEX"`) en `PeriodSummaryCard`, comprobando que `parseHexColor` aplica el color por defecto sin lanzar excepción en tiempo de dibujo.
- Estado vacío verificado contra insignias estáticas o comparativas heredadas (sin `+12%`, `vs Apr`, etc.).

---

## 4. Vulnerabilidades y Edge Cases Identificados

> [!WARNING]
> **Sensibilidad a espacios en el catálogo de categorías (`CategorySummary.kt`):**
> En la implementación de `buildPeriodCategorySummary`:
> ```kotlin
> val rawName = categorySelector(group.first()).trim()
> val matchedCategory = categories.find { it.name.equals(rawName, ignoreCase = true) }
> ```
> Mientras que `rawName` se limpia con `.trim()`, el nombre proveniente de la lista de categorías (`it.name`) **no** se recorta.
> Si en la base de datos o el catálogo una categoría se almacena con espacios adicionales (por ejemplo, `" Employment "`), la coincidencia exacta falla:
> `" Employment ".equals("Employment", ignoreCase = true) == false`
> **Impacto:** La categoría se agrupa con el nombre recortado pero `matchedCategory` resulta nulo, perdiendo el color configurado y recurriendo al color por defecto (`#4E73DF`).
> El test `test_untrimmed_category_name_in_catalog_lookup_behavior` en `PeriodSummaryAdversarialTest.kt` replica y expone este caso borde.

> [!NOTE]
> **Consistencia entre `totalSpent`/`totalIncome` y el resumen:**
> `ExpenseViewModel` y `IncomeViewModel` calculan sus totales directos mediante `sumOf { it.amount }`, mientras que `buildPeriodCategorySummary` filtra registros con categorías en blanco o montos $\le 0$. Si la base de datos contuviera registros con categorías vacías, existiría una discrepancia entre la cifra de la tarjeta de resumen y los estados numéricos globales. Las validaciones de entrada previenen este caso en flujo normal, pero debe contemplarse en migraciones o importaciones de datos.

---

## 5. Instrucciones de Ejecución

Para ejecutar la suite de pruebas adversariales en el entorno local o en pipelines de CI/CD:

```bash
./gradlew testDebugUnitTest --tests "com.example.money_saver_android.adversarial.PeriodSummaryAdversarialTest"
```

Para ejecutar el conjunto completo de pruebas unitarias y de Compose:

```bash
./gradlew testDebugUnitTest
```
