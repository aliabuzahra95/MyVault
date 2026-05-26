package com.myvault.app.data.narration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationController @Inject constructor(
    private val ttsRepository: TtsRepository,
    private val textPreparer: NoteNarrationTextPreparer,
    private val playerManager: NarrationPlayerManager,
) {
    val state: StateFlow<NarrationUiState> = playerManager.state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generationJob: Job? = null
    private var lastRequest: NarrationRequest? = null

    fun start(noteId: String, title: String, body: String, voice: String = NarrationConfig.DEFAULT_VOICE) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        val normalizedVoice = voice.ifBlank { NarrationConfig.DEFAULT_VOICE }
        val current = state.value
        if (current.noteId == noteId && current.voice.equals(normalizedVoice, ignoreCase = true)) {
            when (current.status) {
                NarrationPlaybackStatus.Playing,
                NarrationPlaybackStatus.Paused,
                NarrationPlaybackStatus.Stopped -> {
                    playerManager.toggle()
                    return
                }
                NarrationPlaybackStatus.Preparing,
                NarrationPlaybackStatus.Generating -> return
                else -> Unit
            }
        }
        if (generationJob?.isActive == true) return

        val request = NarrationRequest(noteId, noteTitle, body, normalizedVoice)
        lastRequest = request
        generationJob?.cancel()
        generationJob = scope.launch {
            val narrationText = textPreparer.prepare(request.title, request.body)
            if (narrationText.isBlank()) {
                playerManager.showError(request.noteId, request.title, "This note is empty.")
                return@launch
            }
            playerManager.markPreparing(request.noteId, request.title, request.voice)
            var playbackStarted = false
            runCatching {
                ttsRepository.generateNarrationProgressively(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    narrationText = narrationText,
                    voice = request.voice,
                    speed = state.value.speed,
                    onChunkGenerating = { currentChunk, totalChunks ->
                        playerManager.markGenerating(request.noteId, request.title, currentChunk, totalChunks, request.voice)
                    },
                    onChunkReady = { session, isComplete, totalChunks ->
                        if (!playbackStarted) {
                            playbackStarted = true
                            playerManager.startStreaming(session, totalChunks = totalChunks)
                        } else {
                            playerManager.appendStreamingChunk(session, totalChunks = totalChunks)
                        }
                        if (isComplete) playerManager.finishStreaming(session)
                    },
                )
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                playerManager.showError(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    message = error.message?.takeIf { it.isNotBlank() } ?: "Couldn’t generate narration.",
                )
            }
        }
    }

    fun startDevice(noteId: String, title: String, body: String) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        val current = state.value
        if (current.noteId == noteId && current.voice == DeviceNarrationVoice) {
            when (current.status) {
                NarrationPlaybackStatus.Playing,
                NarrationPlaybackStatus.Paused,
                NarrationPlaybackStatus.Stopped -> {
                    playerManager.toggle()
                    return
                }
                NarrationPlaybackStatus.Preparing,
                NarrationPlaybackStatus.Generating -> return
                else -> Unit
            }
        }
        generationJob?.cancel()
        generationJob = null
        val narrationText = textPreparer.prepare(noteTitle, body)
        if (narrationText.isBlank()) {
            playerManager.showError(noteId, noteTitle, "This note is empty.")
            return
        }
        lastRequest = NarrationRequest(noteId, noteTitle, body, NarrationConfig.DEFAULT_VOICE)
        playerManager.playDevice(noteId, noteTitle, narrationText)
    }

    fun restartWithVoice(voice: String) {
        val request = lastRequest ?: return
        stop(resetLastRequest = false)
        start(request.noteId, request.title, request.body, voice)
    }

    fun toggle() {
        playerManager.toggle()
    }

    fun stop(resetLastRequest: Boolean = true) {
        generationJob?.cancel()
        generationJob = null
        playerManager.stop()
        if (resetLastRequest) lastRequest = null
    }

    fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun refreshProgress() {
        playerManager.refreshProgress()
    }

    private data class NarrationRequest(
        val noteId: String,
        val title: String,
        val body: String,
        val voice: String,
    )

    private companion object {
        const val DeviceNarrationVoice = "device"
    }
}
