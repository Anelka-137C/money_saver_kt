# Plan de Implementación - Story #9

## Story
Como usuario, quiero que el registro de gastos quede condicionado a tener un presupuesto activo en el período, para asegurar que mis gastos se ajusten a lo planificado y ver reflejado el dinero que sobra en la pantalla de presupuesto.

## Acceptance criteria
- [ ] Given un período financiero activo sin presupuesto configurado, when el usuario accede a la pestaña de gastos, then el botón/formulario de registro se encuentra deshabilitado mostrando un mensaje que indica que se requiere definir un presupuesto activo en el período.
- [ ] Given un período financiero con presupuestos activos, when el usuario registra un gasto, then cuenta con dos selectores: uno para elegir de qué presupuesto del período tomará el dinero y otro para categorizar el gasto (una sola categoría de gasto con subcategoría opcional), y el gasto queda guardado exitosamente vinculado al período.
- [ ] Given uno o más gastos registrados en el período activo, when el usuario consulta la pantalla de presupuesto, then se muestra el dinero sobrante (monto presupuestado menos gastos acumulados, con mínimo en 0) reflejado tanto en el balance general como en cada categoría.
- [ ] Given que el usuario accede al módulo de Contabilidad, when consulta la barra inferior o cambia entre pestañas superiores, then la pestaña inferior se llama "Contabilidad" (sin pestaña de gastos en la barra inferior) y la pantalla de gastos aparece como pestaña superior (junto a Ingresos y Presupuesto), manteniendo la estructura de Presupuesto: resumen de gastos (total gastado y % respecto al total presupuestado), asignación agrupada solo por categorías (sin subcategorías), botón de registrar gasto y desglose de gastos.

## Plan
- Renombrar la pestaña inferior de Ingresos a "Contabilidad", remover Gastos de la barra inferior e integrar la pantalla de gastos en `FinanceTopTabs` (junto a Ingresos y Presupuesto) compartiendo el selector de período.
- Reestructurar la vista de gastos siguiendo la estructura de Presupuesto: tarjeta de resumen de gastos (total gastado y % respecto al total presupuestado), asignación agrupada solo por categorías de gasto (sin subcategorías), botón de registrar gasto (diálogo modal) y desglose de gastos del período.
- Asociar los gastos al período financiero activo (`periodId`) y al presupuesto origen seleccionado, y condicionar la disponibilidad del registro a la existencia de al menos un presupuesto activo en el período (deshabilitando el botón de registro y mostrando un mensaje informativo).
- Implementar en el formulario de registro/edición de gastos dos selectores separados (presupuesto origen del período y categoría de gasto con subcategoría opcional) junto con la validación correspondiente en dominio.
- Calcular y mostrar el dinero sobrante del presupuesto (presupuestado menos gastado en el período, con tope mínimo en 0) en el balance general de `BudgetOverviewCard` (junto al ingreso disponible para presupuestar) y por categoría en `BudgetAllocationCard`.

## Out of scope
- Bloquear registros cuando el gasto exceda el presupuesto disponible (no bloqueo estricto de gasto imprevisto).
- Saldos sobrantes negativos (el dinero sobrante mostrado mantiene el mínimo en 0.0).
- Gastos financiados mediante ahorros o fondos de reserva (alcance de la historia #12).
- Historial comparativo de ejecución presupuestaria entre períodos cerrados (alcance de la historia #11).
- Reasignación automática de montos entre diferentes categorías de presupuesto.

## Suggestions (your call)
- **División opcional de la historia**: Si prefieres entregas más pequeñas, podemos dividirla en dos: (1) Navegación/estructura de Gastos en Contabilidad y condicionamiento con los dos selectores, y (2) Deducción y visualización del saldo sobrante global y por categoría en Presupuesto.
