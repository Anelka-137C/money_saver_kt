package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Currency
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.ExpenseRepository
import com.example.money_saver_android.data.repository.SavingsRepository
import com.example.money_saver_android.domain.FakeLoanRepository
import com.example.money_saver_android.domain.model.Expense
import com.example.money_saver_android.domain.model.Saving
import com.example.money_saver_android.domain.usecase.LoanUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.viewmodel.LoansViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        loansViewModel = LoansViewModel(LoanUseCase(loanRepo, savingsRepo, StubExpenseRepository()))
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
}
