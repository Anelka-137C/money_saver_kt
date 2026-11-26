# Story #13: Respaldo hacia Google Drive desde Configuración y recordatorio a los 7 días

## Story
Como usuario, quiero generar un respaldo llamado `money_saver_backup` desde el tab de Configuración hacia la carpeta de Google Drive que yo elija y recibir recordatorios varias veces al día a partir de los 7 días sin respaldar, para mantener mis datos financieros protegidos en la nube sin olvidar actualizarlos.

## Acceptance criteria
- [ ] Given que estoy en el tab de Configuración con información financiera registrada (períodos, ingresos, presupuestos, gastos, ahorros, categorías y preferencias), when pulso crear respaldo y elijo la carpeta destino en Google Drive desde el selector nativo de Android, then se guarda el archivo `money_saver_backup` con toda mi información en esa carpeta, veo confirmación de éxito y se actualiza la fecha/hora del último respaldo reiniciando el contador de 7 días.
- [ ] Given que cancelo el selector o ocurre un error al generar o guardar el respaldo, when el proceso no se completa, then veo el aviso correspondiente sin afectar mis datos actuales y sin modificar la fecha del último respaldo.
- [ ] Given que han transcurrido 7 días desde mi último respaldo exitoso, when se cumple cada intervalo de 8 horas en horario diurno (evitando horario nocturno, ~2–3 veces al día), then recibo un recordatorio para realizar el backup que me dirige a Configuración, y los avisos se detienen automáticamente al completar un nuevo respaldo.

## Plan
- Agregar la sección de respaldo en la pantalla de Configuración con botón de acción, indicador de progreso y fecha del último respaldo (o indicador si aún no existe) en todos los idiomas soportados.
- Extender la capa de presentación de Configuración para coordinar la ejecución del respaldo, sus estados de carga/éxito/error y la persistencia de la fecha del último backup.
- Implementar el caso de uso de respaldo que recopila de forma consistente toda la información financiera y preferencias del usuario en un paquete portátil llamado `money_saver_backup`, conectado al selector nativo de documentos de Android para guardarlo en la carpeta elegida de Google Drive.
- Incorporar la lógica y programación de recordatorios locales que se activan a los 7 días del último respaldo exitoso y se repiten cada 8 horas en horario diurno (evitando horario nocturno), gestionando el permiso de notificaciones cuando corresponda y reiniciando el ciclo tras cada backup exitoso.
- Añadir pruebas unitarias y de interfaz para la generación del paquete de respaldo, la actualización de la fecha, el manejo de cancelación/error y la regla de recordatorio a 7 días con repetición diurna.

## Out of scope
- Autenticación directa con cuenta de Google o integración con la API de Google Drive dentro de la app.
- Restauración o importación de un respaldo existente.
- Cifrado con contraseña del archivo de respaldo.
- Configuración manual por el usuario de la frecuencia u horarios de los recordatorios.

## Suggestions (your call)
- En Configuración ya se repite el mismo patrón de tarjeta con título, descripción y contenido para Idioma y Moneda; convendría mantener esa misma estructura visual para la tarjeta de Respaldo.
