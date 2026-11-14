package com.quillnotes.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text a ViewModel can hand to the UI without holding a Context: a string resource plus its arguments
 * (an argument may itself be a [UiText]). The UI resolves it in the current language.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Plain -> value
        is Res ->
            if (args.isEmpty()) context.getString(id)
            else context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Resolves this text in the language of the current composition. */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
