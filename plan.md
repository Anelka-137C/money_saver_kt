# Story #7: Registro de ingresos con fecha, hora y categorización por período financiero

## Story
Como usuario, quiero registrar mis ingresos con fecha y hora asignando o gestionando categorías y subcategorías, y consultar el histórico filtrado por período financiero, para estructurar y monitorear mis fuentes de dinero.

## Acceptance criteria
- [x] Given que el usuario se encuentra en la pantalla de ingresos con un período seleccionado, when pulsa en cualquier parte del campo de fecha/hora (desplegando secuencialmente el calendario y luego el reloj, con la fecha y hora actual por defecto) e ingresa un monto válido con categoría y subcategoría, then el ingreso se guarda con dicha fecha/hora asociado al período seleccionado.
- [x] Given que existen ingresos registrados en diferentes períodos, when el usuario selecciona un período en el selector desplegable, then la lista muestra únicamente los ingresos de ese período con su categoría, subcategoría, monto, fecha/hora y el total acumulado correspondiente.
- [x] Given que el usuario intenta guardar un ingreso con monto inválido (vacío o menor/igual a cero) o sin categoría/subcategoría seleccionada, when pulsa la acción de guardar, then el sistema muestra un mensaje de validación indicando el error y no registra el ingreso.

## Plan
- [x] Almacenar los ingresos con monto, fecha/hora, categoría, subcategoría y vínculo al período financiero, con consultas filtradas por período.
- [x] Implementar la lógica de negocio para validar montos positivos, categoría requerida y cálculo del total acumulado por período.
- [x] Crear el componente interactivo de fecha/hora que encadena secuencialmente el calendario y el reloj, inicializado con la fecha/hora actual.
- [x] Diseñar la pantalla de ingresos con selector de períodos, formulario de captura (reutilizando el selector modal de categorías) y lista histórica con total.
- [x] Incorporar la pestaña de ingresos en la barra de navegación principal respetando las restricciones de período activo.

## Test list (TDD)
- [x] `test_income_and_category_dao_insert_and_queries` (Repositorio / Room DB)
- [x] `test_income_validation_rejects_empty_or_non_positive_amount` (ViewModel / UseCase)
- [x] `test_income_validation_rejects_missing_category_or_subcategory` (ViewModel / UseCase)
- [x] `test_save_valid_income_persists_with_date_time_and_selected_period` (ViewModel)
- [x] `test_filter_incomes_by_selected_period_updates_list_and_totals` (ViewModel)
- [x] `test_date_time_picker_opens_calendar_then_clock_and_updates_value` (UI / Componente)
- [x] `test_incomes_screen_displays_period_selector_form_and_incomes_list` (UI / Pantalla)
- [x] `test_changing_period_in_selector_updates_displayed_incomes_and_overview` (UI / Pantalla)
- [x] `test_incomes_tab_navigation_and_lock_when_no_active_period` (UI / Navegación)

## Out of scope
- Edición o eliminación de ingresos registrados (historias posteriores).
- Asignación o distribución de ingresos hacia presupuestos (alcance de la historia #8).
- Creación o modificación de períodos financieros desde la pantalla de ingresos (se realiza en la pestaña de período).
- Registro de ingresos recurrentes automáticos o cálculo de impuestos/retenciones.

## Suggestions (your call)
- Reutilizar el componente `CategorySelectionSheet` para la selección y alta de categorías y subcategorías.
- Extraer el diálogo encadenado de Fecha y Hora (`DateTimePicker`) en un componente reutilizable.
