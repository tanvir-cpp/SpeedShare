package com.example.speedshareandroid.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core Deep Backgrounds (OLED Midnight Palette)
val BgMidnight = Color(0xFF07090E)
val BgCard = Color(0xFF10131B)
val BgCardHover = Color(0xFF181C27)
val BgCardElevated = Color(0xFF1E2333)
val BorderGlass = Color(0xFF262C3F)
val BorderGlassActive = Color(0xFF6366F1)

// Vibrant Cyber Neon Accents
val NeonIndigo = Color(0xFF6366F1)
val NeonViolet = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF00F2FE)
val NeonMint = Color(0xFF00F5D4)
val NeonSky = Color(0xFF38BDF8)
val NeonEmerald = Color(0xFF10B981)
val NeonRose = Color(0xFFF43F5E)
val NeonAmber = Color(0xFFF59E0B)

// Typography
val TextPureWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Beautiful Gradients
val PrimaryGradient = Brush.horizontalGradient(
    listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF00F2FE))
)

val CyanMintGradient = Brush.horizontalGradient(
    listOf(Color(0xFF00F2FE), Color(0xFF00F5D4))
)

val CardGlowGradient = Brush.linearGradient(
    listOf(Color(0xFF1A1F2E), Color(0xFF10131B))
)

val SelectedDeviceGradient = Brush.horizontalGradient(
    listOf(Color(0xFF1E1B4B), Color(0xFF172554))
)

val ActiveTransferGradient = Brush.horizontalGradient(
    listOf(Color(0xFF6366F1), Color(0xFF00F5D4))
)
