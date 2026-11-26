package com.example.money_saver_android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.security.AppLockUiState
import com.example.money_saver_android.ui.components.security.AppLockGate
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
class AppLockScreenTest {

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
    fun test_lock_screen_hides_financial_data_and_shows_error_with_retry_on_failure_or_cancel() {
        var lockState by mutableStateOf(AppLockUiState(isLocked = true, isAuthenticating = true, showAuthError = false))
        var retryClicked = false

        composeTestRule.setContent {
            AppLockGate(
                lockState = lockState,
                i18nManager = i18nManager,
                onRetry = { retryClicked = true }
            ) {
                Text("Saldo total: $1.500.000")
            }
        }

        // Locked: financial data is hidden, lock screen is shown without error initially
        composeTestRule.onNodeWithText("Saldo total: $1.500.000").assertDoesNotExist()
        composeTestRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_app_lock_error").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_app_lock_retry").assertDoesNotExist()

        // Failure / cancellation: financial data stays hidden, error + retry button appear
        composeTestRule.runOnUiThread {
            lockState = AppLockUiState(isLocked = true, isAuthenticating = false, showAuthError = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Saldo total: $1.500.000").assertDoesNotExist()
        composeTestRule.onNodeWithTag("text_app_lock_error")
            .assertIsDisplayed()
            .assertTextEquals("No se pudo verificar tu identidad. Intenta de nuevo.")
        composeTestRule.onNodeWithTag("btn_app_lock_retry")
            .assertIsDisplayed()
            .performClick()

        assertTrue(retryClicked)

        // Successful unlock: lock screen disappears and financial data is accessible
        composeTestRule.runOnUiThread {
            lockState = AppLockUiState(isLocked = false, isAuthenticating = false, showAuthError = false)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_lock_screen").assertDoesNotExist()
        composeTestRule.onNodeWithText("Saldo total: $1.500.000").assertIsDisplayed()
    }
}
