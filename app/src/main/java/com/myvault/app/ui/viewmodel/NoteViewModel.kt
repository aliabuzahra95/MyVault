package com.myvault.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.DatabaseSeeder
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteVersionEntity
import com.myvault.app.data.formatting.NoteFormattingAction
import com.myvault.app.data.formatting.NoteFormattingModel
import com.myvault.app.data.formatting.NoteFormattingProvider
import com.myvault.app.data.formatting.NoteFormattingRepository
import com.myvault.app.data.formatting.NoteFormattingRequest
import com.myvault.app.data.formatting.NoteFormattingSessionStore
import com.myvault.app.data.formatting.NoteFormattingUiState
import com.myvault.app.data.narration.NarrationConfig
import com.myvault.app.data.narration.NarrationController
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.NoteExportRepository
import com.myvault.app.data.repository.NoteLinkRef
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.SourceReferenceCard
import com.myvault.app.ui.components.EditorBlock
import com.myvault.app.ui.components.EditorBlockType
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.VaultNoteLink
import com.myvault.app.ui.screens.VaultStyleMark
import com.myvault.app.ui.screens.toJsonArrayString
import com.myvault.app.ui.screens.toNoteLinksJsonArrayString
import com.myvault.app.ui.screens.parseVaultRichTextDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class NoteUiState(
    val note: NoteEntity? = null,
    val folderPath: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val blocks: List<EditorBlock> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val tables: List<NoteTableUiState> = emptyList(),
    val allNotes: List<NoteLinkSuggestion> = emptyList(),
    val backlinks: List<NoteLinkRef> = emptyList(),
    val sourceReferences: List<SourceReferenceCard> = emptyList(),
    val knowledgeTags: List<KnowledgeTagChip> = emptyList(),
    val richHtml: String = "",
    val richText: VaultRichTextDocument = VaultRichTextDocument("", emptyList(), emptyList()),
    val versions: List<NoteVersionEntity> = emptyList(),
    val attachmentCount: Int = 0,
    val attachmentsLoading: Boolean = false,
)

data class NoteLinkSuggestion(
    val id: String,
    val title: String,
)

