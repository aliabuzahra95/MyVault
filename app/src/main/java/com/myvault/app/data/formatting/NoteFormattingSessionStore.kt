package com.myvault.app.data.formatting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NoteFormattingUiState(
    val loading: Boolean = false,
    val action: NoteFormattingAction? = null,
    val provider: NoteFormattingProvider = NoteFormattingProvider.ChatGPT,
    val model: NoteFormattingModel = NoteFormattingModel.Fast,
    val result: String = "",
    val error: String? = null,
    val progressLabel: String? = null,
)

@Singleton
class NoteFormattingSessionStore @Inject constructor() {
    private val states = mutableMapOf<String, MutableStateFlow<NoteFormattingUiState>>()

    fun stateFor(noteId: String): MutableStateFlow<NoteFormattingUiState> =
        states.getOrPut(noteId) { MutableStateFlow(NoteFormattingUiState()) }

    fun observe(noteId: String): StateFlow<NoteFormattingUiState> = stateFor(noteId)
}
