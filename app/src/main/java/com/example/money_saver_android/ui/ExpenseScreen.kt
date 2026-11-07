package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.viewmodel.EditExpenseState
import com.example.money_saver_android.viewmodel.ExpenseUiState
import com.example.money_saver_android.viewmodel.ExpenseViewModel
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showEditCategorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    // Inicializar fecha de hoy por defecto si está vacía
    val todayFormatted = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    if (state.dateInput.isBlank()) {
        viewModel.onDateChange(todayFormatted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gastos",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Formulario "Add Expense"
            item {
                AddExpenseCard(
                    state = state,
                    onAmountChange = viewModel::onAmountChange,
                    onDateChange = viewModel::onDateChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onSelectCategoryClick = { showCategorySheet = true },
                    onSaveClick = viewModel::saveExpense
                )
            }

            // Lista de gastos
            item {
                Text(
                    text = "Historial de gastos",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.expenses.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay gastos registrados",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseCardItem(
                        expense = expense,
                        onEditClick = { viewModel.startEditing(expense) },
                        onDeleteClick = { viewModel.requestDeleteExpense(expense) }
                    )
                }
            }
        }

        if (showCategorySheet) {
            CategorySelectionSheet(
                categories = state.categories,
                newCategoryName = state.newCategoryNameInput,
                selectedColorHex = state.newCategoryColorInput,
                categoryError = state.categoryErrorMessage,
                newSubcategoryName = state.newSubcategoryNameInput,
                subcategoryError = state.subcategoryErrorMessage,
                onCategoryNameChange = viewModel::onNewCategoryNameChange,
                onColorSelect = viewModel::onNewCategoryColorChange,
                onCreateCategory = viewModel::createCategory,
                onSubcategoryNameChange = viewModel::onNewSubcategoryNameChange,
                onCreateSubcategory = viewModel::createSubcategory,
                onDismiss = {
                    showCategorySheet = false
                    viewModel.clearCategoryError()
                    viewModel.clearSubcategoryError()
                },
                onCategorySelected = { category, subcategory ->
                    viewModel.onCategoryChange(category, subcategory)
                    showCategorySheet = false
                }
            )
        }

        val editState = state.editingExpense
        if (editState != null) {
            EditExpenseDialog(
                editState = editState,
                onAmountChange = viewModel::onEditAmountChange,
                onDateChange = viewModel::onEditDateChange,
                onDescriptionChange = viewModel::onEditDescriptionChange,
                onSelectCategoryClick = { showEditCategorySheet = true },
                onConfirm = viewModel::confirmEdit,
                onDismiss = viewModel::cancelEditing
            )
        }

        if (showEditCategorySheet) {
            CategorySelectionSheet(
                categories = state.categories,
                newCategoryName = state.newCategoryNameInput,
                selectedColorHex = state.newCategoryColorInput,
                categoryError = state.categoryErrorMessage,
                newSubcategoryName = state.newSubcategoryNameInput,
                subcategoryError = state.subcategoryErrorMessage,
                onCategoryNameChange = viewModel::onNewCategoryNameChange,
                onColorSelect = viewModel::onNewCategoryColorChange,
                onCreateCategory = viewModel::createCategory,
                onSubcategoryNameChange = viewModel::onNewSubcategoryNameChange,
                onCreateSubcategory = viewModel::createSubcategory,
                onDismiss = {
                    showEditCategorySheet = false
                    viewModel.clearCategoryError()
                    viewModel.clearSubcategoryError()
                },
                onCategorySelected = { category, subcategory ->
                    viewModel.onEditCategoryChange(category, subcategory)
                    showEditCategorySheet = false
                }
            )
        }

        val expenseToDelete = state.expenseToDelete
        if (expenseToDelete != null) {
            DeleteExpenseConfirmationDialog(
                expense = expenseToDelete,
                onConfirm = viewModel::confirmDeleteExpense,
                onDismiss = viewModel::dismissDeleteConfirmation
            )
        }
    }
}

