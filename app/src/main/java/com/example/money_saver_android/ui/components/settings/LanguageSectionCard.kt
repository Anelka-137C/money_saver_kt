package com.example.money_saver_android.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.CardBackground
import com.example.money_saver_android.ui.theme.GreenAccent
import com.example.money_saver_android.ui.theme.TextPrimary
import com.example.money_saver_android.ui.theme.TextSecondary
import com.example.money_saver_android.viewmodel.LanguageOption

@Composable
fun LanguageSectionCard(
    title: String,
    description: String,
    availableLanguages: List<LanguageOption>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
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

        Spacer(modifier = Modifier.height(4.dp))

        availableLanguages.forEach { language ->
            val isSelected = language.code == selectedLanguage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) BorderColor.copy(alpha = 0.5f) else CardBackground)
                    .clickable { onLanguageSelected(language.code) }
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
