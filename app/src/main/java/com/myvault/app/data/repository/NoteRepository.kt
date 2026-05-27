package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.NoteVersionDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteVersionEntity
import com.myvault.app.ui.components.EditorBlockType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class NoteRepository @Inject constructor(
    private val database: VaultDatabase,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val blockDao: BlockDao,
    private val attachmentDao: AttachmentDao,
    private val tagDao: TagDao,
    private val noteTableDao: NoteTableDao,
    private val noteVersionDao: NoteVersionDao,
    private val pdfAnnotationDao: PdfAnnotationDao,
    private val pdfReadingProgressDao: PdfReadingProgressDao,
    private val sourceBacklinkDao: SourceBacklinkDao,
    private val knowledgeTagDao: KnowledgeTagDao,
) {
    private val bodyBlockTypes = listOf(
        "rich_text",
        "rich_html",
        "rich_body",
        "paragraph",
        "heading",
        "quote",
        "checklist",
        "checklist_checked",
        "bullet",
        "numbered",
        "link",
        "divider",
    )

    fun observeRawBlocks(noteId: String) = blockDao.observeForNote(noteId)

    fun observeAllNotes() = noteDao.observeAll()

    fun observeBacklinks(noteId: String): Flow<List<NoteLinkRef>> =
        combine(
            noteDao.observeAll()
                .map { notes ->
                    notes
                        .filter { it.id != noteId }
                        .map { NoteBacklinkMeta(it.id, it.title, it.bodyPlainText.previewLine()) }
                }
                .distinctUntilChanged(),
            blockDao.observeAll()
                .debounce(1_500)
                .onStart { emit(emptyList()) }
                .distinctUntilChanged(),
        ) { noteMetadata, blocks ->
            val notesById = noteMetadata.associateBy { it.id }
            blocks
                .filter { it.type == "rich_text" && it.noteId != noteId }
                .filter { block -> block.content.containsNoteLink(noteId) }
                .mapNotNull { block ->
                    notesById[block.noteId]?.let { noteMeta ->
                        NoteLinkRef(id = noteMeta.id, title = noteMeta.title, preview = noteMeta.preview)
                    }
                }
                .distinctBy { it.id }
        }

    fun observeTables(noteId: String) = noteTableDao.observeForNote(noteId)

    fun observeVersions(noteId: String) = noteVersionDao.observeForNote(noteId)

    fun observePinnedCards() = combine(noteDao.observePinned(), folderDao.observeAll(), noteTableDao.observeAll()) { notes, folders, tables ->
        val folderNames = folders.associate { it.id to it.name }
        val tableCounts = tables.groupingBy { it.noteId }.eachCount()
        notes.map { it.toNoteCard(folderNames[it.folderId], tableCounts[it.id] ?: 0) }
    }

    fun observeRecentCards(limit: Int = 8) = noteDao.observeRecent(limit).map { notes ->
        notes.map { it.toRecentCard() }
    }

    fun observeDeletedNotes() = noteDao.observeDeleted()

    fun observeForFolder(folderId: String) = noteDao.observeForFolder(folderId)

    fun observeNote(noteId: String) = noteDao.observeById(noteId)

    fun observeFolderPath(noteId: String) = combine(noteDao.observeById(noteId), folderDao.observeAll()) { note, folders ->
        val folderById = folders.associateBy { it.id }
        val path = mutableListOf<String>()
        var currentId = note?.folderId
        while (currentId != null) {
            val folder = folderById[currentId] ?: break
            path.add(folder.name)
            currentId = folder.parentId
        }
        path.asReversed()
    }

    fun observeBlocks(noteId: String) = blockDao.observeForNote(noteId).map { blocks ->
        blocks.map { it.toEditorBlock() }
    }

    fun observeTags(noteId: String) = tagDao.observeForNote(noteId).map { tags ->
        tags.map { it.name }
    }

    suspend fun createNote(folderId: String?, title: String = "Untitled note"): String {
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()
        noteDao.upsertAll(
            listOf(
                NoteEntity(
                    id = noteId,
                    folderId = folderId,
                    title = title,
                    bodyPlainText = "",
                    isPinned = false,
                    isFavourite = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        saveRichText(noteId = noteId, text = "", styleMarksJson = "[]")
        return noteId
    }

    suspend fun createImportedRichTextNote(title: String, text: String, styleMarksJson: String): String {
        val folderId = importFolderId()
        val noteId = createNote(folderId = folderId, title = title.ifBlank { text.firstTitleLine() })
        saveRichText(noteId = noteId, text = text, styleMarksJson = styleMarksJson)
        return noteId
    }

    suspend fun updateTitle(noteId: String, title: String) {
        noteDao.updateTitle(noteId, title.ifBlank { "Untitled note" }, System.currentTimeMillis())
    }

    private suspend fun importFolderId(): String {
        val folders = folderDao.getAll()
        folders.firstOrNull { it.name.equals("Inbox", ignoreCase = true) || it.name.equals("Unsorted", ignoreCase = true) }?.let {
            return it.id
        }
        val now = System.currentTimeMillis()
        val folderId = UUID.randomUUID().toString()
        folderDao.upsertAll(
            listOf(
                FolderEntity(
                    id = folderId,
                    parentId = null,
                    name = "Inbox",
                    orderIndex = folders.filter { it.parentId == null }.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0,
                    isFavourite = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        return folderId
    }

    suspend fun moveNote(noteId: String, folderId: String?) {
        noteDao.updateFolder(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun moveNoteToMode(noteId: String, mode: String) {
        database.withTransaction {
            val targetFolderId = when (mode) {
                FOLDER_MODE_PERSONAL -> personalInboxFolderId()
                FOLDER_MODE_STUDY -> null
                else -> return@withTransaction
            }
            noteDao.updateFolder(noteId, targetFolderId, System.currentTimeMillis())
        }
    }

    private suspend fun personalInboxFolderId(): String {
        val folders = folderDao.getAll()
        folders.firstOrNull {
            it.parentId == null &&
                it.mode == FOLDER_MODE_PERSONAL &&
                it.name.equals("Inbox", ignoreCase = true)
        }?.let { return it.id }

        val now = System.currentTimeMillis()
        val folderId = UUID.randomUUID().toString()
        folderDao.upsertAll(
            listOf(
                FolderEntity(
                    id = folderId,
                    parentId = null,
                    name = "Inbox",
                    orderIndex = folders
                        .filter { it.parentId == null && it.mode == FOLDER_MODE_PERSONAL }
                        .maxOfOrNull { it.orderIndex }
                        ?.plus(1) ?: 0,
                    isFavourite = false,
                    mode = FOLDER_MODE_PERSONAL,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        return folderId
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) {
        noteDao.updatePinned(noteId, pinned, System.currentTimeMillis())
    }

    suspend fun setFavourite(noteId: String, favourite: Boolean) {
        noteDao.updateFavourite(noteId, favourite, System.currentTimeMillis())
    }

    suspend fun deleteNote(noteId: String) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            noteDao.updateDeletedAt(listOf(noteId), now, now)
            attachmentDao.updateDeletedAtForNotes(listOf(noteId), now)
        }
    }

    suspend fun restoreNote(noteId: String) {
        database.withTransaction {
            noteDao.updateDeletedAt(listOf(noteId), null, System.currentTimeMillis())
            attachmentDao.updateDeletedAtForNotes(listOf(noteId), null)
        }
    }

    suspend fun permanentlyDeleteNote(noteId: String) {
        val attachments = attachmentDao.getForNotes(listOf(noteId))
        val attachmentIds = attachments.map { it.id }
        val annotationIds = if (attachmentIds.isEmpty()) {
            emptyList()
        } else {
            pdfAnnotationDao.getAll()
                .filter { it.attachmentId in attachmentIds }
                .map { it.id }
        }
        database.withTransaction {
            if (attachmentIds.isNotEmpty()) {
                pdfAnnotationDao.deleteForAttachments(attachmentIds)
                pdfReadingProgressDao.deleteForAttachments(attachmentIds)
                sourceBacklinkDao.deleteForAttachments(attachmentIds)
                knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAttachment, attachmentIds)
            }
            if (annotationIds.isNotEmpty()) {
                knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, annotationIds)
            }
            blockDao.deleteForNotes(listOf(noteId))
            tagDao.deleteRefsForNotes(listOf(noteId))
            sourceBacklinkDao.deleteForNotes(listOf(noteId))
            knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetNote, listOf(noteId))
            noteTableDao.deleteForNotes(listOf(noteId))
            noteVersionDao.deleteForNotes(listOf(noteId))
            attachmentDao.deleteForNotes(listOf(noteId))
            noteDao.deleteByIds(listOf(noteId))
        }
        attachments.deleteLocalFiles()
    }

    suspend fun createTable(noteId: String, rows: Int, columns: Int) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val safeRows = rows.coerceIn(1, 10)
            val safeColumns = columns.coerceIn(1, 10)
            val orderIndex = (noteTableDao.getForNote(noteId).maxOfOrNull { it.orderIndex } ?: -1) + 1
            noteTableDao.upsertAll(
                listOf(
                    NoteTableEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = noteId,
                        rowCount = safeRows,
                        columnCount = safeColumns,
                        cellsJson = emptyCellsJson(safeRows, safeColumns),
                        orderIndex = orderIndex,
                        createdAt = now,
                        updatedAt = now,
                    ),
                ),
            )
            noteDao.updateBodyPlainText(noteId, currentBodyPlainText(noteId), now)
        }
    }

    suspend fun updateTableCell(noteId: String, tableId: String, row: Int, column: Int, text: String) {
        database.withTransaction {
            val table = noteTableDao.getForNote(noteId).firstOrNull { it.id == tableId } ?: return@withTransaction
            val now = System.currentTimeMillis()
            noteTableDao.updateCells(
                id = tableId,
                cellsJson = table.cellsJson.updateCell(table.rowCount, table.columnCount, row, column, text),
                updatedAt = now,
            )
            noteDao.updateBodyPlainText(noteId, currentBodyPlainText(noteId), now)
        }
    }

    suspend fun deleteTable(noteId: String, tableId: String) {
        database.withTransaction {
            noteTableDao.deleteById(tableId)
            noteDao.updateBodyPlainText(noteId, currentBodyPlainText(noteId), System.currentTimeMillis())
        }
    }

    suspend fun updateBody(noteId: String, body: String) {
        database.withTransaction {
            noteDao.updateBodyPlainText(noteId, body, System.currentTimeMillis())
            val blocks = blockDao.getForNote(noteId)
            val mainTextBlock = blocks.firstOrNull { it.type in listOf("paragraph", "heading", "quote", "checklist", "bullet", "numbered", "link") }
            if (mainTextBlock != null) {
                blockDao.updateContent(mainTextBlock.id, body)
            } else {
                blockDao.upsertAll(
                    listOf(
                        BlockEntity(
                            id = UUID.randomUUID().toString(),
                            noteId = noteId,
                            type = "paragraph",
                            content = body,
                            orderIndex = 0,
                        ),
                    ),
                )
            }
        }
    }

    suspend fun saveRichHtml(noteId: String, html: String, plainText: String) {
        database.withTransaction {
            captureVersionIfNeeded(noteId)
            val blockId = "$noteId-rich-html"
            blockDao.upsertAll(
                listOf(
                    BlockEntity(
                        id = blockId,
                        noteId = noteId,
                        type = "rich_html",
                        content = html,
                        orderIndex = 0,
                    ),
                ),
            )
            blockDao.deleteTypesForNoteExcept(noteId, bodyBlockTypes, blockId)
            noteDao.updateBodyPlainText(noteId, currentBodyPlainText(noteId, plainText), System.currentTimeMillis())
        }
    }

    suspend fun saveRichText(noteId: String, text: String, styleMarksJson: String, noteLinksJson: String = "[]") {
        database.withTransaction {
            captureVersionIfNeeded(noteId)
            val safeStyleMarks = sanitizeStyleMarksJson(styleMarksJson, text.length)
            val safeNoteLinks = sanitizeNoteLinksJson(noteLinksJson, text.length)
            val blockId = "$noteId-rich-text"
            blockDao.upsertAll(
                listOf(
                    BlockEntity(
                        id = blockId,
                        noteId = noteId,
                        type = "rich_text",
                        content = JSONObject()
                            .put("text", text)
                            .put("styleMarks", safeStyleMarks)
                            .put("noteLinks", safeNoteLinks)
                            .toString(),
                        orderIndex = 0,
                    ),
                ),
            )
            blockDao.deleteTypesForNoteExcept(noteId, bodyBlockTypes, blockId)
            noteDao.updateBodyPlainText(noteId, currentBodyPlainText(noteId, text), System.currentTimeMillis())
        }
    }

    suspend fun restoreVersion(noteId: String, versionId: String) {
        database.withTransaction {
            val version = noteVersionDao.getById(versionId) ?: return@withTransaction
            captureVersionIfNeeded(noteId)
            val now = System.currentTimeMillis()
            when {
                !version.richHtml.isNullOrBlank() -> {
                    val blockId = "$noteId-rich-html"
                    blockDao.upsertAll(
                        listOf(
                            BlockEntity(
                                id = blockId,
                                noteId = noteId,
                                type = "rich_html",
                                content = version.richHtml,
                                orderIndex = 0,
                            ),
                        ),
                    )
                    blockDao.deleteTypesForNoteExcept(noteId, bodyBlockTypes, blockId)
                }
                !version.richTextJson.isNullOrBlank() -> {
                    val blockId = "$noteId-rich-text"
                    blockDao.upsertAll(
                        listOf(
                            BlockEntity(
                                id = blockId,
                                noteId = noteId,
                                type = "rich_text",
                                content = version.richTextJson,
                                orderIndex = 0,
                            ),
                        ),
                    )
                    blockDao.deleteTypesForNoteExcept(noteId, bodyBlockTypes, blockId)
                }
                else -> {
                    val blockId = "$noteId-rich-text"
                    blockDao.upsertAll(
                        listOf(
                            BlockEntity(
                                id = blockId,
                                noteId = noteId,
                                type = "rich_text",
                                content = JSONObject()
                                    .put("text", version.bodyPlainText)
                                    .put("styleMarks", JSONArray())
                                    .put("noteLinks", JSONArray())
                                    .toString(),
                                orderIndex = 0,
                            ),
                        ),
                    )
                    blockDao.deleteTypesForNoteExcept(noteId, bodyBlockTypes, blockId)
                }
            }
            noteDao.updateTitle(noteId, version.title, now)
            noteDao.updateBodyPlainText(noteId, version.bodyPlainText, now)
        }
    }

    suspend fun updateBlockContent(noteId: String, blockId: String, content: String) {
        database.withTransaction {
            captureVersionIfNeeded(noteId)
            blockDao.updateContent(blockId, content)
            updateBodyPlainText(noteId)
        }
    }

    suspend fun insertBlock(noteId: String, type: EditorBlockType) {
        database.withTransaction {
            captureVersionIfNeeded(noteId)
            val blocks = blockDao.getForNote(noteId)
            val orderIndex = (blocks.maxOfOrNull { it.orderIndex } ?: -1) + 1
            blockDao.upsertAll(
                listOf(
                    BlockEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = noteId,
                        type = type.toStorageType(),
                        content = type.defaultContent(),
                        orderIndex = orderIndex,
                    ),
                ),
            )
            updateBodyPlainText(noteId)
        }
    }

    private suspend fun updateBodyPlainText(noteId: String) {
        val body = currentBodyPlainText(noteId)
        noteDao.updateBodyPlainText(noteId, body, System.currentTimeMillis())
    }

    private suspend fun currentBodyPlainText(noteId: String, overrideBlockText: String? = null): String {
        val blockText = (overrideBlockText ?: blockDao.getForNote(noteId)
            .filterNot { it.type == "divider" }
            .joinToString(separator = "\n") { block ->
                when (block.type) {
                    "rich_text" -> block.content.toVaultRichTextText()
                    "rich_html" -> block.content.stripHtml()
                    else -> block.content.substringBefore("|")
                }
            }).trim()
        val tableText = noteTableDao.getForNote(noteId)
            .joinToString(separator = "\n") { it.cellsJson.tableText() }
            .trim()
        return listOf(blockText, tableText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
    }

    private suspend fun captureVersionIfNeeded(noteId: String) {
        val note = noteDao.getAllIncludingDeleted().firstOrNull { it.id == noteId && it.deletedAt == null } ?: return
        val blocks = blockDao.getForNote(noteId)
        val richTextBlock = blocks.firstOrNull { it.type == "rich_text" }
        val richHtmlBlock = blocks.firstOrNull { it.type == "rich_html" }
        val plainText = currentBodyPlainText(noteId)
        if (plainText.isBlank() && richHtmlBlock?.content.isNullOrBlank()) return

        val latest = noteVersionDao.latestForNote(noteId)
        if (latest?.bodyPlainText == plainText && latest.title == note.title) return
        if (latest != null && System.currentTimeMillis() - latest.createdAt < VersionSnapshotIntervalMs) return

        noteVersionDao.upsertAll(
            listOf(
                NoteVersionEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    title = note.title,
                    bodyPlainText = plainText,
                    richTextJson = richTextBlock?.content,
                    richHtml = richHtmlBlock?.content,
                    wordCount = plainText.wordCount(),
                    characterCount = plainText.length,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        noteVersionDao.pruneForNote(noteId, MaxVersionsPerNote)
    }

    private fun EditorBlockType.toStorageType(): String = when (this) {
        EditorBlockType.Heading -> "heading"
        EditorBlockType.Quote -> "quote"
        EditorBlockType.Checklist -> "checklist"
        EditorBlockType.Divider -> "divider"
        EditorBlockType.Attachment -> "attachment"
        EditorBlockType.Image -> "image"
        EditorBlockType.BulletList -> "bullet"
        EditorBlockType.NumberedList -> "numbered"
        EditorBlockType.Link -> "link"
        EditorBlockType.Paragraph -> "paragraph"
    }

    private fun EditorBlockType.defaultContent(): String = when (this) {
        EditorBlockType.Heading -> "New heading"
        EditorBlockType.Quote -> "New quote"
        EditorBlockType.Checklist -> "New checklist item"
        EditorBlockType.Divider -> ""
        EditorBlockType.Attachment -> "new-file.pdf|0 KB · PDF"
        EditorBlockType.Image -> "Image block"
        EditorBlockType.BulletList -> "New bullet item"
        EditorBlockType.NumberedList -> "New numbered item"
        EditorBlockType.Link -> "https://"
        EditorBlockType.Paragraph -> "Start writing..."
    }
}

private const val VersionSnapshotIntervalMs = 5 * 60 * 1000L
private const val MaxVersionsPerNote = 30

private fun String.wordCount(): Int =
    trim()
        .takeIf { it.isNotBlank() }
        ?.split(Regex("\\s+"))
        ?.count { it.isNotBlank() }
        ?: 0

data class NoteLinkRef(
    val id: String,
    val title: String,
    val preview: String,
)

private data class NoteBacklinkMeta(
    val id: String,
    val title: String,
    val preview: String,
)

private fun String.toVaultRichTextText(): String =
    runCatching { JSONObject(this).optString("text") }.getOrDefault("")

private fun sanitizeStyleMarksJson(styleMarksJson: String, textLength: Int): JSONArray {
    val safeLength = textLength.coerceAtLeast(0)
    val source = runCatching { JSONArray(styleMarksJson) }.getOrDefault(JSONArray())
    return JSONArray().also { output ->
        repeat(source.length()) { index ->
            val mark = source.optJSONObject(index) ?: return@repeat
            val style = mark.optString("style").takeIf { it.isNotBlank() } ?: return@repeat
            val start = mark.optInt("start").coerceIn(0, safeLength)
            val end = mark.optInt("end").coerceIn(0, safeLength)
            if (start < end) {
                output.put(
                    JSONObject()
                        .put("start", start)
                        .put("end", end)
                        .put("style", style),
                )
            }
        }
    }
}

private fun sanitizeNoteLinksJson(noteLinksJson: String, textLength: Int): JSONArray {
    val safeLength = textLength.coerceAtLeast(0)
    val source = runCatching { JSONArray(noteLinksJson) }.getOrDefault(JSONArray())
    return JSONArray().also { output ->
        repeat(source.length()) { index ->
            val link = source.optJSONObject(index) ?: return@repeat
            val noteId = link.optString("noteId").takeIf { it.isNotBlank() } ?: return@repeat
            val start = link.optInt("start").coerceIn(0, safeLength)
            val end = link.optInt("end").coerceIn(0, safeLength)
            if (start < end) {
                output.put(
                    JSONObject()
                        .put("start", start)
                        .put("end", end)
                        .put("noteId", noteId),
                )
            }
        }
    }
}

private fun String.containsNoteLink(noteId: String): Boolean =
    runCatching {
        val links = JSONObject(this).optJSONArray("noteLinks") ?: JSONArray()
        List(links.length()) { index -> links.optJSONObject(index) }
            .any { it?.optString("noteId") == noteId }
    }.getOrDefault(false)

private fun String.previewLine(): String =
    lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

private fun String.firstTitleLine(): String =
    lines().firstOrNull { it.isNotBlank() }?.trim()?.take(80).orEmpty().ifBlank { "Imported note" }

private fun emptyCellsJson(rows: Int, columns: Int): String =
    JSONArray().also { outer ->
        repeat(rows) {
            outer.put(JSONArray().also { inner ->
                repeat(columns) { inner.put("") }
            })
        }
    }.toString()

private fun String.updateCell(rows: Int, columns: Int, row: Int, column: Int, text: String): String {
    val safeRows = rows.coerceAtLeast(1)
    val safeColumns = columns.coerceAtLeast(1)
    val outer = toCellsArray(safeRows, safeColumns)
    if (row in 0 until safeRows && column in 0 until safeColumns) {
        outer.getJSONArray(row).put(column, text)
    }
    return outer.toString()
}

private fun String.tableText(): String {
    val outer = runCatching { JSONArray(this) }.getOrNull() ?: return ""
    return List(outer.length()) { row ->
        val inner = outer.optJSONArray(row)
        List(inner?.length() ?: 0) { column -> inner?.optString(column).orEmpty() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
    }.filter { it.isNotBlank() }.joinToString(separator = "\n")
}

private fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>|</h[1-6]>|</li>|</tr>", RegexOption.IGNORE_CASE), "\n")
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

private fun String.toCellsArray(rows: Int, columns: Int): JSONArray {
    val parsed = runCatching { JSONArray(this) }.getOrNull()
    return JSONArray().also { outer ->
        repeat(rows) { row ->
            outer.put(JSONArray().also { inner ->
                val parsedRow = parsed?.optJSONArray(row)
                repeat(columns) { column ->
                    inner.put(parsedRow?.optString(column).orEmpty())
                }
            })
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
