package com.quillnotes.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.screens.settings.SettingsViewModel

val LocalQuillColors = staticCompositionLocalOf { PaperColors }

/** Access to Quill's extended design tokens alongside [MaterialTheme]. */
object QuillTheme {
    val colors: QuillColors
        @Composable
        @ReadOnlyComposable
        get() = LocalQuillColors.current
}

/** Persisted theme keys (kept stable for existing preferences). */
object ThemeKeys {
    const val SYSTEM = "system"
    const val PAPER = "light"
    const val MIDNIGHT = "dark"
    const val AURORA = "colorful"
    const val DYNAMIC = "dynamic"
}

fun fontScaleFor(key: String): Float = when (key) {
    "small" -> 0.9f
    "large" -> 1.12f
    else -> 1f
}

/**
 * Stateless theme — takes the theme key and font scale directly so it can be
 * used in screenshot tests and previews without a ViewModel.
 */
@Composable
fun QuillTheme(
    themeKey: String = ThemeKeys.SYSTEM,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val (scheme, extended) = when (themeKey) {
        ThemeKeys.PAPER -> PaperScheme to PaperColors
        ThemeKeys.MIDNIGHT -> MidnightScheme to MidnightColors
        ThemeKeys.AURORA -> AuroraScheme to AuroraColors
        ThemeKeys.DYNAMIC -> {
            val dynamic = if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic to dynamicQuillColors(dynamic, systemDark)
        }
        else -> if (systemDark) MidnightScheme to MidnightColors else PaperScheme to PaperColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !extended.isDark
            controller.isAppearanceLightNavigationBars = !extended.isDark
        }
    }

    CompositionLocalProvider(LocalQuillColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = quillTypography(fontScale),
            shapes = QuillShapes,
            content = content
        )
    }
}

/** App-level theme that reads the user's appearance preferences. */
@Composable
fun QuillNotesTheme(content: @Composable () -> Unit) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val theme by settingsViewModel.theme.collectAsState(initial = ThemeKeys.SYSTEM)
    val fontSize by settingsViewModel.fontSize.collectAsState(initial = "medium")
    QuillTheme(themeKey = theme, fontScale = fontScaleFor(fontSize), content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
