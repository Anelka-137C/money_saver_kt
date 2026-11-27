package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.FakeLoanRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Loan
import com.example.money_saver_android.domain.model.LoanInstallment
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.viewmodel.LoansViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LoansViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class StubSavingsRepository(initialSavings: List<Saving> = emptyList()) : SavingsRepository {
        val savingsFlow = MutableStateFlow(initialSavings)
        val categoriesFlow = MutableStateFlow(listOf(Category("Emergencias", "🚨", "#3B82F6", emptyList())))
        override fun getAllSavings(): Flow<List<Saving>> = savingsFlow
        override fun getSavingsByPeriod(periodId: Long): Flow<List<Saving>> = savingsFlow
        override suspend fun getSavingById(id: Long): Saving? = null
        override suspend fun insertSaving(saving: Saving): Long = 0L
        override suspend fun updateSaving(saving: Saving) {}
        override suspend fun deleteSaving(id: Long) {}
        override suspend fun countSavingsForCategory(categoryName: String): Int = 0
        override suspend fun countSavingsForSubcategory(categoryName: String, subcategoryName: String): Int = 0
        override fun getAllCategories(): Flow<List<Category>> = categoriesFlow
        override suspend fun insertCategory(category: Category) {}
        override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {}
        override suspend fun deleteCategory(categoryName: String) {}
        override suspend fun deleteSubcategory(categoryName: String, subcategoryName: String) {}
        override suspend fun preloadDefaultCategoriesIfNeeded() {}
    }

    private class StubExpenseRepository : ExpenseRepository {
        override fun getAllExpenses(): Flow<List<Expense>> = MutableStateFlow(emptyList())
        override suspend fun insertExpense(expense: Expense) {}
        override suspend fun updateExpense(expense: Expense) {}
        override suspend fun deleteExpense(id: Long) {}
    }

    private lateinit var i18nManager: I18nManager
    private lateinit var loanRepo: FakeLoanRepository
    private lateinit var savingsRepo: StubSavingsRepository
    private lateinit var loansViewModel: LoansViewModel
    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$", formatPattern = "#,##0.00")

    @Before
    fun setUp() {
        i18nManager = I18nManager().apply {
            initFromContext(RuntimeEnvironment.getApplication())
            setLanguage("es")
        }
        loanRepo = FakeLoanRepository()
        savingsRepo = StubSavingsRepository(
            listOf(Saving(id = 1L, amount = 500.0, category = "Emergencias", periodId = 1L))
        )
        loansViewModel = LoansViewModel(
            LoanUseCase(
                loanRepository = loanRepo,
                savingsRepository = savingsRepo,
                expenseRepository = StubExpenseRepository(),
                currentDateProvider = { "2026-11-26" }
            )
        )
    }

    @Test
    fun loansView_emptyStateAndPersonalLoanRegistration_rendersCardAndUpdatesSummary() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }

        composeTestRule.onNodeWithTag("text_loans_empty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_loans_count").assertTextContains("0 préstamos")

        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("María Castillo")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("1500")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MC").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("María Castillo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Préstamo personal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_loans_total_receivable").assertTextContains("$ 1,500.00")
        composeTestRule.onNodeWithTag("text_loans_count").assertTextContains("1 préstamo")
    }

    @Test
    fun loansView_savingsLoanRegistration_showsCategorySubtitleAndDeductsAvailableBalance() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }

        composeTestRule.onNodeWithTag("btn_add_loan_top").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Juan Pérez")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("200")
        composeTestRule.onNodeWithTag("selector_loan_saving_category").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("loan_saving_option_Emergencias").performClick()
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("JP").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Juan Pérez").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emergencias").assertIsDisplayed()

        // Reopen dialog and verify remaining available balance for Emergencias is $ 300.00
        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("selector_loan_saving_category").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Emergencias • $ 300.00").assertIsDisplayed()
    }

    @Test
    fun loansView_amountToReceiveLowerThanLent_showsErrorAndDoesNotSave() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }

        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Ana")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("300")
        composeTestRule.onNodeWithTag("input_loan_amount_to_receive").performTextClearance()
        composeTestRule.onNodeWithTag("input_loan_amount_to_receive").performTextInput("200")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("text_loan_dialog_error").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertIsDisplayed()
    }

    @Test
    fun loansView_createLoanWithoutInstallmentFieldsInAddDialog_thenScheduleMultipleInstallmentsViaMenu() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }

        // Register loan dialog must NOT show installment fields
        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_installment_date").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_loan_installment_amount").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_add_loan_installment").assertDoesNotExist()

        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("María Castillo")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("1000")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val savedId = loanRepo.loansFlow.value.single().id
        composeTestRule.onNodeWithTag("loan_due_badge_$savedId").assertDoesNotExist()

        // Manage installments separately via 3-dot menu
        composeTestRule.onNodeWithTag("btn_options_loan_$savedId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$savedId").performClick()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()

        // Open date-only picker and verify time picker does not exist
        composeTestRule.onNodeWithTag("input_schedule_installment_date").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("date_only_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("time_picker_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_confirm_date_only_picker").performClick()

        // Add first installment ($400 on 2026-11-26)
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performScrollTo().performTextInput("400")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()

        // Change second installment date to 2026-12-15 and add $600
        loansViewModel.onScheduleDateChange("2026-12-15")
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performScrollTo().performTextInput("600")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("btn_save_schedule_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_due_badge_$savedId", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Vence: 26 Nov 2026 +1", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun loansView_existingLoanWithoutSchedule_addAndValidateAndClearUpdatesBadgeImmediately() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(personName = "Lucía Gómez", amountLent = 500.0, amountToReceive = 550.0)
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // No installments -> no badge
        composeTestRule.onNodeWithTag("loan_due_badge_$loanId").assertDoesNotExist()

        // Tapping the card itself must NOT open schedule dialog; must use 3-dot menu
        composeTestRule.onNodeWithTag("loan_card_$loanId").performClick()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertDoesNotExist()

        // Open 3-dot menu and choose "Agregar cuotas"
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()

        // Invalid amount (0) shows error and does not add
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performScrollTo().performTextInput("0")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_schedule_dialog_error").performScrollTo().assertIsDisplayed()

        // Add partial amount $200 and try saving -> mismatch error and persists nothing
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performTextInput("200")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_save_schedule_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("text_schedule_dialog_error").performScrollTo().assertIsDisplayed()
        assertTrue(loanRepo.loansFlow.value.single().installments.isEmpty())

        // Add remaining $350 and save
        loansViewModel.onScheduleDateChange("2026-12-05")
        composeTestRule.onNodeWithTag("input_schedule_installment_amount").performScrollTo().performTextInput("350")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_save_schedule_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("loan_due_badge_$loanId", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Vence: 26 Nov 2026 +1", useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(2, loanRepo.loansFlow.value.single().installments.size)

        // Reopen via 3-dot menu, remove all installments via each row's 3-dot menu, and save empty schedule -> badge disappears
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_options_schedule_installment_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_remove_schedule_installment_0").performClick()
        composeTestRule.onNodeWithTag("btn_options_schedule_installment_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_remove_schedule_installment_0").performClick()
        composeTestRule.onNodeWithTag("btn_save_schedule_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_due_badge_$loanId").assertDoesNotExist()
        assertTrue(loanRepo.loansFlow.value.single().installments.isEmpty())
    }

    @Test
    fun loansView_overdueInstallment_showsOverdueBadgeAndAllowsKeepingOrUpdatingSchedule() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(
                    personName = "Carlos Ruiz",
                    amountLent = 300.0,
                    amountToReceive = 300.0,
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-11-20", expectedAmount = 300.0)
                    )
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_due_badge_$loanId", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Vence: 20 Nov 2026", useUnmergedTree = true)
            .assertIsDisplayed()

        // Open schedule dialog via 3-dot menu, verify overdue label is visible, and saving keeps persisted overdue installment
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vencida").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_schedule_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("loan_due_badge_$loanId", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun loansView_threeDotMenu_editLoanAndDeleteLoanWithSlideConfirmationDialog() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(personName = "Roberto Díaz", amountLent = 400.0, amountToReceive = 450.0, note = "Auto")
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Open 3-dot menu -> verify all 3 options and click "Editar deuda"
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$loanId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_edit_loan_$loanId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_delete_loan_$loanId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_edit_loan_$loanId").performClick()

        // Popup opens in edit mode prefilled
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar préstamo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_loan_person").performTextClearance()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Roberto Silva")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Roberto Silva").assertIsDisplayed()

        // 2. Open 3-dot menu -> click "Eliminar deuda" -> verify slide-to-confirm dialog and cancel
        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_delete_loan_$loanId").performClick()

        composeTestRule.onNodeWithTag("dialog_delete_loan").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Eliminar este préstamo?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_loan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_loan").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_delete_loan").assertDoesNotExist()
        composeTestRule.onNodeWithText("Roberto Silva").assertIsDisplayed()
    }

    @Test
    fun loansView_registerPartialAndFullPayment_updatesCardProgressBadgeAndSummary() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(personName = "Sofía Vargas", amountLent = 500.0, amountToReceive = 500.0)
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // Open payment dialog -> loan without scheduled date does NOT show target installment selector
        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("section_payment_target_installment").assertDoesNotExist()

        // Saving without category on personal loan shows error
        composeTestRule.onNodeWithTag("input_payment_amount").performTextInput("200")
        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_payment_dialog_error").performScrollTo().assertIsDisplayed()

        // Choose savings category, optionally enable new due date, and save partial payment
        composeTestRule.onNodeWithTag("selector_payment_saving_category").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("payment_saving_option_Emergencias").performClick()
        composeTestRule.onNodeWithTag("toggle_payment_new_due_date").performScrollTo().performClick()
        loansViewModel.onPaymentNewDueDateChange("2026-12-20")
        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("loan_paid_amount_$loanId", useUnmergedTree = true).assertTextContains("$ 200.00")
        composeTestRule.onNodeWithTag("loan_pending_amount_$loanId", useUnmergedTree = true).assertTextContains("$ 300.00")
        composeTestRule.onNodeWithTag("text_loans_total_paid").assertTextContains("$ 200.00")
        composeTestRule.onNodeWithTag("text_loans_total_receivable").assertTextContains("$ 300.00")
        composeTestRule.onNodeWithText("Vence: 20 Dic 2026", useUnmergedTree = true).assertIsDisplayed()

        // Register full payment using quick button
        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_payment_full_amount").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_payment_will_settle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("selector_payment_saving_category").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("payment_saving_option_Emergencias").performClick()
        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("loan_settled_badge_$loanId", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("loan_due_badge_$loanId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("text_loans_total_paid").assertTextContains("$ 500.00")
        composeTestRule.onNodeWithTag("text_loans_total_receivable").assertTextContains("$ 0.00")
    }

    @Test
    fun loansView_savingsBackedLoanWithFutureInstallments_autoCreditsOriginAndShowsOptionalScheduleActions() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(
                    personName = "Andrés",
                    amountLent = 300.0,
                    amountToReceive = 350.0,
                    savingCategory = "Emergencias",
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-12-01", expectedAmount = 150.0),
                        LoanInstallment(dueDate = "2026-12-20", expectedAmount = 200.0)
                    )
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_register_payment_$loanId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertIsDisplayed()

        // Origin category is auto-credited (no manual selector) and target installment selector IS shown
        composeTestRule.onNodeWithTag("section_payment_target_installment").performScrollTo().assertIsDisplayed()
        val inst1Id = loanRepo.loansFlow.value.single().installments[0].id
        val inst2Id = loanRepo.loansFlow.value.single().installments[1].id
        composeTestRule.onNodeWithTag("payment_installment_option_$inst1Id").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_installment_option_$inst2Id").assertIsDisplayed()

        composeTestRule.onNodeWithTag("text_payment_auto_saving_category").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("selector_payment_saving_category").assertDoesNotExist()

        // Enter 150 (exact first installment) -> future installment Dec 20 remains -> optional schedule actions appear
        composeTestRule.onNodeWithTag("input_payment_amount").performScrollTo().performTextInput("150")
        composeTestRule.onNodeWithTag("option_schedule_keep").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_schedule_add").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_schedule_adjust").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // First installment has paymentAmount == 150 (paid), due badge advances to 20 Dic 2026, and Emergencias net balance becomes 500 - 300 + 150 = 350
        assertEquals(150.0, loanRepo.loansFlow.value.single().installments[0].paymentAmount, 0.001)
        composeTestRule.onNodeWithText("Vence: 20 Dic 2026", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(350.0, loansViewModel.uiState.value.savingsBalances.single().netBalance, 0.001)
    }

    @Test
    fun loansView_createLoanWithSingleDateAndWithAgreedInstallmentsModes() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }

        // 1. Single payment date mode
        composeTestRule.onNodeWithTag("btn_add_loan_bottom").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Elena Castro")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("800")
        composeTestRule.onNodeWithTag("option_loan_payment_single").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("input_loan_single_due_date").performScrollTo().assertIsDisplayed()
        loansViewModel.onCreationSingleDueDateChange("2026-12-22")
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Vence: 22 Dic 2026", useUnmergedTree = true).assertIsDisplayed()

        // 2. Agreed installments mode opens LoanScheduleDialog immediately on save
        composeTestRule.onNodeWithTag("btn_add_loan_top").performClick()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Tomás Vera")
        composeTestRule.onNodeWithTag("input_loan_amount_lent").performTextInput("600")
        composeTestRule.onNodeWithTag("option_loan_payment_installments").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("input_loan_single_due_date").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_save_loan_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()
    }

    @Test
    fun loansView_scheduleInstallmentThreeDotMenuEditAndCloseDiscardConfirmation() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(
                    personName = "Valeria",
                    amountLent = 500.0,
                    amountToReceive = 500.0,
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-12-01", expectedAmount = 200.0),
                        LoanInstallment(dueDate = "2026-12-20", expectedAmount = 300.0)
                    )
                )
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_installments_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()

        // Open 3-dot menu on first installment and choose edit
        composeTestRule.onNodeWithTag("btn_options_schedule_installment_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_edit_schedule_installment_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_remove_schedule_installment_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_edit_schedule_installment_0").performClick()

        // Change date to 2026-12-05 and update installment
        loansViewModel.onScheduleDateChange("2026-12-05")
        composeTestRule.onNodeWithTag("btn_add_schedule_installment").performScrollTo().performClick()
        composeTestRule.onNodeWithText("5 Dic 2026", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // Click X -> confirmation dialog "¿Deseas abandonar los cambios?" appears
        composeTestRule.onNodeWithTag("btn_close_schedule_dialog").performClick()
        composeTestRule.onNodeWithTag("dialog_discard_schedule_changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Deseas abandonar los cambios?").assertIsDisplayed()

        // Cancel discard with "Continuar" button -> stays in schedule dialog with edited installment still present
        composeTestRule.onNodeWithTag("btn_cancel_discard_schedule").assertTextContains("Continuar")
        composeTestRule.onNodeWithTag("btn_cancel_discard_schedule").performClick()
        composeTestRule.onNodeWithTag("dialog_discard_schedule_changes").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertIsDisplayed()

        // Click X again and confirm discard -> closes schedule dialog without persisting edit
        composeTestRule.onNodeWithTag("btn_close_schedule_dialog").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_discard_schedule").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_loan_schedule_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Vence: 1 Dic 2026 +1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loansView_editLoanCloseButton_showsDiscardConfirmationWithContinueAndAbandon() {
        val loanId = runBlocking {
            loanRepo.insertLoan(
                Loan(personName = "Gabriel", amountLent = 400.0, amountToReceive = 450.0)
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_options_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("btn_edit_loan_$loanId").performClick()
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertIsDisplayed()

        composeTestRule.onNodeWithTag("input_loan_person").performTextClearance()
        composeTestRule.onNodeWithTag("input_loan_person").performTextInput("Gabriel Editado")

        // Tap X -> shows discard confirmation with "Continuar"
        composeTestRule.onNodeWithTag("btn_close_loan_dialog").performClick()
        composeTestRule.onNodeWithTag("dialog_discard_edit_loan").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Seguro que quieres abandonar la edición?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_discard_edit_loan").assertTextContains("Continuar")

        // Tap "Continuar" -> stays in edit dialog
        composeTestRule.onNodeWithTag("btn_cancel_discard_edit_loan").performClick()
        composeTestRule.onNodeWithTag("dialog_discard_edit_loan").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertIsDisplayed()

        // Tap X again and confirm discard -> closes without saving changes
        composeTestRule.onNodeWithTag("btn_close_loan_dialog").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_discard_edit_loan").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_add_loan_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Gabriel").assertIsDisplayed()
    }

    @Test
    fun loansView_paymentHistorySheet_showsInstallmentBadgeOrAbonoADeudaAndOpensNewPayment() {
        val loanWithInstallmentsId = runBlocking {
            loanRepo.insertLoan(
                Loan(
                    personName = "María Castillo",
                    amountLent = 300.0,
                    amountToReceive = 1500.0,
                    savingCategory = "Emergencias",
                    installments = listOf(
                        LoanInstallment(dueDate = "2026-12-01", expectedAmount = 500.0),
                        LoanInstallment(dueDate = "2026-12-20", expectedAmount = 500.0),
                        LoanInstallment(dueDate = "2027-01-15", expectedAmount = 500.0)
                    )
                )
            )
        }
        val personalNoScheduleId = runBlocking {
            loanRepo.insertLoan(
                Loan(personName = "Luis Gómez", amountLent = 300.0, amountToReceive = 300.0)
            )
        }
        runBlocking {
            val inst1Id = loanRepo.loansFlow.value.single { it.id == loanWithInstallmentsId }.installments[0].id
            loanRepo.recordPayment(
                payment = com.example.money_saver_android.domain.model.LoanPayment(
                    loanId = loanWithInstallmentsId,
                    amount = 500.0,
                    paidAt = "2026-11-27 10:00",
                    savingCategory = "Emergencias",
                    installmentId = inst1Id
                ),
                updatedInstallments = loanRepo.loansFlow.value
                    .single { it.id == loanWithInstallmentsId }
                    .installments
                    .map { if (it.id == inst1Id) it.copy(paymentAmount = 500.0) else it }
            )
            loanRepo.recordPayment(
                payment = com.example.money_saver_android.domain.model.LoanPayment(
                    loanId = personalNoScheduleId,
                    amount = 100.0,
                    paidAt = "2026-11-27 11:00",
                    savingCategory = "Emergencias",
                    installmentId = null
                ),
                updatedInstallments = emptyList()
            )
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                LoansView(loansViewModel = loansViewModel, i18nManager = i18nManager, currency = usd)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Open payment history on loan with installments -> shows "33% pagado", "1 de 3 cuotas", "Cuota 1 de 3", "Abono completo"
        composeTestRule.onNodeWithTag("btn_options_loan_$loanWithInstallmentsId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_payment_history_loan_$loanWithInstallmentsId").performClick()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_history_paid_percent").assertTextContains("33% pagado")
        composeTestRule.onNodeWithTag("text_history_progress_detail").assertTextContains("1 de 3 cuotas")
        composeTestRule.onNodeWithText("Cuota 1 de 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abono completo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_history_register_payment").assertIsDisplayed()

        // Tapping "Registrar nuevo abono" closes history and opens payment dialog
        composeTestRule.onNodeWithTag("btn_history_register_payment").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertDoesNotExist()
        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_close_payment_dialog").performClick()

        // 2. Open payment history on loan without installments -> shows "Abono a deuda" instead of cuota
        composeTestRule.onNodeWithTag("btn_options_loan_$personalNoScheduleId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_payment_history_loan_$personalNoScheduleId").performClick()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abono a deuda").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abono parcial").assertIsDisplayed()

        // 3. Inside payment history, open 3-dot menu on payment -> Edit updates amount, Delete shows atomic confirmation dialog
        val paymentId = loanRepo.loansFlow.value.single { it.id == personalNoScheduleId }.payments.single().id
        composeTestRule.onNodeWithTag("btn_options_payment_history_$paymentId").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("btn_edit_payment_history_$paymentId").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_record_loan_payment_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar pago").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_payment_amount").performTextClearance()
        composeTestRule.onNodeWithTag("input_payment_amount").performTextInput("150")
        composeTestRule.onNodeWithTag("btn_save_payment_dialog").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(150.0, loanRepo.loansFlow.value.single { it.id == personalNoScheduleId }.paidAmount, 0.001)

        // Reopen payment history -> verify updated amount, then trigger delete confirmation popup
        composeTestRule.onNodeWithTag("btn_options_loan_$personalNoScheduleId").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_payment_history_loan_$personalNoScheduleId").performClick()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_options_payment_history_$paymentId").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("btn_delete_payment_history_$paymentId").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.onNodeWithTag("dialog_delete_loan_payment").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Seguro que quieres eliminar el registro?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_delete_loan_payment").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_delete_loan_payment").assertDoesNotExist()
        composeTestRule.onNodeWithTag("text_payment_history_empty").assertIsDisplayed()
        assertEquals(0.0, loanRepo.loansFlow.value.single { it.id == personalNoScheduleId }.paidAmount, 0.001)

        composeTestRule.onNodeWithTag("btn_close_payment_history").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_loan_payment_history").assertDoesNotExist()
    }
}
