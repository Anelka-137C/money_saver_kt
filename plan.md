# Story #4.1: Crear categorías con color y subcategorías personalizadas

## Story
Como usuario, quiero crear categorías personalizadas con un color identificador y sus subcategorías para mis gastos, para organizar y reconocer visualmente mis salidas de dinero según mis necesidades.

## Acceptance criteria
- [x] Given que estoy en el selector de categorías al registrar o editar un gasto, when ingreso un nombre, elijo un color de la paleta y confirmo la creación, then la nueva categoría queda guardada y se visualiza con su color en la lista.
- [x] Given que he seleccionado una categoría dentro del selector, when ingreso el nombre de una nueva subcategoría y confirmo, then la subcategoría se agrega a esa categoría y queda disponible para asignarla al gasto.
- [x] Given que intento registrar una categoría o subcategoría, when el campo de nombre está en blanco o coincide con un nombre ya existente en esa lista, then se muestra un mensaje de advertencia y no se permite la creación duplicada.

## Plan
- [x] Persistir categorías (con su identificador de color) y subcategorías en la base de datos local pre-cargando los datos iniciales.
- [x] Implementar la validación y el caso de uso para registrar categorías con color y subcategorías.
- [x] Cargar dinámicamente las categorías con sus respectivos colores hacia la interfaz de usuario.
- [x] Agregar en el selector de categorías el formulario de creación que incluye una paleta de colores seleccionables.
- [x] Agregar en la vista de subcategorías el formulario para registrar una nueva subcategoría vinculada.

## Out of scope
- Selector libre de color RGB/hexadecimal (se ofrecerá una paleta predefinida para agilizar la selección).
- Edición de nombres, colores o subcategorías existentes (historia #4.2).
- Eliminación de categorías y subcategorías (historia #4.3).
- Gestión de categorías de ingresos y ahorros (historias #6 y #7).

## Suggestions (your call)
- Ofrecer una paleta de 6 a 8 colores armónicos en forma de círculos seleccionables para mantener consistencia con el diseño oscuro de la app.
- Extraer el componente selector de categorías a su propio archivo composable, dado que la pantalla principal supera las 1.100 líneas.
