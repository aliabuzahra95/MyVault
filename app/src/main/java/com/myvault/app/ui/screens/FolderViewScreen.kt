package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.Breadcrumb
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.FolderUiState

@Composable
fun FolderViewScreen(
    uiState: FolderUiState,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNoteClick: (String) -> Unit,
    onFolderClick: (String) -> Unit = {},
    onNewNoteClick: () -> Unit = {},
    onNewSubfolderClick: (String) -> Unit = {},
    notePreviewLines: Int = 0,
) {
    val colors = VaultThemeTokens.colors
    var fabExpanded by remember { mutableStateOf(false) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var noteFilter by remember { mutableStateOf(FolderNoteFilter.All) }
    var noteSort by remember { mutableStateOf(FolderNoteSort.Recent) }
    val visibleNotes = remember(uiState.notes, noteFilter, noteSort) {
        uiState.notes
            .filter { note ->
                when (noteFilter) {
                    FolderNoteFilter.All -> true
                    FolderNoteFilter.Pinned -> note.pinned
                    FolderNoteFilter.Favourites -> note.favourite
                }
            }
            .let { notes ->
                when (noteSort) {
                    FolderNoteSort.Recent -> notes
                    FolderNoteSort.Title -> notes.sortedBy { it.title.lowercase() }
                }
            }
    }
    val createActions = remember {
        listOf(
            FloatingAction("New Note", Icons.Rounded.Description),
            FloatingAction("New Subfolder", Icons.Rounded.CreateNewFolder),
        )
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.bg) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
            ) {
                item {
                    ScreenTopBar(onBackClick = onBackClick) {
                        SearchMoreActions(onSearchClick = onSearchClick)
                    }
                }
                item {
                    Breadcrumb(
                        items = listOf("My Vault", uiState.folder?.name ?: "Folder"),
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                    )
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = VaultSpacing.screen)) {
                        Text(uiState.folder?.name ?: "Folder", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                        Text("${uiState.notes.size} notes · ${uiState.subfolders.size} subfolders", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
                item {
                    FolderControls(
                        filter = noteFilter,
                        sort = noteSort,
                        onFilterChange = { noteFilter = it },
                        onSortChange = { noteSort = it },
                    )
                }
                if (uiState.subfolders.isEmpty() && visibleNotes.isEmpty()) {
                    item {
                        EmptyFolderState()
                    }
                } else {
                    items(uiState.subfolders, key = { it.id }) { folder ->
                        SubfolderCard(folder, onClick = { onFolderClick(folder.id) })
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(VaultSpacing.xs))
                    SectionLabel(label = "Notes")
                }
                if (visibleNotes.isEmpty() && uiState.subfolders.isNotEmpty()) {
                    item {
                        EmptyFolderState(text = if (uiState.notes.isEmpty()) "No notes in this folder yet" else "No notes match this filter")
                    }
                } else {
                    items(visibleNotes, key = { it.id }) { note ->
                        NoteListRow(note = note, notePreviewLines = notePreviewLines, onClick = { onNoteClick(note.id) })
                    }
                }
            }

            if (fabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim),
                )
            }

            FloatingActionMenu(
                expanded = fabExpanded,
                actions = createActions,
                mainButtonSize = 48.dp,
                actionButtonSize = 38.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = VaultSpacing.screen, bottom = VaultSpacing.screen)
                    .size(width = 220.dp, height = 220.dp),
                onToggle = { fabExpanded = !fabExpanded },
                onActionClick = { action ->
                    fabExpanded = false
                    when (action.label) {
                        "New Note" -> onNewNoteClick()
                        "New Subfolder" -> folderDialogOpen = true
                    }
                },
            )
        }
    }

    if (folderDialogOpen) {
        AlertDialog(
            onDismissRequest = { folderDialogOpen = false },
            title = { Text("New subfolder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onNewSubfolderClick(newFolderName)
                        newFolderName = ""
                        folderDialogOpen = false
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyFolderState(
    text: String = "This folder is empty",
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FolderControls(
    filter: FolderNoteFilter,
    sort: FolderNoteSort,
    onFilterChange: (FolderNoteFilter) -> Unit,
    onSortChange: (FolderNoteSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            FilterChip("All", Icons.Rounded.FilterList, selected = filter == FolderNoteFilter.All) {
                onFilterChange(FolderNoteFilter.All)
            }
            FilterChip("Pinned", Icons.Rounded.PushPin, selected = filter == FolderNoteFilter.Pinned) {
                onFilterChange(FolderNoteFilter.Pinned)
            }
            FilterChip("Favourites", Icons.Rounded.Star, selected = filter == FolderNoteFilter.Favourites) {
                onFilterChange(FolderNoteFilter.Favourites)
            }
        }
        Surface(color = VaultThemeTokens.colors.surface, shape = VaultShapes.pill, border = BorderStroke(1.dp, VaultThemeTokens.colors.border)) {
            Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                IconBtn(Icons.Rounded.CalendarToday, "Recent", active = sort == FolderNoteSort.Recent) {
                    onSortChange(FolderNoteSort.Recent)
                }
                IconBtn(Icons.AutoMirrored.Rounded.ViewList, "Title", active = sort == FolderNoteSort.Title) {
                    onSortChange(FolderNoteSort.Title)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        color = if (selected) colors.accentSoft else colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (selected) colors.accentBorder else colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = if (selected) colors.accent else colors.textSecondary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accent else colors.textSecondary)
        }
    }
}

private enum class FolderNoteFilter {
    All,
    Pinned,
    Favourites,
}

private enum class FolderNoteSort {
    Recent,
    Title,
}

@Composable
private fun SubfolderCard(folder: FolderCardSample, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.elevated,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(VaultSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Surface(modifier = Modifier.size(36.dp), color = colors.accentSoft, shape = VaultShapes.sm, border = BorderStroke(1.dp, colors.accentBorder)) {
                Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(18.dp), tint = colors.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text)
                Text(folder.count, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
    }
}

@Composable
private fun NoteListRow(note: FolderNoteSample, notePreviewLines: Int, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 1.5.dp)
                    .background(
                        color = colors.textMuted,
                        shape = VaultShapes.pill,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.title, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (note.pinned) Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(11.dp), tint = colors.accent)
                    if (note.favourite) Icon(Icons.Rounded.Star, null, modifier = Modifier.size(11.dp), tint = colors.warning)
                }
                if (notePreviewLines > 0 && note.preview.isNotBlank()) {
                    Text(note.preview, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = notePreviewLines, overflow = TextOverflow.Ellipsis)
                }
                Text(note.meta, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.MoreHoriz, null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
    }
}
