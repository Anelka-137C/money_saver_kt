package com.example.money_saver_android.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.ui.components.loans.AddLoanDialog
import com.example.money_saver_android.ui.components.loans.DeleteLoanConfirmationDialog
import com.example.money_saver_android.ui.components.loans.LoanCardItem
import com.example.money_saver_android.ui.components.loans.LoanScheduleDialog
import com.example.money_saver_android.ui.components.loans.LoansSummaryCard
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.LoansViewModel

@Composable
fun LoansView(
    loansViewModel: LoansViewModel,
    i18nManager: I18nManager? = null,
    currency: Currency = defaultCurrencies.first(),
    modifier: Modifier = Modifier
) {
    val state by loansViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val activeI18n = i18nManager ?: LocalI18nManager.current

    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { messageKey ->
            Toast.makeText(context, activeI18n.t(messageKey), Toast.LENGTH_SHORT).show()
            if (messageKey == LoansViewModel.MSG_LOAN_SAVED || messageKey == LoansViewModel.MSG_LOAN_UPDATED) {
                showAddDialog = false
            }
            loansViewModel.clearToastMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("loans_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("loans.header.title"),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GreenAccent)
                    .clickable {
                        loansViewModel.resetForm()
                        showAddDialog = true
                    }
                    .testTag("btn_add_loan_top"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = t("loans.actions.register_loan"),
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LoansSummaryCard(
            totalReceivable = state.totalReceivable,
            totalPaid = state.totalPaid,
            currency = currency
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("loans.section.pending_title"),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            val countLabel = if (state.loans.size == 1) {
                t("loans.section.count_singular")
            } else {
                t("loans.section.count_plural")
            }
            Text(
                text = "${state.loans.size} $countLabel",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.testTag("text_loans_count")
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag("list_loans_pending"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loans.isEmpty()) {
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
                        text = t("loans.empty.message"),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("text_loans_empty")
                    )
                }
            } else {
                state.loans.forEach { loan ->
                    LoanCardItem(
                        loan = loan,
                        currency = currency,
                        todayIso = state.todayIso,
                        onManageInstallmentsClick = { loansViewModel.openScheduleDialog(loan) },
                        onEditClick = {
                            loansViewModel.startEditingLoan(loan)
                            showAddDialog = true
                        },
                        onDeleteClick = { loansViewModel.requestDeleteLoan(loan) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                loansViewModel.resetForm()
                showAddDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_add_loan_bottom"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GreenAccent)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = GreenAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = t("loans.actions.register_loan"),
                color = GreenAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (showAddDialog) {
        val isEditing = state.editingLoanId != null
        AddLoanDialog(
            personName = state.personNameInput,
            amountLent = state.amountLentInput,
            amountToReceive = state.amountToReceiveInput,
            note = state.noteInput,
            selectedSavingCategory = state.selectedSavingCategory,
            savingsBalances = if (isEditing) state.editSavingsBalances else state.savingsBalances,
            errorMessage = state.errorMessage,
            currency = currency,
            onPersonNameChange = loansViewModel::onPersonNameChange,
            onAmountLentChange = loansViewModel::onAmountLentChange,
            onAmountToReceiveChange = loansViewModel::onAmountToReceiveChange,
            onNoteChange = loansViewModel::onNoteChange,
            onSavingCategorySelected = loansViewModel::onSavingCategorySelected,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.saveLoan()
            },
            onCloseClick = {
                loansViewModel.resetForm()
                showAddDialog = false
            },
            isEditing = isEditing
        )
    }

    state.loanToDelete?.let { loanToDelete ->
        DeleteLoanConfirmationDialog(
            loan = loanToDelete,
            onConfirm = loansViewModel::confirmDeleteLoan,
            onDismiss = loansViewModel::dismissDeleteLoan,
            currency = currency
        )
    }

    state.editingScheduleLoan?.let { editingLoan ->
        LoanScheduleDialog(
            loan = editingLoan,
            installments = state.scheduleInstallments,
            dateInput = state.scheduleDateInput,
            amountInput = state.scheduleAmountInput,
            todayIso = state.todayIso,
            errorMessage = state.scheduleErrorMessage,
            currency = currency,
            onDateChange = loansViewModel::onScheduleDateChange,
            onAmountChange = loansViewModel::onScheduleAmountChange,
            onAddInstallment = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.addScheduleInstallment()
            },
            onRemoveInstallment = loansViewModel::removeScheduleInstallment,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.saveLoanSchedule()
            },
            onCloseClick = loansViewModel::closeScheduleDialog
        )
    }
}
