package com.example.money_saver_android.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

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

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit,
    onCategorySelected: (category: String, subcategory: String) -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    val selectedCategory = categories.find { it.name == selectedCategoryName }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selectedCategoryColor = parseHexColor(selectedColorHex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        contentColor = TextPrimary
    ) {
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
                    IconButton(onClick = { selectedCategoryName = null }) {
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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedCategory == null) {
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
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onCreateCategory()
                            },
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

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FieldBackground)
                                .clickable { selectedCategoryName = category.name }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Text(text = ">", fontSize = 16.sp, color = TextSecondary)
                        }
                    }
                }
            } else {
                val parentCategoryColor = parseHexColor(selectedCategory.color)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBackground)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Nueva subcategoría para ${selectedCategory.name}",
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
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onCreateSubcategory(selectedCategory.name)
                            },
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

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedCategory.subcategories) { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FieldBackground)
                                .clickable {
                                    onCategorySelected(selectedCategory.name, sub.name)
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(text = "✓", fontSize = 16.sp, color = GreenAccent)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
