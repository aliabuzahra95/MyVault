package com.myvault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun VaultTopBar(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = VaultSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = colors.text,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing()
        }
    }
}

@Preview(name = "VaultTopBar Light")
@Composable
private fun VaultTopBarLightPreview() {
    VaultComponentPreview(dark = false) {
        VaultTopBar(title = "My Vault") {
            IconBtn(Icons.Rounded.WbSunny, "Theme", active = true)
            IconBtn(Icons.Rounded.Settings, "Settings")
        }
    }
}

@Preview(name = "VaultTopBar Dark")
@Composable
private fun VaultTopBarDarkPreview() {
    VaultComponentPreview(dark = true) {
        VaultTopBar(title = "My Vault") {
            IconBtn(Icons.Rounded.WbSunny, "Theme", active = true)
            IconBtn(Icons.Rounded.Settings, "Settings")
        }
    }
}
