package com.myvault.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class VaultExplorerMoveTarget(
    val id: String?,
    val label: String,
)

private enum class ExplorerActionPanel {
    Actions,
    CreateFolder,
    Rename,
    Move,
    Delete,
}

/**
 * One action surface for every Explorer branch. The drawer owns discovery and
 * this host owns mutations, so Study, Library, and Courses cannot drift apart.
 */
@Composable
fun VaultExplorerActionHost(
    title: String,
    nodeType: VaultMobileWebExplorerNodeType?,
    creationOnly: Boolean,
    canCreateNote: Boolean,
    canCreateFolder: Boolean,
    createFolderActionLabel: String = "New folder",
    canImportDocuments: Boolean,
    canRename: Boolean,
    canMove: Boolean,
    canPin: Boolean,
    pinned: Boolean,
    canDelete: Boolean,
    moveTargets: List<VaultExplorerMoveTarget>,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCreateNote: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onImportDocuments: (List<Uri>) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String?) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var panel by remember(title, nodeType, creationOnly) { mutableStateOf(ExplorerActionPanel.Actions) }
    var textInput by remember(title, panel) { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onImportDocuments(uris)
        onDismiss()
    }

    when (panel) {
        ExplorerActionPanel.Actions -> {
            val actions = buildList {
                if (!creationOnly && nodeType != null) {
                    add(VaultModalAction("Open", Icons.Rounded.FolderOpen) {
                        onOpen()
                        onDismiss()
                    })
                }
                if (canCreateNote) {
                    add(VaultModalAction("New note", Icons.Rounded.Description) {
                        onCreateNote()
                        onDismiss()
                    })
                }
                if (canCreateFolder) {
                    add(VaultModalAction(createFolderActionLabel, Icons.Rounded.CreateNewFolder) {
                        textInput = ""
                        panel = ExplorerActionPanel.CreateFolder
                    })
                }
                if (canImportDocuments) {
                    add(VaultModalAction("Upload document", Icons.Rounded.PictureAsPdf) {
                        importLauncher.launch(arrayOf("application/pdf", "application/*"))
                    })
                }
                if (!creationOnly && canRename) {
                    add(VaultModalAction("Rename", Icons.Rounded.DriveFileRenameOutline) {
                        textInput = title
                        panel = ExplorerActionPanel.Rename
                    })
                }
                if (!creationOnly && canMove) {
                    add(VaultModalAction("Move", Icons.Rounded.Folder) {
                        panel = ExplorerActionPanel.Move
                    })
                }
                if (!creationOnly && canPin) {
                    add(VaultModalAction(if (pinned) "Unpin" else "Pin", Icons.Rounded.PushPin) {
                        onTogglePin()
                        onDismiss()
                    })
                }
                if (!creationOnly && canDelete) {
                    add(VaultModalAction("Delete", Icons.Rounded.Delete, destructive = true) {
                        panel = ExplorerActionPanel.Delete
                    })
                }
            }
            VaultActionModal(
                title = title,
                actions = actions,
                onDismiss = onDismiss,
            )
        }

        ExplorerActionPanel.CreateFolder -> VaultFormModal(
            title = createFolderActionLabel,
            confirmLabel = "Create",
            icon = Icons.Rounded.CreateNewFolder,
            enabled = textInput.isNotBlank(),
            onDismiss = { panel = ExplorerActionPanel.Actions },
            onConfirm = {
                onCreateFolder(textInput.trim())
                onDismiss()
            },
        ) {
            VaultTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = if (createFolderActionLabel == "New course") "Course name" else "Folder name",
                singleLine = true,
            )
        }

        ExplorerActionPanel.Rename -> VaultFormModal(
            title = "Rename",
            confirmLabel = "Save",
            icon = Icons.Rounded.DriveFileRenameOutline,
            enabled = textInput.isNotBlank(),
            onDismiss = { panel = ExplorerActionPanel.Actions },
            onConfirm = {
                onRename(textInput.trim())
                onDismiss()
            },
        ) {
            VaultTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = "Name",
                singleLine = true,
            )
        }

        ExplorerActionPanel.Move -> VaultActionModal(
            title = "Move $title",
            actions = moveTargets.map { target ->
                VaultModalAction(target.label, Icons.Rounded.Folder) {
                    onMove(target.id)
                    onDismiss()
                }
            },
            onDismiss = { panel = ExplorerActionPanel.Actions },
        )

        ExplorerActionPanel.Delete -> VaultConfirmModal(
            title = "Delete $title?",
            message = if (nodeType == VaultMobileWebExplorerNodeType.Folder) {
                "This folder and everything inside it will be removed."
            } else {
                "This item will be removed from MyVault."
            },
            confirmLabel = "Delete",
            dismissLabel = "Keep",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onDismiss = { panel = ExplorerActionPanel.Actions },
            onConfirm = {
                onDelete()
                onDismiss()
            },
        )
    }
}
