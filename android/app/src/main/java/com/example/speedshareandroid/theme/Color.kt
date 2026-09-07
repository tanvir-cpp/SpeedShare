package com.example.speedshareandroid.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// SpeedShare 2.0 "Prism" Design Tokens
// Adaptive light + dark. Brand = Electric Blue -> Cyan.
// ============================================================================

// ---- Brand (identical hues in both modes) ---------------------------------
val BrandBlue = Color(0xFF2563EB)      // Electric Blue 600
val BrandBlueDark = Color(0xFF3B82F6)  // Blue 500 (dark-mode primary)
val BrandBlueLight = Color(0xFF60A5FA) // Blue 400 (dark-mode accents / light-mode container)
val BrandCyan = Color(0xFF06B6D4)      // Cyan 500
val BrandCyanLight = Color(0xFF22D3EE) // Cyan 400

// ---- Semantic accents (shared hues; containers tint per mode) --------------
val AccentEmerald = Color(0xFF10B981)  // Success / received / complete
val AccentBlue = Color(0xFF3B82F6)     // Outbound / send
val AccentCyanAccent = Color(0xFF06B6D4) // Info / speed / live
val AccentAmber = Color(0xFFF59E0B)    // Warning
val AccentRose = Color(0xFFF43F5E)     // Error / cancel

// ---- File-category accents --------------------------------------------------
object CategoryColors {
    val Image = Color(0xFF3B82F6)
    val Video = Color(0xFFEC4899)
    val Audio = Color(0xFFF59E0B)
    val Document = Color(0xFF06B6D4)
    val Archive = Color(0xFF10B981)
    val Code = Color(0xFF8B5CF6)
    val App = Color(0xFF6366F1)
    val File = Color(0xFF64748B)

    fun forCategory(category: String): Color = when (category) {
        "VIDEO" -> Video
        "IMAGE" -> Image
        "AUDIO" -> Audio
        "ARCHIVE" -> Archive
        "DOCUMENT" -> Document
        "APP" -> App
        "CODE" -> Code
        else -> File
    }
}

// ---- Shared palette contract -------------------------------------------------
interface Palette {
    val Background: Color
    val Surface: Color
    val SurfaceRaised: Color
    val SurfaceContainerLow: Color
    val SurfaceContainerHigh: Color
    val Border: Color
    val BorderStrong: Color
    val Primary: Color
    val OnPrimary: Color
    val PrimaryContainer: Color
    val OnPrimaryContainer: Color
    val TextPrimary: Color
    val TextSecondary: Color
    val TextMuted: Color
    val TextDisabled: Color
    val SuccessContainer: Color
    val OnSuccessContainer: Color
    val WarningContainer: Color
    val OnWarningContainer: Color
    val ErrorContainer: Color
    val OnErrorContainer: Color
    val InfoContainer: Color
    val OnInfoContainer: Color
    val Scrim: Color
}

// ---- Light palette -----------------------------------------------------------
object LightColors : Palette {
    override val Background = Color(0xFFF5F7FB)
    override val Surface = Color(0xFFFFFFFF)
    override val SurfaceRaised = Color(0xFFEFF3FA)
    override val SurfaceContainerLow = Color(0xFFF8FAFD)
    override val SurfaceContainerHigh = Color(0xFFFFFFFF)
    override val Border = Color(0xFFD8E0EE)
    override val BorderStrong = Color(0xFFB9C6DC)

    override val Primary = BrandBlue
    override val OnPrimary = Color(0xFFFFFFFF)
    override val PrimaryContainer = Color(0xFFDCE8FE)
    override val OnPrimaryContainer = Color(0xFF1D4ED8)

    override val TextPrimary = Color(0xFF0F172A)
    override val TextSecondary = Color(0xFF475569)
    override val TextMuted = Color(0xFF64748B)
    override val TextDisabled = Color(0xFF94A3B8)

    override val SuccessContainer = Color(0xFFD1FAE5)
    override val OnSuccessContainer = Color(0xFF047857)
    override val WarningContainer = Color(0xFFFEF3C7)
    override val OnWarningContainer = Color(0xFFB45309)
    override val ErrorContainer = Color(0xFFFFE4E6)
    override val OnErrorContainer = Color(0xFFBE123C)
    override val InfoContainer = Color(0xFFCFFAFE)
    override val OnInfoContainer = Color(0xFF0E7490)

    override val Scrim = Color(0x660B1220)
}

// ---- Dark palette -------------------------------------------------------------
object DarkColors : Palette {
    override val Background = Color(0xFF0B1220)
    override val Surface = Color(0xFF131C2E)
    override val SurfaceRaised = Color(0xFF1B2740)
    override val SurfaceContainerLow = Color(0xFF101826)
    override val SurfaceContainerHigh = Color(0xFF1E2A44)
    override val Border = Color(0xFF26344D)
    override val BorderStrong = Color(0xFF364763)

    override val Primary = BrandBlueLight
    override val OnPrimary = Color(0xFF0B1220)
    override val PrimaryContainer = Color(0xFF1E3A8A)
    override val OnPrimaryContainer = Color(0xFFDBEAFE)

    override val TextPrimary = Color(0xFFF1F5F9)
    override val TextSecondary = Color(0xFF94A3B8)
    override val TextMuted = Color(0xFF64748B)
    override val TextDisabled = Color(0xFF475569)

    override val SuccessContainer = Color(0xFF064E3B)
    override val OnSuccessContainer = Color(0xFF6EE7B7)
    override val WarningContainer = Color(0xFF78350F)
    override val OnWarningContainer = Color(0xFFFCD34D)
    override val ErrorContainer = Color(0xFF4C0519)
    override val OnErrorContainer = Color(0xFFFDA4AF)
    override val InfoContainer = Color(0xFF164E63)
    override val OnInfoContainer = Color(0xFF67E8F9)

    override val Scrim = Color(0x99000000)
}

// ---- Gradient brushes (computed from resolved colors per mode) -------------
data class BrandGradients(
    val primary: Brush,
    val primarySoft: Brush,
    val heroRadial: Brush,
    val cardSurface: Brush
)

fun gradientsFor(
    primary: Color,
    primaryEnd: Color,
    canvas: Color,
    surface: Color,
    surfaceRaised: Color
): BrandGradients = BrandGradients(
    primary = Brush.horizontalGradient(listOf(primary, primaryEnd)),
    primarySoft = Brush.linearGradient(
        listOf(primary.copy(alpha = 0.22f), primaryEnd.copy(alpha = 0.06f))
    ),
    heroRadial = Brush.radialGradient(
        listOf(primary.copy(alpha = 0.18f), Color.Transparent)
    ),
    cardSurface = Brush.verticalGradient(listOf(surfaceRaised, surface))
)
