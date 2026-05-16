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
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
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
import com.myvault.app.ui.components.SectionLabel
import com.myvault.app.ui.components.VaultTopBar
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
    onImportFile: (Uri) -> Unit,
    onThemeClick: () -> Unit,
    onQuickBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    quickBackupRecommended: Boolean = false,
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
        onImportFile = onImportFile,
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
    onCreateFolder: (parentId: String?, name: String) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onMoveFolder: (folderId: String, parentId: String?) -> Unit,
    onDeleteFolder: (folderId: String) -> Unit,
    onFolderExpandedChange: (folderId: String, expanded: Boolean) -> Unit,
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
    onImportFile: (Uri) -> Unit,
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
    var selectedFolder by remember { mutableStateOf<LibraryFolderItem?>(null) }
    var actionDialogOpen by remember { mutableStateOf(false) }
    var moveDialogOpen by remember { mutableStateOf(false) }
    var quickBackupConfirmOpen by remember { mutableStateOf(false) }
    val hierarchyViewMode = LibraryViewMode.Compact
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
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    if (onBackClick == null) {
                        VaultTopBar(title = title) {
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
                        Text(
                            text = subtitle,
                            modifier = Modifier.padding(horizontal = VaultSpacing.screen),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        if (uiState.continueReading == null) {
                            Spacer(modifier = Modifier.height(2.dp))
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
                            viewMode = hierarchyViewMode,
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

                if (uiState.files.isEmpty() && currentFolderId != null) {
                    item {
                        LibraryEmptyState(icon = Icons.Rounded.InsertDriveFile, text = "No files yet")
                    }
                } else {
                    items(uiState.files, key = { it.id }) { file ->
                        LibraryNestedFileRow(
                            file = file,
                            depth = 0,
                            showMetadata = false,
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
        LibraryHierarchyRow(
            depth = folder.depth,
            title = folder.name,
            subtitle = if (viewMode != LibraryViewMode.Compact && folder.count > 0) "${folder.count} items" else null,
            count = folder.count.takeIf { it > 0 }?.toString(),
            leading = {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.warning,
                )
            },
            expanded = expanded,
            chevronRotation = rotation,
            onToggle = onToggle,
            onClick = onOpen,
            onLongClick = onLongPress,
        )
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
                        showMetadata = viewMode != LibraryViewMode.Compact,
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
    showMetadata: Boolean,
    onClick: () -> Unit,
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
        leading = {
            AttachmentThumbnail(
                mimeType = file.mimeType,
                localPath = file.localPath,
                kind = file.kind,
                size = 34.dp,
            )
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryHierarchyRow(
    depth: Int,
    title: String,
    subtitle: String? = null,
    count: String? = null,
    leading: @Composable () -> Unit,
    expanded: Boolean = false,
    chevronRotation: Float = 0f,
    onToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = VaultThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        if (depth > 0) {
            Row(
                modifier = Modifier.width((depth * 16).dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(1.dp)
                        .height(58.dp)
                        .background(colors.border.copy(alpha = 0.42f)),
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
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (onToggle != null) {
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
                                    .graphicsLayer { rotationZ = chevronRotation },
                                tint = colors.textMuted,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
                leading()
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
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
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
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
