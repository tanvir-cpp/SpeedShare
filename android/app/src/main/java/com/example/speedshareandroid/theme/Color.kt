package com.example.speedshareandroid.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// SpeedShare Modern Slate & Indigo Design System Tokens
// ============================================================================

// Base Neutral Surface Hierarchy (Tailored Slate/Zinc)
val SurfaceSlate950 = Color(0xFF090D16) // Main screen background
val SurfaceSlate900 = Color(0xFF0F172A) // Cards & list containers
val SurfaceSlate850 = Color(0xFF161F33) // Elevated cards / hovering items
val SurfaceSlate800 = Color(0xFF1E293B) // High elevation / chips / pills
val SurfaceSlate700 = Color(0xFF334155) // Strong borders & separators

// Legacy / Theme compatibility aliases
val BgMidnight = SurfaceSlate950
val BgCard = SurfaceSlate900
val BgCardHover = SurfaceSlate850
val BgCardElevated = SurfaceSlate800
val BorderGlass = Color(0xFF1E293B)
val BorderGlassActive = Color(0xFF6366F1)

// Primary Brand & Accents (Electric Indigo)
val PrimaryIndigo = Color(0xFF6366F1)
val PrimaryIndigoLight = Color(0xFF818CF8)
val PrimaryIndigoDark = Color(0xFF4F46E5)
val PrimaryIndigoContainer = Color(0xFF1E1B4B)
val PrimaryIndigoContainerHigh = Color(0xFF2E2B6B)

// Secondary Accents (Clean Cyan / Sky & Mint)
val AccentSky = Color(0xFF38BDF8)
val AccentSkyContainer = Color(0xFF0C4A6E)
val AccentCyan = Color(0xFF06B6D4)
val AccentMint = Color(0xFF10B981)
val AccentViolet = Color(0xFF8B5CF6)
val AccentAmber = Color(0xFFF59E0B)
val AccentRose = Color(0xFFF43F5E)

// Legacy aliases for components during refactor
val NeonIndigo = PrimaryIndigo
val NeonViolet = AccentViolet
val NeonCyan = AccentCyan
val NeonMint = AccentMint
val NeonSky = AccentSky
val NeonEmerald = AccentMint
val NeonRose = AccentRose
val NeonAmber = AccentAmber

// Typography & Hierarchy
val TextPureWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextDisabled = Color(0xFF475569)

// Semantic Status Colors
val StatusSuccess = Color(0xFF10B981)
val StatusSuccessContainer = Color(0xFF064E3B)
val StatusWarning = Color(0xFFF59E0B)
val StatusWarningContainer = Color(0xFF78350F)
val StatusError = Color(0xFFF43F5E)
val StatusErrorContainer = Color(0xFF4C0519)

// Refined Gradients (Subtle, non-jarring)
val PrimaryGradient = Brush.horizontalGradient(
    listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
)

val PrimaryGlowGradient = Brush.linearGradient(
    listOf(Color(0xFF6366F1).copy(alpha = 0.25f), Color(0xFF4F46E5).copy(alpha = 0.05f))
)

val CardSurfaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF161F33), Color(0xFF0F172A))
)

val HeroRadarGradient = Brush.radialGradient(
    listOf(Color(0xFF6366F1).copy(alpha = 0.15f), Color.Transparent)
)

val ActiveTransferGradient = Brush.horizontalGradient(
    listOf(Color(0xFF6366F1), Color(0xFF10B981))
)

