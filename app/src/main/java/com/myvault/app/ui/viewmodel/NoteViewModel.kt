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
import com.myvault.app.data.narration.NarrationConfig
import com.myvault.app.data.narration.NarrationController
import com.myvault.app.data.repository.AiConversationRepository
import com.myvault.app.data.repository.AiConversationSummary
import com.myvault.app.data.repository.AttachmentRepository
import com.myvault.app.data.repository.KnowledgeRepository
import com.myvault.app.data.repository.KnowledgeTagChip
import com.myvault.app.data.repository.NoteAiAction
import com.myvault.app.data.repository.NoteAiChatRole
import com.myvault.app.data.repository.NoteAiConversationTurn
import com.myvault.app.data.repository.NoteAiModel
import com.myvault.app.data.repository.NoteAiProvider
import com.myvault.app.data.repository.NoteAiRepository
import com.myvault.app.data.repository.NoteExportRepository
import com.myvault.app.data.repository.NoteLinkRef
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.SelectedTextAiAction
import com.myvault.app.data.repository.SourceReferenceCard
import com.myvault.app.data.repository.displayName
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

data class NoteAiUiState(
    val loading: Boolean = false,
    val isStreaming: Boolean = false,
    val action: NoteAiAction? = null,
    val provider: NoteAiProvider = NoteAiProvider.ChatGPT,
    val model: NoteAiModel = NoteAiModel.Gemini25Flash,
    val result: String = "",
    val streamedText: String = "",
    val error: String? = null,
    val question: String = "",
    val progressLabel: String? = null,
    val canContinue: Boolean = false,
    val messages: List<NoteAiChatMessage> = emptyList(),
    val activeConversationId: String? = null,
    val conversationHistory: List<AiConversationSummary> = emptyList(),
)

enum class NoteAiMessageRole {
    User,
    Assistant,
    Error,
}

data class NoteAiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val role: NoteAiMessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: NoteAiAction? = null,
    val provider: NoteAiProvider? = null,
    val model: NoteAiModel? = null,
)

data class SelectedTextAiUiState(
    val loading: Boolean = false,
    val action: SelectedTextAiAction? = null,
    val selectedText: String = "",
    val result: String = "",
    val error: String? = null,
    val question: String = "",
)

@Singleton
class NoteAiSessionStore @Inject constructor() {
    private val states = mutableMapOf<String, MutableStateFlow<NoteAiUiState>>()

    fun stateFor(noteId: String): MutableStateFlow<NoteAiUiState> =
        states.getOrPut(noteId) { MutableStateFlow(NoteAiUiState()) }
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    seeder: DatabaseSeeder,
    noteRepository: NoteRepository,
    private val attachmentRepository: AttachmentRepository,
    private val noteExportRepository: NoteExportRepository,
    private val noteAiRepository: NoteAiRepository,
    private val aiConversationRepository: AiConversationRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val narrationController: NarrationController,
    aiSessionStore: NoteAiSessionStore,
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
    private val _aiState = aiSessionStore.stateFor(noteId)
    val aiState: StateFlow<NoteAiUiState> = _aiState
    private val _selectedTextAiState = MutableStateFlow(SelectedTextAiUiState())
    val selectedTextAiState: StateFlow<SelectedTextAiUiState> = _selectedTextAiState
    val narrationState: StateFlow<com.myvault.app.data.narration.NarrationUiState> = narrationController.state
    private var aiGenerationJob: Job? = null

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
        viewModelScope.launch {
            delay(320)
            secondaryDataReady.value = true
        }
        viewModelScope.launch {
            secondaryDataReady.filter { it }.first()
            val summaries = aiConversationRepository.conversationSummaries(noteId)
            val savedSession = aiConversationRepository.loadLatestSession(noteId)
            if (savedSession != null && savedSession.messages.isNotEmpty() && _aiState.value.messages.isEmpty()) {
                _aiState.update {
                    it.copy(
                        messages = savedSession.messages,
                        activeConversationId = savedSession.id,
                        conversationHistory = summaries,
                    )
                }
            } else {
                _aiState.update { it.copy(conversationHistory = summaries) }
            }
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch { noteRepository.updateTitle(noteId, title) }
    }

