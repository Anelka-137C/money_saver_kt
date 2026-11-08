# Backlog

## Context
Aplicación móvil personal para el monitoreo e histórico de gastos, presupuestos, ingresos y ahorros visualizados por período financiero.
Stack: Android nativo (Kotlin, Jetpack Compose, Room / SQLite)
Expensive decisions: Offline-first con backups a Google Drive; multi-idioma; moneda de preferencia configurable; autenticación por PIN / biometría

## Backlog
- [*] **#1** Como usuario, quiero registrar un gasto con su monto, descripción y categoría/subcategoría, para clasificar y visualizar mis salidas de dinero.
- [*] **#2** Como usuario, quiero editar un gasto existente, para corregir errores en su monto, descripción o clasificación. Requires: #1
- [*] **#3** Como usuario, quiero eliminar un gasto registrado, para mantener mi historial actualizado. Requires: #1
- [*] **#4** Como usuario, quiero cambiar el idioma de la aplicación, para interactuar con ella en mi lengua de preferencia en todas las pantallas.
- [*] **#5** Como usuario, quiero configurar mi moneda de preferencia, para ver reflejados todos los importes en la divisa que elija.
- [*] **#6** Como usuario, quiero configurar las fechas de inicio y fin de mi período financiero según mi cobro o nómina, para establecer el marco temporal de mis finanzas.
- [*] **#7** Como usuario, quiero registrar mis ingresos asignando o gestionando categorías y subcategorías dentro de mi período financiero, para estructurar mis fuentes de dinero. Requires: #6
- [ ] **#8** Como usuario, quiero definir mi presupuesto por categorías a partir de los ingresos registrados en el período, para planificar y limitar mis gastos por rubro. Requires: #7
- [ ] **#9** Como usuario, quiero que el registro de gastos quede condicionado a tener un presupuesto activo en el período, para asegurar que mis gastos se ajusten a lo presupuestado. Requires: #8
- [ ] **#10** Como usuario, quiero apartar dinero de mis ingresos hacia ahorros clasificados por categoría o meta dentro del período, para reservar fondos ordenadamente hacia objetivos específicos. Requires: #7
- [ ] **#11** Como usuario, quiero consultar el histórico de gastos, presupuesto, ingresos y ahorros filtrado por período financiero, para revisar y comparar mi desempeño económico en cada ciclo. Requires: #9, #10
- [ ] **#12** Como usuario, quiero registrar un gasto especificando que los fondos provienen de mis ahorros, para cubrir salidas extraordinarias sin descontar de mi presupuesto corriente. Requires: #10
- [ ] **#13** Como usuario, quiero generar un backup local y sincronizarlo con mi cuenta de Google Drive, para respaldar mi información offline en la nube.
- [ ] **#14** Como usuario, quiero configurar y requerir un PIN o autenticación biométrica (huella) al abrir la aplicación, para proteger el acceso a mis datos financieros.
