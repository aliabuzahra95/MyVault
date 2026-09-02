package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.myvault.app.data.ai.AiResearchLocalPreferences
import com.myvault.app.data.ai.AiResearchProvider
import com.myvault.app.data.ai.ShamelaAuthRepository
import com.myvault.app.data.ai.ShamelaConnectionState
import com.myvault.app.data.ai.ShamelaMcpClient
import com.myvault.app.data.ai.ShamelaMcpConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Intent
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

    fun updateComposer(value: String) {
        _uiState.update { it.copy(composer = value.take(MaxComposerCharacters)) }
    }

    fun submitQuestion() {
        val question = _uiState.value.composer.trim()
        if (question.isBlank()) return
        _uiState.update { state ->
            state.copy(
                composer = "",
                messages = state.messages + listOf(
                    AiResearchMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiResearchMessageRole.User,
                        text = question,
                    ),
                    AiResearchMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiResearchMessageRole.Assistant,
                        text = "Shamela research will appear here after you connect the service.",
                    ),
                ),
            )
        }
    }

    private companion object {
        const val MaxComposerCharacters = 12_000
    }
}

data class AiResearchUiState(
    val selectedProvider: AiResearchProvider = AiResearchProvider.ChatGpt,
    val shamelaConnection: ShamelaConnectionState = ShamelaConnectionState.Disconnected,
    val shamelaMcpConnection: ShamelaMcpConnectionState = ShamelaMcpConnectionState.Idle,
    val composer: String = "",
    val messages: List<AiResearchMessage> = emptyList(),
)

data class AiResearchMessage(
    val id: String,
    val role: AiResearchMessageRole,
    val text: String,
)

enum class AiResearchMessageRole {
    User,
    Assistant,
}
