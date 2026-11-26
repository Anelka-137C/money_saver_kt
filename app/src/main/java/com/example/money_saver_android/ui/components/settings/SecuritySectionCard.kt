package com.example.money_saver_android.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.security.DeviceAuthCapability
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary

/**
 * Settings card allowing the user to enable/disable native device unlock protection
 * and displaying the authentication capability detected on the device.
 */
@Composable
fun SecuritySectionCard(
    title: String,
    description: String,
    toggleLabel: String,
    capabilityBiometricAndPinText: String,
    capabilityPinOnlyText: String,
    capabilityNoneText: String,
    isAppLockEnabled: Boolean,
    deviceAuthCapability: DeviceAuthCapability,
    onAppLockToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSwitchEnabled = deviceAuthCapability != DeviceAuthCapability.NONE
    val capabilityMessage = when (deviceAuthCapability) {
        DeviceAuthCapability.BIOMETRIC_AND_PIN -> capabilityBiometricAndPinText
        DeviceAuthCapability.PIN_ONLY -> capabilityPinOnlyText
        DeviceAuthCapability.NONE -> capabilityNoneText
    }
    val capabilityColor = when (deviceAuthCapability) {
        DeviceAuthCapability.BIOMETRIC_AND_PIN -> GreenAccent
        DeviceAuthCapability.PIN_ONLY, DeviceAuthCapability.NONE -> TextSecondary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("card_security_section"),
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = toggleLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSwitchEnabled) TextPrimary else TextSecondary
            )

            Switch(
                checked = isAppLockEnabled && isSwitchEnabled,
                onCheckedChange = onAppLockToggled,
                enabled = isSwitchEnabled,
                modifier = Modifier.testTag("switch_app_lock"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = GreenAccent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CardBackground,
                    uncheckedBorderColor = BorderColor
                )
            )
        }

        Text(
            text = capabilityMessage,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = capabilityColor,
            modifier = Modifier.testTag("text_security_capability")
        )
    }
}
