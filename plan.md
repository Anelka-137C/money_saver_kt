# Story #1: Registrar gasto y visualizarlo en lista

## Story
Como usuario, quiero registrar un gasto con su monto, descripción y categoría/subcategoría, para clasificar y visualizar mis salidas de dinero.

## Acceptance criteria
- [ ] Given que estoy en la pantalla de gastos, when ingreso un monto válido, fecha, descripción, elijo la categoría/subcategoría mediante el flujo de selección y confirmo, then el gasto se guarda y se muestra en la lista respetando el estilo de tarjeta del diseño.
- [ ] Given que estoy en el formulario de registro de gasto, when intento guardar con un monto inválido (vacío o cero), then se muestra un estado de validación y el gasto no se registra.
- [ ] Given que he registrado gastos previamente, when se reinicia la aplicación, then los gastos registrados se cargan y permanecen visibles en la lista desde la base de datos local.

## Plan
- Configurar la persistencia local para almacenar gastos (monto, fecha, descripción, categoría y subcategoría).
- Implementar la inserción de un gasto y la consulta reactiva de la lista de gastos registrados.
- Crear la lógica de presentación con validación del monto y control del estado del formulario y selección de categorías.
- Construir la interfaz replicando fielmente el diseño visual de design/gastos.png (tema oscuro, tipografías/acentos verdes, tarjeta de captura de monto y tarjeta de ítem en la lista).
- Integrar la vista en el punto de entrada de la aplicación para que esté disponible de inmediato al iniciar.

## Out of scope
- Gráficas circulares de progreso y métricas de balance ("Spent", "Budgeted", "Available") (pertenecen a Historias #5 y #8).
- Creación de nuevas categorías personalizadas desde el botón "+ Create category" (pertenece a la Historia #4; se utiliza un conjunto predefinido inicial para la navegación).
- Selector y filtrado por meses o períodos de nómina (Historias #4 y #5).
- Edición y eliminación de gastos (Historias #2 y #3).

## Suggestions (your call)
- Definir la paleta de colores del tema oscuro directamente en el `Theme.kt` de Compose (fondos oscuros, acento verde `#00E676` / `#10B981` y superficies de tarjetas) para alinear toda la app con design/gastos.png.
- Usar Room (SQLite) como capa de almacenamiento local.
