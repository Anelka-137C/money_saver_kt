# Story #11: Consultar histórico filtrado por período financiero

## Story
Como usuario, quiero consultar el histórico de ingresos, presupuesto, gastos y ahorros filtrado por período financiero con un resumen visual por categorías al inicio de cada vista, para revisar claramente cómo se distribuye mi dinero en cada ciclo.

## Acceptance criteria
- [ ] Given un período financiero con movimientos registrados en ingresos, presupuesto, gastos o ahorros, when el usuario accede a cualquiera de esas cuatro vistas, then visualiza como primer elemento superior la tarjeta de resumen del período con el total acumulado, una gráfica circular cuyas porciones muestran las proporciones agrupadas únicamente por categorías principales (consolidando sus subcategorías) usando el color asignado a cada categoría, y la leyenda de cada categoría con su color correspondiente, monto y porcentaje, sin indicadores comparativos contra otros períodos.
- [ ] Given registros distribuidos en distintos períodos financieros, when el usuario selecciona otro período en el selector de período, then la tarjeta de resumen y el listado histórico de ingresos, presupuesto, gastos y ahorros se actualizan para mostrar exclusivamente la información perteneciente al período elegido.
- [ ] Given un período financiero seleccionado que no tiene registros en alguno de los cuatro rubros, when el usuario consulta esa vista, then la tarjeta de resumen muestra el total en cero, la gráfica circular en estado vacío y sin porcentajes ni cifras simuladas.

## Plan
- Lógica de negocio para agrupar los registros del período seleccionado exclusivamente por categoría principal (consolidando subcategorías), asociar el color propio de cada categoría y calcular totales, montos y porcentajes en los cuatro rubros.
- Gestión de estado para exponer el resumen real por categoría principal (incluyendo su color) de ingresos, presupuesto, gastos y ahorros sincronizado con el período financiero seleccionado.
- Componente atómico reutilizable de resumen (ubicado en la subcarpeta `atom` dentro de los componentes de interfaz) con total destacado, gráfica circular proporcional pintada con el color de cada categoría y leyenda por categoría fiel al diseño de referencia, sin insignia comparativa y con estado vacío.
- Integración del componente de resumen como primer elemento superior en las cuatro vistas (ingresos, presupuesto, gastos y ahorros), eliminando los datos fijos o simulados actuales.
- Textos localizados en los idiomas soportados para encabezados, etiquetas y estado vacío del resumen en cada vista.

## Out of scope
- Comparativas, tendencias o variaciones porcentuales entre períodos financieros distintos.
- Desglose de la gráfica circular por subcategorías (se consolidan dentro de su categoría principal).
- Una quinta pantalla independiente que apile las cuatro tarjetas a la vez.
- Eliminar o rediseñar las tarjetas secundarias de asignación/distribución existentes debajo del resumen.
- Bloquear la edición o eliminación de registros en períodos pasados.
- Gráficos adicionales distintos a la gráfica circular de resumen.

## Suggestions (your call)
- Definir si el centro de la gráfica circular muestra el total del período con `100%` en los cuatro rubros (y `0%` cuando esté vacío) para mantener consistencia visual con el diseño de referencia sin introducir datos ficticios.
