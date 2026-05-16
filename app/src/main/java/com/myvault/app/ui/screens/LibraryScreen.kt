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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.UploadFile
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
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.LibraryViewMode

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFile: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryArchiveScreen(
        title = "Library",
        subtitle = "Books, PDFs, files, and archives",
        uiState = uiState,
        onBackClick = null,
        currentFolderId = null,
        onFolderClick = onFolderClick,
        onAttachmentClick = onAttachmentClick,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onMoveFolder = onMoveFolder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFile = onImportFile,
        modifier = modifier,
    )
}

@Composable
fun LibraryFolderScreen(
    uiState: LibraryUiState,
    onBackClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFile: (Uri) -> Unit,
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
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onMoveFolder = onMoveFolder,
        onDeleteFolder = onDeleteFolder,
        onFolderExpandedChange = onFolderExpandedChange,
        onViewModeChange = onViewModeChange,
        onImportFile = onImportFile,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryArchiveScreen(
    title: String,
    subtitle: String,
    uiState: LibraryUiState,
    onBackClick: (() -> Unit)?,
    currentFolderId: String?,
    onFolderClick: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onImportFile: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var fabExpanded by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<LibraryFolderDialog?>(null) }
    var folderName by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<LibraryFolderItem?>(null) }
    var actionDialogOpen by remember { mutableStateOf(false) }
    var moveDialogOpen by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportFile)
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
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
            ) {
                item {
                    if (onBackClick == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.displayMedium, color = colors.text)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    } else {
                        ScreenTopBar(onBackClick = onBackClick)
                        Column(
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800), color = colors.text)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                item {
                    LibraryViewModeRow(uiState.viewMode, onViewModeChange)
                }

                uiState.continueReading?.let { file ->
                    item {
                        ContinueReadingCard(
                            file = file,
                            onClick = { onAttachmentClick(file.id) },
                        )
                    }
                }

                item {
                    SectionLabel(label = if (currentFolderId == null) "Collections" else "Subfolders")
                }

                if (uiState.folders.isEmpty()) {
                    item {
                        LibraryEmptyState(
                            icon = Icons.Rounded.Folder,
                            text = if (currentFolderId == null) "Create a Library folder to begin your archive" else "No subfolders yet",
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
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(VaultSpacing.xs))
                    SectionLabel(label = "Files")
                }

                if (uiState.files.isEmpty()) {
                    item {
                        LibraryEmptyState(icon = Icons.Rounded.InsertDriveFile, text = "No files yet")
                    }
                } else if (uiState.viewMode == LibraryViewMode.Grid) {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 136.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(((uiState.files.size + 1) / 2 * 146).coerceAtLeast(146).dp),
                            contentPadding = PaddingValues(horizontal = VaultSpacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        ) {
                            items(uiState.files, key = { it.id }) { file ->
                                LibraryFileCard(file, grid = true, onClick = { onAttachmentClick(file.id) })
                            }
                        }
                    }
                } else {
                    items(uiState.files, key = { it.id }) { file ->
                        LibraryFileCard(
                            file = file,
                            compact = uiState.viewMode == LibraryViewMode.Compact,
                            onClick = { onAttachmentClick(file.id) },
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
                        "Import File" -> importPicker.launch(arrayOf("*/*"))
                        "New Library Folder" -> {
                            folderName = ""
                            folderDialog = LibraryFolderDialog.Create(parentId = currentFolderId)
                        }
                    }
                },
            )
        }
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
        val targets = uiState.allFolders
            .flatMap { it.flatten() }
            .filterNot { it.id == selectedId || it.containsFolder(selectedId) }
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
private fun LibraryViewModeRow(
    selected: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = VaultSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val colors = VaultThemeTokens.colors
            Surface(
                onClick = { onViewModeChange(mode) },
                color = if (selected == mode) colors.accentSoft else colors.surface,
                contentColor = if (selected == mode) colors.accent else colors.textSecondary,
                shape = VaultShapes.pill,
                border = BorderStroke(1.dp, if (selected == mode) colors.accentBorder else colors.border),
            ) {
                Text(
                    text = mode.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
                )
            }
        }
    }
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
) {
    val colors = VaultThemeTokens.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "library-folder-chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (folder.depth > 0) {
                Row(
                    modifier = Modifier.width((folder.depth * 16).dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(1.dp)
                            .height(58.dp)
                            .background(colors.border.copy(alpha = 0.55f)),
                    )
                }
            }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(VaultShapes.md),
            color = if (expanded) colors.surface else colors.elevated,
            shape = VaultShapes.md,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Surface(
                    onClick = onToggle,
                    color = Color.Transparent,
                    shape = VaultShapes.sm,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Expand folder",
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = rotation },
                            tint = colors.textMuted,
                        )
                    }
                }
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (folder.depth > 0) 18.dp else 20.dp),
                    tint = colors.warning,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (viewMode != LibraryViewMode.Compact && folder.count > 0) {
                        Text(
                            text = "${folder.count} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                }
                if (folder.count > 0) {
                    Text(
                        text = folder.count.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                        color = colors.textMuted,
                    )
                }
            }
        }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
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
                    )
                }
                folder.files.forEach { file ->
                    LibraryNestedFileRow(
                        file = file,
                        depth = folder.depth + 1,
                        compact = viewMode == LibraryViewMode.Compact,
                        onClick = { onAttachmentClick(file.id) },
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
    compact: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
    ) {
        Row(
            modifier = Modifier.width((depth * 16).dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(1.dp)
                    .height(if (compact) 42.dp else 54.dp)
                    .background(colors.border.copy(alpha = 0.45f)),
            )
        }
        LibraryFileCard(
            file = file,
            compact = compact,
            modifier = Modifier.weight(1f),
            horizontalPadding = 0.dp,
            onClick = onClick,
        )
    }
}

@Composable
private fun LibraryFileCard(
    file: LibraryFileItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    grid: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = VaultSpacing.screen,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (grid) 0.dp else horizontalPadding),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        if (grid) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AttachmentThumbnail(mimeType = file.mimeType, localPath = file.localPath, kind = file.kind, size = 42.dp)
                Text(file.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(file.size, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AttachmentThumbnail(mimeType = file.mimeType, localPath = file.localPath, kind = file.kind, size = if (compact) 30.dp else 38.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        val progress = if (file.pageIndex != null && file.pageCount != null) {
                            " · p. ${file.pageIndex + 1}/${file.pageCount}"
                        } else {
                            ""
                        }
                        Text("${file.kind} · ${file.size} · ${file.meta}$progress", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                        color = colors.surface,
                        shape = VaultShapes.md,
                        border = BorderStroke(1.dp, colors.border),
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
    val onClick: () -> Unit,
)

private fun LibraryFolderItem.flatten(): List<LibraryFolderItem> =
    listOf(this) + children.flatMap { it.flatten() }

private fun LibraryFolderItem.containsFolder(folderId: String): Boolean =
    id == folderId || children.any { it.containsFolder(folderId) }
