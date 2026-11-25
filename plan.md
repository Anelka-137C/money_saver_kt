/# Story #12: Registrar gasto con fondos de ahorros y saldo neto

## Story
Como usuario, quiero registrar un gasto especificando que los fondos provienen de mis ahorros con saldo disponible visible y consultar mi saldo neto por categoría en ahorros con el diseño de resumen, para cubrir salidas extraordinarias sin afectar mi presupuesto corriente y monitorear mis fondos remanentes.

## Acceptance criteria
- [ ] Given categorías de ahorro con saldo acumulado a través de los períodos financieros, when el usuario despliega el selector de fuente de fondos para registrar un gasto desde ahorros, then visualiza cada categoría con su saldo acumulado total disponible (ahorros históricos menos gastos previos) y, al guardarlo, el gasto queda registrado sin descontar ni incrementar el porcentaje consumido del presupuesto corriente del período.
- [ ] Given una categoría de ahorro seleccionada como fuente de fondos, when el usuario intenta registrar un gasto cuyo monto supera el saldo acumulado total disponible en esa categoría, then el sistema bloquea el registro mostrando un mensaje de error por saldo insuficiente en la categoría de ahorro.
- [ ] Given gastos financiados con ahorros registrados en el sistema, when el usuario consulta las vistas de gastos y ahorros, then en gastos identifica cada salida extraordinaria con su etiqueta distintiva de ahorro de origen (excluida del consumo de presupuesto), y en la vista de ahorros visualiza una tarjeta de saldo neto acumulado fiel al diseño de referencia (total general destacado, gráfica circular proporcional y leyenda de categorías con color, monto neto y porcentaje).

## Plan
- Almacenamiento en capa de datos de la fuente de fondos (presupuesto corriente o ahorros) y la categoría de ahorro de origen asociada a cada gasto.
- Lógica de cálculo acumulativo global y validaciones para determinar el saldo neto por categoría de ahorro (aportes históricos menos gastos previos), verificando disponibilidad sin exigir presupuesto corriente activo.
- Selector de fuente de fondos en el diálogo de gastos que liste las categorías de ahorro mostrando explícitamente su saldo acumulado disponible.
- Identificación visual de gastos financiados con ahorros en el listado de gastos, manteniéndolos excluidos del total consumido del presupuesto corriente del período.
- Componente atómico en la vista de ahorros que replique fielmente el diseño de referencia (total, gráfica circular proporcional y leyenda detallada) adaptado a los atributos de saldo neto acumulado por categoría.

## Out of scope
- Traspasos o transferencias directas de dinero entre categorías de ahorro sin mediar un gasto.
- Permitir saldo neto negativo o sobregiro en las categorías de ahorro.
- Modificación retroactiva de los registros históricos de aportes a ahorros o de ingresos.
- Pantalla independiente o módulo de auditoría de movimientos de ahorro fuera de las vistas actuales de gastos y ahorros.

## Suggestions (your call)
- Atributos del componente atómico de resumen: reutilizar o especializar la estructura atómica de la tarjeta de resumen para saldo neto mapeando sus etiquetas (ej. título "Saldo neto en ahorros", subtítulo "Total disponible acumulado", centro de gráfica con porcentaje o etiqueta correspondiente) para mantener total consistencia visual con `design/resumen.png`.
