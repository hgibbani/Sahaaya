package com.sahaaya.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography tuned for the people who actually hold this phone.
 *
 * Sahaaya's primary users are older adults, often with reduced near vision, and
 * caregivers reading a screen in a hurry. So the whole ramp is one step larger
 * than the Material 3 defaults, line height is generous, and nothing in the app
 * is allowed below [MinimumReadableSp].
 *
 * The system font is used deliberately: it is the face the user has already
 * configured their device accessibility settings around.
 */
internal const val MinimumReadableSp = 14

private val readableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = readableLineHeight,
)

internal val SahaayaTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, (-0.25)),
    displayMedium = style(45, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),

    headlineLarge = style(34, 42, FontWeight.Bold),
    headlineMedium = style(30, 38, FontWeight.Bold),
    headlineSmall = style(26, 34, FontWeight.SemiBold),

    titleLarge = style(24, 32, FontWeight.SemiBold),
    titleMedium = style(19, 26, FontWeight.SemiBold, 0.15),
    titleSmall = style(16, 22, FontWeight.SemiBold, 0.1),

    bodyLarge = style(18, 28, FontWeight.Normal, 0.5),
    bodyMedium = style(16, 24, FontWeight.Normal, 0.25),
    bodySmall = style(14, 20, FontWeight.Normal, 0.4),

    labelLarge = style(16, 22, FontWeight.SemiBold, 0.1),
    labelMedium = style(14, 18, FontWeight.Medium, 0.5),
    labelSmall = style(14, 18, FontWeight.Medium, 0.5),
)
