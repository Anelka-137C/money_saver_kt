# Story #3: Eliminar gasto registrado

## Story
Como usuario, quiero eliminar un gasto registrado desde un menú de opciones con confirmación deslizante, para mantener mi historial actualizado y evitar eliminaciones accidentales.

## Acceptance criteria
- [ ] Given que tengo un gasto en el historial, when presiono el botón de tres puntos de su tarjeta y selecciono "Eliminar", then se muestra un diálogo de confirmación con un botón deslizante para confirmar la eliminación.
- [ ] Given que está visible la confirmación con el botón deslizante, when deslizo el botón completamente para confirmar, then el gasto se elimina de la base de datos local, desaparece del historial y se muestra un mensaje de confirmación.
- [ ] Given que está visible la confirmación con el botón deslizante, when cancelo o cierro el diálogo sin completar el deslizamiento, then el gasto permanece intacto en el historial.

## Plan
- Permitir la eliminación de un gasto por su identificador en la capa de persistencia local.
- Añadir el caso de uso para ejecutar la eliminación del gasto.
- Gestionar en la lógica de presentación el menú de opciones del gasto y el estado del diálogo de confirmación.
- Incorporar el botón de tres puntos con la opción "Eliminar" en la tarjeta del gasto.
- Diseñar e integrar el diálogo de confirmación con el botón deslizante de confirmación.

## Out of scope
- Función de deshacer (Undo) o papelera de reciclaje.
- Eliminación múltiple o en bloque de gastos.
- Otras opciones en el menú de tres puntos fuera de "Eliminar".

## Suggestions (your call)
- Implementar un retorno elástico (snap-back) en el botón deslizante si el usuario suelta el control antes de alcanzar el umbral mínimo de confirmación, garantizando una acción 100% deliberada.
