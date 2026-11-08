# Story #6: Configuración obligatoria y gestión de período financiero en pestaña dedicada

## Story
Como usuario, quiero una pestaña dedicada para configurar las fechas de inicio y fin de mi período financiero mediante selectores de calendario en formato ISO, ver el período actual y el histórico de períodos, y poder modificarlos mediante un popup centrado o eliminarlos con un menú de opciones de 3 puntos; además la aplicación me obliga a definir un período si no existe uno vigente para la fecha actual.

## Acceptance criteria
- [x] Given que el usuario abre la aplicación y no existe un período financiero que cubra la fecha actual (formato ISO YYYY-MM-DD), when se inicia la app, then se le sitúa obligatoriamente en la pestaña de Período y se bloquea el acceso a las demás pestañas hasta registrar uno válido.
- [x] Given que el usuario está configurando las fechas del período financiero, when ingresa un rango de fechas inválido (fecha de fin anterior a la de inicio, o un rango que no incluya el día actual), then el sistema muestra un mensaje de validación y restringe el guardado.
- [x] Given que el usuario guarda un período financiero válido en formato ISO, when se confirma el guardado o se reinicia la aplicación, then se desbloquea la navegación por todas las pestañas y la pestaña de Período muestra el rango de fechas activo con opción de modificarlo.
- [x] Given que el usuario registra un nuevo período financiero, when se guarda con éxito, then los campos de entrada quedan limpios/vacíos para un nuevo registro.
- [x] Given que existen períodos guardados, when el usuario visualiza la pestaña de Período, then se muestra el período activo en la parte superior y debajo el listado histórico de períodos registrados.
- [x] Given una tarjeta de período en la lista o vista activa, when el usuario pulsa el botón de 3 puntos, then se despliega un menú contextual con las opciones "Editar" y "Eliminar".
- [x] Given que el usuario selecciona "Editar" en el menú de 3 puntos, when se abre la acción, then se muestra un popup modal centrado con selectores de calendario para modificar las fechas y guardar los cambios con validaciones activas.
- [x] Given que el usuario selecciona "Eliminar" en el menú de 3 puntos, when se confirma la acción, then el período se elimina del repositorio y se actualiza el estado del período activo y la navegación.

## Plan
- [x] Crear la entidad de período financiero con fechas en formato ISO (YYYY-MM-DD) en la base de datos de configuración con su consulta por fecha actual.
- [x] Agregar la pestaña de período financiero en las últimas posiciones de la barra de navegación principal.
- [x] Diseñar la vista de la pestaña de período con selectores de fecha en formato ISO y calendarios interactivos (`DatePickerDialog`).
- [x] Implementar validaciones de coherencia del rango (fecha fin posterior a inicio y cobertura del día actual) con mensajes informativos.
- [x] Habilitar el bloqueo y redirección obligatoria a la pestaña de período al iniciar la app si no existe un período vigente para la fecha actual.
- [x] Incorporar consulta de historial (`getAllPeriods()`) y eliminación (`deletePeriod(id: Long)`) en DAO, Repositorio y ViewModel.
- [x] Limpiar los campos del formulario de creación tras guardar un período con éxito.
- [x] Mostrar en la pantalla el período actual arriba y abajo el historial de períodos guardados.
- [x] Agregar botón de 3 puntos (`MoreVert`) con menú contextual (opciones "Editar" y "Eliminar") en las tarjetas de período.
- [x] Implementar el diálogo centrado (`AlertDialog`) para editar fechas y el flujo de eliminación de períodos.
- [x] Extraer lógica de negocio y validaciones a `PeriodUseCase` (sin solapamiento de fechas, permitir meses pasados y restringir fechas futuras/no actuales).
- [x] Desacoplar ViewModel para depender exclusivamente de `PeriodUseCase`.
- [x] Homogeneizar el menú de 3 puntos de períodos con el estilo y opciones de gastos.
- [x] Configurar el botón central de períodos para crear siempre nuevos registros.

## Test list (TDD)
- [x] `test_financial_period_dao_insert_and_get_period_covering_date` (Repositorio / Room DB)
- [x] `test_period_validation_rejects_end_date_before_start_date` (ViewModel)
- [x] `test_period_validation_rejects_range_not_covering_current_date` (ViewModel)
- [x] `test_save_valid_period_persists_and_updates_active_period` (ViewModel)
- [x] `test_app_starts_on_period_tab_and_locks_navigation_when_no_active_period` (UI / Navegación)
- [x] `test_saving_valid_period_unlocks_navigation_and_shows_active_period_with_edit_option` (UI / Pantalla y Navegación)
- [x] `test_period_screen_shows_validation_error_when_dates_are_invalid` (UI / Pantalla)
- [x] `test_clicking_start_date_opens_date_picker_dialog` (UI / Pantalla)
- [x] `test_selecting_date_in_picker_updates_iso_date_field` (UI / Pantalla)
- [x] `test_date_picker_selection_preserves_validations` (UI / Pantalla)
- [x] `test_get_all_periods_and_delete_period` (Repositorio / Room DB)
- [x] `test_save_period_clears_form_inputs_and_updates_period_list` (ViewModel)
- [x] `test_delete_period_removes_it_and_updates_active_period_state` (ViewModel)
- [x] `test_period_screen_displays_active_period_and_history_list` (UI / Pantalla)
- [x] `test_clicking_three_dots_menu_displays_edit_and_delete_options` (UI / Pantalla)
- [x] `test_selecting_edit_opens_dialog_and_saving_updates_period` (UI / Pantalla)
- [x] `test_edit_dialog_date_pickers_open_and_update_fields` (UI / Pantalla)
- [x] `test_clicking_delete_in_menu_opens_slide_confirmation_dialog` (UI / Pantalla)
- [x] `test_confirming_slide_to_delete_removes_period` (UI / Pantalla)
- [x] `test_period_use_case_allows_past_periods_and_rejects_invalid_ranges` (Dominio / UseCase)
- [x] `test_period_use_case_rejects_overlapping_dates_with_existing_period` (Dominio / UseCase)
- [x] `test_main_period_button_always_creates_new_period` (UI / Pantalla)
- [x] `test_three_dots_menu_styling_and_options_match_expenses` (UI / Pantalla)

## Out of scope
- Opciones de configuración o edición de períodos dentro de la pestaña de Configuración.
- Generación automática o rollover desatendido de períodos futuros sin confirmación.
- Asociación o imputación de ingresos, presupuestos o gastos a este período (alcance de las historias #7, #8 y #9).
