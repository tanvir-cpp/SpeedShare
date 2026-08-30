package com.example.speedshareandroid.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextPureWhite,
    primaryContainer = PrimaryIndigoContainer,
    onPrimaryContainer = PrimaryIndigoLight,
    secondary = AccentSky,
    onSecondary = TextPureWhite,
    secondaryContainer = AccentSkyContainer,
    onSecondaryContainer = AccentSky,
    tertiary = AccentMint,
    onTertiary = TextPureWhite,
    background = SurfaceSlate950,
    onBackground = TextPrimary,
    surface = SurfaceSlate900,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSlate850,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = SurfaceSlate950,
    surfaceContainerLow = SurfaceSlate900,
    surfaceContainer = SurfaceSlate900,
    surfaceContainerHigh = SurfaceSlate850,
    surfaceContainerHighest = SurfaceSlate800,
    outline = SurfaceSlate700,
    outlineVariant = BorderGlass,
    error = StatusError,
    onError = TextPureWhite,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusError
)

@Composable
fun SpeedShareAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = SurfaceSlate950.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = SurfaceSlate950.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

