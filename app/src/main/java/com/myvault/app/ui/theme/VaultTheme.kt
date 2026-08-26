package com.myvault.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

enum class VaultThemeMode {
    Light,
    Dark,
    Oled,
    FollowSystemDark,
    FollowSystemOled;

    companion object {
        fun fromStoredValues(themeModeV2: String?, legacyTheme: String?): VaultThemeMode =
            fromV2StoredValue(themeModeV2) ?: fromLegacyStoredValue(legacyTheme)

        fun fromLegacyStoredValue(value: String?): VaultThemeMode = when (value) {
            "light" -> Light
            "dark" -> Dark
            else -> FollowSystemDark
        }

        fun fromStoredValue(value: String?): VaultThemeMode = fromLegacyStoredValue(value)

        private fun fromV2StoredValue(value: String?): VaultThemeMode? = when (value) {
            "light" -> Light
            "dark" -> Dark
            "oled" -> Oled
            "follow_system_dark" -> FollowSystemDark
            "follow_system_oled" -> FollowSystemOled
            else -> null
        }
    }

    val legacyStoredValue: String
        get() = when (this) {
            Light -> "light"
            Dark, Oled -> "dark"
            FollowSystemDark, FollowSystemOled -> "auto"
        }

    val v2StoredValue: String
        get() = when (this) {
            Light -> "light"
            Dark -> "dark"
            Oled -> "oled"
            FollowSystemDark -> "follow_system_dark"
            FollowSystemOled -> "follow_system_oled"
        }

    val storedValue: String
        get() = legacyStoredValue

    fun quickToggle(): VaultThemeMode = if (isDarkForQuickToggle) Light else Dark

    private val isDarkForQuickToggle: Boolean
        get() = this != Light
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

private val OledMaterialColors = darkColorScheme(
    primary = OledVaultColors.accent,
    onPrimary = Color.White,
    background = OledVaultColors.bg,
    onBackground = OledVaultColors.text,
    surface = OledVaultColors.surface,
    onSurface = OledVaultColors.text,
    surfaceVariant = OledVaultColors.elevated,
    onSurfaceVariant = OledVaultColors.textSecondary,
    outline = OledVaultColors.border,
)

@Composable
fun VaultTheme(
    mode: VaultThemeMode = VaultThemeMode.FollowSystemDark,
    accentColorHex: String = "#4F88E6",
    materialYouEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useOled = when (mode) {
        VaultThemeMode.Oled -> true
        VaultThemeMode.FollowSystemOled -> systemDark
        else -> false
    }
    val useDark = when (mode) {
        VaultThemeMode.Light -> false
        VaultThemeMode.Dark, VaultThemeMode.Oled -> true
        VaultThemeMode.FollowSystemDark, VaultThemeMode.FollowSystemOled -> systemDark
    }
    val savedAccent = accentColorHex.toComposeColorOrNull()
    val context = LocalContext.current
    val dynamicAccent = if (materialYouEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        null
    }
    val baseVaultColors = when {
        useOled -> OledVaultColors
        useDark -> DarkVaultColors
        else -> LightVaultColors
    }
    val vaultColors = (dynamicAccent ?: savedAccent)?.let { baseVaultColors.withAccent(it) } ?: baseVaultColors
    val baseMaterialColors = when {
        useOled -> OledMaterialColors
        useDark -> DarkMaterialColors
        else -> LightMaterialColors
    }
    val materialColors = baseMaterialColors.copy(
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
