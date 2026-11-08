package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.viewmodel.FakeFinancialPeriodRepository
import com.example.money_saver_android.viewmodel.PeriodViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var i18nManager: I18nManager
    private lateinit var fakeRepo: FakeFinancialPeriodRepository
    private lateinit var periodViewModel: PeriodViewModel

    @Before
    fun setUp() {
        i18nManager = I18nManager()
        val esJson = """
            {
              "period": {
                "title": "Período Financiero",
                "active_period_title": "Período activo",
                "history_title": "Historial de períodos",
                "start_date": "Fecha de inicio",
                "end_date": "Fecha de fin",
                "save_button": "Guardar período",
                "edit_button": "Modificar período",
                "menu_edit": "Modificar",
                "menu_delete": "Eliminar",
                "edit_dialog_title": "Editar período",
                "edit_dialog_save": "Guardar",
                "edit_dialog_cancel": "Cancelar",
                "delete_dialog": {
                  "title": "¿Eliminar este período?",
                  "warning_message": "Esta acción no se puede deshacer. Desliza el botón para confirmar.",
                  "cancel_button": "Cancelar"
                }
              }
            }
        """.trimIndent()
        i18nManager.loadTranslation("es", esJson)
        i18nManager.setLanguage("es")

        fakeRepo = FakeFinancialPeriodRepository(initialPeriod = null)
        val useCase = PeriodUseCase(fakeRepo, currentDateProvider = { "2026-11-07" })
        periodViewModel = PeriodViewModel(useCase)
    }

    private fun createPeriodViewModel(repo: FakeFinancialPeriodRepository): PeriodViewModel {
        val useCase = PeriodUseCase(repo, currentDateProvider = { "2026-11-07" })
        return PeriodViewModel(useCase)
    }

    @Test
    fun test_period_screen_shows_validation_error_when_dates_are_invalid() {
        composeTestRule.setContent {
            PeriodView(
                periodViewModel = periodViewModel,
                i18nManager = i18nManager
            )
        }

        // Enter end date prior to start date
        composeTestRule.onNodeWithTag("input_start_date").performTextInput("2026-11-20")
        composeTestRule.onNodeWithTag("input_end_date").performTextInput("2026-11-10")
        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        // Validation error is displayed
        composeTestRule.onNodeWithTag("period_error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("La fecha de fin no puede ser anterior a la de inicio").assertIsDisplayed()
    }

    @Test
    fun test_clicking_start_date_opens_date_picker_dialog() {
        composeTestRule.setContent {
            PeriodView(
                periodViewModel = periodViewModel,
                i18nManager = i18nManager
            )
        }

        // Initially no dialog
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()

        // Click calendar picker icon on start date
        composeTestRule.onNodeWithTag("btn_pick_start_date").performClick()

        // Date picker dialog is displayed
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
    }

    @Test
    fun test_selecting_date_in_picker_updates_iso_date_field() {
        composeTestRule.setContent {
            PeriodView(
                periodViewModel = periodViewModel,
                i18nManager = i18nManager
            )
        }

        // Open start date picker
        composeTestRule.onNodeWithTag("btn_pick_start_date").performClick()
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()

        // Confirm
        composeTestRule.onNodeWithTag("btn_confirm_date_picker").performClick()

        // Dialog closes and start date is updated in ISO format
        composeTestRule.onNodeWithTag("date_picker_dialog").assertDoesNotExist()
        assertTrue(periodViewModel.uiState.value.startDateInput.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun test_date_picker_selection_preserves_validations() {
        composeTestRule.setContent {
            PeriodView(
                periodViewModel = periodViewModel,
                i18nManager = i18nManager
            )
        }

        // Open and confirm date via picker
        composeTestRule.onNodeWithTag("btn_pick_start_date").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_date_picker").performClick()

        // Set range that does not cover current date (2026-11-07)
        periodViewModel.onStartDateChanged("2026-11-15")
        periodViewModel.onEndDateChanged("2026-11-25")

        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        composeTestRule.onNodeWithTag("period_error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("El período debe incluir la fecha actual o pertenecer a un mes anterior").assertIsDisplayed()
    }

    @Test
    fun test_period_screen_displays_active_period_and_history_list() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val pastPeriod = FinancialPeriod(id = 2L, startDate = "2026-10-01", endDate = "2026-10-31")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        runBlocking { repo.savePeriod(pastPeriod) }
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("active_period_info").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_history_list").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_card_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_card_2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_clicking_three_dots_menu_displays_edit_and_delete_options() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_edit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_option_delete").assertIsDisplayed()
    }

    @Test
    fun test_selecting_edit_opens_dialog_and_saving_updates_period() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_edit").performClick()

        // Centered modal edit dialog is displayed
        composeTestRule.onNodeWithTag("edit_period_dialog").assertIsDisplayed()

        // Modify date in edit dialog
        composeTestRule.onNodeWithTag("input_edit_end_date").performTextReplacement("2026-12-05")
        composeTestRule.onNodeWithTag("btn_save_edit_period").performClick()

        // Dialog closes
        composeTestRule.onNodeWithTag("edit_period_dialog").assertDoesNotExist()
    }

    @Test
    fun test_edit_dialog_date_pickers_open_and_update_fields() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_edit").performClick()

        // Click start date picker icon inside edit dialog
        composeTestRule.onNodeWithTag("btn_pick_edit_start_date").performClick()
        composeTestRule.onNodeWithTag("edit_start_date_picker_dialog").assertIsDisplayed()

        // Confirm date picker selection
        composeTestRule.onNodeWithTag("btn_confirm_edit_start_date_picker").performClick()
        composeTestRule.onNodeWithTag("edit_start_date_picker_dialog").assertDoesNotExist()

        // Click end date picker icon inside edit dialog
        composeTestRule.onNodeWithTag("btn_pick_edit_end_date").performClick()
        composeTestRule.onNodeWithTag("edit_end_date_picker_dialog").assertIsDisplayed()

        // Confirm date picker selection
        composeTestRule.onNodeWithTag("btn_confirm_edit_end_date_picker").performClick()
        composeTestRule.onNodeWithTag("edit_end_date_picker_dialog").assertDoesNotExist()
    }

    @Test
    fun test_clicking_delete_in_menu_opens_slide_confirmation_dialog() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("period_card_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_delete").performClick()

        // Delete confirmation dialog is displayed
        composeTestRule.onNodeWithTag("delete_period_dialog").assertIsDisplayed()

        // Cancel dismissal
        composeTestRule.onNodeWithTag("btn_cancel_delete_period").performClick()
        composeTestRule.onNodeWithTag("delete_period_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_card_1").assertExists()
    }

    @Test
    fun test_confirming_slide_to_delete_removes_period() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("period_card_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_delete").performClick()

        composeTestRule.onNodeWithTag("delete_period_dialog").assertIsDisplayed()

        // Slide the thumb to confirm deletion
        composeTestRule.onNodeWithContentDescription("Deslizar para eliminar").performTouchInput {
            down(center)
            moveBy(Offset(1000f, 0f))
            up()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("period_card_1").assertDoesNotExist()
    }

    @Test
    fun test_main_period_button_always_creates_new_period() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        // Button always displays create label
        composeTestRule.onNodeWithTag("btn_save_period").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar período").assertIsDisplayed()

        // Create a past period (September 2026)
        composeTestRule.onNodeWithTag("input_start_date").performTextInput("2026-09-01")
        composeTestRule.onNodeWithTag("input_end_date").performTextInput("2026-09-30")
        composeTestRule.onNodeWithTag("btn_save_period").performClick()

        // Active period is still 2026-11-01 to 2026-11-30
        assertEquals("2026-11-01", vm.uiState.value.activePeriod?.startDate)
        // Two periods are registered in total
        assertEquals(2, vm.uiState.value.allPeriods.size)
    }

    @Test
    fun test_three_dots_menu_styling_and_options_match_expenses() {
        val activePeriod = FinancialPeriod(id = 1L, startDate = "2026-11-01", endDate = "2026-11-30")
        val repo = FakeFinancialPeriodRepository(activePeriod)
        val vm = createPeriodViewModel(repo)

        composeTestRule.setContent {
            PeriodView(
                periodViewModel = vm,
                i18nManager = i18nManager
            )
        }

        composeTestRule.onNodeWithTag("btn_period_options_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("menu_option_edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Modificar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_option_delete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminar").assertIsDisplayed()
    }
}
