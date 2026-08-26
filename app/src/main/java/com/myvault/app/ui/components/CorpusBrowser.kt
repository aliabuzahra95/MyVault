package com.myvault.app.ui.components

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
                    fontWeight = FontWeight.W800,
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
                icon = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                description = if (searchOpen) "Close search" else "Search $title",
                onClick = if (searchOpen) onSearchClose else onSearchOpen,
            )
        }
        if (searchOpen) {
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = searchPlaceholder,
                query = searchQuery,
                active = true,
                requestFocus = true,
                onQueryChange = onSearchQueryChange,
            )
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
        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
            Text(
                text = "Pinned",
                modifier = Modifier.padding(start = 6.dp),
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
            )
        }
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
            .heightIn(min = 45.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.surface,
        shape = VaultShapes.sm,
    ) {
        Text(
            text = item.title,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            color = colors.text,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.W700,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
        Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(18.dp), tint = colors.textSecondary)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp, end = 8.dp),
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.W700,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            color = colors.textMuted,
            fontSize = 11.5.sp,
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
            .padding(start = 24.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = colors.textMuted)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp, end = 7.dp),
            color = colors.text,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.W500,
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
            Icon(Icons.Rounded.Add, "Add", modifier = Modifier.size(24.dp))
        }
    }
}

data class CorpusAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val selected: Boolean = false,
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
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.W800,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            groups.forEachIndexed { index, group ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                group.label?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 3.dp),
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W800,
                    )
                }
                group.actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = action.onClick)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (action.destructive) Color(0xFFE35D64) else if (action.selected) colors.accent else colors.textSecondary
                        Icon(action.icon, null, modifier = Modifier.size(19.dp), tint = tint)
                        Text(
                            text = action.label,
                            modifier = Modifier.padding(start = 13.dp),
                            color = if (action.destructive) Color(0xFFE35D64) else colors.text,
                            fontSize = 14.sp,
                            fontWeight = if (action.selected) FontWeight.W700 else FontWeight.W500,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
