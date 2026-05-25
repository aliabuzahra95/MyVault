package com.myvault.app.data.narration

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationPlayerManager @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null
    private var activeFiles: List<File> = emptyList()
    private var expectedChunks: Int = 0
    private var activeChunkIndex: Int = 0
    private var activeSession: NarrationSession? = null
    private var requestCounter = 0L
    private var speed = 1f
    private var streamingGeneration = false
    private var waitingForNextChunk = false

    private val _state = MutableStateFlow(NarrationUiState())
    val state: StateFlow<NarrationUiState> = _state.asStateFlow()

    fun markPreparing(noteId: String, noteTitle: String) {
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Preparing,
            noteId = noteId,
            noteTitle = noteTitle,
            label = "Preparing narration...",
            speed = speed,
        )
    }

    fun markGenerating(noteId: String, noteTitle: String, current: Int, total: Int) {
        val status = _state.value.status
        if (status == NarrationPlaybackStatus.Playing || status == NarrationPlaybackStatus.Paused) {
            _state.update { it.copy(totalChunks = total.takeIf { value -> value > 0 } ?: it.totalChunks) }
            return
        }
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Generating,
            noteId = noteId,
            noteTitle = noteTitle,
            label = if (total > 1) "Generating narration $current of $total..." else "Generating narration...",
            speed = speed,
            currentChunk = current,
            totalChunks = total,
        )
    }

    fun play(session: NarrationSession) {
        stopInternal(resetState = FalseResetState)
        activeSession = session
        activeFiles = session.files
        expectedChunks = session.files.size
        streamingGeneration = false
        waitingForNextChunk = false
        activeChunkIndex = 0
        speed = session.speed
        if (activeFiles.isEmpty()) {
            showError(session.noteId, session.noteTitle, "Audio unavailable. Try again.")
            return
        }
        playChunk(0)
    }

    fun startStreaming(session: NarrationSession, totalChunks: Int) {
        stopInternal(resetState = FalseResetState)
        activeSession = session
        activeFiles = session.files
        expectedChunks = totalChunks.coerceAtLeast(session.files.size)
        streamingGeneration = true
        waitingForNextChunk = false
        activeChunkIndex = 0
        speed = session.speed
        if (activeFiles.isEmpty()) {
            markGenerating(session.noteId, session.noteTitle, 1, expectedChunks)
            return
        }
        playChunk(0)
    }

    fun appendStreamingChunk(session: NarrationSession, totalChunks: Int) {
        val current = activeSession
        if (current?.cacheKey != session.cacheKey) return
        activeSession = session
        activeFiles = session.files
        expectedChunks = totalChunks.coerceAtLeast(session.files.size)
        _state.update { state ->
            state.copy(
                totalChunks = expectedChunks,
                currentChunk = (activeChunkIndex + 1).coerceAtMost(expectedChunks),
                error = null,
            )
        }
        if (waitingForNextChunk && activeChunkIndex + 1 < activeFiles.size) {
            waitingForNextChunk = false
            playChunk(activeChunkIndex + 1)
        }
    }

    fun finishStreaming(session: NarrationSession) {
        if (activeSession?.cacheKey != session.cacheKey) return
        activeSession = session
        activeFiles = session.files
        expectedChunks = session.files.size
        streamingGeneration = false
        if (waitingForNextChunk && activeChunkIndex + 1 < activeFiles.size) {
            waitingForNextChunk = false
            playChunk(activeChunkIndex + 1)
        }
    }

    fun toggle() {
        when (_state.value.status) {
            NarrationPlaybackStatus.Playing -> pause()
            NarrationPlaybackStatus.Paused,
            NarrationPlaybackStatus.Stopped -> resume()
            else -> Unit
        }
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        updatePlaybackState(NarrationPlaybackStatus.Paused, "Paused")
    }

    fun resume() {
        mediaPlayer?.let {
            applyPlaybackSpeed(it)
            if (!it.isPlaying) it.start()
            updatePlaybackState(NarrationPlaybackStatus.Playing, "Playing")
            return
        }
        val session = activeSession ?: return
        if (activeFiles.isEmpty()) activeFiles = session.files
        activeChunkIndex = activeChunkIndex.coerceAtMost((activeFiles.size - 1).coerceAtLeast(0))
        playChunk(activeChunkIndex)
    }

    fun stop() {
        stopInternal(resetState = true)
    }

    fun stopForNote(noteId: String) {
        if (_state.value.noteId == noteId) stop()
    }

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.75f, 1.5f)
        mediaPlayer?.let(::applyPlaybackSpeed)
        _state.update { it.copy(speed = speed) }
    }

    fun showError(noteId: String?, noteTitle: String, message: String) {
        stopInternal(resetState = FalseResetState)
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Error,
            noteId = noteId,
            noteTitle = noteTitle,
            label = "Narration unavailable",
            error = message,
            speed = speed,
        )
    }

    private fun playChunk(index: Int) {
        val session = activeSession ?: return
        val file = activeFiles.getOrNull(index) ?: run {
            if (streamingGeneration) {
                waitingForNextChunk = true
                _state.value = _state.value.copy(
                    status = NarrationPlaybackStatus.Generating,
                    label = "Preparing next part...",
                    totalChunks = expectedChunks,
                )
            } else {
                stop()
            }
            return
        }
        requestCounter += 1
        val requestId = requestCounter
        activeChunkIndex = index
        val player = MediaPlayer()
        mediaPlayer = player
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Preparing,
            noteId = session.noteId,
            noteTitle = session.noteTitle,
            label = "Loading narration...",
            speed = speed,
            currentChunk = index + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
        )
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                if (!isCurrentRequest(it, requestId)) {
                    it.release()
                    return@setOnPreparedListener
                }
                applyPlaybackSpeed(it)
                it.start()
                updatePlaybackState(NarrationPlaybackStatus.Playing, "Playing")
            }
            player.setOnCompletionListener {
                if (!isCurrentRequest(it, requestId)) return@setOnCompletionListener
                releaseCurrentPlayer()
                if (activeChunkIndex + 1 < activeFiles.size) {
                    playChunk(activeChunkIndex + 1)
                } else if (streamingGeneration) {
                    waitingForNextChunk = true
                    _state.value = _state.value.copy(
                        status = NarrationPlaybackStatus.Generating,
                        label = "Preparing next part...",
                        totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
                    )
                } else {
                    _state.value = NarrationUiState(
                        status = NarrationPlaybackStatus.Stopped,
                        noteId = session.noteId,
                        noteTitle = session.noteTitle,
                        label = "Narration finished",
                        speed = speed,
                        currentChunk = activeFiles.size,
                        totalChunks = activeFiles.size,
                    )
                }
            }
            player.setOnErrorListener { _, _, _ ->
                showError(session.noteId, session.noteTitle, "Audio playback failed. Try again.")
                true
            }
            player.prepareAsync()
        }.onFailure {
            showError(session.noteId, session.noteTitle, "Audio playback failed. Try again.")
        }
    }

    private fun updatePlaybackState(status: NarrationPlaybackStatus, label: String) {
        val player = mediaPlayer
        val session = activeSession
        _state.value = _state.value.copy(
            status = status,
            noteId = session?.noteId ?: _state.value.noteId,
            noteTitle = session?.noteTitle ?: _state.value.noteTitle,
            label = label,
            error = null,
            speed = speed,
            currentChunk = activeChunkIndex + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
            currentPositionMs = player?.currentPosition?.toLong() ?: 0L,
            durationMs = player?.duration?.takeIf { it > 0 }?.toLong() ?: 0L,
        )
    }

    private fun applyPlaybackSpeed(player: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
        }
    }

    private fun isCurrentRequest(player: MediaPlayer, requestId: Long): Boolean = player === mediaPlayer && requestId == requestCounter

    private fun stopInternal(resetState: Boolean) {
        releaseCurrentPlayer()
        activeFiles = emptyList()
        expectedChunks = 0
        activeChunkIndex = 0
        activeSession = null
        streamingGeneration = false
        waitingForNextChunk = false
        if (resetState) _state.value = NarrationUiState(speed = speed)
    }

    private fun releaseCurrentPlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
    }

    private companion object {
        const val FalseResetState = false
    }
}
