package com.myvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderStickyNoteEntity
import com.myvault.app.ui.components.Breadcrumb
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.FloatingActionMenuExpansion
import com.myvault.app.ui.components.FloatingActionStackDefaults
import com.myvault.app.ui.components.FolderTreeRow
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultConfirmModal
import com.myvault.app.ui.components.VaultFormModal
import com.myvault.app.ui.components.VaultTextField
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
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
    onNewSubfolderClick: (String, String?) -> Unit = { _, _ -> },
    onUpdateFolderClick: (String, String?) -> Unit = { _, _ -> },
    onMoveCurrentFolderClick: (String?) -> Unit = {},
    onDeleteCurrentFolderClick: () -> Unit = {},
    onCreateNoteInFolderClick: (String) -> Unit = {},
    onCreateSubfolderInFolderClick: (String, String, String?) -> Unit = { _, _, _ -> },
    onUpdateChildFolderClick: (String, String, String?) -> Unit = { _, _, _ -> },
    onMoveChildFolderClick: (String, String?) -> Unit = { _, _ -> },
    onDeleteChildFolderClick: (String) -> Unit = {},
    onFolderExpandedChange: (String, Boolean) -> Unit = { _, _ -> },
    onMoveItemInOrderClick: (String, VaultTreeItemType, Int) -> Unit = { _, _, _ -> },
    onRenameNoteClick: (String, String) -> Unit = { _, _ -> },
    onMoveNoteClick: (String, String?) -> Unit = { _, _ -> },
    onMoveNoteToModeClick: (String, String) -> Unit = { _, _ -> },
    onDeleteNoteClick: (String) -> Unit = {},
    onSetNotePinnedClick: (String, Boolean) -> Unit = { _, _ -> },
    onSetNoteFolderPinnedClick: (String, Boolean) -> Unit = { _, _ -> },
    onSetNoteFavouriteClick: (String, Boolean) -> Unit = { _, _ -> },
    onCreateSubNoteClick: (String) -> Unit = {},
    onNewStickyNoteClick: (String) -> Unit = {},
    onUpdateStickyNoteClick: (String, String) -> Unit = { _, _ -> },
    onDeleteStickyNoteClick: (String) -> Unit = {},
    notePreviewLines: Int = 0,
    showFullNoteTitles: Boolean = false,
    dashboardFontSizeSp: Float = 14f,
    showNavigationHeader: Boolean = true,
    showFloatingCreateAction: Boolean = true,
    topContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
    coursePresentation: Boolean = false,
    fabBottomPadding: Dp = FloatingActionStackDefaults.fabBottomPadding,
) {
    val colors = VaultThemeTokens.colors
    val expansionPrefix = "folder:${uiState.folder?.id}:"
    fun isExpanded(id: String) = "$expansionPrefix$id" in uiState.expandedFolderIds
    var fabExpanded by remember { mutableStateOf(false) }
    var folderActionsOpen by remember { mutableStateOf(false) }
    var organizeMode by remember { mutableStateOf(false) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    var renameFolderDialogOpen by remember { mutableStateOf(false) }
    var editDescriptionDialogOpen by remember { mutableStateOf(false) }
    var moveFolderDialogOpen by remember { mutableStateOf(false) }
    var deleteFolderDialogOpen by remember { mutableStateOf(false) }
    var selectedTreeFolder by remember { mutableStateOf<VaultTreeItem?>(null) }
    var treeFolderCreateOpen by remember { mutableStateOf(false) }
    var treeFolderActionsOpen by remember { mutableStateOf(false) }
    var treeFolderDialogMode by remember { mutableStateOf<TreeFolderDialogMode?>(null) }
    var treeFolderMoveDialogOpen by remember { mutableStateOf(false) }
    var treeFolderDeleteDialogOpen by remember { mutableStateOf(false) }
    var folderNameDraft by remember { mutableStateOf("") }
    var folderDescriptionDraft by remember { mutableStateOf("") }
    var stickyDialogOpen by remember { mutableStateOf(false) }
    var stickyDraft by remember { mutableStateOf("") }
    var selectedStickyNote by remember { mutableStateOf<FolderStickyNoteEntity?>(null) }
    var deleteStickyConfirmOpen by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<VaultTreeItem?>(null) }
    var noteActionsOpen by remember { mutableStateOf(false) }
    var renameNoteDialogOpen by remember { mutableStateOf(false) }
    var moveNoteDialogOpen by remember { mutableStateOf(false) }
    var deleteNoteDialogOpen by remember { mutableStateOf(false) }
    var noteTitleDraft by remember { mutableStateOf("") }
    BackHandler(enabled = fabExpanded) {
        fabExpanded = false
    }
    val createActions = remember {
        listOf(
            FloatingAction("New Note", Icons.Rounded.Description),
            FloatingAction("New Subfolder", Icons.Rounded.CreateNewFolder),
            FloatingAction("New Sticky Note", Icons.Rounded.StickyNote2),
        )
    }
    val pinnedNotes = remember(uiState.contents) {
        uiState.contents.filter { it.type == VaultTreeItemType.Note }.flatMap { it.flattenNoteTree() }.filter { it.folderPinned }
    }
    val folderStats = remember(uiState.contents, uiState.stickyNotes) {
        val subfolderCount = uiState.contents.count { it.type == VaultTreeItemType.Folder }
        val noteCount = uiState.contents.sumOf { it.noteCount() }
        listOfNotNull(
            subfolderCount.takeIf { it > 0 }?.let { "$it subfolder${if (it == 1) "" else "s"}" },
            noteCount.takeIf { it > 0 }?.let { "$it note${if (it == 1) "" else "s"}" },
            uiState.stickyNotes.size.takeIf { it > 0 }?.let { "$it sticky note${if (it == 1) "" else "s"}" },
        ).ifEmpty { listOf("Empty folder") }.joinToString(" • ")
    }

    val scaffoldInsets = if (showNavigationHeader) {
        ScaffoldDefaults.contentWindowInsets
    } else {
        WindowInsets(0, 0, 0, 0)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        contentWindowInsets = scaffoldInsets,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                if (showNavigationHeader) {
                    item {
                        ScreenTopBar(onBackClick = onBackClick) {
                            if (organizeMode) {
                                TextButton(onClick = { organizeMode = false }) {
                                    Icon(Icons.Rounded.Done, null, modifier = Modifier.size(15.dp))
                                    Text("Done")
                                }
                            } else {
                                SearchMoreActions(onSearchClick = onSearchClick, onMoreClick = { folderActionsOpen = true })
                            }
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
                            Text(
                                text = uiState.folder?.name ?: "Folder",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.text,
                            )
                            uiState.folder?.description?.takeIf { it.isNotBlank() }?.let { description ->
                                Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                            Text(text = folderStats, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        }
                    }
                }
                topContent?.let { content -> item { content() } }
                if (!coursePresentation && pinnedNotes.isNotEmpty()) {
                    item { SectionLabel(label = "Pinned Notes") }
                    items(pinnedNotes, key = { "folder-pin-${it.id}" }) { note ->
                        FolderPinnedNoteRow(note = note, onClick = { onNoteClick(note.id) })
                    }
                }
                if (!coursePresentation && uiState.stickyNotes.isNotEmpty()) {
                    item { SectionLabel(label = "Sticky Notes") }
                    items(uiState.stickyNotes, key = { "sticky-${it.id}" }) { stickyNote ->
                        FolderStickyNoteRow(stickyNote = stickyNote) {
                            selectedStickyNote = stickyNote
                            stickyDraft = stickyNote.text
                            stickyDialogOpen = true
                        }
                    }
                    item { Spacer(Modifier.size(14.dp)) }
                }
                if (coursePresentation && uiState.stickyNotes.isNotEmpty()) {
                    item { CourseCountSectionLabel(label = "STICKY NOTES", count = uiState.stickyNotes.size) }
                    items(uiState.stickyNotes, key = { "course-sticky-${it.id}" }) { stickyNote ->
                        CourseStickyNoteCard(stickyNote = stickyNote) {
                            selectedStickyNote = stickyNote
                            stickyDraft = stickyNote.text
                            stickyDialogOpen = true
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                if (coursePresentation) {
                    item {
                        CourseCountSectionLabel(label = "COURSE NOTES", count = uiState.contents.sumOf { it.noteCount() })
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.xs)) {
                        uiState.contents.forEachIndexed { index, item ->
                            FolderTreeRow(
                                item = item,
                                depth = 0,
                                expanded = isExpanded(item.id),
                                isChildExpanded = ::isExpanded,
                                onToggle = { folder -> onFolderExpandedChange(folder.id, !isExpanded(folder.id)) },
                                onOpenFolder = { folder -> onFolderClick(folder.id) },
                                onOpenNote = { note -> onNoteClick(note.id) },
                                onLongPress = { item ->
                                    when (item.type) {
                                        VaultTreeItemType.Folder -> {
                                            selectedTreeFolder = item
                                            treeFolderActionsOpen = true
                                        }
                                        VaultTreeItemType.Note -> {
                                            selectedNote = item
                                            noteActionsOpen = true
                                        }
                                    }
                                },
                                onCreateInside = null,
                                organizeMode = organizeMode,
                                organizeAllItems = true,
                                notePreviewLines = notePreviewLines,
                                showFullNoteTitles = showFullNoteTitles,
                                dashboardFontSizeSp = dashboardFontSizeSp,
                                flatHierarchy = coursePresentation,
                                showLeafEditedTime = !coursePresentation,
                                canMoveUp = index > 0,
                                canMoveDown = index < uiState.contents.lastIndex,
                                onMoveFolder = { movedItem, direction ->
                                    onMoveItemInOrderClick(movedItem.id, movedItem.type, direction)
                                },
                            )
                        }
                    }
                }
                bottomContent?.let { content -> item { content() } }
            }

            if (fabExpanded && !organizeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim)
                        .clickable { fabExpanded = false },
                )
            }
            if (showFloatingCreateAction && !organizeMode) FloatingActionMenu(
                expanded = fabExpanded,
                actions = createActions,
                mainButtonSize = FloatingActionStackDefaults.mainButtonSize,
                actionButtonSize = FloatingActionStackDefaults.actionButtonSize,
                expansionDirection = FloatingActionMenuExpansion.Start,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(
                        end = FloatingActionStackDefaults.endPadding,
                        bottom = fabBottomPadding,
                    )
                    .size(
                        width = FloatingActionStackDefaults.menuWidth,
                        height = FloatingActionStackDefaults.menuHeight,
                    ),
                onToggle = { fabExpanded = !fabExpanded },
                onActionClick = { action ->
                    fabExpanded = false
                    when (action.label) {
                        "New Note" -> onNewNoteClick()
                        "New Subfolder" -> {
                            folderNameDraft = ""
                            folderDescriptionDraft = ""
                            folderDialogOpen = true
                        }
                        "New Sticky Note" -> {
                            selectedStickyNote = null
                            stickyDraft = ""
                            stickyDialogOpen = true
                        }
                    }
                },
            )
        }
    }

    if (folderDialogOpen) {
        FolderDetailsDialog(
            title = "New subfolder",
            confirmLabel = "Create",
            name = folderNameDraft,
            description = folderDescriptionDraft,
            onNameChange = { folderNameDraft = it },
            onDescriptionChange = { folderDescriptionDraft = it },
            onDismiss = { folderDialogOpen = false },
            onConfirm = {
                onNewSubfolderClick(folderNameDraft, folderDescriptionDraft)
                folderDialogOpen = false
            },
        )
    }
    if (folderActionsOpen) {
        PremiumActionDialog(
            title = uiState.folder?.name.orEmpty(),
            onDismiss = { folderActionsOpen = false },
            actions = listOf(
                PremiumAction("Rename Folder", Icons.Rounded.DriveFileRenameOutline) {
                    folderNameDraft = uiState.folder?.name.orEmpty()
                    folderActionsOpen = false
                    renameFolderDialogOpen = true
                },
                PremiumAction("Edit Description", Icons.Rounded.Description) {
                    folderDescriptionDraft = uiState.folder?.description.orEmpty()
                    folderActionsOpen = false
                    editDescriptionDialogOpen = true
                },
                PremiumAction("Move Folder", Icons.Rounded.Folder) {
                    folderActionsOpen = false
                    moveFolderDialogOpen = true
                },
                PremiumAction("Organise", Icons.Rounded.SwapVert) {
                    folderActionsOpen = false
                    organizeMode = true
                },
                PremiumAction("Delete Folder", Icons.Rounded.Delete, destructive = true) {
                    folderActionsOpen = false
                    deleteFolderDialogOpen = true
                },
            ),
        )
    }
    if (renameFolderDialogOpen) {
        VaultFormModal(
            title = "Rename folder",
            confirmLabel = "Save",
            enabled = folderNameDraft.isNotBlank(),
            icon = Icons.Rounded.Folder,
            onDismiss = { renameFolderDialogOpen = false },
            onConfirm = {
                onUpdateFolderClick(folderNameDraft, uiState.folder?.description)
                renameFolderDialogOpen = false
            },
        ) {
            VaultTextField(folderNameDraft, { folderNameDraft = it }, label = "Folder name", singleLine = true)
        }
    }
    if (editDescriptionDialogOpen) {
        VaultFormModal(
            title = "Edit description",
            confirmLabel = "Save",
            icon = Icons.Rounded.Description,
            onDismiss = { editDescriptionDialogOpen = false },
            onConfirm = {
                onUpdateFolderClick(uiState.folder?.name.orEmpty(), folderDescriptionDraft)
                editDescriptionDialogOpen = false
            },
        ) {
            VaultTextField(
                value = folderDescriptionDraft,
                onValueChange = { folderDescriptionDraft = it },
                label = "Description (optional)",
                minLines = 2,
                maxLines = 5,
            )
        }
    }
    if (moveFolderDialogOpen) {
        val excludedIds = remember(uiState.workspace, uiState.folder?.id) {
            uiState.workspace.findItem(uiState.folder?.id.orEmpty())?.collectFolderIds().orEmpty()
        }
        PremiumActionDialog(
            title = "Move ${uiState.folder?.name.orEmpty()}",
            onDismiss = { moveFolderDialogOpen = false },
            actions = listOf(PremiumAction("My Vault", Icons.Rounded.Folder) {
                onMoveCurrentFolderClick(null)
                moveFolderDialogOpen = false
            }) + uiState.workspace.folderPathActions(excludedIds = excludedIds) { targetId ->
                onMoveCurrentFolderClick(targetId)
                moveFolderDialogOpen = false
            },
        )
    }
    if (deleteFolderDialogOpen) {
        VaultConfirmModal(
            title = "Move ${uiState.folder?.name.orEmpty()}?",
            message = "Its contained folders and notes will also be moved to Recently Deleted.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteFolderDialogOpen = false },
            onConfirm = {
                deleteFolderDialogOpen = false
                onDeleteCurrentFolderClick()
            },
        )
    }

    if (treeFolderActionsOpen && selectedTreeFolder != null) {
        val folder = selectedTreeFolder
        PremiumActionDialog(
            title = folder?.name.orEmpty(),
            onDismiss = { treeFolderActionsOpen = false },
            actions = listOf(
                PremiumAction("New note", Icons.Rounded.NoteAdd) {
                    folder?.let { onCreateNoteInFolderClick(it.id) }
                    treeFolderActionsOpen = false
                },
                PremiumAction("New subfolder", Icons.Rounded.CreateNewFolder) {
                    folderNameDraft = ""
                    folderDescriptionDraft = ""
                    treeFolderActionsOpen = false
                    treeFolderDialogMode = TreeFolderDialogMode.CreateSubfolder
                },
                PremiumAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    folderNameDraft = folder?.name.orEmpty()
                    folderDescriptionDraft = folder?.description.orEmpty()
                    treeFolderActionsOpen = false
                    treeFolderDialogMode = TreeFolderDialogMode.Rename
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    treeFolderActionsOpen = false
                    treeFolderMoveDialogOpen = true
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    treeFolderActionsOpen = false
                    treeFolderDeleteDialogOpen = true
                },
            ),
        )
    }

    if (treeFolderCreateOpen && selectedTreeFolder != null) {
        val folder = selectedTreeFolder
        PremiumActionDialog(
            title = folder?.name.orEmpty(),
            onDismiss = { treeFolderCreateOpen = false },
            actions = listOf(
                PremiumAction("New note", Icons.Rounded.NoteAdd) {
                    folder?.let { onCreateNoteInFolderClick(it.id) }
                    treeFolderCreateOpen = false
                },
                PremiumAction("New subfolder", Icons.Rounded.CreateNewFolder) {
                    folderNameDraft = ""
                    folderDescriptionDraft = ""
                    treeFolderCreateOpen = false
                    treeFolderDialogMode = TreeFolderDialogMode.CreateSubfolder
                },
            ),
        )
    }

    treeFolderDialogMode?.let { dialogMode ->
        VaultFormModal(
            title = if (dialogMode == TreeFolderDialogMode.Rename) "Rename folder" else "New subfolder",
            confirmLabel = if (dialogMode == TreeFolderDialogMode.Rename) "Save" else "Create",
            enabled = folderNameDraft.isNotBlank(),
            icon = Icons.Rounded.Folder,
            onDismiss = { treeFolderDialogMode = null },
            onConfirm = {
                selectedTreeFolder?.let { folder ->
                    when (dialogMode) {
                        TreeFolderDialogMode.CreateSubfolder -> onCreateSubfolderInFolderClick(
                            folder.id,
                            folderNameDraft,
                            folderDescriptionDraft,
                        )
                        TreeFolderDialogMode.Rename -> onUpdateChildFolderClick(
                            folder.id,
                            folderNameDraft,
                            folderDescriptionDraft,
                        )
                    }
                }
                treeFolderDialogMode = null
            },
        ) {
            VaultTextField(folderNameDraft, { folderNameDraft = it }, label = "Folder name", singleLine = true)
            VaultTextField(
                value = folderDescriptionDraft,
                onValueChange = { folderDescriptionDraft = it },
                label = "Description (optional)",
                minLines = 2,
                maxLines = 3,
            )
        }
    }

    if (treeFolderMoveDialogOpen && selectedTreeFolder != null) {
        val folder = selectedTreeFolder
        val courseRootId = uiState.folder?.takeIf { it.mode.startsWith("course:") }?.id
        val rootTarget = courseRootId to (uiState.folder?.name ?: "Course")
        val targets = remember(uiState.workspace, folder?.id, courseRootId) {
            val topTarget = if (courseRootId != null) listOf(rootTarget) else listOf(null to "My Vault")
            topTarget + uiState.workspace
                .flatMap { it.folderPathItems() }
                .filterNot { (target, _) ->
                    target.id == folder?.id || target.id in folder?.collectFolderIds().orEmpty()
                }
                .map { (target, path) -> target.id to path }
                .filterNot { (targetId, _) -> targetId == courseRootId }
        }
        PremiumActionDialog(
            title = "Move ${folder?.name.orEmpty()}",
            onDismiss = { treeFolderMoveDialogOpen = false },
            actions = targets.map { (targetId, label) ->
                PremiumAction(label, Icons.Rounded.Folder) {
                    folder?.let { onMoveChildFolderClick(it.id, targetId) }
                    treeFolderMoveDialogOpen = false
                }
            },
        )
    }

    if (treeFolderDeleteDialogOpen && selectedTreeFolder != null) {
        val folder = selectedTreeFolder
        VaultConfirmModal(
            title = "Move ${folder?.name.orEmpty()}?",
            message = "Its contained folders and notes will also be moved to Recently Deleted.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { treeFolderDeleteDialogOpen = false },
            onConfirm = {
                folder?.let { onDeleteChildFolderClick(it.id) }
                selectedTreeFolder = null
                treeFolderDeleteDialogOpen = false
            },
        )
    }

    if (noteActionsOpen && selectedNote != null) {
        val note = selectedNote
        val oppositeMode = if (uiState.folder?.mode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal Workspace" else "Islamic Corpus"
        PremiumActionDialog(
            title = note?.name.orEmpty(),
            onDismiss = { noteActionsOpen = false },
            actions = listOf(
                PremiumAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    noteTitleDraft = note?.name.orEmpty()
                    noteActionsOpen = false
                    renameNoteDialogOpen = true
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    noteActionsOpen = false
                    moveNoteDialogOpen = true
                },
                PremiumAction("Create Sub-note", Icons.Rounded.NoteAdd) {
                    note?.let { onCreateSubNoteClick(it.id) }
                    noteActionsOpen = false
                },
                PremiumAction("Move to $oppositeLabel", Icons.Rounded.LocalOffer) {
                    note?.let { onMoveNoteToModeClick(it.id, oppositeMode) }
                    noteActionsOpen = false
                },
                PremiumAction(if (note?.pinned == true) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                    note?.let { onSetNotePinnedClick(it.id, !it.pinned) }
                    noteActionsOpen = false
                },
                PremiumAction(if (note?.folderPinned == true) "Unpin Note" else "Pin Note", Icons.Rounded.PushPin) {
                    note?.let { onSetNoteFolderPinnedClick(it.id, !it.folderPinned) }
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
        PremiumActionDialog(
            title = "Move ${selectedNote?.name.orEmpty()}",
            onDismiss = { moveNoteDialogOpen = false },
            actions = listOf(PremiumAction("My Vault", Icons.Rounded.Folder) {
                selectedNote?.let { onMoveNoteClick(it.id, null) }
                moveNoteDialogOpen = false
            }) + uiState.workspace.folderPathActions { folderId ->
                selectedNote?.let { onMoveNoteClick(it.id, folderId) }
                moveNoteDialogOpen = false
            },
        )
    }
    if (renameNoteDialogOpen && selectedNote != null) {
        VaultFormModal(
            title = "Rename note",
            confirmLabel = "Save",
            enabled = noteTitleDraft.isNotBlank(),
            icon = Icons.Rounded.Description,
            onDismiss = { renameNoteDialogOpen = false },
            onConfirm = {
                selectedNote?.let { onRenameNoteClick(it.id, noteTitleDraft) }
                renameNoteDialogOpen = false
            },
        ) {
            VaultTextField(noteTitleDraft, { noteTitleDraft = it }, label = "Note title", singleLine = true)
        }
    }
    if (deleteNoteDialogOpen && selectedNote != null) {
        VaultConfirmModal(
            title = "Move ${selectedNote?.name.orEmpty()}?",
            message = "You can restore this note from Settings.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteNoteDialogOpen = false },
            onConfirm = {
                selectedNote?.let { onDeleteNoteClick(it.id) }
                selectedNote = null
                deleteNoteDialogOpen = false
            },
        )
    }

    if (stickyDialogOpen) {
        VaultFormModal(
            title = if (selectedStickyNote == null) "New Sticky Note" else "Edit Sticky Note",
            confirmLabel = "Save",
            enabled = stickyDraft.isNotBlank(),
            icon = Icons.Rounded.StickyNote2,
            destructiveLabel = if (selectedStickyNote == null) null else "Delete",
            onDestructive = selectedStickyNote?.let {
                {
                    stickyDialogOpen = false
                    deleteStickyConfirmOpen = true
                }
            },
            onDismiss = { stickyDialogOpen = false },
            onConfirm = {
                selectedStickyNote?.let { onUpdateStickyNoteClick(it.id, stickyDraft) }
                    ?: onNewStickyNoteClick(stickyDraft)
                stickyDialogOpen = false
            },
        ) {
            VaultTextField(
                value = stickyDraft,
                onValueChange = { stickyDraft = it },
                label = "Study reminder",
                minLines = 6,
                maxLines = 12,
            )
        }
    }
    if (deleteStickyConfirmOpen && selectedStickyNote != null) {
        VaultConfirmModal(
            title = "Delete sticky note?",
            message = "This sticky note will be removed from this folder.",
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteStickyConfirmOpen = false },
            onConfirm = {
                selectedStickyNote?.let { onDeleteStickyNoteClick(it.id) }
                selectedStickyNote = null
                deleteStickyConfirmOpen = false
            },
        )
    }
}

@Composable
private fun CourseStickyNoteCard(
    stickyNote: FolderStickyNoteEntity,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 3.dp),
        color = colors.warningSoft,
        shape = VaultShapes.sm,
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.StickyNote2, null, modifier = Modifier.size(16.dp), tint = colors.warning)
            Text(
                text = stickyNote.text,
                modifier = Modifier.weight(1f),
                color = colors.text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun CourseCountSectionLabel(label: String, count: Int) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.7.sp,
        )
        Text(count.toString(), color = colors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun FolderDetailsDialog(
    title: String,
    confirmLabel: String,
    name: String,
    description: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    VaultFormModal(
        title = title,
        confirmLabel = confirmLabel,
        enabled = name.isNotBlank(),
        icon = Icons.Rounded.Folder,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        VaultTextField(name, onNameChange, label = "Folder name", singleLine = true)
        VaultTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = "Description (optional)",
            minLines = 2,
            maxLines = 3,
        )
    }
}

