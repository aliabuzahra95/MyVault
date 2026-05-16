package com.myvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun VaultComponentPreview(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    VaultTheme(mode = if (dark) VaultThemeMode.Dark else VaultThemeMode.Light) {
        val colors = VaultThemeTokens.colors
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(VaultSpacing.screen),
        ) {
            content()
        }
    }
}
