package com.myvault.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.CompactActionGroup
import com.myvault.app.ui.components.CompactPrimaryAction
import com.myvault.app.ui.components.CompactViewAction
import com.myvault.app.ui.components.CompactWorkspaceHeader
import com.myvault.app.ui.components.CorpusEmptyState
import com.myvault.app.ui.components.CorpusAction
import com.myvault.app.ui.components.CorpusActionGroup
import com.myvault.app.ui.components.CorpusActionSheet
import com.myvault.app.ui.components.CorpusFab
import com.myvault.app.ui.components.CorpusFolderRow
import com.myvault.app.ui.components.CorpusHeader
import com.myvault.app.ui.components.CorpusLeafRow
import com.myvault.app.ui.components.CorpusPinnedItem
import com.myvault.app.ui.components.CorpusPinnedStrip
import com.myvault.app.ui.components.CorpusSearchSummary
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenuExpansion
import com.myvault.app.ui.components.FloatingActionStackDefaults
import com.myvault.app.ui.components.FolderTreeRow
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.PinnedNoteCard
import com.myvault.app.ui.components.SearchBar
import com.myvault.app.ui.components.SearchResultCard
import com.myvault.app.ui.components.SearchResultData
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultActionModal
import com.myvault.app.ui.components.VaultConfirmModal
import com.myvault.app.ui.components.VaultFormModal
import com.myvault.app.ui.components.VaultModalAction
import com.myvault.app.ui.components.VaultTextField
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
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
    workspaceTitle: String = "My Vault",
    onWorkspaceSelected: (String) -> Unit = {},
    workspaceOptions: List<String> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onSettingsClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onNewNoteClick: (folderId: String?) -> Unit = {},
    onNewFolderClick: (parentId: String?, name: String, description: String?) -> Unit = { _, _, _ -> },
    onRenameFolderClick: (folderId: String, name: String, description: String?) -> Unit = { _, _, _ -> },
    onMoveFolderClick: (folderId: String, parentId: String?) -> Unit = { _, _ -> },
    onMoveFolderInOrderClick: (folderId: String, direction: Int) -> Unit = { _, _ -> },
    onMoveFolderToModeClick: (folderId: String, mode: String) -> Unit = { _, _ -> },
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit = { _, _ -> },
    onPinnedExpandedChange: (Boolean) -> Unit = {},
    onDeleteFolderClick: (folderId: String) -> Unit = {},
    onRenameNoteClick: (noteId: String, title: String) -> Unit = { _, _ -> },
    onMoveNoteClick: (noteId: String, folderId: String?) -> Unit = { _, _ -> },
    onMoveNoteToModeClick: (noteId: String, mode: String) -> Unit = { _, _ -> },
    onDeleteNoteClick: (noteId: String) -> Unit = {},
    onSetNotePinnedClick: (noteId: String, pinned: Boolean) -> Unit = { _, _ -> },
    onSetNoteFolderPinnedClick: (noteId: String, pinned: Boolean) -> Unit = { _, _ -> },
    onSetNoteFavouriteClick: (noteId: String, favourite: Boolean) -> Unit = { _, _ -> },
    onCreateSubNoteClick: (parentNoteId: String) -> Unit = {},
    onNewStickyNoteClick: (folderId: String, text: String) -> Unit = { _, _ -> },
    onImportFileClick: (Uri) -> Unit = {},
    onAttachmentClick: (String) -> Unit = {},
    onOpenAttachmentsClick: () -> Unit = {},
    onQuranReflectionsClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    onQuickBackupClick: () -> Unit = {},
    quickBackupRecommended: Boolean = false,
    dashboardFontSizeSp: Float = 14f,
    currentFolderMode: String = FOLDER_MODE_STUDY,
    fabBottomPadding: Dp = FloatingActionStackDefaults.fabBottomPadding,
    onCorpusSearchActiveChange: (Boolean) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    BackHandler(enabled = uiState.searchQuery.isNotBlank()) {
        onSearchQueryChange("")
    }
    val context = LocalContext.current
    val isSearching = uiState.searchQuery.isNotBlank()
    var fabExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) {
        fabExpanded = false
    }
    var folderDialogMode by remember { mutableStateOf<FolderDialogMode?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var folderDescriptionInput by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<VaultTreeItem?>(null) }
    var folderActionsOpen by remember { mutableStateOf(false) }
    var moveFolderDialogOpen by remember { mutableStateOf(false) }
    var deleteFolderDialogOpen by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<VaultTreeItem?>(null) }
    var noteActionsOpen by remember { mutableStateOf(false) }
    var noteMoreActionsOpen by remember { mutableStateOf(false) }
    var folderMoreActionsOpen by remember { mutableStateOf(false) }
    var renameNoteDialogOpen by remember { mutableStateOf(false) }
    var moveNoteDialogOpen by remember { mutableStateOf(false) }
    var deleteNoteDialogOpen by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val pinnedExpanded = uiState.pinnedExpanded
    var utilityMenuOpen by remember { mutableStateOf(false) }
    var studyCreateMenuOpen by remember { mutableStateOf(false) }
    var folderCreateMenuOpen by remember { mutableStateOf(false) }
    var stickyNoteDialogOpen by remember { mutableStateOf(false) }
    var stickyNoteInput by remember { mutableStateOf("") }
    var studySearchOpen by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotBlank()) }
    LaunchedEffect(studySearchOpen) {
        onCorpusSearchActiveChange(studySearchOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onCorpusSearchActiveChange(false) }
    }
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
        uiState.workspace.selectedTreeItems(selectedItemIds)
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
    val listState = rememberLazyListState()
    val folderExpansionPrefix = remember(currentFolderMode) { "home:$currentFolderMode:" }
    fun folderExpansionKey(folderId: String): String = "$folderExpansionPrefix$folderId"
    fun isFolderExpanded(folderId: String): Boolean = folderExpansionKey(folderId) in uiState.expandedFolderIds

    BackHandler(enabled = currentFolderMode == FOLDER_MODE_STUDY && studySearchOpen) {
        studySearchOpen = false
        onSearchQueryChange("")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (studySearchOpen) 1f else 0f)
            .background(colors.bg),
    ) {
        StudyMobileWebContent(
            uiState = uiState,
            displayedWorkspace = displayedWorkspace,
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            searchOpen = studySearchOpen,
            onSearchOpen = { studySearchOpen = true },
            onSearchClose = {
                studySearchOpen = false
                onSearchQueryChange("")
            },
            isFolderExpanded = ::isFolderExpanded,
            onToggleFolder = { folder ->
                onFolderExpandedChange(folderExpansionKey(folder.id), !isFolderExpanded(folder.id))
            },
            onCreateInside = { folder ->
                selectedFolder = folder
                folderCreateMenuOpen = true
            },
            onOpenNote = onNoteClick,
            onMore = { item ->
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
            organizeMode = organizeMode,
            onDoneOrganizing = { organizeMode = false },
            listState = listState,
        )
        if (!organizeMode) {
            CorpusFab(
                onClick = { studyCreateMenuOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (fabBottomPadding < 18.dp) 18.dp else fabBottomPadding),
            )
        }
    }

    if (studyCreateMenuOpen) {
        PremiumActionDialog(
            title = "Add to Study",
            onDismiss = { studyCreateMenuOpen = false },
            actions = listOf(
                PremiumAction("New note", Icons.AutoMirrored.Rounded.NoteAdd, section = "CREATE") {
                    studyCreateMenuOpen = false
                    onNewNoteClick(null)
                },
                PremiumAction("New folder", Icons.Rounded.CreateNewFolder, section = "CREATE") {
                    studyCreateMenuOpen = false
                    selectedFolder = null
                    newFolderName = ""
                    folderDescriptionInput = ""
                    folderDialogMode = FolderDialogMode.CreateRoot
                },
                PremiumAction("Import file", Icons.Rounded.AttachFile, section = "CREATE") {
                    studyCreateMenuOpen = false
                    importPicker.launch(arrayOf("*/*"))
                },
                PremiumAction("Organise existing items", Icons.Rounded.SwapVert, section = "ORGANISE") {
                    studyCreateMenuOpen = false
                    manageMenuOpen = true
                },
            ),
        )
    }

    if (folderCreateMenuOpen && selectedFolder != null) {
        val folder = selectedFolder
        PremiumActionDialog(
            title = "Add inside ${folder?.name.orEmpty()}",
            onDismiss = { folderCreateMenuOpen = false },
            actions = listOf(
                PremiumAction("New note", Icons.AutoMirrored.Rounded.NoteAdd) {
                    folderCreateMenuOpen = false
                    onNewNoteClick(folder?.id)
                },
                PremiumAction("New subfolder", Icons.Rounded.CreateNewFolder) {
                    folderCreateMenuOpen = false
                    newFolderName = ""
                    folderDescriptionInput = ""
                    folderDialogMode = FolderDialogMode.CreateSubfolder
                },
                PremiumAction("New sticky note", Icons.Rounded.Description) {
                    folderCreateMenuOpen = false
                    stickyNoteInput = ""
                    stickyNoteDialogOpen = true
                },
            ),
        )
    }

    if (folderActionsOpen && selectedFolder != null) {
        val folder = selectedFolder
        val oppositeMode = if (currentFolderMode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeModeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal Workspace" else "Islamic Corpus"
        PremiumActionDialog(
            title = folder?.name.orEmpty(),
            onDismiss = { folderActionsOpen = false },
            actions = listOf(
                PremiumAction("Open", Icons.Rounded.FolderOpen) {
                    folderActionsOpen = false
                    folder?.let {
                        onFolderExpandedChange(folderExpansionKey(it.id), !isFolderExpanded(it.id))
                    }
                },
                PremiumAction("New note", Icons.AutoMirrored.Rounded.NoteAdd) {
                    folderActionsOpen = false
                    onNewNoteClick(folder?.id)
                },
                PremiumAction("+ Subfolder", Icons.Rounded.CreateNewFolder) {
                    folderActionsOpen = false
                    newFolderName = ""
                    folderDescriptionInput = ""
                    folderDialogMode = FolderDialogMode.CreateSubfolder
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    folderActionsOpen = false
                    moveFolderDialogOpen = true
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    folderActionsOpen = false
                    deleteFolderDialogOpen = true
                },
                PremiumAction("More actions", Icons.Rounded.MoreHoriz) {
                    folderActionsOpen = false
                    folderMoreActionsOpen = true
                },
            ),
        )
    }

    if (folderMoreActionsOpen && selectedFolder != null) {
        val folder = selectedFolder
        val oppositeMode = if (currentFolderMode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeModeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal Workspace" else "Islamic Corpus"
        PremiumActionDialog(
            title = "More actions",
            onDismiss = { folderMoreActionsOpen = false },
            actions = listOf(
                PremiumAction("Rename / edit description", Icons.Rounded.DriveFileRenameOutline) {
                    folderMoreActionsOpen = false
                    newFolderName = folder?.name.orEmpty()
                    folderDescriptionInput = folder?.description.orEmpty()
                    folderDialogMode = FolderDialogMode.Rename
                },
                PremiumAction("Organise", Icons.Rounded.SwapVert) {
                    folderMoreActionsOpen = false
                    selectedItemIds.clear()
                    manageSelectionMode = false
                    sortMode = WorkspaceSortMode.FoldersFirst
                    organizeMode = true
                },
                PremiumAction("Move to $oppositeModeLabel", Icons.Rounded.LocalOffer) {
                    folderMoreActionsOpen = false
                    folder?.let { onMoveFolderToModeClick(it.id, oppositeMode) }
                },
            ),
        )
    }

    if (utilityMenuOpen) {
        PremiumActionDialog(
            title = "Manage workspace",
            onDismiss = { utilityMenuOpen = false },
            actions = listOf(
                PremiumAction("Sort workspace", Icons.Rounded.SortByAlpha) {
                    utilityMenuOpen = false
                    sortMenuOpen = true
                },
                PremiumAction("Manage workspace", Icons.Rounded.SwapVert) {
                    utilityMenuOpen = false
                    manageMenuOpen = true
                },
            ),
        )
    }

    if (quickBackupConfirmOpen) {
        AlertDialog(
            onDismissRequest = { quickBackupConfirmOpen = false },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            title = { Text("Push to Google Drive?") },
            text = {
                Text(
                    "This will push this device's latest vault to Google Drive. Make sure this phone has your latest notes before continuing.",
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
                    Text("Push now")
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
        val oppositeMode = if (currentFolderMode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeModeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal Workspace" else "Islamic Corpus"
        PremiumActionDialog(
            title = note?.name.orEmpty(),
            onDismiss = { noteActionsOpen = false },
            actions = listOf(
                PremiumAction("Open", Icons.Rounded.Description) {
                    noteActionsOpen = false
                    note?.let { onNoteClick(it.id) }
                },
                PremiumAction("Create sub-note", Icons.AutoMirrored.Rounded.NoteAdd) {
                    note?.let { onCreateSubNoteClick(it.id) }
                    noteActionsOpen = false
                },
                PremiumAction("Move", Icons.Rounded.Folder) {
                    noteActionsOpen = false
                    moveNoteDialogOpen = true
                },
                PremiumAction(if (note?.pinned == true) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                    note?.let { onSetNotePinnedClick(it.id, !it.pinned) }
                    noteActionsOpen = false
                },
                PremiumAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    noteActionsOpen = false
                    deleteNoteDialogOpen = true
                },
                PremiumAction("More actions", Icons.Rounded.MoreHoriz) {
                    noteActionsOpen = false
                    noteMoreActionsOpen = true
                },
            ),
        )
    }

    if (noteMoreActionsOpen && selectedNote != null) {
        val note = selectedNote
        val oppositeMode = if (currentFolderMode == FOLDER_MODE_PERSONAL) FOLDER_MODE_STUDY else FOLDER_MODE_PERSONAL
        val oppositeModeLabel = if (oppositeMode == FOLDER_MODE_PERSONAL) "Personal Workspace" else "Islamic Corpus"
        PremiumActionDialog(
            title = "More actions",
            onDismiss = { noteMoreActionsOpen = false },
            actions = listOf(
                PremiumAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    noteMoreActionsOpen = false
                    noteTitleInput = note?.name.orEmpty()
                    renameNoteDialogOpen = true
                },
                PremiumAction(if (note?.favourite == true) "Unfavourite" else "Favourite", Icons.Rounded.Star) {
                    note?.let { onSetNoteFavouriteClick(it.id, !it.favourite) }
                    noteMoreActionsOpen = false
                },
                PremiumAction(
                    if (note?.folderPinned == true) "Unpin within folder" else "Pin within folder",
                    Icons.Rounded.PushPin,
                ) {
                    note?.let { onSetNoteFolderPinnedClick(it.id, !it.folderPinned) }
                    noteMoreActionsOpen = false
                },
                PremiumAction("Move to $oppositeModeLabel", Icons.Rounded.LocalOffer) {
                    note?.let { onMoveNoteToModeClick(it.id, oppositeMode) }
                    noteMoreActionsOpen = false
                },
            ),
        )
    }

    if (stickyNoteDialogOpen && selectedFolder != null) {
        VaultFormModal(
            title = "New sticky note",
            confirmLabel = "Create",
            enabled = stickyNoteInput.isNotBlank(),
            icon = Icons.Rounded.Description,
            onDismiss = { stickyNoteDialogOpen = false },
            onConfirm = {
                selectedFolder?.let { onNewStickyNoteClick(it.id, stickyNoteInput) }
                stickyNoteInput = ""
                stickyNoteDialogOpen = false
            },
        ) {
            VaultTextField(
                value = stickyNoteInput,
                onValueChange = { stickyNoteInput = it },
                label = "Sticky note",
                singleLine = false,
            )
        }
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
        VaultFormModal(
            title = "Rename note",
            confirmLabel = "Save",
            enabled = noteTitleInput.isNotBlank(),
            icon = Icons.Rounded.Description,
            onDismiss = { renameNoteDialogOpen = false },
            onConfirm = {
                selectedNote?.let { onRenameNoteClick(it.id, noteTitleInput) }
                noteTitleInput = ""
                renameNoteDialogOpen = false
            },
        ) {
            VaultTextField(
                value = noteTitleInput,
                onValueChange = { noteTitleInput = it },
                label = "Note title",
                singleLine = true,
            )
        }
    }

    if (deleteNoteDialogOpen && selectedNote != null) {
        val note = selectedNote
        VaultConfirmModal(
            title = "Move ${note?.name.orEmpty()}?",
            message = "This note will move to Recently Deleted. You can restore it from Settings.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteNoteDialogOpen = false },
            onConfirm = {
                note?.let { onDeleteNoteClick(it.id) }
                deleteNoteDialogOpen = false
                selectedNote = null
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
        VaultConfirmModal(
            title = "Move ${folder?.name.orEmpty()}?",
            message = "This will also move its subfolders and notes to Recently Deleted. You can restore them from Settings.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteFolderDialogOpen = false },
            onConfirm = {
                folder?.let { onDeleteFolderClick(it.id) }
                deleteFolderDialogOpen = false
                selectedFolder = null
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
        VaultConfirmModal(
            title = "Move selected items?",
            message = "This will move ${selectedFolders.size} folder${if (selectedFolders.size == 1) "" else "s"} " +
                "and ${selectedNotes.size} note${if (selectedNotes.size == 1) "" else "s"} to Recently Deleted.",
            confirmLabel = "Move",
            dismissLabel = "Keep",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onDismiss = { deleteSelectedOpen = false },
            onConfirm = {
                selectedNotes.forEach { onDeleteNoteClick(it.id) }
                selectedFolders.forEach { onDeleteFolderClick(it.id) }
                selectedItemIds.clear()
                deleteSelectedOpen = false
                manageSelectionMode = false
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
                folderDescriptionInput = folder.description.orEmpty()
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
        VaultFormModal(
            title = when (dialogMode) {
                FolderDialogMode.CreateRoot -> "New folder"
                FolderDialogMode.CreateSubfolder -> "New subfolder"
                FolderDialogMode.Rename -> "Rename folder"
            },
            confirmLabel = if (dialogMode == FolderDialogMode.Rename) "Save" else "Create",
            enabled = newFolderName.isNotBlank(),
            icon = Icons.Rounded.Folder,
            onDismiss = { folderDialogMode = null },
            onConfirm = {
                when (dialogMode) {
                    FolderDialogMode.CreateRoot -> onNewFolderClick(null, newFolderName, folderDescriptionInput)
                    FolderDialogMode.CreateSubfolder -> onNewFolderClick(selectedFolder?.id, newFolderName, folderDescriptionInput)
                    FolderDialogMode.Rename -> selectedFolder?.let {
                        onRenameFolderClick(it.id, newFolderName, folderDescriptionInput)
                    }
                }
                newFolderName = ""
                folderDescriptionInput = ""
                folderDialogMode = null
            },
        ) {
            VaultTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = "Folder name",
                singleLine = true,
            )
            VaultTextField(
                value = folderDescriptionInput,
                onValueChange = { folderDescriptionInput = it },
                label = "Description (optional)",
                minLines = 2,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun StudyMobileWebContent(
    uiState: HomeUiState,
    displayedWorkspace: List<VaultTreeItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    isFolderExpanded: (String) -> Boolean,
    onToggleFolder: (VaultTreeItem) -> Unit,
    onCreateInside: (VaultTreeItem) -> Unit,
    onOpenNote: (String) -> Unit,
    onMore: (VaultTreeItem) -> Unit,
    organizeMode: Boolean,
    onDoneOrganizing: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val allFolders = remember(uiState.workspace) { uiState.workspace.flatMap { it.flattenFolders() } }
    val allNotes = remember(uiState.workspace) {
        uiState.workspace.flatMap { it.flattenNotes() }.distinctBy { it.id }
    }
    val pinnedNotes = remember(allNotes) { allNotes.filter { it.pinned }.take(3) }
    val searching = searchQuery.isNotBlank()
    val matchingFolders = remember(allFolders, searchQuery) {
        if (!searching) emptyList() else allFolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val matchingNotes = remember(allNotes, searchQuery) {
        if (!searching) emptyList() else allNotes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (searchOpen) 1f else 0f)
            .background(colors.bg),
        contentPadding = PaddingValues(
            start = 14.dp,
            top = 4.dp,
            end = 14.dp,
            bottom = 96.dp,
        ),
    ) {
        item(key = "corpus_study_header") {
            CorpusHeader(
                title = "Study",
                metadata = "${allFolders.size} ${if (allFolders.size == 1) "folder" else "folders"} · " +
                    "${allNotes.size} ${if (allNotes.size == 1) "note" else "notes"}",
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                searchPlaceholder = "Search notes and folders...",
                onSearchQueryChange = onSearchQueryChange,
                onSearchOpen = onSearchOpen,
                onSearchClose = onSearchClose,
                reserveNavigationSpace = true,
            )
            if (organizeMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDoneOrganizing)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text("Done", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.W700)
                }
            }
        }

        if (pinnedNotes.isNotEmpty() && !searching) {
            item(key = "corpus_study_pinned") {
                CorpusPinnedStrip(
                    items = pinnedNotes.map { note ->
                        CorpusPinnedItem(note.id, note.name, uiState.workspace.parentFolderName(note.id))
                    },
                    onClick = onOpenNote,
                    onLongPress = { id -> allNotes.firstOrNull { it.id == id }?.let(onMore) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        if (searching && matchingFolders.isEmpty() && matchingNotes.isEmpty()) {
            item(key = "corpus_study_empty") {
                CorpusEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "No matching notes or folders",
                )
            }
        } else if (searching) {
            item(key = "corpus_study_search_results") {
                Column {
                    val resultLabel = when {
                        matchingFolders.isEmpty() -> "${matchingNotes.size} ${if (matchingNotes.size == 1) "note" else "notes"}"
                        matchingNotes.isEmpty() -> "${matchingFolders.size} ${if (matchingFolders.size == 1) "folder" else "folders"}"
                        else -> "${matchingFolders.size + matchingNotes.size} results"
                    }
                    CorpusSearchSummary(resultLabel = resultLabel, contextLabel = "in Study")
                    matchingFolders.forEach { folder ->
                        CorpusFolderRow(
                            title = folder.name,
                            count = folder.count.takeIf { it > 0 } ?: folder.children.size,
                            expanded = false,
                            onToggle = { onToggleFolder(folder) },
                            onLongPress = { onMore(folder) },
                            onAdd = null,
                        )
                    }
                    matchingNotes.forEach { note ->
                        CorpusLeafRow(
                            title = note.name,
                            icon = Icons.Outlined.Description,
                            onClick = { onOpenNote(note.id) },
                            onLongPress = { onMore(note) },
                            pinned = note.pinned,
                            attachmentCount = note.attachmentCount,
                            showFullTitle = uiState.showFullNoteTitles,
                        )
                    }
                }
            }
        } else if (displayedWorkspace.isEmpty()) {
            item(key = "corpus_study_empty") {
                CorpusEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "No study items yet",
                )
            }
        } else {
            item(key = "corpus_study_tree") {
                Column {
                    displayedWorkspace.forEach { item ->
                        StudyCorpusItem(
                            item = item,
                            searching = false,
                            isFolderExpanded = isFolderExpanded,
                            onToggleFolder = onToggleFolder,
                            onCreateInside = onCreateInside,
                            onOpenNote = onOpenNote,
                            onMore = onMore,
                            showFullTitle = uiState.showFullNoteTitles,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyCorpusItem(
    item: VaultTreeItem,
    searching: Boolean,
    isFolderExpanded: (String) -> Boolean,
    onToggleFolder: (VaultTreeItem) -> Unit,
    onCreateInside: (VaultTreeItem) -> Unit,
    onOpenNote: (String) -> Unit,
    onMore: (VaultTreeItem) -> Unit,
    showFullTitle: Boolean,
) {
    if (item.type == VaultTreeItemType.Folder) {
        val expanded = searching || isFolderExpanded(item.id)
        CorpusFolderRow(
            title = item.name,
            count = item.count.takeIf { it > 0 } ?: item.children.size,
            expanded = expanded,
            onToggle = { onToggleFolder(item) },
            onLongPress = { onMore(item) },
            onAdd = null,
        )
        if (expanded) {
            item.children.forEach { child ->
                StudyCorpusItem(
                    item = child,
                    searching = searching,
                    isFolderExpanded = isFolderExpanded,
                    onToggleFolder = onToggleFolder,
                    onCreateInside = onCreateInside,
                    onOpenNote = onOpenNote,
                    onMore = onMore,
                    showFullTitle = showFullTitle,
                )
            }
        }
    } else {
        CorpusLeafRow(
            title = item.name,
            icon = Icons.Outlined.Description,
            onClick = { onOpenNote(item.id) },
            onLongPress = { onMore(item) },
            pinned = item.pinned,
            attachmentCount = item.attachmentCount,
            showFullTitle = showFullTitle,
        )
    }
}

@Composable
private fun StudyViewButton(
    icon: ImageVector,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    CompactViewAction(
        icon = icon,
        selected = selected,
        description = description,
        onClick = onClick,
    )
}

@Composable
private fun StudyVisualFolderCard(
    folder: VaultTreeItem,
    compact: Boolean,
    onOpen: () -> Unit,
    onCreateInside: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 132.dp else 144.dp),
        color = colors.surface,
        shape = VaultShapes.sm,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 30.dp else 38.dp),
                    tint = colors.accent,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconBtn(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More actions for ${folder.name}",
                        onClick = onMore,
                    )
                    IconBtn(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add inside ${folder.name}",
                        active = true,
                        onClick = onCreateInside,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = folder.name,
                modifier = Modifier.fillMaxWidth(),
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.children.count { it.type == VaultTreeItemType.Folder }} folders · ${folder.flattenNotes().size} notes",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = colors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudyRecentNoteRow(
    note: VaultTreeItem,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = VaultShapes.sm,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (note.pinned) Icons.Rounded.PushPin else Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (note.pinned) colors.accent else colors.textMuted,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = note.name,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.preview.isNotBlank()) {
                    Text(
                        text = note.preview,
                        modifier = Modifier.padding(top = 2.dp),
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconBtn(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "More actions for ${note.name}",
                onClick = onMore,
            )
        }
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
                modifier = Modifier.weight(1f),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}



@Composable
private fun HomeQuranReflectionCard(
    reflection: QuranReflectionItem,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 214.dp, height = 132.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${reflection.surahName} ${reflection.surahNumber}:${reflection.ayahNumber}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W900),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reflection.title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reflection.reflectionPreview.ifBlank { reflection.translationPreview }.ifBlank { "Open reflection" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class FolderDialogMode {
    CreateRoot,
    CreateSubfolder,
    Rename,
}

internal data class PremiumAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val section: String? = null,
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

@Composable
private fun QuranReflectionsEntryCard(
    count: Int,
    latestReference: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = VaultShapes.sm,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.accent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "Qur'an Reflections ($count)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (latestReference.isBlank()) "No reflections yet" else "Last updated: $latestReference",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    onAttachmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val hasResults = uiState.searchNotes.isNotEmpty() ||
        uiState.searchFolders.isNotEmpty() ||
        uiState.searchAttachments.isNotEmpty()

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
                    text = "Workspace search",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                )
            }

            if (!hasResults) {
                Text(
                    text = "No matching notes, folders, or files in this workspace.",
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

            uiState.searchAttachments.take(5).forEach { attachment ->
                InlineAttachmentResult(attachment = attachment, onClick = { onAttachmentClick(attachment.id) })
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
private fun InlineAttachmentResult(
    attachment: AttachmentSample,
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
                    Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(15.dp), tint = colors.accent)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${attachment.kind} · ${attachment.note}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PremiumActionDialog(
    title: String,
    actions: List<PremiumAction>,
    onDismiss: () -> Unit,
) {
    val groups = actions.fold(mutableListOf<CorpusActionGroup>()) { result, action ->
        val mapped = CorpusAction(action.label, action.icon, action.destructive, onClick = action.onClick)
        val last = result.lastOrNull()
        if (last != null && last.label == action.section) {
            result[result.lastIndex] = last.copy(actions = last.actions + mapped)
        } else {
            result += CorpusActionGroup(label = action.section, actions = listOf(mapped))
        }
        result
    }
    CorpusActionSheet(
        title = title,
        onDismiss = onDismiss,
        groups = groups,
    )
}

private fun VaultTreeItem.flattenFolders(): List<VaultTreeItem> =
    if (type == VaultTreeItemType.Folder) {
        listOf(this) + children.flatMap { it.flattenFolders() }
    } else {
        emptyList()
    }

private fun List<VaultTreeItem>.parentFolderName(itemId: String): String? {
    forEach { folder ->
        if (folder.type == VaultTreeItemType.Folder) {
            if (folder.children.any { it.id == itemId }) return folder.name
            folder.children.parentFolderName(itemId)?.let { return it }
        }
    }
    return null
}

private fun List<VaultTreeItem>.selectedTreeItems(selectedItemIds: Map<String, Boolean>): List<VaultTreeItem> {
    val selected = mutableListOf<VaultTreeItem>()
    forEach { it.collectSelectedItems(selectedItemIds, selected) }
    return selected
}

private fun VaultTreeItem.collectSelectedItems(
    selectedItemIds: Map<String, Boolean>,
    selected: MutableList<VaultTreeItem>,
) {
    if (selectedItemIds[id] == true) selected += this
    children.forEach { it.collectSelectedItems(selectedItemIds, selected) }
}

private fun VaultTreeItem.flattenNotes(): List<VaultTreeItem> =
    if (type == VaultTreeItemType.Note) {
        listOf(this) + children.flatMap { it.flattenNotes() }
    } else {
        children.flatMap { it.flattenNotes() }
    }

private fun List<VaultTreeItem>.filterStudyTree(query: String): List<VaultTreeItem> {
    val term = query.trim()
    if (term.isBlank()) return this
    return mapNotNull { it.filterStudyNode(term) }
}

private fun VaultTreeItem.filterStudyNode(term: String): VaultTreeItem? {
    val matchingChildren = children.mapNotNull { it.filterStudyNode(term) }
    val matches = name.contains(term, ignoreCase = true) ||
        description.orEmpty().contains(term, ignoreCase = true) ||
        preview.contains(term, ignoreCase = true)
    return when {
        matches -> this
        matchingChildren.isNotEmpty() -> copy(children = matchingChildren)
        else -> null
    }
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
    showFullTitles: Boolean,
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
                items(notes, key = { it.id }) { note ->
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
                                    maxLines = if (showFullTitles) Int.MAX_VALUE else 1,
                                    overflow = if (showFullTitles) TextOverflow.Clip else TextOverflow.Ellipsis,
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
            val normallySorted = when (mode) {
                WorkspaceSortMode.FoldersFirst -> items.sortedBy { it.type != VaultTreeItemType.Folder }
                WorkspaceSortMode.Name -> items.sortedBy { it.name.lowercase() }
                WorkspaceSortMode.RecentlyEdited -> items.sortedByDescending { it.updatedAt }
            }
            normallySorted.partition { item ->
                item.type == VaultTreeItemType.Note && (item.pinned || item.folderPinned)
            }.let { (pinnedNotes, remainingItems) -> pinnedNotes + remainingItems }
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
