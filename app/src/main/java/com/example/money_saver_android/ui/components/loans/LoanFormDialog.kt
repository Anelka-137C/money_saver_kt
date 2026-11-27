package com.example.money_saver_android.ui.components.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Shared modal container for loan dialogs (add/edit loan, payment schedule, and record payment).
 */
@Composable
internal fun LoanFormDialog(
    title: String,
    subtitle: String? = null,
    errorMessage: String?,
    confirmButtonText: String,
    dialogTestTag: String,
    closeButtonTestTag: String,
    errorTestTag: String,
    confirmButtonTestTag: String,
    scrollTestTag: String? = null,
    onConfirmClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 16.dp)
                .heightIn(max = maxDialogHeight)
                .testTag(dialogTestTag),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(closeButtonTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val scrollModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .let { base ->
                        if (scrollTestTag != null) base.testTag(scrollTestTag) else base
                    }

                Column(
                    modifier = scrollModifier,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    content()

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag(errorTestTag)
                        )
                    }

                    Button(
                        onClick = onConfirmClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag(confirmButtonTestTag),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = confirmButtonText,
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscardChangesDialog(
    title: String,
    message: String,
    cancelText: String,
    confirmText: String,
    dialogTestTag: String,
    cancelButtonTestTag: String,
    confirmButtonTestTag: String,
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag(dialogTestTag),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(cancelButtonTestTag),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            text = cancelText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = onConfirmDiscard,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(confirmButtonTestTag),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text(
                            text = confirmText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
