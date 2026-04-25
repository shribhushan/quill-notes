package com.quillnotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.screens.settings.SettingsViewModel

// ── Color Schemes ──────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    error = LightError
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    error = DarkError
)

private val ColorfulColorScheme = lightColorScheme(
    primary = ColorfulPrimary,
    onPrimary = ColorfulOnPrimary,
    primaryContainer = ColorfulPrimaryContainer,
    onPrimaryContainer = ColorfulOnPrimaryContainer,
    secondary = ColorfulSecondary,
    onSecondary = ColorfulOnSecondary,
    secondaryContainer = ColorfulSecondaryContainer,
    tertiary = ColorfulTertiary,
    onTertiary = ColorfulOnTertiary,
    tertiaryContainer = ColorfulTertiaryContainer,
    background = ColorfulBackground,
    onBackground = ColorfulOnBackground,
    surface = ColorfulSurface,
    onSurface = ColorfulOnSurface,
    surfaceVariant = ColorfulSurfaceVariant,
    outline = ColorfulOutline,
    error = ColorfulError
)

// ── Typography Scaling ─────────────────────────────────────

private fun scaledTypography(scale: Float): Typography {
    if (scale == 1.0f) return QuillTypography
    fun TextStyle.scaled() = copy(
        fontSize = (fontSize.value * scale).sp,
        lineHeight = (lineHeight.value * scale).sp
    )
    return QuillTypography.copy(
        displayLarge  = QuillTypography.displayLarge.scaled(),
        headlineLarge = QuillTypography.headlineLarge.scaled(),
        headlineMedium = QuillTypography.headlineMedium.scaled(),
        headlineSmall = QuillTypography.headlineSmall.scaled(),
        titleLarge    = QuillTypography.titleLarge.scaled(),
        titleMedium   = QuillTypography.titleMedium.scaled(),
        titleSmall    = QuillTypography.titleSmall.scaled(),
        bodyLarge     = QuillTypography.bodyLarge.scaled(),
        bodyMedium    = QuillTypography.bodyMedium.scaled(),
        bodySmall     = QuillTypography.bodySmall.scaled(),
        labelLarge    = QuillTypography.labelLarge.scaled(),
        labelMedium   = QuillTypography.labelMedium.scaled(),
        labelSmall    = QuillTypography.labelSmall.scaled()
    )
}

// ── Theme Composable ───────────────────────────────────────

@Composable
fun QuillNotesTheme(
    content: @Composable () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val themePreference by settingsViewModel.theme.collectAsState(initial = "system")
    val fontSizePreference by settingsViewModel.fontSize.collectAsState(initial = "medium")

    val isDarkTheme = isSystemInDarkTheme()

    val colorScheme = when (themePreference) {
        "light" -> LightColorScheme
        "dark" -> DarkColorScheme
        "colorful" -> ColorfulColorScheme
        else -> if (isDarkTheme) DarkColorScheme else LightColorScheme
    }

    val fontScale = when (fontSizePreference) {
        "small" -> 0.85f
        "large" -> 1.15f
        else -> 1.0f
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontScale),
        content = content
    )
}
