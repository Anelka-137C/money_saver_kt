# Story #4: Cambiar el idioma de la aplicación y agregar tab de configuración

## Story
Como usuario, quiero acceder a una pestaña de configuración para cambiar el idioma de la aplicación entre Español, Inglés, Alemán y Portugués (Brasil), para interactuar con la aplicación en mi lengua de preferencia desde un espacio dedicado.

## Acceptance criteria
- [ ] Given que el usuario está en la aplicación, when interactúa con las pestañas de navegación, then puede alternar fluidamente entre la vista de Gastos y la vista de Configuración.
- [ ] Given que el usuario se encuentra en la pantalla de Configuración, when selecciona un idioma (Español, Inglés, Alemán o Portugués de Brasil), then todos los textos de la aplicación (incluyendo pestañas, Gastos y Configuración) se actualizan de inmediato al idioma elegido.
- [ ] Given que se configuró un idioma de preferencia, when el usuario reinicia la aplicación, then todos los textos se presentan directamente en el idioma seleccionado.

## Plan
- [ ] Crear los archivos JSON con las traducciones para Español, Inglés, Alemán y Portugués de Brasil.
- [ ] Integrar el motor de i18next-android para resolver los textos traducidos desde los archivos JSON.
- [ ] Persistir la clave del idioma seleccionado en el almacenamiento local ligero (sin base de datos).
- [ ] Implementar la barra de pestañas para alternar entre la pantalla de Gastos y la de Configuración.
- [ ] Construir la vista de Configuración con el selector de idioma conectado a la actualización en tiempo real de la interfaz.

## Out of scope
- Almacenamiento o sincronización de traducciones en la base de datos local (solo archivos JSON y persistencia liviana del locale elegido).
- Configuraciones adicionales dentro de la pestaña (como moneda o backups, correspondientes a historias posteriores).
- Idiomas adicionales fuera de Español, Inglés, Alemán y Portugués (Brasil).
- Traducción de contenido libre ingresado por el usuario (descripciones de gastos o nombres de categorías).

## Suggestions (your call)
- Implementar una barra de navegación inferior (`NavigationBar` de Material 3) como contenedor de pestañas para alternar entre Gastos y Configuración, preparando la navegación para futuras opciones.
- Ubicar los archivos JSON (`es.json`, `en.json`, `de.json`, `pt.json`) en `assets` para su lectura directa a través del loader de `i18next-android`.
- Mostrar en la vista de Configuración una lista de selección simple donde cada idioma aparezca en su forma nativa ("Español", "English", "Deutsch", "Português (Brasil)").
