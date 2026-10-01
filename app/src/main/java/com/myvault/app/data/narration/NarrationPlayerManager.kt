package com.myvault.app.data.narration

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationPlayerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val progressStore: NarrationProgressStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: Player? = null
    private var tickerJob: Job? = null

    private inline fun runOnMain(crossinline action: () -> Unit) {
        val isMain = runCatching { android.os.Looper.myLooper() != null && android.os.Looper.myLooper() == android.os.Looper.getMainLooper() }.getOrDefault(true)
        if (isMain) {
            action()
        } else {
            scope.launch(Dispatchers.Main.immediate) {
                action()
            }
        }
    }

    private var activeFiles: List<File> = emptyList()
    private var activeDurationsMs: List<Long> = emptyList()
    private var activeChunkIndices: List<Int> = emptyList()
    private var activeCues: List<NarrationCue> = emptyList()
    private var expectedChunks: Int = 0
    private var activeChunkIndex: Int = 0
    private var activeSession: NarrationSession? = null
    private var speed = 1f
    private var streamingGeneration = false
    private var waitingForNextChunk = false
    private var pendingSeekMs: Long? = null
    private var demandDriven = false
    private var chunkRequestListener: ((Int) -> Unit)? = null
    private val requestedChunks = NarrationChunkRequestTracker()
    private var pendingStartSession: NarrationSession? = null
    private var pendingInitialPositionMs: Long = 0L
    private var lastProgressSavedAt = 0L
    private var startupStartedAtMs: Long? = null
    private var startupSourceId: String? = null

    private val _state = MutableStateFlow(NarrationUiState())
    val state: StateFlow<NarrationUiState> = _state.asStateFlow()
    val azureProgress: StateFlow<Map<String, AzureNarrationProgress>> = progressStore.progress

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    when {
                        pendingSeekMs != null -> updatePlaybackState(NarrationPlaybackStatus.Generating, "Loading saved position...")
                        player?.isPlaying == true -> updatePlaybackState(NarrationPlaybackStatus.Playing, "Playing")
                        player?.playWhenReady == true -> updatePlaybackState(NarrationPlaybackStatus.Preparing, "Buffering narration...")
                        else -> updatePlaybackState(NarrationPlaybackStatus.Paused, "Paused")
                    }
                }
                Player.STATE_BUFFERING -> {
                    updatePlaybackState(NarrationPlaybackStatus.Preparing, "Loading narration...")
                }
                Player.STATE_ENDED -> {
                    if (streamingGeneration && activeChunkIndex + 1 >= activeFiles.size) {
                        waitingForNextChunk = true
                        _state.update {
                            it.copy(
                                status = NarrationPlaybackStatus.Generating,
                                label = "Preparing next part...",
                            )
                        }
                        requestChunk((activeChunkIndices.getOrNull(activeChunkIndex) ?: activeChunkIndex) + 1, "playback-ended")
                    } else {
                        onNarrationCompleted()
                    }
                }
                Player.STATE_IDLE -> {
                    // Handled during stop / error
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startupStartedAtMs?.let { startedAt ->
                    Log.d(Tag, "startup first-playing source=${startupSourceId.orEmpty().take(96)} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                }
                startupStartedAtMs = null
                startupSourceId = null
                startTicker()
                updatePlaybackState(NarrationPlaybackStatus.Playing, "Playing")
            } else {
                stopTicker()
                val currentStatus = _state.value.status
                if (currentStatus == NarrationPlaybackStatus.Playing) {
                    updatePlaybackState(NarrationPlaybackStatus.Paused, "Paused")
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentPlayer = player ?: return
            activeChunkIndex = currentPlayer.currentMediaItemIndex.coerceAtLeast(0)
            Log.d(Tag, "current-playback-chunk chunk=${(activeChunkIndices.getOrNull(activeChunkIndex) ?: activeChunkIndex) + 1}")
            updatePlaybackState(_state.value.status, _state.value.label)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(Tag, "ExoPlayer playback error: ${error.errorCodeName}", error)
            showError(activeSession?.noteId, activeSession?.noteTitle ?: _state.value.noteTitle, "Audio playback error. Try again.")
        }
    }

    fun attachPlayer(newPlayer: Player) {
        player = newPlayer
        newPlayer.addListener(playerListener)
        newPlayer.playbackParameters = PlaybackParameters(speed)

        val pending = pendingStartSession
        if (pending != null) {
            pendingStartSession = null
            val pos = pendingInitialPositionMs
            pendingInitialPositionMs = 0L
            startStreaming(pending, expectedChunks.coerceAtLeast(pending.files.size), pos)
        }
    }

    fun setChunkRequestListener(listener: ((Int) -> Unit)?) {
        chunkRequestListener = listener
    }

    fun releaseChunkRequest(chunkIndex: Int) = runOnMain {
        requestedChunks.complete(chunkIndex)
    }

    fun detachPlayer() {
        stopTicker()
        player?.removeListener(playerListener)
        player = null
    }

    private fun ensurePlayerOrStartService(): Player? {
        val current = player
        if (current != null) return current
        NarrationPlaybackService.start(context)
        return null
    }

    fun markPreparing(noteId: String, noteTitle: String, voice: String = NarrationConfig.DEFAULT_VOICE,
        provider: NarrationProvider = _state.value.provider) = runOnMain {
        _state.value = _state.value.preparing(noteId, noteTitle, provider, voice)
    }

    fun beginStartup(noteId: String) = runOnMain {
        startupSourceId = noteId
        startupStartedAtMs = SystemClock.elapsedRealtime()
        Log.d(Tag, "startup begin source=${noteId.take(96)}")
    }

    fun markGenerating(noteId: String, noteTitle: String, current: Int, total: Int, voice: String = NarrationConfig.DEFAULT_VOICE) = runOnMain {
        val status = _state.value.status
        if (status == NarrationPlaybackStatus.Playing || status == NarrationPlaybackStatus.Paused) {
            _state.update { it.copy(totalChunks = total.takeIf { v -> v > 0 } ?: it.totalChunks) }
            return@runOnMain
        }
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Generating,
            noteId = noteId,
            noteTitle = noteTitle,
            label = if (total > 1) "Generating narration $current of $total..." else "Generating narration...",
            speed = speed,
            provider = _state.value.provider,
            voice = voice,
            currentChunk = current,
            totalChunks = total,
        )
    }

    fun play(session: NarrationSession) {
        startStreaming(session, totalChunks = session.files.size, initialPositionMs = 0L)
    }

    fun startStreaming(session: NarrationSession, totalChunks: Int, initialPositionMs: Long = 0L) = runOnMain {
        val attachStartedAt = SystemClock.elapsedRealtime()
        stopInternal(resetState = false)
        activeSession = session
        _state.update { it.copy(provider = NarrationProvider.fromModel(session.model), voice = session.voice) }
        activeFiles = session.files
        activeChunkIndices = session.chunkIndices.takeIf { it.size == session.files.size } ?: session.files.indices.toList()
        activeCues = session.cues
        val durationStartedAt = SystemClock.elapsedRealtime()
        activeDurationsMs = session.files.map(::readDurationMs)
        Log.d(Tag, "startup duration-scan source=${session.noteId.take(96)} files=${session.files.size} elapsedMs=${SystemClock.elapsedRealtime() - durationStartedAt}")
        expectedChunks = maxOf(totalChunks, session.totalChunks, session.files.size)
        demandDriven = session.demandDriven
        streamingGeneration = if (demandDriven) {
            (activeChunkIndices.lastOrNull() ?: 0) < expectedChunks - 1
        } else {
            totalChunks > session.files.size
        }
        activeChunkIndices.forEach(requestedChunks::complete)
        waitingForNextChunk = false
        activeChunkIndex = 0
        speed = session.speed

        if (activeFiles.isEmpty()) {
            markGenerating(session.noteId, session.noteTitle, 1, expectedChunks, session.voice)
            return@runOnMain
        }

        val p = ensurePlayerOrStartService()
        if (p == null) {
            // Service is starting up, stash pending session
            pendingStartSession = session
            pendingInitialPositionMs = initialPositionMs
            markPreparing(session.noteId, session.noteTitle, session.voice, NarrationProvider.fromModel(session.model))
            return@runOnMain
        }

        _state.update {
            it.copy(
                status = NarrationPlaybackStatus.Preparing,
                label = if (initialPositionMs > 0L) "Loading saved position..." else "Buffering narration...",
                noteId = session.noteId,
                noteTitle = session.noteTitle,
                provider = NarrationProvider.fromModel(session.model),
                voice = session.voice,
                totalChunks = expectedChunks,
                error = null,
            )
        }
        val mediaItemsStartedAt = SystemClock.elapsedRealtime()
        val mediaItems = activeFiles.map { file -> buildMediaItem(session, file) }
        p.setMediaItems(mediaItems)
        p.playbackParameters = PlaybackParameters(speed)
        Log.d(Tag, "startup media-items source=${session.noteId.take(96)} count=${mediaItems.size} elapsedMs=${SystemClock.elapsedRealtime() - mediaItemsStartedAt}")
        p.prepare()

        if (initialPositionMs > 0L) {
            seekTo(initialPositionMs)
        } else {
            p.play()
        }
        Log.d(Tag, "startup player-attached source=${session.noteId.take(96)} elapsedMs=${SystemClock.elapsedRealtime() - attachStartedAt}")
    }

    fun acceptDemandChunk(session: NarrationSession, initialPositionMs: Long = 0L) = runOnMain {
        val chunkIndex = session.chunkIndices.firstOrNull() ?: return@runOnMain
        requestedChunks.complete(chunkIndex)
        val current = activeSession
        if (current?.cacheKey != session.cacheKey || activeFiles.isEmpty()) {
            startStreaming(session, session.totalChunks, initialPositionMs)
            return@runOnMain
        }
        if (chunkIndex in activeChunkIndices) return@runOnMain

        val pendingTarget = pendingSeekMs?.let { NarrationDemandPolicy.chunkForPosition(it, expectedChunks) }
        if (pendingTarget == chunkIndex) {
            startStreaming(session, session.totalChunks, pendingSeekMs ?: 0L)
            return@runOnMain
        }

        val lastChunk = activeChunkIndices.lastOrNull() ?: -1
        if (chunkIndex != lastChunk + 1) return@runOnMain
        activeSession = current.copy(cues = (activeCues + session.cues).distinctBy { cue -> cue.chunkIndex to cue.startMs })
        activeCues = activeSession?.cues.orEmpty()
        activeFiles = activeFiles + session.files
        activeChunkIndices = activeChunkIndices + chunkIndex
        activeDurationsMs = activeDurationsMs + session.files.map(::readDurationMs)
        expectedChunks = maxOf(expectedChunks, session.totalChunks)
        streamingGeneration = chunkIndex < expectedChunks - 1

        val p = player
        if (p != null) p.addMediaItems(session.files.map { buildMediaItem(session, it) })
        _state.update {
            it.copy(
                totalChunks = expectedChunks,
                totalDurationMs = estimatedTotalDurationMs(),
                error = null,
            )
        }
        if (waitingForNextChunk && p != null && p.playbackState == Player.STATE_ENDED) {
            waitingForNextChunk = false
            p.seekToNextMediaItem()
            p.play()
        }
    }

    fun appendStreamingChunk(session: NarrationSession, totalChunks: Int) = runOnMain {
        val current = activeSession
        if (current?.cacheKey != session.cacheKey) return@runOnMain
        activeSession = session

        val newFiles = session.files.drop(activeFiles.size)
        activeFiles = session.files
        activeChunkIndices = session.chunkIndices.takeIf { it.size == session.files.size } ?: session.files.indices.toList()
        activeCues = session.cues
        activeDurationsMs = if (activeDurationsMs.size + newFiles.size == session.files.size) {
            activeDurationsMs + newFiles.map(::readDurationMs)
        } else {
            session.files.map(::readDurationMs)
        }
        expectedChunks = totalChunks.coerceAtLeast(session.files.size)
        streamingGeneration = totalChunks > session.files.size

        val p = player
        if (p != null && newFiles.isNotEmpty()) {
            val newItems = newFiles.map { buildMediaItem(session, it) }
            p.addMediaItems(newItems)
        }

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

        if (waitingForNextChunk && pendingSeekMs == null && p != null && p.playbackState == Player.STATE_ENDED) {
            waitingForNextChunk = false
            p.seekToNextMediaItem()
            p.play()
        }
    }

    fun finishStreaming(session: NarrationSession) = runOnMain {
        if (activeSession?.cacheKey != session.cacheKey) return@runOnMain
        activeSession = session
        val newFiles = session.files.drop(activeFiles.size)
        activeFiles = session.files
        activeChunkIndices = session.chunkIndices.takeIf { it.size == session.files.size } ?: session.files.indices.toList()
        activeCues = session.cues
        activeDurationsMs = if (activeDurationsMs.size + newFiles.size == session.files.size) {
            activeDurationsMs + newFiles.map(::readDurationMs)
        } else {
            session.files.map(::readDurationMs)
        }
        expectedChunks = session.files.size
        streamingGeneration = false
        updatePlaybackState(_state.value.status, _state.value.label.ifBlank { "Playing" })
        resumePendingSeekIfReady(force = true)
    }

    fun toggle() = runOnMain {
        when (_state.value.status) {
            NarrationPlaybackStatus.Playing -> pause()
            NarrationPlaybackStatus.Paused,
            NarrationPlaybackStatus.Stopped -> resume()
            else -> Unit
        }
    }

    fun pause() = runOnMain {
        val p = player
        if (p != null && p.isPlaying) {
            p.pause()
        }
        updatePlaybackState(NarrationPlaybackStatus.Paused, "Paused")
        persistAzureProgress(force = true)
    }

    fun resume() = runOnMain {
        val p = player
        if (p != null) {
            p.playbackParameters = PlaybackParameters(speed)
            updatePlaybackState(NarrationPlaybackStatus.Preparing, "Buffering narration...")
            p.play()
            return@runOnMain
        }
        val session = activeSession ?: return@runOnMain
        startStreaming(session, expectedChunks, _state.value.totalPositionMs)
    }

    fun stop() = runOnMain {
        persistAzureProgress(force = true)
        stopInternal(resetState = true)
    }

    fun rewind10s() = runOnMain {
        val currentPos = _state.value.totalPositionMs
        seekTo((currentPos - 10_000L).coerceAtLeast(0L))
    }

    fun forward10s() = runOnMain {
        val currentPos = _state.value.totalPositionMs
        val totalDur = _state.value.totalDurationMs
        seekTo((currentPos + 10_000L).coerceAtMost((totalDur - 250L).coerceAtLeast(0L)))
    }

    fun seekTo(totalPositionMs: Long) = runOnMain {
        if (activeFiles.isEmpty()) return@runOnMain
        if (demandDriven) {
            val totalDuration = estimatedTotalDurationMs().coerceAtLeast(NarrationTargetChunkMs)
            val target = totalPositionMs.coerceIn(0L, (totalDuration - 250L).coerceAtLeast(0L))
            val targetChunk = NarrationDemandPolicy.chunkForPosition(target, expectedChunks)
            val localPosition = target % NarrationTargetChunkMs
            val playlistIndex = activeChunkIndices.indexOf(targetChunk)
            if (playlistIndex >= 0) {
                pendingSeekMs = null
                waitingForNextChunk = false
                player?.seekTo(playlistIndex, localPosition)
                player?.play()
                activeChunkIndex = playlistIndex
                _state.update {
                    it.copy(
                        status = NarrationPlaybackStatus.Preparing,
                        label = "Buffering narration...",
                        currentChunk = targetChunk + 1,
                        totalPositionMs = target,
                        error = null,
                    )
                }
            } else {
                pendingSeekMs = target
                waitingForNextChunk = true
                player?.pause()
                _state.update {
                    it.copy(
                        status = NarrationPlaybackStatus.Generating,
                        label = "Generating part ${targetChunk + 1}...",
                        currentChunk = targetChunk + 1,
                        totalPositionMs = target,
                        error = null,
                    )
                }
                requestChunk(targetChunk, "seek")
            }
            return@runOnMain
        }
        if (activeDurationsMs.size != activeFiles.size) activeDurationsMs = activeFiles.map(::readDurationMs)
        val generatedDuration = activeDurationsMs.sum().takeIf { it > 0L } ?: return@runOnMain
        val estimatedDuration = estimatedTotalDurationMs().takeIf { it > 0L } ?: generatedDuration
        val target = totalPositionMs.coerceIn(0L, (estimatedDuration - 250L).coerceAtLeast(0L))

        if (target >= generatedDuration && streamingGeneration) {
            pendingSeekMs = target
            waitingForNextChunk = true
            player?.pause()
            _state.value = _state.value.copy(
                status = NarrationPlaybackStatus.Generating,
                label = "Loading selected position...",
                totalPositionMs = target,
                totalDurationMs = estimatedDuration,
                totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
                error = null,
            )
            return@runOnMain
        }
        pendingSeekMs = null
        seekWithinGenerated(target.coerceAtMost((generatedDuration - 250L).coerceAtLeast(0L)))
    }

    fun setSpeed(newSpeed: Float) = runOnMain {
        speed = newSpeed.coerceIn(0.75f, 2.0f)
        player?.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun refreshProgress() {
        val status = _state.value.status
        if (status == NarrationPlaybackStatus.Playing || status == NarrationPlaybackStatus.Paused || status == NarrationPlaybackStatus.Preparing) {
            updatePlaybackState(status, _state.value.label)
            persistAzureProgress()
        }
    }

    fun saveProgress() {
        updatePlaybackState(_state.value.status, _state.value.label)
        persistAzureProgress(force = true)
    }

    fun resumePositionFor(session: NarrationSession): Long? =
        progressStore.getUnified(session.noteId)
            ?.takeIf {
                it.cacheKey == session.cacheKey &&
                    (it.contentHash.isBlank() || it.contentHash == session.contentHash) &&
                    it.positionMs > ResumeMinimumMs
            }
            ?.positionMs
            ?: progressStore.get(session.noteId)
                ?.takeIf { it.cacheKey == session.cacheKey && it.positionMs > ResumeMinimumMs }
                ?.positionMs

    fun showError(noteId: String?, noteTitle: String, message: String) = runOnMain {
        stopInternal(resetState = false)
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Error,
            noteId = noteId,
            noteTitle = noteTitle,
            label = "Narration unavailable",
            error = message,
            speed = speed,
            provider = _state.value.provider,
            voice = _state.value.voice,
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(ProgressTickIntervalMs)
                updatePlaybackState(_state.value.status, _state.value.label)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updatePlaybackState(status: NarrationPlaybackStatus, label: String) {
        val p = player
        val session = activeSession
        val playlistIndex = p?.currentMediaItemIndex?.coerceAtLeast(0) ?: activeChunkIndex
        activeChunkIndex = playlistIndex
        val chunkIndex = activeChunkIndices.getOrNull(playlistIndex) ?: playlistIndex
        val chunkPosition = p?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val chunkDuration = p?.duration?.takeIf { it > 0L }
            ?: activeDurationsMs.getOrNull(playlistIndex)
            ?: 0L

        val globalPos = pendingSeekMs ?: globalPositionMs(chunkPosition)
        val activeCue = NarrationDemandPolicy.activeCue(activeCues, chunkIndex, chunkPosition)
        val activeSentence = activeCue?.displayText.orEmpty()
        if (activeCue != null && activeSentence != _state.value.activeSentence) {
            Log.d(Tag, "highlight-segment source=${session?.noteId.orEmpty().take(96)} chunk=${chunkIndex + 1} start=${activeCue.textStart} end=${activeCue.textEnd}")
        }

        if (demandDriven && status == NarrationPlaybackStatus.Playing && chunkIndex < expectedChunks - 1) {
            if (NarrationDemandPolicy.shouldPrefetch(chunkPosition, chunkDuration)) {
                requestChunk(chunkIndex + 1, "prefetch")
            }
        }

        _state.value = _state.value.copy(
            status = status,
            noteId = session?.noteId ?: _state.value.noteId,
            noteTitle = session?.noteTitle ?: _state.value.noteTitle,
            label = label,
            error = null,
            speed = speed,
            provider = session?.let { NarrationProvider.fromModel(it.model) } ?: _state.value.provider,
            voice = session?.voice ?: _state.value.voice,
            currentChunk = chunkIndex + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
            currentPositionMs = chunkPosition,
            durationMs = chunkDuration,
            totalPositionMs = globalPos,
            totalDurationMs = estimatedTotalDurationMs().takeIf { it > 0L } ?: chunkDuration,
            activeSentence = activeSentence,
        )
        persistAzureProgress()
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
        val p = player
        if (p != null) {
            p.seekTo(targetChunk, chunkOffset)
            if (!p.isPlaying) p.play()
        }
        activeChunkIndex = targetChunk
        _state.update {
            it.copy(
                currentPositionMs = chunkOffset,
                totalPositionMs = target,
                currentChunk = targetChunk + 1,
                error = null,
            )
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

    private fun onNarrationCompleted() {
        val session = activeSession
        if (session != null) {
            progressStore.clear(session.noteId)
        }
        stopTicker()
        _state.value = NarrationUiState(
            status = NarrationPlaybackStatus.Stopped,
            noteId = session?.noteId,
            noteTitle = session?.noteTitle.orEmpty(),
            label = "Narration finished",
            speed = speed,
            provider = session?.let { NarrationProvider.fromModel(it.model) } ?: _state.value.provider,
            voice = session?.voice ?: NarrationConfig.DEFAULT_VOICE,
            currentChunk = (activeChunkIndices.getOrNull(activeChunkIndex) ?: activeChunkIndex) + 1,
            totalChunks = expectedChunks.takeIf { it > 0 } ?: activeFiles.size,
            totalPositionMs = globalPositionMs(activeDurationsMs.getOrNull(activeChunkIndex) ?: 0L),
            totalDurationMs = estimatedTotalDurationMs(),
        )
    }

    private fun stopInternal(resetState: Boolean) {
        stopTicker()
        player?.run {
            stop()
            clearMediaItems()
        }
        activeFiles = emptyList()
        activeDurationsMs = emptyList()
        activeChunkIndices = emptyList()
        activeCues = emptyList()
        expectedChunks = 0
        activeChunkIndex = 0
        activeSession = null
        streamingGeneration = false
        waitingForNextChunk = false
        pendingSeekMs = null
        pendingStartSession = null
        pendingInitialPositionMs = 0L
        demandDriven = false
        requestedChunks.clear()
        if (resetState) {
            startupStartedAtMs = null
            startupSourceId = null
            _state.value = NarrationUiState(speed = speed,
                provider = _state.value.provider, voice = _state.value.voice)
        }
    }

    private fun persistAzureProgress(force: Boolean = false) {
        val session = activeSession ?: return
        val state = _state.value
        val now = System.currentTimeMillis()
        if (!force && now - lastProgressSavedAt < ProgressSaveIntervalMs) return
        val position = state.totalPositionMs.coerceAtLeast(0L)
        if (position < ResumeMinimumMs) return
        progressStore.saveUnified(
            NarrationProgress(
                sourceId = session.noteId,
                cacheKey = session.cacheKey,
                contentHash = session.contentHash,
                provider = state.provider.storedValue,
                model = session.model,
                voice = session.voice,
                positionMs = position,
                durationMs = state.totalDurationMs.coerceAtLeast(0L),
                speed = speed,
                activeSentence = state.activeSentence,
                updatedAt = now,
            ),
        )
        lastProgressSavedAt = now
    }

    private fun estimatedTotalDurationMs(): Long {
        val generatedDuration = activeDurationsMs.sum()
        if (demandDriven && expectedChunks > 0) return expectedChunks * NarrationTargetChunkMs
        if (expectedChunks <= 0 || activeDurationsMs.isEmpty()) return generatedDuration
        if (activeDurationsMs.size >= expectedChunks) return generatedDuration
        val average = activeDurationsMs.filter { it > 0L }.average().takeIf { !it.isNaN() && it > 0.0 } ?: return generatedDuration
        return (average * expectedChunks).toLong().coerceAtLeast(generatedDuration)
    }

    private fun globalPositionMs(chunkPositionMs: Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L): Long {
        if (demandDriven) {
            val chunkIndex = activeChunkIndices.getOrNull(activeChunkIndex) ?: activeChunkIndex
            return chunkIndex * NarrationTargetChunkMs + chunkPositionMs
        }
        return activeDurationsMs.take(activeChunkIndex).sum() + chunkPositionMs
    }

    private fun requestChunk(chunkIndex: Int, reason: String) {
        if (!demandDriven || chunkIndex !in 0 until expectedChunks) return
        if (chunkIndex in activeChunkIndices || !requestedChunks.tryStart(chunkIndex)) {
            Log.d(Tag, "duplicate-request-prevented chunk=${chunkIndex + 1} reason=$reason")
            return
        }
        Log.d(Tag, "chunk-requested chunk=${chunkIndex + 1} reason=$reason")
        val listener = chunkRequestListener
        if (listener == null) {
            requestedChunks.complete(chunkIndex)
            showError(activeSession?.noteId, activeSession?.noteTitle.orEmpty(), "Narration chunk loader is unavailable.")
            return
        }
        listener(chunkIndex)
    }

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

    private fun buildMediaItem(session: NarrationSession, file: File): MediaItem {
        val isAttachment = session.noteId.startsWith("attachment:")
        val subtitle = if (isAttachment) "Library Document" else "Note Narration"
        val metadata = MediaMetadata.Builder()
            .setTitle(session.noteTitle)
            .setDisplayTitle(session.noteTitle)
            .setSubtitle(subtitle)
            .setArtist("MyVault Narration")
            .build()
        return MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        private const val Tag = "MyVaultNarration"
        private const val ProgressSaveIntervalMs = 5_000L
        private const val ProgressTickIntervalMs = 250L
        private const val ResumeMinimumMs = 5_000L
    }
}
