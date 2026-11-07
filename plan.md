# Story #2: Editar gasto existente

## Story
Como usuario, quiero editar un gasto existente desde un popup, para corregir errores en su monto, descripción o clasificación.

## Acceptance criteria
- [ ] Given que tengo un gasto en el historial, when presiono la tarjeta del gasto, then se abre un popup modal con los datos actuales precargados para su edición.
- [ ] Given que estoy en el popup de edición, when modifico los campos (monto, fecha, categoría/subcategoría o descripción) y confirmo, then los cambios se actualizan en la base de datos local, el popup se cierra y la lista refleja los datos actualizados.
- [ ] Given que estoy en el popup de edición, when ingreso un monto inválido (vacío o menor/igual a cero) e intento guardar, then se muestra un mensaje de validación en el popup y la edición no se guarda, conservando los datos originales.

## Plan
- Habilitar la actualización de un gasto en la persistencia local.
- Manejar en la lógica de presentación el estado del popup de edición y la carga de los datos del gasto seleccionado.
- Aplicar la validación del monto antes de persistir las modificaciones en el popup.
- Diseñar y construir el popup modal de edición siguiendo el estilo visual oscuro y acentos verdes.
- Conectar la apertura del popup al pulsar una tarjeta del historial y cerrar tras guardar o cancelar.

## Out of scope
- Eliminación de gastos (pertenece a la Historia #3).
- Creación de nuevas categorías o subcategorías personalizadas (pertenece a la Historia #4).
- Historial de cambios o auditoría de ediciones pasadas.

## Suggestions (your call)
- Implementar el popup mediante un `ModalBottomSheet` o `Dialog` reutilizando los componentes de captura (monto, fecha, selector de categoría) y la paleta oscura de `design/gastos.png` para asegurar consistencia con el formulario de registro.
