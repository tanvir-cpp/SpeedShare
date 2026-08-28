package com.example.speedshareandroid.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = TextPrimary,
    primaryContainer = DeepNavy,
    onPrimaryContainer = AccentSky,
    secondary = AccentSky,
    onSecondary = TextPrimary,
    background = BgDark,
    onBackground = TextPrimary,
    surface = CardDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDarkHover,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark
)

@Composable
fun SpeedShareAndroidTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
