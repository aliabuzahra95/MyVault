package com.myvault.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.ui.screens.AttachmentSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val attachmentDao: AttachmentDao,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val pdfAnnotationDao: PdfAnnotationDao,
    private val pdfReadingProgressDao: PdfReadingProgressDao,
    private val sourceBacklinkDao: SourceBacklinkDao,
    private val knowledgeTagDao: KnowledgeTagDao,
) {
    fun observeAllCards() = combine(attachmentDao.observeAll(), noteDao.observeAll()) { attachments, notes ->
        val noteTitles = notes.associate { it.id to it.title }
        attachments.map {
            AttachmentSample(
                name = it.fileName,
                note = if (it.libraryFolderId != null) "Library" else noteTitles[it.noteId] ?: "Untitled note",
                size = it.sizeLabel(),
                date = it.createdAt.toRelativeTime(),
                kind = it.kindLabel(),
                id = it.id,
                noteId = it.noteId,
                mimeType = it.mimeType,
                localPath = it.localPath,
            )
        }
    }

    fun observeCardsForMode(mode: String) = combine(
        attachmentDao.observeAll(),
        noteDao.observeAll(),
        folderDao.observeAll(),
    ) { attachments, notes, folders ->
        val visibleFolderIds = folders.filter { it.mode == mode }.map { it.id }.toSet()
        val visibleNotes = notes.filter { note ->
            note.folderId in visibleFolderIds || (note.folderId == null && mode == FOLDER_MODE_STUDY)
        }
        val visibleNoteIds = visibleNotes.map { it.id }.toSet()
        val noteTitles = visibleNotes.associate { it.id to it.title }
        attachments
            .filter { it.noteId in visibleNoteIds }
            .map {
                AttachmentSample(
                    name = it.fileName,
                    note = noteTitles[it.noteId] ?: "Untitled note",
                    size = it.sizeLabel(),
                    date = it.createdAt.toRelativeTime(),
                    kind = it.kindLabel(),
                    id = it.id,
                    noteId = it.noteId,
                    mimeType = it.mimeType,
                    localPath = it.localPath,
                )
            }
    }

    fun observeAttachment(id: String) = attachmentDao.observeById(id)

    fun observeForNote(noteId: String) = attachmentDao.observeForNote(noteId)

    fun observeCountForNote(noteId: String) = attachmentDao.observeCountForNote(noteId)

    fun observeLibraryFiles() = attachmentDao.observeLibraryFiles()

    fun observeForLibraryFolder(folderId: String) = attachmentDao.observeForLibraryFolder(folderId)

    fun observeRootLibraryFiles() = attachmentDao.observeRootLibraryFiles()

    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.displayName(uri)
    }

    suspend fun findDuplicateLibraryPdf(folderId: String?, uri: Uri): AttachmentEntity? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = resolver.displayName(uri).sanitizeFileName()
        val mimeType = resolver.getType(uri).orEmpty()
        if (!fileName.isPdfFileName() && mimeType != "application/pdf") return@withContext null
        attachmentDao.getAll()
            .firstOrNull { attachment ->
                attachment.libraryFolderId == folderId &&
                    attachment.fileName.equals(fileName, ignoreCase = true) &&
                    (attachment.mimeType == "application/pdf" || attachment.fileName.isPdfFileName())
            }
    }

    suspend fun attachDocument(noteId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val id = UUID.randomUUID().toString()
        val fileName = resolver.displayName(uri).sanitizeFileName().ifBlank { "attachment-$id" }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val attachmentsDir = File(context.filesDir, "attachments/$noteId").apply { mkdirs() }
        val localFile = File(attachmentsDir, "${id}_$fileName")

        resolver.openInputStream(uri)?.use { input ->
            localFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected file")

        val sizeBytes = resolver.fileSize(uri).takeIf { it > 0 } ?: localFile.length()
        attachmentDao.upsertAll(
            listOf(
                AttachmentEntity(
                    id = id,
                    noteId = noteId,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    localPath = localFile.absolutePath,
                    remoteUrl = null,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        id
    }

    suspend fun importLibraryDocument(folderId: String?, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val id = UUID.randomUUID().toString()
        val fileName = resolver.displayName(uri).sanitizeFileName().ifBlank { "library-file-$id" }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val attachmentsDir = File(context.filesDir, "library/${folderId ?: "root"}").apply { mkdirs() }
        val localFile = File(attachmentsDir, "${id}_$fileName")

        resolver.openInputStream(uri)?.use { input ->
            localFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected file")

        val sizeBytes = resolver.fileSize(uri).takeIf { it > 0 } ?: localFile.length()
        attachmentDao.upsertAll(
            listOf(
                AttachmentEntity(
                    id = id,
                    noteId = "",
                    libraryFolderId = folderId,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    localPath = localFile.absolutePath,
                    remoteUrl = null,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        id
    }

    suspend fun replaceLibraryPdf(existingAttachmentId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val existing = attachmentDao.getByIdIncludingDeleted(existingAttachmentId)
            ?: error("Original PDF could not be found")
        val resolver = context.contentResolver
        val fileName = resolver.displayName(uri).sanitizeFileName().ifBlank { existing.fileName }
        val mimeType = resolver.getType(uri) ?: existing.mimeType
        check(fileName.isPdfFileName() || mimeType == "application/pdf") { "Only PDFs can be replaced here." }
        val attachmentsDir = File(context.filesDir, "library/${existing.libraryFolderId ?: "root"}").apply { mkdirs() }
        val localFile = File(attachmentsDir, "${existing.id}_$fileName")

        resolver.openInputStream(uri)?.use { input ->
            localFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected file")

        runCatching {
            File(existing.localPath).takeIf { it.absolutePath != localFile.absolutePath && it.exists() }?.delete()
        }
        val sizeBytes = resolver.fileSize(uri).takeIf { it > 0 } ?: localFile.length()
        attachmentDao.upsertAll(
            listOf(
                existing.copy(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    localPath = localFile.absolutePath,
                    deletedAt = null,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        existing.id
    }

    suspend fun exportAttachmentToUri(attachmentId: String, destination: Uri) = withContext(Dispatchers.IO) {
        val attachment = attachmentDao.getByIdIncludingDeleted(attachmentId)
            ?: error("File could not be found in MyVault.")
        check(attachment.deletedAt == null) { "File is no longer available." }
        val source = File(attachment.localPath)
        check(source.exists() && source.isFile) { "Stored file is missing. It may need to be restored from backup." }

        context.contentResolver.openOutputStream(destination)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open the selected save location.")
    }

    suspend fun importLibraryDocuments(folderId: String?, uris: List<Uri>): LibraryImportResult = withContext(Dispatchers.IO) {
        var failedCount = 0
        val importedIds = mutableListOf<String>()
        uris.distinct().forEach { uri ->
            runCatching {
                importLibraryDocument(folderId, uri)
            }.onSuccess { id ->
                importedIds += id
            }.onFailure {
                failedCount += 1
            }
        }
        LibraryImportResult(importedIds = importedIds, failedCount = failedCount)
    }

    suspend fun deleteAttachment(attachmentId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val annotationIds = pdfAnnotationDao.getAll()
                .filter { it.attachmentId == attachmentId }
                .map { it.id }
            attachmentDao.updateDeletedAt(attachmentId, System.currentTimeMillis())
            pdfAnnotationDao.deleteForAttachments(listOf(attachmentId))
            pdfReadingProgressDao.deleteForAttachments(listOf(attachmentId))
            sourceBacklinkDao.deleteForAttachments(listOf(attachmentId))
            knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAttachment, listOf(attachmentId))
            if (annotationIds.isNotEmpty()) {
                knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, annotationIds)
            }
        }
    }

    suspend fun renameAttachment(attachmentId: String, fileName: String) = withContext(Dispatchers.IO) {
        val cleanName = fileName.sanitizeFileName()
        if (cleanName.isNotBlank()) {
            attachmentDao.updateFileName(attachmentId, cleanName)
        }
    }

    suspend fun moveLibraryAttachment(attachmentId: String, folderId: String?) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val oldFolderId = attachmentDao.getByIdIncludingDeleted(attachmentId)?.libraryFolderId
            attachmentDao.updateLibraryFolder(attachmentId, folderId)
            val now = System.currentTimeMillis()
            pdfAnnotationDao.updateSourceFolderForAttachment(attachmentId, folderId, now)
            pdfAnnotationDao.updateDisplayFolderForAttachmentIfMatching(attachmentId, oldFolderId, folderId, now)
        }
    }

    suspend fun setPinned(attachmentId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        attachmentDao.updatePinned(attachmentId, pinned)
    }
}

data class LibraryImportResult(
    val importedIds: List<String>,
    val failedCount: Int,
)

private fun android.content.ContentResolver.displayName(uri: Uri): String =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
    }.orEmpty()

private fun android.content.ContentResolver.fileSize(uri: Uri): Long =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
    } ?: 0L

private fun String.sanitizeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

private fun String.isPdfFileName(): Boolean =
    endsWith(".pdf", ignoreCase = true)
