package com.example.money_saver_android.ui.components.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.security.AppLockUiState
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Hides protected financial content behind [AppLockScreen] while the session is locked.
 */
@Composable
fun AppLockGate(
    lockState: AppLockUiState,
    i18nManager: I18nManager,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (lockState.isLocked) {
        AppLockScreen(
            showAuthError = lockState.showAuthError,
            title = i18nManager.t("app_lock.screen_title"),
            subtitle = i18nManager.t("app_lock.screen_subtitle"),
            errorMessage = i18nManager.t("app_lock.auth_error"),
            retryButtonLabel = i18nManager.t("app_lock.retry_button"),
            onRetry = onRetry,
            modifier = modifier
        )
    } else {
        content()
    }
}

/**
 * Simple lock screen displayed when native device unlock is active and not yet verified.
 */
@Composable
fun AppLockScreen(
    showAuthError: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String,
    retryButtonLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BorderColor.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = title,
                    tint = GreenAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (showAuthError) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ErrorRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("text_app_lock_error")
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_app_lock_retry"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = retryButtonLabel,
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
