package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.myvault.app.data.ai.AiResearchLocalPreferences
import com.myvault.app.data.ai.AiResearchProvider
import com.myvault.app.data.ai.ShamelaAuthRepository
import com.myvault.app.data.ai.ShamelaConnectionState
import com.myvault.app.data.ai.ShamelaMcpClient
import com.myvault.app.data.ai.ShamelaMcpConnectionState
import com.myvault.app.data.ai.ShamelaResearchProvider
import com.myvault.app.data.ai.ResearchSource
import com.myvault.app.data.ai.ResearchSourceContext
import com.myvault.app.data.ai.GroundedResearchOrchestrator
import com.myvault.app.data.ai.QuoteVerificationClassification
import com.myvault.app.data.ai.toShamelaSourceNote
import com.myvault.app.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Intent
import android.os.SystemClock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class AiResearchViewModel @Inject constructor(
    private val localPreferences: AiResearchLocalPreferences,
    private val shamelaAuthRepository: ShamelaAuthRepository,
    private val shamelaMcpClient: ShamelaMcpClient,
    private val shamelaResearchProvider: ShamelaResearchProvider,
    private val groundedResearch: GroundedResearchOrchestrator,
    private val noteRepository: NoteRepository,
) : ViewModel() {
    private var requestJob: Job? = null
    private var activeWorkingMessageId: String? = null
    private val _uiState = MutableStateFlow(
        AiResearchUiState(selectedProvider = localPreferences.selectedProvider()),
    )
    val uiState: StateFlow<AiResearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shamelaAuthRepository.connection.collect { connection ->
                _uiState.update { it.copy(shamelaConnection = connection) }
                if (connection == ShamelaConnectionState.Connected) discoverShamelaMcp()
            }
        }
        viewModelScope.launch {
            noteRepository.observeAllNotes().collect { notes ->
                _uiState.update { state ->
                    state.copy(
                        noteOptions = notes.map { note ->
                            ResearchNoteOption(note.id, note.title, note.updatedAt)
                        },
                    )
                }
            }
        }
    }

    fun createShamelaAuthorizationIntent(): Result<Intent> = runCatching {
        shamelaAuthRepository.createAuthorizationIntent()
    }.onFailure { error ->
        _uiState.update {
            it.copy(shamelaConnection = ShamelaConnectionState.Error(error.message ?: "Unable to open Shamela sign-in."))
        }
    }

    fun completeShamelaAuthorization(data: Intent?) {
        viewModelScope.launch {
            shamelaAuthRepository.completeAuthorization(data).onFailure { error ->
                _uiState.update {
                    it.copy(
                        notice = if (data == null) {
                            "Shamela sign-in cancelled."
                        } else {
                            error.safeResearchMessage("Could not connect Shamela.")
                        },
                    )
                }
            }
        }
    }

    fun disconnectShamela() {
        viewModelScope.launch {
            shamelaMcpClient.clearSession()
            _uiState.update { it.copy(shamelaMcpConnection = ShamelaMcpConnectionState.Idle) }
            shamelaAuthRepository.disconnect()
        }
    }

    fun discoverShamelaMcp() {
        if (_uiState.value.shamelaMcpConnection == ShamelaMcpConnectionState.Connecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(shamelaMcpConnection = ShamelaMcpConnectionState.Connecting) }
            runCatching { shamelaMcpClient.discover() }
                .onSuccess { contract ->
                    _uiState.update { it.copy(shamelaMcpConnection = ShamelaMcpConnectionState.Ready(contract)) }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    handleShamelaAuthorizationFailure(error)
                    _uiState.update {
                        it.copy(
                            shamelaMcpConnection = ShamelaMcpConnectionState.Error(
                                error.safeResearchMessage("Could not initialize Shamela research."),
                            ),
                        )
                    }
                }
        }
    }

    fun selectProvider(provider: AiResearchProvider) {
        localPreferences.setSelectedProvider(provider)
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun selectMode(mode: AiResearchMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun updateComposer(value: String) {
        _uiState.update { it.copy(composer = value.take(MaxComposerCharacters)) }
    }

    fun openSource(source: ResearchSource) {
        _uiState.update { it.copy(sourceDetail = SourceDetailState.Loading(source)) }
        viewModelScope.launch {
            runCatching { shamelaResearchProvider.sourceContext(source) }
                .onSuccess { context ->
                    _uiState.update { it.copy(sourceDetail = SourceDetailState.Ready(context)) }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    handleShamelaAuthorizationFailure(error)
                    _uiState.update {
                        it.copy(
                            sourceDetail = SourceDetailState.Error(
                                source = source,
                                message = error.safeResearchMessage("Could not open this Shamela source."),
                            ),
                        )
                    }
                }
        }
    }

    fun closeSource() {
        _uiState.update { it.copy(sourceDetail = null) }
    }

    fun beginSaveSource(source: ResearchSource) {
        _uiState.update {
            it.copy(
                sourceDetail = null,
                sourceSave = SourceSaveState(source = source),
            )
        }
    }

    fun dismissSourceSave() {
        if (_uiState.value.sourceSave?.isSaving == true) return
        _uiState.update { it.copy(sourceSave = null) }
    }

    fun createSourceNote() {
        val source = _uiState.value.sourceSave?.source ?: return
        saveSource(source) { sourceNote ->
            val noteId = noteRepository.createNote(folderId = null, title = sourceNote.title)
            noteRepository.saveRichText(noteId, sourceNote.body, "[]")
        }
    }

    fun appendSourceToNote(noteId: String) {
        val source = _uiState.value.sourceSave?.source ?: return
        val noteTitle = _uiState.value.noteOptions.firstOrNull { it.id == noteId }?.title ?: "note"
        saveSource(source, successMessage = "Passage added to $noteTitle") { sourceNote ->
            noteRepository.appendPlainText(noteId, sourceNote.body)
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun saveSource(
        source: ResearchSource,
        successMessage: String = "Shamela source saved to a new note",
        save: suspend (com.myvault.app.data.ai.ShamelaSourceNote) -> Unit,
    ) {
        if (_uiState.value.sourceSave?.isSaving == true) return
        _uiState.update { state ->
            state.copy(sourceSave = state.sourceSave?.copy(isSaving = true, error = null))
        }
        viewModelScope.launch {
            runCatching { save(source.toShamelaSourceNote()) }
                .onSuccess {
                    _uiState.update { it.copy(sourceSave = null, notice = successMessage) }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            sourceSave = state.sourceSave?.copy(
                                isSaving = false,
                                error = error.message ?: "Could not save this source.",
                            ),
                        )
                    }
                }
        }
    }

    fun submitQuestion() {
        val question = _uiState.value.composer.trim()
        if (question.isBlank() || _uiState.value.isBusy) return
        val userMessage = AiResearchMessage(
            id = UUID.randomUUID().toString(),
            role = AiResearchMessageRole.User,
            text = question,
        )
        if (_uiState.value.shamelaMcpConnection !is ShamelaMcpConnectionState.Ready) {
            _uiState.update { state ->
                state.copy(
                    composer = "",
                    messages = (state.messages + userMessage + AiResearchMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiResearchMessageRole.Assistant,
                        text = "Connect Shamela before searching verified sources.",
                        isError = true,
                    )).takeLast(MaxConversationMessages),
                )
            }
            return
        }
        val workingId = UUID.randomUUID().toString()
        _uiState.update { state ->
            state.copy(
                composer = "",
                isBusy = true,
                messages = (state.messages + listOf(
                    userMessage,
                    AiResearchMessage(
                        id = workingId,
                        role = AiResearchMessageRole.Assistant,
                        text = "Searching Shamela…",
                        isWorking = true,
                    ),
                )).takeLast(MaxConversationMessages),
            )
        }
        val job = viewModelScope.launch {
            if (_uiState.value.selectedMode == AiResearchMode.CompareScholars) {
                submitScholarComparison(question, workingId)
                return@launch
            }
            if (_uiState.value.selectedMode == AiResearchMode.VerifyQuote) {
                submitQuoteVerification(question, workingId)
                return@launch
            }
            if (!question.isRawShamelaSearch()) {
                submitGroundedQuestion(question, workingId)
                return@launch
            }
            runCatching {
                shamelaResearchProvider.search(
                    com.myvault.app.data.ai.ResearchSearchRequest(
                        query = question.toShamelaQuery(),
                    ),
                )
            }.onSuccess { result ->
                val summary = when {
                    result.sources.isEmpty() -> "No verified Shamela sources were located for this search."
                    result.totalHits != null -> "Found ${result.sources.size} of ${result.totalHits} matching Shamela sources in ${result.elapsedMillis} ms."
                    else -> "Found ${result.sources.size} matching Shamela sources in ${result.elapsedMillis} ms."
                }
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = summary,
                                sources = result.sources,
                            ),
                        ),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                handleShamelaAuthorizationFailure(error)
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = error.safeResearchMessage("Could not search Shamela."),
                                isError = true,
                            ),
                        ),
                    )
                }
            }
        }
        activeWorkingMessageId = workingId
        requestJob = job
        job.invokeOnCompletion {
            if (requestJob === job) {
                requestJob = null
                activeWorkingMessageId = null
            }
        }
    }

    fun cancelRequest() {
        val active = requestJob ?: return
        val workingId = activeWorkingMessageId
        active.cancel(CancellationException("Cancelled by user"))
        requestJob = null
        activeWorkingMessageId = null
        _uiState.update { state ->
            state.copy(
                isBusy = false,
                messages = state.messages.map { message ->
                    if (message.id == workingId) {
                        message.copy(text = "Request cancelled.", isWorking = false, isError = false)
                    } else {
                        message
                    }
                },
            )
        }
    }

    private suspend fun submitScholarComparison(question: String, workingId: String) {
        val provider = _uiState.value.selectedProvider
        val streamedAnswer = StringBuilder()
        var lastStreamUpdateMillis = 0L
        runCatching {
            groundedResearch.compareScholars(
                question = question,
                provider = provider,
                onStage = { stage ->
                    if (streamedAnswer.isEmpty()) {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.updateWorkingText(workingId, stage.label))
                        }
                    }
                },
                onDelta = { delta ->
                    streamedAnswer.append(delta)
                    val now = SystemClock.elapsedRealtime()
                    if (lastStreamUpdateMillis == 0L || now - lastStreamUpdateMillis >= StreamUiUpdateMillis) {
                        lastStreamUpdateMillis = now
                        val currentText = streamedAnswer.toString()
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.replaceMessage(
                                    workingId,
                                    AiResearchMessage(workingId, AiResearchMessageRole.Assistant, currentText),
                                ),
                            )
                        }
                    }
                },
            )
        }.onSuccess { result ->
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    messages = state.messages.replaceMessage(
                        workingId,
                        AiResearchMessage(
                            id = workingId,
                            role = AiResearchMessageRole.Assistant,
                            text = result.answer,
                            sources = result.sources,
                            providerModel = result.model,
                        ),
                    ),
                )
            }
        }.onFailure { error ->
            if (error is CancellationException) return@onFailure
            handleShamelaAuthorizationFailure(error)
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    messages = state.messages.replaceMessage(
                        workingId,
                        AiResearchMessage(
                            id = workingId,
                            role = AiResearchMessageRole.Assistant,
                            text = error.safeResearchMessage("Could not compare these scholars."),
                            isError = true,
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun submitQuoteVerification(quote: String, workingId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.updateWorkingText(workingId, "Verifying quotation…"))
        }
        runCatching { shamelaResearchProvider.verifyQuote(quote) }
            .onSuccess { result ->
                val detail = when (result.classification) {
                    QuoteVerificationClassification.Exact -> result.totalHits?.let { " in $it Shamela pages." }
                        ?: "."
                    QuoteVerificationClassification.Similar ->
                        ", but the exact consecutive wording was not found."
                    QuoteVerificationClassification.NotLocated ->
                        " in the searchable Shamela library."
                }
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = result.classification.label + detail,
                                sources = result.sources,
                            ),
                        ),
                    )
                }
            }
            .onFailure { error ->
                if (error is CancellationException) return@onFailure
                handleShamelaAuthorizationFailure(error)
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = error.safeResearchMessage("Could not verify this quotation."),
                                isError = true,
                            ),
                        ),
                    )
                }
            }
    }

    private suspend fun submitGroundedQuestion(question: String, workingId: String) {
        val provider = _uiState.value.selectedProvider
        val streamedAnswer = StringBuilder()
        var lastStreamUpdateMillis = 0L
        runCatching {
            groundedResearch.answer(
                question = question,
                provider = provider,
                onStage = { stage ->
                    if (streamedAnswer.isEmpty()) {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.updateWorkingText(workingId, stage.label),
                            )
                        }
                    }
                },
                onDelta = { delta ->
                    streamedAnswer.append(delta)
                    val now = SystemClock.elapsedRealtime()
                    if (lastStreamUpdateMillis == 0L || now - lastStreamUpdateMillis >= StreamUiUpdateMillis) {
                        lastStreamUpdateMillis = now
                        val currentText = streamedAnswer.toString()
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.replaceMessage(
                                    workingId,
                                    AiResearchMessage(
                                        id = workingId,
                                        role = AiResearchMessageRole.Assistant,
                                        text = currentText,
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        }.onSuccess { result ->
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    messages = state.messages.replaceMessage(
                        workingId,
                        AiResearchMessage(
                            id = workingId,
                            role = AiResearchMessageRole.Assistant,
                            text = result.answer,
                            sources = result.sources,
                            providerModel = result.model,
                        ),
                    ),
                )
            }
        }.onFailure { error ->
            if (error is CancellationException) return@onFailure
            handleShamelaAuthorizationFailure(error)
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    messages = state.messages.replaceMessage(
                        workingId,
                        AiResearchMessage(
                            id = workingId,
                            role = AiResearchMessageRole.Assistant,
                            text = error.safeResearchMessage("Could not generate a grounded answer."),
                            isError = true,
                        ),
                    ),
                )
            }
        }
    }

    private fun handleShamelaAuthorizationFailure(error: Throwable) {
        val mcpError = error as? com.myvault.app.data.ai.ShamelaMcpException ?: return
        if (mcpError.httpStatus == 401) {
            shamelaMcpClient.clearSession()
            _uiState.update { it.copy(shamelaMcpConnection = ShamelaMcpConnectionState.Idle) }
            shamelaAuthRepository.invalidateLocalSession("Shamela sign-in expired. Connect again.")
        }
    }

    private companion object {
        const val MaxComposerCharacters = 12_000
        const val MaxConversationMessages = 200
        const val StreamUiUpdateMillis = 50L
    }
}

