package com.myvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun FolderTreeRow(
    item: VaultTreeItem,
    depth: Int,
    expanded: Boolean,
    isChildExpanded: (String) -> Boolean,
    onToggle: (VaultTreeItem) -> Unit,
    onOpenNote: (VaultTreeItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (VaultTreeItem) -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: (String) -> Boolean = { false },
    onSelectionToggle: (VaultTreeItem) -> Unit = {},
    organizeMode: Boolean = false,
    notePreviewLines: Int = 0,
    dashboardFontSizeSp: Float = 14f,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveFolder: (VaultTreeItem, Int) -> Unit = { _, _ -> },
) {
    val isFolder = item.type == VaultTreeItemType.Folder
    val folderChildren = remember(item.children) { item.children.filter { it.type == VaultTreeItemType.Folder } }

    Column(modifier = modifier.fillMaxWidth()) {
        FolderTreeSingleRow(
            item = item,
            depth = depth,
            expanded = expanded,
            onClick = {
                if (organizeMode && isFolder) {
                    Unit
                } else if (selectionMode) {
                    onSelectionToggle(item)
                } else if (isFolder) {
                    onToggle(item)
                } else {
                    onOpenNote(item)
                }
            },
            onLongPress = { if (!organizeMode) onLongPress(item) },
            selectionMode = selectionMode,
            selected = isSelected(item.id),
            organizeMode = organizeMode,
            notePreviewLines = notePreviewLines,
            dashboardFontSizeSp = dashboardFontSizeSp,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMoveUp = { onMoveFolder(item, -1) },
            onMoveDown = { onMoveFolder(item, 1) },
        )

        AnimatedVisibility(
            visible = isFolder && expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 115, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column {
                item.children.forEach { child ->
                    val childFolderIndex = folderChildren.indexOfFirst { it.id == child.id }
                    FolderTreeRow(
                        item = child,
                        depth = depth + 1,
                        expanded = isChildExpanded(child.id),
                        isChildExpanded = isChildExpanded,
                        onToggle = onToggle,
                        onOpenNote = onOpenNote,
                        onLongPress = onLongPress,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onSelectionToggle = onSelectionToggle,
                        organizeMode = organizeMode,
                        notePreviewLines = notePreviewLines,
                        dashboardFontSizeSp = dashboardFontSizeSp,
                        canMoveUp = childFolderIndex > 0,
                        canMoveDown = childFolderIndex in 0 until folderChildren.lastIndex,
                        onMoveFolder = onMoveFolder,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTreeSingleRow(
    item: VaultTreeItem,
    depth: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    organizeMode: Boolean,
    notePreviewLines: Int,
    dashboardFontSizeSp: Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val topLevel = depth == 0
    val isFolder = item.type == VaultTreeItemType.Folder
    val rowShape = if (topLevel) VaultShapes.md else VaultShapes.sm
    val background = if (topLevel && expanded) colors.surface else Color.Transparent
    val borderColor = if (topLevel && expanded) colors.border else Color.Transparent
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "folder-chevron-rotation",
    )

    val interactionSource = remember { MutableInteractionSource() }
    var dragCarry by remember(item.id, organizeMode) { mutableFloatStateOf(0f) }
    val shakeTransition = rememberInfiniteTransition(label = "folder-organise-shake")
    val shakeRotation by shakeTransition.animateFloat(
        initialValue = -0.45f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "folder-organise-rotation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (topLevel) 12.dp else (10 + depth * 14).dp,
                top = if (topLevel) 2.dp else 0.dp,
                end = if (topLevel) 12.dp else 8.dp,
                bottom = if (topLevel) 4.dp else 0.dp,
            )
            .clip(rowShape)
            .graphicsLayer {
                rotationZ = if (organizeMode && isFolder) shakeRotation else 0f
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .then(
                if (organizeMode && isFolder) {
                    Modifier.pointerInput(item.id, canMoveUp, canMoveDown) {
                        detectVerticalDragGestures(
                            onDragCancel = { dragCarry = 0f },
                            onDragEnd = { dragCarry = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragCarry += dragAmount
                                when {
                                    dragCarry <= -36f && canMoveUp -> {
                                        onMoveUp()
                                        dragCarry = 0f
                                    }
                                    dragCarry >= 36f && canMoveDown -> {
                                        onMoveDown()
                                        dragCarry = 0f
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        color = background,
        shape = rowShape,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (topLevel) 12.dp else 10.dp,
                    vertical = if (topLevel) 10.dp else 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(if (topLevel) 16.dp else 14.dp),
                    tint = if (selected) colors.accent else colors.textMuted,
                )
            } else if (isFolder) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (topLevel) 14.dp else 12.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = colors.textMuted,
                )
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }

            Icon(
                imageVector = if (isFolder) Icons.Rounded.Folder else Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(if (topLevel) 16.dp else 13.dp),
                tint = if (topLevel && isFolder) colors.accent else colors.textSecondary,
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = if (topLevel) {
                        MaterialTheme.typography.bodyMedium.copy(fontSize = dashboardFontSizeSp.sp, fontWeight = FontWeight.W600)
                    } else {
                        MaterialTheme.typography.bodySmall.copy(fontSize = (dashboardFontSizeSp - 1.5f).coerceAtLeast(12f).sp, fontWeight = FontWeight.W500)
                    },
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isFolder && notePreviewLines > 0 && item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (dashboardFontSizeSp - 2f).coerceAtLeast(11f).sp),
                        color = colors.textMuted,
                        maxLines = notePreviewLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (item.favourite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = colors.warning,
                )
            }
            if (item.pinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = colors.accent,
                )
            }

            if (organizeMode && isFolder) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Move folder up",
                            modifier = Modifier.size(18.dp),
                            tint = if (canMoveUp) colors.accent else colors.textMuted,
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Move folder down",
                            modifier = Modifier.size(18.dp),
                            tint = if (canMoveDown) colors.accent else colors.textMuted,
                        )
                    }
                }
            } else {
                TreeTrailing(item = item, topLevel = topLevel)
            }
        }
    }
}

@Composable
private fun TreeTrailing(item: VaultTreeItem, topLevel: Boolean) {
    val colors = VaultThemeTokens.colors
    val isFolder = item.type == VaultTreeItemType.Folder

    when {
        isFolder && topLevel -> {
            Surface(
                shape = VaultShapes.pill,
                color = colors.inset,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = item.count.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
        isFolder -> Text(
            text = item.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        item.attachmentCount > 0 || item.tableCount > 0 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.attachmentCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = colors.textMuted,
                    )
                    Text(
                        text = item.attachmentCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }
            if (item.tableCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = colors.textMuted,
                    )
                    Text(
                        text = item.tableCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
        else -> Text(
            text = item.edited?.let { "Edited $it" } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
    }
}

@Preview(name = "FolderTreeRow Light")
@Composable
private fun FolderTreeRowLightPreview() {
    VaultComponentPreview(dark = false) {
        val expanded = remember { mutableStateMapOf("islamic" to true, "aqeedah" to true) }
        FolderTreeRow(
            item = ComponentSamples.tree,
            depth = 0,
            expanded = true,
            isChildExpanded = { expanded[it] == true },
            onToggle = { expanded[it.id] = expanded[it.id] != true },
            onOpenNote = {},
        )
    }
}

@Preview(name = "FolderTreeRow Dark")
@Composable
private fun FolderTreeRowDarkPreview() {
    VaultComponentPreview(dark = true) {
        val expanded = remember { mutableStateMapOf("islamic" to true, "aqeedah" to true) }
        FolderTreeRow(
            item = ComponentSamples.tree,
            depth = 0,
            expanded = true,
            isChildExpanded = { expanded[it] == true },
            onToggle = { expanded[it.id] = expanded[it.id] != true },
            onOpenNote = {},
        )
    }
}
