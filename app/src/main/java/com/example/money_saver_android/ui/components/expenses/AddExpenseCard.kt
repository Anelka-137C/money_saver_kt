package com.example.money_saver_android.ui.components.expenses

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.ExpenseUiState

@Composable
fun AddExpenseCard(
    state: ExpenseUiState,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = t("expenses.add_card.title"),
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
                    text = t("expenses.add_card.amount_label"),
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
                                t("expenses.add_card.amount_placeholder"),
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
                Text(text = t("expenses.add_card.date_label"), fontSize = 11.sp, color = TextSecondary)
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
                Text(text = t("expenses.add_card.category_label"), fontSize = 11.sp, color = TextSecondary)
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
                            text = t("expenses.add_card.category_placeholder"),
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
                Text(text = t("expenses.add_card.description_label"), fontSize = 11.sp, color = TextSecondary)
                OutlinedTextField(
                    value = state.descriptionInput,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text(t("expenses.add_card.description_placeholder"), color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSaveClick() }),
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
                    text = "✓ " + t("expenses.add_card.save_button"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
