# Backlog

## Context
Aplicación móvil personal para el monitoreo de gastos, ahorros e ingresos categorizados por período financiero.
Stack: Android nativo (Kotlin, Jetpack Compose, Room / SQLite)
Expensive decisions: Offline-first con backups a Google Drive; multi-idioma; moneda de preferencia configurable; autenticación por PIN / biometría

## Backlog
- [*] **#1** Como usuario, quiero registrar un gasto con su monto, descripción y categoría/subcategoría, para clasificar y visualizar mis salidas de dinero.
- [*] **#2** Como usuario, quiero editar un gasto existente, para corregir errores en su monto, descripción o clasificación. Requires: #1
- [ ] **#3** Como usuario, quiero eliminar un gasto registrado, para mantener mi historial actualizado. Requires: #1
- [ ] **#4** Como usuario, quiero crear, editar y eliminar categorías y subcategorías para gastos, ingresos y ahorros, para adaptar la clasificación a mis necesidades.
- [ ] **#5** Como usuario, quiero configurar los días de mi período financiero según mi fecha de cobro de nómina, para acotar mi ciclo financiero.
- [ ] **#6** Como usuario, quiero registrar los ingresos clasificados por categoría/subcategoría dentro de mi período financiero, para conocer el presupuesto disponible en ese ciclo. Requires: #5
- [ ] **#7** Como usuario, quiero registrar un monto destinado al ahorro con su categoría/subcategoría dentro de mi período financiero, para apartar dinero deliberadamente hacia metas específicas. Requires: #5
- [ ] **#8** Como usuario, quiero editar y eliminar montos destinados al ahorro, para corregir o ajustar los aportes que he separado. Requires: #7
- [ ] **#9** Como usuario, quiero visualizar el total acumulado de mis ahorros, para conocer el avance de mis fondos guardados. Requires: #7
- [ ] **#10** Como usuario, quiero cambiar mi moneda de preferencia en cualquier momento, para ver todos los importes en la divisa que elija. Requires: #1
- [ ] **#11** Como usuario, quiero cambiar el idioma de la aplicación, para interactuar con ella en mi lengua de preferencia.
- [ ] **#12** Como usuario, quiero generar un backup local y subirlo a mi cuenta de Google Drive, para respaldar mi información offline en la nube. Requires: #1
- [ ] **#13** Como usuario, quiero configurar y requerir un PIN o autenticación biométrica (huella) al abrir la aplicación, para proteger el acceso a mis datos financieros. Requires: #1
