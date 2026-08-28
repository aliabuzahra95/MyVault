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
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.key
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
    onOpenFolder: (VaultTreeItem) -> Unit = {},
    onOpenNote: (VaultTreeItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (VaultTreeItem) -> Unit = {},
    onCreateInside: ((VaultTreeItem) -> Unit)? = null,
    onMore: ((VaultTreeItem) -> Unit)? = null,
    selectionMode: Boolean = false,
    isSelected: (String) -> Boolean = { false },
    onSelectionToggle: (VaultTreeItem) -> Unit = {},
    organizeMode: Boolean = false,
    organizeAllItems: Boolean = false,
    notePreviewLines: Int = 0,
    showFullNoteTitles: Boolean = false,
    dashboardFontSizeSp: Float = 14f,
    indentHierarchy: Boolean = true,
    flatHierarchy: Boolean = false,
    showLeafEditedTime: Boolean = true,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveFolder: (VaultTreeItem, Int) -> Unit = { _, _ -> },
) {
    val isFolder = item.type == VaultTreeItemType.Folder
    val childItems = remember(item.children, organizeAllItems) {
        if (organizeAllItems) item.children else item.children.filter { it.type == VaultTreeItemType.Folder }
    }
    val movable = isFolder || organizeAllItems

    Column(modifier = modifier.fillMaxWidth()) {
        FolderTreeSingleRow(
            item = item,
            depth = depth,
            expanded = expanded,
            onClick = {
                if (organizeMode && movable) {
                    Unit
                } else if (selectionMode) {
                    onSelectionToggle(item)
                } else if (isFolder) {
                    onOpenFolder(item)
                } else {
                    onOpenNote(item)
                }
            },
            onToggleClick = { onToggle(item) },
            onLongPress = { if (!organizeMode) onLongPress(item) },
            onCreateInside = onCreateInside,
            onMore = onMore,
            selectionMode = selectionMode,
            selected = isSelected(item.id),
            organizeMode = organizeMode,
            organizeAllItems = organizeAllItems,
            notePreviewLines = notePreviewLines,
            showFullNoteTitles = showFullNoteTitles,
            dashboardFontSizeSp = dashboardFontSizeSp,
            indentHierarchy = indentHierarchy,
            flatHierarchy = flatHierarchy,
            showLeafEditedTime = showLeafEditedTime,
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
                    key(child.id) {
                        val childIndex = childItems.indexOfFirst { it.id == child.id }
                        FolderTreeRow(
                            item = child,
                            depth = depth + 1,
                            expanded = isChildExpanded(child.id),
                            isChildExpanded = isChildExpanded,
                            onToggle = onToggle,
                            onOpenFolder = onOpenFolder,
                            onOpenNote = onOpenNote,
                            onLongPress = onLongPress,
                            onCreateInside = onCreateInside,
                            onMore = onMore,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onSelectionToggle = onSelectionToggle,
                            organizeMode = organizeMode,
                            organizeAllItems = organizeAllItems,
                            notePreviewLines = notePreviewLines,
                            showFullNoteTitles = showFullNoteTitles,
                            dashboardFontSizeSp = dashboardFontSizeSp,
                            indentHierarchy = indentHierarchy,
                            flatHierarchy = flatHierarchy,
                            showLeafEditedTime = showLeafEditedTime,
                            canMoveUp = childIndex > 0,
                            canMoveDown = childIndex in 0 until childItems.lastIndex,
                            onMoveFolder = onMoveFolder,
                        )
                    }
                }
            }
        }

        if (!isFolder && item.children.isNotEmpty()) {
            Column {
                item.children.forEach { child ->
                    key(child.id) {
                        val childIndex = item.children.indexOfFirst { it.id == child.id }
                        FolderTreeRow(
                            item = child,
                            depth = depth + 1,
                            expanded = false,
                            isChildExpanded = isChildExpanded,
                            onToggle = onToggle,
                            onOpenFolder = onOpenFolder,
                            onOpenNote = onOpenNote,
                            onLongPress = onLongPress,
                            onCreateInside = onCreateInside,
                            onMore = onMore,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onSelectionToggle = onSelectionToggle,
                            organizeMode = organizeMode,
                            organizeAllItems = organizeAllItems,
                            notePreviewLines = notePreviewLines,
                            showFullNoteTitles = showFullNoteTitles,
                            dashboardFontSizeSp = dashboardFontSizeSp,
                            indentHierarchy = indentHierarchy,
                            flatHierarchy = flatHierarchy,
                            showLeafEditedTime = showLeafEditedTime,
                            canMoveUp = childIndex > 0,
                            canMoveDown = childIndex in 0 until item.children.lastIndex,
                            onMoveFolder = onMoveFolder,
                        )
                    }
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
    onToggleClick: () -> Unit,
    onLongPress: () -> Unit,
    onCreateInside: ((VaultTreeItem) -> Unit)?,
    onMore: ((VaultTreeItem) -> Unit)?,
    selectionMode: Boolean,
    selected: Boolean,
    organizeMode: Boolean,
    organizeAllItems: Boolean,
    notePreviewLines: Int,
    showFullNoteTitles: Boolean,
    dashboardFontSizeSp: Float,
    indentHierarchy: Boolean,
    flatHierarchy: Boolean,
    showLeafEditedTime: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val topLevel = depth == 0
    val isFolder = item.type == VaultTreeItemType.Folder
    val effectiveDepth = depth.coerceAtMost(3)
    val movable = isFolder || organizeAllItems
    val rowVerticalPadding = when {
        flatHierarchy -> 3.dp
        !isFolder && notePreviewLines > 0 && item.preview.isNotBlank() -> 7.dp
        !isFolder -> 6.dp
        isFolder -> 7.dp
        else -> 6.dp
    }
    val rowStartPadding = when {
        !indentHierarchy -> 0.dp
        topLevel -> 0.dp
        else -> (effectiveDepth * 8).dp
    }
    val rowOuterVerticalGap = if (topLevel) 0.5.dp else 0.dp
    val rowHorizontalPadding = when {
        flatHierarchy -> 5.dp
        topLevel -> 8.dp
        else -> 5.dp
    }
    val rowShape = if (topLevel) VaultShapes.md else VaultShapes.sm
    val background = if (!flatHierarchy && topLevel && isFolder) colors.surface else Color.Transparent
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
                start = rowStartPadding,
                top = rowOuterVerticalGap,
                end = 0.dp,
                bottom = rowOuterVerticalGap,
            )
            .heightIn(
                min = when {
                    flatHierarchy -> 38.dp
                    isFolder && topLevel -> 48.dp
                    isFolder -> 44.dp
                    else -> 44.dp
                },
            )
            .clip(rowShape)
            .graphicsLayer {
                rotationZ = if (organizeMode && movable) shakeRotation else 0f
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .then(
                if (organizeMode && movable) {
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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = rowHorizontalPadding,
                    vertical = rowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(if (topLevel) 15.dp else 13.dp),
                    tint = if (selected) colors.accent else colors.textMuted,
                )
            } else if (isFolder && item.children.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (topLevel) 13.dp else 11.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleClick,
                        )
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = colors.textMuted,
                )
            } else if (isFolder) {
                Spacer(modifier = Modifier.width(if (topLevel) 13.dp else 11.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isFolder) {
                Icon(
                    imageVector = if (flatHierarchy && !topLevel) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (topLevel) 16.dp else 14.dp),
                    tint = when {
                        flatHierarchy && topLevel -> colors.text
                        flatHierarchy -> colors.textSecondary
                        topLevel -> colors.accent
                        else -> colors.textSecondary
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 1.5.dp)
                        .background(
                            color = colors.textMuted,
                            shape = VaultShapes.pill,
                        ),
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = item.name,
                    style = when {
                        !isFolder && topLevel -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                        !isFolder -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                        topLevel -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                        else -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                    },
                    color = colors.text,
                    maxLines = if (!isFolder && showFullNoteTitles) Int.MAX_VALUE else 1,
                    overflow = if (!isFolder && showFullNoteTitles) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                if (!isFolder && notePreviewLines > 0 && item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500),
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

            if (organizeMode && movable) {
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
                            contentDescription = "Move item up",
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
                            contentDescription = "Move item down",
                            modifier = Modifier.size(18.dp),
                            tint = if (canMoveDown) colors.accent else colors.textMuted,
                        )
                    }
                }
            } else {
                TreeTrailing(
                    item = item,
                    topLevel = topLevel,
                    flatHierarchy = flatHierarchy,
                    showLeafEditedTime = showLeafEditedTime,
                )
                if (isFolder && onCreateInside != null) {
                    IconButton(
                        onClick = { onCreateInside(item) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add inside ${item.name}",
                            modifier = Modifier.size(17.dp),
                            tint = colors.accent,
                        )
                    }
                }
                if (onMore != null) {
                    IconButton(
                        onClick = { onMore(item) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "More actions for ${item.name}",
                            modifier = Modifier.size(16.dp),
                            tint = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeTrailing(
    item: VaultTreeItem,
    topLevel: Boolean,
    flatHierarchy: Boolean,
    showLeafEditedTime: Boolean,
) {
    val colors = VaultThemeTokens.colors
    val isFolder = item.type == VaultTreeItemType.Folder

    when {
        isFolder && topLevel && !flatHierarchy -> {
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
        !isFolder && showLeafEditedTime -> Text(
            text = item.edited?.compactEditedTime().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        else -> Unit
    }
}

private fun String.compactEditedTime(): String =
    removePrefix("Edited ")
        .removeSuffix(" ago")
        .trim()

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
