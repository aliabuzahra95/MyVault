package com.myvault.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.CompactActionGroup
import com.myvault.app.ui.components.CompactPrimaryAction
import com.myvault.app.ui.components.CompactViewAction
import com.myvault.app.ui.components.CompactWorkspaceHeader
import com.myvault.app.ui.components.CorpusAction
import com.myvault.app.ui.components.CorpusActionGroup
import com.myvault.app.ui.components.CorpusActionSheet
import com.myvault.app.ui.components.CorpusEmptyState
import com.myvault.app.ui.components.CorpusExpandedChildren
import com.myvault.app.ui.components.CorpusFab
import com.myvault.app.ui.components.CorpusFolderRow
import com.myvault.app.ui.components.CorpusFolderColorSheet
import com.myvault.app.ui.components.CorpusHeader
import com.myvault.app.ui.components.CorpusLeafRow
import com.myvault.app.ui.components.CorpusPinnedItem
import com.myvault.app.ui.components.CorpusPinnedStrip
import com.myvault.app.ui.components.CorpusSearchSummary
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.FloatingActionMenuExpansion
import com.myvault.app.ui.components.FloatingActionStackDefaults
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.PinnedNoteCard
import com.myvault.app.ui.components.SearchBar
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultActionModal
import com.myvault.app.ui.components.VaultModalAction
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryAnnotationItem
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.LibraryViewMode

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
    onReferenceNoteClick: (String) -> Unit,
    workspaceTitle: String = "Islamic Corpus",
    workspaceOptions: List<String> = emptyList(),
    onWorkspaceSelected: (String) -> Unit = {},
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onLinkAnnotationToStudyNote: (String, String) -> Unit,
    onCreateStudyNoteFromAnnotation: (String) -> Unit,
    onPrepareStudyNoteLinks: () -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onUpdateFolderColor: (folderId: String, colorKey: String?) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onImportFilesToFolder: (String?, List<Uri>) -> Unit = { _, uris -> onImportFiles(uris) },
    onReplaceDuplicatePdf: () -> Unit,
    onSkipDuplicatePdf: () -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
    onExportFile: (fileId: String, destination: Uri) -> Unit,
    onAddAttachmentTag: (String, String) -> Unit,
    onRemoveAttachmentTag: (String, String) -> Unit,
    onAddAnnotationTag: (String, String) -> Unit,
    onRemoveAnnotationTag: (String, String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean = false,
    showFullFileTitles: Boolean = false,
    onViewAllAnnotationsClick: () -> Unit = {},
    fabBottomPadding: Dp = FloatingActionStackDefaults.fabBottomPadding,
    modifier: Modifier = Modifier,
    onCorpusSearchActiveChange: (Boolean) -> Unit = {},
) {
    LibraryArchiveScreen(
        title = "Library",
        subtitle = null,
        workspaceTitle = workspaceTitle,
        workspaceOptions = workspaceOptions,
        onWorkspaceSelected = onWorkspaceSelected,
        uiState = uiState,
        onBackClick = null,
        currentFolderId = null,
        onFolderClick = onFolderClick,
        onAttachmentClick = onAttachmentClick,
        onAnnotationClick = onAnnotationClick,
        onReferenceNoteClick = onReferenceNoteClick,
        onRenameAnnotation = onRenameAnnotation,
        onMoveAnnotation = onMoveAnnotation,
        onDeleteAnnotationNote = onDeleteAnnotationNote,
        onDeleteAnnotation = onDeleteAnnotation,
        onLinkAnnotationToStudyNote = onLinkAnnotationToStudyNote,
        onCreateStudyNoteFromAnnotation = onCreateStudyNoteFromAnnotation,
        onPrepareStudyNoteLinks = onPrepareStudyNoteLinks,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onUpdateFolderColor = onUpdateFolderColor,
        onMoveFolder = onMoveFolder,
        onMoveFolderInOrder = onMoveFolderInOrder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
        onImportFilesToFolder = onImportFilesToFolder,
        onReplaceDuplicatePdf = onReplaceDuplicatePdf,
        onSkipDuplicatePdf = onSkipDuplicatePdf,
        onDismissImportMessage = onDismissImportMessage,
        onRenameFile = onRenameFile,
        onMoveFile = onMoveFile,
        onSetFilePinned = onSetFilePinned,
        onDeleteFile = onDeleteFile,
        onExportFile = onExportFile,
        onAddAttachmentTag = onAddAttachmentTag,
        onRemoveAttachmentTag = onRemoveAttachmentTag,
        onAddAnnotationTag = onAddAnnotationTag,
        onRemoveAnnotationTag = onRemoveAnnotationTag,
        onThemeClick = onThemeClick,
        onQuickBackupClick = onQuickBackupClick,
        onSettingsClick = onSettingsClick,
        quickBackupRecommended = quickBackupRecommended,
        showFullFileTitles = showFullFileTitles,
        onViewAllAnnotationsClick = onViewAllAnnotationsClick,
        fabBottomPadding = fabBottomPadding,
        modifier = modifier,
        onCorpusSearchActiveChange = onCorpusSearchActiveChange,
    )
}

