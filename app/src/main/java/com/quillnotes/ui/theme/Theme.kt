package com.quillnotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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

// ── Theme Composable ───────────────────────────────────────

@Composable
fun QuillNotesTheme(
    content: @Composable () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val themePreference by settingsViewModel.theme.collectAsState(initial = "system")

    val isDarkTheme = isSystemInDarkTheme()

    val colorScheme = when (themePreference) {
        "light" -> LightColorScheme
        "dark" -> DarkColorScheme
        "colorful" -> ColorfulColorScheme
        else -> if (isDarkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = QuillTypography,
        content = content
    )
}
