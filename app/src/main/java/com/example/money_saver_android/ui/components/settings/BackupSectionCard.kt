package com.example.money_saver_android.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.BackupStatus
import com.example.money_saver_android.viewmodel.RestoreStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSectionCard(
    title: String,
    description: String,
    lastBackupLabel: String,
    neverBackedUpText: String,
    actionButtonLabel: String,
    inProgressLabel: String,
    successMessage: String,
    errorMessage: String,
    cancelledMessage: String,
    lastBackupTimestamp: Long?,
    backupStatus: BackupStatus,
    onCreateBackupClick: () -> Unit,
    restoreButtonLabel: String = "Restaurar desde Google Drive",
    restoreInProgressLabel: String = "Restaurando respaldo...",
    restoreSuccessMessage: String = "Datos restaurados correctamente",
    restoreInvalidFileMessage: String = "El archivo seleccionado no es un respaldo válido",
    restoreErrorMessage: String = "No se pudieron restaurar los datos",
    restoreCancelledMessage: String = "Restauración cancelada",
    restoreStatus: RestoreStatus = RestoreStatus.IDLE,
    onRestoreBackupClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val formattedTimestamp = remember(lastBackupTimestamp) {
        lastBackupTimestamp?.takeIf { it > 0L }?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
        }
    }
    val isRestoreBusy = restoreStatus == RestoreStatus.READING || restoreStatus == RestoreStatus.RESTORING
    val isAnyBusy = backupStatus == BackupStatus.IN_PROGRESS || isRestoreBusy

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("card_backup_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Text(
            text = description,
            fontSize = 14.sp,
            color = TextSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(FieldBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = lastBackupLabel,
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = formattedTimestamp ?: neverBackedUpText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (formattedTimestamp != null) TextPrimary else TextSecondary,
                modifier = Modifier.testTag("text_last_backup_status")
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = onCreateBackupClick,
            enabled = !isAnyBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_create_backup"),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenAccent,
                disabledContainerColor = FieldBackground
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (backupStatus == BackupStatus.IN_PROGRESS) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = GreenAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = inProgressLabel,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = actionButtonLabel,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedButton(
            onClick = onRestoreBackupClick,
            enabled = !isAnyBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_restore_backup"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            if (isRestoreBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = GreenAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = restoreInProgressLabel,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = restoreButtonLabel,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val backupFeedback: Pair<String, Color>? = when (backupStatus) {
            BackupStatus.SUCCESS -> successMessage to GreenAccent
            BackupStatus.ERROR -> errorMessage to ErrorRed
            BackupStatus.CANCELLED -> cancelledMessage to TextSecondary
            BackupStatus.IDLE, BackupStatus.IN_PROGRESS -> null
        }
        backupFeedback?.let { (message, color) ->
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.testTag("text_backup_feedback")
            )
        }

        val restoreFeedback: Pair<String, Color>? = when (restoreStatus) {
            RestoreStatus.SUCCESS -> restoreSuccessMessage to GreenAccent
            RestoreStatus.INVALID_FILE -> restoreInvalidFileMessage to ErrorRed
            RestoreStatus.ERROR -> restoreErrorMessage to ErrorRed
            RestoreStatus.CANCELLED -> restoreCancelledMessage to TextSecondary
            RestoreStatus.IDLE, RestoreStatus.READING, RestoreStatus.RESTORING -> null
        }
        restoreFeedback?.let { (message, color) ->
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.testTag("text_restore_feedback")
            )
        }
    }
}
