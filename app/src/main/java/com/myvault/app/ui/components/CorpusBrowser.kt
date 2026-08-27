package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun CorpusHeader(
    title: String,
    metadata: String,
    searchOpen: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    reserveNavigationSpace: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (searchOpen) {
            CorpusInlineSearch(
                placeholder = searchPlaceholder,
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchClose,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (reserveNavigationSpace) 48.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata,
                        modifier = Modifier.padding(top = 2.dp),
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CorpusIconButton(
                    icon = Icons.Rounded.Search,
                    description = "Search $title",
                    onClick = onSearchOpen,
                )
            }
        }
    }
}

@Composable
private fun CorpusInlineSearch(
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        color = colors.elevated,
        shape = VaultShapes.lg,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp), tint = colors.textSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 13.5.sp),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) Text(placeholder, color = colors.textMuted, fontSize = 13.5.sp)
                        innerTextField()
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Close search", modifier = Modifier.size(19.dp), tint = colors.text)
            }
        }
    }
}

@Composable
private fun CorpusIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, modifier = Modifier.size(20.dp), tint = colors.textSecondary)
    }
}

data class CorpusPinnedItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

@Composable
fun CorpusPinnedStrip(
    items: List<CorpusPinnedItem>,
    onClick: (String) -> Unit,
    onLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = VaultThemeTokens.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PINNED",
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            color = colors.textSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.85.sp,
        )
        LazyRow(
            contentPadding = PaddingValues(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.id }) { item ->
                CorpusPinnedCard(
                    item = item,
                    onClick = { onClick(item.id) },
                    onLongPress = { onLongPress(item.id) },
                )
            }
        }
    }
}

@Composable
fun CorpusSearchSummary(
    resultLabel: String,
    contextLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(start = 2.dp, top = 4.dp, end = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = resultLabel,
            color = colors.textMuted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.W700,
        )
        Text(
            text = contextLabel,
            color = colors.textMuted,
            fontSize = 9.5.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CorpusPinnedCard(
    item: CorpusPinnedItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .width(153.dp)
            .height(45.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Text(
                text = item.title,
                color = colors.text,
                fontSize = 11.7.sp,
                lineHeight = 13.5.sp,
                fontWeight = FontWeight(650),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.textSecondary,
                    fontSize = 9.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CorpusFolderRow(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            modifier = Modifier
                .size(16.dp)
                .rotate(if (expanded) 90f else 0f),
            tint = colors.textMuted,
        )
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(21.dp), tint = colors.textSecondary)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp, end = 8.dp),
            color = colors.text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight(670),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            color = colors.textMuted,
            fontSize = 12.5.sp,
            maxLines = 1,
        )
        if (onAdd != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add inside $title",
                    modifier = Modifier.size(16.dp),
                    tint = colors.textMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CorpusLeafRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    pinned: Boolean = false,
    attachmentCount: Int = 0,
    showFullTitle: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 35.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(start = 1.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(21.dp), tint = colors.textMuted)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp, end = 7.dp),
            color = colors.text,
            fontSize = 13.2.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.W400,
            maxLines = if (showFullTitle) 4 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (pinned) {
            Icon(Icons.Rounded.PushPin, "Pinned", modifier = Modifier.size(13.dp), tint = colors.textMuted)
            Spacer(Modifier.width(5.dp))
        }
        if (attachmentCount > 0) {
            Text(attachmentCount.toString(), color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
        }
        if (!trailingText.isNullOrBlank()) {
            Text(trailingText, color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun CorpusEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(30.dp), tint = colors.textMuted)
        Text(title, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
fun CorpusFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(54.dp),
        color = colors.accent,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "Add", modifier = Modifier.size(27.dp))
        }
    }
}

data class CorpusAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val description: String? = null,
    val onClick: () -> Unit,
)

data class CorpusActionGroup(
    val label: String? = null,
    val actions: List<CorpusAction>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpusActionSheet(
    title: String,
    groups: List<CorpusActionGroup>,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = colors.elevated,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, VaultShapes.pill),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val headingLabel = groups.firstOrNull()?.label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 1.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    headingLabel?.let { label ->
                        Text(
                            text = if (label == "CREATE") "CREATE IN" else label,
                            color = colors.textMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.W700,
                            letterSpacing = 0.75.sp,
                        )
                    }
                    Text(
                        text = title.removePrefix("Add to "),
                        modifier = Modifier.padding(top = if (headingLabel == null) 0.dp else 1.dp),
                        color = colors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(19.dp), tint = colors.textSecondary)
                }
            }
            groups.forEachIndexed { index, group ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                group.label?.takeUnless { index == 0 }?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 3.dp),
                        color = colors.textMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.75.sp,
                    )
                }
                group.actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = action.onClick)
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (action.destructive) Color(0xFFE35D64) else if (action.selected) colors.accent else colors.textSecondary
                        Icon(action.icon, null, modifier = Modifier.size(20.dp), tint = tint)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = action.label,
                                color = if (action.destructive) Color(0xFFE35D64) else colors.text,
                                fontSize = 13.5.sp,
                                fontWeight = if (action.selected) FontWeight.W700 else FontWeight.W600,
                            )
                            Text(
                                text = action.description ?: action.defaultDescription(title),
                                modifier = Modifier.padding(top = 2.dp),
                                color = colors.textMuted,
                                fontSize = 10.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun CorpusAction.defaultDescription(contextTitle: String): String = when {
    label.equals("New note", true) -> "Add a note to ${contextTitle.removePrefix("Add to ")}"
    label.equals("New folder", true) -> "Organise items in this workspace"
    label.equals("New subfolder", true) -> "Organise items inside this folder"
    label.startsWith("Upload", true) || label.startsWith("Import", true) -> "Choose a file from this device"
    label.equals("Open", true) -> "Open this item"
    label.equals("Rename", true) -> "Change the current title"
    label.startsWith("Move", true) -> "Choose another folder"
    label.equals("Pin", true) -> "Show in the compact Pinned strip"
    label.equals("Unpin", true) -> "Remove from the compact Pinned strip"
    label.equals("Delete", true) -> "Remove this item"
    label.contains("More actions", true) -> "Show additional actions"
    else -> "Open ${label.lowercase()}"
}
