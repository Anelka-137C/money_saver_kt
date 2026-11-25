package com.example.money_saver_android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.ui.components.atom.CategorySelectionSheetContent

val predefinedPalette = listOf(
    "#4E73DF", // Azul
    "#1CC88A", // Verde menta
    "#36B9CC", // Cian
    "#F6C23E", // Amarillo
    "#E74A3B", // Rojo
    "#6F42C1", // Púrpura
    "#FD7E14", // Naranja
    "#E83E8C"  // Rosa
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFF4E73DF)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Legacy compatibility wrapper delegating to `CategorySelectionSheetContent` in `ui/components/atom/`.
 */
@Composable
fun CategorySelectionSheet(
    categories: List<Category>,
    newCategoryName: String,
    selectedColorHex: String,
    categoryError: String?,
    newSubcategoryName: String,
    subcategoryError: String?,
    onCategoryNameChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSubcategoryNameChange: (String) -> Unit,
    onCreateSubcategory: (categoryName: String) -> Unit,
    onEditCategory: ((category: Category) -> Unit)? = null,
    onDeleteCategory: ((categoryName: String) -> Unit)? = null,
    onDeleteSubcategory: ((categoryName: String, subcategoryName: String) -> Unit)? = null,
    onClearSubcategoryError: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onCategorySelected: (category: String, subcategory: String) -> Unit
) {
    CategorySelectionSheetContent(
        categories = categories,
        newCategoryName = newCategoryName,
        selectedColorHex = selectedColorHex,
        categoryError = categoryError,
        newSubcategoryName = newSubcategoryName,
        subcategoryError = subcategoryError,
        onCategoryNameChange = onCategoryNameChange,
        onColorSelect = onColorSelect,
        onCreateCategory = onCreateCategory,
        onSubcategoryNameChange = onSubcategoryNameChange,
        onCreateSubcategory = onCreateSubcategory,
        onEditCategory = onEditCategory,
        onDeleteCategory = onDeleteCategory,
        onDeleteSubcategory = onDeleteSubcategory,
        onClearSubcategoryError = onClearSubcategoryError,
        onDismiss = onDismiss,
        onCategorySelected = onCategorySelected
    )
}
