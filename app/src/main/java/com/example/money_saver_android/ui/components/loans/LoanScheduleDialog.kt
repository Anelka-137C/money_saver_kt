package com.example.money_saver_android.ui.components.loans

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.domain.CurrencyFormatter
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.i18n.t
import com.example.money_saver_android.viewmodel.LoanInstallmentDraft

@Composable
fun LoanScheduleDialog(
    loan: Loan,
    installments: List<LoanInstallmentDraft>,
    editingDraftId: String? = null,
    dateInput: String,
    amountInput: String,
    todayIso: String,
    errorMessage: String?,
    showDiscardConfirmation: Boolean = false,
    currency: Currency,
    onDateChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onAddInstallment: () -> Unit,
    onEditInstallment: (String) -> Unit = {},
    onCancelEditInstallment: () -> Unit = {},
    onRemoveInstallment: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit,
    onConfirmDiscardChanges: () -> Unit = onCloseClick,
    onDismissDiscardChanges: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LoanFormDialog(
        title = t("loans.schedule.dialog_title"),
        subtitle = "${loan.personName} • ${CurrencyFormatter.format(loan.pendingAmount, currency)}",
        errorMessage = errorMessage,
        confirmButtonText = t("loans.schedule.save_button"),
        dialogTestTag = "card_loan_schedule_dialog",
        closeButtonTestTag = "btn_close_schedule_dialog",
        errorTestTag = "text_schedule_dialog_error",
        confirmButtonTestTag = "btn_save_schedule_dialog",
        onConfirmClick = onSaveClick,
        onCloseClick = onCloseClick,
        modifier = modifier
    ) {
        LoanInstallmentsSection(
            installments = installments,
            dateInput = dateInput,
            amountInput = amountInput,
            todayIso = todayIso,
            targetAmountToReceive = loan.pendingAmount,
            currency = currency,
            onDateChange = onDateChange,
            onAmountChange = onAmountChange,
            onAddClick = onAddInstallment,
            onRemoveClick = onRemoveInstallment,
            editingDraftId = editingDraftId,
            onEditClick = onEditInstallment,
            onCancelEditClick = onCancelEditInstallment,
            dateFieldTestTag = "input_schedule_installment_date",
            amountFieldTestTag = "input_schedule_installment_amount",
            addButtonTestTag = "btn_add_schedule_installment",
            removeButtonTagPrefix = "btn_remove_schedule_installment",
            itemTagPrefix = "schedule_installment_item",
            showEmptyState = true
        )
    }

    if (showDiscardConfirmation) {
        DiscardChangesDialog(
            title = t("loans.schedule.discard_dialog_title"),
            message = t("loans.schedule.discard_dialog_message"),
            cancelText = t("loans.schedule.discard_cancel"),
            confirmText = t("loans.schedule.discard_confirm"),
            dialogTestTag = "dialog_discard_schedule_changes",
            cancelButtonTestTag = "btn_cancel_discard_schedule",
            confirmButtonTestTag = "btn_confirm_discard_schedule",
            onConfirmDiscard = onConfirmDiscardChanges,
            onDismiss = onDismissDiscardChanges
        )
    }
}