@Composable
fun AddExpenseCard(
    state: ExpenseUiState,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Registrar Gasto",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Monto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Monto",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$ ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent
                    )
                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = onAmountChange,
                        placeholder = {
                            Text(
                                "0.00",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            }

            // Fecha
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = "Fecha", fontSize = 11.sp, color = TextSecondary)
                OutlinedTextField(
                    value = state.dateInput,
                    onValueChange = onDateChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            // Selector Categoría
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .clickable { onSelectCategoryClick() }
                    .padding(14.dp)
            ) {
                Text(text = "Categoría", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                if (state.categoryInput.isNotBlank()) {
                    Text(
                        text = state.categoryInput,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (state.subcategoryInput.isNotBlank()) {
                        Text(
                            text = state.subcategoryInput,
                            fontSize = 13.sp,
                            color = GreenAccent
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seleccionar categoría",
                            fontSize = 15.sp,
                            color = GreenAccent
                        )
                        Text(text = ">", fontSize = 16.sp, color = TextSecondary)
                    }
                }
            }

            // Nota opcional
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = "Nota (opcional)", fontSize = 11.sp, color = TextSecondary)
                OutlinedTextField(
                    value = state.descriptionInput,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Agregar una descripción...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            // Mensaje de validación
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Botón Guardar
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = Color(0xFF0C1017)
                )
            ) {
                Text(
                    text = "✓ Guardar Gasto",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ExpenseCardItem(
    expense: Expense,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "$", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.subcategory.ifBlank { expense.category.ifBlank { "Gasto" } },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (expense.description.isNotBlank()) expense.description else expense.category,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    text = String.format(Locale.US, "$%.2f", expense.amount),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = TextSecondary
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
                            text = { Text("Modificar", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = ErrorRed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = ErrorRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expense.date,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GreenAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Guardado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditExpenseDialog(
    editState: EditExpenseState,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modificar Gasto",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                // Monto
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Monto",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$ ",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenAccent
                        )
                        OutlinedTextField(
                            value = editState.amount,
                            onValueChange = onAmountChange,
                            placeholder = {
                                Text(
                                    "0.00",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }

                // Fecha
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(text = "Fecha", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = editState.date,
                        onValueChange = onDateChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                // Selector Categoría
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelectCategoryClick() }
                        .padding(14.dp)
                ) {
                    Text(text = "Categoría", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (editState.category.isNotBlank()) {
                        Text(
                            text = editState.category,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (editState.subcategory.isNotBlank()) {
                            Text(
                                text = editState.subcategory,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Text(
                            text = "Seleccionar categoría",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Descripción
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(text = "Descripción (opcional)", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = editState.description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                // Error de validación
                if (!editState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = editState.errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Botones Cancelar y Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text(text = "Cancelar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenAccent,
                            contentColor = Color(0xFF0C1017)
                        )
                    ) {
                        Text(
                            text = "Guardar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlideToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 48.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val thumbPaddingDp = 4.dp
    val thumbPaddingPx = with(density) { thumbPaddingDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - (thumbPaddingPx * 2)).coerceAtLeast(0f)

    val dragOffset = remember { Animatable(0f) }
    var confirmed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(28.dp))
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val progress = if (maxDragPx > 0f) (dragOffset.value / maxDragPx).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ErrorRed.copy(alpha = 0.15f * progress))
        )

        Text(
            text = if (progress > 0.8f) "Soltar para eliminar" else "Desliza para eliminar >>",
            color = if (progress > 0.8f) ErrorRed else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .padding(start = thumbPaddingDp)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(ErrorRed)
                .pointerInput(maxDragPx, confirmed) {
                    if (confirmed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (maxDragPx > 0f && dragOffset.value >= maxDragPx * 0.8f) {
                                confirmed = true
                                coroutineScope.launch {
                                    dragOffset.animateTo(maxDragPx)
                                    onConfirm()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f,
                                            stiffness = 400f
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.6f,
                                        stiffness = 400f
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!confirmed && maxDragPx > 0f) {
                                coroutineScope.launch {
                                    dragOffset.snapTo(
                                        (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                    )
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Deslizar para eliminar",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun DeleteExpenseConfirmationDialog(
    expense: Expense,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "¿Eliminar este gasto?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = expense.subcategory.ifBlank { expense.category.ifBlank { "Gasto" } },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (expense.description.isNotBlank()) {
                        Text(
                            text = expense.description,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "$%.2f", expense.amount),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }

                Text(
                    text = "Esta acción no se puede deshacer. Desliza el botón para confirmar.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                SlideToConfirmButton(onConfirm = onConfirm)

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(text = "Cancelar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
