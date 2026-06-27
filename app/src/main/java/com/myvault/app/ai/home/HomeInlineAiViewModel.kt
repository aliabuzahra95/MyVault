package com.myvault.app.ai.home

import androidx.lifecycle.ViewModel
import com.myvault.app.data.narration.NarrationController
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class HomeInlineAiViewModel @Inject constructor(
    private val repository: HomeInlineAiRepository,
    private val narrationController: NarrationController,
) : ViewModel() {
    private val initialProvider = repository.providerStatuses()
        .firstOrNull { it.provider == HomeAiProvider.GEMINI && it.selectable }
        ?.provider
        ?: repository.providerStatuses().firstOrNull { it.selectable }?.provider
        ?: HomeAiProvider.GEMINI
    private val _state = MutableStateFlow(
        HomeInlineAiState(
            selectedProvider = initialProvider,
            selectedModelMode = HomeAiModelMode.FAST,
            resolvedModelId = repository.resolvedModelId(initialProvider, HomeAiModelMode.FAST),
            providerStatuses = repository.providerStatuses(),
            maskedKeyStatus = repository.maskedKeyLabel(initialProvider),
        ),
    )
    val state: StateFlow<HomeInlineAiState> = _state.asStateFlow()

    private var titleSearchJob: Job? = null
    private var streamJob: Job? = null
    private val pdfPreparationJobs = mutableMapOf<String, Job>()

    fun openPanel(
        scope: HomeAiAttachmentScope = HomeAiAttachmentScope.Notes,
        courseId: String? = null,
    ) {
        refreshProviderState()
        refreshHistory()
        _state.update {
            val scopeChanged = it.attachmentScope != scope || it.screenContextCourseId != courseId
            it.copy(
                isPanelOpen = true,
                attachmentScope = scope,
                screenContextCourseId = courseId,
                attachedItems = if (scopeChanged) emptyList() else it.attachedItems,
                suggestedTitles = emptyList(),
                pickerItems = emptyList(),
                error = null,
                warning = null,
            )
        }
    }

    fun closePanel() {
        stopStreaming()
        _state.update {
            it.copy(
                isPanelOpen = false,
                suggestedTitles = emptyList(),
                panelMode = HomeAiPanelMode.Chat,
            )
        }
    }

    fun toggleSettingsMode() {
        refreshProviderState()
        refreshHistory()
        _state.update {
            it.copy(
                panelMode = if (it.panelMode == HomeAiPanelMode.Settings) HomeAiPanelMode.Chat else HomeAiPanelMode.Settings,
                suggestedTitles = emptyList(),
                error = null,
            )
        }
    }

    fun showChatMode() {
        _state.update { it.copy(panelMode = HomeAiPanelMode.Chat, suggestedTitles = emptyList()) }
    }

    fun clearHistory() {
        stopStreaming()
        _state.update {
            it.copy(
                chatMessages = emptyList(),
                activeThreadId = null,
                currentStreamingAnswer = "",
                chatInputText = "",
                attachedItems = emptyList(),
                suggestedTitles = emptyList(),
                panelMode = HomeAiPanelMode.Chat,
                error = null,
                warning = "Started a new chat. Saved history is still available in Settings.",
            )
        }
    }

    fun openHistoryItem(id: String) {
        viewModelScope.launch {
            val item = repository.historyById(id) ?: return@launch
            val messages = repository.messagesForHistory(item)
            _state.update {
                it.copy(
                    chatMessages = messages,
                    activeThreadId = item.id,
                    chatInputText = "",
                    currentStreamingAnswer = "",
                    isStreaming = false,
                    panelMode = HomeAiPanelMode.Chat,
                    error = null,
                    warning = null,
                )
            }
        }
    }

    fun setInput(text: String) {
        _state.update { it.copy(chatInputText = text, warning = null, error = null) }
        titleSearchJob?.cancel()
        titleSearchJob = viewModelScope.launch {
            delay(90)
            val token = trailingAttachmentToken(text)
            if (token == null) {
                _state.update { it.copy(suggestedTitles = emptyList()) }
                return@launch
            }
            val suggestions = repository.searchTitles(token, _state.value.attachmentScope, _state.value.screenContextCourseId)
                .filterNot { candidate -> _state.value.attachedItems.any { it.id == candidate.id && it.type == candidate.type } }
            _state.update { it.copy(suggestedTitles = suggestions) }
        }
    }

    fun setProvider(provider: HomeAiProvider) {
        val status = repository.providerStatuses().firstOrNull { it.provider == provider }
        if (status?.selectable != true) {
            _state.update {
                it.copy(
                    providerStatuses = repository.providerStatuses(),
                    warning = "${provider.label} is not configured.",
                    error = null,
                )
            }
            return
        }
        _state.update {
            it.copy(
                selectedProvider = provider,
                resolvedModelId = repository.resolvedModelId(provider, it.selectedModelMode),
                providerStatuses = repository.providerStatuses(),
                maskedKeyStatus = repository.maskedKeyLabel(provider),
                error = null,
                warning = null,
            )
        }
        if (provider == HomeAiProvider.GEMINI) {
            _state.value.attachedItems.forEach(::preparePdfAfterAttach)
        }
    }

    fun setModelMode(modelMode: HomeAiModelMode) {
        _state.update {
            it.copy(
                selectedModelMode = modelMode,
                resolvedModelId = repository.resolvedModelId(it.selectedProvider, modelMode),
                error = null,
                warning = null,
            )
        }
    }

    fun toggleWebSearch() {
        if (_state.value.isStreaming || _state.value.isPreparingAttachments) return
        _state.update {
            it.copy(
                webSearchEnabled = !it.webSearchEnabled,
                warning = null,
                error = null,
            )
        }
    }

    fun attachSuggestion(item: HomeAiAttachableItem) {
        attachItem(item)
        _state.update {
            it.copy(
                chatInputText = stripTrailingAttachmentToken(it.chatInputText),
                suggestedTitles = emptyList(),
            )
        }
    }

    fun attachItem(item: HomeAiAttachableItem) {
        val current = _state.value.attachedItems
        if (current.any { it.id == item.id && it.type == item.type }) return
        if (current.size >= MaxAttachments) {
            _state.update { it.copy(warning = "Maximum 5 attachments. Remove one before adding another.") }
            return
        }
        _state.update { it.copy(attachedItems = it.attachedItems + item, warning = null, error = null) }
        preparePdfAfterAttach(item)
    }

    fun detachItem(item: HomeAiAttachableItem) {
        pdfPreparationJobs.remove(item.id)?.cancel()
        _state.update { state ->
            state.copy(
                attachedItems = state.attachedItems.filterNot { it.id == item.id && it.type == item.type },
                error = null,
                warning = null,
                isPreparingAttachments = pdfPreparationJobs.isNotEmpty(),
            )
        }
    }

    fun openPicker() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    panelMode = HomeAiPanelMode.AttachNotes,
                    pickerItems = repository.pickerItems(_state.value.attachmentScope, _state.value.screenContextCourseId),
                    suggestedTitles = emptyList(),
                    error = null,
                )
            }
        }
    }

    fun closePicker() {
        showChatMode()
    }

    fun send() {
        val snapshot = _state.value
        val question = snapshot.chatInputText.trim()
        if (question.isBlank() || snapshot.isStreaming || snapshot.isPreparingAttachments) return
        sendRequest(question = question, attachments = snapshot.attachedItems, clearInput = true)
    }

    fun retryLastRequest() {
        val snapshot = _state.value
        val question = snapshot.lastRequestQuestion.ifBlank {
            snapshot.chatMessages.lastOrNull { it.role == HomeInlineAiRole.User }?.text.orEmpty()
        }.trim()
        if (question.isBlank() || snapshot.isStreaming || snapshot.isPreparingAttachments) return
        val attachments = snapshot.lastRequestAttachments
        sendRequest(question = question, attachments = attachments, clearInput = false)
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun speakAnswer(messageId: String, text: String) {
        val cleanText = text.cleanForNarration()
        if (cleanText.isBlank()) {
            _state.update { it.copy(warning = "This answer has no text to read aloud.") }
            return
        }
        narrationController.startAzure(
            noteId = "ask-ai:$messageId",
            title = "AI answer",
            body = cleanText,
        )
    }

    private fun sendRequest(
        question: String,
        attachments: List<HomeAiAttachableItem>,
        clearInput: Boolean,
    ) {
        val snapshot = _state.value
        val priorMessages = snapshot.chatMessages

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val userMessage = HomeInlineAiMessage(
                id = UUID.randomUUID().toString(),
                role = HomeInlineAiRole.User,
                text = question,
                attachedTitles = attachments.map { it.title },
            )
            _state.update {
                it.copy(
                    chatMessages = it.chatMessages + userMessage,
                    chatInputText = if (clearInput) "" else it.chatInputText,
                    suggestedTitles = emptyList(),
                    isStreaming = true,
                    currentStreamingAnswer = "",
                    error = null,
                    warning = null,
                    lastRequestQuestion = question,
                    lastRequestAttachments = attachments,
                )
            }

            var answer = ""
            runCatching {
                val contexts = repository.loadScreenContexts(snapshot.attachmentScope, snapshot.screenContextCourseId) + repository.loadContexts(attachments)
                val estimatedChars = repository.estimateContextChars(contexts)
                if (estimatedChars > SoftContextWarningChars) {
                    _state.update { it.copy(warning = "Large attachment payload. Streaming may take longer.") }
                }
                val needsGeminiPdfUpload = snapshot.selectedProvider == HomeAiProvider.GEMINI &&
                    attachments.any { it.type == HomeAiAttachableType.Pdf }
                val geminiFiles = if (needsGeminiPdfUpload) {
                    _state.update { it.copy(isPreparingAttachments = true) }
                    repository.prepareGeminiPdfFiles(attachments)
                } else {
                    emptyList()
                }
                _state.update { it.copy(isPreparingAttachments = false) }
                try {
                    answer = collectStreamingAnswer(
                        repository.streamAnswer(
                            question = question,
                            contexts = contexts,
                            provider = snapshot.selectedProvider,
                            modelMode = snapshot.selectedModelMode,
                            webSearchEnabled = snapshot.webSearchEnabled,
                            files = geminiFiles,
                            conversationMessages = priorMessages,
                        ),
                    )
                } catch (error: HomeInlineAiException) {
                    if (!needsGeminiPdfUpload || !repository.isLikelyStaleGeminiFileError(error.aiError)) {
                        throw error
                    }
                    answer = ""
                    _state.update {
                        it.copy(
                            currentStreamingAnswer = "",
                            isPreparingAttachments = true,
                            warning = "Refreshing PDF link...",
                        )
                    }
                    repository.clearGeminiPdfFileCache(attachments)
                    val freshGeminiFiles = repository.prepareGeminiPdfFiles(attachments, forceUpload = true)
                    _state.update { it.copy(isPreparingAttachments = false) }
                    answer = collectStreamingAnswer(
                        repository.streamAnswer(
                            question = question,
                            contexts = contexts,
                            provider = snapshot.selectedProvider,
                            modelMode = snapshot.selectedModelMode,
                            webSearchEnabled = snapshot.webSearchEnabled,
                            files = freshGeminiFiles,
                            conversationMessages = priorMessages,
                        ),
                    )
                }
            }.onSuccess {
                val assistant = HomeInlineAiMessage(
                    id = UUID.randomUUID().toString(),
                    role = HomeInlineAiRole.Assistant,
                    text = answer.trim(),
                    attachedTitles = attachments.map { it.title },
                )
                val savedThreadId = repository.saveThread(
                    threadId = snapshot.activeThreadId,
                    messages = priorMessages + userMessage + assistant,
                    modelId = snapshot.resolvedModelId,
                )
                refreshHistory()
                _state.update {
                    it.copy(
                        isStreaming = false,
                        isPreparingAttachments = false,
                        currentStreamingAnswer = "",
                        chatMessages = it.chatMessages + assistant,
                        activeThreadId = savedThreadId,
                        warning = null,
                    )
                }
            }.onFailure { error ->
                val aiError = (error as? HomeInlineAiException)?.aiError ?: HomeInlineAiError.Unknown(error.message.orEmpty())
                _state.update {
                    it.copy(
                        isStreaming = false,
                        isPreparingAttachments = false,
                        currentStreamingAnswer = "",
                        error = aiError,
                    )
                }
            }
        }
    }

    private suspend fun collectStreamingAnswer(stream: Flow<String>): String {
        val buffer = StringBuilder()
        var lastRenderedLength = 0
        var lastRenderedAt = 0L

        stream.collect { chunk ->
            buffer.append(chunk)
            val now = System.currentTimeMillis()
            val shouldRender = lastRenderedLength == 0 ||
                now - lastRenderedAt >= StreamingRenderIntervalMs ||
                buffer.length - lastRenderedLength >= StreamingRenderCharDelta
            if (shouldRender) {
                lastRenderedLength = buffer.length
                lastRenderedAt = now
                _state.update { it.copy(currentStreamingAnswer = buffer.toString()) }
            }
        }

        if (buffer.length != lastRenderedLength) {
            _state.update { it.copy(currentStreamingAnswer = buffer.toString()) }
        }
        return buffer.toString()
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(isStreaming = false, isPreparingAttachments = false, currentStreamingAnswer = "") }
    }

    private fun preparePdfAfterAttach(item: HomeAiAttachableItem) {
        val snapshot = _state.value
        if (
            item.type != HomeAiAttachableType.Pdf ||
            snapshot.attachmentScope != HomeAiAttachmentScope.LibraryPdfs ||
            snapshot.selectedProvider != HomeAiProvider.GEMINI ||
            pdfPreparationJobs.containsKey(item.id)
        ) {
            return
        }
        val job = viewModelScope.launch {
            _state.update { it.copy(isPreparingAttachments = true, error = null) }
            runCatching {
                repository.prepareGeminiPdfFiles(listOf(item))
            }.onFailure { error ->
                val aiError = (error as? HomeInlineAiException)?.aiError
                    ?: HomeInlineAiError.Unknown(error.message.orEmpty())
                _state.update {
                    it.copy(
                        error = aiError,
                        warning = "PDF preparation failed. MyVault will try again when you ask.",
                    )
                }
            }
            pdfPreparationJobs.remove(item.id)
            _state.update { it.copy(isPreparingAttachments = pdfPreparationJobs.isNotEmpty()) }
        }
        pdfPreparationJobs[item.id] = job
    }

    private fun trailingAttachmentToken(text: String): String? {
        val at = text.lastIndexOf('@')
        if (at < 0) return null
        val token = text.substring(at + 1)
        if (token.any { it.isWhitespace() }) return null
        return token.takeIf { it.length >= 2 }
    }

    private fun stripTrailingAttachmentToken(text: String): String {
        val at = text.lastIndexOf('@')
        if (at < 0) return text
        val token = text.substring(at + 1)
        if (token.any { it.isWhitespace() }) return text
        return text.removeRange(at, text.length).trimEnd()
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            _state.update { it.copy(historyItems = repository.recentHistory()) }
        }
    }

    private fun parseAttachedTitles(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun refreshProviderState() {
        val statuses = repository.providerStatuses()
        _state.update { state ->
            val selectedStillAvailable = statuses.any { it.provider == state.selectedProvider && it.selectable }
            val selectedProvider = if (selectedStillAvailable) {
                state.selectedProvider
            } else {
                statuses.firstOrNull { it.provider == HomeAiProvider.GEMINI && it.selectable }?.provider
                    ?: statuses.firstOrNull { it.selectable }?.provider
                    ?: state.selectedProvider
            }
            state.copy(
                selectedProvider = selectedProvider,
                providerStatuses = statuses,
                resolvedModelId = repository.resolvedModelId(selectedProvider, state.selectedModelMode),
                maskedKeyStatus = repository.maskedKeyLabel(selectedProvider),
            )
        }
    }


    private fun String.cleanForNarration(): String =
        replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("__", "")
            .replace("*", "")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        const val MaxAttachments = 5
        const val SoftContextWarningChars = 45_000
        const val StreamingRenderIntervalMs = 45L
        const val StreamingRenderCharDelta = 180
    }
}
