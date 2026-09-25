package com.whalert.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette de cybersécurité défensive et confiance
val TealPrimary = Color(0xFF0D9488)
val TealPrimaryDark = Color(0xFF14B8A6)
val NavyBackgroundDark = Color(0xFF0B0F19)
val NavySurfaceDark = Color(0xFF111827)
val NavyCardDark = Color(0xFF1F2937)
val BorderDark = Color(0xFF374151)

val SlateLightBackground = Color(0xFFF8FAFC)
val SlateLightSurface = Color(0xFFFFFFFF)
val SlateLightCard = Color(0xFFF1F5F9)
val BorderLight = Color(0xFFE2E8F0)

val AmberWarning = Color(0xFFF59E0B)
val CrimsonError = Color(0xFFEF4444)
val EmeraldSuccess = Color(0xFF10B981)

val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF042F2E),
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = Color(0xFF64748B),
    background = NavyBackgroundDark,
    surface = NavySurfaceDark,
    surfaceVariant = NavyCardDark,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = CrimsonError,
    outline = BorderDark
)

val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = Color(0xFF475569),
    background = SlateLightBackground,
    surface = SlateLightSurface,
    surfaceVariant = SlateLightCard,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = CrimsonError,
    outline = BorderLight
)
