package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun WorkspaceHeader(
    modifier: Modifier = Modifier,
    onSortClick: () -> Unit = {},
    onManageClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceLabel()
        Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            WorkspacePill(label = "Sort", showIcon = true, onClick = onSortClick)
            WorkspacePill(label = "Manage", accent = true, onClick = onManageClick)
        }
    }
}

@Composable
private fun WorkspaceLabel() {
    val colors = VaultThemeTokens.colors
    Text(
        text = "WORKSPACE",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textMuted,
    )
}

@Composable
private fun WorkspacePill(
    label: String,
    showIcon: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = VaultShapes.pill,
        color = if (accent) colors.accentSoft else colors.elevated,
        border = BorderStroke(1.dp, if (accent) colors.accentBorder else colors.borderStrong),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Rounded.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = if (accent) colors.accent else colors.textSecondary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (accent) colors.accent else colors.textSecondary,
            )
        }
    }
}

@Preview(name = "WorkspaceHeader Light")
@Composable
private fun WorkspaceHeaderLightPreview() {
    VaultComponentPreview(dark = false) {
        WorkspaceHeader()
    }
}

@Preview(name = "WorkspaceHeader Dark")
@Composable
private fun WorkspaceHeaderDarkPreview() {
    VaultComponentPreview(dark = true) {
        WorkspaceHeader()
    }
}
