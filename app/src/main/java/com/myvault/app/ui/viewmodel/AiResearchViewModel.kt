package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.myvault.app.data.ai.AiResearchLocalPreferences
import com.myvault.app.data.ai.AiResearchProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AiResearchViewModel @Inject constructor(
    private val localPreferences: AiResearchLocalPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AiResearchUiState(selectedProvider = localPreferences.selectedProvider()),
    )
    val uiState: StateFlow<AiResearchUiState> = _uiState.asStateFlow()

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
    val shamelaStatus: String = "Sign in",
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