data class NoteTableUiState(
    val id: String,
    val rowCount: Int,
    val columnCount: Int,
    val cells: List<List<String>>,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    seeder: DatabaseSeeder,
    noteRepository: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val noteExportRepository: NoteExportRepository,
    private val noteFormattingRepository: NoteFormattingRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val narrationController: NarrationController,
    formattingSessionStore: NoteFormattingSessionStore,
) : ViewModel() {
    private val noteId: String = savedStateHandle.get<String>("noteId").orEmpty()
    private val noteRepository = noteRepository
    private val secondaryDataReady = MutableStateFlow(false)
    private val noteWithFolderPath = combine(
        noteRepository.observeNote(noteId),
        noteRepository.observeFolderPath(noteId),
    ) { note, folderPath ->
        note to folderPath
    }

    private fun <T> deferredSecondaryFlow(initialValue: T, source: Flow<T>): Flow<T> = flow {
        emit(initialValue)
        secondaryDataReady.filter { it }.first()
        emitAll(source)
    }

    private data class AttachmentHydrationState(
        val attachments: List<AttachmentEntity> = emptyList(),
        val loaded: Boolean = false,
    )

    private data class NoteRichContentState(
        val richHtml: String = "",
        val richText: VaultRichTextDocument = VaultRichTextDocument("", emptyList(), emptyList()),
    )

    private data class NoteLinkState(
        val allNotes: List<NoteLinkSuggestion> = emptyList(),
        val backlinks: List<NoteLinkRef> = emptyList(),
    )

    private data class NoteSecondaryUiState(
        val tables: List<NoteTableUiState> = emptyList(),
        val versions: List<NoteVersionEntity> = emptyList(),
        val sourceReferences: List<SourceReferenceCard> = emptyList(),
        val knowledgeTags: List<KnowledgeTagChip> = emptyList(),
    )

    private val deferredAttachments = flow {
        emit(AttachmentHydrationState())
        secondaryDataReady.filter { it }.first()
        emitAll(
            attachmentRepository.observeForNote(noteId)
                .map { AttachmentHydrationState(attachments = it, loaded = true) },
            )
    }

    private val richContentState = combine(
        noteRepository.observeRawBlocks(noteId),
        noteRepository.observeNote(noteId)
            .map { it?.bodyPlainText }
            .distinctUntilChanged(),
    ) { rawBlocks, bodyPlainText ->
        NoteRichContentState(
            richHtml = richHtmlFrom(rawBlocks, bodyPlainText),
            richText = richTextFrom(rawBlocks, bodyPlainText),
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val noteContentState = combine(
        noteWithFolderPath,
        deferredSecondaryFlow(emptyList(), noteRepository.observeTags(noteId)),
        noteRepository.observeBlocks(noteId),
        deferredAttachments,
        richContentState,
    ) { noteAndPath, tags, blocks, attachmentHydration, richContent ->
        val (note, folderPath) = noteAndPath
        NoteUiState(
            note = note,
            folderPath = folderPath,
            tags = tags,
            blocks = blocks,
            attachments = attachmentHydration.attachments,
            attachmentCount = attachmentHydration.attachments.size,
            attachmentsLoading = !attachmentHydration.loaded,
            richHtml = richContent.richHtml,
            richText = richContent.richText,
        )
    }.let { contentFlow ->
        combine(
            contentFlow,
            attachmentRepository.observeCountForNote(noteId),
        ) { state, attachmentCount ->
            state.copy(
                attachmentCount = maxOf(attachmentCount, state.attachments.size),
                attachmentsLoading = attachmentCount > 0 && state.attachmentsLoading,
            )
        }
    }

    private val noteLinkState = combine(
        deferredSecondaryFlow(
            emptyList(),
            noteRepository.observeAllNotes()
                .map { notes ->
                    notes
                        .filter { it.id != noteId }
                        .map { NoteLinkSuggestion(it.id, it.title) }
                }
                .distinctUntilChanged(),
        ),
        deferredSecondaryFlow(emptyList(), noteRepository.observeBacklinks(noteId)),
    ) { noteSuggestions, backlinks ->
        NoteLinkState(
            allNotes = noteSuggestions,
            backlinks = backlinks,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val coreUiState = combine(
        noteContentState,
        noteLinkState,
    ) { state, links ->
        state.copy(
            allNotes = links.allNotes,
            backlinks = links.backlinks,
        )
    }

    private val noteSecondaryState = combine(
        deferredSecondaryFlow(emptyList(), noteRepository.observeTables(noteId)),
        deferredSecondaryFlow(emptyList(), noteRepository.observeVersions(noteId)),
        deferredSecondaryFlow(emptyList(), knowledgeRepository.observeSourceReferencesForNote(noteId)),
        deferredSecondaryFlow(emptyList(), knowledgeRepository.observeTagsFor(KnowledgeRepository.TargetNote, noteId)),
    ) { tables, versions, sourceReferences, knowledgeTags ->
        NoteSecondaryUiState(
            tables = tables.map { it.toUiState() },
            versions = versions,
            sourceReferences = sourceReferences,
            knowledgeTags = knowledgeTags,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<NoteUiState> = combine(
        coreUiState,
        noteSecondaryState,
    ) { state, secondary ->
        state.copy(
            tables = secondary.tables,
            versions = secondary.versions,
            sourceReferences = secondary.sourceReferences,
            knowledgeTags = secondary.knowledgeTags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteUiState())
    private val _formattingState = formattingSessionStore.stateFor(noteId)
    val formattingState: StateFlow<NoteFormattingUiState> = _formattingState
    val narrationState: StateFlow<com.myvault.app.data.narration.NarrationUiState> = narrationController.state
    val azureNarrationProgress =
        narrationController.progressFor(noteId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var formattingJob: Job? = null

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
        viewModelScope.launch {
            delay(320)
            secondaryDataReady.value = true
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch { noteRepository.updateTitle(noteId, title) }
    }

    fun updateBody(body: String) {
        viewModelScope.launch { noteRepository.updateBody(noteId, body) }
    }

    fun insertBlock(type: EditorBlockType) {
        viewModelScope.launch { noteRepository.insertBlock(noteId, type) }
    }

    fun saveRichHtml(html: String, plainText: String) {
        viewModelScope.launch { noteRepository.saveRichHtml(noteId, html, plainText) }
    }

    fun saveRichText(text: String, styleMarks: List<VaultStyleMark>, noteLinks: List<VaultNoteLink>) {
        viewModelScope.launch { noteRepository.saveRichText(noteId, text, styleMarks.toJsonArrayString(), noteLinks.toNoteLinksJsonArrayString()) }
    }

    fun restoreVersion(versionId: String) {
        viewModelScope.launch { noteRepository.restoreVersion(noteId, versionId) }
    }


    fun startNarration(title: String, body: String, voice: String = NarrationConfig.DEFAULT_VOICE) {
        narrationController.start(noteId, title, body, voice)
    }

    fun startDeviceNarration(title: String, body: String) {
        narrationController.startDevice(noteId, title, body)
    }

    fun startAzureNarration(title: String, body: String) {
        narrationController.startAzure(noteId, title, body)
    }

    fun resumeAzureNarration(title: String, body: String) {
        narrationController.startAzure(noteId, title, body, resume = true)
    }

    fun startAzureNarrationFromSelection(title: String, body: String, startOffset: Int) {
        narrationController.startAzure(noteId, title, body, bodyStartOffset = startOffset)
    }

    fun createTable(rows: Int, columns: Int) {
        viewModelScope.launch { noteRepository.createTable(noteId, rows, columns) }
    }

    fun updateTableCell(tableId: String, row: Int, column: Int, text: String) {
        viewModelScope.launch { noteRepository.updateTableCell(noteId, tableId, row, column, text) }
    }

    fun deleteTable(tableId: String) {
        viewModelScope.launch { noteRepository.deleteTable(noteId, tableId) }
    }

    fun attachDocument(uri: Uri) {
        viewModelScope.launch { attachmentRepository.attachDocument(noteId, uri) }
    }

    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { noteRepository.setPinned(noteId, pinned) }
    }

    fun setFavourite(favourite: Boolean) {
        viewModelScope.launch { noteRepository.setFavourite(noteId, favourite) }
    }

    fun addKnowledgeTag(name: String) {
        viewModelScope.launch { knowledgeRepository.addTag(KnowledgeRepository.TargetNote, noteId, name) }
    }

    fun removeKnowledgeTag(tagId: String) {
        viewModelScope.launch { knowledgeRepository.removeTag(KnowledgeRepository.TargetNote, noteId, tagId) }
    }

    fun removeSourceReference(referenceId: String) {
        viewModelScope.launch { knowledgeRepository.removeSourceReference(referenceId) }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            onDeleted()
        }
    }

    fun exportText(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { noteExportRepository.exportText(noteId, uri) }
                .onSuccess { onComplete("Text export saved") }
                .onFailure { onComplete("Text export failed: ${it.message ?: "Unknown error"}") }
        }
    }

    fun exportPdf(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { noteExportRepository.exportPdf(noteId, uri) }
                .onSuccess { onComplete("PDF export saved") }
                .onFailure { onComplete("PDF export failed: ${it.message ?: "Unknown error"}") }
        }
    }

    fun runFormattingTool(
        action: NoteFormattingAction,
        provider: NoteFormattingProvider,
        model: NoteFormattingModel,
        title: String,
        body: String,
    ) {
        formattingJob?.cancel()
        formattingJob = viewModelScope.launch {
            val effectiveModel = model.fastForRetainedFormattingAction()
            _formattingState.update {
                it.copy(
                    loading = true,
                    action = action,
                    provider = provider,
                    model = effectiveModel,
                    result = "",
                    error = null,
                    progressLabel = formattingLoadingLabel(action, body),
                )
            }

            runCatching {
                noteFormattingRepository.format(
                    request = NoteFormattingRequest(
                        action = action,
                        provider = provider,
                        model = effectiveModel,
                        title = title,
                        body = body,
                    ),
                    onProgress = { progress ->
                        _formattingState.update { state -> state.copy(progressLabel = progress) }
                    },
                ).editorHtml
            }.onSuccess { result ->
                _formattingState.update {
                    it.copy(
                        loading = false,
                        action = action,
                        provider = provider,
                        model = effectiveModel,
                        result = result,
                        error = null,
                        progressLabel = null,
                    )
                }
            }.onFailure { error ->
                _formattingState.update {
                    it.copy(
                        loading = false,
                        action = action,
                        provider = provider,
                        model = effectiveModel,
                        result = "",
                        error = error.message ?: "Note formatting failed.",
                        progressLabel = null,
                    )
                }
            }
        }
    }

    fun setFormattingProvider(provider: NoteFormattingProvider) {
        _formattingState.update { it.copy(provider = provider) }
    }

    fun setFormattingModel(model: NoteFormattingModel) {
        _formattingState.update { it.copy(model = model) }
    }

    fun clearFormattingResult() {
        _formattingState.update { it.copy(result = "", error = null, action = null, progressLabel = null) }
    }

}

private const val FormattingChunkProgressThreshold = 7_000

private fun formattingLoadingLabel(action: NoteFormattingAction, body: String): String =
    if (action == NoteFormattingAction.IntelligentStructure || action == NoteFormattingAction.StructureOnly) {
        if (body.length > FormattingChunkProgressThreshold) "Structuring part 1..." else "Structuring note..."
    } else {
        "Formatting note..."
    }

private fun NoteFormattingModel.fastForRetainedFormattingAction(): NoteFormattingModel =
    if (this == NoteFormattingModel.Smart) NoteFormattingModel.Fast else this

private fun NoteTableEntity.toUiState(): NoteTableUiState =
    NoteTableUiState(
        id = id,
        rowCount = rowCount,
        columnCount = columnCount,
        cells = cellsJson.toCells(rowCount, columnCount),
    )

private fun String.toCells(rows: Int, columns: Int): List<List<String>> {
    val parsed = runCatching { JSONArray(this) }.getOrNull()
    return List(rows.coerceAtLeast(1)) { row ->
        val parsedRow = parsed?.optJSONArray(row)
        List(columns.coerceAtLeast(1)) { column ->
            parsedRow?.optString(column).orEmpty()
        }
    }
}

private fun richHtmlFrom(blocks: List<BlockEntity>, fallbackPlainText: String?): String {
    blocks.firstOrNull { it.type == "rich_text" }?.content?.let { content ->
        parseVaultRichTextDocument(content)?.let { return plainTextToHtml(it.text) }
    }
    blocks.firstOrNull { it.type == "rich_html" }?.content?.let { return it }
    blocks.firstOrNull { it.type == "rich_body" }?.content?.let { legacyRichBody ->
        parseLegacyRichBodyText(legacyRichBody)?.let { text ->
            return plainTextToHtml(text)
        }
    }

    if (blocks.isNotEmpty()) {
        return blocks
            .filterNot { it.type == "divider" }
            .joinToString(separator = "") { block ->
                val escaped = block.content.substringBefore("|").escapeHtml().replace("\n", "<br>")
                when (block.type) {
                    "heading" -> "<h2>$escaped</h2>"
                    "quote" -> "<p><i>$escaped</i></p>"
                    "bullet" -> "<ul><li>$escaped</li></ul>"
                    "numbered" -> "<ol><li>$escaped</li></ol>"
                    else -> "<p>$escaped</p>"
                }
            }
    }

    return plainTextToHtml(fallbackPlainText.orEmpty())
}

private fun richTextFrom(blocks: List<BlockEntity>, fallbackPlainText: String?): VaultRichTextDocument {
    blocks.firstOrNull { it.type == "rich_text" }?.content?.let { content ->
        parseVaultRichTextDocument(content)?.let { return it }
    }
    blocks.firstOrNull { it.type == "rich_html" }?.content?.let { html ->
            return VaultRichTextDocument(text = html.stripHtml(), styleMarks = emptyList(), noteLinks = emptyList())
    }
    blocks.firstOrNull { it.type == "rich_body" }?.content?.let { legacyRichBody ->
        parseLegacyRichBodyText(legacyRichBody)?.let { text ->
            return VaultRichTextDocument(text = text, styleMarks = emptyList(), noteLinks = emptyList())
        }
    }
    if (blocks.isNotEmpty()) {
        return VaultRichTextDocument(
            text = blocks
                .filterNot { it.type == "divider" }
                .joinToString(separator = "\n") { it.content.substringBefore("|") },
            styleMarks = emptyList(),
            noteLinks = emptyList(),
        )
    }
    return VaultRichTextDocument(text = fallbackPlainText.orEmpty(), styleMarks = emptyList(), noteLinks = emptyList())
}

private fun parseLegacyRichBodyText(raw: String): String? =
    runCatching { JSONObject(raw).optString("text").takeIf { it.isNotBlank() } }.getOrNull()

private fun plainTextToHtml(text: String): String =
    if (text.isBlank()) "" else "<p>${text.escapeHtml().replace("\n", "<br>")}</p>"

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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
