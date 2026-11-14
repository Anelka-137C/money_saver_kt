# Story #9: Condicionamiento del registro de gastos a un presupuesto activo en el período

## Story
Como usuario, quiero que el registro de gastos quede condicionado a tener un presupuesto activo en el período, para asegurar que mis gastos se ajusten a lo presupuestado.

## Acceptance criteria
- [ ] Given que el usuario se encuentra en la pantalla de gastos y el período financiero activo no cuenta con ningún presupuesto registrado, when visualiza la pantalla o intenta registrar un gasto, then el sistema muestra un mensaje informativo indicando que se requiere un presupuesto activo en el período y no permite guardar el gasto.
- [ ] Given que el período financiero activo cuenta con presupuestos registrados, when el usuario ingresa un monto válido, selecciona una categoría con presupuesto asignado en dicho período y pulsa guardar, then el gasto se guarda exitosamente vinculado al período y se refleja en la lista de gastos.
- [ ] Given que el usuario intenta guardar un gasto seleccionando una categoría que no tiene un presupuesto asignado en el período activo, when pulsa guardar, then el sistema muestra un mensaje de advertencia indicando que la categoría seleccionada no tiene presupuesto asignado y no aplica el registro.

## Plan
- [ ] Asociar los gastos al período financiero activo en la persistencia para compartir el contexto temporal de los presupuestos.
- [ ] Implementar la lógica de negocio para validar que el período cuente con presupuesto activo y que la categoría del gasto posea presupuesto en dicho período.
- [ ] Exponer en la capa de presentación el estado de presupuestos activos del período y la lista de gastos filtrada por período.
- [ ] Mostrar en la interfaz un estado informativo o advertencia cuando el período no cuente con presupuesto activo, bloqueando el guardado de nuevos gastos.
- [ ] Restringir la selección o validación de categorías en el formulario de gastos verificando que cuenten con presupuesto activo en el período.

## Test list (TDD)
- [ ] `test_expense_dao_and_repository_associate_and_query_by_period` (Repositorio / Room DB)
- [ ] `test_expense_use_case_blocks_saving_when_no_active_budget_in_period` (UseCase)
- [ ] `test_expense_use_case_allows_saving_when_category_has_active_budget_in_period` (UseCase)
- [ ] `test_expense_viewmodel_conditions_expense_creation_based_on_active_period_budgets` (ViewModel)
- [ ] `test_expenses_view_shows_no_budget_banner_and_disables_or_validates_expense_registration` (UI / Compose)

## Out of scope
- Bloqueo por sobregiro cuando un gasto supera el monto asignado al presupuesto de la categoría (se mantiene flexible o para una historia de alertas).
- Registro de gastos cubiertos desde ahorros (corresponde a la Historia #12).
- Históricos comparativos y reportes de ejecución presupuestaria entre períodos (corresponde a la Historia #11).
- Creación o edición de presupuestos desde la pantalla de gastos (se gestiona desde la pestaña Presupuesto de la Historia #8).

## Suggestions (your call)
- En el selector de categorías de gastos, filtrar o resaltar visualmente solo aquellas categorías que tienen presupuesto asignado en el período activo, guiando al usuario y previniendo selecciones inválidas.
- En el mensaje informativo cuando no hay presupuesto en el período, incluir un botón de acción rápida ("Definir Presupuesto") que cambie a la pestaña de Presupuesto para facilitar el flujo.
- Confirmar si estás de acuerdo con que la validación sea por categoría presupuestada (criterio 3) o si prefieres que baste con tener cualquier presupuesto activo en el período a nivel global.
