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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.money_saver_android.ui.components.atom.DeleteRecordConfirmationDialog
import com.example.money_saver_android.ui.components.loans.AddLoanDialog
import com.example.money_saver_android.ui.components.loans.DeleteLoanConfirmationDialog
import com.example.money_saver_android.ui.components.loans.LoanCardItem
import com.example.money_saver_android.ui.components.loans.LoanPaymentHistorySheet
import com.example.money_saver_android.ui.components.loans.LoanScheduleDialog
import com.example.money_saver_android.ui.components.loans.LoansSummaryCard
import com.example.money_saver_android.ui.components.loans.RecordLoanPaymentDialog
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.ErrorRed
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

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { messageKey ->
            Toast.makeText(context, activeI18n.t(messageKey), Toast.LENGTH_SHORT).show()
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
                        loansViewModel.openCreateLoanDialog()
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

        val mainScreenError = state.errorMessage?.takeIf {
            !state.showLoanFormDialog && state.historyLoanId == null
        }
        if (!mainScreenError.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErrorRed.copy(alpha = 0.14f))
                    .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("banner_loans_error"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mainScreenError,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = loansViewModel::clearErrorMessage,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("btn_dismiss_loans_error")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

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
                        onPaymentHistoryClick = { loansViewModel.openPaymentHistory(loan) },
                        onRegisterPaymentClick = { loansViewModel.openPaymentDialog(loan) },
                        onEditClick = {
                            loansViewModel.startEditingLoan(loan)
                        },
                        onDeleteClick = { loansViewModel.requestDeleteLoan(loan) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                loansViewModel.openCreateLoanDialog()
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

    if (state.showLoanFormDialog) {
        val isEditing = state.editingLoanId != null
        AddLoanDialog(
            personName = state.personNameInput,
            amountLent = state.amountLentInput,
            amountToReceive = state.amountToReceiveInput,
            note = state.noteInput,
            selectedSavingCategory = state.selectedSavingCategory,
            savingsBalances = if (isEditing) state.editSavingsBalances else state.savingsBalances,
            creationPaymentMode = state.creationPaymentMode,
            singleDueDateInput = state.creationSingleDueDateInput,
            todayIso = state.todayIso,
            errorMessage = state.errorMessage,
            showDiscardConfirmation = isEditing && state.showDiscardEditLoanDialog,
            currency = currency,
            onPersonNameChange = loansViewModel::onPersonNameChange,
            onAmountLentChange = loansViewModel::onAmountLentChange,
            onAmountToReceiveChange = loansViewModel::onAmountToReceiveChange,
            onNoteChange = loansViewModel::onNoteChange,
            onSavingCategorySelected = loansViewModel::onSavingCategorySelected,
            onCreationPaymentModeSelected = loansViewModel::onCreationPaymentModeSelected,
            onSingleDueDateChange = loansViewModel::onCreationSingleDueDateChange,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.saveLoan()
            },
            onCloseClick = {
                if (isEditing) {
                    loansViewModel.requestCloseEditLoanDialog()
                } else {
                    loansViewModel.resetForm()
                }
            },
            onConfirmDiscardChanges = {
                loansViewModel.resetForm()
            },
            onDismissDiscardChanges = loansViewModel::dismissDiscardEditLoanDialog,
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
            editingDraftId = state.editingScheduleInstallmentDraftId,
            dateInput = state.scheduleDateInput,
            amountInput = state.scheduleAmountInput,
            todayIso = state.todayIso,
            errorMessage = state.scheduleErrorMessage,
            showDiscardConfirmation = state.showDiscardScheduleChangesDialog,
            currency = currency,
            onDateChange = loansViewModel::onScheduleDateChange,
            onAmountChange = loansViewModel::onScheduleAmountChange,
            onAddInstallment = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.addScheduleInstallment()
            },
            onEditInstallment = loansViewModel::startEditingScheduleInstallment,
            onCancelEditInstallment = loansViewModel::cancelEditingScheduleInstallment,
            onRemoveInstallment = loansViewModel::removeScheduleInstallment,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.saveLoanSchedule()
            },
            onCloseClick = loansViewModel::requestCloseScheduleDialog,
            onConfirmDiscardChanges = loansViewModel::closeScheduleDialog,
            onDismissDiscardChanges = loansViewModel::dismissDiscardScheduleChangesDialog
        )
    }

    state.payingLoan?.let { payingLoan ->
        RecordLoanPaymentDialog(
            loan = payingLoan,
            amountInput = state.paymentAmountInput,
            dateTimeInput = state.paymentDateTimeInput,
            selectedSavingCategory = state.paymentSavingCategoryInput,
            savingCategories = state.savingCategories,
            savingsBalances = state.savingsBalances,
            selectedTargetInstallmentId = state.paymentTargetInstallmentId,
            defineNewDateOnNoSchedule = state.paymentDefineNewDateOnNoSchedule,
            newDueDateInput = state.paymentNewDueDateInput,
            partialScheduleAction = state.partialScheduleAction,
            extraInstallmentDateInput = state.paymentExtraInstallmentDateInput,
            extraInstallmentAmountInput = state.paymentExtraInstallmentAmountInput,
            adjustedNextAmountInput = state.paymentAdjustedNextAmountInput,
            adjustedRemainderDateInput = state.paymentAdjustedRemainderDateInput,
            todayIso = state.todayIso,
            errorMessage = state.paymentErrorMessage,
            currency = currency,
            onAmountChange = loansViewModel::onPaymentAmountChange,
            onFullAmountClick = loansViewModel::onPaymentFullAmountClick,
            onDateTimeChange = loansViewModel::onPaymentDateTimeChange,
            onSavingCategorySelected = loansViewModel::onPaymentSavingCategorySelected,
            onTargetInstallmentSelected = loansViewModel::onPaymentTargetInstallmentSelected,
            onDefineNewDateToggle = loansViewModel::onPaymentDefineNewDateToggle,
            onNewDueDateChange = loansViewModel::onPaymentNewDueDateChange,
            onPartialScheduleActionSelected = loansViewModel::onPartialScheduleActionSelected,
            onExtraInstallmentDateChange = loansViewModel::onPaymentExtraInstallmentDateChange,
            onExtraInstallmentAmountChange = loansViewModel::onPaymentExtraInstallmentAmountChange,
            onAdjustedNextAmountChange = loansViewModel::onPaymentAdjustedNextAmountChange,
            onAdjustedRemainderDateChange = loansViewModel::onPaymentAdjustedRemainderDateChange,
            onSaveClick = {
                hideKeyboard(context, view, keyboardController, focusManager)
                loansViewModel.saveLoanPayment()
            },
            onCloseClick = loansViewModel::closePaymentDialog,
            isEditing = state.editingPaymentId != null
        )
    }

    val historyLoan = state.historyLoanId?.let { id -> state.loans.find { it.id == id } }
    if (historyLoan != null) {
        LoanPaymentHistorySheet(
            loan = historyLoan,
            currency = currency,
            todayIso = state.todayIso,
            errorMessage = state.paymentErrorMessage,
            onEditPaymentClick = { payment ->
                loansViewModel.closePaymentHistory()
                loansViewModel.openEditPaymentDialog(historyLoan, payment)
            },
            onDeletePaymentClick = { payment ->
                loansViewModel.requestDeleteLoanPayment(historyLoan, payment)
            },
            onRegisterNewPaymentClick = {
                loansViewModel.closePaymentHistory()
                loansViewModel.openPaymentDialog(historyLoan)
            },
            onDismiss = loansViewModel::closePaymentHistory
        )
    }

    if (state.paymentToDelete != null) {
        DeleteRecordConfirmationDialog(
            onConfirm = loansViewModel::confirmDeleteLoanPayment,
            onDismiss = loansViewModel::dismissDeleteLoanPayment,
            dialogTestTag = "dialog_delete_loan_payment",
            confirmButtonTestTag = "btn_confirm_delete_loan_payment",
            cancelButtonTestTag = "btn_cancel_delete_loan_payment"
        )
    }
}
