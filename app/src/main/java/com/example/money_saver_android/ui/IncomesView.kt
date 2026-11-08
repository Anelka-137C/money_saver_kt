package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.defaultCurrencies
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.model.Income
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.incomes.AddIncomeCard
import com.example.money_saver_android.ui.components.incomes.IncomeAllocationCard
import com.example.money_saver_android.ui.components.incomes.IncomeOverviewCard
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.IncomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IncomesView(
    incomeViewModel: IncomeViewModel,
    allPeriods: List<FinancialPeriod> = emptyList(),
    selectedPeriodId: Long? = null,
    onPeriodSelected: (Long) -> Unit = {},
    i18nManager: I18nManager? = null,
    currency: Currency = defaultCurrencies.first()
) {
    val state by incomeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var periodMenuExpanded by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            incomeViewModel.clearToastMessage()
            showAddForm = false
        }
    }

    LaunchedEffect(selectedPeriodId) {
        if (selectedPeriodId != null && state.selectedPeriodId != selectedPeriodId) {
            incomeViewModel.onPeriodSelected(selectedPeriodId)
        }
    }

    val activePeriod = allPeriods.find { it.id == selectedPeriodId } ?: allPeriods.firstOrNull()
    val currentLang = LocalI18nManager.current.currentLanguage
    val periodLabel = remember(activePeriod, currentLang) {
        activePeriod?.let {
            formatPeriodLabel(it.startDate, currentLang)
        } ?: "May 2026"
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Period Selector Header
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .clickable { periodMenuExpanded = true }
                        .testTag("selector_period")
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = periodLabel,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = t("incomes.header.select_period"),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false },
                    modifier = Modifier.background(CardBackground)
                ) {
                    allPeriods.forEach { period ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${formatPeriodLabel(period.startDate)} (${period.startDate} - ${period.endDate})",
                                    color = if (period.id == selectedPeriodId) GreenAccent else TextPrimary
                                )
                            },
                            onClick = {
                                onPeriodSelected(period.id)
                                periodMenuExpanded = false
                            },
                            modifier = Modifier.testTag("period_option_${period.id}")
                        )
                    }
                }
            }

            // 2. Income Overview Card
            IncomeOverviewCard(
                totalIncome = state.totalIncome,
                currency = currency
            )

            // 3. Income Allocation Card
            IncomeAllocationCard(
                totalIncome = state.totalIncome,
                currency = currency
            )

            // 4. Add Income Action / Form
            if (!showAddForm) {
                Button(
                    onClick = { showAddForm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_toggle_add_income"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("incomes.header.add_button"),
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                AddIncomeCard(
                    state = state,
                    currency = currency,
                    onAmountChange = incomeViewModel::onAmountChange,
                    onDateTimeChange = incomeViewModel::onDateTimeChange,
                    onDescriptionChange = incomeViewModel::onDescriptionChange,
                    onSelectCategoryClick = { showCategorySheet = true },
                    onSaveClick = {
                        hideKeyboard(context, view, keyboardController, focusManager)
                        incomeViewModel.saveIncome()
                    },
                    onCloseClick = { showAddForm = false }
                )
            }

            // 5. Income Breakdown Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("incomes.breakdown.title"),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Incomes List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("list_income_breakdown"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.incomes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("incomes.history.empty_message"),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    state.incomes.forEach { income ->
                        IncomeListItem(
                            income = income,
                            currency = currency
                        )
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        CategorySelectionSheet(
            categories = state.categories,
            newCategoryName = "",
            selectedColorHex = "#4E73DF",
            categoryError = null,
            newSubcategoryName = "",
            subcategoryError = null,
            onCategoryNameChange = {},
            onColorSelect = {},
            onCreateCategory = {},
            onSubcategoryNameChange = {},
            onCreateSubcategory = {},
            onDeleteCategory = { incomeViewModel.deleteCategory(it) },
            onDismiss = { showCategorySheet = false },
            onCategorySelected = { cat, sub ->
                incomeViewModel.onCategorySelected(cat, sub)
                showCategorySheet = false
            }
        )
    }
}

@Composable
fun IncomeListItem(
    income: Income,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("income_item_${income.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF143026)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$",
                        color = GreenAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = income.subcategory.ifBlank { income.category },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${income.category} • ${income.dateTime}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyFormatter.format(income.amount, currency),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(">", color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

private fun formatPeriodLabel(isoDate: String, lang: String = "es"): String {
    return try {
        val locale = Locale.forLanguageTag(lang)
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(isoDate) ?: Date()
        SimpleDateFormat("MMMM yyyy", locale).format(parsed)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    } catch (_: Exception) {
        isoDate
    }
}
