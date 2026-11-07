package com.example.money_saver_android.ui.components.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.EditExpenseState

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
                        text = t("expenses.edit_dialog.title"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = t("expenses.edit_dialog.cancel_button"),
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
                        text = t("expenses.edit_dialog.amount_label"),
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
                                    t("expenses.edit_dialog.amount_placeholder"),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = TextStyle(
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
                    Text(text = t("expenses.edit_dialog.date_label"), fontSize = 11.sp, color = TextSecondary)
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
                    Text(text = t("expenses.edit_dialog.category_label"), fontSize = 11.sp, color = TextSecondary)
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
                            text = t("expenses.edit_dialog.category_placeholder"),
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
                    Text(text = t("expenses.edit_dialog.description_label"), fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = editState.description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
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
                        Text(text = t("expenses.edit_dialog.cancel_button"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            text = t("expenses.edit_dialog.save_button"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
