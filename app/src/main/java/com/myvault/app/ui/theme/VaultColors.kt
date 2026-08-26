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
    bg = Color(0xFF171A20),
    surface = Color(0xFF22262E),
    elevated = Color(0xFF1D2129),
    inset = Color(0xFF262B35),
    border = Color(0xFF303641),
    borderStrong = Color(0xFF3A414E),
    text = Color(0xFFEDF1F7),
    textSecondary = Color(0xFF9FA9B9),
    textMuted = Color(0xFF737D8E),
    accent = Color(0xFF4F88E6),
    accentSoft = Color(0x1F4F88E6),
    accentBorder = Color(0x474F88E6),
    warning = Color(0xFFD4A24C),
    warningSoft = Color(0x1AD4A24C),
    success = Color(0xFF6FB78A),
    scrim = Color(0x8C000000),
)

val LightVaultColors = VaultColors(
    bg = Color(0xFFF7F9FD),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFF2F6FB),
    inset = Color(0xFFEDF4FD),
    border = Color(0xFFDFE7F1),
    borderStrong = Color(0xFFD1DCE9),
    text = Color(0xFF172238),
    textSecondary = Color(0xFF718097),
    textMuted = Color(0xFF9AA7B8),
    accent = Color(0xFF4F88E6),
    accentSoft = Color(0x144F88E6),
    accentBorder = Color(0x384F88E6),
    warning = Color(0xFFB47A1F),
    warningSoft = Color(0x14B47A1F),
    success = Color(0xFF3F8C5C),
    scrim = Color(0x4014161C),
)

val OledVaultColors = VaultColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF0C0D10),
    elevated = Color(0xFF090A0D),
    inset = Color(0xFF14161B),
    border = Color(0xFF202329),
    borderStrong = Color(0xFF292D35),
    text = Color(0xFFF5F6F8),
    textSecondary = Color(0xFFA2A8B3),
    textMuted = Color(0xFF707680),
    accent = Color(0xFF4F88E6),
    accentSoft = Color(0x1F4F88E6),
    accentBorder = Color(0x474F88E6),
    warning = Color(0xFFD4A24C),
    warningSoft = Color(0x1AD4A24C),
    success = Color(0xFF6FB78A),
    scrim = Color(0xB3000000),
)

val LocalVaultColors = staticCompositionLocalOf { DarkVaultColors }
