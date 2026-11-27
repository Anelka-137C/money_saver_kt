# Historia #16 — Programación de Fechas de Pago de Préstamos

## Story
Como usuario, quiero programar y gestionar de forma opcional una o varias fechas de pago con su monto esperado para un préstamo (al crearlo o en uno ya existente), para organizar el calendario acordado y ver rápidamente el próximo vencimiento en la tarjeta.

## Acceptance criteria
- [ ] Given que estoy registrando un préstamo nuevo, when lo guardo sin fechas o agregando una o varias cuotas con fecha igual o posterior a hoy cuyos montos esperados suman exactamente el monto a recibir, then el préstamo se guarda con su calendario y su tarjeta muestra el badge "Vence: <fecha más próxima>" (con indicador "+N" si tiene cuotas adicionales, resaltado en rojo si esa fecha ya venció, o sin badge cuando no tiene cuotas).
- [ ] Given que tengo un préstamo registrado en la lista de deudas pendientes (con o sin cuotas previas), when abro su calendario desde la tarjeta para consultar sus cuotas, agregar nuevas (desde hoy en adelante) o quitar existentes y confirmo con el calendario vacío o sumando exactamente el monto a recibir, then los cambios quedan guardados y el badge de la tarjeta se actualiza de inmediato reflejando la nueva fecha más próxima, el indicador "+N" y su estado de vencimiento.
- [ ] Given que intento programar una cuota con fecha inválida o anterior a hoy, con un monto esperado menor o igual a cero, o confirmar un calendario con una o más cuotas cuya suma no es exactamente igual al monto a recibir del préstamo, when confirmo la acción, then veo un mensaje de error claro y no se guardan datos inválidos.

## Plan
- Extender el almacenamiento local de préstamos para persistir las cuotas programadas (fecha y monto esperado) asociadas a cada préstamo, conservando los préstamos ya registrados.
- Agregar en el dominio las validaciones del calendario (fechas válidas no anteriores a hoy, monto esperado mayor a cero y suma total exactamente igual al monto a recibir cuando existan cuotas) y el cálculo de la próxima fecha a vencer, cuotas adicionales y estado vencido según la fecha actual.
- Incorporar en el estado de préstamos la adición y eliminación de cuotas tanto durante el registro de un préstamo nuevo como en la gestión del calendario de un préstamo existente.
- Añadir la sección opcional de cuotas (selector de fecha desde hoy en adelante y monto esperado en la moneda configurada) al diálogo de registro, un diálogo de calendario al tocar la tarjeta de un préstamo existente y el badge de vencimiento en la tarjeta ("Vence: <fecha>", indicador "+N" y resaltado en rojo cuando esté vencida).
- Agregar las traducciones y el formato de fecha corta localizado en los cuatro idiomas soportados, y cubrir con pruebas unitarias y de interfaz el alta, la gestión en préstamos existentes, las validaciones y los estados del badge.

## Out of scope
- Editar el monto prestado, el monto a recibir u otros datos del préstamo, o eliminar préstamos (#20 / división sugerida en #20a y #20b).
- Registrar pagos totales o parciales, marcar cuotas como pagadas o acreditar dinero a ahorros (#17).
- Consultar el historial de pagos realizados (#18) y separar pestañas de vigentes e historial (#19).
- Notificaciones o recordatorios push de vencimiento de cuotas.
- Respaldo/restauración de préstamos y cuotas en Google Drive (se integrará al cerrar #17).

## Suggestions (your call)
- **Migración explícita de la base de datos local de préstamos:** agregar una migración explícita al actualizar el esquema evitará que los préstamos ya registrados en el dispositivo se pierdan al añadir las cuotas.
- **Componente reutilizable de selector de fecha (sin hora):** extraer un componente simple de selección de fecha evitaría duplicar la lógica ya existente en períodos y facilitará su reutilización en pagos (#17).

## Decisiones confirmadas
- **Opcionalidad:** un préstamo puede guardarse sin fechas (0 cuotas) o con 1 o varias cuotas programadas.
- **Datos por cuota:** cada fecha programada incluye fecha (día/mes/año) y monto esperado.
- **Suma exacta:** si el préstamo tiene al menos una cuota programada, la suma de los montos esperados debe ser exactamente igual al monto a recibir.
- **Sin fechas pasadas:** no se permite programar fechas anteriores a hoy; las fechas previamente guardadas que venzan con el paso del tiempo se resaltan en rojo en el badge.
- **Badge en la tarjeta:** muestra `"Vence: <fecha más próxima>"`, con indicador `"+N"` si existen cuotas adicionales, y en rojo si la fecha más próxima ya venció.
- **Gestión en existentes y división de #20:** desde la tarjeta de un préstamo existente se gestiona su calendario de cuotas; la edición de monto prestado y monto a recibir queda fuera de #16 (en #20 / #20a).
