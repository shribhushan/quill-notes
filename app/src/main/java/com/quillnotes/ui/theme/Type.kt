package com.quillnotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.quillnotes.R

/** Editorial serif used for screen titles, note titles and other display text. */
val QuillSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/** UI and reading text — the platform sans, so it stays native and legible at small sizes. */
val QuillSans: FontFamily = FontFamily.Default

private val BaseTypography = Typography(
    displayLarge = TextStyle(fontFamily = QuillSerif, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.0).sp),
    displayMedium = TextStyle(fontFamily = QuillSerif, fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = QuillSerif, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = QuillSerif, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = QuillSerif, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = QuillSerif, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = QuillSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp)
)

private fun TextUnit.scaledBy(scale: Float): TextUnit = if (isSpecified) (value * scale).sp else this

private fun TextStyle.scaled(scale: Float): TextStyle =
    copy(fontSize = fontSize.scaledBy(scale), lineHeight = lineHeight.scaledBy(scale))

internal fun quillTypography(scale: Float): Typography {
    if (scale == 1f) return BaseTypography
    val t = BaseTypography
    return Typography(
        displayLarge = t.displayLarge.scaled(scale),
        displayMedium = t.displayMedium.scaled(scale),
        displaySmall = t.displaySmall.scaled(scale),
        headlineLarge = t.headlineLarge.scaled(scale),
        headlineMedium = t.headlineMedium.scaled(scale),
        headlineSmall = t.headlineSmall.scaled(scale),
        titleLarge = t.titleLarge.scaled(scale),
        titleMedium = t.titleMedium.scaled(scale),
        titleSmall = t.titleSmall.scaled(scale),
        bodyLarge = t.bodyLarge.scaled(scale),
        bodyMedium = t.bodyMedium.scaled(scale),
        bodySmall = t.bodySmall.scaled(scale),
        labelLarge = t.labelLarge.scaled(scale),
        labelMedium = t.labelMedium.scaled(scale),
        labelSmall = t.labelSmall.scaled(scale)
    )
}
