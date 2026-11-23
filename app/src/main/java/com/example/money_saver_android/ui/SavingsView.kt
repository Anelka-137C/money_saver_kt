package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.atom.PeriodSummaryCard
import com.example.money_saver_android.ui.components.savings.AddSavingDialog
import com.example.money_saver_android.ui.components.savings.DeleteSavingCategoryConfirmationDialog
import com.example.money_saver_android.ui.components.savings.DeleteSavingConfirmationDialog
import com.example.money_saver_android.ui.components.savings.SavingListItem
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.SavingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavingsTabContent(
    viewModel: SavingsViewModel,
    currency: Currency = defaultCurrencies.first()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val i18nManager = LocalI18nManager.current

    var showAddSavingDialog by remember { mutableStateOf(false) }
    var showSavingCategorySheet by remember { mutableStateOf(false) }
    var isCategorySheetForEdit by remember { mutableStateOf(false) }

    LaunchedEffect(state.savingToastMessage) {
        state.savingToastMessage?.let { messageKey ->
            Toast.makeText(context, i18nManager.t(messageKey), Toast.LENGTH_SHORT).show()
            if (messageKey == SavingsViewModel.MSG_SAVING_SAVED) {
                showAddSavingDialog = false
            }
            viewModel.clearToastMessage()
        }
    }

    LaunchedEffect(state.savingErrorMessage, state.editingSaving?.errorMessage) {
        if (state.savingErrorMessage != null || state.editingSaving?.errorMessage != null) {
            showSavingCategorySheet = false
        }
    }

    LaunchedEffect(showAddSavingDialog, state.savingDateTimeInput) {
        if (showAddSavingDialog && state.savingDateTimeInput.isBlank()) {
            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
            viewModel.onDateTimeChange(nowFormatted)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("container_savings_view"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PeriodSummaryCard(
            title = t("savings.overview.title"),
            totalSubtitle = t("savings.overview.total_saved"),
            centerLabel = t("savings.overview.center_label"),
            emptyMessage = t("savings.overview.empty_message"),
            summary = state.savingsSummary,
            currency = currency,
            cardTestTag = "card_savings_overview",
            totalTestTag = "text_total_saved"
        )

        Button(
            onClick = { showAddSavingDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_toggle_add_saving"),
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
                text = t("savings.header.add_button"),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = t("savings.breakdown.title"),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        val savingsHistoryScrollState = rememberScrollState()
        LaunchedEffect(state.selectedPeriodId) {
            savingsHistoryScrollState.scrollTo(0)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(savingsHistoryScrollState)
                .testTag("list_savings_breakdown"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.savings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("savings.history.empty_message"),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("text_savings_empty")
                    )
                }
            } else {
                state.savings.forEach { saving ->
                    SavingListItem(
                        saving = saving,
                        currency = currency,
                        onEditClick = { viewModel.startEditingSaving(saving) },
                        onDeleteClick = { viewModel.requestDeleteSaving(saving) }
                    )
                }
            }
        }
    }

    if (showAddSavingDialog && !showSavingCategorySheet) {
        AddSavingDialog(
            amount = state.savingAmountInput,
            incomes = state.incomes,
            selectedIncomeId = state.savingIncomeIdInput,
            selectedIncomeCategory = state.savingIncomeCategoryInput,
            selectedIncomeSubcategory = state.savingIncomeSubcategoryInput,
            category = state.savingCategoryInput,
            subcategory = state.savingSubcategoryInput,
            dateTime = state.savingDateTimeInput,
            note = state.savingNoteInput,
            errorMessage = state.savingErrorMessage,
            isEditing = false,
            currency = currency,
            onAmountChange = viewModel::onAmountChange,
            onIncomeSelected = viewModel::onIncomeSelected,
            onSelectCategoryClick = {
                isCategorySheetForEdit = false
                showSavingCategorySheet = true
            },
            onDateTimeChange = viewModel::onDateTimeChange,
            onNoteChange = viewModel::onNoteChange,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                viewModel.saveSaving()
            },
            onCloseClick = {
                viewModel.clearErrorMessage()
                showAddSavingDialog = false
            }
        )
    }

    if (!showSavingCategorySheet) {
        state.editingSaving?.let { editState ->
            AddSavingDialog(
                amount = editState.amount,
                incomes = state.incomes,
                selectedIncomeId = editState.incomeId,
                selectedIncomeCategory = editState.incomeCategory,
                selectedIncomeSubcategory = editState.incomeSubcategory,
                category = editState.category,
                subcategory = editState.subcategory,
                dateTime = editState.dateTime,
                note = editState.note,
                errorMessage = editState.errorMessage,
                isEditing = true,
                currency = currency,
                onAmountChange = viewModel::onEditAmountChange,
                onIncomeSelected = viewModel::onEditIncomeSelected,
                onSelectCategoryClick = {
                    isCategorySheetForEdit = true
                    showSavingCategorySheet = true
                },
                onDateTimeChange = viewModel::onEditDateTimeChange,
                onNoteChange = viewModel::onEditNoteChange,
                onSaveClick = {
                    hideKeyboard(context, view, keyboardController, focusManager)
                    viewModel.confirmEditSaving()
                },
                onCloseClick = {
                    viewModel.cancelEditingSaving()
                }
            )
        }
    }

    if (showSavingCategorySheet) {
        CategorySelectionSheet(
            categories = state.savingCategories,
            newCategoryName = state.newCategoryNameInput,
            selectedColorHex = state.selectedColorHex,
            categoryError = state.categoryError,
            newSubcategoryName = state.newSubcategoryNameInput,
            subcategoryError = state.subcategoryError,
            onCategoryNameChange = viewModel::onNewCategoryNameChange,
            onColorSelect = viewModel::onColorSelect,
            onCreateCategory = { viewModel.createSavingCategory() },
            onSubcategoryNameChange = viewModel::onNewSubcategoryNameChange,
            onCreateSubcategory = { parentName -> viewModel.createSavingSubcategory(parentName) },
            onDeleteCategory = { viewModel.requestDeleteSavingCategory(it) },
            onDeleteSubcategory = { cat, sub -> viewModel.deleteSavingSubcategory(cat, sub) },
            onClearSubcategoryError = viewModel::clearSubcategoryError,
            onDismiss = {
                viewModel.clearCategoryError()
                viewModel.clearSubcategoryError()
                showSavingCategorySheet = false
            },
            onCategorySelected = { cat, sub ->
                if (isCategorySheetForEdit) {
                    viewModel.onEditCategorySelected(cat, sub)
                } else {
                    viewModel.onCategorySelected(cat, sub)
                }
                viewModel.clearCategoryError()
                viewModel.clearSubcategoryError()
                showSavingCategorySheet = false
            }
        )
    }

    state.savingToDelete?.let { savingToDelete ->
        DeleteSavingConfirmationDialog(
            saving = savingToDelete,
            currency = currency,
            onConfirm = { viewModel.confirmDeleteSaving() },
            onDismiss = { viewModel.dismissDeleteSavingConfirmation() }
        )
    }

    state.savingCategoryToDelete?.let { categoryToDelete ->
        DeleteSavingCategoryConfirmationDialog(
            category = categoryToDelete,
            onConfirm = { viewModel.confirmDeleteSavingCategory() },
            onDismiss = { viewModel.dismissDeleteSavingCategoryConfirmation() }
        )
    }
}
