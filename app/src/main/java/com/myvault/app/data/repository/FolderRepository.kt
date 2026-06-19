package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.FolderStickyNoteDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.NoteVersionDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.ui.components.VaultTreeItem
import com.myvault.app.ui.components.VaultTreeItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val database: VaultDatabase,
    private val aiConversationDao: AiConversationDao,
    private val folderDao: FolderDao,
    private val folderStickyNoteDao: FolderStickyNoteDao,
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val blockDao: BlockDao,
    private val tagDao: TagDao,
    private val noteTableDao: NoteTableDao,
    private val noteVersionDao: NoteVersionDao,
    private val pdfAnnotationDao: PdfAnnotationDao,
    private val pdfReadingProgressDao: PdfReadingProgressDao,
    private val sourceBacklinkDao: SourceBacklinkDao,
    private val knowledgeTagDao: KnowledgeTagDao,
) {
    fun observeWorkspaceTree() = combine(
        folderDao.observeAll(),
        noteDao.observeAll(),
        attachmentDao.observeAll(),
        noteTableDao.observeAll(),
        ::buildTree,
    )

    fun observeWorkspaceTree(mode: String) = combine(
        folderDao.observeAll(),
        noteDao.observeAll(),
        attachmentDao.observeAll(),
        noteTableDao.observeAll(),
    ) { folders, notes, attachments, tables ->
        buildTree(folders, notes, attachments, tables, mode)
    }

    fun observeFolder(id: String): Flow<FolderEntity?> = folderDao.observeById(id)

    fun observeFolderContents(id: String): Flow<List<VaultTreeItem>> = combine(
        folderDao.observeAll(),
        noteDao.observeAll(),
        attachmentDao.observeAll(),
        noteTableDao.observeAll(),
    ) { folders, notes, attachments, tables ->
        val mode = folders.firstOrNull { it.id == id }?.mode ?: FOLDER_MODE_STUDY
        buildTree(folders, notes, attachments, tables, mode)
            .findTreeItem(id)
            ?.children
            .orEmpty()
    }

    fun observeWorkspaceTreeForFolder(id: String): Flow<List<VaultTreeItem>> = combine(
        folderDao.observeAll(),
        noteDao.observeAll(),
        attachmentDao.observeAll(),
        noteTableDao.observeAll(),
    ) { folders, notes, attachments, tables ->
        val mode = folders.firstOrNull { it.id == id }?.mode ?: FOLDER_MODE_STUDY
        buildTree(folders, notes, attachments, tables, mode)
    }

    fun observeSubfolders(id: String) = folderDao.observeChildren(id)

    fun observeLibraryFolders() = folderDao.observeAll()

    fun observeDeletedFolders() = folderDao.observeDeleted()

    fun observeAllFoldersIncludingDeleted() = folderDao.observeAllIncludingDeleted()

    suspend fun createFolder(parentId: String?, name: String, mode: String = FOLDER_MODE_STUDY, description: String? = null): String {
        val now = System.currentTimeMillis()
        val folderId = UUID.randomUUID().toString()
        val folders = folderDao.getAll()
        val folderMode = parentId?.let { parent -> folders.firstOrNull { it.id == parent }?.mode } ?: mode
        val folderMax = folders.filter { it.parentId == parentId }.maxOfOrNull { it.orderIndex } ?: -1
        val noteMax = noteDao.getAll()
            .filter { it.folderId == parentId && it.parentNoteId == null }
            .maxOfOrNull { it.orderIndex } ?: -1
        val orderIndex = maxOf(folderMax, noteMax) + 1

        folderDao.upsertAll(
            listOf(
                FolderEntity(
                    id = folderId,
                    parentId = parentId,
                    name = name.ifBlank { "New Folder" },
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    orderIndex = orderIndex,
                    isFavourite = false,
                    mode = folderMode,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        return folderId
    }

    suspend fun ensureRootFolderForMode(name: String, mode: String): String {
        val folders = folderDao.getAll()
        folders.firstOrNull {
            it.parentId == null &&
                it.mode == mode &&
                it.name.equals(name, ignoreCase = true)
        }?.let { return it.id }

        return createFolder(parentId = null, name = name, mode = mode)
    }

    suspend fun renameFolder(folderId: String, name: String) {
        folderDao.updateName(folderId, name.ifBlank { "Untitled folder" }, System.currentTimeMillis())
    }

    suspend fun updateFolderDetails(folderId: String, name: String, description: String?) {
        folderDao.updateDetails(
            id = folderId,
            name = name.ifBlank { "Untitled folder" },
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun moveFolder(folderId: String, parentId: String?) {
        val folders = folderDao.getAll()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val oldParentId = folder.parentId
        val descendantIds = descendantFolderIds(folderId, folders).toSet()
        if (parentId == folderId || parentId in descendantIds) return

        val folderMax = folders
            .filter { it.parentId == parentId && it.id != folderId }
            .maxOfOrNull { it.orderIndex } ?: -1
        val noteMax = noteDao.getAll()
            .filter { it.folderId == parentId && it.parentNoteId == null }
            .maxOfOrNull { it.orderIndex } ?: -1
        val orderIndex = maxOf(folderMax, noteMax) + 1
        folderDao.updateParentAndOrder(folderId, parentId, orderIndex, System.currentTimeMillis())
        normalizeOrderIndexes(oldParentId)
        normalizeOrderIndexes(parentId)
    }

    suspend fun moveFolderWithinSiblings(folderId: String, direction: Int) {
        val folders = folderDao.getAll()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val siblings = folders
            .filter { it.parentId == folder.parentId }
            .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
        val currentIndex = siblings.indexOfFirst { it.id == folderId }
        if (currentIndex == -1) return

        val targetIndex = (currentIndex + direction).coerceIn(0, siblings.lastIndex)
        if (targetIndex == currentIndex) return

        val reordered = siblings.toMutableList()
        val moved = reordered.removeAt(currentIndex)
        reordered.add(targetIndex, moved)
        persistSiblingOrder(reordered)
    }

    suspend fun moveTreeItemWithinSiblings(itemId: String, type: VaultTreeItemType, direction: Int) {
        val folders = folderDao.getAll()
        val notes = noteDao.getAll()
        val siblings: List<Pair<VaultTreeItemType, String>> = when (type) {
            VaultTreeItemType.Folder -> {
                val folder = folders.firstOrNull { it.id == itemId } ?: return
                (
                    folders.filter { it.parentId == folder.parentId }.map { Triple(VaultTreeItemType.Folder, it.id, it.orderIndex) } +
                        notes.filter { it.folderId == folder.parentId && it.parentNoteId == null }
                            .map { Triple(VaultTreeItemType.Note, it.id, it.orderIndex) }
                    ).sortedBy { it.third }.map { it.first to it.second }
            }
            VaultTreeItemType.Note -> {
                val note = notes.firstOrNull { it.id == itemId } ?: return
                if (note.parentNoteId != null) {
                    notes.filter { it.parentNoteId == note.parentNoteId }
                        .sortedBy { it.orderIndex }
                        .map { VaultTreeItemType.Note to it.id }
                } else {
                    (
                        folders.filter { it.parentId == note.folderId }.map { Triple(VaultTreeItemType.Folder, it.id, it.orderIndex) } +
                            notes.filter { it.folderId == note.folderId && it.parentNoteId == null }
                                .map { Triple(VaultTreeItemType.Note, it.id, it.orderIndex) }
                        ).sortedBy { it.third }.map { it.first to it.second }
                }
            }
        }
        val currentIndex = siblings.indexOfFirst { it.second == itemId }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + direction).coerceIn(0, siblings.lastIndex)
        if (targetIndex == currentIndex) return
        val reordered = siblings.toMutableList()
        reordered.add(targetIndex, reordered.removeAt(currentIndex))
        val now = System.currentTimeMillis()
        reordered.forEachIndexed { index, (itemType, id) ->
            if (itemType == VaultTreeItemType.Folder) folderDao.updateOrderIndex(id, index, now)
            else noteDao.updateOrderIndex(id, index, now)
        }
    }

    suspend fun moveFolderToMode(folderId: String, mode: String) {
        database.withTransaction {
            val folders = folderDao.getAll()
            val folder = folders.firstOrNull { it.id == folderId } ?: return@withTransaction
            val folderIds = descendantFolderIds(folderId, folders) + folderId
            val oldParentId = folder.parentId
            val now = System.currentTimeMillis()
            val orderIndex = folders
                .filter { it.parentId == null && it.id !in folderIds && it.mode == mode }
                .maxOfOrNull { it.orderIndex }
                ?.plus(1) ?: 0
            folderDao.updateParentAndOrder(folderId, null, orderIndex, now)
            folderDao.updateMode(folderIds, mode, now)
            if (oldParentId != null) normalizeOrderIndexes(oldParentId)
            normalizeOrderIndexes(null)
        }
    }

    suspend fun deleteFolderTree(folderId: String) {
        database.withTransaction {
            val folders = folderDao.getAll()
            val folderIds = descendantFolderIds(folderId, folders) + folderId
            val noteIds = noteDao.getAll()
                .filter { it.folderId in folderIds }
                .map { it.id }

            val now = System.currentTimeMillis()
            if (noteIds.isNotEmpty()) {
                noteDao.updateDeletedAt(noteIds, now, now)
                attachmentDao.updateDeletedAtForNotes(noteIds, now)
            }
            if (folders.firstOrNull { it.id == folderId }?.mode.isLibraryFolderMode()) {
                attachmentDao.updateDeletedAtForLibraryFolders(folderIds, now)
            }
            folderDao.updateDeletedAt(folderIds, now, now)
        }
    }

    suspend fun restoreFolderTree(folderId: String) {
        database.withTransaction {
            val folders = folderDao.getAllIncludingDeleted()
            val folderIds = descendantFolderIds(folderId, folders) + folderId
            val noteIds = noteDao.getAllIncludingDeleted()
                .filter { it.folderId in folderIds }
                .map { it.id }

            val now = System.currentTimeMillis()
            if (noteIds.isNotEmpty()) {
                noteDao.updateDeletedAt(noteIds, null, now)
                attachmentDao.updateDeletedAtForNotes(noteIds, null)
            }
            if (folders.firstOrNull { it.id == folderId }?.mode.isLibraryFolderMode()) {
                attachmentDao.updateDeletedAtForLibraryFolders(folderIds, null)
            }
            folderDao.updateDeletedAt(folderIds, null, now)
        }
    }

    suspend fun permanentlyDeleteFolderTree(folderId: String) {
        val folders = folderDao.getAllIncludingDeleted()
        val folderIds = descendantFolderIds(folderId, folders) + folderId
        val noteIds = noteDao.getAllIncludingDeleted()
            .filter { it.folderId in folderIds }
            .map { it.id }
        val noteAttachments = if (noteIds.isNotEmpty()) attachmentDao.getForNotes(noteIds) else emptyList()
        val libraryAttachments = if (folders.firstOrNull { it.id == folderId }?.mode.isLibraryFolderMode()) {
            attachmentDao.getAllIncludingDeleted().filter { it.libraryFolderId in folderIds }
        } else {
            emptyList()
        }

        database.withTransaction {
            if (noteIds.isNotEmpty()) {
                val attachmentIds = noteAttachments.map { it.id }
                val annotationIds = if (attachmentIds.isEmpty()) {
                    emptyList()
                } else {
                    pdfAnnotationDao.getAll()
                        .filter { it.attachmentId in attachmentIds }
                        .map { it.id }
                }
                if (attachmentIds.isNotEmpty()) {
                    pdfAnnotationDao.deleteForAttachments(attachmentIds)
                    pdfReadingProgressDao.deleteForAttachments(attachmentIds)
                    sourceBacklinkDao.deleteForAttachments(attachmentIds)
                    knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAttachment, attachmentIds)
                }
                if (annotationIds.isNotEmpty()) {
                    knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, annotationIds)
                }
                blockDao.deleteForNotes(noteIds)
                tagDao.deleteRefsForNotes(noteIds)
                sourceBacklinkDao.deleteForNotes(noteIds)
                knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetNote, noteIds)
                noteTableDao.deleteForNotes(noteIds)
                noteVersionDao.deleteForNotes(noteIds)
                aiConversationDao.deleteMessagesForNotes(noteIds)
                aiConversationDao.deleteConversationsForNotes(noteIds)
                attachmentDao.deleteForNotes(noteIds)
                noteDao.deleteByIds(noteIds)
            }
            if (libraryAttachments.isNotEmpty()) {
                val libraryAttachmentIds = libraryAttachments.map { it.id }
                val libraryAnnotationIds = pdfAnnotationDao.getAll()
                    .filter { it.attachmentId in libraryAttachmentIds }
                    .map { it.id }
                pdfAnnotationDao.deleteForAttachments(libraryAttachmentIds)
                pdfReadingProgressDao.deleteForAttachments(libraryAttachmentIds)
                sourceBacklinkDao.deleteForAttachments(libraryAttachmentIds)
                knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAttachment, libraryAttachmentIds)
                if (libraryAnnotationIds.isNotEmpty()) {
                    knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, libraryAnnotationIds)
                }
                attachmentDao.deleteByIds(libraryAttachmentIds)
            }
            folderStickyNoteDao.deleteForFolders(folderIds)
            folderDao.deleteByIds(folderIds)
        }
        (noteAttachments + libraryAttachments).deleteLocalFiles()
    }

    private fun descendantFolderIds(folderId: String, folders: List<FolderEntity>): List<String> {
        val childrenByParent = folders.groupBy { it.parentId }

        fun collect(parentId: String): List<String> =
            childrenByParent[parentId].orEmpty().flatMap { child ->
                listOf(child.id) + collect(child.id)
            }

        return collect(folderId)
    }

    private suspend fun normalizeOrderIndexes(parentId: String?) {
        val siblings = folderDao.getAll()
            .filter { it.parentId == parentId }
            .sortedWith(compareBy<FolderEntity> { it.orderIndex }.thenBy { it.name.lowercase() })
        persistSiblingOrder(siblings)
    }

    private suspend fun persistSiblingOrder(siblings: List<FolderEntity>) {
        val now = System.currentTimeMillis()
        siblings.forEachIndexed { index, folder ->
            if (folder.orderIndex != index) {
                folderDao.updateOrderIndex(folder.id, index, now)
            }
        }
    }
}

private fun List<VaultTreeItem>.findTreeItem(id: String): VaultTreeItem? {
    for (item in this) {
        if (item.id == id) return item
        item.children.findTreeItem(id)?.let { return it }
    }
    return null
}

private fun String?.isLibraryFolderMode(): Boolean =
    this == FOLDER_MODE_LIBRARY || this == FOLDER_MODE_PERSONAL_LIBRARY

private fun List<com.myvault.app.data.local.entity.AttachmentEntity>.deleteLocalFiles() {
    forEach { attachment ->
        runCatching {
            val file = File(attachment.localPath)
            if (file.exists() && file.isFile) file.delete()
        }
    }
}
