package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.ui.components.EditorBlock
import com.myvault.app.ui.components.EditorBlockType
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType

fun NoteEntity.toNoteCard(parentName: String? = null, tableCount: Int = 0): VaultNoteCardData =
    VaultNoteCardData(title = title, meta = parentName ?: "Edited ${updatedAt.toRelativeTime()}", id = id, tableCount = tableCount, preview = bodyPlainText.previewText())

fun NoteEntity.toRecentCard(): VaultNoteCardData =
    VaultNoteCardData(title = title, meta = "Edited ${updatedAt.toRelativeTime()}", id = id, preview = bodyPlainText.previewText())

fun buildTree(
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    attachments: List<AttachmentEntity>,
    tables: List<NoteTableEntity>,
    mode: String = FOLDER_MODE_STUDY,
): List<VaultTreeItem> {
    val visibleFolders = folders.filter { it.mode == mode }
    val visibleFolderIds = visibleFolders.map { it.id }.toSet()
    val visibleNotes = notes.filter { note ->
        note.folderId in visibleFolderIds || (note.folderId == null && mode == FOLDER_MODE_STUDY)
    }
    val foldersByParent = visibleFolders.groupBy { it.parentId }
    val notesByFolder = visibleNotes.groupBy { it.folderId }
    val attachmentsByNote = attachments.groupBy { it.noteId }
    val tablesByNote = tables.groupBy { it.noteId }

    fun folderItem(folder: FolderEntity, depth: Int): VaultTreeItem {
        val childFolders = foldersByParent[folder.id].orEmpty().sortedBy { it.orderIndex }.map { folderItem(it, depth + 1) }
        val childNotes = notesByFolder[folder.id].orEmpty().map { note ->
            note.toTreeItem(attachmentsByNote, tablesByNote)
        }
        val children = childFolders + childNotes
        return VaultTreeItem(
            id = folder.id,
            name = folder.name,
            type = VaultTreeItemType.Folder,
            count = countNotes(folder, foldersByParent, notesByFolder),
            updatedAt = folder.updatedAt,
            favourite = folder.isFavourite,
            children = children,
        )
    }

    val rootFolders = foldersByParent[null].orEmpty().sortedBy { it.orderIndex }.map { folderItem(it, 0) }
    val rootNotes = notesByFolder[null].orEmpty().map { note -> note.toTreeItem(attachmentsByNote, tablesByNote) }
    return rootFolders + rootNotes
}

private fun NoteEntity.toTreeItem(
    attachmentsByNote: Map<String, List<AttachmentEntity>>,
    tablesByNote: Map<String, List<NoteTableEntity>>,
): VaultTreeItem =
    VaultTreeItem(
        id = id,
        name = title,
        type = VaultTreeItemType.Note,
        edited = updatedAt.toRelativeTime(),
        updatedAt = updatedAt,
        attachmentCount = attachmentsByNote[id].orEmpty().size,
        tableCount = tablesByNote[id].orEmpty().size,
        pinned = isPinned,
        favourite = isFavourite,
        preview = bodyPlainText.previewText(),
    )

private fun countNotes(
    folder: FolderEntity,
    foldersByParent: Map<String?, List<FolderEntity>>,
    notesByFolder: Map<String?, List<NoteEntity>>,
): Int {
    val own = notesByFolder[folder.id].orEmpty().size
    val childCount = foldersByParent[folder.id].orEmpty().sumOf { countNotes(it, foldersByParent, notesByFolder) }
    return own + childCount
}

fun BlockEntity.toEditorBlock(): EditorBlock = when (type) {
    "rich_html" -> EditorBlock(EditorBlockType.Paragraph, content.stripHtml(), id = id)
    "rich_body" -> EditorBlock(EditorBlockType.Paragraph, content.substringAfter("\"text\":\"", content).substringBefore("\"").stripHtml(), id = id)
    "heading" -> EditorBlock(EditorBlockType.Heading, content, id = id)
    "quote" -> EditorBlock(EditorBlockType.Quote, content, id = id)
    "checklist_checked" -> EditorBlock(EditorBlockType.Checklist, content, checked = true, id = id)
    "checklist" -> EditorBlock(EditorBlockType.Checklist, content, id = id)
    "bullet" -> EditorBlock(EditorBlockType.BulletList, content, id = id)
    "numbered" -> EditorBlock(EditorBlockType.NumberedList, content, id = id)
    "link" -> EditorBlock(EditorBlockType.Link, content, id = id)
    "divider" -> EditorBlock(EditorBlockType.Divider, id = id)
    "attachment" -> {
        val parts = content.split("|", limit = 2)
        EditorBlock(EditorBlockType.Attachment, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, id = id)
    }
    "image" -> EditorBlock(EditorBlockType.Image, content, "Image", id = id)
    else -> EditorBlock(EditorBlockType.Paragraph, content, id = id)
}

private fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>|</h[1-6]>|</li>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

fun Long.toRelativeTime(): String {
    val diff = (System.currentTimeMillis() - this).coerceAtLeast(0)
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

fun String.previewText(): String =
    lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("\n")

fun AttachmentEntity.kindLabel(): String = when {
    mimeType.startsWith("image/") -> "Image"
    mimeType.startsWith("audio/") -> "Audio"
    mimeType == "application/pdf" -> "PDF"
    else -> "Doc"
}

fun AttachmentEntity.sizeLabel(): String = when {
    sizeBytes >= 1_000_000 -> "${sizeBytes / 100_000 / 10.0} MB"
    else -> "${sizeBytes / 1000} KB"
}
