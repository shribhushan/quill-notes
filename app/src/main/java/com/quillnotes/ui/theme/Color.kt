package com.quillnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ── Paper (light) ──────────────────────────────────────────
// Warm paper background, ink-blue accent, sepia and terracotta supporting tones.
internal val PaperScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3446D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE3FF),
    onPrimaryContainer = Color(0xFF0B1773),
    inversePrimary = Color(0xFFB9C3FF),
    secondary = Color(0xFF8A6A2E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E7C8),
    onSecondaryContainer = Color(0xFF2E2104),
    tertiary = Color(0xFFC4512A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDCF),
    onTertiaryContainer = Color(0xFF3D1101),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEEE8DD),
    onSurfaceVariant = Color(0xFF6A6358),
    surfaceTint = Color(0xFF3446D4),
    inverseSurface = Color(0xFF33302A),
    inverseOnSurface = Color(0xFFF7F0E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF948C7F),
    outlineVariant = Color(0xFFDDD5C8),
    scrim = Color(0xFF000000)
)

// ── Midnight (dark) ────────────────────────────────────────
internal val MidnightScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB8C2FF),
    onPrimary = Color(0xFF13207A),
    primaryContainer = Color(0xFF2A37A0),
    onPrimaryContainer = Color(0xFFDFE3FF),
    inversePrimary = Color(0xFF3446D4),
    secondary = Color(0xFFE3C687),
    onSecondary = Color(0xFF3F2E07),
    secondaryContainer = Color(0xFF5A4419),
    onSecondaryContainer = Color(0xFFF7E3B8),
    tertiary = Color(0xFFFFB599),
    onTertiary = Color(0xFF5C1A04),
    tertiaryContainer = Color(0xFF7A2E14),
    onTertiaryContainer = Color(0xFFFFDBCD),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFE8E5EE),
    surface = Color(0xFF15151B),
    onSurface = Color(0xFFE8E5EE),
    surfaceVariant = Color(0xFF22222B),
    onSurfaceVariant = Color(0xFFA9A6B5),
    surfaceTint = Color(0xFFB8C2FF),
    inverseSurface = Color(0xFFE8E5EE),
    inverseOnSurface = Color(0xFF1B1B21),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6E6B7A),
    outlineVariant = Color(0xFF2E2E38),
    scrim = Color(0xFF000000)
)

// ── Aurora (vivid light) ───────────────────────────────────
internal val AuroraScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF7A3CE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF270058),
    inversePrimary = Color(0xFFD4BBFF),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F1E8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFE0407B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3E0019),
    background = Color(0xFFFBF7FF),
    onBackground = Color(0xFF1D1A24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1A24),
    surfaceVariant = Color(0xFFF0E9FB),
    onSurfaceVariant = Color(0xFF645C73),
    surfaceTint = Color(0xFF7A3CE8),
    inverseSurface = Color(0xFF322F38),
    inverseOnSurface = Color(0xFFF5EFFA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF958DA3),
    outlineVariant = Color(0xFFE1D8EE),
    scrim = Color(0xFF000000)
)

// ── Note colour labels ─────────────────────────────────────
// Keys are persisted on NoteEntity.color, so they must never change.
val NoteColorKeys: List<String> = listOf("yellow", "green", "blue", "pink", "purple", "orange")

@Immutable
data class NoteTone(
    val container: Color,
    val content: Color,
    val accent: Color
)

internal val LightNoteTones: Map<String, NoteTone> = mapOf(
    "yellow" to NoteTone(Color(0xFFFFF0B3), Color(0xFF2B2306), Color(0xFFD9A400)),
    "green" to NoteTone(Color(0xFFDDF3D4), Color(0xFF12290F), Color(0xFF3E9B4F)),
    "blue" to NoteTone(Color(0xFFDCE8FF), Color(0xFF0B1E3F), Color(0xFF3D74DB)),
    "pink" to NoteTone(Color(0xFFFFE0EA), Color(0xFF3A0D1E), Color(0xFFDB4B7E)),
    "purple" to NoteTone(Color(0xFFECE1FF), Color(0xFF210F45), Color(0xFF7B53D6)),
    "orange" to NoteTone(Color(0xFFFFE4D0), Color(0xFF3A1A05), Color(0xFFE2741F))
)

