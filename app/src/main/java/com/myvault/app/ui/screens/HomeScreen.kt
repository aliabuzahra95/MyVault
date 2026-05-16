package com.myvault.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FolderTreeRow
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.PinnedNoteCard
import com.myvault.app.ui.components.SearchBar
import com.myvault.app.ui.components.SearchResultCard
import com.myvault.app.ui.components.SearchResultData
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.components.WorkspaceHeader
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.util.openAttachment
import com.myvault.app.ui.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onSettingsClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onNewNoteClick: (folderId: String?) -> Unit = {},
    onNewFolderClick: (parentId: String?, name: String) -> Unit = { _, _ -> },
    onRenameFolderClick: (folderId: String, name: String) -> Unit = { _, _ -> },
    onMoveFolderClick: (folderId: String, parentId: String?) -> Unit = { _, _ -> },
    onMoveFolderInOrderClick: (folderId: String, direction: Int) -> Unit = { _, _ -> },
    onMoveFolderToModeClick: (folderId: String, mode: String) -> Unit = { _, _ -> },
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit = { _, _ -> },
    onDeleteFolderClick: (folderId: String) -> Unit = {},
    onRenameNoteClick: (noteId: String, title: String) -> Unit = { _, _ -> },
    onMoveNoteClick: (noteId: String, folderId: String?) -> Unit = { _, _ -> },
    onDeleteNoteClick: (noteId: String) -> Unit = {},
    onSetNotePinnedClick: (noteId: String, pinned: Boolean) -> Unit = { _, _ -> },
    onSetNoteFavouriteClick: (noteId: String, favourite: Boolean) -> Unit = { _, _ -> },
    onImportFileClick: (Uri) -> Unit = {},
    onAttachmentClick: (String) -> Unit = {},
    onOpenAttachmentsClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    onQuickBackupClick: () -> Unit = {},
    quickBackupRecommended: Boolean = false,
    dashboardFontSizeSp: Float = 14f,
    currentFolderMode: String = FOLDER_MODE_STUDY,
) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current
    val isSearching = uiState.searchQuery.isNotBlank()
    var fabExpanded by remember { mutableStateOf(false) }
    var folderDialogMode by remember { mutableStateOf<FolderDialogMode?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<VaultTreeItem?>(null) }
    var folderActionsOpen by remember { mutableStateOf(false) }
    var moveFolderDialogOpen by remember { mutableStateOf(false) }
    var deleteFolderDialogOpen by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<VaultTreeItem?>(null) }
    var noteActionsOpen by remember { mutableStateOf(false) }
    var renameNoteDialogOpen by remember { mutableStateOf(false) }
    var moveNoteDialogOpen by remember { mutableStateOf(false) }
    var deleteNoteDialogOpen by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var pinnedOverflowOpen by remember { mutableStateOf(false) }
    var quickBackupConfirmOpen by remember { mutableStateOf(false) }
    var manageMenuOpen by remember { mutableStateOf(false) }
    var manageRenameFolderOpen by remember { mutableStateOf(false) }
    var manageMoveFolderOpen by remember { mutableStateOf(false) }
    var manageDeleteFolderOpen by remember { mutableStateOf(false) }
    var managePinnedNotesOpen by remember { mutableStateOf(false) }
    var manageFavouriteNotesOpen by remember { mutableStateOf(false) }
    var manageSelectionMode by remember { mutableStateOf(false) }
    var organizeMode by remember { mutableStateOf(false) }
    var moveSelectedNotesOpen by remember { mutableStateOf(false) }
    var deleteSelectedOpen by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(WorkspaceSortMode.FoldersFirst) }
    val selectedItemIds = remember { mutableStateMapOf<String, Boolean>() }
    val selectedItems = remember(uiState.workspace, selectedItemIds.keys.toList()) {
        uiState.workspace.flatMap { it.flattenItems() }.filter { selectedItemIds[it.id] == true }
    }
    val selectedNotes = selectedItems.filter { it.type == VaultTreeItemType.Note }
    val selectedFolders = selectedItems.filter { it.type == VaultTreeItemType.Folder }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportFileClick)
    }
    val createActions = remember {
        listOf(
            FloatingAction("New Note", Icons.Rounded.Description),
            FloatingAction("New Folder", Icons.Rounded.CreateNewFolder),
            FloatingAction("Import File", Icons.Rounded.AttachFile),
        )
    }
    val sortedWorkspace = remember(uiState.workspace, sortMode) {
        uiState.workspace.sortForMode(sortMode)
    }
    val displayedWorkspace = if (organizeMode) uiState.workspace else sortedWorkspace
    val rootFolders = displayedWorkspace.filter { it.type == VaultTreeItemType.Folder }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                item {
                    VaultTopBar(title = "My Vault") {
                        if (organizeMode) {
                            Surface(
                                onClick = { organizeMode = false },
                                shape = VaultShapes.pill,
                                color = colors.accentSoft,
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Done, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                                    Text(
                                        text = "Done",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                                        color = colors.accent,
                                    )
                                }
                            }
                        } else {
                            IconBtn(
                                icon = Icons.Rounded.WbSunny,
                                contentDescription = "Toggle theme",
                                active = true,
                                onClick = onThemeClick,
                            )
                            IconBtn(
                                icon = Icons.Rounded.Backup,
                                contentDescription = "Quick cloud backup",
                                active = quickBackupRecommended,
                                onClick = { quickBackupConfirmOpen = true },
                            )
                            IconBtn(
                                icon = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                onClick = onSettingsClick,
                            )
                        }
                    }
                }

                item {
                    SearchBar(
                        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                        query = uiState.searchQuery,
                        active = isSearching,
                        onQueryChange = onSearchQueryChange,
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = isSearching,
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it / 8 },
                        ) + expandVertically(
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 10 },
                        ) + shrinkVertically(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ),
                    ) {
                        InlineSearchResults(
                            uiState = uiState,
                            onNoteClick = onNoteClick,
                            onFolderClick = { folderId ->
                                onFolderExpandedChange(folderId, true)
                            },
                            modifier = Modifier.padding(top = VaultSpacing.sm),
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(5.dp))
                    SectionLabel(
                        label = "Pinned",
                        actionLabel = if (uiState.pinnedNotes.isNotEmpty()) "View all" else null,
                        onActionClick = { pinnedOverflowOpen = true },
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (uiState.pinnedNotes.isEmpty()) {
                        EmptyHomeState(
                            icon = Icons.Rounded.PushPin,
                            text = "Pinned notes will appear here",
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                        ) {
                            items(uiState.pinnedNotes) { note ->
                                PinnedNoteCard(note = note, previewLines = uiState.notePreviewLines, onClick = { onNoteClick(note.id) })
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    WorkspaceHeader(
                        onSortClick = { sortMenuOpen = true },
                        onManageClick = { manageMenuOpen = true },
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VaultSpacing.xs),
                    ) {
                        if (displayedWorkspace.isEmpty()) {
                            EmptyHomeState(
                                icon = Icons.Rounded.Folder,
                                text = "Create a folder or note to start your vault",
                                modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            )
                        } else {
                            displayedWorkspace.forEach { item ->
                                val rootFolderIndex = rootFolders.indexOfFirst { it.id == item.id }
                                FolderTreeRow(
                                    item = item,
                                    depth = 0,
                                    notePreviewLines = uiState.notePreviewLines,
                                    dashboardFontSizeSp = dashboardFontSizeSp,
                                    expanded = item.id in uiState.expandedFolderIds,
                                    isChildExpanded = { id -> id in uiState.expandedFolderIds },
                                    onToggle = { folder ->
                                        onFolderExpandedChange(folder.id, folder.id !in uiState.expandedFolderIds)
                                    },
                                    onOpenNote = { note -> onNoteClick(note.id) },
                                    onLongPress = { item ->
                                        if (manageSelectionMode) {
                                            selectedItemIds.toggle(item.id)
                                        } else if (item.type == VaultTreeItemType.Folder) {
                                            selectedFolder = item
                                            folderActionsOpen = true
                                        } else {
                                            selectedNote = item
                                            noteActionsOpen = true
                                        }
                                    },
                                    selectionMode = manageSelectionMode,
                                    isSelected = { id -> selectedItemIds[id] == true },
                                    onSelectionToggle = { item -> selectedItemIds.toggle(item.id) },
                                    organizeMode = organizeMode,
                                    canMoveUp = rootFolderIndex > 0,
                                    canMoveDown = rootFolderIndex in 0 until rootFolders.lastIndex,
                                    onMoveFolder = { folder, direction ->
                                        onMoveFolderInOrderClick(folder.id, direction)
                                    },
                                )
                            }
                        }
                    }
                }

                if (manageSelectionMode) {
                    item {
                        SelectionManageBar(
                            selectedCount = selectedItems.size,
                            selectedNoteCount = selectedNotes.size,
                            onMoveNotes = { moveSelectedNotesOpen = true },
                            onPinNotes = {
                                selectedNotes.forEach { onSetNotePinnedClick(it.id, true) }
                                selectedItemIds.clear()
                            },
                            onFavouriteNotes = {
                                selectedNotes.forEach { onSetNoteFavouriteClick(it.id, true) }
                                selectedItemIds.clear()
                            },
                            onDelete = { deleteSelectedOpen = true },
                            onDone = {
                                selectedItemIds.clear()
                                manageSelectionMode = false
                            },
                            modifier = Modifier.padding(top = VaultSpacing.sm),
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionLabel(label = "Attachments")
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                    ) {
                        item {
                            AllAttachmentsCard(
                                count = uiState.attachments.size,
                                onClick = onOpenAttachmentsClick,
                            )
                        }
                        if (uiState.attachments.isEmpty()) {
                            item {
                                EmptyAttachmentPreviewCard()
                            }
                        } else {
                            items(uiState.attachments) { attachment ->
                                HomeAttachmentCard(
                                    attachment = attachment,
                                    onClick = {
                                        if (attachment.id.isNotBlank()) {
                                            onAttachmentClick(attachment.id)
                                        } else {
                                            attachment.toEntityOrNull()?.let { openAttachment(context, it) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (fabExpanded && !organizeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { fabExpanded = false },
                )
            }

            if (!organizeMode) {
                FloatingActionMenu(
                    expanded = fabExpanded,
                    actions = createActions,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = VaultSpacing.screen, bottom = VaultSpacing.screen)
                        .size(width = 220.dp, height = 320.dp),
                    onToggle = { fabExpanded = !fabExpanded },
                    onActionClick = { action ->
                        fabExpanded = false
                        when (action.label) {
                            "New Note" -> onNewNoteClick(null)
                            "New Folder" -> {
                                selectedFolder = null
                                newFolderName = ""
                                folderDialogMode = FolderDialogMode.CreateRoot
                            }
                            "Import File" -> importPicker.launch(arrayOf("*/*"))
                        }
                    },
                )
            }
        }
    }

    if (folderActionsOpen && selectedFolder != null) {
        val folder = selectedFolder
        val oppositeMode = if (currentFolderMode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeModeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal" else "Study"
        PremiumActionDialog(
            title = folder?.name.orEmpty(),
            onDismiss = { folderActionsOpen = false },
            actions = listOf(
                PremiumAction("New note", Icons.AutoMirrored.Rounded.NoteAdd) {
                    folderActionsOpen = false
                    onNewNoteClick(folder?.id)
                },
                PremiumAction("+ Subfolder", Icons.Rounded.CreateNewFolder) {
                    folderActionsOpen = false
                    newFolderName = ""
                    folderDialogMode = FolderDialogMode.CreateSubfolder
                },
                PremiumAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    folderActionsOpen = false
                    newFolderName = folder?.name.orEmpty()
                    folderDialogMode = FolderDialogMode.Rename
                },
                PremiumAction("Organise", Icons.Rounded.SwapVert) {
                    folderActionsOpen = false
                    selectedItemIds.clear()
                    manageSelectionMode = false
                    sortMode = WorkspaceSortMode.FoldersFirst
                    organizeMode = true
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    folderActionsOpen = false
                    moveFolderDialogOpen = true
                },
                PremiumAction("Move to $oppositeModeLabel", Icons.Rounded.LocalOffer) {
                    folderActionsOpen = false
                    folder?.let { onMoveFolderToModeClick(it.id, oppositeMode) }
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    folderActionsOpen = false
                    deleteFolderDialogOpen = true
                },
            ),
        )
    }

    if (pinnedOverflowOpen) {
        PinnedNotesSheet(
            notes = uiState.pinnedNotes,
            previewLines = uiState.notePreviewLines,
            onDismiss = { pinnedOverflowOpen = false },
            onNoteClick = { noteId ->
                pinnedOverflowOpen = false
                onNoteClick(noteId)
            },
        )
    }

    if (quickBackupConfirmOpen) {
        AlertDialog(
            onDismissRequest = { quickBackupConfirmOpen = false },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            title = { Text("Back up to cloud?") },
            text = {
                Text(
                    "This will replace the current cloud backup with the notes on this device. Make sure this phone has your latest notes before continuing.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        quickBackupConfirmOpen = false
                        onQuickBackupClick()
                    },
                ) {
                    Text("Back up now")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickBackupConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (noteActionsOpen && selectedNote != null) {
        val note = selectedNote
        PremiumActionDialog(
            title = note?.name.orEmpty(),
            onDismiss = { noteActionsOpen = false },
            actions = listOf(
                PremiumAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    noteActionsOpen = false
                    noteTitleInput = note?.name.orEmpty()
                    renameNoteDialogOpen = true
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    noteActionsOpen = false
                    moveNoteDialogOpen = true
                },
                PremiumAction(if (note?.pinned == true) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                    note?.let { onSetNotePinnedClick(it.id, !it.pinned) }
                    noteActionsOpen = false
                },
                PremiumAction(if (note?.favourite == true) "Unfavourite" else "Favourite", Icons.Rounded.Star) {
                    note?.let { onSetNoteFavouriteClick(it.id, !it.favourite) }
                    noteActionsOpen = false
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    noteActionsOpen = false
                    deleteNoteDialogOpen = true
                },
            ),
        )
    }

    if (moveNoteDialogOpen && selectedNote != null) {
        val note = selectedNote
        val targets = remember(uiState.workspace) {
            listOf(null to "My Vault") + uiState.workspace.flatMap { it.flattenFolderPaths() }
        }
        PremiumActionDialog(
            title = "Move ${note?.name.orEmpty()}",
            onDismiss = { moveNoteDialogOpen = false },
            actions = targets.map { (targetId, label) ->
                PremiumAction(label = label, icon = Icons.Rounded.Folder) {
                    note?.let { onMoveNoteClick(it.id, targetId) }
                    moveNoteDialogOpen = false
                }
            },
        )
    }

    if (renameNoteDialogOpen && selectedNote != null) {
        AlertDialog(
            onDismissRequest = { renameNoteDialogOpen = false },
            title = { Text("Rename note") },
            text = {
                OutlinedTextField(
                    value = noteTitleInput,
                    onValueChange = { noteTitleInput = it },
                    singleLine = true,
                    label = { Text("Note title") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedNote?.let { onRenameNoteClick(it.id, noteTitleInput) }
                        noteTitleInput = ""
                        renameNoteDialogOpen = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameNoteDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (deleteNoteDialogOpen && selectedNote != null) {
        val note = selectedNote
        AlertDialog(
            onDismissRequest = { deleteNoteDialogOpen = false },
            title = { Text("Move ${note?.name.orEmpty()} to Recently Deleted?") },
            text = { Text("You can restore this note from Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        note?.let { onDeleteNoteClick(it.id) }
                        deleteNoteDialogOpen = false
                        selectedNote = null
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNoteDialogOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }

    if (moveFolderDialogOpen && selectedFolder != null) {
        val folder = selectedFolder
        val targets = remember(uiState.workspace, folder?.id) {
            listOf(null to "My Vault") + uiState.workspace
                .flatMap { it.flattenFolderPathItems() }
                .filterNot { (target, _) -> target.id == folder?.id || target.containsFolder(folder?.id.orEmpty()) }
                .map { (target, path) -> target.id to path }
        }
        PremiumActionDialog(
            title = "Move ${folder?.name.orEmpty()}",
            onDismiss = { moveFolderDialogOpen = false },
            actions = targets.map { (targetId, label) ->
                PremiumAction(label = label, icon = Icons.Rounded.Folder) {
                    folder?.let { onMoveFolderClick(it.id, targetId) }
                    moveFolderDialogOpen = false
                }
            },
        )
    }

    if (deleteFolderDialogOpen && selectedFolder != null) {
        val folder = selectedFolder
        AlertDialog(
            onDismissRequest = { deleteFolderDialogOpen = false },
            title = { Text("Move ${folder?.name.orEmpty()} to Recently Deleted?") },
            text = { Text("This will also move its subfolders and notes. You can restore them from Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        folder?.let { onDeleteFolderClick(it.id) }
                        deleteFolderDialogOpen = false
                        selectedFolder = null
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderDialogOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }

    if (sortMenuOpen) {
        PremiumActionDialog(
            title = "Sort workspace",
            onDismiss = { sortMenuOpen = false },
            actions = listOf(
                PremiumAction("Name", Icons.Rounded.SortByAlpha) {
                    sortMode = WorkspaceSortMode.Name
                    sortMenuOpen = false
                },
                PremiumAction("Recently edited", Icons.Rounded.Update) {
                    sortMode = WorkspaceSortMode.RecentlyEdited
                    sortMenuOpen = false
                },
                PremiumAction("Folders first", Icons.Rounded.Folder) {
                    sortMode = WorkspaceSortMode.FoldersFirst
                    sortMenuOpen = false
                },
            ),
        )
    }

    if (manageMenuOpen) {
        PremiumActionDialog(
            title = "Manage workspace",
            onDismiss = { manageMenuOpen = false },
            actions = listOf(
                PremiumAction("Select items", Icons.Rounded.CheckCircle) {
                    selectedItemIds.clear()
                    manageSelectionMode = true
                    manageMenuOpen = false
                },
                PremiumAction("Organise folders", Icons.Rounded.SwapVert) {
                    selectedItemIds.clear()
                    manageSelectionMode = false
                    sortMode = WorkspaceSortMode.FoldersFirst
                    organizeMode = true
                    manageMenuOpen = false
                },
                PremiumAction("Rename folder", Icons.Rounded.DriveFileRenameOutline) {
                    manageMenuOpen = false
                    manageRenameFolderOpen = true
                },
                PremiumAction("Move folder", Icons.Rounded.Folder) {
                    manageMenuOpen = false
                    manageMoveFolderOpen = true
                },
                PremiumAction("Delete folder", Icons.Rounded.Delete, destructive = true) {
                    manageMenuOpen = false
                    manageDeleteFolderOpen = true
                },
                PremiumAction("Pinned notes", Icons.Rounded.PushPin) {
                    manageMenuOpen = false
                    managePinnedNotesOpen = true
                },
                PremiumAction("Favourites", Icons.Rounded.Star) {
                    manageMenuOpen = false
                    manageFavouriteNotesOpen = true
                },
            ),
        )
    }

    if (moveSelectedNotesOpen) {
        val targets = remember(uiState.workspace) {
            listOf(null to "My Vault") + uiState.workspace.flatMap { it.flattenFolderPaths() }
        }
        PremiumActionDialog(
            title = "Move ${selectedNotes.size} note${if (selectedNotes.size == 1) "" else "s"}",
            onDismiss = { moveSelectedNotesOpen = false },
            actions = if (selectedNotes.isEmpty()) {
                listOf(PremiumAction("Select notes first", Icons.Rounded.Description) {})
            } else {
                targets.map { (targetId, label) ->
                    PremiumAction(label = label, icon = Icons.Rounded.Folder) {
                        selectedNotes.forEach { onMoveNoteClick(it.id, targetId) }
                        selectedItemIds.clear()
                        moveSelectedNotesOpen = false
                    }
                }
            },
        )
    }

    if (deleteSelectedOpen) {
        AlertDialog(
            onDismissRequest = { deleteSelectedOpen = false },
            title = { Text("Move selected items to Recently Deleted?") },
            text = {
                Text(
                    "This will move ${selectedFolders.size} folder${if (selectedFolders.size == 1) "" else "s"} " +
                        "and ${selectedNotes.size} note${if (selectedNotes.size == 1) "" else "s"} to Recently Deleted.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedNotes.forEach { onDeleteNoteClick(it.id) }
                        selectedFolders.forEach { onDeleteFolderClick(it.id) }
                        selectedItemIds.clear()
                        deleteSelectedOpen = false
                        manageSelectionMode = false
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedOpen = false }) {
                    Text("Keep")
                }
            },
        )
    }

    if (manageRenameFolderOpen) {
        val folders = remember(uiState.workspace) { uiState.workspace.flatMap { it.flattenFolders() } }
        PremiumActionDialog(
            title = "Rename folder",
            onDismiss = { manageRenameFolderOpen = false },
            actions = folders.toPremiumFolderActions(Icons.Rounded.DriveFileRenameOutline) { folder ->
                selectedFolder = folder
                newFolderName = folder.name
                manageRenameFolderOpen = false
                folderDialogMode = FolderDialogMode.Rename
            },
        )
    }

    if (manageMoveFolderOpen) {
        val folders = remember(uiState.workspace) { uiState.workspace.flatMap { it.flattenFolderPathItems() } }
        PremiumActionDialog(
            title = "Move folder",
            onDismiss = { manageMoveFolderOpen = false },
            actions = folders.toPremiumFolderPathActions(Icons.Rounded.Folder) { folder ->
                selectedFolder = folder
                manageMoveFolderOpen = false
                moveFolderDialogOpen = true
            },
        )
    }

    if (manageDeleteFolderOpen) {
        val folders = remember(uiState.workspace) { uiState.workspace.flatMap { it.flattenFolders() } }
        PremiumActionDialog(
            title = "Delete folder",
            onDismiss = { manageDeleteFolderOpen = false },
            actions = folders.toPremiumFolderActions(Icons.Rounded.Delete, destructive = true) { folder ->
                selectedFolder = folder
                manageDeleteFolderOpen = false
                deleteFolderDialogOpen = true
            },
        )
    }

    if (managePinnedNotesOpen) {
        val pinnedNotes = remember(uiState.workspace) {
            uiState.workspace.flatMap { it.flattenNotes() }.filter { it.pinned }
        }
        PremiumActionDialog(
            title = "Pinned notes",
            onDismiss = { managePinnedNotesOpen = false },
            actions = pinnedNotes.toPremiumNoteActions(Icons.Rounded.PushPin, emptyLabel = "No pinned notes") { note ->
                onSetNotePinnedClick(note.id, false)
                managePinnedNotesOpen = false
            },
        )
    }

    if (manageFavouriteNotesOpen) {
        val favouriteNotes = remember(uiState.workspace) {
            uiState.workspace.flatMap { it.flattenNotes() }.filter { it.favourite }
        }
        PremiumActionDialog(
            title = "Favourites",
            onDismiss = { manageFavouriteNotesOpen = false },
            actions = favouriteNotes.toPremiumNoteActions(Icons.Rounded.Star, emptyLabel = "No favourite notes") { note ->
                onSetNoteFavouriteClick(note.id, false)
                manageFavouriteNotesOpen = false
            },
        )
    }

    val dialogMode = folderDialogMode
    if (dialogMode != null) {
        AlertDialog(
            onDismissRequest = { folderDialogMode = null },
            title = {
                Text(
                    when (dialogMode) {
                        FolderDialogMode.CreateRoot -> "New folder"
                        FolderDialogMode.CreateSubfolder -> "New subfolder"
                        FolderDialogMode.Rename -> "Rename folder"
                    },
                )
            },
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
                        when (dialogMode) {
                            FolderDialogMode.CreateRoot -> onNewFolderClick(null, newFolderName)
                            FolderDialogMode.CreateSubfolder -> onNewFolderClick(selectedFolder?.id, newFolderName)
                            FolderDialogMode.Rename -> selectedFolder?.let { onRenameFolderClick(it.id, newFolderName) }
                        }
                        newFolderName = ""
                        folderDialogMode = null
                    },
                ) {
                    Text(if (dialogMode == FolderDialogMode.Rename) "Save" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDialogMode = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyHomeState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
            Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = colors.textSecondary,
            )
        }
    }
}

private enum class FolderDialogMode {
    CreateRoot,
    CreateSubfolder,
    Rename,
}

private data class PremiumAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun AllAttachmentsCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 154.dp, height = 74.dp),
        color = colors.accentSoft,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = colors.accent,
                shape = VaultShapes.sm,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All attachments",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$count file${if (count == 1) "" else "s"}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                Text(
                    text = "View library",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyAttachmentPreviewCard(
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.size(width = 154.dp, height = 74.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(24.dp), tint = colors.textMuted)
            Text(
                text = "Attached files will appear here",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeAttachmentCard(
    attachment: AttachmentSample,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val iconData = homeAttachmentIcon(attachment.kind, colors)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 154.dp, height = 74.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentThumbnail(mimeType = attachment.mimeType, localPath = attachment.localPath, kind = attachment.kind, size = 30.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attachment.note,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attachment.size,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun AttachmentSample.toEntityOrNull(): AttachmentEntity? =
    if (id.isBlank() || noteId.isBlank() || localPath.isBlank()) {
        null
    } else {
        AttachmentEntity(
            id = id,
            noteId = noteId,
            fileName = name,
            mimeType = mimeType,
            sizeBytes = 0,
            localPath = localPath,
            remoteUrl = null,
            createdAt = 0L,
        )
    }

private fun homeAttachmentIcon(
    kind: String,
    colors: com.myvault.app.ui.theme.VaultColors,
): Pair<ImageVector, Color> = when (kind) {
    "PDF" -> Icons.Rounded.PictureAsPdf to colors.warning
    "Audio" -> Icons.Rounded.Audiotrack to colors.accent
    "Image" -> Icons.Rounded.Image to colors.success
    "Doc" -> Icons.AutoMirrored.Rounded.Article to colors.textSecondary
    else -> Icons.Rounded.AttachFile to colors.textSecondary
}

private enum class WorkspaceSortMode {
    FoldersFirst,
    Name,
    RecentlyEdited,
}

@Composable
private fun SelectionManageBar(
    selectedCount: Int,
    selectedNoteCount: Int,
    onMoveNotes: () -> Unit,
    onPinNotes: () -> Unit,
    onFavouriteNotes: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.elevated,
        shape = VaultShapes.lg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                Text(
                    text = "$selectedCount selected",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDone) {
                    Text("Done")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                item {
                    SelectionActionPill("Move notes", enabled = selectedNoteCount > 0, onClick = onMoveNotes)
                }
                item {
                    SelectionActionPill("Pin", enabled = selectedNoteCount > 0, onClick = onPinNotes)
                }
                item {
                    SelectionActionPill("Favourite", enabled = selectedNoteCount > 0, onClick = onFavouriteNotes)
                }
                item {
                    SelectionActionPill("Delete", enabled = selectedCount > 0, destructive = true, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun SelectionActionPill(
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = VaultShapes.pill,
        color = if (enabled) colors.surface else colors.inset,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = when {
                !enabled -> colors.textMuted
                destructive -> colors.warning
                else -> colors.text
            },
        )
    }
}

@Composable
private fun InlineSearchResults(
    uiState: HomeUiState,
    onNoteClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val hasResults = uiState.searchNotes.isNotEmpty() ||
        uiState.searchFolders.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.elevated,
        shape = VaultShapes.lg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                Text(
                    text = "Search results",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
            }

            if (!hasResults) {
                Text(
                    text = "No matching notes or folders yet.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            uiState.searchNotes.take(4).forEach { result ->
                InlineNoteResult(
                    result = result,
                    query = uiState.searchQuery,
                    onClick = { onNoteClick(result.id) },
                )
            }

            uiState.searchFolders.take(4).forEach { folder ->
                InlineFolderResult(folder = folder, onClick = { onFolderClick(folder.id) })
            }
        }
    }
}

@Composable
private fun InlineNoteResult(
    result: SearchResultData,
    query: String,
    onClick: () -> Unit,
) {
    SearchResultCard(
        result = result,
        query = query,
        onClick = onClick,
    )
}

@Composable
private fun InlineFolderResult(
    folder: FolderEntity,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                color = colors.accentSoft,
                shape = VaultShapes.sm,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Folder",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun PremiumActionDialog(
    title: String,
    actions: List<PremiumAction>,
    onDismiss: () -> Unit,
) {
    val colors = VaultThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                color = colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                actions.forEach { action ->
                    Surface(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                color = colors.accentSoft,
                                shape = VaultShapes.sm,
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(action.icon, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                                }
                            }
                            Text(
                                text = action.label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                                color = if (action.destructive) colors.warning else colors.text,
                            )
                        }
                    }
                }
            }
        },
        containerColor = colors.elevated,
        tonalElevation = 0.dp,
    )
}

private fun VaultTreeItem.flattenFolders(): List<VaultTreeItem> =
    if (type == VaultTreeItemType.Folder) {
        listOf(this) + children.flatMap { it.flattenFolders() }
    } else {
        emptyList()
    }

private fun VaultTreeItem.flattenItems(): List<VaultTreeItem> =
    listOf(this) + children.flatMap { it.flattenItems() }

private fun VaultTreeItem.flattenNotes(): List<VaultTreeItem> =
    if (type == VaultTreeItemType.Note) {
        listOf(this)
    } else {
        children.flatMap { it.flattenNotes() }
    }

private fun VaultTreeItem.containsFolder(folderId: String): Boolean =
    children.any { it.id == folderId || it.containsFolder(folderId) }

private fun VaultTreeItem.flattenFolderPaths(parentPath: String = ""): List<Pair<String, String>> =
    flattenFolderPathItems(parentPath).map { (folder, path) -> folder.id to path }

private fun VaultTreeItem.flattenFolderPathItems(parentPath: String = ""): List<Pair<VaultTreeItem, String>> =
    if (type == VaultTreeItemType.Folder) {
        val path = if (parentPath.isBlank()) name else "$parentPath / $name"
        listOf(this to path) + children.flatMap { it.flattenFolderPathItems(path) }
    } else {
        emptyList()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PinnedNotesSheet(
    notes: List<com.myvault.app.ui.components.VaultNoteCardData>,
    previewLines: Int,
    onDismiss: () -> Unit,
    onNoteClick: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevated,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp),
                color = colors.borderStrong,
                shape = VaultShapes.pill,
                content = {},
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(horizontal = VaultSpacing.screen)
                .padding(bottom = VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = colors.accentSoft,
                    shape = VaultShapes.md,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pinned notes",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                    )
                    Text(
                        text = "${notes.size} saved for quick access",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                contentPadding = PaddingValues(bottom = VaultSpacing.sm),
            ) {
                items(notes) { note ->
                    Surface(
                        onClick = { onNoteClick(note.id) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                color = colors.accentSoft,
                                shape = VaultShapes.sm,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = note.title,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (previewLines > 0 && note.preview.isNotBlank()) {
                                    Text(
                                        text = note.preview,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        color = colors.textMuted,
                                        maxLines = previewLines,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MutableMap<String, Boolean>.toggle(id: String) {
    if (this[id] == true) remove(id) else this[id] = true
}

private fun List<VaultTreeItem>.sortForMode(mode: WorkspaceSortMode): List<VaultTreeItem> =
    map { it.copy(children = it.children.sortForMode(mode)) }
        .let { items ->
            when (mode) {
                WorkspaceSortMode.FoldersFirst -> items.sortedBy { it.type != VaultTreeItemType.Folder }
                WorkspaceSortMode.Name -> items.sortedBy { it.name.lowercase() }
                WorkspaceSortMode.RecentlyEdited -> items.sortedByDescending { it.updatedAt }
            }
        }

private fun List<VaultTreeItem>.toPremiumFolderActions(
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: (VaultTreeItem) -> Unit,
): List<PremiumAction> =
    if (isEmpty()) {
        listOf(PremiumAction("No folders yet", icon) {})
    } else {
        map { folder ->
            PremiumAction(folder.name, icon, destructive = destructive) { onClick(folder) }
        }
    }

private fun List<Pair<VaultTreeItem, String>>.toPremiumFolderPathActions(
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: (VaultTreeItem) -> Unit,
): List<PremiumAction> =
    if (isEmpty()) {
        listOf(PremiumAction("No folders yet", icon) {})
    } else {
        map { (folder, path) ->
            PremiumAction(path, icon, destructive = destructive) { onClick(folder) }
        }
    }

private fun List<VaultTreeItem>.toPremiumNoteActions(
    icon: ImageVector,
    emptyLabel: String,
    onClick: (VaultTreeItem) -> Unit,
): List<PremiumAction> =
    if (isEmpty()) {
        listOf(PremiumAction(emptyLabel, icon) {})
    } else {
        map { note ->
            PremiumAction(note.name, icon) { onClick(note) }
        }
    }

@Preview(name = "HomeScreen Light")
@Composable
private fun HomeScreenLightPreview() {
    VaultTheme(mode = VaultThemeMode.Light) {
        HomeScreen(
            uiState = HomeUiState(
                pinnedNotes = HomeSampleData.pinnedNotes,
                attachments = Pass4Samples.attachments,
                workspace = HomeSampleData.workspace,
            ),
            onSearchClick = {},
            onSettingsClick = {},
            onFolderClick = {},
            onNoteClick = {},
        )
    }
}

@Preview(name = "HomeScreen Dark")
@Composable
private fun HomeScreenDarkPreview() {
    VaultTheme(mode = VaultThemeMode.Dark) {
        HomeScreen(
            uiState = HomeUiState(
                pinnedNotes = HomeSampleData.pinnedNotes,
                attachments = Pass4Samples.attachments,
                workspace = HomeSampleData.workspace,
            ),
            onSearchClick = {},
            onSettingsClick = {},
            onFolderClick = {},
            onNoteClick = {},
        )
    }
}
