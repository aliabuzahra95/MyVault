package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun BlockRenderer(
    block: EditorBlock,
    modifier: Modifier = Modifier,
) {
    when (block.type) {
        EditorBlockType.Heading -> HeadingBlock(block.primary, modifier)
        EditorBlockType.Paragraph -> ParagraphBlock(block.primary, modifier)
        EditorBlockType.Quote -> QuoteBlock(block.primary, modifier)
        EditorBlockType.Checklist -> ChecklistBlock(block.primary, block.checked, modifier)
        EditorBlockType.Divider -> DividerBlock(modifier)
        EditorBlockType.Attachment -> AttachmentBlock(block.primary, block.secondary, modifier)
        EditorBlockType.Image -> ImageBlock(block.primary, block.secondary, block.tint, modifier)
        EditorBlockType.BulletList -> ListBlock("•", block.primary, modifier)
        EditorBlockType.NumberedList -> ListBlock("1.", block.primary, modifier)
        EditorBlockType.Link -> LinkBlock(block.primary, modifier)
    }
}

@Composable
private fun ListBlock(marker: String, text: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.text)
    }
}

@Composable
private fun LinkBlock(text: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.accentSoft,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.accentBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.accent)
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.accent)
        }
    }
}

@Composable
private fun HeadingBlock(text: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = colors.text,
    )
}

@Composable
private fun ParagraphBlock(text: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textSecondary,
    )
}

@Composable
private fun QuoteBlock(text: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(VaultShapes.sm)
            .background(colors.accentSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .background(colors.accent),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ChecklistBlock(text: String, checked: Boolean, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(VaultShapes.sm)
                .background(if (checked) colors.accent else Color.Transparent)
                .border(1.5.dp, if (checked) colors.accent else colors.borderStrong, VaultShapes.sm),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (checked) colors.textMuted else colors.text,
        )
    }
}

@Composable
private fun DividerBlock(modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.border),
    )
}

@Composable
private fun AttachmentBlock(name: String, meta: String, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Surface(modifier = Modifier.size(34.dp), color = colors.inset, shape = VaultShapes.sm) {
                Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.warning)
            }
            Column {
                Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text)
                Text(text = meta, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun ImageBlock(title: String, caption: String, tint: Color?, modifier: Modifier) {
    val colors = VaultThemeTokens.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
        Image(
            painter = ColorPainter(tint ?: colors.success),
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(VaultShapes.md),
        )
        Text(text = caption, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Preview(name = "BlockRenderer Light")
@Composable
private fun BlockRendererLightPreview() {
    VaultComponentPreview(dark = false) {
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
            ComponentSamples.blocks.forEach { BlockRenderer(it) }
        }
    }
}

@Preview(name = "BlockRenderer Dark")
@Composable
private fun BlockRendererDarkPreview() {
    VaultComponentPreview(dark = true) {
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
            ComponentSamples.blocks.forEach { BlockRenderer(it) }
        }
    }
}
