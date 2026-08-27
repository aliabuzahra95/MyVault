package com.myvault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun NoteWorkspaceHeader(
    breadcrumb: String,
    onMenuClick: () -> Unit,
    onMoreClick: () -> Unit,
    status: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = VaultSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
    ) {
        Surface(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp),
            color = Color.Transparent,
            shape = VaultShapes.sm,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Menu, "Open Explorer", modifier = Modifier.size(20.dp), tint = colors.text)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Note",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
            Text(
                text = status ?: breadcrumb,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = onMoreClick,
            modifier = Modifier.size(40.dp),
            color = Color.Transparent,
            shape = VaultShapes.sm,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, "Note actions", modifier = Modifier.size(20.dp), tint = colors.text)
            }
        }
    }
}

data class NoteSheetAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val subtitle: String? = null,
)

data class NoteSheetSection(
    val label: String,
    val actions: List<NoteSheetAction>,
)

@Composable
fun NoteActionSheet(
    title: String,
    sections: List<NoteSheetSection>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    VaultModal(title = title, onDismiss = onDismiss, modifier = modifier) {
        sections.forEach { section ->
            Text(
                text = section.label.uppercase(),
                modifier = Modifier.padding(top = VaultSpacing.xs, start = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.textMuted,
            )
            Column {
                section.actions.forEach { action ->
                    NoteModalActionRow(action)
                }
            }
        }
    }
}

@Composable
fun NoteModalActionRow(
    action: NoteSheetAction,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = action.onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (action.selected) colors.accentSoft else Color.Transparent,
        shape = VaultShapes.sm,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when {
                    action.destructive -> colors.warning
                    action.selected -> colors.accent
                    else -> colors.textSecondary
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = if (action.destructive) colors.warning else colors.text,
                )
                action.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
    }
}
