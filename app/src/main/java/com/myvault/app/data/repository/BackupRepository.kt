package com.myvault.app.data.repository

import android.content.Context
import android.net.Uri
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.AiConversationEntity
import com.myvault.app.data.local.entity.AiMessageEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteFtsEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.local.entity.TagEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val tagDao: TagDao,
    private val attachmentDao: AttachmentDao,
    private val searchDao: SearchDao,
    private val noteTableDao: NoteTableDao,
    private val aiConversationDao: AiConversationDao,
    private val pdfReadingProgressDao: PdfReadingProgressDao,
    private val pdfAnnotationDao: PdfAnnotationDao,
) {
    suspend fun exportBackup(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "vault-manual-export-${System.currentTimeMillis()}.vaultbackup")
        try {
            val result = exportBackupToFile(file)
            context.contentResolver.openOutputStream(destination)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to create backup file")
            result
        } finally {
            file.delete()
        }
    }

    suspend fun exportBackupToFile(destination: File): BackupResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "vault-export-${System.currentTimeMillis()}.vaultbackup")
        try {
            val result = temp.outputStream().use { output -> writeBackup(output) }
            val verification = verifyBackupFile(temp, result)
            check(verification.valid) { verification.message }
            destination.parentFile?.mkdirs()
            temp.copyTo(destination, overwrite = true)
            result
        } finally {
            temp.delete()
        }
    }

    suspend fun restoreBackup(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(source)?.use { input ->
            restoreBackup(input)
        } ?: error("Unable to open backup file")
    }

    suspend fun restoreBackupFromFile(source: File): BackupResult = withContext(Dispatchers.IO) {
        source.inputStream().use { input -> restoreBackup(input) }
    }

    suspend fun createSafetyBackup(reason: String): BackupResult = withContext(Dispatchers.IO) {
        val safeReason = reason.replace(Regex("[^A-Za-z0-9_-]"), "-").ifBlank { "safety" }
        val backupDir = File(context.filesDir, "emergency_backups").apply { mkdirs() }
        val file = File(backupDir, "$safeReason-${System.currentTimeMillis()}.vaultbackup")
        exportBackupToFile(file)
    }

    suspend fun verifyCurrentBackupIntegrity(): BackupVerificationResult = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "vault-backup-verification.vaultbackup")
        runCatching {
            val exported = exportBackupToFile(file)
            verifyBackupFile(file, exported)
        }.also {
            file.delete()
        }.getOrElse { error ->
            BackupVerificationResult(
                valid = false,
                message = "Backup check failed: ${error.message ?: "Unknown error"}",
            )
        }
    }

    private suspend fun writeBackup(output: OutputStream): BackupResult {
        val folders = folderDao.getAllIncludingDeleted()
        val notes = noteDao.getAllIncludingDeleted()
        val backupNoteIds = notes.map { it.id }.toSet()
        val blocks = blockDao.getAll().filter { it.noteId in backupNoteIds }
        val tags = tagDao.getAll()
        val tagRefs = tagDao.getAllRefs().filter { it.noteId in backupNoteIds }
        val tables = noteTableDao.getAll().filter { it.noteId in backupNoteIds }
        val folderIds = folders.map { it.id }.toSet()
        val attachments = attachmentDao.getAllIncludingDeleted().filter {
            it.noteId in backupNoteIds || it.noteId.isBlank() || it.libraryFolderId in folderIds
        }
        val aiConversations = aiConversationDao.getAllConversations().filter { it.noteId in backupNoteIds }
        val aiConversationIds = aiConversations.map { it.id }.toSet()
        val aiMessages = aiConversationDao.getAllMessages().filter { it.conversationId in aiConversationIds && it.noteId in backupNoteIds }
        val activeAttachmentIds = attachments.filter { it.deletedAt == null }.map { it.id }.toSet()
        val pdfReadingProgress = pdfReadingProgressDao.getAll().filter { it.attachmentId in activeAttachmentIds }
        val pdfAnnotations = pdfAnnotationDao.getAll().filter { it.attachmentId in activeAttachmentIds }

        ZipOutputStream(output.buffered()).use { zip ->
            zip.writeJson("manifest.json", manifestJson())
            zip.writeJson("folders.json", folders.toJsonArray { it.toJson() })
            zip.writeJson("notes.json", notes.toJsonArray { it.toJson() })
            zip.writeJson("blocks.json", blocks.toJsonArray { it.toJson() })
            zip.writeJson("tags.json", tags.toJsonArray { it.toJson() })
            zip.writeJson("note_tags.json", tagRefs.toJsonArray { it.toJson() })
            zip.writeJson("note_tables.json", tables.toJsonArray { it.toJson() })
            zip.writeJson("ai_conversations.json", aiConversations.toJsonArray { it.toJson() })
            zip.writeJson("ai_messages.json", aiMessages.toJsonArray { it.toJson() })
            zip.writeJson("attachments.json", attachments.toJsonArray { it.toBackupJson() })
            zip.writeJson("pdf_reading_progress.json", pdfReadingProgress.toJsonArray { it.toJson() })
            zip.writeJson("pdf_annotations.json", pdfAnnotations.toJsonArray { it.toJson() })

            attachments.forEach { attachment ->
                val file = File(attachment.localPath)
                if (file.exists() && file.isFile) {
                    zip.putNextEntry(ZipEntry(attachment.backupFileEntry()))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        return BackupResult(
            folderCount = folders.size,
            noteCount = notes.size,
            attachmentCount = attachments.size,
        )
    }

    private fun verifyBackupFile(file: File, exported: BackupResult): BackupVerificationResult {
        val entries = mutableMapOf<String, String>()
        val fileEntries = mutableSetOf<String>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name.startsWith("files/")) {
                        validateAttachmentEntryName(entry.name.removePrefix("files/"))
                        fileEntries += entry.name
                        zip.readBytes()
                    } else {
                        entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        validateManifest(entries["manifest.json"])
        val notes = entries.requireJsonArray("notes.json")
        val blocks = entries.requireJsonArray("blocks.json")
        val tables = entries.requireJsonArray("note_tables.json")
        val aiConversations = entries.optionalJsonArray("ai_conversations.json")
        val aiMessages = entries.optionalJsonArray("ai_messages.json")
        val attachments = entries.requireJsonArray("attachments.json")
        val pdfReadingProgress = entries.optionalJsonArray("pdf_reading_progress.json")
        val pdfAnnotations = entries.optionalJsonArray("pdf_annotations.json")
        entries.requireJsonArray("folders.json")
        entries.requireJsonArray("tags.json")
        entries.requireJsonArray("note_tags.json")

        check(notes.length() == exported.noteCount) {
            "Backup note count changed while verifying."
        }
        check(attachments.length() == exported.attachmentCount) {
            "Backup attachment count changed while verifying."
        }

        val folderJson = entries.requireJsonArray("folders.json")
        val folderIds = List(folderJson.length()) { index ->
            folderJson.getJSONObject(index).getString("id")
        }.toSet()
        check(folderIds.size == folderJson.length()) { "Backup contains duplicate folder IDs." }
        validateFolderHierarchy(folderJson, folderIds)

        val noteIds = List(notes.length()) { index -> notes.getJSONObject(index).getString("id") }.toSet()
        check(noteIds.size == notes.length()) { "Backup contains duplicate note IDs." }
        List(notes.length()) { index -> notes.getJSONObject(index) }.forEach { note ->
            val folderId = note.optNullableString("folderId")
            check(folderId == null || folderId in folderIds) {
                "Backup contains a note without a matching folder."
            }
        }
        List(blocks.length()) { index -> blocks.getJSONObject(index) }.forEach { block ->
            check(block.getString("noteId") in noteIds) { "Backup contains a block without a matching note." }
            if (block.getString("type") == "rich_text") {
                val content = JSONObject(block.getString("content"))
                val text = content.optString("text")
                val marks = content.optJSONArray("styleMarks") ?: JSONArray()
                List(marks.length()) { index -> marks.getJSONObject(index) }.forEach { mark ->
                    val start = mark.getInt("start")
                    val end = mark.getInt("end")
                    check(start in 0..text.length && end in 0..text.length && start < end) {
                        "Backup contains an invalid rich text style range."
                    }
                    check(mark.getString("style").isNotBlank()) {
                        "Backup contains an invalid rich text style."
                    }
                }
                val links = content.optJSONArray("noteLinks") ?: JSONArray()
                List(links.length()) { index -> links.getJSONObject(index) }.forEach { link ->
                    val start = link.getInt("start")
                    val end = link.getInt("end")
                    check(start in 0..text.length && end in 0..text.length && start < end) {
                        "Backup contains an invalid note link range."
                    }
                    check(link.getString("noteId") in noteIds) {
                        "Backup contains a note link without a matching note."
                    }
                }
            }
        }
        List(tables.length()) { index -> tables.getJSONObject(index) }.forEach { table ->
            check(table.getString("noteId") in noteIds) { "Backup contains a table without a matching note." }
            val rows = table.getInt("rowCount").coerceIn(1, 10)
            val columns = table.getInt("columnCount").coerceIn(1, 10)
            table.getString("cellsJson").toCellsArray(rows, columns)
        }
        val aiConversationIds = List(aiConversations.length()) { index ->
            val conversation = aiConversations.getJSONObject(index)
            check(conversation.getString("noteId") in noteIds) { "Backup contains an AI conversation without a matching note." }
            conversation.getString("id")
        }.toSet()
        List(aiMessages.length()) { index -> aiMessages.getJSONObject(index) }.forEach { message ->
            check(message.getString("noteId") in noteIds) { "Backup contains an AI message without a matching note." }
            check(message.getString("conversationId") in aiConversationIds) { "Backup contains an AI message without a matching conversation." }
            check(message.getString("role").isNotBlank()) { "Backup contains an AI message without a role." }
            check(message.getString("content").isNotBlank()) { "Backup contains an empty AI message." }
        }
        List(attachments.length()) { index -> attachments.getJSONObject(index) }.forEach { attachment ->
            val noteId = attachment.optString("noteId")
            val libraryFolderId = attachment.optNullableString("libraryFolderId")
            check(noteId in noteIds || noteId.isBlank() || (libraryFolderId != null && libraryFolderId in folderIds)) {
                "Backup contains an attachment without a matching note or library folder."
            }
            val entry = attachment.optString("fileEntry")
            if (entry.isNotBlank()) {
                check(entry in fileEntries) { "Backup is missing an attachment file: ${attachment.getString("fileName")}" }
            }
        }
        check(
            List(attachments.length()) { index -> attachments.getJSONObject(index).getString("id") }.toSet().size == attachments.length(),
        ) {
            "Backup contains duplicate attachment IDs."
        }
        val activeAttachmentIds = List(attachments.length()) { index -> attachments.getJSONObject(index) }
            .filter { it.optNullableLong("deletedAt") == null }
            .map { it.getString("id") }
            .toSet()
        List(pdfReadingProgress.length()) { index -> pdfReadingProgress.getJSONObject(index) }.forEach { progress ->
            check(progress.getString("attachmentId") in activeAttachmentIds) {
                "Backup contains PDF progress without a matching attachment."
            }
        }
        List(pdfAnnotations.length()) { index -> pdfAnnotations.getJSONObject(index) }.forEach { annotation ->
            check(annotation.getString("attachmentId") in activeAttachmentIds) {
                "Backup contains a PDF annotation without a matching attachment."
            }
            val sourceFolderId = annotation.optNullableString("libraryFolderId")
            val displayFolderId = annotation.optNullableString("displayFolderId")
            check(sourceFolderId == null || sourceFolderId in folderIds) {
                "Backup contains a PDF annotation source folder without a matching folder."
            }
            check(displayFolderId == null || displayFolderId in folderIds) {
                "Backup contains a PDF annotation display folder without a matching folder."
            }
            val left = annotation.getDouble("left")
            val top = annotation.getDouble("top")
            val right = annotation.getDouble("right")
            val bottom = annotation.getDouble("bottom")
            check(left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0 && left < right && top < bottom) {
                "Backup contains an invalid PDF annotation rectangle."
            }
        }

        return BackupVerificationResult(
            valid = true,
            message = "Backup check passed: ${exported.noteCount} notes, ${exported.attachmentCount} attachments, ${tables.length()} tables.",
        )
    }

    private suspend fun restoreBackup(input: InputStream): BackupResult {
        val entries = mutableMapOf<String, String>()
        val restoredFiles = mutableMapOf<String, File>()

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name.startsWith("files/")) {
                        val attachmentId = entry.name.removePrefix("files/")
                        validateAttachmentEntryName(attachmentId)
                        val file = File(context.cacheDir, "restore-$attachmentId").apply {
                            parentFile?.mkdirs()
                        }
                        file.outputStream().use { zip.copyTo(it) }
                        restoredFiles[attachmentId] = file
                    } else {
                        entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        validateManifest(entries["manifest.json"])

        val folders = entries.requireJsonArray("folders.json").mapJson { it.toFolderEntity() }
        val notes = entries.requireJsonArray("notes.json").mapJson { it.toNoteEntity() }
        val restoredNoteIds = notes.map { it.id }.toSet()
        val blocks = entries.requireJsonArray("blocks.json")
            .mapJson { it.toBlockEntity() }
            .filter { it.noteId in restoredNoteIds }
        val tags = entries.requireJsonArray("tags.json").mapJson { it.toTagEntity() }
        val tagRefs = entries.requireJsonArray("note_tags.json")
            .mapJson { it.toNoteTagCrossRef() }
            .filter { it.noteId in restoredNoteIds }
        val tables = entries.requireJsonArray("note_tables.json")
            .mapJson { it.toNoteTableEntity() }
            .filter { it.noteId in restoredNoteIds }
        val aiConversations = entries.optionalJsonArray("ai_conversations.json")
            .mapJson { it.toAiConversationEntity() }
            .filter { it.noteId in restoredNoteIds }
        val aiConversationIds = aiConversations.map { it.id }.toSet()
        val aiMessages = entries.optionalJsonArray("ai_messages.json")
            .mapJson { it.toAiMessageEntity() }
            .filter { it.noteId in restoredNoteIds && it.conversationId in aiConversationIds }
        val attachmentsJson = entries.requireJsonArray("attachments.json")
        attachmentsJson.validateAttachmentFiles(restoredFiles.keys)
        val attachments = attachmentsJson
            .mapJson { json -> json.toAttachmentEntity(restoredFiles[json.getString("id")]) }
            .filter {
                val restoredFolderIds = folders.map { folder -> folder.id }.toSet()
                it.noteId in restoredNoteIds || it.noteId.isBlank() || it.libraryFolderId in restoredFolderIds
            }
        val restoredAttachmentIds = attachments.map { it.id }.toSet()
        val activeRestoredAttachmentIds = attachments.filter { it.deletedAt == null }.map { it.id }.toSet()
        val pdfReadingProgress = entries.optionalJsonArray("pdf_reading_progress.json")
            .mapJson { it.toPdfReadingProgressEntity() }
            .filter { it.attachmentId in activeRestoredAttachmentIds }
        val restoredLibraryFolderIds = folders.map { it.id }.toSet()
        val pdfAnnotations = entries.optionalJsonArray("pdf_annotations.json")
            .mapJson { it.toPdfAnnotationEntity() }
            .filter {
                it.attachmentId in activeRestoredAttachmentIds &&
                    (it.libraryFolderId == null || it.libraryFolderId in restoredLibraryFolderIds) &&
                    (it.displayFolderId == null || it.displayFolderId in restoredLibraryFolderIds)
            }

        createEmergencyBackupBeforeRestore()

        if (folders.isNotEmpty()) folderDao.upsertAll(folders)
        if (notes.isNotEmpty()) {
            noteDao.upsertAll(notes)
            searchDao.upsertFts(notes.map { NoteFtsEntity(title = it.title, bodyPlainText = it.bodyPlainText) })
        }
        if (blocks.isNotEmpty()) blockDao.upsertAll(blocks)
        if (tags.isNotEmpty()) tagDao.upsertAll(tags)
        if (tagRefs.isNotEmpty()) tagDao.upsertRefs(tagRefs)
        if (tables.isNotEmpty()) noteTableDao.upsertAll(tables)
        if (aiConversations.isNotEmpty()) aiConversationDao.upsertConversations(aiConversations)
        if (aiMessages.isNotEmpty()) aiConversationDao.upsertMessages(aiMessages)
        if (attachments.isNotEmpty()) attachmentDao.upsertAll(attachments)
        if (restoredAttachmentIds.isNotEmpty()) {
            pdfReadingProgressDao.deleteForAttachments(restoredAttachmentIds.toList())
            pdfAnnotationDao.deleteForAttachments(restoredAttachmentIds.toList())
        }
        if (pdfReadingProgress.isNotEmpty()) pdfReadingProgressDao.upsertAll(pdfReadingProgress)
        if (pdfAnnotations.isNotEmpty()) pdfAnnotationDao.upsertAll(pdfAnnotations)

        restoredFiles.values.forEach { it.delete() }

        return BackupResult(
            folderCount = folders.size,
            noteCount = notes.size,
            attachmentCount = attachments.size,
        )
    }

    private fun JSONObject.toAttachmentEntity(restoredFile: File?): AttachmentEntity {
        val id = getString("id")
        val noteId = optString("noteId")
        val libraryFolderId = optNullableString("libraryFolderId")
        validateBackupPathSegment(id)
        if (noteId.isNotBlank()) validateBackupPathSegment(noteId)
        if (!libraryFolderId.isNullOrBlank()) validateBackupPathSegment(libraryFolderId)
        val fileName = getString("fileName").sanitizeBackupFileName().ifBlank { "attachment-$id" }
        val storageRoot = if (!libraryFolderId.isNullOrBlank()) "library/$libraryFolderId" else "attachments/$noteId"
        val targetFile = File(context.filesDir, "$storageRoot/${id}_$fileName").apply {
            parentFile?.mkdirs()
            if (restoredFile != null) {
                restoredFile.copyTo(this, overwrite = true)
            }
        }
        return AttachmentEntity(
            id = id,
            noteId = noteId,
            libraryFolderId = libraryFolderId,
            fileName = fileName,
            mimeType = getString("mimeType"),
            sizeBytes = getLong("sizeBytes"),
            localPath = targetFile.absolutePath,
            remoteUrl = optNullableString("remoteUrl"),
            isPinned = optBoolean("isPinned", false),
            createdAt = getLong("createdAt"),
            deletedAt = optNullableLong("deletedAt"),
        )
    }

    private suspend fun createEmergencyBackupBeforeRestore() {
        runCatching {
            createSafetyBackup("before-restore")
        }.getOrElse { error ->
            throw IllegalStateException(
                "Restore was stopped because My Vault could not create an emergency backup first: ${error.message ?: "Unknown error"}",
            )
        }
    }
}

private fun validateManifest(manifestText: String?) {
    val manifest = manifestText?.let { JSONObject(it) }
        ?: error("Selected file is not a Vault backup")
    val format = manifest.optString("format")
    val version = manifest.optInt("version", -1)
    if (format != "myvault-backup") {
        error("Selected file is not a Vault backup")
    }
    if (version != 1) {
        error("This backup version is not supported")
    }
}

private fun validateAttachmentEntryName(attachmentId: String) {
    if (attachmentId.isBlank() || attachmentId.contains("/") || attachmentId.contains("..")) {
        error("Backup contains an invalid attachment file")
    }
}

private fun validateFolderHierarchy(folders: JSONArray, folderIds: Set<String>) {
    val parentById = List(folders.length()) { index -> folders.getJSONObject(index) }
        .associate { folder ->
            val id = folder.getString("id")
            val parentId = folder.optNullableString("parentId")
            check(id.isNotBlank()) { "Backup contains a folder without an ID." }
            check(parentId == null || parentId in folderIds) {
                "Backup contains a folder without a matching parent."
            }
            check(parentId != id) { "Backup contains a folder that points to itself." }
            id to parentId
        }

    parentById.keys.forEach { folderId ->
        val seen = mutableSetOf<String>()
        var current: String? = folderId
        while (current != null) {
            check(seen.add(current)) { "Backup contains a circular folder hierarchy." }
            current = parentById[current]
        }
    }
}

private fun validateBackupPathSegment(value: String) {
    if (value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) {
        error("Backup contains an invalid file path")
    }
}

data class BackupResult(
    val folderCount: Int,
    val noteCount: Int,
    val attachmentCount: Int,
)

data class BackupVerificationResult(
    val valid: Boolean,
    val message: String,
)

private fun manifestJson(): JSONObject =
    JSONObject()
        .put("format", "myvault-backup")
        .put("version", 1)
        .put("createdAt", System.currentTimeMillis())

private fun ZipOutputStream.writeJson(name: String, json: Any) {
    putNextEntry(ZipEntry(name))
    write(json.toString().toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun AttachmentEntity.backupFileEntry(): String = "files/$id"

private fun AttachmentEntity.toBackupJson(): JSONObject =
    toJson().also { json ->
        if (deletedAt == null) {
            json.put("fileEntry", backupFileEntry())
        }
    }

private fun FolderEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("parentId", parentId)
        .put("name", name)
        .put("orderIndex", orderIndex)
        .put("isFavourite", isFavourite)
        .put("mode", mode)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)

private fun NoteEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("folderId", folderId)
        .put("title", title)
        .put("bodyPlainText", bodyPlainText)
        .put("isPinned", isPinned)
        .put("isFavourite", isFavourite)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)

private fun BlockEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("type", type)
        .put("content", content)
        .put("orderIndex", orderIndex)

private fun NoteTableEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("rowCount", rowCount)
        .put("columnCount", columnCount)
        .put("cellsJson", cellsJson)
        .put("orderIndex", orderIndex)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun AiConversationEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("title", title)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun AiMessageEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("conversationId", conversationId)
        .put("noteId", noteId)
        .put("role", role)
        .put("content", content)
        .put("action", action)
        .put("provider", provider)
        .put("model", model)
        .put("selectedTextContext", selectedTextContext)
        .put("createdAt", createdAt)

private fun PdfReadingProgressEntity.toJson(): JSONObject =
    JSONObject()
        .put("attachmentId", attachmentId)
        .put("pageIndex", pageIndex)
        .put("pageCount", pageCount)
        .put("progressPercent", progressPercent)
        .put("lastOpenedAt", lastOpenedAt)
        .put("updatedAt", updatedAt)

private fun PdfAnnotationEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("attachmentId", attachmentId)
        .put("libraryFolderId", libraryFolderId)
        .put("pageIndex", pageIndex)
        .put("left", left)
        .put("top", top)
        .put("right", right)
        .put("bottom", bottom)
        .put("color", color)
        .put("noteText", noteText)
        .put("displayTitle", displayTitle)
        .put("displayFolderId", displayFolderId)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun TagEntity.toJson(): JSONObject =
    JSONObject().put("name", name)

private fun NoteTagCrossRef.toJson(): JSONObject =
    JSONObject()
        .put("noteId", noteId)
        .put("tagName", tagName)

private fun AttachmentEntity.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("libraryFolderId", libraryFolderId)
        .put("fileName", fileName)
        .put("mimeType", mimeType)
        .put("sizeBytes", sizeBytes)
        .put("localPath", localPath)
        .put("remoteUrl", remoteUrl)
        .put("isPinned", isPinned)
        .put("createdAt", createdAt)
        .put("deletedAt", deletedAt)

private fun JSONObject.toFolderEntity(): FolderEntity =
    FolderEntity(
        id = getString("id"),
        parentId = optNullableString("parentId"),
        name = getString("name"),
        orderIndex = getInt("orderIndex"),
        isFavourite = getBoolean("isFavourite"),
        mode = optString("mode").ifBlank { "study" },
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
        deletedAt = optNullableLong("deletedAt"),
    )

private fun JSONObject.toNoteEntity(): NoteEntity =
    NoteEntity(
        id = getString("id"),
        folderId = optNullableString("folderId"),
        title = getString("title"),
        bodyPlainText = getString("bodyPlainText"),
        isPinned = getBoolean("isPinned"),
        isFavourite = getBoolean("isFavourite"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
        deletedAt = optNullableLong("deletedAt"),
    )

private fun JSONObject.toPdfReadingProgressEntity(): PdfReadingProgressEntity =
    PdfReadingProgressEntity(
        attachmentId = getString("attachmentId"),
        pageIndex = getInt("pageIndex"),
        pageCount = getInt("pageCount"),
        progressPercent = optDouble("progressPercent", 0.0).toFloat(),
        lastOpenedAt = getLong("lastOpenedAt"),
        updatedAt = getLong("updatedAt"),
    )

private fun JSONObject.toPdfAnnotationEntity(): PdfAnnotationEntity =
    PdfAnnotationEntity(
        id = getString("id"),
        attachmentId = getString("attachmentId"),
        libraryFolderId = optNullableString("libraryFolderId"),
        pageIndex = getInt("pageIndex").coerceAtLeast(0),
        left = optDouble("left", 0.0).toFloat().coerceIn(0f, 1f),
        top = optDouble("top", 0.0).toFloat().coerceIn(0f, 1f),
        right = optDouble("right", 0.0).toFloat().coerceIn(0f, 1f),
        bottom = optDouble("bottom", 0.0).toFloat().coerceIn(0f, 1f),
        color = optString("color").ifBlank { "yellow" },
        noteText = optNullableString("noteText"),
        displayTitle = optNullableString("displayTitle"),
        displayFolderId = optNullableString("displayFolderId") ?: optNullableString("libraryFolderId"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
    )

private fun JSONObject.toBlockEntity(): BlockEntity =
    BlockEntity(
        id = getString("id"),
        noteId = getString("noteId"),
        type = getString("type"),
        content = getString("content"),
        orderIndex = getInt("orderIndex"),
    )

private fun JSONObject.toNoteTableEntity(): NoteTableEntity =
    NoteTableEntity(
        id = getString("id"),
        noteId = getString("noteId"),
        rowCount = getInt("rowCount").coerceIn(1, 10),
        columnCount = getInt("columnCount").coerceIn(1, 10),
        cellsJson = getString("cellsJson")
            .toCellsArray(getInt("rowCount").coerceIn(1, 10), getInt("columnCount").coerceIn(1, 10))
            .toString(),
        orderIndex = getInt("orderIndex"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
    )

private fun JSONObject.toAiConversationEntity(): AiConversationEntity =
    AiConversationEntity(
        id = getString("id"),
        noteId = getString("noteId"),
        title = optString("title").ifBlank { "Ask AI" },
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
    )

private fun JSONObject.toAiMessageEntity(): AiMessageEntity =
    AiMessageEntity(
        id = getString("id"),
        conversationId = getString("conversationId"),
        noteId = getString("noteId"),
        role = getString("role"),
        content = getString("content"),
        action = optNullableString("action"),
        provider = optNullableString("provider"),
        model = optNullableString("model"),
        selectedTextContext = optNullableString("selectedTextContext"),
        createdAt = getLong("createdAt"),
    )

private fun JSONObject.toTagEntity(): TagEntity =
    TagEntity(name = getString("name"))

private fun JSONObject.toNoteTagCrossRef(): NoteTagCrossRef =
    NoteTagCrossRef(
        noteId = getString("noteId"),
        tagName = getString("tagName"),
    )

private fun String.toJsonArray(): JSONArray =
    if (isBlank()) JSONArray() else JSONArray(this)

private fun Map<String, String>.requireJsonArray(name: String): JSONArray =
    get(name)?.toJsonArray() ?: error("Backup is missing $name")

private fun Map<String, String>.optionalJsonArray(name: String): JSONArray =
    get(name)?.toJsonArray() ?: JSONArray()

private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(transform(it)) } }

private fun <T> JSONArray.mapJson(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONArray.validateAttachmentFiles(restoredFileIds: Set<String>) {
    mapJson { it }.forEach { attachment ->
        val entry = attachment.optString("fileEntry")
        if (entry.isNotBlank()) {
            val id = attachment.getString("id")
            validateAttachmentEntryName(id)
            check(id in restoredFileIds) {
                "Backup is missing an attachment file: ${attachment.getString("fileName")}"
            }
        }
    }
}

private fun String.sanitizeBackupFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

private fun String.toCellsArray(rows: Int, columns: Int): JSONArray {
    val parsed = JSONArray(this)
    return JSONArray().also { outer ->
        repeat(rows) { row ->
            val parsedRow = parsed.optJSONArray(row)
            outer.put(JSONArray().also { inner ->
                repeat(columns) { column ->
                    inner.put(parsedRow?.optString(column).orEmpty())
                }
            })
        }
    }
}
