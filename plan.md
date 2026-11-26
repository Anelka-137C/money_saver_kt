# Plan de Implementación: Rediseño de Barra de Navegación Inferior Flotante

## Objetivo
Rediseñar la barra de navegación inferior actual en `MainContainer.kt` para transformarla en una **única cápsula flotante redondeada con sombra** inspirada en `design/barra_inferior.png`, manteniendo la paleta de colores oscura y acento verde esmeralda de la aplicación (`Color.kt`), la compatibilidad con el bloqueo por período y todos los tests existentes.

## Decisiones Aprobadas
- **Estructura (Opción B):** Una única cápsula flotante unificada con las 3 pestañas (`Contabilidad`, `Configuración`, `Período`), donde la pestaña activa destaca mediante una pastilla interior redondeada con tinte suave (`GreenAccent.copy(alpha = 0.16f)`), ícono y texto en `GreenAccent`.
- **Integración con scroll (Opción 2):** Ubicada en el slot `bottomBar` del `Scaffold` con contenedor exterior transparente, `navigationBarsPadding()`, márgenes horizontales e inferior y sombra elevada; el contenido de cada pantalla termina limpiamente encima de la barra mediante `innerPadding` (sin modificar `IncomesView`, `PeriodView` ni `SettingsView`).

## Especificación Visual (Paleta Money Saver)
- **Contenedor flotante:** `CardBackground` (`#161B22`), `RoundedCornerShape(24.dp)`, borde sutil `1.dp` en `BorderColor` (`#30363D`), `Modifier.shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)` y márgenes externos `horizontal = 16.dp`, `vertical = 10.dp` + `navigationBarsPadding()`.
- **Ítem seleccionado:** Pastilla interior `RoundedCornerShape(18.dp)` con relleno `GreenAccent.copy(alpha = 0.16f)` (`#10B981` al 16%), ícono y etiqueta en `GreenAccent` (`FontWeight.SemiBold`).
- **Ítem no seleccionado:** Fondo transparente, ícono y etiqueta en `TextSecondary` (`#8B949E`, `FontWeight.Medium`). Si `isPeriodLocked == true`, la pestaña `Contabilidad` se muestra atenuada (`alpha = 0.4f`) y mantiene el bloqueo de navegación.
- **Transiciones y accesibilidad:** Animación suave de color (`animateColorAsState`), semántica `Role.Tab` y `selected = isSelected`, conservando los `testTag` actuales (`tab_incomes`, `tab_settings`, `tab_period`) más `floating_bottom_nav` en el contenedor.

## Cambios Propuestos
1. **[NEW] `app/src/main/java/com/example/money_saver_android/ui/components/navigation/FloatingBottomNavigationBar.kt`**
   - Crear el composable `FloatingBottomNavigationBar(activeTab, isPeriodLocked, i18nManager, onTabSelected, modifier)` implementando la cápsula flotante con sombra, pastilla activa suave y soporte completo de traducciones (`navigation.tab_incomes`, `navigation.tab_settings`, `navigation.tab_period`).
2. **[MODIFY] `app/src/main/java/com/example/money_saver_android/ui/MainContainer.kt`**
   - Reemplazar `NavigationBar` / `NavigationBarItem` en el slot `bottomBar` del `Scaffold` por `FloatingBottomNavigationBar(...)`.
   - Mantener `Modifier.padding(innerPadding)` en el `Box` de contenido y eliminar los imports no utilizados de `NavigationBar`.
3. **[MODIFY] `app/src/test/java/com/example/money_saver_android/ui/NavigationTest.kt`**
   - Mantener en verde los 7 tests existentes.
   - Agregar `test_floating_bottom_bar_displays_all_tabs_and_reflects_selected_state` validando `floating_bottom_nav`, renderizado de las 3 pestañas, actualización del estado `isSelected()` al alternar pestañas y persistencia del bloqueo de `tab_incomes` cuando `isPeriodLocked == true`.

## Plan de Verificación
- **Pruebas automatizadas:**
  - `./gradlew testDebugUnitTest --offline --tests "com.example.money_saver_android.ui.NavigationTest"`
  - `./gradlew testDebugUnitTest --offline`
  - `./gradlew assembleDebug --offline`
- **Verificación manual:**
  - Revisar en emulador/dispositivo la elevación visual, sombra, bordes redondeados y separación sobre la barra de gestos del sistema (`edge-to-edge`) en `Contabilidad`, `Configuración` y `Período`.
  - Probar los 4 idiomas (`es`, `en`, `de`, `pt`) verificando que las etiquetas no se corten ni desborden.
