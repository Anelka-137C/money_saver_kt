# Story #7: Eliminación de subcategorías de ingresos

## Story
Como usuario, quiero eliminar una subcategoría de ingresos que ya no utilice, para mantener mi catálogo de subcategorías limpio y organizado.

## Acceptance criteria
- [x] Given que el usuario visualiza las subcategorías de una categoría y una subcategoría no tiene ingresos asociados, when pulsa el botón para eliminarla, then la subcategoría se elimina y deja de mostrarse en la lista.
- [x] Given que una subcategoría tiene uno o más ingresos asociados, when el usuario intenta eliminarla, then la subcategoría no se elimina y se muestra un mensaje de error indicando que tiene ingresos asociados.

## Plan
- [x] Agregar la consulta para verificar ingresos vinculados y la operación para borrar la subcategoría en la base de datos local.
- [x] Implementar la validación en la lógica de negocio para permitir el borrado únicamente si no hay ingresos asociados.
- [x] Exponer la acción de eliminar subcategoría y su estado de error en la capa de presentación.
- [x] Añadir el botón de eliminar junto a cada subcategoría en la hoja de selección y mostrar el mensaje de error si la validación falla.

## Test list (TDD)
- [x] `test_income_category_dao_delete_subcategory_by_name` (Repositorio / Room DB)
- [x] `test_delete_subcategory_when_incomes_associated_returns_error_and_does_not_delete` (UseCase)
- [x] `test_delete_subcategory_when_no_incomes_associated_deletes_successfully` (UseCase)
- [x] `test_viewmodel_delete_subcategory_updates_state_and_handles_error` (ViewModel)
- [x] `test_category_sheet_displays_delete_button_for_subcategories_and_triggers_deletion` (UI / Componente)

## Out of scope
- Eliminación en cascada de ingresos al borrar una subcategoría.
- Edición o renombramiento de subcategorías.
- Borrado de subcategorías en la sección de gastos (este alcance es específico para ingresos).

## Suggestions (your call)
- Reutilizar el mismo estilo visual del botón de eliminar categoría (ícono de papelera con color de advertencia/error) junto al nombre de la subcategoría para mantener consistencia en la interfaz.