@Composable
fun LibraryFolderScreen(
    uiState: LibraryUiState,
    onBackClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
    onReferenceNoteClick: (String) -> Unit,
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onLinkAnnotationToStudyNote: (String, String) -> Unit,
    onCreateStudyNoteFromAnnotation: (String) -> Unit,
    onPrepareStudyNoteLinks: () -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onUpdateFolderColor: (folderId: String, colorKey: String?) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onImportFilesToFolder: (String?, List<Uri>) -> Unit = { _, uris -> onImportFiles(uris) },
    onReplaceDuplicatePdf: () -> Unit,
    onSkipDuplicatePdf: () -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
    onExportFile: (fileId: String, destination: Uri) -> Unit,
    onAddAttachmentTag: (String, String) -> Unit,
    onRemoveAttachmentTag: (String, String) -> Unit,
    onAddAnnotationTag: (String, String) -> Unit,
    onRemoveAnnotationTag: (String, String) -> Unit,
    onViewAllAnnotationsClick: () -> Unit,
    showFullFileTitles: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val folder = uiState.currentFolder
    LibraryArchiveScreen(
        title = folder?.name ?: "Library Folder",
        subtitle = "${uiState.folders.size} subfolders · ${uiState.files.size} files",
        uiState = uiState,
        onBackClick = onBackClick,
        currentFolderId = folder?.id,
        onFolderClick = onFolderClick,
        onAttachmentClick = onAttachmentClick,
        onAnnotationClick = onAnnotationClick,
        onReferenceNoteClick = onReferenceNoteClick,
        onRenameAnnotation = onRenameAnnotation,
        onMoveAnnotation = onMoveAnnotation,
        onDeleteAnnotationNote = onDeleteAnnotationNote,
        onDeleteAnnotation = onDeleteAnnotation,
        onLinkAnnotationToStudyNote = onLinkAnnotationToStudyNote,
        onCreateStudyNoteFromAnnotation = onCreateStudyNoteFromAnnotation,
        onPrepareStudyNoteLinks = onPrepareStudyNoteLinks,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onUpdateFolderColor = onUpdateFolderColor,
        onMoveFolder = onMoveFolder,
        onMoveFolderInOrder = onMoveFolderInOrder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
        onImportFilesToFolder = onImportFilesToFolder,
        onReplaceDuplicatePdf = onReplaceDuplicatePdf,
        onSkipDuplicatePdf = onSkipDuplicatePdf,
        onDismissImportMessage = onDismissImportMessage,
        onRenameFile = onRenameFile,
        onMoveFile = onMoveFile,
        onSetFilePinned = onSetFilePinned,
        onDeleteFile = onDeleteFile,
        onExportFile = onExportFile,
        onAddAttachmentTag = onAddAttachmentTag,
        onRemoveAttachmentTag = onRemoveAttachmentTag,
        onAddAnnotationTag = onAddAnnotationTag,
        onRemoveAnnotationTag = onRemoveAnnotationTag,
        showFullFileTitles = showFullFileTitles,
        onViewAllAnnotationsClick = onViewAllAnnotationsClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryArchiveScreen(
    title: String,
    subtitle: String?,
    workspaceTitle: String = title,
    workspaceOptions: List<String> = emptyList(),
    onWorkspaceSelected: (String) -> Unit = {},
    uiState: LibraryUiState,
    onBackClick: (() -> Unit)?,
    currentFolderId: String?,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
    onReferenceNoteClick: (String) -> Unit,
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onLinkAnnotationToStudyNote: (String, String) -> Unit,
    onCreateStudyNoteFromAnnotation: (String) -> Unit,
    onPrepareStudyNoteLinks: () -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onUpdateFolderColor: (folderId: String, colorKey: String?) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onImportFilesToFolder: (String?, List<Uri>) -> Unit,
    onReplaceDuplicatePdf: () -> Unit,
    onSkipDuplicatePdf: () -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
    onExportFile: (fileId: String, destination: Uri) -> Unit,
    onAddAttachmentTag: (String, String) -> Unit,
    onRemoveAttachmentTag: (String, String) -> Unit,
    onAddAnnotationTag: (String, String) -> Unit,
    onRemoveAnnotationTag: (String, String) -> Unit,
    onThemeClick: () -> Unit = {},
    onQuickBackupClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    quickBackupRecommended: Boolean = false,
    showFullFileTitles: Boolean = false,
    onViewAllAnnotationsClick: () -> Unit = {},
    fabBottomPadding: Dp = FloatingActionStackDefaults.fabBottomPadding,
    modifier: Modifier = Modifier,
    onCorpusSearchActiveChange: (Boolean) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    var folderDialog by remember { mutableStateOf<LibraryFolderDialog?>(null) }
    var folderName by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<LibraryFolderItem?>(null) }
    var selectedFile by remember { mutableStateOf<LibraryFileItem?>(null) }
    var selectedAnnotation by remember { mutableStateOf<LibraryAnnotationItem?>(null) }
    var actionDialogOpen by remember { mutableStateOf(false) }
    var folderMoreActionsOpen by remember { mutableStateOf(false) }
    var folderColorSheetOpen by remember { mutableStateOf(false) }
    var fileActionDialogOpen by remember { mutableStateOf(false) }
    var fileMoreActionsOpen by remember { mutableStateOf(false) }
    var annotationActionDialogOpen by remember { mutableStateOf(false) }
    var moveDialogOpen by remember { mutableStateOf(false) }
    var fileMoveDialogOpen by remember { mutableStateOf(false) }
    var annotationMoveDialogOpen by remember { mutableStateOf(false) }
    var annotationRenameDialogOpen by remember { mutableStateOf(false) }
    var annotationDeleteDialogOpen by remember { mutableStateOf(false) }
    var annotationLinkDialogOpen by remember { mutableStateOf(false) }
    var fileTagDialogOpen by remember { mutableStateOf(false) }
    var annotationTagDialogOpen by remember { mutableStateOf(false) }
    var fileRemoveTagDialogOpen by remember { mutableStateOf(false) }
    var annotationRemoveTagDialogOpen by remember { mutableStateOf(false) }
    var fileRenameDialogOpen by remember { mutableStateOf(false) }
    var folderDeleteDialogOpen by remember { mutableStateOf(false) }
    var fileDeleteDialogOpen by remember { mutableStateOf(false) }
    var quickBackupConfirmOpen by remember { mutableStateOf(false) }
    var displayModeDialogOpen by remember { mutableStateOf(false) }
    var librarySearchOpen by remember { mutableStateOf(false) }
    LaunchedEffect(librarySearchOpen) {
        onCorpusSearchActiveChange(librarySearchOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onCorpusSearchActiveChange(false) }
    }
    var librarySearchQuery by remember { mutableStateOf("") }
    var rootCreateMenuOpen by remember { mutableStateOf(false) }
    var folderCreateMenuOpen by remember { mutableStateOf(false) }
    var importTargetFolderId by remember { mutableStateOf<String?>(null) }
    val closeLibrarySearch = {
        librarySearchOpen = false
        librarySearchQuery = ""
    }
    val toggleLibrarySearch = {
        if (librarySearchOpen) closeLibrarySearch() else librarySearchOpen = true
    }
    var annotationTitle by remember { mutableStateOf("") }
    var tagDraft by remember { mutableStateOf("") }
    BackHandler(enabled = librarySearchOpen) {
        closeLibrarySearch()
    }
    val multiImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImportFilesToFolder(importTargetFolderId, uris)
    }
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = selectedFile
        if (uri != null && file != null) {
            onExportFile(file.id, uri)
        }
    }
    val allFolders = remember(uiState.folders) { uiState.folders.flatMap { it.flatten() } }
    val allFiles = remember(uiState.folders, uiState.files) {
        (uiState.files + allFolders.flatMap { it.files }).distinctBy { it.id }
    }
    val query = librarySearchQuery.trim()
    val matchingFolders = remember(allFolders, query) {
        if (query.isBlank()) emptyList() else allFolders.filter { it.name.contains(query, ignoreCase = true) }
    }
    val matchingFiles = remember(allFiles, query) {
        if (query.isBlank()) emptyList() else allFiles.filter { it.name.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (librarySearchOpen) 1f else 0f)
            .background(colors.bg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 96.dp),
        ) {
            item(key = "corpus_library_header") {
                CorpusHeader(
                    title = title,
                    metadata = "${allFolders.size} ${if (allFolders.size == 1) "folder" else "folders"} · " +
                        "${allFiles.size} ${if (allFiles.size == 1) "file" else "files"}",
                    searchOpen = librarySearchOpen,
                    searchQuery = librarySearchQuery,
                    searchPlaceholder = "Search folders and files...",
                    onSearchQueryChange = { librarySearchQuery = it },
                    onSearchOpen = { librarySearchOpen = true },
                    onSearchClose = closeLibrarySearch,
                    reserveNavigationSpace = true,
                )
            }

            if (uiState.pinnedFiles.isNotEmpty() && query.isBlank()) {
                item(key = "corpus_library_pinned") {
                    CorpusPinnedStrip(
                        items = uiState.pinnedFiles.take(3).map { file ->
                            CorpusPinnedItem(file.id, file.name, allFolders.parentFolderName(file.id))
                        },
                        onClick = onAttachmentClick,
                        onLongPress = { id ->
                            allFiles.firstOrNull { it.id == id }?.let {
                                selectedFile = it
                                fileActionDialogOpen = true
                            }
                        },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }

            if (query.isNotBlank()) {
                if (matchingFolders.isEmpty() && matchingFiles.isEmpty()) {
                    item(key = "corpus_library_no_results") {
                        CorpusEmptyState(Icons.Rounded.Search, "No matching folders or files")
                    }
                } else {
                    item(key = "corpus_library_search_results") {
                        Column {
                            val resultLabel = when {
                                matchingFolders.isEmpty() -> "${matchingFiles.size} ${if (matchingFiles.size == 1) "file" else "files"}"
                                matchingFiles.isEmpty() -> "${matchingFolders.size} ${if (matchingFolders.size == 1) "folder" else "folders"}"
                                else -> "${matchingFolders.size + matchingFiles.size} results"
                            }
                            CorpusSearchSummary(resultLabel = resultLabel, contextLabel = "in Library")
                            matchingFolders.forEach { folderItem ->
                                CorpusFolderRow(
                                    title = folderItem.name,
                                    colorKey = folderItem.colorKey,
                                    count = folderItem.count,
                                    expanded = false,
                                    onToggle = { onFolderClick(folderItem.id) },
                                    onLongPress = {
                                        selectedFolder = folderItem
                                        actionDialogOpen = true
                                    },
                                    onAdd = {
                                        selectedFolder = folderItem
                                        folderCreateMenuOpen = true
                                    },
                                )
                            }
                            matchingFiles.forEach { file ->
                                LibraryCorpusFileRow(
                                    file = file,
                                    showFullFileTitles = showFullFileTitles,
                                    onAttachmentClick = onAttachmentClick,
                                    onLongPress = {
                                        selectedFile = file
                                        fileActionDialogOpen = true
                                    },
                                )
                            }
                        }
                    }
                }
            } else if (uiState.folders.isEmpty() && uiState.files.isEmpty()) {
                item(key = "corpus_library_empty") {
                    CorpusEmptyState(Icons.Rounded.FolderOpen, "No Library items yet")
                }
            } else {
                item(key = "corpus_library_tree") {
                    Column {
                        uiState.folders.forEach { folderItem ->
                            LibraryCorpusFolderItem(
                                folder = folderItem,
                                expandedFolderIds = uiState.expandedFolderIds,
                                showFullFileTitles = showFullFileTitles,
                                onFolderExpandedChange = onFolderExpandedChange,
                                onFolderCreate = {
                                    selectedFolder = it
                                    folderCreateMenuOpen = true
                                },
                                onFolderLongPress = {
                                    selectedFolder = it
                                    actionDialogOpen = true
                                },
                                onAttachmentClick = onAttachmentClick,
                                onFileLongPress = {
                                    selectedFile = it
                                    fileActionDialogOpen = true
                                },
                                depth = 0,
                            )
                        }
                        uiState.files.forEach { file ->
                            LibraryCorpusFileRow(
                                file = file,
                                showFullFileTitles = showFullFileTitles,
                                onAttachmentClick = onAttachmentClick,
                                onLongPress = {
                                    selectedFile = file
                                    fileActionDialogOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }

        CorpusFab(
            onClick = { rootCreateMenuOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (fabBottomPadding < 18.dp) 18.dp else fabBottomPadding),
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
                    "This will push this device's latest vault to Google Drive. Make sure this phone has your latest Library files and notes before continuing.",
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

    if (displayModeDialogOpen) {
        LibraryActionDialog(
            title = "Display",
            actions = LibraryViewMode.entries.map { mode ->
                LibraryAction(
                    label = mode.label,
                    icon = when (mode) {
                        LibraryViewMode.List -> Icons.Rounded.ViewList
                        LibraryViewMode.Grid -> Icons.Rounded.GridView
                        LibraryViewMode.Icons -> Icons.Rounded.Apps
                    },
                    selected = mode == uiState.viewMode,
                ) {
                    onViewModeChange(mode)
                    displayModeDialogOpen = false
                }
            },
            onDismiss = { displayModeDialogOpen = false },
        )
    }

    if (rootCreateMenuOpen) {
        LibraryActionDialog(
            title = "Add to Library",
            actions = listOf(
                LibraryAction("Upload file", Icons.Rounded.UploadFile, section = "CREATE") {
                    rootCreateMenuOpen = false
                    importTargetFolderId = currentFolderId
                    multiImportPicker.launch(arrayOf("*/*"))
                },
                LibraryAction("New folder", Icons.Rounded.CreateNewFolder, section = "CREATE") {
                    rootCreateMenuOpen = false
                    folderName = ""
                    folderDialog = LibraryFolderDialog.Create(parentId = currentFolderId)
                },
            ),
            onDismiss = { rootCreateMenuOpen = false },
        )
    }

    if (folderCreateMenuOpen) {
        val targetFolder = selectedFolder
        LibraryActionDialog(
            title = targetFolder?.name ?: "Folder",
            actions = listOf(
                LibraryAction("Upload file", Icons.Rounded.UploadFile, section = "CREATE") {
                    folderCreateMenuOpen = false
                    importTargetFolderId = targetFolder?.id
                    multiImportPicker.launch(arrayOf("*/*"))
                },
                LibraryAction("New subfolder", Icons.Rounded.CreateNewFolder, section = "CREATE") {
                    folderCreateMenuOpen = false
                    folderName = ""
                    folderDialog = LibraryFolderDialog.Create(parentId = targetFolder?.id)
                },
            ),
            onDismiss = { folderCreateMenuOpen = false },
        )
    }

    if (uiState.importing || !uiState.importMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.importing) onDismissImportMessage()
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            title = { Text(if (uiState.importing) "Importing files" else "Library import") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    if (uiState.importing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = uiState.importMessage ?: "Importing selected files...",
                        color = colors.textSecondary,
                    )
                }
            },
            confirmButton = {
                if (!uiState.importing) {
                    TextButton(onClick = onDismissImportMessage) {
                        Text("OK")
                    }
                }
            },
        )
    }

    uiState.duplicatePdfImport?.let { duplicate ->
        LibraryActionDialog(
            title = "${duplicate.fileName} already exists",
            actions = listOf(
                LibraryAction("Replace existing PDF", Icons.Rounded.UploadFile) {
                    onReplaceDuplicatePdf()
                },
                LibraryAction("Skip this file", Icons.Rounded.InsertDriveFile) {
                    onSkipDuplicatePdf()
                },
            ),
            onDismiss = onSkipDuplicatePdf,
        )
    }

    if (actionDialogOpen && selectedFolder != null) {
        LibraryActionDialog(
            title = selectedFolder?.name.orEmpty(),
            actions = listOf(
                LibraryAction("Open", Icons.Rounded.FolderOpen) {
                    selectedFolder?.let { onFolderClick(it.id) }
                    actionDialogOpen = false
                },
                LibraryAction("Move", Icons.Rounded.DriveFileMove) {
                    actionDialogOpen = false
                    moveDialogOpen = true
                },
                LibraryAction("More actions", Icons.Rounded.MoreVert) {
                    actionDialogOpen = false
                    folderMoreActionsOpen = true
                },
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    actionDialogOpen = false
                    folderDeleteDialogOpen = true
                },
            ),
            onDismiss = { actionDialogOpen = false },
        )
    }

    if (folderMoreActionsOpen && selectedFolder != null) {
        LibraryActionDialog(
            title = "More actions",
            actions = listOf(
                LibraryAction("New subfolder", Icons.Rounded.CreateNewFolder) {
                    val parent = selectedFolder?.id
                    folderMoreActionsOpen = false
                    folderName = ""
                    folderDialog = LibraryFolderDialog.Create(parentId = parent)
                },
                LibraryAction("Rename", Icons.Rounded.Edit) {
                    folderMoreActionsOpen = false
                    folderName = selectedFolder?.name.orEmpty()
                    folderDialog = selectedFolder?.let { LibraryFolderDialog.Rename(it.id) }
                },
                LibraryAction("Change colour", Icons.Rounded.Palette) {
                    folderMoreActionsOpen = false
                    folderColorSheetOpen = true
                },
                LibraryAction("Move up", Icons.Rounded.KeyboardArrowUp) {
                    selectedFolder?.let { onMoveFolderInOrder(it.id, -1) }
                    folderMoreActionsOpen = false
                },
                LibraryAction("Move down", Icons.Rounded.KeyboardArrowDown) {
                    selectedFolder?.let { onMoveFolderInOrder(it.id, 1) }
                    folderMoreActionsOpen = false
                },
            ),
            onDismiss = { folderMoreActionsOpen = false },
        )
    }

    if (folderColorSheetOpen && selectedFolder != null) {
        val folder = selectedFolder
        CorpusFolderColorSheet(
            folderName = folder?.name.orEmpty(),
            selectedColorKey = folder?.colorKey,
            onBack = {
                folderColorSheetOpen = false
                folderMoreActionsOpen = true
            },
            onDismiss = { folderColorSheetOpen = false },
            onSelect = { colorKey ->
                folder?.let { onUpdateFolderColor(it.id, colorKey) }
                folderColorSheetOpen = false
            },
        )
    }

    if (moveDialogOpen && selectedFolder != null) {
        val selectedId = selectedFolder?.id.orEmpty()
        val targets = remember(uiState.allFolders, selectedId) {
            uiState.allFolders
                .flatMap { it.flatten() }
                .filterNot { it.id == selectedId || it.containsFolder(selectedId) }
        }
        LibraryActionDialog(
            title = "Move ${selectedFolder?.name.orEmpty()}",
            actions = listOf(
                LibraryAction("Library root", Icons.Rounded.Folder) {
                    onMoveFolder(selectedId, null)
                    moveDialogOpen = false
                },
            ) + targets.map { target ->
                LibraryAction(target.name, Icons.Rounded.Folder) {
                    onMoveFolder(selectedId, target.id)
                    moveDialogOpen = false
                }
            },
            onDismiss = { moveDialogOpen = false },
        )
    }

    if (fileActionDialogOpen && selectedFile != null) {
        val file = selectedFile
        LibraryActionDialog(
            title = file?.name.orEmpty(),
            actions = listOf(
                LibraryAction("Open", Icons.Rounded.MenuBook) {
                    file?.let { onAttachmentClick(it.id) }
                    fileActionDialogOpen = false
                },
                LibraryAction("Move", Icons.Rounded.DriveFileMove) {
                    fileActionDialogOpen = false
                    fileMoveDialogOpen = true
                },
                LibraryAction(if (file?.pinned == true) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                    file?.let { onSetFilePinned(it.id, !it.pinned) }
                    fileActionDialogOpen = false
                },
                LibraryAction("PDF activity", Icons.Rounded.Description) {
                    fileActionDialogOpen = false
                    onViewAllAnnotationsClick()
                },
                LibraryAction("More actions", Icons.Rounded.MoreVert) {
                    fileActionDialogOpen = false
                    fileMoreActionsOpen = true
                },
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    fileActionDialogOpen = false
                    fileDeleteDialogOpen = true
                },
            ).filterNotNull(),
            onDismiss = { fileActionDialogOpen = false },
        )
    }

    if (fileMoreActionsOpen && selectedFile != null) {
        val file = selectedFile
        LibraryActionDialog(
            title = "More actions",
            actions = listOf(
                LibraryAction("Rename", Icons.Rounded.Edit) {
                    fileMoreActionsOpen = false
                    fileName = file?.name.orEmpty()
                    fileRenameDialogOpen = true
                },
                LibraryAction("Save to device", Icons.Rounded.FileDownload) {
                    fileMoreActionsOpen = false
                    file?.let { exportFileLauncher.launch(it.name.ifBlank { "myvault-file" }) }
                },
                LibraryAction("Add tag", Icons.Rounded.LocalOffer) {
                    fileMoreActionsOpen = false
                    tagDraft = ""
                    fileTagDialogOpen = true
                },
                LibraryAction("Remove tag", Icons.Rounded.LocalOffer) {
                    fileMoreActionsOpen = false
                    fileRemoveTagDialogOpen = true
                }.takeIf { file?.id?.let { id -> uiState.attachmentTags[id].orEmpty().isNotEmpty() } == true },
            ).filterNotNull(),
            onDismiss = { fileMoreActionsOpen = false },
        )
    }

    if (fileMoveDialogOpen && selectedFile != null) {
        val file = selectedFile
        val targets = remember(uiState.allFolders) { uiState.allFolders.flatMap { it.flatten() } }
        LibraryActionDialog(
            title = "Move ${file?.name.orEmpty()}",
            actions = listOf(
                LibraryAction("Library root", Icons.Rounded.Folder) {
                    file?.let { onMoveFile(it.id, null) }
                    fileMoveDialogOpen = false
                },
            ) + targets.map { target ->
                LibraryAction(target.name, Icons.Rounded.Folder) {
                    file?.let { onMoveFile(it.id, target.id) }
                    fileMoveDialogOpen = false
                }
            },
            onDismiss = { fileMoveDialogOpen = false },
        )
    }

    if (fileRenameDialogOpen && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { fileRenameDialogOpen = false },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    label = { Text("File name") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFile?.let { onRenameFile(it.id, fileName) }
                        fileRenameDialogOpen = false
                        fileName = ""
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileRenameDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (fileTagDialogOpen && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { fileTagDialogOpen = false },
            title = { Text("Add file tag") },
            text = {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    singleLine = true,
                    label = { Text("Tag") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFile?.let { onAddAttachmentTag(it.id, tagDraft) }
                        tagDraft = ""
                        fileTagDialogOpen = false
                    },
                    enabled = tagDraft.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileTagDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (fileRemoveTagDialogOpen && selectedFile != null) {
        val file = selectedFile
        val tags = uiState.attachmentTags[file?.id].orEmpty()
        LibraryActionDialog(
            title = "Remove file tag",
            actions = tags.map { tag ->
                LibraryAction(tag.name, Icons.Rounded.LocalOffer, destructive = true) {
                    file?.let { onRemoveAttachmentTag(it.id, tag.id) }
                    fileRemoveTagDialogOpen = false
                }
            }.ifEmpty {
                listOf(
                    LibraryAction("No tags to remove", Icons.Rounded.LocalOffer) {
                        fileRemoveTagDialogOpen = false
                    },
                )
            },
            onDismiss = { fileRemoveTagDialogOpen = false },
        )
    }

    if (folderDeleteDialogOpen && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { folderDeleteDialogOpen = false },
            title = { Text("Move folder to Recently Deleted?") },
            text = {
                Text(
                    "${selectedFolder?.name.orEmpty()} and its contents will move to Recently Deleted.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFolder?.let { onDeleteFolder(it.id) }
                        folderDeleteDialogOpen = false
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDeleteDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (fileDeleteDialogOpen && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { fileDeleteDialogOpen = false },
            title = { Text("Delete file?") },
            text = {
                Text(
                    "${selectedFile?.name.orEmpty()} will be removed from Library. The original note text is not changed.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFile?.let { onDeleteFile(it.id) }
                        fileDeleteDialogOpen = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileDeleteDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (annotationActionDialogOpen && selectedAnnotation != null) {
        val annotation = selectedAnnotation
        LibraryActionDialog(
            title = annotation?.displayTitle ?: annotation?.notePreview?.take(40).orEmpty().ifBlank { "PDF highlight" },
            actions = listOf(
                LibraryAction("Open source PDF", Icons.Rounded.MenuBook) {
                    annotation?.let { onAnnotationClick(it.attachmentId, it.pageIndex) }
                    annotationActionDialogOpen = false
                },
                LibraryAction("Rename", Icons.Rounded.Edit) {
                    annotationActionDialogOpen = false
                    annotationTitle = annotation?.displayTitle ?: annotation?.notePreview?.take(60).orEmpty()
                    annotationRenameDialogOpen = true
                },
                LibraryAction("Move display location", Icons.Rounded.DriveFileMove) {
                    annotationActionDialogOpen = false
                    annotationMoveDialogOpen = true
                },
                LibraryAction("Link to Study note", Icons.Rounded.LocalOffer) {
                    onPrepareStudyNoteLinks()
                    annotationActionDialogOpen = false
                    annotationLinkDialogOpen = true
                },
                LibraryAction("Create Study note", Icons.Rounded.Description) {
                    annotation?.let { onCreateStudyNoteFromAnnotation(it.id) }
                    annotationActionDialogOpen = false
                },
                LibraryAction("Add tag", Icons.Rounded.LocalOffer) {
                    annotationActionDialogOpen = false
                    tagDraft = ""
                    annotationTagDialogOpen = true
                },
                LibraryAction("Remove tag", Icons.Rounded.LocalOffer) {
                    annotationActionDialogOpen = false
                    annotationRemoveTagDialogOpen = true
                }.takeIf { annotation?.id?.let { id -> uiState.annotationTags[id].orEmpty().isNotEmpty() } == true },
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    annotationActionDialogOpen = false
                    annotationDeleteDialogOpen = true
                },
            ).filterNotNull(),
            onDismiss = { annotationActionDialogOpen = false },
        )
    }

    if (annotationTagDialogOpen && selectedAnnotation != null) {
        AlertDialog(
            onDismissRequest = { annotationTagDialogOpen = false },
            title = { Text("Add annotation tag") },
            text = {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    singleLine = true,
                    label = { Text("Tag") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedAnnotation?.let { onAddAnnotationTag(it.id, tagDraft) }
                        tagDraft = ""
                        annotationTagDialogOpen = false
                    },
                    enabled = tagDraft.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { annotationTagDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
        )
    }

    if (annotationRemoveTagDialogOpen && selectedAnnotation != null) {
        val annotation = selectedAnnotation
        val tags = uiState.annotationTags[annotation?.id].orEmpty()
        LibraryActionDialog(
            title = "Remove annotation tag",
            actions = tags.map { tag ->
                LibraryAction(tag.name, Icons.Rounded.LocalOffer, destructive = true) {
                    annotation?.let { onRemoveAnnotationTag(it.id, tag.id) }
                    annotationRemoveTagDialogOpen = false
                }
            }.ifEmpty {
                listOf(
                    LibraryAction("No tags to remove", Icons.Rounded.LocalOffer) {
                        annotationRemoveTagDialogOpen = false
                    },
                )
            },
            onDismiss = { annotationRemoveTagDialogOpen = false },
        )
    }

    if (annotationLinkDialogOpen && selectedAnnotation != null) {
        val annotation = selectedAnnotation
        LibraryActionDialog(
            title = "Link to Study note",
            actions = when {
                uiState.studyNotesLoading -> listOf(
                    LibraryAction("Loading Study notes...", Icons.Rounded.Description) {},
                )
                uiState.studyNotes.isNotEmpty() -> uiState.studyNotes.take(30).map { note ->
                    LibraryAction(note.title.ifBlank { "Untitled note" }, Icons.Rounded.Description) {
                        annotation?.let { onLinkAnnotationToStudyNote(it.id, note.id) }
                        annotationLinkDialogOpen = false
                    }
                }
                else -> listOf(
                    LibraryAction("No Study notes found", Icons.Rounded.Description) {
                        annotationLinkDialogOpen = false
                    },
                )
            },
            onDismiss = { annotationLinkDialogOpen = false },
        )
    }

    if (annotationMoveDialogOpen && selectedAnnotation != null) {
        val annotation = selectedAnnotation
        val targets = remember(uiState.allFolders) { uiState.allFolders.flatMap { it.flatten() } }
        LibraryActionDialog(
            title = "Move annotation note",
            actions = listOf(
                LibraryAction("Library root", Icons.Rounded.Folder) {
                    annotation?.let { onMoveAnnotation(it.id, null) }
                    annotationMoveDialogOpen = false
                },
            ) + targets.map { target ->
                LibraryAction(target.name, Icons.Rounded.Folder) {
                    annotation?.let { onMoveAnnotation(it.id, target.id) }
                    annotationMoveDialogOpen = false
                }
            },
            onDismiss = { annotationMoveDialogOpen = false },
        )
    }

    if (annotationRenameDialogOpen && selectedAnnotation != null) {
        AlertDialog(
            onDismissRequest = { annotationRenameDialogOpen = false },
            title = { Text("Rename annotation note") },
            text = {
                OutlinedTextField(
                    value = annotationTitle,
                    onValueChange = { annotationTitle = it },
                    singleLine = true,
                    label = { Text("Display title") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedAnnotation?.let { onRenameAnnotation(it.id, annotationTitle) }
                        annotationRenameDialogOpen = false
                        annotationTitle = ""
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { annotationRenameDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (annotationDeleteDialogOpen && selectedAnnotation != null) {
        AlertDialog(
            onDismissRequest = { annotationDeleteDialogOpen = false },
            title = { Text("Delete annotation note?") },
            text = {
                Text(
                    text = "You can remove only the note text and keep the PDF highlight, or delete the highlight and note together.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedAnnotation?.let { onDeleteAnnotation(it.id) }
                        annotationDeleteDialogOpen = false
                    },
                ) {
                    Text("Delete highlight + note", color = colors.warning)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { annotationDeleteDialogOpen = false }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            selectedAnnotation?.let { onDeleteAnnotationNote(it.id) }
                            annotationDeleteDialogOpen = false
                        },
                    ) {
                        Text("Delete note only")
                    }
                }
            },
        )
    }

    folderDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { folderDialog = null },
            title = { Text(if (dialog is LibraryFolderDialog.Rename) "Rename folder" else "New Library folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (dialog) {
                            is LibraryFolderDialog.Create -> onCreateFolder(dialog.parentId, folderName)
                            is LibraryFolderDialog.Rename -> onRenameFolder(dialog.folderId, folderName)
                        }
                        folderDialog = null
                        folderName = ""
                    },
                ) {
                    Text(if (dialog is LibraryFolderDialog.Rename) "Save" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun LibraryFileItem.annotationSummaryLabel(): String? {
    val highlights = highlightCount.takeIf { it > 0 }?.let { count ->
        "$count highlight${if (count == 1) "" else "s"}"
    }
    val annotations = annotationNoteCount.takeIf { it > 0 }?.let { count ->
        "$count annotation${if (count == 1) "" else "s"}"
    }
    return listOfNotNull(highlights, annotations).takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryFolderRow(
    folder: LibraryFolderItem,
    viewMode: LibraryViewMode,
    forceSubfolderStyle: Boolean,
    showFullFileTitles: Boolean,
    expanded: Boolean,
    isChildExpanded: (String) -> Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onFolderExpandedChange: (String, Boolean) -> Unit,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
    onFolderLongPress: (LibraryFolderItem) -> Unit,
    onFileLongPress: (LibraryFileItem) -> Unit,
    onAnnotationLongPress: (LibraryAnnotationItem) -> Unit,
    attachmentTags: Map<String, List<KnowledgeTagChip>>,
    annotationTags: Map<String, List<KnowledgeTagChip>>,
) {
    val colors = VaultThemeTokens.colors
    val subfolderStyle = forceSubfolderStyle || folder.depth > 0
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = LIBRARY_EXPAND_ROTATION_MS, easing = FastOutSlowInEasing),
        label = "library-folder-chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        LibraryHierarchyRow(
            depth = folder.depth,
            title = folder.name,
            subtitle = null,
            count = folder.count.takeIf { it > 0 }?.toString(),
            subfolderStyle = subfolderStyle,
            leading = { topLevel ->
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (topLevel) 16.dp else 14.dp),
                    tint = if (subfolderStyle) Color(0xFFE23B3B) else colors.accent,
                )
            },
            expanded = expanded,
            chevronRotation = rotation,
            onToggle = onToggle,
            onClick = onOpen,
            onLongClick = onLongPress,
            dense = viewMode == LibraryViewMode.Icons,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(LIBRARY_EXPAND_ENTER_MS, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(animationSpec = tween(LIBRARY_EXPAND_EXIT_MS, easing = FastOutSlowInEasing)),
        ) {
            Column {
                folder.children.forEach { child ->
                    LibraryFolderRow(
                        folder = child,
                        viewMode = viewMode,
                        forceSubfolderStyle = true,
                        showFullFileTitles = showFullFileTitles,
                        expanded = isChildExpanded(child.id),
                        isChildExpanded = isChildExpanded,
                        onToggle = { onFolderExpandedChange(child.id, !isChildExpanded(child.id)) },
                        onOpen = { onFolderClick(child.id) },
                        onLongPress = { onFolderLongPress(child) },
                        onFolderExpandedChange = onFolderExpandedChange,
                        onFolderClick = onFolderClick,
                        onAttachmentClick = onAttachmentClick,
                        onAnnotationClick = onAnnotationClick,
                        onFolderLongPress = onFolderLongPress,
                        onFileLongPress = onFileLongPress,
                        onAnnotationLongPress = onAnnotationLongPress,
                        attachmentTags = attachmentTags,
                        annotationTags = annotationTags,
                    )
                }
                folder.files.forEach { file ->
                    LibraryNestedFileRow(
                        file = file,
                        depth = folder.depth + 1,
                        showFullTitle = showFullFileTitles,
                        showMetadata = false,
                        dense = viewMode == LibraryViewMode.Icons,
                        tags = attachmentTags[file.id].orEmpty(),
                        onClick = { onAttachmentClick(file.id) },
                        onLongPress = { onFileLongPress(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryNestedFileRow(
    file: LibraryFileItem,
    depth: Int,
    showFullTitle: Boolean,
    showMetadata: Boolean,
    dense: Boolean = false,
    tags: List<KnowledgeTagChip> = emptyList(),
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val progress = if (file.pageIndex != null && file.pageCount != null) {
        " · p. ${file.pageIndex + 1}/${file.pageCount}"
    } else {
        ""
    }
    val tagSummary = tags.take(2).joinToString(" • ") { it.name } + if (tags.size > 2) " • +${tags.size - 2}" else ""
    val annotationSummary = file.annotationSummaryLabel()
    LibraryHierarchyRow(
        depth = depth,
        title = file.name,
        subtitle = when {
            tags.isNotEmpty() && annotationSummary != null -> "$tagSummary - $annotationSummary"
            tags.isNotEmpty() -> tagSummary
            showMetadata && annotationSummary != null -> "${file.kind} · ${file.size} · ${file.meta}$progress - $annotationSummary"
            showMetadata -> "${file.kind} · ${file.size} · ${file.meta}$progress"
            else -> annotationSummary
        },
        leading = { topLevel ->
            AttachmentThumbnail(
                mimeType = file.mimeType,
                localPath = file.localPath,
                kind = file.kind,
                size = if (topLevel) 18.dp else 16.dp,
                renderPdfPreview = false,
            )
        },
        onClick = onClick,
        onLongClick = onLongPress,
        dense = dense,
        fileRow = true,
        showFullTitle = showFullTitle,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryHierarchyRow(
    depth: Int,
    title: String,
    subtitle: String? = null,
    count: String? = null,
    leading: @Composable (topLevel: Boolean) -> Unit,
    subfolderStyle: Boolean = false,
    showFullTitle: Boolean = false,
    expanded: Boolean = false,
    chevronRotation: Float = 0f,
    onToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    dense: Boolean = false,
    fileRow: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    val topLevel = depth == 0
    val rowShape = if (topLevel) VaultShapes.md else VaultShapes.sm
    val background = if (topLevel && expanded) colors.surface else Color.Transparent
    val borderColor = if (topLevel && expanded) colors.border else Color.Transparent
    val chevronInteractionSource = remember { MutableInteractionSource() }
    val hierarchyIndent = when {
        topLevel -> 6.dp
        fileRow -> (2 + depth * 7).dp
        else -> (4 + depth * 8).dp
    }
    val rowVerticalGap = if (topLevel) 0.5.dp else 0.dp
    val rowHorizontalPadding = if (topLevel) 8.dp else 5.dp
    val rowVerticalPadding = when {
        fileRow && !subtitle.isNullOrBlank() -> 3.dp
        fileRow -> 2.dp
        topLevel -> 6.dp
        else -> 3.dp
    }
    val rowMinHeight = when {
        !fileRow && topLevel -> 36.dp
        !fileRow -> 32.dp
        else -> 30.dp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = hierarchyIndent,
                top = rowVerticalGap,
                end = if (topLevel) 8.dp else 6.dp,
                bottom = rowVerticalGap,
            )
            .heightIn(min = rowMinHeight)
            .clip(rowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = background,
        shape = rowShape,
        border = BorderStroke(1.dp, borderColor),
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
            if (onToggle != null) {
                Box(
                    modifier = Modifier
                        .size(if (topLevel) 13.dp else 11.dp)
                        .clickable(
                            interactionSource = chevronInteractionSource,
                            indication = null,
                            onClick = onToggle,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Expand folder",
                        modifier = Modifier
                            .size(if (topLevel) 13.dp else 11.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = colors.textMuted,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            leading(topLevel)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = when {
                        fileRow && topLevel -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                        fileRow -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                        topLevel -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                        else -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                    },
                    color = if (subfolderStyle && !fileRow) Color(0xFFE23B3B) else colors.text,
                    maxLines = if (fileRow && showFullTitle) Int.MAX_VALUE else 1,
                    overflow = if (fileRow && showFullTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!count.isNullOrBlank()) {
                if (topLevel) {
                    Surface(
                        shape = VaultShapes.pill,
                        color = colors.inset,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Text(
                            text = count,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                } else {
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryActionDialog(
    title: String,
    actions: List<LibraryAction>,
    onDismiss: () -> Unit,
) {
    CorpusActionSheet(
        title = title,
        onDismiss = onDismiss,
        groups = actions
            .groupBy { it.section }
            .map { (section, sectionActions) ->
                CorpusActionGroup(
                    label = section,
                    actions = sectionActions.map { action ->
                        CorpusAction(
                            label = action.label,
                            icon = action.icon,
                            destructive = action.destructive,
                            selected = action.selected,
                            onClick = action.onClick,
                        )
                    },
                )
            },
    )
}

private sealed interface LibraryFolderDialog {
    data class Create(val parentId: String?) : LibraryFolderDialog
    data class Rename(val folderId: String) : LibraryFolderDialog
}


private data class LibraryAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val section: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun LibraryCorpusFolderItem(
    folder: LibraryFolderItem,
    expandedFolderIds: Set<String>,
    showFullFileTitles: Boolean,
    onFolderExpandedChange: (String, Boolean) -> Unit,
    onFolderCreate: (LibraryFolderItem) -> Unit,
    onFolderLongPress: (LibraryFolderItem) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onFileLongPress: (LibraryFileItem) -> Unit,
    depth: Int,
) {
    val expanded = folder.id in expandedFolderIds
    CorpusFolderRow(
        title = folder.name,
        colorKey = folder.colorKey,
        count = folder.count,
        expanded = expanded,
        onToggle = { onFolderExpandedChange(folder.id, !expanded) },
        onLongPress = { onFolderLongPress(folder) },
        onAdd = null,
        depth = depth,
    )
    CorpusExpandedChildren(expanded = expanded) {
        folder.children.forEach { child ->
            LibraryCorpusFolderItem(
                folder = child,
                expandedFolderIds = expandedFolderIds,
                showFullFileTitles = showFullFileTitles,
                onFolderExpandedChange = onFolderExpandedChange,
                onFolderCreate = onFolderCreate,
                onFolderLongPress = onFolderLongPress,
                onAttachmentClick = onAttachmentClick,
                onFileLongPress = onFileLongPress,
                depth = depth + 1,
            )
        }
        folder.files.forEach { file ->
            LibraryCorpusFileRow(
                file = file,
                showFullFileTitles = showFullFileTitles,
                onAttachmentClick = onAttachmentClick,
                onLongPress = { onFileLongPress(file) },
                depth = depth + 1,
            )
        }
    }
}

@Composable
private fun LibraryCorpusFileRow(
    file: LibraryFileItem,
    showFullFileTitles: Boolean,
    onAttachmentClick: (String) -> Unit,
    onLongPress: () -> Unit,
    depth: Int = 0,
) {
    CorpusLeafRow(
        title = file.name,
        icon = Icons.Outlined.Description,
        onClick = { onAttachmentClick(file.id) },
        onLongPress = onLongPress,
        pinned = file.pinned,
        showFullTitle = showFullFileTitles,
        supportingText = file.pdfActivityMetadata(),
        depth = depth,
    )
}

private fun LibraryFileItem.pdfActivityMetadata(): String? {
    if (mimeType != "application/pdf") return null
    val parts = buildList {
        if (highlightCount > 0) add("$highlightCount highlight${if (highlightCount == 1) "" else "s"}")
        if (annotationNoteCount > 0) add("$annotationNoteCount note${if (annotationNoteCount == 1) "" else "s"}")
        if (pageIndex != null && pageCount != null && pageCount > 0) {
            add("Page ${pageIndex.coerceIn(0, pageCount - 1) + 1} of $pageCount")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun LibraryFolderItem.flatten(): List<LibraryFolderItem> =
    listOf(this) + children.flatMap { it.flatten() }

private fun List<LibraryFolderItem>.parentFolderName(fileId: String): String? {
    forEach { folder ->
        if (folder.files.any { it.id == fileId }) return folder.name
        folder.children.parentFolderName(fileId)?.let { return it }
    }
    return null
}

private fun LibraryFolderItem.containsFolder(folderId: String): Boolean =
    id == folderId || children.any { it.containsFolder(folderId) }

private const val LIBRARY_EXPAND_ROTATION_MS = 230
private const val LIBRARY_EXPAND_ENTER_MS = 240
private const val LIBRARY_EXPAND_EXIT_MS = 200
