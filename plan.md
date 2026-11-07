# Story #5: Configurar moneda de preferencia con entidad de base de datos y formato ISO 4217

## Story
Como usuario, quiero configurar mi moneda de preferencia desde la pantalla de Configuración a partir de las divisas disponibles en el sistema, para ver reflejados todos los importes de gastos con su símbolo y formato numérico estándar ISO 4217.

## Acceptance criteria
- [ ] Given que el usuario se encuentra en la pantalla de Configuración, when accede a la sección de moneda, then visualiza el listado de divisas disponibles (Pesos colombianos, Reales brasileños, Euros y Dólares) y puede seleccionar una de ellas como la divisa activa.
- [ ] Given que el usuario configuró una moneda de preferencia, when consulta el historial de gastos o interactúa con los campos de importe (registro y edición), then los montos se muestran con el símbolo y el formato numérico (decimales) correspondiente según el estándar ISO 4217.
- [ ] Given que se configuró una moneda de preferencia, when el usuario reinicia la aplicación, then la moneda seleccionada se mantiene activa y todos los montos conservan dicho formato.

## Plan
- [ ] Crear la entidad y acceso a base de datos para almacenar las divisas (identificador, nombre, símbolo y formato numérico ISO 4217), con precarga inicial de COP, BRL, EUR y USD.
- [ ] Implementar la persistencia de la divisa de preferencia activa seleccionada por el usuario.
- [ ] Incorporar en la pantalla de Configuración la lista de divisas obtenida de la base de datos, marcando la opción seleccionada.
- [ ] Aplicar el formato numérico y símbolo de la divisa activa en las tarjetas del historial de gastos.
- [ ] Adaptar los indicadores de moneda en el formulario de registro y en el modal de edición de gastos.

## Out of scope
- Interfaz de usuario para crear, modificar o eliminar monedas del catálogo (se precargan las 4 divisas solicitadas).
- Conversión de divisas o recálculo de montos históricos con tasas de cambio.
- Divisas adicionales fuera de las 4 iniciales (COP, BRL, EUR, USD).
- Personalización manual de separadores numéricos fuera de la definición estándar ISO 4217 de cada moneda.

## Suggestions (your call)
- **Definición de campos de la entidad**: Definir los campos de la tabla como: `id` (clave primaria con el código ISO ej. "COP", "BRL", "EUR", "USD"), `name` ("Peso colombiano", "Real brasileño", "Euro", "Dólar"), `symbol` ("$", "R$", "€", "$") y `decimalDigits` (0 para COP; 2 para BRL, EUR y USD según la norma ISO 4217).
- **Sembrado (Pre-populating)**: Cargar las 4 divisas iniciales en la base de datos mediante la inicialización del repositorio o callback de Room al crear la base de datos, asegurando que estén disponibles al primer inicio.
- **Persistencia de preferencia**: Guardar el identificador de la divisa elegida en almacenamiento local liviano (`SharedPreferences`) y exponer la divisa activa reactivamente para que la pantalla de gastos actualice sus formatos inmediatamente.
