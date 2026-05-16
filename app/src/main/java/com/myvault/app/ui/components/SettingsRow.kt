package com.myvault.app.ui.components

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
        color = colors.surface,
        shape = VaultShapes.md,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.accent)
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
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
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
