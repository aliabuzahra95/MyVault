package com.myvault.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultThemeModeCompatibilityTest {
    @Test
    fun legacyValuesMapToSafeFrozenModes() {
        assertEquals(VaultThemeMode.Light, VaultThemeMode.fromStoredValues(null, "light"))
        assertEquals(VaultThemeMode.Dark, VaultThemeMode.fromStoredValues(null, "dark"))
        assertEquals(VaultThemeMode.FollowSystemDark, VaultThemeMode.fromStoredValues(null, "auto"))
    }

    @Test
    fun eachFrozenModeWritesCompatibleLegacyAndExactV2Values() {
        val expected = mapOf(
            VaultThemeMode.Light to ("light" to "light"),
            VaultThemeMode.Dark to ("dark" to "dark"),
            VaultThemeMode.Oled to ("dark" to "oled"),
            VaultThemeMode.FollowSystemDark to ("auto" to "follow_system_dark"),
            VaultThemeMode.FollowSystemOled to ("auto" to "follow_system_oled"),
        )

        expected.forEach { (mode, storedValues) ->
            assertEquals(storedValues.first, mode.legacyStoredValue)
            assertEquals(storedValues.second, mode.v2StoredValue)
        }
    }

    @Test
    fun validV2ValueTakesPrecedenceOverLegacyValue() {
        assertEquals(
            VaultThemeMode.Oled,
            VaultThemeMode.fromStoredValues(themeModeV2 = "oled", legacyTheme = "light"),
        )
        assertEquals(
            VaultThemeMode.FollowSystemOled,
            VaultThemeMode.fromStoredValues(themeModeV2 = "follow_system_oled", legacyTheme = "dark"),
        )
    }

    @Test
    fun missingOrInvalidV2ValueFallsBackToLegacyValue() {
        assertEquals(
            VaultThemeMode.Dark,
            VaultThemeMode.fromStoredValues(themeModeV2 = null, legacyTheme = "dark"),
        )
        assertEquals(
            VaultThemeMode.Light,
            VaultThemeMode.fromStoredValues(themeModeV2 = "future_theme", legacyTheme = "light"),
        )
        assertEquals(
            VaultThemeMode.FollowSystemDark,
            VaultThemeMode.fromStoredValues(themeModeV2 = "future_theme", legacyTheme = "auto"),
        )
    }
}
