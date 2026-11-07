package com.example.money_saver_android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun hideKeyboard(
    context: Context,
    view: View? = null,
    keyboardController: SoftwareKeyboardController? = null,
    focusManager: FocusManager? = null
) {
    focusManager?.clearFocus(force = true)
    keyboardController?.hide()

    val activity = context.findActivity()
    activity?.window?.let { window ->
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view?.windowInsetsController?.hide(WindowInsets.Type.ime())
    }

    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val token = view?.windowToken
        ?: activity?.currentFocus?.windowToken
        ?: activity?.window?.decorView?.windowToken

    if (imm != null && token != null) {
        imm.hideSoftInputFromWindow(token, 0)
    }
}
