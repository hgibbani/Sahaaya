package com.sahaaya.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours Material 3 has no role for.
 *
 * Kept out of [androidx.compose.material3.ColorScheme] so that "success" and
 * "warning" stay meaningful names in feature code rather than being smuggled in
 * as tertiary or error.
 */
@Immutable
data class SahaayaStatusColors(
    val ok: Color,
    val onOkContainer: Color,
    val okContainer: Color,
    val warn: Color,
    val onWarnContainer: Color,
    val warnContainer: Color,
)

private val LightStatusColors = SahaayaStatusColors(
    ok = StatusOkLight,
    okContainer = StatusOkContainerLight,
    onOkContainer = StatusOkLight,
    warn = StatusWarnLight,
    warnContainer = StatusWarnContainerLight,
    onWarnContainer = StatusWarnLight,
)

private val DarkStatusColors = SahaayaStatusColors(
    ok = StatusOkDark,
    okContainer = StatusOkContainerDark,
    onOkContainer = StatusOkDark,
    warn = StatusWarnDark,
    warnContainer = StatusWarnContainerDark,
    onWarnContainer = StatusWarnDark,
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = NavySecondary,
    onSecondary = Color.White,
    secondaryContainer = NavyContainer,
    onSecondaryContainer = OnNavyContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyContainer,
    onErrorContainer = OnEmergencyContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003736),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = NavySecondaryDark,
    onSecondary = Color(0xFF002B72),
    secondaryContainer = NavyContainerDark,
    onSecondaryContainer = OnNavyContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = EmergencyRedDark,
    onError = Color(0xFF601410),
    errorContainer = EmergencyContainerDark,
    onErrorContainer = OnEmergencyContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    outline = OutlineDark,
)

/**
 * The single theme wrapper for the whole application.
 *
 * Dynamic colour is deliberately **not** used. The emergency red and the
 * teal/navy pairing carry meaning here; letting the wallpaper repaint them
 * would break the one visual rule the app relies on.
 */
@Composable
fun SahaayaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SahaayaTypography,
            shapes = SahaayaShapes,
            content = content,
        )
    }
}

/** Access point for the semantic status colours: `SahaayaTheme.status.ok`. */
object SahaayaTheme {
    val status: SahaayaStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
