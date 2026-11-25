package com.example.money_saver_android.ui.components.expenses

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Budget
import com.example.money_saver_android.domain.model.ExpenseFundingSource
import com.example.money_saver_android.domain.usecase.SavingsCategoryBalance
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.incomes.CurrencyFormatVisualTransformation
import com.example.money_saver_android.ui.components.incomes.DateTimePickerField
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Modal dialog for creating or editing an expense with separate selectors for
 * source budget item and expense classification category.
 */
@Composable
fun AddEditExpenseDialog(
    isEditing: Boolean,
    amount: String,
    date: String,
    description: String,
    budgetCategory: String,
    budgetSubcategory: String,
    category: String,
    subcategory: String,
    budgets: List<Budget>,
    errorMessage: String?,
    currency: Currency = defaultCurrencies.first(),
    fundingSource: ExpenseFundingSource = ExpenseFundingSource.BUDGET,
    savingCategory: String = "",
    savingsBalances: List<SavingsCategoryBalance> = emptyList(),
    onFundingSourceSelected: (ExpenseFundingSource) -> Unit = {},
    onSavingCategorySelected: (String) -> Unit = {},
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onBudgetSelected: (Budget) -> Unit,
    onSelectCategoryClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetMenuExpanded by remember { mutableStateOf(false) }
    var savingMenuExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("card_add_edit_expense"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) t("expenses.edit_dialog.title") else t("expenses.add_card.title"),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.size(28.dp).testTag("btn_close_expense_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Amount input
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = t("expenses.add_card.amount_label"), color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = currency.symbol,
                            color = GreenAccent,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() || it == '.' }
                                val parts = filtered.split(".")
                                if (parts.size <= 2 && (parts.size == 1 || parts[1].length <= 2)) {
                                    onAmountChange(filtered)
                                }
                            },
                            visualTransformation = CurrencyFormatVisualTransformation(currency),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = t("expenses.add_card.amount_placeholder"),
                                    color = TextSecondary.copy(alpha = 0.5f),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = transparentFieldColors(),
                            textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(200.dp).testTag("input_expense_amount")
                        )
                    }
                }

                // Funding Source selector (Budget vs Savings)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("expenses.add_card.funding_source_label"),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FieldBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FundingSourceChip(
                            label = t("expenses.add_card.source_budget"),
                            selected = fundingSource == ExpenseFundingSource.BUDGET,
                            accentColor = GreenAccent,
                            testTag = "source_option_budget",
                            onClick = { onFundingSourceSelected(ExpenseFundingSource.BUDGET) },
                            modifier = Modifier.weight(1f)
                        )
                        FundingSourceChip(
                            label = t("expenses.add_card.source_savings"),
                            selected = fundingSource == ExpenseFundingSource.SAVINGS,
                            accentColor = Color(0xFF3B82F6),
                            testTag = "source_option_savings",
                            onClick = { onFundingSourceSelected(ExpenseFundingSource.SAVINGS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Selector 1: Source Budget or Source Savings Category
                if (fundingSource == ExpenseFundingSource.BUDGET) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExpenseSelectorField(
                            label = t("expenses.add_card.budget_label"),
                            displayValue = formatCategoryLabel(budgetCategory, budgetSubcategory),
                            placeholder = t("expenses.add_card.budget_placeholder"),
                            testTag = "selector_expense_budget",
                            onClick = { budgetMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = budgetMenuExpanded,
                            onDismissRequest = { budgetMenuExpanded = false },
                            modifier = Modifier.background(CardBackground)
                        ) {
                            budgets.forEach { budget ->
                                val label = formatCategoryLabel(budget.category, budget.subcategory).orEmpty()
                                val amountFormatted = CurrencyFormatter.format(budget.amount, currency)
                                DropdownMenuItem(
                                    text = {
                                        Text("$label • $amountFormatted", color = TextPrimary, fontSize = 14.sp)
                                    },
                                    onClick = {
                                        onBudgetSelected(budget)
                                        budgetMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("budget_option_${budget.id}")
                                )
                            }
                        }
                    }
                } else {
                    val selectedBalance = savingsBalances.find {
                        it.categoryName.equals(savingCategory, ignoreCase = true)
                    }
                    val savingDisplayValue = if (savingCategory.isNotBlank()) {
                        selectedBalance?.let {
                            "${it.categoryName} • ${CurrencyFormatter.format(it.netBalance, currency)}"
                        } ?: savingCategory
                    } else null
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExpenseSelectorField(
                            label = t("expenses.add_card.saving_source_label"),
                            displayValue = savingDisplayValue,
                            placeholder = t("expenses.add_card.saving_source_placeholder"),
                            testTag = "selector_expense_saving_category",
                            onClick = { savingMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = savingMenuExpanded,
                            onDismissRequest = { savingMenuExpanded = false },
                            modifier = Modifier.background(CardBackground)
                        ) {
                            val availableBalances = savingsBalances.filter { it.netBalance > 0.0 }
                            if (availableBalances.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = t("expenses.add_card.no_savings_available"),
                                            color = TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                availableBalances.forEach { balance ->
                                    val formattedBalance = CurrencyFormatter.format(balance.netBalance, currency)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${balance.categoryName} • $formattedBalance",
                                                color = TextPrimary,
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            onSavingCategorySelected(balance.categoryName)
                                            savingMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("saving_source_option_${balance.categoryName}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Selector 2: Expense Category (+ optional Subcategory)
                ExpenseSelectorField(
                    label = t("expenses.add_card.category_label"),
                    displayValue = formatCategoryLabel(category, subcategory),
                    placeholder = t("expenses.add_card.category_placeholder"),
                    testTag = "selector_expense_category",
                    onClick = onSelectCategoryClick
                )

                // Date & Time picker (calendar + clock)
                DateTimePickerField(
                    value = date,
                    onValueChange = onDateChange,
                    label = if (isEditing) t("expenses.edit_dialog.date_label") else t("expenses.add_card.date_label")
                )

                // Optional Description
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("expenses.add_card.description_label"),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        placeholder = {
                            Text(t("expenses.add_card.description_placeholder"), color = TextSecondary)
                        },
                        colors = transparentFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FieldBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .testTag("input_expense_description")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("text_expense_dialog_error")
                    )
                }

                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_save_expense_dialog"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isEditing) t("expenses.edit_dialog.save_button") else t("expenses.add_card.save_button"),
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FundingSourceChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ExpenseSelectorField(
    label: String,
    displayValue: String?,
    placeholder: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FieldBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp)
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayValue ?: placeholder,
                    color = if (displayValue != null) TextPrimary else TextSecondary,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatCategoryLabel(category: String, subcategory: String): String? {
    if (category.isBlank()) return null
    return if (subcategory.isNotBlank()) "$category • $subcategory" else category
}

@Composable
private fun transparentFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
