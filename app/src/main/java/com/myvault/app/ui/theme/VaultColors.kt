package com.myvault.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VaultColors(
    val bg: Color,
    val surface: Color,
    val elevated: Color,
    val inset: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val warning: Color,
    val warningSoft: Color,
    val success: Color,
    val scrim: Color,
)

val DarkVaultColors = VaultColors(
    bg = Color(0xFF0A0A0B),
    surface = Color(0xFF141416),
    elevated = Color(0xFF1A1A1D),
    inset = Color(0xFF101012),
    border = Color(0xFF26262A),
    borderStrong = Color(0xFF2E2E33),
    text = Color(0xFFF2F2F0),
    textSecondary = Color(0xFFA3A3A8),
    textMuted = Color(0xFF65656B),
    accent = Color(0xFF5B8DEF),
    accentSoft = Color(0x1F5B8DEF),
    accentBorder = Color(0x475B8DEF),
    warning = Color(0xFFD4A24C),
    warningSoft = Color(0x1AD4A24C),
    success = Color(0xFF6FB78A),
    scrim = Color(0x8C000000),
)

val LightVaultColors = VaultColors(
    bg = Color(0xFFF2F3F5),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    inset = Color(0xFFF7F8FA),
    border = Color(0xFFE4E6EA),
    borderStrong = Color(0xFFD6D9DE),
    text = Color(0xFF15171C),
    textSecondary = Color(0xFF5C606A),
    textMuted = Color(0xFF8A8E97),
    accent = Color(0xFF2F6BD8),
    accentSoft = Color(0x142F6BD8),
    accentBorder = Color(0x382F6BD8),
    warning = Color(0xFFB47A1F),
    warningSoft = Color(0x14B47A1F),
    success = Color(0xFF3F8C5C),
    scrim = Color(0x4014161C),
)

val LocalVaultColors = staticCompositionLocalOf { DarkVaultColors }
