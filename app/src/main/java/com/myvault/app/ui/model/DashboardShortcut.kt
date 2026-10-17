package com.myvault.app.ui.model

import com.myvault.app.data.repository.DashboardActivityItem
import com.myvault.app.data.repository.DashboardActivityKind
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.viewmodel.LibraryFileItem
import com.myvault.app.data.local.entity.FolderEntity

enum class DashboardShortcutKind { Note, Document, Folder }

data class DashboardShortcut(
    val id: String,
    val kind: DashboardShortcutKind,
    val title: String,
    val metadata: String,
    val mode: String = "study",
) {
    val key: String get() = "${kind.name}:$id"
}

fun dashboardReadingPosition(pageIndex: Int?, pageCount: Int?): String =
    if (pageIndex != null && pageCount != null && pageCount > 0 && pageIndex in 0 until pageCount) {
        "Last read · Page ${pageIndex + 1}"
    } else "Last opened · Position unavailable"

fun dashboardLibraryRecents(items: List<DashboardActivityItem>): List<DashboardActivityItem> =
    items.filter { it.kind == DashboardActivityKind.Library }
        .sortedByDescending { it.openedAt }.distinctBy { it.destinationId }.take(5)

fun dashboardShortcuts(
    notes: List<VaultNoteCardData>,
    files: List<LibraryFileItem>,
    folders: List<FolderEntity>,
    order: List<String> = emptyList(),
): List<DashboardShortcut> {
    val items = (
        notes.map { DashboardShortcut(it.id, DashboardShortcutKind.Note, it.title, "Study · Note") } +
            files.map { DashboardShortcut(it.id, DashboardShortcutKind.Document, it.name,
                if (it.pageIndex != null && it.pageCount != null && it.pageIndex in 0 until it.pageCount)
                    "Library · Page ${it.pageIndex + 1}" else "Library · Document") } +
            folders.map { DashboardShortcut(it.id, DashboardShortcutKind.Folder, it.name,
                if (it.mode == "library" || it.mode == "personal_library") "Library · Folder" else "Study · Folder", it.mode) }
    ).distinctBy { it.key }

    return if (order.isEmpty()) {
        items.sortedBy { it.key }
    } else {
        val orderMap = order.withIndex().associate { it.value to it.index }
        items.sortedWith(
            compareBy<DashboardShortcut> { orderMap[it.key] ?: (Int.MAX_VALUE - 1000) }
                .thenBy { it.key }
        )
    }
}

internal fun availableDashboardFolders(ids: Set<String>, folders: List<FolderEntity>): List<FolderEntity> {
    val byId = folders.associateBy { it.id }
    fun available(folder: FolderEntity): Boolean {
        val visited = mutableSetOf<String>()
        var current: FolderEntity? = folder
        while (current != null) {
            if (!visited.add(current.id) || current.deletedAt != null || current.mode != folder.mode) return false
            val parent = current.parentId ?: return true
            current = byId[parent] ?: return false
        }
        return false
    }
    return folders.filter { it.id in ids && it.mode in setOf("study", "personal", "library", "personal_library") && available(it) }
        .sortedBy { it.id }
}