private fun List<VaultTreeItem>.folderPathActions(
    parentPath: String = "",
    excludedIds: Set<String> = emptySet(),
    onClick: (String) -> Unit,
): List<PremiumAction> = flatMap { item ->
    if (item.type != VaultTreeItemType.Folder || item.id in excludedIds) {
        emptyList()
    } else {
        val path = if (parentPath.isBlank()) item.name else "$parentPath / ${item.name}"
        listOf(PremiumAction(path, Icons.Rounded.Folder) { onClick(item.id) }) +
            item.children.folderPathActions(path, excludedIds, onClick)
    }
}

private fun VaultTreeItem.folderPathItems(parentPath: String = ""): List<Pair<VaultTreeItem, String>> =
    if (type != VaultTreeItemType.Folder) {
        emptyList()
    } else {
        val path = if (parentPath.isBlank()) name else "$parentPath / $name"
        listOf(this to path) + children.flatMap { it.folderPathItems(path) }
    }

private fun VaultTreeItem.findItem(id: String): VaultTreeItem? {
    if (this.id == id) return this
    children.forEach { child -> child.findItem(id)?.let { return it } }
    return null
}

private fun List<VaultTreeItem>.findItem(id: String): VaultTreeItem? {
    forEach { item -> item.findItem(id)?.let { return it } }
    return null
}

private fun VaultTreeItem.collectFolderIds(): Set<String> =
    buildSet {
        if (type == VaultTreeItemType.Folder) add(id)
        children.forEach { addAll(it.collectFolderIds()) }
    }

private fun VaultTreeItem.flattenNoteTree(): List<VaultTreeItem> =
    if (type != VaultTreeItemType.Note) emptyList() else listOf(this) + children.flatMap { it.flattenNoteTree() }

private fun VaultTreeItem.noteCount(): Int =
    (if (type == VaultTreeItemType.Note) 1 else 0) + children.sumOf { it.noteCount() }

private enum class TreeFolderDialogMode {
    CreateSubfolder,
    Rename,
}

@Composable
private fun FolderPinnedNoteRow(
    note: VaultTreeItem,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 3.dp),
        color = colors.surface,
        shape = VaultShapes.sm,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(14.dp), tint = colors.accent)
            Text(
                text = note.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderStickyNoteRow(
    stickyNote: FolderStickyNoteEntity,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 3.dp),
        color = colors.surface,
        shape = VaultShapes.sm,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.StickyNote2, null, modifier = Modifier.size(14.dp), tint = colors.accent)
            Text(
                text = stickyNote.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = colors.text,
            )
        }
    }
}
