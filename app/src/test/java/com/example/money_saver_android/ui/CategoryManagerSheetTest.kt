package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.AnnotatedString
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.i18n.LocalI18nManager
import com.example.money_saver_android.ui.components.atom.CategoryEditState
import com.example.money_saver_android.ui.components.atom.CategoryManagerSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryManagerSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var i18nManager: I18nManager

    @Before
    fun setUp() {
        i18nManager = I18nManager().apply {
            initFromContext(RuntimeEnvironment.getApplication())
            setLanguage("es")
        }
    }

    @Test
    fun categoryCreation_selectsColor_showsError_andSubmits() {
        var nameInput by mutableStateOf("")
        var selectedColor by mutableStateOf("#4E73DF")
        var categoryError by mutableStateOf<String?>("Nombre inválido")
        var created = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                CategoryManagerSheet(
                    visible = true,
                    categories = emptyList(),
                    newCategoryName = nameInput,
                    selectedColorHex = selectedColor,
                    categoryError = categoryError,
                    newSubcategoryName = "",
                    subcategoryError = null,
                    editingCategory = null,
                    categoryToDelete = null,
                    onCategoryNameChange = {
                        nameInput = it
                        categoryError = null
                    },
                    onColorSelect = { selectedColor = it },
                    onCreateCategory = { created = true },
                    onSubcategoryNameChange = {},
                    onCreateSubcategory = {},
                    onEditCategoryClick = {},
                    onEditCategoryNameChange = {},
                    onEditCategoryColorSelect = {},
                    onConfirmEditCategory = {},
                    onDismissEditCategory = {},
                    onDeleteCategoryClick = {},
                    onConfirmDeleteCategory = {},
                    onDismissDeleteCategory = {},
                    onDeleteSubcategory = { _, _ -> },
                    onClearCategoryError = { categoryError = null },
                    onClearSubcategoryError = {},
                    onDismiss = {},
                    onCategorySelected = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Nombre inválido").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nombre de la categoría")
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("Inversiones")) }
        composeTestRule.onNodeWithContentDescription("Crear")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        assertEquals("Inversiones", nameInput)
        assertTrue(created)
    }

    @Test
    fun threeDotsMenu_triggersEditDialog_saveCancelAndError() {
        val sampleCategories = listOf(
            Category(name = "Alimentación", icon = "🍔", color = "#1CC88A", subcategories = listOf(Subcategory("Restaurante")))
        )
        var editingState by mutableStateOf<CategoryEditState?>(null)
        var confirmed = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                CategoryManagerSheet(
                    visible = true,
                    categories = sampleCategories,
                    newCategoryName = "",
                    selectedColorHex = "#4E73DF",
                    categoryError = null,
                    newSubcategoryName = "",
                    subcategoryError = null,
                    editingCategory = editingState,
                    categoryToDelete = null,
                    onCategoryNameChange = {},
                    onColorSelect = {},
                    onCreateCategory = {},
                    onSubcategoryNameChange = {},
                    onCreateSubcategory = {},
                    onEditCategoryClick = {
                        editingState = CategoryEditState(
                            originalName = it.name,
                            name = it.name,
                            color = it.color,
                            icon = it.icon
                        )
                    },
                    onEditCategoryNameChange = { newName ->
                        editingState = editingState?.copy(name = newName, errorMessage = null)
                    },
                    onEditCategoryColorSelect = { hex ->
                        editingState = editingState?.copy(color = hex)
                    },
                    onConfirmEditCategory = {
                        if (editingState?.name.isNullOrBlank()) {
                            editingState = editingState?.copy(errorMessage = "El nombre de la categoría no puede estar vacío")
                        } else {
                            confirmed = true
                            editingState = null
                        }
                    },
                    onDismissEditCategory = { editingState = null },
                    onDeleteCategoryClick = {},
                    onConfirmDeleteCategory = {},
                    onDismissDeleteCategory = {},
                    onDeleteSubcategory = { _, _ -> },
                    onClearCategoryError = {},
                    onClearSubcategoryError = {},
                    onDismiss = {},
                    onCategorySelected = { _, _ -> }
                )
            }
        }

        // Open three-dots menu -> Modificar
        composeTestRule.onNodeWithTag("btn_options_category_Alimentación").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_edit_category_Alimentación").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_edit_category").assertIsDisplayed()

        // Cancel closes dialog
        composeTestRule.onNodeWithTag("btn_cancel_edit_category").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_edit_category").assertDoesNotExist()
        assertFalse(confirmed)

        // Open again, trigger error, then fix and save
        composeTestRule.onNodeWithTag("btn_options_category_Alimentación").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_edit_category_Alimentación").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            editingState = editingState?.copy(name = "")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_save_edit_category").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_edit_category_error").assertIsDisplayed()

        composeTestRule.onNodeWithTag("color_option_edit_category_#F6C23E").performClick()
        composeTestRule.onNodeWithTag("input_edit_category_name")
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("Comida")) }
        composeTestRule.onNodeWithTag("btn_save_edit_category").performClick()
        composeTestRule.waitForIdle()

        assertTrue(confirmed)
        composeTestRule.onNodeWithTag("dialog_edit_category").assertDoesNotExist()
    }

    @Test
    fun threeDotsMenu_triggersDeleteDialog_cancelAndSlideToConfirm() {
        val sampleCategories = listOf(
            Category(name = "Viajes", icon = "✈️", color = "#36B9CC", subcategories = emptyList())
        )
        var categoryToDelete by mutableStateOf<Category?>(null)
        var confirmedDelete = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                CategoryManagerSheet(
                    visible = true,
                    categories = sampleCategories,
                    newCategoryName = "",
                    selectedColorHex = "#4E73DF",
                    categoryError = null,
                    newSubcategoryName = "",
                    subcategoryError = null,
                    editingCategory = null,
                    categoryToDelete = categoryToDelete,
                    onCategoryNameChange = {},
                    onColorSelect = {},
                    onCreateCategory = {},
                    onSubcategoryNameChange = {},
                    onCreateSubcategory = {},
                    onEditCategoryClick = {},
                    onEditCategoryNameChange = {},
                    onEditCategoryColorSelect = {},
                    onConfirmEditCategory = {},
                    onDismissEditCategory = {},
                    onDeleteCategoryClick = { name ->
                        categoryToDelete = sampleCategories.find { it.name == name }
                    },
                    onConfirmDeleteCategory = {
                        confirmedDelete = true
                        categoryToDelete = null
                    },
                    onDismissDeleteCategory = { categoryToDelete = null },
                    onDeleteSubcategory = { _, _ -> },
                    onClearCategoryError = {},
                    onClearSubcategoryError = {},
                    onDismiss = {},
                    onCategorySelected = { _, _ -> }
                )
            }
        }

        // Open three-dots menu -> Eliminar
        composeTestRule.onNodeWithTag("btn_options_category_Viajes").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_delete_category_Viajes").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_delete_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_delete_category").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_delete_category").assertDoesNotExist()
        assertFalse(confirmedDelete)

        // Open again and slide to confirm
        composeTestRule.onNodeWithTag("btn_options_category_Viajes").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_delete_category_Viajes").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_slide_to_confirm_delete_category").performTouchInput {
            swipe(
                start = Offset(width * 0.12f, centerY),
                end = Offset(width * 0.95f, centerY),
                durationMillis = 300
            )
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        assertTrue(confirmedDelete)
        composeTestRule.onNodeWithTag("dialog_delete_category").assertDoesNotExist()
    }

    @Test
    fun subcategoriesFlow_continueWithoutSubcategory_createAndDeleteSubcategory() {
        val sampleCategories = listOf(
            Category(name = "Trabajo", icon = "💼", color = "#4E73DF", subcategories = listOf(Subcategory("Salario")))
        )
        var newSub by mutableStateOf("")
        var createdForCategory: String? = null
        var deletedSub: Pair<String, String>? = null
        var selected: Pair<String, String>? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalI18nManager provides i18nManager) {
                CategoryManagerSheet(
                    visible = true,
                    categories = sampleCategories,
                    newCategoryName = "",
                    selectedColorHex = "#4E73DF",
                    categoryError = null,
                    newSubcategoryName = newSub,
                    subcategoryError = null,
                    editingCategory = null,
                    categoryToDelete = null,
                    onCategoryNameChange = {},
                    onColorSelect = {},
                    onCreateCategory = {},
                    onSubcategoryNameChange = { newSub = it },
                    onCreateSubcategory = { createdForCategory = it },
                    onEditCategoryClick = {},
                    onEditCategoryNameChange = {},
                    onEditCategoryColorSelect = {},
                    onConfirmEditCategory = {},
                    onDismissEditCategory = {},
                    onDeleteCategoryClick = {},
                    onConfirmDeleteCategory = {},
                    onDismissDeleteCategory = {},
                    onDeleteSubcategory = { cat, sub -> deletedSub = cat to sub },
                    onClearCategoryError = {},
                    onClearSubcategoryError = {},
                    onDismiss = {},
                    onCategorySelected = { cat, sub -> selected = cat to sub }
                )
            }
        }

        // Enter subcategory view
        composeTestRule.onNodeWithText("Trabajo").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Create subcategory
        composeTestRule.onNodeWithText("Nombre de la subcategoría")
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("Bonos")) }
        composeTestRule.onNodeWithContentDescription("Crear")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("Bonos", newSub)
        assertEquals("Trabajo", createdForCategory)

        // Direct delete subcategory
        composeTestRule.onNodeWithTag("btn_delete_subcategory_Salario").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Trabajo" to "Salario", deletedSub)

        // Continue without subcategory
        composeTestRule.onNodeWithTag("btn_select_category_without_subcategory").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Trabajo" to "", selected)
    }
}
