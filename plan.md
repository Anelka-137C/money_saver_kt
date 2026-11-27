# Historia #15 — Registro de Préstamos

## Story
Como usuario, quiero registrar un préstamo a una persona desde una nueva pestaña "Préstamos" en la barra de navegación inferior, indicando el monto prestado, el monto a recibir y, de forma opcional, la categoría de ahorro de origen cuando provenga de mis ahorros, para controlar tanto préstamos nuevos como previos a la app.

## Acceptance criteria
- [ ] Given que accedo a la pestaña "Préstamos" en la barra de navegación inferior (habilitada incluso sin un período financiero activo), when registro un préstamo desde el botón "+" superior o "+ Registrar préstamo" ingresando la persona, el monto prestado, el monto a recibir (prellenado por defecto igual al monto prestado y siempre mayor o igual a este) y opcionalmente una nota/concepto sin elegir categoría de ahorro de origen, then el préstamo aparece en la lista de deudas pendientes siguiendo el diseño de referencia (avatar con iniciales, nombre, subtítulo con la nota o "Préstamo personal", barra de progreso en 0%, monto prestado, pagado en 0 y pendiente igual al monto a recibir) y la tarjeta resumen superior actualiza el "Total por cobrar" en la moneda configurada.
- [ ] Given que tengo saldo neto disponible en una categoría de ahorro, when registro un préstamo seleccionando esa categoría de ahorro como origen con un monto prestado menor o igual a su saldo disponible, then el préstamo queda registrado vinculado a ese origen (mostrando la categoría de ahorro si no se ingresó nota) y el monto prestado se descuenta inmediatamente del saldo neto disponible de esa categoría en toda la aplicación (Ahorros y Gastos desde ahorros).
- [ ] Given que intento registrar un préstamo con la persona vacía, un monto prestado inválido (menor o igual a cero), un monto a recibir menor al monto prestado o un monto prestado superior al saldo disponible de la categoría de ahorro seleccionada, when confirmo el registro, then veo un mensaje de error claro, el préstamo no se guarda y mis saldos de ahorro no se modifican.

## Plan
- Integrar la pestaña "Préstamos" en la barra de navegación inferior (entre Contabilidad y Configuración, siempre habilitada aunque no haya período activo) y construir su pantalla fiel al panel de deudas pendientes del diseño de referencia (encabezado con botón "+", tarjeta resumen con total por cobrar y pagado, listado de tarjetas de préstamos, estado vacío y botón inferior "+ Registrar préstamo", sin botón de volver).
- Definir el modelo y almacenamiento local offline para préstamos (persona, monto prestado, monto a recibir, nota/concepto opcional y categoría de ahorro de origen opcional).
- Centralizar el cálculo del saldo neto disponible por categoría de ahorro para que descuente tanto gastos como préstamos financiados desde ahorros, implementar las validaciones de registro (incluyendo monto a recibir mayor o igual al prestado y saldo suficiente) y proteger la edición/eliminación de ahorros frente a préstamos vigentes.
- Construir el estado reactivo de la pantalla de préstamos y el diálogo modal de registro con formato de la moneda activa, sugerencia automática del monto a recibir igual al prestado y selector opcional de categoría de ahorro mostrando su saldo disponible.
- Agregar las traducciones correspondientes en los cuatro idiomas soportados de la aplicación y cubrir con pruebas unitarias y de interfaz el registro, las validaciones, la navegación y la consistencia del saldo de ahorros.

## Out of scope
- Programar fechas de pago y mostrar el badge de vencimiento en cada tarjeta (#16).
- Registrar pagos totales o parciales y acreditar el dinero a ahorros (#17).
- Consultar el historial de pagos o abrir una vista de detalle al tocar una tarjeta (#18).
- Selector de pestañas "Deudas pendientes" e "Historial" con préstamos saldados (#19).
- Editar o eliminar préstamos registrados (#20).
- Incluir préstamos en el respaldo/restauración a Google Drive (se integrará junto con pagos al cerrar #17 para hacer un único cambio al formato de backup).

## Decisiones confirmadas
- **Monto a recibir:** siempre mayor o igual al monto prestado; si no se edita manualmente, se prellena con el mismo valor del monto prestado.
- **Subtítulo de la tarjeta:** campo opcional de nota/concepto; si se deja vacío, muestra la categoría de ahorro de origen o "Préstamo personal" cuando no proviene de ahorros.
- **Navegación y diseño:** pestaña "Préstamos" siempre habilitada en la barra inferior; pantalla fiel al panel izquierdo del diseño de referencia sin flecha de volver ni elementos exclusivos de #16 y #19.
- **Saldo neto de ahorros centralizado:** Ahorros, Gastos y Préstamos consultan un único cálculo compartido de saldo disponible por categoría.
- **Respaldo de préstamos:** postergado hasta completar #17.
