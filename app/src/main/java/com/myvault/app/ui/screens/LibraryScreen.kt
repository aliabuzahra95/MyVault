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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.PinnedNoteCard
import com.myvault.app.ui.components.SearchBar
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultActionModal
import com.myvault.app.ui.components.VaultModalAction
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTopBar
import com.myvault.app.ui.components.VaultWorkspaceSwitcher
import com.myvault.app.ui.home.HomeInlineAiPanel
import com.myvault.app.ai.home.HomeAiAttachmentScope
import com.myvault.app.ai.home.HomeInlineAiViewModel
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryAnnotationItem
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.LibraryViewMode
import androidx.hilt.navigation.compose.hiltViewModel

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
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
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
    onShareAiAnswerClick: (String) -> Unit = {},
    quickBackupRecommended: Boolean = false,
    showFullFileTitles: Boolean = false,
    onViewAllAnnotationsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
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
        onMoveFolder = onMoveFolder,
        onMoveFolderInOrder = onMoveFolderInOrder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
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
        onShareAiAnswerClick = onShareAiAnswerClick,
        quickBackupRecommended = quickBackupRecommended,
        showFullFileTitles = showFullFileTitles,
        onViewAllAnnotationsClick = onViewAllAnnotationsClick,
        modifier = modifier,
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
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
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
    onShareAiAnswerClick: (String) -> Unit = {},
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
        onMoveFolder = onMoveFolder,
        onMoveFolderInOrder = onMoveFolderInOrder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
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
        onShareAiAnswerClick = onShareAiAnswerClick,
        showFullFileTitles = showFullFileTitles,
        onViewAllAnnotationsClick = {},
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
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onMoveFolderInOrder: (folderId: String, direction: Int) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
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
    onShareAiAnswerClick: (String) -> Unit = {},
    quickBackupRecommended: Boolean = false,
    showFullFileTitles: Boolean = false,
    onViewAllAnnotationsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val libraryInlineAiViewModel: HomeInlineAiViewModel = hiltViewModel()
    val libraryInlineAiState by libraryInlineAiViewModel.state.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) {
        fabExpanded = false
    }
    var folderDialog by remember { mutableStateOf<LibraryFolderDialog?>(null) }
    var folderName by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<LibraryFolderItem?>(null) }
    var selectedFile by remember { mutableStateOf<LibraryFileItem?>(null) }
    var selectedAnnotation by remember { mutableStateOf<LibraryAnnotationItem?>(null) }
    var actionDialogOpen by remember { mutableStateOf(false) }
    var fileActionDialogOpen by remember { mutableStateOf(false) }
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
    var librarySearchQuery by remember { mutableStateOf("") }
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
        if (uris.isNotEmpty()) onImportFiles(uris)
    }
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = selectedFile
        if (uri != null && file != null) {
            onExportFile(file.id, uri)
        }
    }
    val actions = remember {
        listOf(
            FloatingAction("Import File", Icons.Rounded.UploadFile),
            FloatingAction("New Library Folder", Icons.Rounded.CreateNewFolder),
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
                contentPadding = PaddingValues(bottom = 118.dp),
            ) {
                item {
                    if (onBackClick == null) {
                        VaultTopBar(
                            title = workspaceTitle,
                            titleContent = if (workspaceOptions.isNotEmpty()) {
                                {
                                    VaultWorkspaceSwitcher(
                                        selectedLabel = workspaceTitle,
                                        options = workspaceOptions,
                                        onSelected = onWorkspaceSelected,
                                    )
                                }
                            } else {
                                null
                            },
                        ) {
                            IconBtn(
                                icon = Icons.Rounded.Search,
                                contentDescription = "Search Library",
                                active = librarySearchOpen,
                                onClick = toggleLibrarySearch,
                            )
                            IconBtn(
                                icon = Icons.Rounded.ViewList,
                                contentDescription = "Display mode",
                                active = uiState.viewMode != LibraryViewMode.List,
                                onClick = { displayModeDialogOpen = true },
                            )
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
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    } else {
                        ScreenTopBar(
                            onBackClick = onBackClick,
                            actions = {
                                IconBtn(
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "Search folder",
                                    active = librarySearchOpen,
                                    onClick = toggleLibrarySearch,
                                )
                                IconBtn(
                                    icon = Icons.Rounded.ViewList,
                                    contentDescription = "Display mode",
                                    active = uiState.viewMode != LibraryViewMode.List,
                                    onClick = { displayModeDialogOpen = true },
                                )
                            },
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800), color = colors.text)
                            if (!subtitle.isNullOrBlank()) {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                    }
                }

                if (librarySearchOpen) {
                    item {
                        LibrarySearchOverlay(
                            query = librarySearchQuery,
                            onQueryChange = { librarySearchQuery = it },
                        )
                    }
                    if (librarySearchQuery.isNotBlank()) {
                        item {
                            LibrarySearchResults(
                                query = librarySearchQuery,
                                uiState = uiState,
                                onFolderClick = onFolderClick,
                                onAttachmentClick = onAttachmentClick,
                                onAnnotationClick = onAnnotationClick,
                            )
                        }
                    }
                }

                if (currentFolderId == null && uiState.annotations.isNotEmpty()) {
                    item {
                        RecentLibraryAnnotationsRow(
                            annotations = uiState.annotations,
                            onAnnotationClick = onAnnotationClick,
                            onViewAllClick = onViewAllAnnotationsClick,
                        )
                    }
                }

                if (uiState.pinnedFiles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(5.dp))
                        SectionLabel(label = "Pinned")
                        Spacer(modifier = Modifier.height(3.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                        ) {
                            items(uiState.pinnedFiles, key = { it.id }) { file ->
                                PinnedNoteCard(
                                    note = file.toPinnedCardData(),
                                    previewLines = 1,
                                    showFullTitle = showFullFileTitles,
                                    onClick = { onAttachmentClick(file.id) },
                                    onLongPress = {
                                        selectedFile = file
                                        fileActionDialogOpen = true
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionLabel(label = if (currentFolderId == null) "Collections" else "Subfolders")
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (uiState.folders.isEmpty()) {
                    item {
                        LibraryEmptyState(
                            icon = Icons.Rounded.Folder,
                            text = if (currentFolderId == null) "Create a Library folder to begin your archive" else "No subfolders yet",
                        )
                    }
                } else if (uiState.viewMode == LibraryViewMode.Grid) {
                    items(uiState.folders.chunked(3), key = { row -> row.joinToString(":") { it.id } }) { row ->
                        LibraryGridRow(
                            items = row,
                            columns = 3,
                            content = { folder ->
                                LibraryGridFolderCard(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.id) },
                                    onLongPress = {
                                        selectedFolder = folder
                                        actionDialogOpen = true
                                    },
                                )
                            },
                        )
                    }
                } else {
                    items(uiState.folders, key = { it.id }) { folder ->
                        LibraryFolderRow(
                            folder = folder,
                            viewMode = uiState.viewMode,
                            forceSubfolderStyle = currentFolderId != null,
                            showFullFileTitles = showFullFileTitles,
                            expanded = folder.id in uiState.expandedFolderIds,
                            isChildExpanded = { id -> id in uiState.expandedFolderIds },
                            onToggle = {
                                onFolderExpandedChange(folder.id, folder.id !in uiState.expandedFolderIds)
                            },
                            onOpen = { onFolderClick(folder.id) },
                            onLongPress = {
                                selectedFolder = folder
                                actionDialogOpen = true
                            },
                            onFolderExpandedChange = onFolderExpandedChange,
                            onFolderClick = onFolderClick,
                            onAttachmentClick = onAttachmentClick,
                            onAnnotationClick = onAnnotationClick,
                            onFolderLongPress = {
                                selectedFolder = it
                                actionDialogOpen = true
                            },
                            onFileLongPress = {
                                selectedFile = it
                                fileActionDialogOpen = true
                            },
                            onAnnotationLongPress = {
                                selectedAnnotation = it
                                annotationActionDialogOpen = true
                            },
                            attachmentTags = uiState.attachmentTags,
                            annotationTags = uiState.annotationTags,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(VaultSpacing.xs))
                    SectionLabel(label = "Files")
                }

                if (uiState.files.isEmpty() && currentFolderId != null) {
                    item {
                        LibraryEmptyState(icon = Icons.Rounded.InsertDriveFile, text = "No files yet")
                    }
                } else if (uiState.viewMode == LibraryViewMode.Grid) {
                    items(uiState.files.chunked(2), key = { row -> row.joinToString(":") { it.id } }) { row ->
                        LibraryGridRow(
                            items = row,
                            content = { file ->
                                LibraryGridFileCard(
                                    file = file,
                                    showFullTitle = showFullFileTitles,
                                    onClick = { onAttachmentClick(file.id) },
                                    onLongPress = {
                                        selectedFile = file
                                        fileActionDialogOpen = true
                                    },
                                )
                            },
                        )
                    }
                } else {
                    items(uiState.files, key = { it.id }) { file ->
                        LibraryNestedFileRow(
                            file = file,
                            depth = 0,
                            showFullTitle = showFullFileTitles,
                            showMetadata = uiState.viewMode == LibraryViewMode.Icons,
                            dense = uiState.viewMode == LibraryViewMode.Icons,
                            tags = uiState.attachmentTags[file.id].orEmpty(),
                            onClick = { onAttachmentClick(file.id) },
                            onLongPress = {
                                selectedFile = file
                                fileActionDialogOpen = true
                            },
                        )
                    }
                }

                if (uiState.references.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        SectionLabel(label = "Referenced in")
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(uiState.references.take(3), key = { it.id }) { reference ->
                        LibraryReferenceRow(
                            title = reference.noteTitle,
                            subtitle = "Page ${reference.pageIndex + 1}",
                            onClick = { onReferenceNoteClick(reference.noteId) },
                        )
                    }
                    if (uiState.references.size > 3) {
                        item {
                            Text(
                                text = "+${uiState.references.size - 3} more references",
                                modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }

            if (fabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(onClick = { fabExpanded = false }, onLongClick = {}),
                )
            }

            FloatingActionMenu(
                expanded = fabExpanded,
                actions = actions,
                mainButtonSize = 48.dp,
                actionButtonSize = 38.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = VaultSpacing.screen, bottom = 74.dp)
                    .size(width = 220.dp, height = 230.dp),
                onToggle = { fabExpanded = !fabExpanded },
                onActionClick = { action ->
                    fabExpanded = false
                    when (action.label) {
                        "Import File" -> multiImportPicker.launch(arrayOf("*/*"))
                        "New Library Folder" -> {
                            folderName = ""
                            folderDialog = LibraryFolderDialog.Create(parentId = currentFolderId)
                        }
                    }
                },
            )

            LibraryInlineAiPill(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = VaultSpacing.screen + 2.dp, bottom = 132.dp),
                onClick = { libraryInlineAiViewModel.openPanel(HomeAiAttachmentScope.LibraryPdfs) },
            )

            HomeInlineAiPanel(
                state = libraryInlineAiState,
                onInputChange = libraryInlineAiViewModel::setInput,
                onAttachClick = libraryInlineAiViewModel::openPicker,
                onSuggestionClick = libraryInlineAiViewModel::attachSuggestion,
                onDetachClick = libraryInlineAiViewModel::detachItem,
                onSendClick = libraryInlineAiViewModel::send,
                onStopClick = libraryInlineAiViewModel::stopStreaming,
                onProviderSelected = libraryInlineAiViewModel::setProvider,
                onModelModeSelected = libraryInlineAiViewModel::setModelMode,
                onWebSearchToggle = libraryInlineAiViewModel::toggleWebSearch,
                onSettingsClick = libraryInlineAiViewModel::toggleSettingsMode,
                onClearHistoryClick = libraryInlineAiViewModel::clearHistory,
                onHistoryClick = libraryInlineAiViewModel::openHistoryItem,
                onRetryClick = libraryInlineAiViewModel::retryLastRequest,
                onDismissErrorClick = libraryInlineAiViewModel::dismissError,
                onShareAnswerClick = onShareAiAnswerClick,
                onSpeakAnswerClick = libraryInlineAiViewModel::speakAnswer,
                onClose = libraryInlineAiViewModel::closePanel,
                onPickerToggle = libraryInlineAiViewModel::attachItem,
                onPickerClose = libraryInlineAiViewModel::closePicker,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(80f),
            )
        }
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
        AlertDialog(
            onDismissRequest = onSkipDuplicatePdf,
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            title = { Text("PDF already exists.") },
            text = {
                Text(
                    text = "${duplicate.fileName} already exists in this Library folder.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Button(onClick = onReplaceDuplicatePdf) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = onSkipDuplicatePdf) {
                    Text("Skip")
                }
            },
        )
    }

    if (actionDialogOpen && selectedFolder != null) {
        LibraryActionDialog(
            title = selectedFolder?.name.orEmpty(),
            actions = listOf(
                LibraryAction("New subfolder", Icons.Rounded.CreateNewFolder) {
                    val parent = selectedFolder?.id
                    actionDialogOpen = false
                    folderName = ""
                    folderDialog = LibraryFolderDialog.Create(parentId = parent)
                },
                LibraryAction("Rename", Icons.Rounded.Edit) {
                    actionDialogOpen = false
                    folderName = selectedFolder?.name.orEmpty()
                    folderDialog = selectedFolder?.let { LibraryFolderDialog.Rename(it.id) }
                },
                LibraryAction("Move", Icons.Rounded.DriveFileMove) {
                    actionDialogOpen = false
                    moveDialogOpen = true
                },
                LibraryAction("Move up", Icons.Rounded.KeyboardArrowUp) {
                    selectedFolder?.let { onMoveFolderInOrder(it.id, -1) }
                    actionDialogOpen = false
                },
                LibraryAction("Move down", Icons.Rounded.KeyboardArrowDown) {
                    selectedFolder?.let { onMoveFolderInOrder(it.id, 1) }
                    actionDialogOpen = false
                },
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    actionDialogOpen = false
                    folderDeleteDialogOpen = true
                },
            ),
            onDismiss = { actionDialogOpen = false },
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
                LibraryAction("Rename", Icons.Rounded.Edit) {
                    fileActionDialogOpen = false
                    fileName = file?.name.orEmpty()
                    fileRenameDialogOpen = true
                },
                LibraryAction("Move", Icons.Rounded.DriveFileMove) {
                    fileActionDialogOpen = false
                    fileMoveDialogOpen = true
                },
                LibraryAction(if (file?.pinned == true) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                    file?.let { onSetFilePinned(it.id, !it.pinned) }
                    fileActionDialogOpen = false
                },
                LibraryAction("Save to device", Icons.Rounded.FileDownload) {
                    fileActionDialogOpen = false
                    file?.let { exportFileLauncher.launch(it.name.ifBlank { "myvault-file" }) }
                },
                LibraryAction("Add tag", Icons.Rounded.LocalOffer) {
                    fileActionDialogOpen = false
                    tagDraft = ""
                    fileTagDialogOpen = true
                },
                LibraryAction("Remove tag", Icons.Rounded.LocalOffer) {
                    fileActionDialogOpen = false
                    fileRemoveTagDialogOpen = true
                }.takeIf { file?.id?.let { id -> uiState.attachmentTags[id].orEmpty().isNotEmpty() } == true },
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    fileActionDialogOpen = false
                    fileDeleteDialogOpen = true
                },
            ).filterNotNull(),
            onDismiss = { fileActionDialogOpen = false },
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

@Composable
private fun LibrarySearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 4.dp),
        placeholder = "Search notes and folders...",
        query = query,
        active = query.isNotBlank(),
        onQueryChange = onQueryChange,
    )
}

@Composable
private fun LibrarySearchResults(
    query: String,
    uiState: LibraryUiState,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val normalized = query.trim().lowercase()
    val folders = remember(uiState.allFolders, normalized) {
        uiState.allFolders.flatMap { it.flatten() }
            .filter { it.name.lowercase().contains(normalized) }
            .take(8)
    }
    val files = remember(uiState.allFolders, uiState.files, uiState.attachmentTags, normalized) {
        (uiState.files + uiState.allFolders.flatMap { it.flatten() }.flatMap { it.files })
            .distinctBy { it.id }
            .filter { file ->
                file.name.lowercase().contains(normalized) ||
                    file.kind.lowercase().contains(normalized) ||
                    uiState.attachmentTags[file.id].orEmpty().any { it.name.lowercase().contains(normalized) }
            }
            .take(8)
    }
    val annotations = remember(uiState.allFolders, uiState.annotations, uiState.annotationTags, normalized) {
        (uiState.annotations + uiState.allFolders.flatMap { it.flatten() }.flatMap { it.annotations })
            .distinctBy { it.id }
            .filter { annotation ->
                annotation.notePreview.lowercase().contains(normalized) ||
                    annotation.displayTitle.orEmpty().lowercase().contains(normalized) ||
                    annotation.fileName.lowercase().contains(normalized) ||
                    uiState.annotationTags[annotation.id].orEmpty().any { it.name.lowercase().contains(normalized) }
            }
            .take(8)
    }
    val hasResults = folders.isNotEmpty() || files.isNotEmpty() || annotations.isNotEmpty()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 4.dp),
        color = colors.elevated,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Library results",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                color = colors.text,
            )
            if (!hasResults) {
                Text(
                    text = "No matching folders, files, annotations, or tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            folders.forEach { folder ->
                LibraryHierarchyRow(
                    depth = 0,
                    title = folder.name,
                    subtitle = "Folder",
                    leading = {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.warning)
                    },
                    onClick = { onFolderClick(folder.id) },
                )
            }
            files.forEach { file ->
                LibraryNestedFileRow(
                    file = file,
                    depth = 0,
                    showFullTitle = false,
                    showMetadata = true,
                    tags = uiState.attachmentTags[file.id].orEmpty(),
                    onClick = { onAttachmentClick(file.id) },
                )
            }
            annotations.forEach { annotation ->
                LibraryNestedAnnotationRow(
                    annotation = annotation,
                    depth = 0,
                    tags = uiState.annotationTags[annotation.id].orEmpty(),
                    onClick = { onAnnotationClick(annotation.attachmentId, annotation.pageIndex) },
                    onLongPress = {},
                )
            }
        }
    }
}

