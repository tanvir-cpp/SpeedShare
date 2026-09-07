package com.example.speedshareandroid.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Theme mode enum — persisted via SharedPreferences ("theme_mode").
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Non-Material-3 tokens the screens need (semantic accents, category hues,
// scrims) that aren't first-class M3 ColorScheme roles.
@Immutable
data class SpeedShareColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val error: Color,
    val onErrorContainer: Color,
    val errorContainer: Color,
    val info: Color,
    val onInfoContainer: Color,
    val infoContainer: Color,
    val brandGradients: BrandGradients,
    val scrim: Color
)

val LocalSpeedShareColors = staticCompositionLocalOf {
    SpeedShareColors(
        canvas = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceRaised = Color.Unspecified,
        border = Color.Unspecified,
        borderStrong = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textMuted = Color.Unspecified,
        textDisabled = Color.Unspecified,
        success = Color.Unspecified,
        onSuccessContainer = Color.Unspecified,
        successContainer = Color.Unspecified,
        warning = Color.Unspecified,
        onWarningContainer = Color.Unspecified,
        warningContainer = Color.Unspecified,
        error = Color.Unspecified,
        onErrorContainer = Color.Unspecified,
        errorContainer = Color.Unspecified,
        info = Color.Unspecified,
        onInfoContainer = Color.Unspecified,
        infoContainer = Color.Unspecified,
        brandGradients = BrandGradients(
            primary = androidx.compose.ui.graphics.SolidColor(Color.Unspecified),
            primarySoft = androidx.compose.ui.graphics.SolidColor(Color.Unspecified),
            heroRadial = androidx.compose.ui.graphics.SolidColor(Color.Unspecified),
            cardSurface = androidx.compose.ui.graphics.SolidColor(Color.Unspecified)
        ),
        scrim = Color.Unspecified
    )
}

/** Read semantic colors that aren't M3 roles: `val colors = LocalSpeedShareColors.current`. */
object SpeedShareTheme {
    val colors: SpeedShareColors
        @Composable get() = LocalSpeedShareColors.current
}

private fun buildColorScheme(light: Boolean): ColorScheme {
    val p: Palette = if (light) LightColors else DarkColors
    return if (light) {
        lightColorScheme(
            primary = p.Primary,
            onPrimary = p.OnPrimary,
            primaryContainer = p.PrimaryContainer,
            onPrimaryContainer = p.OnPrimaryContainer,
            secondary = AccentBlue,
            onSecondary = Color.White,
            secondaryContainer = p.InfoContainer,
            onSecondaryContainer = p.OnInfoContainer,
            tertiary = AccentEmerald,
            onTertiary = Color.White,
            tertiaryContainer = p.SuccessContainer,
            onTertiaryContainer = p.OnSuccessContainer,
            background = p.Background,
            onBackground = p.TextPrimary,
            surface = p.Surface,
            onSurface = p.TextPrimary,
            surfaceVariant = p.SurfaceRaised,
            onSurfaceVariant = p.TextSecondary,
            surfaceContainerLowest = p.Background,
            surfaceContainerLow = p.SurfaceContainerLow,
            surfaceContainer = p.Surface,
            surfaceContainerHigh = p.Surface,
            surfaceContainerHighest = p.SurfaceRaised,
            outline = p.BorderStrong,
            outlineVariant = p.Border,
            error = AccentRose,
            onError = Color.White,
            errorContainer = p.ErrorContainer,
            onErrorContainer = p.OnErrorContainer
        )
    } else {
        darkColorScheme(
            primary = p.Primary,
            onPrimary = p.OnPrimary,
            primaryContainer = p.PrimaryContainer,
            onPrimaryContainer = p.OnPrimaryContainer,
            secondary = AccentBlue,
            onSecondary = Color.White,
            secondaryContainer = p.InfoContainer,
            onSecondaryContainer = p.OnInfoContainer,
            tertiary = AccentEmerald,
            onTertiary = Color.White,
            tertiaryContainer = p.SuccessContainer,
            onTertiaryContainer = p.OnSuccessContainer,
            background = p.Background,
            onBackground = p.TextPrimary,
            surface = p.Surface,
            onSurface = p.TextPrimary,
            surfaceVariant = p.SurfaceRaised,
            onSurfaceVariant = p.TextSecondary,
            surfaceContainerLowest = p.Background,
            surfaceContainerLow = p.SurfaceContainerLow,
            surfaceContainer = p.Surface,
            surfaceContainerHigh = p.SurfaceContainerHigh,
            surfaceContainerHighest = p.SurfaceRaised,
            outline = p.BorderStrong,
            outlineVariant = p.Border,
            error = AccentRose,
            onError = Color.White,
            errorContainer = p.ErrorContainer,
            onErrorContainer = p.OnErrorContainer
        )
    }
}

@Composable
fun SpeedShareAndroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = buildColorScheme(light = !dark)
    val p: Palette = if (dark) DarkColors else LightColors

    val extras = SpeedShareColors(
        canvas = p.Background,
        surface = p.Surface,
        surfaceRaised = p.SurfaceRaised,
        border = p.Border,
        borderStrong = p.BorderStrong,
        textPrimary = p.TextPrimary,
        textSecondary = p.TextSecondary,
        textMuted = p.TextMuted,
        textDisabled = p.TextDisabled,
        success = AccentEmerald,
        onSuccessContainer = p.OnSuccessContainer,
        successContainer = p.SuccessContainer,
        warning = AccentAmber,
        onWarningContainer = p.OnWarningContainer,
        warningContainer = p.WarningContainer,
        error = AccentRose,
        onErrorContainer = p.OnErrorContainer,
        errorContainer = p.ErrorContainer,
        info = AccentCyanAccent,
        onInfoContainer = p.OnInfoContainer,
        infoContainer = p.InfoContainer,
        brandGradients = gradientsFor(
            primary = p.Primary,
            primaryEnd = if (dark) AccentCyanAccent.copy(alpha = 1f) else AccentCyanAccent,
            canvas = p.Background,
            surface = p.Surface,
            surfaceRaised = p.SurfaceRaised
        ),
        scrim = p.Scrim
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = p.Background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = p.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalSpeedShareColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
