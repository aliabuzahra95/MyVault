package com.myvault.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun VaultTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
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
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = colors.text,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing()
        }
    }
}

@Composable
fun VaultWorkspaceSwitcher(
    selectedLabel: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val nextWorkspace = remember(selectedLabel, options) {
        options.firstOrNull { it != selectedLabel } ?: selectedLabel
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (nextWorkspace != selectedLabel) onSelected(nextWorkspace)
            }
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900),
            color = colors.text,
        )
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
