package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.FOLDER_MODE_LIBRARY
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val database: VaultDatabase,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val blockDao: BlockDao,
    private val tagDao: TagDao,
    private val noteTableDao: NoteTableDao,
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

    fun observeSubfolders(id: String) = folderDao.observeChildren(id)

    fun observeLibraryFolders() = folderDao.observeAll()

    fun observeDeletedFolders() = folderDao.observeDeleted()

    fun observeAllFoldersIncludingDeleted() = folderDao.observeAllIncludingDeleted()

    suspend fun createFolder(parentId: String?, name: String, mode: String = FOLDER_MODE_STUDY): String {
        val now = System.currentTimeMillis()
        val folderId = UUID.randomUUID().toString()
        val folders = folderDao.getAll()
        val folderMode = parentId?.let { parent -> folders.firstOrNull { it.id == parent }?.mode } ?: mode
        val orderIndex = folders
            .filter { it.parentId == parentId }
            .maxOfOrNull { it.orderIndex }
            ?.plus(1) ?: 0

        folderDao.upsertAll(
            listOf(
                FolderEntity(
                    id = folderId,
                    parentId = parentId,
                    name = name.ifBlank { "New Folder" },
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

    suspend fun renameFolder(folderId: String, name: String) {
        folderDao.updateName(folderId, name.ifBlank { "Untitled folder" }, System.currentTimeMillis())
    }

    suspend fun moveFolder(folderId: String, parentId: String?) {
        val folders = folderDao.getAll()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val oldParentId = folder.parentId
        val descendantIds = descendantFolderIds(folderId, folders).toSet()
        if (parentId == folderId || parentId in descendantIds) return

        val orderIndex = folders
            .filter { it.parentId == parentId && it.id != folderId }
            .maxOfOrNull { it.orderIndex }
            ?.plus(1) ?: 0
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

    suspend fun moveFolderToMode(folderId: String, mode: String) {
        val folders = folderDao.getAll()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val folderIds = descendantFolderIds(folderId, folders) + folderId
        val now = System.currentTimeMillis()
        if (folder.parentId != null && folder.parentId !in folderIds) {
            val orderIndex = folders
                .filter { it.parentId == null && it.id != folderId && it.mode == mode }
                .maxOfOrNull { it.orderIndex }
                ?.plus(1) ?: 0
            folderDao.updateParentAndOrder(folderId, null, orderIndex, now)
            normalizeOrderIndexes(folder.parentId)
        }
        folderDao.updateMode(folderIds, mode, now)
        normalizeOrderIndexes(null)
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
            if (folders.firstOrNull { it.id == folderId }?.mode == FOLDER_MODE_LIBRARY) {
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
            if (folders.firstOrNull { it.id == folderId }?.mode == FOLDER_MODE_LIBRARY) {
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
        val libraryAttachments = if (folders.firstOrNull { it.id == folderId }?.mode == FOLDER_MODE_LIBRARY) {
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

private fun List<com.myvault.app.data.local.entity.AttachmentEntity>.deleteLocalFiles() {
    forEach { attachment ->
        runCatching {
            val file = File(attachment.localPath)
            if (file.exists() && file.isFile) file.delete()
        }
    }
}