internal fun Throwable.safeResearchMessage(fallback: String): String = when (this) {
    is com.myvault.app.data.ai.AiProviderException -> message ?: fallback
    is com.myvault.app.data.ai.ShamelaMcpException -> when (httpStatus) {
        401 -> "Shamela sign-in expired. Connect again."
        429 -> "Shamela is temporarily rate limited. Try again shortly."
        in 500..599 -> "Shamela is temporarily unavailable. Try again."
        else -> message ?: fallback
    }
    is java.net.SocketTimeoutException -> "The request timed out. Try again."
    is java.net.UnknownHostException -> "No network connection is available."
    else -> fallback
}

data class AiResearchUiState(
    val selectedProvider: AiResearchProvider = AiResearchProvider.ChatGpt,
    val selectedMode: AiResearchMode = AiResearchMode.Ask,
    val shamelaConnection: ShamelaConnectionState = ShamelaConnectionState.Disconnected,
    val shamelaMcpConnection: ShamelaMcpConnectionState = ShamelaMcpConnectionState.Idle,
    val composer: String = "",
    val messages: List<AiResearchMessage> = emptyList(),
    val isBusy: Boolean = false,
    val sourceDetail: SourceDetailState? = null,
    val sourceSave: SourceSaveState? = null,
    val noteOptions: List<ResearchNoteOption> = emptyList(),
    val notice: String? = null,
)

