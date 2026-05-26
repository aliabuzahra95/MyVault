package com.myvault.app.data.narration

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationPlayerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var deviceTtsReady = false
    private var deviceChunks: List<String> = emptyList()
    private var deviceChunkIndex = 0
    private var deviceNoteId: String? = null
    private var deviceNoteTitle: String = ""
    private var devicePaused = false
    private var deviceRequestId = 0L
    private var activeFiles: List<File> = emptyList()
    private var activeDurationsMs: List<Long> = emptyList()
    private var expectedChunks: Int = 0
    private var activeChunkIndex: Int = 0
    private var activeSession: NarrationSession? = null
    private var requestCounter = 0L
    private var speed = 1f
    private var streamingGeneration = false
    private var waitingForNextChunk = false
    private var pendingSeekMs: Long? = null

    private val _state = MutableStateFlow(NarrationUiState())
    val state: StateFlow<NarrationUiState> = _state.asStateFlow()

    fun playDevice(noteId: String, noteTitle: String, narrationText: String) {
        stopInternal(resetState = FalseResetState)
        releaseDeviceTts(stopOnly = true)
        val chunks = narrationText.splitForDeviceTts()
        if (chunks.isEmpty()) {
            showError(noteId, noteTitle, "This note is empty.")
            return
        }
        deviceChunks = chunks
        deviceChunkIndex = 0
        deviceNoteId = noteId
        deviceNoteTitle = noteTitle
        devicePaused = false
        deviceRequestId += 1
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Preparing,
            noteId = noteId,
            noteTitle = noteTitle,
            label = "Preparing device voice...",
            speed = speed,
            voice = DeviceNarrationVoice,
            currentChunk = 1,
            totalChunks = chunks.size,
        )
        ensureDeviceTts { ready ->
            if (!ready) {
                showError(noteId, noteTitle, "Device voice is unavailable. Check your phone's text-to-speech settings.")
                return@ensureDeviceTts
            }
            speakDeviceChunk(0, deviceRequestId)
        }
    }

    fun markPreparing(noteId: String, noteTitle: String, voice: String = NarrationConfig.DEFAULT_VOICE) {
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Preparing,
            noteId = noteId,
            noteTitle = noteTitle,
            label = "Preparing narration...",
            speed = speed,
            voice = voice,
        )
    }

    fun markGenerating(noteId: String, noteTitle: String, current: Int, total: Int, voice: String = NarrationConfig.DEFAULT_VOICE) {
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
            voice = voice,
            currentChunk = current,
            totalChunks = total,
        )
    }

    fun play(session: NarrationSession) {
        stopInternal(resetState = FalseResetState)
        activeSession = session
        activeFiles = session.files
        activeDurationsMs = session.files.map(::readDurationMs)
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
        activeDurationsMs = session.files.map(::readDurationMs)
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
        activeDurationsMs = session.files.map(::readDurationMs)
        expectedChunks = totalChunks.coerceAtLeast(session.files.size)
        _state.update { state ->
            state.copy(
                totalChunks = expectedChunks,
                currentChunk = (activeChunkIndex + 1).coerceAtMost(expectedChunks),
                totalPositionMs = pendingSeekMs ?: globalPositionMs(),
                totalDurationMs = estimatedTotalDurationMs(),
                error = null,
            )
        }
        resumePendingSeekIfReady()
        if (waitingForNextChunk && pendingSeekMs == null && activeChunkIndex + 1 < activeFiles.size) {
            waitingForNextChunk = false
            playChunk(activeChunkIndex + 1)
        }
    }

    fun finishStreaming(session: NarrationSession) {
        if (activeSession?.cacheKey != session.cacheKey) return
        activeSession = session
        activeFiles = session.files
        activeDurationsMs = session.files.map(::readDurationMs)
        expectedChunks = session.files.size
        streamingGeneration = false
        updatePlaybackState(_state.value.status, _state.value.label.ifBlank { "Playing" })
        resumePendingSeekIfReady(force = true)
        if (waitingForNextChunk && pendingSeekMs == null && activeChunkIndex + 1 < activeFiles.size) {
            waitingForNextChunk = false
            playChunk(activeChunkIndex + 1)
        }
    }

    fun toggle() {
        if (_state.value.voice == DeviceNarrationVoice) {
            when (_state.value.status) {
                NarrationPlaybackStatus.Playing -> pauseDevice()
                NarrationPlaybackStatus.Paused,
                NarrationPlaybackStatus.Stopped -> resumeDevice()
                else -> Unit
            }
            return
        }
        when (_state.value.status) {
            NarrationPlaybackStatus.Playing -> pause()
            NarrationPlaybackStatus.Paused,
            NarrationPlaybackStatus.Stopped -> resume()
            else -> Unit
        }
    }

    fun pause() {
        if (_state.value.voice == DeviceNarrationVoice) {
            pauseDevice()
            return
        }
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        updatePlaybackState(NarrationPlaybackStatus.Paused, "Paused")
    }

    fun resume() {
        if (_state.value.voice == DeviceNarrationVoice) {
            resumeDevice()
            return
        }
        mediaPlayer?.let {
            applyPlaybackSpeed(it)
            if (!it.isPlaying) it.start()
            updatePlaybackState(NarrationPlaybackStatus.Playing, "Playing")
            return
        }
        val session = activeSession ?: return
        if (activeFiles.isEmpty()) activeFiles = session.files
        if (activeDurationsMs.isEmpty()) activeDurationsMs = activeFiles.map(::readDurationMs)
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
        textToSpeech?.setSpeechRate(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun seekTo(totalPositionMs: Long) {
        if (activeFiles.isEmpty()) return
        if (activeDurationsMs.size != activeFiles.size) activeDurationsMs = activeFiles.map(::readDurationMs)
        val generatedDuration = activeDurationsMs.sum().takeIf { it > 0L } ?: return
        val estimatedDuration = estimatedTotalDurationMs().takeIf { it > 0L } ?: generatedDuration
        val target = totalPositionMs.coerceIn(0L, (estimatedDuration - 250L).coerceAtLeast(0L))
        if (target >= generatedDuration && streamingGeneration) {
            pendingSeekMs = target
            waitingForNextChunk = true
            releaseCurrentPlayer()
            _state.value = _state.value.copy(
                status = NarrationPlaybackStatus.Generating,
                label = "Loading selected position...",
                totalPositionMs = target,
                totalDurationMs = estimatedDuration,
                totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
                error = null,
            )
            return
        }
        pendingSeekMs = null
        seekWithinGenerated(target.coerceAtMost((generatedDuration - 250L).coerceAtLeast(0L)))
    }

    fun refreshProgress() {
        val status = _state.value.status
        if (status == NarrationPlaybackStatus.Playing || status == NarrationPlaybackStatus.Paused || status == NarrationPlaybackStatus.Preparing) {
            updatePlaybackState(status, _state.value.label)
        }
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

    private fun ensureDeviceTts(onReady: (Boolean) -> Unit) {
        if (deviceTtsReady && textToSpeech != null) {
            onReady(true)
            return
        }
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            deviceTtsReady = status == TextToSpeech.SUCCESS
            val engine = textToSpeech
            if (deviceTtsReady && engine != null) {
                engine.language = Locale.getDefault()
                engine.setSpeechRate(speed)
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (!utteranceId.isCurrentDeviceUtterance()) return
                            updateDeviceState(NarrationPlaybackStatus.Playing, "Reading with device voice")
                        }

                        override fun onDone(utteranceId: String?) {
                            if (!utteranceId.isCurrentDeviceUtterance()) return
                            if (devicePaused) return
                            val next = deviceChunkIndex + 1
                            if (next < deviceChunks.size) {
                                speakDeviceChunk(next, deviceRequestId)
                            } else {
                                _state.value = NarrationUiState(
                                    status = NarrationPlaybackStatus.Stopped,
                                    noteId = deviceNoteId,
                                    noteTitle = deviceNoteTitle,
                                    label = "Device reading finished",
                                    speed = speed,
                                    voice = DeviceNarrationVoice,
                                    currentChunk = deviceChunks.size,
                                    totalChunks = deviceChunks.size,
                                )
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onError(utteranceId, TextToSpeech.ERROR)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (!utteranceId.isCurrentDeviceUtterance()) return
                            showError(deviceNoteId, deviceNoteTitle, "Device voice failed. Try again.")
                        }
                    },
                )
            }
            onReady(deviceTtsReady)
        }
    }

    private fun speakDeviceChunk(index: Int, requestId: Long) {
        val engine = textToSpeech ?: return
        val text = deviceChunks.getOrNull(index) ?: return
        deviceChunkIndex = index
        devicePaused = false
        updateDeviceState(NarrationPlaybackStatus.Preparing, "Loading device voice...")
        val utteranceId = deviceUtteranceId(requestId, index)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            showError(deviceNoteId, deviceNoteTitle, "Device voice failed. Try again.")
        }
    }

    private fun pauseDevice() {
        devicePaused = true
        textToSpeech?.stop()
        updateDeviceState(NarrationPlaybackStatus.Paused, "Paused")
    }

    private fun resumeDevice() {
        if (deviceChunks.isEmpty()) return
        speakDeviceChunk(deviceChunkIndex.coerceIn(0, deviceChunks.lastIndex), deviceRequestId)
    }

    private fun updateDeviceState(status: NarrationPlaybackStatus, label: String) {
        _state.value = _state.value.copy(
            status = status,
            noteId = deviceNoteId,
            noteTitle = deviceNoteTitle,
            label = label,
            error = null,
            speed = speed,
            voice = DeviceNarrationVoice,
            currentChunk = (deviceChunkIndex + 1).coerceAtLeast(1),
            totalChunks = deviceChunks.size,
            currentPositionMs = 0L,
            durationMs = 0L,
            totalPositionMs = 0L,
            totalDurationMs = 0L,
        )
    }

    private fun String?.isCurrentDeviceUtterance(): Boolean =
        this?.startsWith("device-$deviceRequestId-") == true

    private fun deviceUtteranceId(requestId: Long, index: Int): String = "device-$requestId-$index"

    private fun playChunk(index: Int, startPositionMs: Long = 0L) {
        val session = activeSession ?: return
        val file = activeFiles.getOrNull(index) ?: run {
            if (streamingGeneration) {
                waitingForNextChunk = true
                _state.value = _state.value.copy(
                    status = NarrationPlaybackStatus.Generating,
                    label = "Preparing next part...",
                    totalChunks = expectedChunks,
                    totalPositionMs = pendingSeekMs ?: globalPositionMs(),
                    totalDurationMs = estimatedTotalDurationMs(),
                )
            } else {
                stop()
            }
            return
        }
        releaseCurrentPlayer()
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
            voice = session.voice,
            currentChunk = index + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
            totalPositionMs = pendingSeekMs ?: globalPositionMs(startPositionMs),
            totalDurationMs = estimatedTotalDurationMs(),
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
                if (startPositionMs > 0L) seekPlayer(it, startPositionMs)
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
                        totalPositionMs = pendingSeekMs ?: globalPositionMs(),
                        totalDurationMs = estimatedTotalDurationMs(),
                    )
                } else {
                    _state.value = NarrationUiState(
                        status = NarrationPlaybackStatus.Stopped,
                        noteId = session.noteId,
                        noteTitle = session.noteTitle,
                        label = "Narration finished",
                        speed = speed,
                        voice = session.voice,
                        currentChunk = activeFiles.size,
                        totalChunks = activeFiles.size,
                        totalPositionMs = activeDurationsMs.sum(),
                        totalDurationMs = activeDurationsMs.sum(),
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
        val chunkPosition = player?.currentPosition?.toLong() ?: 0L
        val chunkDuration = player?.duration?.takeIf { it > 0 }?.toLong()
            ?: activeDurationsMs.getOrNull(activeChunkIndex)
            ?: 0L
        _state.value = _state.value.copy(
            status = status,
            noteId = session?.noteId ?: _state.value.noteId,
            noteTitle = session?.noteTitle ?: _state.value.noteTitle,
            label = label,
            error = null,
            speed = speed,
            voice = session?.voice ?: _state.value.voice,
            currentChunk = activeChunkIndex + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
            currentPositionMs = chunkPosition,
            durationMs = chunkDuration,
            totalPositionMs = pendingSeekMs ?: globalPositionMs(chunkPosition),
            totalDurationMs = estimatedTotalDurationMs().takeIf { it > 0L } ?: chunkDuration,
        )
    }

    private fun applyPlaybackSpeed(player: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
        }
    }


    private fun seekWithinGenerated(target: Long) {
        var accumulated = 0L
        var targetChunk = 0
        for (index in activeDurationsMs.indices) {
            val duration = activeDurationsMs[index]
            val chunkEnd = accumulated + duration
            if (target < chunkEnd || index == activeDurationsMs.lastIndex) {
                targetChunk = index
                break
            }
            accumulated += duration
        }
        val chunkOffset = (target - accumulated).coerceAtLeast(0L)
        val player = mediaPlayer
        if (targetChunk == activeChunkIndex && player != null) {
            seekPlayer(player, chunkOffset)
            _state.update {
                it.copy(
                    currentPositionMs = chunkOffset,
                    totalPositionMs = target,
                    error = null,
                )
            }
        } else {
            playChunk(targetChunk, startPositionMs = chunkOffset)
        }
    }

    private fun resumePendingSeekIfReady(force: Boolean = false) {
        val target = pendingSeekMs ?: return
        val generatedDuration = activeDurationsMs.sum()
        if (force || generatedDuration > target) {
            pendingSeekMs = null
            waitingForNextChunk = false
            seekWithinGenerated(target.coerceAtMost((generatedDuration - 250L).coerceAtLeast(0L)))
        }
    }

    private fun estimatedTotalDurationMs(): Long {
        val generatedDuration = activeDurationsMs.sum()
        if (expectedChunks <= 0 || activeDurationsMs.isEmpty()) return generatedDuration
        if (activeDurationsMs.size >= expectedChunks) return generatedDuration
        val average = activeDurationsMs.filter { it > 0L }.average().takeIf { !it.isNaN() && it > 0.0 } ?: return generatedDuration
        return (average * expectedChunks).toLong().coerceAtLeast(generatedDuration)
    }

    private fun seekPlayer(player: MediaPlayer, positionMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            player.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            @Suppress("DEPRECATION")
            player.seekTo(positionMs.toInt())
        }
    }

    private fun globalPositionMs(chunkPositionMs: Long = mediaPlayer?.currentPosition?.toLong() ?: 0L): Long =
        activeDurationsMs.take(activeChunkIndex).sum() + chunkPositionMs

    private fun readDurationMs(file: File): Long {
        if (!file.exists() || file.length() <= 0L) return 0L
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
    }

    private fun isCurrentRequest(player: MediaPlayer, requestId: Long): Boolean = player === mediaPlayer && requestId == requestCounter

    private fun stopInternal(resetState: Boolean) {
        releaseCurrentPlayer()
        releaseDeviceTts(stopOnly = true)
        activeFiles = emptyList()
        activeDurationsMs = emptyList()
        expectedChunks = 0
        activeChunkIndex = 0
        activeSession = null
        streamingGeneration = false
        waitingForNextChunk = false
        pendingSeekMs = null
        deviceChunks = emptyList()
        deviceChunkIndex = 0
        deviceNoteId = null
        deviceNoteTitle = ""
        devicePaused = false
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

    private fun releaseDeviceTts(stopOnly: Boolean) {
        textToSpeech?.stop()
        if (!stopOnly) {
            textToSpeech?.shutdown()
            textToSpeech = null
            deviceTtsReady = false
        }
    }

    private fun String.splitForDeviceTts(): List<String> =
        split(Regex("\\n{2,}"))
            .flatMap { paragraph ->
                val clean = paragraph.trim()
                if (clean.length <= DeviceTtsMaxChunkChars) {
                    listOf(clean)
                } else {
                    clean.split(Regex("(?<=[.!?؟。])\\s+"))
                        .fold(mutableListOf<String>()) { chunks, sentence ->
                            val next = sentence.trim()
                            if (next.isBlank()) return@fold chunks
                            val current = chunks.lastOrNull()
                            if (current != null && current.length + next.length + 1 <= DeviceTtsMaxChunkChars) {
                                chunks[chunks.lastIndex] = "$current $next"
                            } else {
                                chunks += next
                            }
                            chunks
                        }
                }
            }
            .filter { it.isNotBlank() }

    private companion object {
        const val FalseResetState = false
        const val DeviceNarrationVoice = "device"
        const val DeviceTtsMaxChunkChars = 900
    }
}
