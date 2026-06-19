package com.myvault.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvault.app.ai.home.HomeAiAttachableType
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun VaultAiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    warning: Boolean = false,
    size: Dp = 36.dp,
    iconSize: Dp = 17.dp,
    shape: Shape = CircleShape,
) {
    val colors = VaultThemeTokens.colors
    val background = when {
        accent && enabled -> colors.accent
        warning -> colors.warningSoft
        else -> colors.surface
    }
    val border = when {
        accent && enabled -> colors.accent.copy(alpha = 0.38f)
        warning -> colors.warning.copy(alpha = 0.35f)
        else -> colors.border
    }
    val tint = when {
        !enabled -> colors.textMuted
        accent -> colors.text
        warning -> colors.warning
        else -> colors.textSecondary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = shape,
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}

@Composable
fun <T> VaultAiPillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.inset,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.borderStrong),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val isEnabled = enabled(option)
                Surface(
                    onClick = { onSelected(option) },
                    enabled = isEnabled,
                    color = if (isSelected) colors.accentSoft else Color.Transparent,
                    shape = VaultShapes.pill,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) colors.accentBorder else Color.Transparent,
                    ),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                        color = when {
                            !isEnabled -> colors.textMuted.copy(alpha = 0.55f)
                            isSelected -> colors.accent
                            else -> colors.textMuted
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun VaultAiChip(
    title: String,
    type: HomeAiAttachableType?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.widthIn(max = 240.dp),
        color = colors.accentSoft,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.accentBorder),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = if (onRemove == null) 8.dp else 4.dp, top = 5.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type != null) {
                SourceCapsule(type = type)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRemove != null) {
                VaultAiIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    onClick = onRemove,
                    size = 22.dp,
                    iconSize = 13.dp,
                    shape = CircleShape,
                )
            }
        }
    }
}

@Composable
fun VaultAiSelectableRow(
    title: String,
    subtitle: String?,
    sourceType: HomeAiAttachableType?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = if (selected) colors.accentSoft else colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, if (selected) colors.accentBorder else colors.borderStrong),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sourceType != null) {
                SourceCapsule(type = sourceType)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800),
                    color = when {
                        !enabled -> colors.textMuted
                        selected -> colors.accent
                        else -> colors.text
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.textSecondary else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800),
                    color = if (enabled) colors.accent else colors.textMuted,
                    maxLines = 1,
                )
            }
            if (selected) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp), tint = colors.accent)
            }
        }
    }
}
