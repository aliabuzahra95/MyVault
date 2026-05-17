package com.myvault.app.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.AttachmentThumbnail
import com.myvault.app.ui.components.FloatingAction
import com.myvault.app.ui.components.FloatingActionMenu
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.components.PinnedNoteCard
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTopBar
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
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LibraryArchiveScreen(
        title = "Library",
        subtitle = null,
        uiState = uiState,
        onBackClick = null,
        currentFolderId = null,
        onFolderClick = onFolderClick,
        onAttachmentClick = onAttachmentClick,
        onAnnotationClick = onAnnotationClick,
        onRenameAnnotation = onRenameAnnotation,
        onMoveAnnotation = onMoveAnnotation,
        onDeleteAnnotationNote = onDeleteAnnotationNote,
        onDeleteAnnotation = onDeleteAnnotation,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onMoveFolder = onMoveFolder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
        onDismissImportMessage = onDismissImportMessage,
        onRenameFile = onRenameFile,
        onMoveFile = onMoveFile,
        onSetFilePinned = onSetFilePinned,
        onDeleteFile = onDeleteFile,
        onThemeClick = onThemeClick,
        onQuickBackupClick = onQuickBackupClick,
        onSettingsClick = onSettingsClick,
        quickBackupRecommended = quickBackupRecommended,
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
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
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
        onRenameAnnotation = onRenameAnnotation,
        onMoveAnnotation = onMoveAnnotation,
        onDeleteAnnotationNote = onDeleteAnnotationNote,
        onDeleteAnnotation = onDeleteAnnotation,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onMoveFolder = onMoveFolder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFiles = onImportFiles,
        onDismissImportMessage = onDismissImportMessage,
        onRenameFile = onRenameFile,
        onMoveFile = onMoveFile,
        onSetFilePinned = onSetFilePinned,
        onDeleteFile = onDeleteFile,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryArchiveScreen(
    title: String,
    subtitle: String?,
    uiState: LibraryUiState,
    onBackClick: (() -> Unit)?,
    currentFolderId: String?,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onAnnotationClick: (String, Int) -> Unit,
    onRenameAnnotation: (String, String) -> Unit,
    onMoveAnnotation: (String, String?) -> Unit,
    onDeleteAnnotationNote: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onDismissImportMessage: () -> Unit,
    onRenameFile: (fileId: String, name: String) -> Unit,
    onMoveFile: (fileId: String, folderId: String?) -> Unit,
    onSetFilePinned: (fileId: String, pinned: Boolean) -> Unit,
    onDeleteFile: (fileId: String) -> Unit,
    onThemeClick: () -> Unit = {},
    onQuickBackupClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    quickBackupRecommended: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var fabExpanded by remember { mutableStateOf(false) }
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
    var fileRenameDialogOpen by remember { mutableStateOf(false) }
    var quickBackupConfirmOpen by remember { mutableStateOf(false) }
    var displayModeDialogOpen by remember { mutableStateOf(false) }
    var annotationTitle by remember { mutableStateOf("") }
    val multiImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImportFiles(uris)
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
                        VaultTopBar(title = title) {
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

                uiState.continueReading?.let { file ->
                    item {
                        ContinueReadingCard(
                            file = file,
                            onClick = { onAttachmentClick(file.id) },
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
                                    onClick = { onAttachmentClick(file.id) },
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
                    items(uiState.folders.chunked(2), key = { row -> row.joinToString(":") { it.id } }) { row ->
                        LibraryGridRow(
                            items = row,
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
                            onFolderLongPress = {
                                selectedFolder = it
                                actionDialogOpen = true
                            },
                            onFileLongPress = {
                                selectedFile = it
                                fileActionDialogOpen = true
                            },
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
                            showMetadata = uiState.viewMode == LibraryViewMode.Icons,
                            dense = uiState.viewMode == LibraryViewMode.Icons,
                            onClick = { onAttachmentClick(file.id) },
                            onLongPress = {
                                selectedFile = file
                                fileActionDialogOpen = true
                            },
                        )
                    }
                }

                if (uiState.annotations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        SectionLabel(label = "Annotations")
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(uiState.annotations, key = { it.id }) { annotation ->
                        LibraryAnnotationRow(
                            annotation = annotation,
                            onClick = { onAnnotationClick(annotation.attachmentId, annotation.pageIndex) },
                            onLongPress = {
                                selectedAnnotation = annotation
                                annotationActionDialogOpen = true
                            },
                        )
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
                    .padding(end = VaultSpacing.screen, bottom = VaultSpacing.xl)
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
        }
    }

    if (quickBackupConfirmOpen) {
        AlertDialog(
            onDismissRequest = { quickBackupConfirmOpen = false },
            containerColor = colors.elevated,
            tonalElevation = 0.dp,
            title = { Text("Back up to cloud?") },
            text = {
                Text(
                    "This will replace the current cloud backup with the vault on this device. Make sure this phone has your latest Library files and notes before continuing.",
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
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    selectedFolder?.let { onDeleteFolder(it.id) }
                    actionDialogOpen = false
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
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    file?.let { onDeleteFile(it.id) }
                    fileActionDialogOpen = false
                },
            ),
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

    if (annotationActionDialogOpen && selectedAnnotation != null) {
        val annotation = selectedAnnotation
        LibraryActionDialog(
            title = annotation?.displayTitle ?: annotation?.notePreview?.take(40).orEmpty().ifBlank { "Annotation note" },
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
                LibraryAction("Delete", Icons.Rounded.Delete, destructive = true) {
                    annotationActionDialogOpen = false
                    annotationDeleteDialogOpen = true
                },
            ),
            onDismiss = { annotationActionDialogOpen = false },
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
private fun LibraryAnnotationRow(
    annotation: LibraryAnnotationItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    LibraryHierarchyRow(
        depth = 0,
        title = annotation.displayTitle ?: annotation.notePreview.ifBlank { "Annotation" },
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
private fun <T> LibraryGridRow(
    items: List<T>,
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
        if (items.size == 1) {
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
            .height(106.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.warning)
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
        preview = kind,
    )

private fun String.toAnnotationColor(): Color =
    when (lowercase()) {
        "blue" -> Color(0xFF5EA2FF)
        "green" -> Color(0xFF34C759)
        "red" -> Color(0xFFFF5A5F)
        else -> Color(0xFFFFD84D)
    }

@Composable
private fun ContinueReadingCard(
    file: LibraryFileItem,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val pageLabel = if (file.pageIndex != null && file.pageCount != null) {
        "Page ${file.pageIndex + 1} of ${file.pageCount}"
    } else {
        "Resume reading"
    }
    val percent = file.progressPercent?.let { "${(it.coerceIn(0f, 1f) * 100).toInt()}%" } ?: ""
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        color = colors.surface,
        shape = VaultShapes.lg,
        border = BorderStroke(1.dp, colors.accentBorder),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = colors.accentSoft,
                contentColor = colors.accent,
                shape = VaultShapes.md,
                border = BorderStroke(1.dp, colors.accentBorder),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Continue reading",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                    color = colors.accent,
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(pageLabel, percent).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryFolderRow(
    folder: LibraryFolderItem,
    viewMode: LibraryViewMode,
    expanded: Boolean,
    isChildExpanded: (String) -> Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onFolderExpandedChange: (String, Boolean) -> Unit,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onFolderLongPress: (LibraryFolderItem) -> Unit,
    onFileLongPress: (LibraryFileItem) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
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
            leading = { topLevel ->
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (topLevel) 16.dp else 13.dp),
                    tint = colors.warning,
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
            enter = expandVertically(animationSpec = tween(140, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(animationSpec = tween(115, easing = FastOutSlowInEasing)),
        ) {
            Column {
                folder.children.forEach { child ->
                    LibraryFolderRow(
                        folder = child,
                        viewMode = viewMode,
                        expanded = isChildExpanded(child.id),
                        isChildExpanded = isChildExpanded,
                        onToggle = { onFolderExpandedChange(child.id, !isChildExpanded(child.id)) },
                        onOpen = { onFolderClick(child.id) },
                        onLongPress = { onFolderLongPress(child) },
                        onFolderExpandedChange = onFolderExpandedChange,
                        onFolderClick = onFolderClick,
                        onAttachmentClick = onAttachmentClick,
                        onFolderLongPress = onFolderLongPress,
                        onFileLongPress = onFileLongPress,
                    )
                }
                folder.files.forEach { file ->
                    LibraryNestedFileRow(
                        file = file,
                        depth = folder.depth + 1,
                        showMetadata = false,
                        dense = viewMode == LibraryViewMode.Icons,
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
    showMetadata: Boolean,
    dense: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val progress = if (file.pageIndex != null && file.pageCount != null) {
        " · p. ${file.pageIndex + 1}/${file.pageCount}"
    } else {
        ""
    }
    LibraryHierarchyRow(
        depth = depth,
        title = file.name,
        subtitle = if (showMetadata) "${file.kind} · ${file.size} · ${file.meta}$progress" else null,
        leading = { topLevel ->
            AttachmentThumbnail(
                mimeType = file.mimeType,
                localPath = file.localPath,
                kind = file.kind,
                size = if (topLevel) 16.dp else 13.dp,
            )
        },
        onClick = onClick,
        onLongClick = onLongPress,
        dense = dense,
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
    expanded: Boolean = false,
    chevronRotation: Float = 0f,
    onToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    dense: Boolean = false,
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
                        dense -> 6.dp
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
                    style = if (topLevel) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                    } else {
                        MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500)
                    },
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    val colors = VaultThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Surface(
                        onClick = action.onClick,
                        color = if (action.selected) colors.accentSoft else colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, if (action.selected) colors.accentBorder else colors.border),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(action.icon, null, modifier = Modifier.size(17.dp), tint = if (action.destructive) colors.warning else colors.accent)
                            Text(action.label, color = if (action.destructive) colors.warning else colors.text, fontWeight = FontWeight.W700)
                        }
                    }
                }
            }
        },
        confirmButton = {},
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
    val onClick: () -> Unit,
)

private fun LibraryFolderItem.flatten(): List<LibraryFolderItem> =
    listOf(this) + children.flatMap { it.flatten() }

private fun LibraryFolderItem.containsFolder(folderId: String): Boolean =
    id == folderId || children.any { it.containsFolder(folderId) }
