package com.example.money_saver_android.ui.components.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.ui.hideKeyboard
import com.example.money_saver_android.ui.parseHexColor
import com.example.money_saver_android.ui.predefinedPalette
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Shared edit state for category edit modal dialogs across all accounting modules.
 */
data class CategoryEditState(
    val originalName: String = "",
    val name: String = "",
    val color: String = "#4E73DF",
    val icon: String = "💰",
    val errorMessage: String? = null
)

/**
 * Configurable testTag identifiers allowing module-specific test compatibility.
 */
data class CategoryManagerTestTags(
    val editDialog: String = "dialog_edit_category",
    val editNameInput: String = "input_edit_category_name",
    val editColorOptionPrefix: String = "color_option_edit_category_",
    val editErrorText: String = "text_edit_category_error",
    val editCancelButton: String = "btn_cancel_edit_category",
    val editSaveButton: String = "btn_save_edit_category",
    val deleteDialog: String = "dialog_delete_category",
    val deleteConfirmButton: String = "btn_slide_to_confirm_delete_category",
    val deleteCancelButton: String = "btn_cancel_delete_category"
)

/**
 * Generic reusable category and subcategory selector + manager component in `atom/`.
 * Replicates the ExpensesView design: category creation with color palette, three-dots context
 * menu (Modificar / Eliminar), subcategory management with optional selection, EditCategoryDialog,
 * and DeleteCategoryConfirmationDialog with SlideToConfirmButton.
 */
@Composable
fun CategoryManagerSheet(
    visible: Boolean,
    categories: List<Category>,
    newCategoryName: String,
    selectedColorHex: String,
    categoryError: String?,
    newSubcategoryName: String,
    subcategoryError: String?,
    editingCategory: CategoryEditState?,
    categoryToDelete: Category?,
    onCategoryNameChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onSubcategoryNameChange: (String) -> Unit,
    onCreateSubcategory: (categoryName: String) -> Unit,
    onEditCategoryClick: (category: Category) -> Unit,
    onEditCategoryNameChange: (String) -> Unit,
    onEditCategoryColorSelect: (String) -> Unit,
    onConfirmEditCategory: () -> Unit,
    onDismissEditCategory: () -> Unit,
    onDeleteCategoryClick: (categoryName: String) -> Unit,
    onConfirmDeleteCategory: () -> Unit,
    onDismissDeleteCategory: () -> Unit,
    onDeleteSubcategory: (categoryName: String, subcategoryName: String) -> Unit,
    onClearCategoryError: () -> Unit,
    onClearSubcategoryError: () -> Unit,
    onDismiss: () -> Unit,
    onCategorySelected: (category: String, subcategory: String) -> Unit,
    testTags: CategoryManagerTestTags = CategoryManagerTestTags(),
    deleteDialogTitle: String = tOrFallback("expenses.delete_category_dialog.title", "¿Eliminar esta categoría?"),
    deleteDialogWarning: String = tOrFallback(
        "expenses.delete_category_dialog.warning_message",
        "Esta acción no se puede deshacer. Desliza el botón para confirmar."
    ),
    deleteDialogCancel: String = tOrFallback("expenses.delete_category_dialog.cancel_button", "Cancelar")
) {
    if (visible) {
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
            onEditCategory = onEditCategoryClick,
            onDeleteCategory = onDeleteCategoryClick,
            onDeleteSubcategory = onDeleteSubcategory,
            onClearCategoryError = onClearCategoryError,
            onClearSubcategoryError = onClearSubcategoryError,
            onDismiss = onDismiss,
            onCategorySelected = onCategorySelected
        )
    }

    editingCategory?.let { editState ->
        EditCategoryDialog(
            editState = editState,
            onNameChange = onEditCategoryNameChange,
            onColorSelect = onEditCategoryColorSelect,
            onConfirm = onConfirmEditCategory,
            onDismiss = onDismissEditCategory,
            testTags = testTags
        )
    }

    categoryToDelete?.let { category ->
        DeleteCategoryConfirmationDialog(
            category = category,
            onConfirm = onConfirmDeleteCategory,
            onDismiss = onDismissDeleteCategory,
            title = deleteDialogTitle,
            warningMessage = deleteDialogWarning,
            cancelLabel = deleteDialogCancel,
            testTags = testTags
        )
    }
}

