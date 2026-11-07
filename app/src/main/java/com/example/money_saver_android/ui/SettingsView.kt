package com.example.money_saver_android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.i18n.I18nManager
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.DarkBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.SettingsViewModel

@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    i18nManager: I18nManager,
    modifier: Modifier = Modifier
) {
    val state by settingsViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = i18nManager.t("settings.header.title"),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = i18nManager.t("settings.language_section.title"),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                text = i18nManager.t("settings.language_section.description"),
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            state.availableLanguages.forEach { language ->
                val isSelected = language.code == state.selectedLanguage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) BorderColor.copy(alpha = 0.5f) else CardBackground)
                        .clickable { settingsViewModel.onLanguageSelected(language.code) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("lang_option_${language.code}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = language.nativeName,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) GreenAccent else BorderColor, CircleShape)
                            .background(if (isSelected) GreenAccent else CardBackground)
                    )
                }
            }
        }
    }
}
