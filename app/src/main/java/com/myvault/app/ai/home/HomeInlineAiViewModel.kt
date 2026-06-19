package com.myvault.app.ai.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class HomeInlineAiViewModel @Inject constructor(
    private val repository: HomeInlineAiRepository,
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

    fun openPanel() {
        refreshProviderState()
        refreshHistory()
        _state.update { it.copy(isPanelOpen = true, error = null, warning = null) }
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
            val attachedTitles = parseAttachedTitles(item.attachedTitles)
            _state.update {
                it.copy(
                    chatMessages = listOf(
                        HomeInlineAiMessage(
                            id = "${item.id}-user",
                            role = HomeInlineAiRole.User,
                            text = item.userQuery,
                            attachedTitles = attachedTitles,
                            timestamp = item.createdAt,
                        ),
                        HomeInlineAiMessage(
                            id = "${item.id}-assistant",
                            role = HomeInlineAiRole.Assistant,
                            text = item.assistantAnswer,
                            attachedTitles = attachedTitles,
                            timestamp = item.createdAt,
                        ),
                    ),
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
            val suggestions = repository.searchTitles(token)
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
    }

    fun detachItem(item: HomeAiAttachableItem) {
        _state.update { state ->
            state.copy(
                attachedItems = state.attachedItems.filterNot { it.id == item.id && it.type == item.type },
                error = null,
                warning = null,
            )
        }
    }

    fun openPicker() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    panelMode = HomeAiPanelMode.AttachNotes,
                    pickerItems = repository.pickerItems(),
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
        if (question.isBlank() || snapshot.isStreaming) return
        sendRequest(question = question, attachments = snapshot.attachedItems, clearInput = true)
    }

    fun retryLastRequest() {
        val snapshot = _state.value
        val question = snapshot.lastRequestQuestion.ifBlank {
            snapshot.chatMessages.lastOrNull { it.role == HomeInlineAiRole.User }?.text.orEmpty()
        }.trim()
        if (question.isBlank() || snapshot.isStreaming) return
        val attachments = snapshot.lastRequestAttachments
        sendRequest(question = question, attachments = attachments, clearInput = false)
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun sendRequest(
        question: String,
        attachments: List<HomeAiAttachableItem>,
        clearInput: Boolean,
    ) {
        val snapshot = _state.value

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

            val contexts = repository.loadContexts(attachments)
            val estimatedChars = repository.estimateContextChars(contexts)
            if (estimatedChars > SoftContextWarningChars) {
                _state.update { it.copy(warning = "Large attachment payload. Streaming may take longer.") }
            }

            var answer = ""
            runCatching {
                repository.streamAnswer(
                    question = question,
                    contexts = contexts,
                    provider = snapshot.selectedProvider,
                    modelMode = snapshot.selectedModelMode,
                ).collect { chunk ->
                    answer += chunk
                    _state.update { it.copy(currentStreamingAnswer = answer) }
                }
            }.onSuccess {
                val assistant = HomeInlineAiMessage(
                    id = UUID.randomUUID().toString(),
                    role = HomeInlineAiRole.Assistant,
                    text = answer.trim(),
                    attachedTitles = attachments.map { it.title },
                )
                repository.saveHistory(question, assistant.text, attachments, snapshot.resolvedModelId)
                refreshHistory()
                _state.update {
                    it.copy(
                        isStreaming = false,
                        currentStreamingAnswer = "",
                        chatMessages = it.chatMessages + assistant,
                        warning = null,
                    )
                }
            }.onFailure { error ->
                val aiError = (error as? HomeInlineAiException)?.aiError ?: HomeInlineAiError.Unknown(error.message.orEmpty())
                _state.update {
                    it.copy(
                        isStreaming = false,
                        currentStreamingAnswer = "",
                        error = aiError,
                    )
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(isStreaming = false, currentStreamingAnswer = "") }
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

    private companion object {
        const val MaxAttachments = 5
        const val SoftContextWarningChars = 45_000
    }
}
