## Story
Como usuario, quiero registrar pagos totales o parciales de un préstamo acreditando el monto a la categoría de ahorro de origen —o a la categoría de ahorro que elija si no provino de ahorros— y definiendo una nueva fecha de pago cuando quede saldo pendiente, para controlar el cobro y sumar ese dinero a mis ahorros.

## Acceptance criteria
- [ ] **Given** un préstamo con saldo pendiente, **when** el usuario registra un pago total o parcial con un monto válido (mayor a cero y menor o igual al saldo pendiente) y una fecha y hora de pago (por defecto la fecha y hora actual, editable por el usuario), **then** el préstamo actualiza su monto pagado, su saldo pendiente y su barra de progreso, y el resumen superior actualiza el total por cobrar y el total pagado (si el monto o la fecha y hora son inválidos, o el monto supera el saldo pendiente, se impide guardar y se muestra un mensaje de error).
- [ ] **Given** un préstamo al que se le registra un pago, **when** el préstamo provino de una categoría de ahorro, **then** el monto se acredita automáticamente al saldo neto disponible de esa categoría sin depender de un período financiero; cuando no provino de ahorros, el usuario debe seleccionar una categoría de ahorro destino y el monto se suma a su saldo neto disponible.
- [ ] **Given** un pago parcial que deja saldo pendiente en el préstamo, **when** el préstamo no tiene cuotas futuras programadas, el usuario define una nueva fecha de pago (hoy o posterior), y si ya tiene cuotas programadas puede opcionalmente registrar una nueva cuota o ajustar el monto esperado de la siguiente cuota, **then** la tarjeta del préstamo refleja la próxima fecha de cobro actualizada (y si el pago salda el total pendiente, no se solicita nueva fecha ni se muestra vencimiento pendiente).

## Plan
- **Persistencia de pagos**: Ampliar el modelo y almacenamiento local de préstamos mediante una migración que conserve los datos existentes para guardar cada pago (monto, fecha y hora del pago, y categoría de ahorro destino) vinculado a su préstamo.
- **Reglas de dominio y saldo neto de ahorros**: Validar el monto del pago frente al saldo pendiente y la fecha y hora ingresadas, resolver automáticamente o exigir la categoría de ahorro destino, aplicar la nueva fecha o el ajuste opcional de la siguiente cuota cuando quede saldo pendiente, y sumar los cobros al cálculo compartido de saldo neto disponible de ahorros de forma independiente a los períodos financieros.
- **Estado y sincronización entre pantallas**: Exponer en la gestión de estado de préstamos el flujo de registro de pago (inicializado con la fecha y hora actual), recalcular el total pagado y el total por cobrar del resumen superior, y propagar el saldo neto actualizado a las pantallas que consultan ahorros disponibles.
- **Interfaz de registro y tarjeta de préstamo**: Extraer un contenedor base compartido para los diálogos de préstamos, agregar la acción de registrar pago en cada préstamo con saldo pendiente, crear el diálogo de pago (monto, selector de fecha y hora con valor actual por defecto, selector de categoría destino solo si no provino de ahorros y opciones condicionales para definir nueva fecha o ajustar opcionalmente la siguiente cuota en pagos parciales) y actualizar la tarjeta con el progreso, pagado, pendiente y vencimiento reales.
- **Multi-idioma y pruebas**: Añadir los textos necesarios en los cuatro idiomas soportados y cubrir con pruebas unitarias las validaciones de pagos totales y parciales, la acreditación al saldo neto de ahorros y el manejo de nuevas fechas o ajuste de la siguiente cuota.

## Out of scope
- Consultar el historial detallado de pagos de cada préstamo (historia **#18**).
- Organizar los préstamos en pestañas de vigentes e histórico (historia **#19**).
- Ajustar la edición o eliminación de préstamos que ya tienen pagos registrados (historia **#20**).
- Editar o eliminar pagos ya registrados (historia **#21**).
- Respaldo y restauración de préstamos/pagos en Google Drive.
