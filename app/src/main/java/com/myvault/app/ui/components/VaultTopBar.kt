package com.myvault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.BorderStroke
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultShapes
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
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = true },
        modifier = modifier,
        shape = VaultShapes.pill,
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Switch workspace",
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (option == selectedLabel) FontWeight.W800 else FontWeight.W600,
                            ),
                            color = if (option == selectedLabel) colors.accent else colors.text,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
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