    fun updateBlock(blockId: String, content: String) {
        viewModelScope.launch { noteRepository.updateBlockContent(noteId, blockId, content) }
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

    fun toggleNarrationPlayback() {
        narrationController.toggle()
    }

    fun stopNarration() {
        narrationController.stop()
    }

    fun setNarrationSpeed(speed: Float) {
        narrationController.setSpeed(speed)
    }

    fun seekNarration(positionMs: Long) {
        narrationController.seekTo(positionMs)
    }

    fun refreshNarrationProgress() {
        narrationController.refreshProgress()
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

    fun runAiTool(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel, title: String, body: String, question: String = "") {
        if ((action == NoteAiAction.Ask || action == NoteAiAction.GeneralAsk) && question.isBlank()) {
            _aiState.update { it.copy(error = "Type a question first.") }
            return
        }
        aiGenerationJob?.cancel()
        aiGenerationJob = viewModelScope.launch {
            val routedAction = routeAiIntent(action, question)
            val effectiveModel = model.fastForLightweight(routedAction)
            val userPrompt = if (question.isNotBlank()) question.trim() else userMessageFor(routedAction, question)
            val appendToConversation = !routedAction.isEditorOutputMode()
            val requestWithVaultContext = question.withVaultContextIfUseful(routedAction, uiState.value)
            val history = _aiState.value.messages.toConversationHistory()

            if (appendToConversation) {
                val userMessage = NoteAiChatMessage(
                    noteId = noteId,
                    role = NoteAiMessageRole.User,
                    content = userPrompt,
                    action = routedAction,
                    provider = provider,
                    model = effectiveModel,
                )
                _aiState.update {
                    it.copy(
                        loading = true,
                        isStreaming = provider == NoteAiProvider.Gemini,
                        action = routedAction,
                        provider = provider,
                        model = effectiveModel,
                        error = null,
                        result = "",
                        streamedText = "",
                        canContinue = false,
                        progressLabel = loadingLabelFor(routedAction, body),
                        question = if (routedAction.isEditorOutputMode() || question.isNotBlank()) "" else it.question,
                        messages = it.messages + userMessage,
                    )
                }
                val conversationId = aiConversationRepository.saveMessage(userMessage, _aiState.value.activeConversationId)
                val summaries = aiConversationRepository.conversationSummaries(noteId)
                _aiState.update { it.copy(activeConversationId = conversationId, conversationHistory = summaries) }
            } else {
                _aiState.update {
                    it.copy(
                        loading = true,
                        isStreaming = false,
                        action = routedAction,
                        provider = provider,
                        model = effectiveModel,
                        error = null,
                        result = "",
                        streamedText = "",
                        canContinue = false,
                        progressLabel = loadingLabelFor(routedAction, body),
                        question = "",
                    )
                }
            }

            if (appendToConversation && !routedAction.isEditorOutputMode()) {
                var fullResponse = ""
                runCatching {
                    noteAiRepository.generateStreaming(
                        action = routedAction,
                        provider = provider,
                        model = effectiveModel,
                        title = title,
                        body = body,
                        question = requestWithVaultContext,
                        history = history,
                        onProgress = { progress -> _aiState.update { it.copy(progressLabel = progress) } },
                    ).collect { chunk ->
                        fullResponse += chunk
                        _aiState.update {
                            it.copy(
                                loading = true,
                                isStreaming = true,
                                streamedText = fullResponse,
                                result = fullResponse,
                                progressLabel = null,
                            )
                        }
                    }
                    fullResponse
                }.onSuccess { result ->
                    val cleaned = result.trim()
                    val pausedForLimit = cleaned.contains("output limit", ignoreCase = true) || cleaned.contains("Tap Continue", ignoreCase = true)
                    val assistantMessage = NoteAiChatMessage(
                        noteId = noteId,
                        role = NoteAiMessageRole.Assistant,
                        content = cleaned,
                        action = routedAction,
                        provider = provider,
                        model = effectiveModel,
                    )
                    val conversationId = _aiState.value.activeConversationId
                    aiConversationRepository.saveMessage(assistantMessage, conversationId)
                    refreshAiConversationHistory()
                    _aiState.update {
                        it.copy(
                            loading = false,
                            isStreaming = false,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                            result = cleaned,
                            streamedText = "",
                            error = null,
                            canContinue = pausedForLimit,
                            progressLabel = null,
                            messages = it.messages + assistantMessage,
                        )
                    }
                }.onFailure { error ->
                    val partial = _aiState.value.streamedText.trim()
                    val message = error.message ?: "AI request failed."
                    if (partial.isNotBlank()) {
                        val assistantMessage = NoteAiChatMessage(
                            noteId = noteId,
                            role = NoteAiMessageRole.Assistant,
                            content = partial,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                        )
                        val conversationId = _aiState.value.activeConversationId
                        aiConversationRepository.saveMessage(assistantMessage, conversationId)
                        refreshAiConversationHistory()
                        _aiState.update {
                            it.copy(
                                loading = false,
                                isStreaming = false,
                                result = partial,
                                streamedText = "",
                                error = message,
                                canContinue = true,
                                progressLabel = null,
                                messages = it.messages + assistantMessage,
                            )
                        }
                    } else {
                        val errorMessage = NoteAiChatMessage(
                            noteId = noteId,
                            role = NoteAiMessageRole.Error,
                            content = message,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                        )
                        val conversationId = _aiState.value.activeConversationId
                        aiConversationRepository.saveMessage(errorMessage, conversationId)
                        refreshAiConversationHistory()
                        _aiState.update {
                            it.copy(
                                loading = false,
                                isStreaming = false,
                                error = message,
                                progressLabel = null,
                                messages = it.messages + errorMessage,
                            )
                        }
                    }
                }
                return@launch
            }

            runCatching {
                noteAiRepository.generate(
                    action = routedAction,
                    provider = provider,
                    model = effectiveModel,
                    title = title,
                    body = body,
                    question = requestWithVaultContext,
                    history = if (appendToConversation) history else emptyList(),
                    onProgress = { progress -> _aiState.update { it.copy(progressLabel = progress) } },
                )
            }.onSuccess { result ->
                _aiState.update {
                    if (appendToConversation) {
                        val assistantMessage = NoteAiChatMessage(
                            noteId = noteId,
                            role = NoteAiMessageRole.Assistant,
                            content = result,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                        )
                        val conversationId = it.activeConversationId
                        viewModelScope.launch {
                            aiConversationRepository.saveMessage(assistantMessage, conversationId)
                            refreshAiConversationHistory()
                        }
                        it.copy(
                            loading = false,
                            isStreaming = false,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                            result = result,
                            streamedText = "",
                            error = null,
                            canContinue = false,
                            progressLabel = null,
                            messages = it.messages + assistantMessage,
                        )
                    } else {
                        it.copy(
                            loading = false,
                            isStreaming = false,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                            result = result,
                            streamedText = "",
                            error = null,
                            canContinue = false,
                            progressLabel = null,
                        )
                    }
                }
            }.onFailure { error ->
                val message = error.message ?: "AI request failed."
                _aiState.update {
                    if (appendToConversation) {
                        val errorMessage = NoteAiChatMessage(
                            noteId = noteId,
                            role = NoteAiMessageRole.Error,
                            content = message,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                        )
                        val conversationId = it.activeConversationId
                        viewModelScope.launch {
                            aiConversationRepository.saveMessage(errorMessage, conversationId)
                            refreshAiConversationHistory()
                        }
                        it.copy(
                            loading = false,
                            isStreaming = false,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                            error = message,
                            progressLabel = null,
                            messages = it.messages + errorMessage,
                        )
                    } else {
                        it.copy(
                            loading = false,
                            isStreaming = false,
                            action = routedAction,
                            provider = provider,
                            model = effectiveModel,
                            error = message,
                            progressLabel = null,
                        )
                    }
                }
            }
        }
    }

    fun cancelAiGeneration() {
        aiGenerationJob?.cancel()
        aiGenerationJob = null
        val partial = _aiState.value.streamedText.trim()
        _aiState.update {
            it.copy(
                loading = false,
                isStreaming = false,
                result = partial.ifBlank { it.result },
                streamedText = "",
                progressLabel = null,
                canContinue = partial.isNotBlank(),
            )
        }
    }


    fun runSelectedTextAi(
        action: SelectedTextAiAction,
        provider: NoteAiProvider,
        model: NoteAiModel,
        title: String,
        body: String,
        selectedText: String,
        question: String = "",
    ) {
        if (selectedText.isBlank()) {
            _selectedTextAiState.update { it.copy(error = "Select some text first.") }
            return
        }
        viewModelScope.launch {
            val effectiveModel = model.fastForSelectedText(action)
            _selectedTextAiState.update {
                it.copy(
                    loading = true,
                    action = action,
                    selectedText = selectedText,
                    result = "",
                    error = null,
                    question = question,
                )
            }
            runCatching {
                noteAiRepository.generateForSelectedText(
                    action = action,
                    provider = provider,
                    model = effectiveModel,
                    title = title,
                    body = body,
                    selectedText = selectedText,
                    question = question,
                    history = _aiState.value.messages.toConversationHistory(),
                )
            }.onSuccess { result ->
                _selectedTextAiState.update {
                    it.copy(
                        loading = false,
                        action = action,
                        selectedText = selectedText,
                        result = result,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _selectedTextAiState.update {
                    it.copy(
                        loading = false,
                        action = action,
                        selectedText = selectedText,
                        result = "",
                        error = error.message ?: "Selected text AI request failed.",
                    )
                }
            }
        }
    }

    fun clearSelectedTextAi() {
        _selectedTextAiState.update { SelectedTextAiUiState() }
    }

    fun setSelectedTextAiQuestion(question: String, action: SelectedTextAiAction? = null) {
        _selectedTextAiState.update {
            it.copy(
                question = question,
                action = action ?: it.action,
                error = null,
            )
        }
    }

    fun sendSelectedTextResultToChat(action: SelectedTextAiAction, selectedText: String, result: String) {
        if (result.isBlank()) return
        val userMessage = NoteAiChatMessage(
            noteId = noteId,
            role = NoteAiMessageRole.User,
            content = "Selected text: ${selectedText.take(500)}\n\nAction: ${action.displayName}",
            action = NoteAiAction.Ask,
            provider = _aiState.value.provider,
            model = _aiState.value.model,
        )
        val assistantMessage = NoteAiChatMessage(
            noteId = noteId,
            role = NoteAiMessageRole.Assistant,
            content = result,
            action = NoteAiAction.Ask,
            provider = _aiState.value.provider,
            model = _aiState.value.model,
        )
        _aiState.update {
            it.copy(
                messages = (it.messages + listOf(userMessage, assistantMessage)).takeLast(AiHistoryMessageLimit),
            )
        }
        viewModelScope.launch {
            val conversationId = aiConversationRepository.saveMessage(userMessage, _aiState.value.activeConversationId)
            aiConversationRepository.saveMessage(assistantMessage, conversationId)
            val summaries = aiConversationRepository.conversationSummaries(noteId)
            _aiState.update {
                it.copy(
                    activeConversationId = conversationId,
                    conversationHistory = summaries,
                )
            }
        }
    }

    fun setAiProvider(provider: NoteAiProvider) {
        _aiState.update { it.copy(provider = provider) }
    }

    fun setAiModel(model: NoteAiModel) {
        _aiState.update { it.copy(model = model) }
    }

    fun setAiQuestion(question: String) {
        _aiState.update { it.copy(question = question) }
    }

    fun clearAiResult() {
        _aiState.update { it.copy(result = "", streamedText = "", error = null, action = null, canContinue = false) }
    }

    fun clearAiConversation() {
        val currentConversationId = _aiState.value.activeConversationId
        _aiState.update { it.copy(messages = emptyList(), result = "", streamedText = "", error = null, action = null, canContinue = false, activeConversationId = null) }
        viewModelScope.launch {
            if (currentConversationId != null) {
                aiConversationRepository.clearConversation(currentConversationId)
            }
            refreshAiConversationHistory()
        }
    }

    fun openAiConversation(conversationId: String) {
        viewModelScope.launch {
            val messages = aiConversationRepository.loadMessages(conversationId)
            _aiState.update {
                it.copy(
                    loading = false,
                    action = null,
                    result = "",
                    error = null,
                    progressLabel = null,
                    question = "",
                    messages = messages,
                    activeConversationId = conversationId,
                    conversationHistory = aiConversationRepository.conversationSummaries(noteId),
                )
            }
        }
    }

    fun startNewAiConversation() {
        viewModelScope.launch {
            val conversationId = aiConversationRepository.startConversation(noteId)
            _aiState.update {
                it.copy(
                    loading = false,
                    action = null,
                    result = "",
                    error = null,
                    progressLabel = null,
                    question = "",
                    messages = emptyList(),
                    activeConversationId = conversationId,
                    conversationHistory = aiConversationRepository.conversationSummaries(noteId),
                )
            }
        }
    }

    private suspend fun refreshAiConversationHistory() {
        _aiState.update { it.copy(conversationHistory = aiConversationRepository.conversationSummaries(noteId)) }
    }

}

private const val AiHistoryMessageLimit = 20
private const val AiChunkProgressThreshold = 7_000

private fun loadingLabelFor(action: NoteAiAction, body: String): String =
    if (action == NoteAiAction.IntelligentStructure || action == NoteAiAction.StructureOnly) {
        if (body.length > AiChunkProgressThreshold) "Structuring part 1..." else "Structuring note..."
    } else {
        "Thinking..."
    }

private fun NoteAiAction.isEditorOutputMode(): Boolean =
    this == NoteAiAction.IntelligentStructure ||
        this == NoteAiAction.StructureOnly ||
        this == NoteAiAction.CleanFormat ||
        this == NoteAiAction.FormatNote

private fun NoteAiModel.fastForLightweight(action: NoteAiAction): NoteAiModel =
    if (
        this.isDeepModel &&
        action in setOf(
            NoteAiAction.QuickSummary,
            NoteAiAction.DeepSummary,
            NoteAiAction.ExplainNote,
            NoteAiAction.FormatNote,
            NoteAiAction.CleanFormat,
            NoteAiAction.StructureOnly,
            NoteAiAction.IntelligentStructure,
        )
    ) {
        NoteAiModel.Gemini25Flash
    } else {
        this
    }

private fun NoteAiModel.fastForSelectedText(action: SelectedTextAiAction): NoteAiModel =
    if (
        this.isDeepModel &&
        action in setOf(
            SelectedTextAiAction.Ask,
            SelectedTextAiAction.Explain,
            SelectedTextAiAction.Simplify,
            SelectedTextAiAction.Terminology,
            SelectedTextAiAction.RelatedConcepts,
            SelectedTextAiAction.StudyQuestions,
        )
    ) {
        NoteAiModel.Gemini25Flash
    } else {
        this
    }

private fun routeAiIntent(action: NoteAiAction, question: String): NoteAiAction {
    if (action != NoteAiAction.Ask && action != NoteAiAction.GeneralAsk) return action
    val normalized = question.trim().lowercase()
    if (normalized.isBlank()) return action
    return when {
        normalized.containsAny(
            "intelligent structure",
            "deep structure",
            "restructure with ai",
            "improve the wording",
        ) -> NoteAiAction.IntelligentStructure

        normalized.containsAny(
            "organise",
            "organize",
            "structure this",
            "structure the note",
            "restructure",
            "tidy this note",
            "clean up this note",
        ) -> NoteAiAction.StructureOnly

        normalized.containsAny(
            "format this",
            "format the note",
            "make this cleaner",
            "make it cleaner",
            "cleaner format",
        ) -> NoteAiAction.StructureOnly

        normalized.containsAny(
            "quick summary",
            "brief summary",
            "short summary",
            "summarise briefly",
            "summarize briefly",
            "recap",
            "key points",
        ) -> NoteAiAction.QuickSummary

        normalized.containsAny(
            "summarise",
            "summarize",
            "summary",
        ) -> NoteAiAction.DeepSummary

        normalized.containsAny(
            "deep analysis",
            "analyse",
            "analyze",
            "compare",
            "ashari",
            "maturidi",
            "mu'tazili",
            "mutazili",
            "objection",
            "response",
            "assumption",
            "argument map",
        ) -> NoteAiAction.DeepAnalysis

        normalized.containsAny(
            "explain",
            "clarify",
            "what does",
            "what is meant",
            "what does this mean",
        ) -> NoteAiAction.ExplainNote

        normalized.containsAny(
            "teach me",
            "help me understand",
        ) -> NoteAiAction.StudyTutor

        else -> action
    }
}

private fun String.containsAny(vararg terms: String): Boolean =
    terms.any { term -> contains(term) }

private fun userMessageFor(action: NoteAiAction, question: String): String =
    when (action) {
        NoteAiAction.Ask,
        NoteAiAction.GeneralAsk,
        -> question.trim()
        else -> action.displayName
    }

private fun String.withVaultContextIfUseful(action: NoteAiAction, state: NoteUiState): String {
    if (action.isEditorOutputMode()) return this
    val normalized = lowercase()
    val alwaysContextual = action in setOf(NoteAiAction.StudyTutor, NoteAiAction.DeepAnalysis)
    val wantsContext = alwaysContextual || action in setOf(NoteAiAction.Ask, NoteAiAction.GeneralAsk) &&
        normalized.containsAny(
            "related",
            "connect",
            "connection",
            "backlink",
            "source",
            "reference",
            "pdf",
            "annotation",
            "tag",
            "folder",
            "where did i",
            "where have i",
            "my notes",
            "vault",
        )
    if (!wantsContext) return this
    val context = buildString {
        appendLine("Active note title: ${state.note?.title.orEmpty().ifBlank { "Untitled note" }}")
        if (state.folderPath.isNotEmpty()) appendLine("Folder path: ${state.folderPath.joinToString(" / ")}")
        if (state.knowledgeTags.isNotEmpty()) appendLine("Tags: ${state.knowledgeTags.take(8).joinToString { it.name }}")
        if (state.backlinks.isNotEmpty()) {
            appendLine("Backlinks:")
            state.backlinks.take(5).forEach { appendLine("- ${it.title}: ${it.preview.take(180)}") }
        }
        if (state.sourceReferences.isNotEmpty()) {
            appendLine("Source references:")
            state.sourceReferences.take(5).forEach { reference ->
                appendLine("- ${reference.title} p.${reference.pageIndex + 1}: linked source/reference")
            }
        }
    }.trim()
    if (context.isBlank()) return this
    return """
        ${trim()}

        Local MyVault context available for this note:
        <vault_context>
        $context
        </vault_context>

        Use this vault context only if it directly helps. Do not pretend it is exhaustive.
    """.trimIndent()
}

private fun List<NoteAiChatMessage>.toConversationHistory(): List<NoteAiConversationTurn> =
    filter { it.role == NoteAiMessageRole.User || it.role == NoteAiMessageRole.Assistant }
        .takeLast(AiHistoryMessageLimit)
        .map { message ->
            NoteAiConversationTurn(
                role = if (message.role == NoteAiMessageRole.User) NoteAiChatRole.User else NoteAiChatRole.Assistant,
                content = message.content,
            )
        }

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
