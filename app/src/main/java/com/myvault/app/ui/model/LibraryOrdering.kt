package com.myvault.app.ui.model

import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.ui.viewmodel.LibraryFolderItem

val librarySortModes = StudySortMode.entries.filterNot { it == StudySortMode.Modified }

/** Adapt hierarchy only; Library renders its own file rows and retains PDF metadata. */
fun libraryOrderTree(folders: List<LibraryFolderItem>, files: List<LibraryFileItem>, mode: StudySortMode, openedAt: Map<String, Long>): List<VaultTreeItem> {
    fun tree(folders: List<LibraryFolderItem>, files: List<LibraryFileItem>): List<VaultTreeItem> =
        folders.map { folder -> VaultTreeItem(folder.id, folder.name, VaultTreeItemType.Folder,
            description = folder.description, orderIndex = folder.orderIndex, count = folder.count, colorKey = folder.colorKey,
            createdAt = folder.createdAt, children = tree(folder.children, folder.files)) } +
        files.map { file -> VaultTreeItem(file.id, file.name, VaultTreeItemType.Note,
            orderIndex = file.orderIndex, createdAt = file.createdAt, pinned = file.pinned) }
    fun activity(folders: List<LibraryFolderItem>, files: List<LibraryFileItem>): Map<String, Long> =
        files.associate { it.id to maxOf(it.lastOpenedAt, openedAt[it.id] ?: 0L) } +
            folders.flatMap { activity(it.children, it.files).entries }.associate { it.key to it.value }
    return tree(folders, files).studySorted(mode.takeIf { it in librarySortModes } ?: StudySortMode.Manual, openedAt + activity(folders, files))
}
