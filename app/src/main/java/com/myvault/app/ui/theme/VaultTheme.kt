package com.myvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

enum class VaultThemeMode {
    Light,
    Dark,
    Auto;

    companion object {
        fun fromStoredValue(value: String?): VaultThemeMode = when (value) {
            "light" -> Light
            "dark" -> Dark
            else -> Auto
        }
    }

    val storedValue: String
        get() = name.lowercase()
}

private val DarkMaterialColors = darkColorScheme(
    primary = DarkVaultColors.accent,
    onPrimary = Color.White,
    background = DarkVaultColors.bg,
    onBackground = DarkVaultColors.text,
    surface = DarkVaultColors.surface,
    onSurface = DarkVaultColors.text,
    surfaceVariant = DarkVaultColors.elevated,
    onSurfaceVariant = DarkVaultColors.textSecondary,
    outline = DarkVaultColors.border,
)

private val LightMaterialColors = lightColorScheme(
    primary = LightVaultColors.accent,
    onPrimary = Color.White,
    background = LightVaultColors.bg,
    onBackground = LightVaultColors.text,
    surface = LightVaultColors.surface,
    onSurface = LightVaultColors.text,
    surfaceVariant = LightVaultColors.elevated,
    onSurfaceVariant = LightVaultColors.textSecondary,
    outline = LightVaultColors.border,
)

@Composable
fun VaultTheme(
    mode: VaultThemeMode = VaultThemeMode.Auto,
    accentColorHex: String = "#5B8DEF",
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (mode) {
        VaultThemeMode.Light -> false
        VaultThemeMode.Dark -> true
        VaultThemeMode.Auto -> systemDark
    }
    val savedAccent = accentColorHex.toComposeColorOrNull()
    val baseVaultColors = if (useDark) DarkVaultColors else LightVaultColors
    val vaultColors = savedAccent?.let { baseVaultColors.withAccent(it) } ?: baseVaultColors
    val materialColors = (if (useDark) DarkMaterialColors else LightMaterialColors).copy(
        primary = vaultColors.accent,
    )

    CompositionLocalProvider(LocalVaultColors provides vaultColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = VaultTypography,
            content = content,
        )
    }
}

private fun String.toComposeColorOrNull(): Color? =
    runCatching { Color(toColorInt()) }.getOrNull()

private fun VaultColors.withAccent(accent: Color): VaultColors =
    copy(
        accent = accent,
        accentSoft = accent.copy(alpha = 0.12f),
        accentBorder = accent.copy(alpha = 0.28f),
    )

object VaultThemeTokens {
    val colors: VaultColors
        @Composable get() = LocalVaultColors.current
}
