# Story #10: Apartar dinero hacia ahorros clasificados por categoría

## Story
Como usuario, quiero apartar dinero de mis ingresos hacia ahorros clasificados por categoría, para reservar fondos ordenadamente hacia objetivos específicos.

## Acceptance criteria
- [ ] Given un período financiero con ingreso disponible (ingresos no comprometidos en presupuesto u otros ahorros), when el usuario ingresa un monto válido y selecciona una categoría para apartar como ahorro, then el ahorro queda registrado bajo esa categoría en el período, y el monto total de ahorros y el ingreso disponible restante se actualizan.
- [ ] Given un período financiero activo, when el usuario intenta apartar un monto mayor al ingreso disponible restante o un monto menor o igual a cero, then se muestra un mensaje de validación indicando el error y no se registra el ahorro.
- [ ] Given ahorros registrados en el período financiero actual, when el usuario consulta la sección financiera del período, then visualiza el total acumulado en ahorros y el listado de montos apartados con su categoría correspondiente.

## Plan
- Persistencia local para almacenar los registros de ahorros vinculados a su categoría y período financiero.
- Lógica de negocio para calcular el total ahorrado y validar que el monto apartado no supere el ingreso disponible del período ni sea menor o igual a cero.
- Gestión de estado para el período activo, exponiendo el total de ahorros registrados, el ingreso disponible actualizado y la lista de ahorros.
- Formulario de entrada para ingresar el monto, seleccionar la categoría y confirmar el apartado de ahorro.
- Visualización en la interfaz del total apartado en ahorros, su impacto en la distribución del ingreso del período y el listado de ahorros realizados.

## Out of scope
- Modificar o eliminar ahorros ya apartados (se puede abordar en una historia dedicada si se requiere).
- Registrar gastos financiados desde ahorros (cubierto en la historia #12).
- Historial global o comparativa de ahorros entre períodos (cubierto en la historia #11).
- Metas de ahorro complejas con plazos, fechas límite o aportes automáticos recurrentes.
- Creación de nuevas categorías exclusivas de ahorro (se reutiliza el catálogo de categorías existente).

## Suggestions (your call)
- Añadir pestaña "Ahorros" a la navegación superior (`FinanceTopTabs`): Actualmente la pantalla de Contabilidad agrupa `Ingresos`, `Presupuesto` y `Gastos`. Agregar una 4ª pestaña sigue el patrón visual y de navegación idéntico al resto de módulos.
- Reemplazar el cálculo mock de ahorros en las tarjetas de distribución: Actualmente la tarjeta de distribución de ingresos calcula los ahorros con un valor estático del 21% (`val savings = totalIncome * 0.21`). Al implementar esta historia, esa tarjeta puede consumir directamente el total real ahorrado.
