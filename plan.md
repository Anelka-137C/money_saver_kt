

---

# Story #14: Protección de acceso con desbloqueo nativo del teléfono (huella y/o PIN)

## Story
Como usuario, quiero poder activar o desactivar una capa de seguridad al abrir o retomar la aplicación usando el desbloqueo nativo incorporado en mi teléfono (huella y/o PIN del dispositivo), para proteger el acceso a mis datos financieros.

## Acceptance criteria
- [ ] Given que estoy en la pantalla de Configuración, when activo o desactivo la protección de acceso, then mi preferencia se guarda y refleja si mi dispositivo permite usar el desbloqueo incorporado con huella y PIN del teléfono o solo PIN del teléfono (o me avisa que debo configurar un bloqueo de pantalla en el sistema si no tiene ninguno activo).
- [ ] Given que tengo activada la protección de acceso, when abro la aplicación o vuelvo a ella desde segundo plano, then mis datos financieros permanecen ocultos y se abre el diálogo nativo de autenticación del teléfono (ofreciendo huella y PIN del dispositivo si hay lector de huella disponible, o solo el PIN del dispositivo si no lo hay), accediendo normalmente al validarme con éxito.
- [ ] Given que se muestra el diálogo nativo de autenticación al abrir o retomar la aplicación, when la autenticación falla o la cancelo, then continúo en la pantalla de bloqueo sin exponer mis datos financieros y veo un mensaje de error con un botón para volver a lanzar la autenticación del teléfono.

## Plan
- Agregar una sección de seguridad en Configuración con un interruptor para activar o desactivar la protección de acceso y un mensaje acorde a la capacidad detectada por el sistema del dispositivo (huella y PIN del teléfono, solo PIN, o aviso si el teléfono no tiene bloqueo configurado), traducida en los cuatro idiomas soportados.
- Persistir localmente la preferencia de protección de acceso y exponer su estado junto con la disponibilidad de autenticación del dispositivo en la capa de presentación de Configuración.
- Integrar exclusivamente el flujo nativo de autenticación incorporado en el teléfono (usando la huella del sistema junto con el PIN del dispositivo cuando hay lector de huella, o solo el PIN del dispositivo en caso contrario) en el ciclo de vida principal al abrir la app y al regresar desde segundo plano.
- Mostrar una vista de bloqueo simple que oculte los datos financieros mientras la sesión no esté autenticada, mostrando un mensaje de error y un botón de reintentar que vuelva a invocar el diálogo nativo del teléfono ante fallo o cancelación.
- Añadir pruebas unitarias y de interfaz para la persistencia de la preferencia, la detección de capacidades del dispositivo, el bloqueo al abrir o retomar la app y el manejo de fallo o cancelación con reintento.

## Out of scope
- Lector de huella personalizado o teclado/flujo de PIN propio distinto al diálogo nativo incorporado en el teléfono.
- Bloqueo temporal por cantidad de intentos fallidos o flujos de recuperación de PIN.
- Configuración de un tiempo de gracia antes de volver a pedir autenticación al regresar desde segundo plano.
- Incluir la preferencia de protección de acceso dentro del respaldo de Google Drive.

## Suggestions (your call)
- En Configuración ya se repite el mismo patrón visual de tarjeta (título, descripción y control) en Idioma, Moneda y Respaldo; conviene mantener esa misma estructura para la tarjeta de Seguridad.
- Como el flujo actual de respaldo/restauración en Google Drive abre el selector de archivos del sistema y hace que la app pase brevemente a segundo plano, conviene omitir el rebloqueo al regresar inmediatamente de ese selector para no interrumpir la operación en curso.
