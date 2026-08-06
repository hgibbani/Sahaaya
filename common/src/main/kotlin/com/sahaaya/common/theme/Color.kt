package com.sahaaya.common.theme

import androidx.compose.ui.graphics.Color

/**
 * Sahaaya's palette.
 *
 * Chosen for a care product used by older adults: a calm teal as the primary
 * (trust, clinical without being cold), a deep navy for structure, and a
 * high-contrast red reserved exclusively for emergency affordances so that
 * "red" never means anything but "urgent" anywhere in the app.
 *
 * Every foreground/background pair below meets WCAG AA (4.5:1) for body text.
 */

// --- Brand ------------------------------------------------------------------
internal val TealPrimary = Color(0xFF0E7C7B)
internal val TealPrimaryDark = Color(0xFF6FD9D6)
internal val TealContainer = Color(0xFFB8ECEA)
internal val TealContainerDark = Color(0xFF00504F)
internal val OnTealContainer = Color(0xFF00201F)
internal val OnTealContainerDark = Color(0xFFB8ECEA)

internal val NavySecondary = Color(0xFF1F3864)
internal val NavySecondaryDark = Color(0xFFAFC6F5)
internal val NavyContainer = Color(0xFFD9E2FF)
internal val NavyContainerDark = Color(0xFF1B3C74)
internal val OnNavyContainer = Color(0xFF001947)
internal val OnNavyContainerDark = Color(0xFFD9E2FF)

internal val AmberTertiary = Color(0xFF7A5900)
internal val AmberTertiaryDark = Color(0xFFF3BF48)
internal val AmberContainer = Color(0xFFFFDF9B)
internal val AmberContainerDark = Color(0xFF5C4200)
internal val OnAmberContainer = Color(0xFF261A00)
internal val OnAmberContainerDark = Color(0xFFFFDF9B)

// --- Emergency (error role) -------------------------------------------------
internal val EmergencyRed = Color(0xFFB3261E)
internal val EmergencyRedDark = Color(0xFFFFB4AB)
internal val EmergencyContainer = Color(0xFFF9DEDC)
internal val EmergencyContainerDark = Color(0xFF8C1D18)
internal val OnEmergencyContainer = Color(0xFF410E0B)
internal val OnEmergencyContainerDark = Color(0xFFF9DEDC)

// --- Neutrals ---------------------------------------------------------------
internal val SurfaceLight = Color(0xFFFCFDFC)
internal val SurfaceDark = Color(0xFF0E1414)
internal val SurfaceContainerLight = Color(0xFFEFF3F2)
internal val SurfaceContainerDark = Color(0xFF1A2120)
internal val OnSurfaceLight = Color(0xFF191C1C)
internal val OnSurfaceDark = Color(0xFFE0E3E2)
internal val OnSurfaceVariantLight = Color(0xFF3F4948)
internal val OnSurfaceVariantDark = Color(0xFFBEC9C8)
internal val OutlineLight = Color(0xFF6F7978)
internal val OutlineDark = Color(0xFF899392)

/**
 * Semantic colours that Material 3 has no role for, exposed through
 * [com.sahaaya.common.theme.SahaayaTheme] as [SahaayaStatusColors].
 */
internal val StatusOkLight = Color(0xFF1B6B3A)
internal val StatusOkDark = Color(0xFF7EDA9C)
internal val StatusOkContainerLight = Color(0xFFC6F0D3)
internal val StatusOkContainerDark = Color(0xFF00391C)

internal val StatusWarnLight = Color(0xFF8A5100)
internal val StatusWarnDark = Color(0xFFFFB868)
internal val StatusWarnContainerLight = Color(0xFFFFDCBE)
internal val StatusWarnContainerDark = Color(0xFF663D00)
