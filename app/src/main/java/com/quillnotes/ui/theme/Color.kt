package com.quillnotes.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light Theme ────────────────────────────────────────────
val LightPrimary = Color(0xFF1A1A2E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8E8F0)
val LightOnPrimaryContainer = Color(0xFF1A1A2E)
val LightSecondary = Color(0xFF5C5C6E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE2E1EC)
val LightBackground = Color(0xFFFCFCFF)
val LightOnBackground = Color(0xFF1A1A2E)
val LightSurface = Color(0xFFFCFCFF)
val LightOnSurface = Color(0xFF1A1A2E)
val LightSurfaceVariant = Color(0xFFF0EFF4)
val LightOutline = Color(0xFFC4C4CE)
val LightError = Color(0xFFBA1A1A)

// ── Dark Theme ─────────────────────────────────────────────
val DarkPrimary = Color(0xFFCBCBE0)
val DarkOnPrimary = Color(0xFF30304A)
val DarkPrimaryContainer = Color(0xFF464662)
val DarkOnPrimaryContainer = Color(0xFFE8E8F0)
val DarkSecondary = Color(0xFFC6C5D0)
val DarkOnSecondary = Color(0xFF2E2E40)
val DarkSecondaryContainer = Color(0xFF454556)
val DarkBackground = Color(0xFF121218)
val DarkOnBackground = Color(0xFFE4E4EC)
val DarkSurface = Color(0xFF121218)
val DarkOnSurface = Color(0xFFE4E4EC)
val DarkSurfaceVariant = Color(0xFF1E1E2A)
val DarkOutline = Color(0xFF5C5C6E)
val DarkError = Color(0xFFFFB4AB)

// ── Colorful Theme ─────────────────────────────────────────
val ColorfulPrimary = Color(0xFF6750A4)
val ColorfulOnPrimary = Color(0xFFFFFFFF)
val ColorfulPrimaryContainer = Color(0xFFEADDFF)
val ColorfulOnPrimaryContainer = Color(0xFF21005D)
val ColorfulSecondary = Color(0xFF00696E)
val ColorfulOnSecondary = Color(0xFFFFFFFF)
val ColorfulSecondaryContainer = Color(0xFF9CF0F4)
val ColorfulTertiary = Color(0xFFB5446E)
val ColorfulOnTertiary = Color(0xFFFFFFFF)
val ColorfulTertiaryContainer = Color(0xFFFFD9E2)
val ColorfulBackground = Color(0xFFFFFBFE)
val ColorfulOnBackground = Color(0xFF1C1B1F)
val ColorfulSurface = Color(0xFFFFFBFE)
val ColorfulOnSurface = Color(0xFF1C1B1F)
val ColorfulSurfaceVariant = Color(0xFFF4EEFF)
val ColorfulOutline = Color(0xFF938F99)
val ColorfulError = Color(0xFFBA1A1A)

// ── Accent Colors (shared) ─────────────────────────────────
val NoteYellow = Color(0xFFFFF9C4)
val NoteGreen = Color(0xFFC8E6C9)
val NoteBlue = Color(0xFFBBDEFB)
val NotePink = Color(0xFFF8BBD0)
val NotePurple = Color(0xFFE1BEE7)
val NoteOrange = Color(0xFFFFE0B2)

val MoodHappy = Color(0xFF66BB6A)
val MoodNeutral = Color(0xFFFFA726)
val MoodSad = Color(0xFF42A5F5)
val MoodAnxious = Color(0xFFEF5350)
val MoodCalm = Color(0xFF7E57C2)

// ── Note Color Labels ──────────────────────────────────────
// Keys stored on NoteEntity.color; null/"default" means no accent.
val noteColorPalette: Map<String, Color> = mapOf(
    "yellow" to NoteYellow,
    "green" to NoteGreen,
    "blue" to NoteBlue,
    "pink" to NotePink,
    "purple" to NotePurple,
    "orange" to NoteOrange
)
