package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun BlockMenu(
    items: List<BlockMenuItem>,
    modifier: Modifier = Modifier,
    onItemClick: (BlockMenuItem) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.elevated,
        shape = VaultShapes.xl,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = VaultSpacing.md, vertical = VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(modifier = Modifier.size(width = 40.dp, height = 4.dp), color = colors.borderStrong, shape = VaultShapes.pill) {}
            }
            Text("INSERT BLOCK", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            BlockMenuSection("Text", items.filter { it.type in listOf(EditorBlockType.Paragraph, EditorBlockType.Heading, EditorBlockType.Quote) }, onItemClick)
            BlockMenuSection("Structure", items.filter { it.type in listOf(EditorBlockType.BulletList, EditorBlockType.NumberedList, EditorBlockType.Checklist, EditorBlockType.Divider) }, onItemClick)
            BlockMenuSection("Media", items.filter { it.type in listOf(EditorBlockType.Link, EditorBlockType.Attachment, EditorBlockType.Image) }, onItemClick)
        }
    }
}

@Composable
private fun BlockMenuSection(
    label: String,
    items: List<BlockMenuItem>,
    onItemClick: (BlockMenuItem) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        items.forEach { item ->
            Surface(
                onClick = { onItemClick(item) },
                color = colors.elevated,
                shape = VaultShapes.md,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    Surface(modifier = Modifier.size(34.dp), color = colors.inset, shape = VaultShapes.sm, border = BorderStroke(1.dp, colors.border)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(menuIcon(item.type), contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.accent)
                        }
                    }
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                            color = colors.text,
                        )
                        Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

private fun menuIcon(type: EditorBlockType): ImageVector = when (type) {
    EditorBlockType.Paragraph -> Icons.Rounded.Notes
    EditorBlockType.Heading -> Icons.Rounded.Title
    EditorBlockType.BulletList -> Icons.Rounded.FormatListBulleted
    EditorBlockType.NumberedList -> Icons.Rounded.FormatListNumbered
    EditorBlockType.Checklist -> Icons.Rounded.CheckBox
    EditorBlockType.Quote -> Icons.Rounded.FormatQuote
    EditorBlockType.Divider -> Icons.Rounded.HorizontalRule
    EditorBlockType.Link -> Icons.Rounded.Link
    EditorBlockType.Attachment -> Icons.Rounded.AttachFile
    EditorBlockType.Image -> Icons.Rounded.Image
}

@Preview(name = "BlockMenu Light")
@Composable
private fun BlockMenuLightPreview() {
    VaultComponentPreview(dark = false) {
        BlockMenu(items = ComponentSamples.blockMenuItems)
    }
}

@Preview(name = "BlockMenu Dark")
@Composable
private fun BlockMenuDarkPreview() {
    VaultComponentPreview(dark = true) {
        BlockMenu(items = ComponentSamples.blockMenuItems)
    }
}
