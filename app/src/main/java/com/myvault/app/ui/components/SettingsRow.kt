package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = colors.accentSoft,
                shape = VaultShapes.sm,
                border = BorderStroke(1.dp, colors.accentBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.accent)
                }
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                modifier = Modifier.weight(0.9f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(17.dp), tint = colors.textMuted)
        }
    }
}

@Preview(name = "SettingsRow Light")
@Composable
private fun SettingsRowLightPreview() {
    VaultComponentPreview(dark = false) {
        SettingsRow(icon = Icons.Rounded.Lock, label = "Security lock", value = "Off")
    }
}

@Preview(name = "SettingsRow Dark")
@Composable
private fun SettingsRowDarkPreview() {
    VaultComponentPreview(dark = true) {
        SettingsRow(icon = Icons.Rounded.Lock, label = "Security lock", value = "Off")
    }
}