internal val DarkNoteTones: Map<String, NoteTone> = mapOf(
    "yellow" to NoteTone(Color(0xFF3A3216), Color(0xFFF5EBC4), Color(0xFFF2CB57)),
    "green" to NoteTone(Color(0xFF1E3424), Color(0xFFD8F2DC), Color(0xFF74D08A)),
    "blue" to NoteTone(Color(0xFF1A2B4A), Color(0xFFD9E5FF), Color(0xFF86ABFF)),
    "pink" to NoteTone(Color(0xFF3E1B2A), Color(0xFFFFDDE8), Color(0xFFFF8FB5)),
    "purple" to NoteTone(Color(0xFF2C2147), Color(0xFFE9DDFF), Color(0xFFBA9EFF)),
    "orange" to NoteTone(Color(0xFF3E2717), Color(0xFFFFE3CF), Color(0xFFFFA866))
)

// ── Extended (non-Material) tokens ─────────────────────────
@Immutable
data class QuillColors(
    val isDark: Boolean,
    val card: Color,
    val cardBorder: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val dockContainer: Color,
    val dockContent: Color,
    val dockSelectedContainer: Color,
    val dockSelectedContent: Color,
    val noteTones: Map<String, NoteTone>
) {
    fun toneFor(key: String?): NoteTone? = key?.let { noteTones[it] }
}

internal val PaperColors = QuillColors(
    isDark = false,
    card = Color(0xFFFFFCF7),
    cardBorder = Color(0xFFE7E0D3),
    heroStart = Color(0xFFDFE3FF),
    heroEnd = Color(0xFFFFE3D3),
    success = Color(0xFF2E7D4F),
    successContainer = Color(0xFFD3F0DC),
    warning = Color(0xFFB26B00),
    dockContainer = Color(0xFF1E1B16),
    dockContent = Color(0xFFB9B2A6),
    dockSelectedContainer = Color(0xFFF7F3EC),
    dockSelectedContent = Color(0xFF1E1B16),
    noteTones = LightNoteTones
)

internal val MidnightColors = QuillColors(
    isDark = true,
    card = Color(0xFF1A1A21),
    cardBorder = Color(0xFF26262F),
    heroStart = Color(0xFF1F2560),
    heroEnd = Color(0xFF4A1F2F),
    success = Color(0xFF7DDC9F),
    successContainer = Color(0xFF1B3A27),
    warning = Color(0xFFF2B84B),
    dockContainer = Color(0xFF26262F),
    dockContent = Color(0xFF9C99A8),
    dockSelectedContainer = Color(0xFFB8C2FF),
    dockSelectedContent = Color(0xFF13207A),
    noteTones = DarkNoteTones
)

internal val AuroraColors = QuillColors(
    isDark = false,
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFECE3F8),
    heroStart = Color(0xFFE9DDFF),
    heroEnd = Color(0xFFFFD9E8),
    success = Color(0xFF00875A),
    successContainer = Color(0xFFCDF3E1),
    warning = Color(0xFFC77700),
    dockContainer = Color(0xFF241A3A),
    dockContent = Color(0xFFB7A9D1),
    dockSelectedContainer = Color(0xFFFFFFFF),
    dockSelectedContent = Color(0xFF3A1B7A),
    noteTones = LightNoteTones
)

/** Extended tokens derived from a wallpaper-based (Material You) scheme. */
internal fun dynamicQuillColors(scheme: ColorScheme, isDark: Boolean): QuillColors = QuillColors(
    isDark = isDark,
    card = if (isDark) scheme.surfaceVariant.copy(alpha = 0.55f) else scheme.surface,
    cardBorder = scheme.outlineVariant,
    heroStart = scheme.primaryContainer,
    heroEnd = scheme.tertiaryContainer,
    success = if (isDark) Color(0xFF7DDC9F) else Color(0xFF2E7D4F),
    successContainer = if (isDark) Color(0xFF1B3A27) else Color(0xFFD3F0DC),
    warning = if (isDark) Color(0xFFF2B84B) else Color(0xFFB26B00),
    dockContainer = if (isDark) scheme.surfaceVariant else scheme.inverseSurface,
    dockContent = if (isDark) scheme.onSurfaceVariant else scheme.inverseOnSurface.copy(alpha = 0.72f),
    dockSelectedContainer = scheme.primary,
    dockSelectedContent = scheme.onPrimary,
    noteTones = if (isDark) DarkNoteTones else LightNoteTones
)

// ── Moods ──────────────────────────────────────────────────
val MoodHappy = Color(0xFF4CAF6A)
val MoodNeutral = Color(0xFFF2A33A)
val MoodSad = Color(0xFF4A90E2)
val MoodAnxious = Color(0xFFE5534B)
val MoodCalm = Color(0xFF8B6CE0)
