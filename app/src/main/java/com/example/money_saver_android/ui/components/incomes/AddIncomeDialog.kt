package com.example.money_saver_android.ui.components.incomes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.viewmodel.IncomeUiState

class CurrencyFormatVisualTransformation(
    private val currency: Currency
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val formatted = CurrencyFormatter.formatValue(original, currency)
        val offsetMapping = createOffsetMapping(original, formatted)
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }

    private fun createOffsetMapping(original: String, formatted: String): OffsetMapping {
        val origToTrans = IntArray(original.length + 1)
        val transToOrig = IntArray(formatted.length + 1)

        var o = 0
        var t = 0
        while (o < original.length && t < formatted.length) {
            origToTrans[o] = t
            transToOrig[t] = o

            val oChar = original[o]
            val tChar = formatted[t]

            if (oChar == tChar) {
                o++
                t++
            } else if (tChar == ',') {
                t++
            } else {
                o++
            }
        }

        while (o <= original.length) {
            origToTrans[o] = t.coerceAtMost(formatted.length)
            o++
        }
        while (t <= formatted.length) {
            transToOrig[t] = o.coerceAtMost(original.length)
            t++
        }

        return object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, origToTrans.size - 1)
                return origToTrans[clamped].coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transToOrig.size - 1)
                return transToOrig[clamped].coerceIn(0, original.length)
            }
        }
    }
}

@Composable
fun AddIncomeDialog(
    state: IncomeUiState,
    currency: Currency = defaultCurrencies.first(),
    onAmountChange: (String) -> Unit,
    onDateTimeChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AddIncomeCard(
            state = state,
            currency = currency,
            onAmountChange = onAmountChange,
            onDateTimeChange = onDateTimeChange,
            onDescriptionChange = onDescriptionChange,
            onSelectCategoryClick = onSelectCategoryClick,
            onSaveClick = onSaveClick,
            onCloseClick = onCloseClick,
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        )
    }
}

@Composable
fun AddIncomeCard(
    state: IncomeUiState,
    currency: Currency = defaultCurrencies.first(),
    onAmountChange: (String) -> Unit,
    onDateTimeChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_add_income"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("incomes.add_card.title"),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(28.dp).testTag("btn_close_add_income")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Amount field centered as in design
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = t("incomes.add_card.amount_label"),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currency.symbol,
                        color = GreenAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = { newValue ->
                            val allowsDecimals = currency.formatPattern.contains(".")
                            val maxDecimals = if (allowsDecimals) currency.formatPattern.substringAfter(".").length else 0
                            val filtered = buildString {
                                var hasDot = false
                                var decCount = 0
                                for (char in newValue.replace(",", "")) {
                                    if (char.isDigit()) {
                                        if (hasDot) {
                                            if (decCount < maxDecimals) {
                                                append(char)
                                                decCount++
                                            }
                                        } else {
                                            append(char)
                                        }
                                    } else if ((char == '.' || char == ',') && allowsDecimals && !hasDot) {
                                        append('.')
                                        hasDot = true
                                    }
                                }
                            }
                            onAmountChange(filtered)
                        },
                        visualTransformation = CurrencyFormatVisualTransformation(currency),
                        modifier = Modifier
                            .width(220.dp)
                            .testTag("input_income_amount"),
                        placeholder = {
                            val placeholderText = if (currency.formatPattern.contains(".")) {
                                t("incomes.add_card.amount_placeholder")
                            } else {
                                "0"
                            }
                            Text(
                                placeholderText,
                                color = TextSecondary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            // Date / Time field
            DateTimePickerField(
                value = state.dateTimeInput,
                onValueChange = onDateTimeChange,
                label = t("incomes.add_card.date_time_label")
            )

            // Category selector field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .clickable { onSelectCategoryClick() }
                    .testTag("btn_select_income_category")
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = t("incomes.add_card.category_label"),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val categoryText = if (state.categoryInput.isNotBlank()) {
                                if (state.subcategoryInput.isNotBlank()) {
                                    "${state.categoryInput} / ${state.subcategoryInput}"
                                } else {
                                    state.categoryInput
                                }
                            } else {
                                t("incomes.add_card.select_category")
                            }
                            Text(
                                text = categoryText,
                                color = if (state.categoryInput.isNotBlank()) TextPrimary else GreenAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(">", color = TextSecondary, fontSize = 16.sp)
                }
            }

            // Note (optional)
            OutlinedTextField(
                value = state.descriptionInput,
                onValueChange = onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_income_description"),
                label = { Text(t("incomes.add_card.description_label"), color = TextSecondary) },
                placeholder = { Text(t("incomes.add_card.description_placeholder"), color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = FieldBackground,
                    unfocusedContainerColor = FieldBackground,
                    focusedBorderColor = GreenAccent,
                    unfocusedBorderColor = BorderColor
                )
            )

            // Error message if any
            state.errorMessage?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("text_income_error")
                )
            }

            // Save button
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_save_income"),
                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = t("incomes.add_card.save_button"),
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