@Composable
private fun LibraryAnnotationRow(
    annotation: LibraryAnnotationItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    LibraryHierarchyRow(
        depth = 0,
        title = annotation.displayTitle ?: annotation.notePreview.ifBlank { annotation.defaultAnnotationTitle() },
        subtitle = "${annotation.fileName} · p. ${annotation.pageIndex + 1}",
        leading = {
            Icon(
                imageVector = Icons.Rounded.StickyNote2,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = annotation.color.toAnnotationColor(),
            )
        },
        onClick = onClick,
        onLongClick = onLongPress,
    )
}

@Composable
private fun LibraryReferenceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    LibraryHierarchyRow(
        depth = 0,
        title = title,
        subtitle = subtitle,
        leading = {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VaultThemeTokens.colors.accent,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun <T> LibraryGridRow(
    items: List<T>,
    columns: Int = 2,
    content: @Composable (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                content(item)
            }
        }
        repeat((columns - items.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridFolderCard(
    folder: LibraryFolderItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.warning)
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.count} item${if (folder.count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridFileCard(
    file: LibraryFileItem,
    showFullTitle: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttachmentThumbnail(
                mimeType = file.mimeType,
                localPath = file.localPath,
                kind = file.kind,
                size = 34.dp,
                renderPdfPreview = false,
            )
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${file.kind} · ${file.size}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LibraryFileItem.toPinnedCardData(): VaultNoteCardData =
    VaultNoteCardData(
        id = id,
        title = name,
        meta = meta,
        preview = annotationSummaryLabel() ?: kind,
    )

private fun LibraryFileItem.annotationSummaryLabel(): String? {
    val highlights = highlightCount.takeIf { it > 0 }?.let { count ->
        "$count highlight${if (count == 1) "" else "s"}"
    }
    val annotations = annotationNoteCount.takeIf { it > 0 }?.let { count ->
        "$count annotation${if (count == 1) "" else "s"}"
    }
    return listOfNotNull(highlights, annotations).takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

private fun String.toAnnotationColor(): Color =
    when (lowercase()) {
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFD84D)
    }

@Composable
private fun RecentLibraryAnnotationsRow(
    annotations: List<LibraryAnnotationItem>,
    onAnnotationClick: (String, Int) -> Unit,
    onViewAllClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Annotations",
            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = colors.textMuted,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "view-all") {
                Surface(
                    onClick = onViewAllClick,
                    color = colors.surface,
                    shape = VaultShapes.md,
                    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(width = 104.dp, height = 64.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                            color = colors.accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(annotations.take(8), key = { it.id }) { annotation ->
                Surface(
                    onClick = { onAnnotationClick(annotation.attachmentId, annotation.pageIndex) },
                    color = colors.surface,
                    shape = VaultShapes.md,
                    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(width = 104.dp, height = 64.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StickyNote2,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = annotation.color.toAnnotationColor(),
                        )
                        Text(
                            text = annotation.displayTitle ?: annotation.notePreview.ifBlank { annotation.defaultAnnotationTitle() },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${annotation.fileName} · p. ${annotation.pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun LibraryAnnotationItem.defaultAnnotationTitle(): String =
    if (annotationType == "page_note") "Page note" else "Highlight"

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
                    modifier = Modifier.size(if (topLevel) 16.dp else 13.dp),
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

@Composable
private fun LibraryNestedAnnotationRow(
    annotation: LibraryAnnotationItem,
    depth: Int,
    dense: Boolean = false,
    tags: List<KnowledgeTagChip> = emptyList(),
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    LibraryHierarchyRow(
        depth = depth,
        title = annotation.displayTitle ?: annotation.notePreview.ifBlank { annotation.defaultAnnotationTitle() },
        subtitle = if (tags.isEmpty()) {
            "${annotation.fileName} · p. ${annotation.pageIndex + 1}"
        } else {
            tags.take(2).joinToString(" • ") { it.name } + if (tags.size > 2) " • +${tags.size - 2}" else ""
        },
        leading = {
            Icon(
                imageVector = Icons.Rounded.StickyNote2,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = annotation.color.toAnnotationColor(),
            )
        },
        onClick = onClick,
        onLongClick = onLongPress,
        dense = dense,
        fileRow = true,
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.xs)
            .padding(
                start = if (topLevel) 12.dp else (10 + depth * 14).dp,
                top = if (topLevel) 2.dp else 0.dp,
                end = if (topLevel) 12.dp else 8.dp,
                bottom = if (topLevel) 4.dp else 0.dp,
            )
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
                    horizontal = if (topLevel) 12.dp else 10.dp,
                    vertical = when {
                        dense && fileRow -> 8.dp
                        dense -> 6.dp
                        fileRow -> 10.dp
                        topLevel -> 10.dp
                        else -> 8.dp
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onToggle != null) {
                Box(
                    modifier = Modifier
                        .size(if (topLevel) 14.dp else 12.dp)
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
                            .size(if (topLevel) 14.dp else 12.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = colors.textMuted,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            leading(topLevel)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = if (fileRow) {
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600)
                    } else if (topLevel) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                    } else {
                        MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                    },
                    color = if (subfolderStyle && !fileRow) Color(0xFFE23B3B) else colors.text,
                    maxLines = if (fileRow && showFullTitle) Int.MAX_VALUE else 1,
                    overflow = if (fileRow && showFullTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
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
private fun LibraryEmptyState(icon: ImageVector, text: String) {
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun LibraryActionDialog(
    title: String,
    actions: List<LibraryAction>,
    onDismiss: () -> Unit,
) {
    VaultActionModal(
        title = title,
        onDismiss = onDismiss,
        actions = actions.map { action ->
            VaultModalAction(
                label = action.label,
                icon = action.icon,
                destructive = action.destructive,
                selected = action.selected,
                onClick = action.onClick,
            )
        },
    )
}

private sealed interface LibraryFolderDialog {
    data class Create(val parentId: String?) : LibraryFolderDialog
    data class Rename(val folderId: String) : LibraryFolderDialog
}


@Composable
private fun LibraryInlineAiPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.size(44.dp),
        onClick = onClick,
        shape = VaultShapes.pill,
        color = colors.elevated.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, colors.accentBorder),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Open Library AI chat",
                modifier = Modifier.size(20.dp),
                tint = colors.accent,
            )
        }
    }
}

private data class LibraryAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private fun LibraryFolderItem.flatten(): List<LibraryFolderItem> =
    listOf(this) + children.flatMap { it.flatten() }

private fun LibraryFolderItem.containsFolder(folderId: String): Boolean =
    id == folderId || children.any { it.containsFolder(folderId) }

private const val LIBRARY_EXPAND_ROTATION_MS = 230
private const val LIBRARY_EXPAND_ENTER_MS = 240
private const val LIBRARY_EXPAND_EXIT_MS = 200
