package com.example.money_saver_android.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalI18nManager = compositionLocalOf { I18nManager() }

@Composable
fun t(key: String): String {
    return LocalI18nManager.current.t(key)
}
