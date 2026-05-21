package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun EditorToolbar(
    modifier: Modifier = Modifier,
    tools: List<EditorTool> = EditorTool.entries,
    activeTools: Set<EditorTool> = emptySet(),
    onToolClick: (EditorTool) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.elevated,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xxs),
            ) {
                tools.forEach { tool ->
                    ToolbarIcon(tool = tool, active = tool in activeTools, onClick = { onToolClick(tool) })
                }
            }
        }
    }
}

@Composable
private fun ToolbarIcon(tool: EditorTool, active: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 32.dp),
        color = if (active) colors.accentSoft else Color.Transparent,
        contentColor = if (active) colors.accent else colors.textSecondary,
        shape = VaultShapes.sm,
        border = if (active) BorderStroke(1.dp, colors.accentBorder) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (tool.label.length <= 2) 8.dp else 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(tool.icon, contentDescription = tool.label, modifier = Modifier.size(15.dp))
            if (tool.showLabel) {
                Text(tool.label, fontWeight = FontWeight.W600)
            }
        }
    }
}

enum class EditorTool(
    val label: String,
    val icon: ImageVector,
    val showLabel: Boolean = false,
) {
    Heading("H1", Icons.Rounded.Title, true),
    Heading2("H2", Icons.Rounded.Title, true),
    Heading3("H3", Icons.Rounded.Title, true),
    Heading4("H4", Icons.Rounded.Title, true),
    Bold("B", Icons.Rounded.FormatBold),
    Italic("I", Icons.Rounded.FormatItalic),
    Underline("U", Icons.Rounded.FormatUnderlined),
    TextColor("Color", Icons.Rounded.FormatColorText),
    BulletList("Bullets", Icons.AutoMirrored.Rounded.FormatListBulleted),
    NumberedList("Numbers", Icons.Rounded.FormatListNumbered),
    Checklist("Check", Icons.Rounded.CheckBox),
    Table("Table", Icons.Rounded.TableChart),
    Link("Link", Icons.Rounded.Link),
    Attachment("File", Icons.Rounded.AttachFile),
    Image("Image", Icons.Rounded.Image),
}

@Preview(name = "EditorToolbar Light")
@Composable
private fun EditorToolbarLightPreview() {
    VaultComponentPreview(dark = false) {
        EditorToolbar()
    }
}

@Preview(name = "EditorToolbar Dark")
@Composable
private fun EditorToolbarDarkPreview() {
    VaultComponentPreview(dark = true) {
        EditorToolbar()
    }
}