data class SourceSaveState(
    val source: ResearchSource,
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class ResearchNoteOption(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

enum class AiResearchMode(val label: String, val composerHint: String) {
    Ask("Ask", "Ask AI or search Shamela"),
    VerifyQuote("Verify quote", "Paste an Arabic quotation"),
    CompareScholars("Compare", "Name scholars and a topic"),
}

sealed interface SourceDetailState {
    val source: ResearchSource

    data class Loading(override val source: ResearchSource) : SourceDetailState
    data class Ready(val context: ResearchSourceContext) : SourceDetailState {
        override val source: ResearchSource = context.source
    }
    data class Error(override val source: ResearchSource, val message: String) : SourceDetailState
}

data class AiResearchMessage(
    val id: String,
    val role: AiResearchMessageRole,
    val text: String,
    val sources: List<ResearchSource> = emptyList(),
    val providerModel: String? = null,
    val isWorking: Boolean = false,
    val isError: Boolean = false,
)

enum class AiResearchMessageRole {
    User,
    Assistant,
}

private fun String.toShamelaQuery(): String {
    val prefixes = listOf(
        "Search Shamela for ",
        "Search Shamela: ",
        "Search for ",
        "ابحث في الشاملة عن ",
    )
    return prefixes.firstNotNullOfOrNull { prefix ->
        takeIf { startsWith(prefix, ignoreCase = true) }?.drop(prefix.length)?.trim()
    }.orEmpty().ifBlank { trim() }
}

internal fun String.isRawShamelaSearch(): Boolean = listOf(
    "Search Shamela for ",
    "Search Shamela: ",
    "Search for ",
    "ابحث في الشاملة عن ",
).any { startsWith(it, ignoreCase = true) }

private fun List<AiResearchMessage>.replaceMessage(
    id: String,
    replacement: AiResearchMessage,
): List<AiResearchMessage> = map { if (it.id == id) replacement else it }

private fun List<AiResearchMessage>.updateWorkingText(
    id: String,
    text: String,
): List<AiResearchMessage> = map { message ->
    if (message.id == id && message.isWorking) message.copy(text = text) else message
}
