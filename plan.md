# Story #8: Definición y modificación de presupuesto por categorías

## Story
Como usuario, quiero definir y modificar mi presupuesto por categorías a partir de mis ingresos y visualizar el total presupuestado en la pantalla de ingresos mediante pestañas superiores, utilizando mi moneda configurada y sin necesidad de indicar fechas, para planificar mis límites de gasto y monitorear la asignación de mi dinero.

## Acceptance criteria
- [x] Given que el usuario se encuentra en la pestaña "Presupuesto" de un período financiero con ingresos disponibles, when registra un presupuesto seleccionando una categoría e ingresando el monto (mostrado según la moneda configurada y sin solicitar fecha), then el presupuesto se guarda, se muestra en la lista y, al regresar a la vista de "Ingresos", la tarjeta de asignación refleja el monto real efectivamente presupuestado y su porcentaje.
- [x] Given que existe un presupuesto registrado para una categoría en el período, when el usuario selecciona la opción de modificar y actualiza su monto por un nuevo valor válido, then el presupuesto refleja el nuevo valor y se recalcula el saldo disponible y el total presupuestado tanto en la vista de presupuesto como en la de ingresos.
- [x] Given que el usuario intenta registrar o modificar un presupuesto con un monto que sobrepasa el total de ingresos disponibles del período o un valor menor o igual a cero, when pulsa guardar, then el sistema muestra un mensaje de advertencia indicando el error y no aplica los cambios.

## Plan
- [x] Crear la persistencia local para guardar y actualizar asignaciones de presupuesto (monto y categoría) vinculadas al período financiero, sin almacenar fechas individuales.
- [x] Implementar la lógica de negocio para calcular el total presupuestado real, el saldo de ingresos disponible y validar que las asignaciones no sobrepasen dicho total.
- [x] Exponer en la capa de presentación el estado de presupuesto (total presupuestado real, disponible, listado y edición) y la moneda de preferencia para ambas vistas.
- [x] Incorporar pestañas de navegación superiores en la pantalla para alternar de forma fluida entre ingresos y presupuesto compartiendo el contexto del período activo.
- [x] Construir la interfaz de presupuesto (diálogo unificado de creación/edición y listado) y actualizar la tarjeta de asignación en la vista de ingresos con el total presupuestado real.

## Test list (TDD)
- [x] `test_budget_dao_save_and_update_budget_by_category_and_period` (Repositorio / Room DB)
- [x] `test_budget_use_case_calculates_available_income_and_validates_exceeding_budget` (UseCase)
- [x] `test_budget_use_case_returns_total_budgeted_for_period` (UseCase)
- [x] `test_viewmodel_create_and_edit_budget_updates_budgeted_total_and_reflects_in_income_state` (ViewModel)
- [x] `test_incomes_view_top_tabs_switch_between_income_and_budget_and_updates_allocation_card` (UI / Compose)

## Out of scope
- Eliminación de asignaciones de presupuesto (se abordará en una historia posterior).
- Condicionamiento del registro de gastos a la existencia de un presupuesto activo (corresponde a la Historia #9).
- Asignación de fondos para ahorros o metas (corresponde a la Historia #10).
- Históricos comparativos de presupuestos entre diferentes períodos (corresponde a la Historia #11).

## Suggestions (your call)
- Reemplazar el valor presupuestado simulado actual (`34%`) en `IncomeAllocationCard` por el cálculo dinámico basado en la suma de los presupuestos reales del período seleccionado.
- Reutilizar el diálogo de ingresos adaptándolo para presupuesto (sin selector de fecha/hora y aplicando el símbolo y formato de la moneda configurada).
- Utilizar las categorías de gastos existentes mediante `CategorySelectionSheet` para que el presupuesto se asigne directamente a los rubros donde luego se registrarán los gastos.
- Proponer para el backlog: *#8.1: Como usuario, quiero eliminar un presupuesto asignado a una categoría, para liberar saldo disponible hacia otros rubros.*