/**
 * Core ModalBottomSheet content for selecting and managing categories and subcategories.
 * Also used by legacy wrapper `ui/CategorySelectionSheet.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionSheetContent(
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
    onClearCategoryError: (() -> Unit)? = null,
    onClearSubcategoryError: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onCategorySelected: (category: String, subcategory: String) -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    val selectedCategory = categories.find { it.name == selectedCategoryName }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selectedCategoryColor = parseHexColor(selectedColorHex)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val handleDismiss = {
        onClearCategoryError?.invoke()
        onClearSubcategoryError?.invoke()
        onDismiss()
    }

    val handleSelect = { category: String, subcategory: String ->
        onClearCategoryError?.invoke()
        onClearSubcategoryError?.invoke()
        onCategorySelected(category, subcategory)
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        contentColor = TextPrimary
    ) {
        val sheetContext = LocalContext.current
        val sheetView = LocalView.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedCategory != null) {
                    IconButton(onClick = {
                        onClearSubcategoryError?.invoke()
                        selectedCategoryName = null
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                }
                Text(
                    text = if (selectedCategory == null) "Seleccionar categoría" else selectedCategory.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(
                    onClick = handleDismiss,
                    modifier = Modifier.testTag("btn_close_category_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedCategory == null) {
                NewCategoryFormCard(
                    newCategoryName = newCategoryName,
                    selectedColorHex = selectedColorHex,
                    selectedCategoryColor = selectedCategoryColor,
                    categoryError = categoryError,
                    onCategoryNameChange = onCategoryNameChange,
                    onColorSelect = onColorSelect,
                    onCreateCategory = {
                        hideKeyboard(sheetContext, sheetView, keyboardController, focusManager)
                        onCreateCategory()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        CategoryRowItem(
                            category = category,
                            onSelectCategory = {
                                onClearSubcategoryError?.invoke()
                                selectedCategoryName = category.name
                            },
                            onEditCategory = onEditCategory,
                            onDeleteCategory = onDeleteCategory
                        )
                    }
                }
            } else {
                val parentCategoryColor = parseHexColor(selectedCategory.color)
                ContinueWithoutSubcategoryCard(
                    category = selectedCategory,
                    parentCategoryColor = parentCategoryColor,
                    onClick = { handleSelect(selectedCategory.name, "") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                NewSubcategoryFormCard(
                    parentCategoryName = selectedCategory.name,
                    parentCategoryColor = parentCategoryColor,
                    newSubcategoryName = newSubcategoryName,
                    subcategoryError = subcategoryError,
                    onSubcategoryNameChange = onSubcategoryNameChange,
                    onCreateSubcategory = {
                        hideKeyboard(sheetContext, sheetView, keyboardController, focusManager)
                        onCreateSubcategory(selectedCategory.name)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedCategory.subcategories) { sub ->
                        SubcategoryRowItem(
                            subcategoryName = sub.name,
                            onSelect = { handleSelect(selectedCategory.name, sub.name) },
                            onDelete = onDeleteSubcategory?.let { deleteFn ->
                                { deleteFn(selectedCategory.name, sub.name) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NewCategoryFormCard(
    newCategoryName: String,
    selectedColorHex: String,
    selectedCategoryColor: Color,
    categoryError: String?,
    onCategoryNameChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onCreateCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FieldBackground)
            .padding(12.dp)
    ) {
        Text(
            text = "Nueva categoría",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = onCategoryNameChange,
                placeholder = { Text("Nombre de la categoría", color = TextSecondary, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreateCategory() }),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = selectedCategoryColor,
                    unfocusedBorderColor = selectedCategoryColor.copy(alpha = 0.7f),
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                )
            )
            Button(
                onClick = onCreateCategory,
                colors = ButtonDefaults.buttonColors(containerColor = selectedCategoryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Crear", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(predefinedPalette) { hex ->
                val color = parseHexColor(hex)
                val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onColorSelect(hex) }
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else BorderColor,
                            shape = CircleShape
                        )
                )
            }
        }

        if (categoryError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = categoryError,
                color = ErrorRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CategoryRowItem(
    category: Category,
    onSelectCategory: () -> Unit,
    onEditCategory: ((Category) -> Unit)?,
    onDeleteCategory: ((String) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FieldBackground),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelectCategory)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(category.color))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = category.icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onEditCategory != null) {
                val editLabel = tOrFallback("expenses.options.edit", "Modificar")
                val deleteLabel = tOrFallback("expenses.options.delete", "Eliminar")
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_options_category_${category.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones de categoría",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(editLabel, color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditCategory(category)
                            },
                            modifier = Modifier.testTag("btn_edit_category_${category.name}")
                        )
                        if (onDeleteCategory != null) {
                            DropdownMenuItem(
                                text = { Text(deleteLabel, color = ErrorRed) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = ErrorRed
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteCategory(category.name)
                                },
                                modifier = Modifier.testTag("btn_delete_category_${category.name}")
                            )
                        }
                    }
                }
            } else {
                if (onDeleteCategory != null) {
                    IconButton(
                        onClick = { onDeleteCategory(category.name) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_delete_category_${category.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar categoría",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = ">",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onSelectCategory)
                )
            }
        }
    }
}

@Composable
private fun ContinueWithoutSubcategoryCard(
    category: Category,
    parentCategoryColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(parentCategoryColor.copy(alpha = 0.15f))
            .border(1.dp, parentCategoryColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("btn_select_category_without_subcategory")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = category.icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Continuar sin subcategoría",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Usar solo ${category.name}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        Text(
            text = "✓",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GreenAccent
        )
    }
}

@Composable
private fun NewSubcategoryFormCard(
    parentCategoryName: String,
    parentCategoryColor: Color,
    newSubcategoryName: String,
    subcategoryError: String?,
    onSubcategoryNameChange: (String) -> Unit,
    onCreateSubcategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FieldBackground)
            .padding(12.dp)
    ) {
        Text(
            text = "Nueva subcategoría para $parentCategoryName",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newSubcategoryName,
                onValueChange = onSubcategoryNameChange,
                placeholder = { Text("Nombre de la subcategoría", color = TextSecondary, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreateSubcategory() }),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = parentCategoryColor,
                    unfocusedBorderColor = parentCategoryColor.copy(alpha = 0.7f),
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                )
            )
            Button(
                onClick = onCreateSubcategory,
                colors = ButtonDefaults.buttonColors(containerColor = parentCategoryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Crear", tint = Color.White)
            }
        }

        if (subcategoryError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subcategoryError,
                color = ErrorRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SubcategoryRowItem(
    subcategoryName: String,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FieldBackground),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subcategoryName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        Row(
            modifier = Modifier.padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_delete_subcategory_$subcategoryName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar subcategoría",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "✓",
                fontSize = 16.sp,
                color = GreenAccent,
                modifier = Modifier.clickable(onClick = onSelect)
            )
        }
    }
}
