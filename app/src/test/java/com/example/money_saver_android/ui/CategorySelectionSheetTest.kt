package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CategorySelectionSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test_category_sheet_displays_delete_button_and_triggers_deletion() {
        var deletedCategory: String? = null
        val testCategories = listOf(
            Category(name = "Investments", icon = "📈", color = "#1CC88A", subcategories = listOf(Subcategory("Stocks")))
        )

        composeTestRule.setContent {
            CategorySelectionSheet(
                categories = testCategories,
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
                onDeleteCategory = { deletedCategory = it },
                onDismiss = {},
                onCategorySelected = { _, _ -> }
            )
        }

        // 1. Delete button exists and is displayed for category "Investments"
        composeTestRule.onNodeWithTag("btn_delete_category_Investments").assertIsDisplayed()

        // 2. Clicking delete button invokes onDeleteCategory
        composeTestRule.onNodeWithTag("btn_delete_category_Investments").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Investments", deletedCategory)
    }
}
