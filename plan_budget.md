# Story: Sección Asignación de Presupuesto en el tab Presupuesto de IncomesView

## Story
Como usuario, quiero visualizar una sección de "Asignación de presupuesto" en la pestaña de presupuesto de la pantalla de ingresos/presupuestos, que consolide los montos únicamente a nivel de categoría, mostrando el presupuesto asignado, el dinero sobrante (100% disponible mientras no haya gastos registrados) y una barra de progreso con el color de la categoría correspondiente, para monitorear de forma clara e intuitiva la disponibilidad de fondos por rubro.

## Acceptance criteria
- [ ] Given que el usuario se encuentra en la pestaña "Presupuesto" de `IncomesView`, when se tienen presupuestos registrados en el período, then se visualiza la tarjeta de sección "Asignación de presupuesto" ubicada debajo de la tarjeta de resumen (`BudgetOverviewCard`), con la misma estética y jerarquía visual que la sección de asignación del tab de ingresos.
- [ ] Given que un usuario tiene uno o más presupuestos registrados (incluso con distintas subcategorías) para una categoría en el período, when visualiza la sección "Asignación de presupuesto", then los montos se muestran consolidados únicamente por categorías, sumando el presupuesto total de cada categoría sin listar subcategorías en esta sección.
- [ ] Given una categoría con presupuesto asignado y sin gastos registrados en el período activo, when el usuario observa su tarjeta de asignación, then el dinero sobrante mostrado es igual al total del presupuesto de esa categoría, el porcentaje es 100% y la barra de progreso está completamente llena con el color configurado para esa categoría/presupuesto.
- [ ] Given una categoría con presupuesto asignado y con gastos registrados en el período activo, when se consultan los datos, then el dinero sobrante se reduce restando dichos gastos del presupuesto de esa categoría (`sobrante = max(0, presupuesto - gastos)`), y el porcentaje y la barra de progreso disminuyen proporcionalmente.
- [ ] Given los gastos registrados en la aplicación, when se calcula el dinero sobrante de una categoría en un período, then únicamente se consideran los gastos cuya fecha se encuentre dentro del rango del período financiero seleccionado (`startDate` a `endDate`).
- [ ] Given una categoría presupuestada con un color personalizado o predeterminado (`hexColor`), when se renderiza su indicador de progreso en la tarjeta de asignación, then el color de la barra (`LinearProgressIndicator`) coincide exactamente con el color asignado a la categoría/presupuesto.
- [ ] Given que no hay presupuestos registrados en el período actual, when el usuario visualiza la pestaña de presupuesto, then la sección de asignación muestra un estado vacío informativo que indica que no hay presupuestos asignados para el período.

## Plan
- [ ] **Extensión de Capa de Dominio y Datos**:
  - Agregar parámetro opcional `expenseRepository: ExpenseRepository? = null` en `IncomeUseCase`.
  - Exponer `fun getExpenses(): Flow<List<Expense>>` en `IncomeUseCase`.
  - Crear lógica de consolidación y cálculo por categorías (`calculateCategoryBudgets` o modelo `CategoryBudgetItem`) que calcule para cada categoría: total presupuestado, gastos filtrados por fecha del período, saldo sobrante, porcentaje remanente y progreso `[0f..1f]`.
- [ ] **Integración en ViewModel y MainActivity**:
  - Agregar `expenses: List<Expense> = emptyList()` a `IncomeUiState`.
  - En `IncomeViewModel`, recolectar reactivamente `incomeUseCase.getExpenses()` para actualizar el estado cuando se creen, modifiquen o eliminen gastos.
  - En `MainActivity.kt`, suministrar `repository` (`RoomExpenseRepository`) al instanciar `incomeUseCase`.
- [ ] **Componente UI `BudgetAllocationCard`**:
  - Crear `com.example.money_saver_android.ui.components.budget.BudgetAllocationCard.kt` replicando el diseño de `IncomeAllocationCard` (`Card` con `CardBackground`, esquinas `20.dp`, bordes `BorderColor`).
  - Crear `BudgetAllocationMiniCard` con:
    - Ícono y nombre de la categoría.
    - Dinero sobrante en texto destacado (negrita).
    - Presupuesto total de la categoría como subtítulo (`de $X` o `Presupuesto: $X`).
    - Porcentaje remanente en texto coloreado con el color de la categoría.
    - `LinearProgressIndicator` con `color = categoryColor` (`parseHexColor(category.color)`) y `trackColor = CardBackground`.
  - Distribuir las tarjetas de categorías en filas de 2 (`chunked(2)`), manteniendo la coherencia visual con `IncomeAllocationCard`.
- [ ] **Integración en `IncomesView.kt`**:
  - En `FinanceTopTab.BUDGET`, insertar `BudgetAllocationCard` entre `BudgetOverviewCard` y el botón de acción de agregar presupuesto.
  - Pasar los datos de `state.budgets`, `state.budgetCategories`, `state.expenses`, `activePeriod` y `currency`.
- [ ] **Internacionalización (i18n)**:
  - Añadir claves en `assets/es.json`, `en.json`, `de.json` y `pt.json`:
    - `budget.allocation.title`: "Asignación de Presupuesto" / "Budget Allocation"
    - `budget.allocation.details`: "Detalles >" / "Details >"
    - `budget.allocation.budget_label`: "Presupuesto" / "Budget"
    - `budget.allocation.remaining_label`: "Sobrante" / "Remaining"
    - `budget.allocation.empty_message`: "No hay presupuestos asignados para este período" / "No budgets allocated for this period"

## Test list (TDD)
- [ ] `test_calculate_category_budgets_consolidates_subcategories_into_single_category` (UseCase / Helper Unit Test)
- [ ] `test_calculate_category_budgets_with_zero_expenses_returns_full_budget_as_remaining_and_progress_1` (UseCase / Helper Unit Test)
- [ ] `test_calculate_category_budgets_with_partial_expenses_in_period_calculates_remaining_correctly` (UseCase / Helper Unit Test)
- [ ] `test_calculate_category_budgets_ignores_expenses_outside_period_date_range` (UseCase / Helper Unit Test)
- [ ] `test_income_viewmodel_collects_expenses_and_exposes_in_ui_state` (ViewModel Unit Test)
- [ ] `test_budget_tab_displays_budget_allocation_card_with_categories_and_colors` (UI / Compose Test)
- [ ] `test_budget_allocation_card_updates_remaining_reactively_when_expense_is_added` (UI / Compose Test)

## Out of scope
- Bloqueo o advertencias de sobregiro en la creación de gastos (corresponde a la Historia #9).
- Modificación directa del presupuesto pulsando sobre la tarjeta de asignación (la edición se realiza desde la lista de desglose individual de presupuestos).
- Gestión de presupuestos con fondos provenientes de ahorros (corresponde a la Historia #12).

## Suggestions (your call)
- En caso de que los gastos de una categoría superen el 100% del presupuesto asignado, la barra se fijará en 0f y el monto sobrante mostrará $0 (o número negativo formateado) para no romper la interfaz visual.
