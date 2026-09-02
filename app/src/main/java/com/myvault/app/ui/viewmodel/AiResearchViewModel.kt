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
import kotlinx.coroutines.launch

@HiltViewModel
class AiResearchViewModel @Inject constructor(
    private val localPreferences: AiResearchLocalPreferences,
    private val shamelaAuthRepository: ShamelaAuthRepository,
    private val shamelaMcpClient: ShamelaMcpClient,
    private val shamelaResearchProvider: ShamelaResearchProvider,
    private val groundedResearch: GroundedResearchOrchestrator,
) : ViewModel() {
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
    }

    fun createShamelaAuthorizationIntent(): Result<Intent> = runCatching {
        shamelaAuthRepository.createAuthorizationIntent()
    }.onFailure { error ->
        _uiState.update {
            it.copy(shamelaConnection = ShamelaConnectionState.Error(error.message ?: "Unable to open Shamela sign-in."))
        }
    }

    fun completeShamelaAuthorization(data: Intent?) {
        viewModelScope.launch { shamelaAuthRepository.completeAuthorization(data) }
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
                    _uiState.update {
                        it.copy(
                            shamelaMcpConnection = ShamelaMcpConnectionState.Error(
                                error.message ?: "Could not initialize Shamela research.",
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
                    _uiState.update {
                        it.copy(
                            sourceDetail = SourceDetailState.Error(
                                source = source,
                                message = error.message ?: "Could not open this Shamela source.",
                            ),
                        )
                    }
                }
        }
    }

    fun closeSource() {
        _uiState.update { it.copy(sourceDetail = null) }
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
                    messages = state.messages + userMessage + AiResearchMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiResearchMessageRole.Assistant,
                        text = "Connect Shamela before searching verified sources.",
                        isError = true,
                    ),
                )
            }
            return
        }
        val workingId = UUID.randomUUID().toString()
        _uiState.update { state ->
            state.copy(
                composer = "",
                isBusy = true,
                messages = state.messages + listOf(
                    userMessage,
                    AiResearchMessage(
                        id = workingId,
                        role = AiResearchMessageRole.Assistant,
                        text = "Searching Shamela…",
                        isWorking = true,
                    ),
                ),
            )
        }
        viewModelScope.launch {
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
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = error.message ?: "Could not search Shamela.",
                                isError = true,
                            ),
                        ),
                    )
                }
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
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        messages = state.messages.replaceMessage(
                            workingId,
                            AiResearchMessage(
                                id = workingId,
                                role = AiResearchMessageRole.Assistant,
                                text = error.message ?: "Could not verify this quotation.",
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
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    messages = state.messages.replaceMessage(
                        workingId,
                        AiResearchMessage(
                            id = workingId,
                            role = AiResearchMessageRole.Assistant,
                            text = error.message ?: "Could not generate a grounded answer.",
                            isError = true,
                        ),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MaxComposerCharacters = 12_000
        const val StreamUiUpdateMillis = 50L
    }
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
)

enum class AiResearchMode(val label: String, val composerHint: String) {
    Ask("Ask", "Ask AI or search Shamela"),
    VerifyQuote("Verify quote", "Paste an Arabic quotation"),
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
