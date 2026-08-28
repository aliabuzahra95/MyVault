package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryFileItem

@Composable
internal fun FrozenDestinationHeader(
    title: String,
    subtitle: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Menu, "Open navigation", Modifier.size(20.dp), tint = colors.textSecondary)
            }
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.W800, color = colors.text)
            Text(subtitle, fontSize = 11.sp, color = colors.textMuted)
        }
    }
}

@Composable
internal fun FrozenDashboardScreen(
    continueFile: LibraryFileItem?,
    recentFiles: List<LibraryFileItem>,
    pinnedNotes: List<VaultNoteCardData>,
    pinnedFiles: List<LibraryFileItem>,
    onMenuClick: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val hasContent = continueFile != null || recentFiles.isNotEmpty() || pinnedNotes.isNotEmpty() || pinnedFiles.isNotEmpty()
    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Dashboard", "Continue where you left off", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            continueFile?.let { file ->
                item {
                    DashboardSection("Continue") {
                        DashboardRow(
                            title = file.name,
                            meta = file.pageIndex?.let { "Continue PDF · Page ${it + 1}" } ?: "Continue PDF",
                            icon = Icons.Rounded.PictureAsPdf,
                            outlined = true,
                            onClick = { onOpenFile(file.id) },
                        )
                    }
                }
            }
            if (recentFiles.isNotEmpty()) {
                item {
                    DashboardSection("Recent", "Recently opened") {
                        Column {
                            recentFiles.take(4).forEach { file ->
                                DashboardRow(file.name, "Library · ${file.kind}", Icons.Rounded.Description) {
                                    onOpenFile(file.id)
                                }
                            }
                        }
                    }
                }
            }
            if (pinnedNotes.isNotEmpty() || pinnedFiles.isNotEmpty()) {
                item {
                    DashboardSection("Pinned", "${pinnedNotes.size + pinnedFiles.size} items") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(pinnedNotes, key = { "note:${it.id}" }) { note ->
                                DashboardPinnedItem(note.title, "Study", onClick = { onOpenNote(note.id) })
                            }
                            items(pinnedFiles, key = { "file:${it.id}" }) { file ->
                                DashboardPinnedItem(file.name, "Library", onClick = { onOpenFile(file.id) })
                            }
                        }
                    }
                }
            }
            if (!hasContent) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.72f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(23.dp), tint = colors.textMuted)
                        Spacer(Modifier.height(7.dp))
                        Text("Nothing to continue yet", fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
                        Text("Recently opened and pinned items will appear here.", fontSize = 10.5.sp, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FrozenFavouritesScreen(
    favourites: List<com.myvault.app.ui.components.VaultTreeItem>,
    onMenuClick: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(modifier.fillMaxSize()) {
        FrozenDestinationHeader("Favourites", "Saved across your workspace", onMenuClick)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen, vertical = 6.dp),
        ) {
            item {
                Text(
                    "Notes you marked as favourites.",
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                )
            }
            if (favourites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.65f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(23.dp), tint = colors.textMuted)
                        Spacer(Modifier.height(7.dp))
                        Text("No favourites", fontSize = 13.sp, fontWeight = FontWeight.W700, color = colors.text)
                        Text("Favourite a note to keep it here.", fontSize = 10.5.sp, color = colors.textMuted)
                    }
                }
            } else {
                items(favourites, key = { it.id }) { item ->
                    DashboardRow(
                        title = item.name,
                        meta = "Study · Note",
                        icon = Icons.Rounded.Star,
                        titleFontSize = 13.sp,
                        metaFontSize = 10.5.sp,
                    ) { onOpenNote(item.id) }
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(label: String, meta: String? = null, content: @Composable () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.W800, color = colors.textMuted)
            meta?.let { Text(it, fontSize = 10.5.sp, color = colors.textMuted) }
        }
        content()
    }
}

@Composable
internal fun DashboardRow(
    title: String,
    meta: String,
    icon: ImageVector,
    outlined: Boolean = false,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    metaFontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (outlined) 61.dp else 52.dp),
        shape = if (outlined) VaultShapes.lg else VaultShapes.md,
        color = if (outlined) colors.surface else Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, colors.border) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = titleFontSize, fontWeight = FontWeight.W700, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = metaFontSize, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = colors.textMuted)
        }
    }
}

@Composable
private fun DashboardPinnedItem(title: String, meta: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 154.dp, height = 58.dp),
        shape = VaultShapes.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PushPin, null, Modifier.size(15.dp), tint = colors.accent)
            Column(Modifier.padding(start = 5.dp)) {
                Text(title, fontSize = 12.2.sp, fontWeight = FontWeight.W700, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = 10.2.sp, color = colors.textMuted)
            }
        }
    }
}
