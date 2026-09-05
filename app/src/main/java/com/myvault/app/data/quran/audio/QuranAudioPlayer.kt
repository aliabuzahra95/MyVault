package com.myvault.app.data.quran.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranAudioPlayer @Inject constructor(@param:ApplicationContext private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var playbackSpeed = 1f
    private var shouldStartAfterSeek = false
    private var requestCounter = 0L
    private var prepared = false
    var isSeeking = false
        private set

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(
        file: File,
        startMs: Long,
        speed: Float,
        verseByVerse: Boolean,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: () -> Unit,
    ) {
        stop()
        playbackSpeed = speed
        requestCounter += 1
        val requestId = requestCounter
        val requestedStartMs = if (verseByVerse) 0L else startMs.coerceAtLeast(0L)
        val player = MediaPlayer()
        mediaPlayer = player
        updatePlaybackState(hasActiveMedia = true, isPlaying = false, currentPositionMs = 0L, durationMs = 0L)

        runCatching {
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                if (!isCurrentRequest(it, requestId)) {
                    it.release()
                    return@setOnPreparedListener
                }
                prepared = true
                applyPlaybackSpeed(it)
                if (requestedStartMs > 0L) {
                    shouldStartAfterSeek = true
                    isSeeking = true
                    it.seekTo(requestedStartMs, MediaPlayer.SEEK_CLOSEST)
                } else {
                    it.start()
                    updatePlaybackState(
                        hasActiveMedia = true,
                        isPlaying = true,
                        currentPositionMs = it.currentPosition.toLong(),
                        durationMs = it.duration.toLong(),
                    )
                    onStarted()
                }
            }
            player.setOnSeekCompleteListener {
                if (!isCurrentRequest(it, requestId)) return@setOnSeekCompleteListener
                isSeeking = false
                if (shouldStartAfterSeek) {
                    shouldStartAfterSeek = false
                    it.start()
                    updatePlaybackState(
                        hasActiveMedia = true,
                        isPlaying = true,
                        currentPositionMs = it.currentPosition.toLong(),
                        durationMs = it.duration.toLong(),
                    )
                    onStarted()
                }
            }
            player.setOnCompletionListener {
                if (!isCurrentRequest(it, requestId)) return@setOnCompletionListener
                onCompleted()
                if (isCurrentRequest(it, requestId)) stop()
            }
            player.setOnErrorListener { failed, _, _ ->
                if (isCurrentRequest(failed, requestId)) {
                    stop()
                    onError()
                }
                true
            }
            player.prepareAsync()
        }.onFailure {
            stop()
            onError()
        }
    }

    fun pause() {
        if (!prepared) return
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        mediaPlayer?.let {
            updatePlaybackState(
                hasActiveMedia = true,
                isPlaying = false,
                currentPositionMs = it.currentPosition.toLong(),
                durationMs = it.duration.toLong(),
            )
        }
    }

    fun resume() {
        if (!prepared) return
        mediaPlayer?.let {
            applyPlaybackSpeed(it)
            if (!it.isPlaying) it.start()
            updatePlaybackState(
                hasActiveMedia = true,
                isPlaying = true,
                currentPositionMs = it.currentPosition.toLong(),
                durationMs = it.duration.toLong(),
            )
        }
    }

    fun seekTo(positionMs: Long) {
        if (!prepared) return
        mediaPlayer?.let {
            val clamped = positionMs.coerceIn(0L, durationMs())
            isSeeking = true
            it.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
            updatePlaybackState(
                hasActiveMedia = true,
                isPlaying = it.isPlaying,
                currentPositionMs = clamped,
                durationMs = it.duration.toLong(),
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        if (prepared) mediaPlayer?.let(::applyPlaybackSpeed)
    }

    fun isPlaying(): Boolean = prepared && mediaPlayer?.isPlaying == true

    fun currentPositionMs(): Long = if (prepared) mediaPlayer?.currentPosition?.toLong() ?: 0L else 0L

    fun durationMs(): Long = if (prepared) mediaPlayer?.duration?.takeIf { it > 0 }?.toLong() ?: 0L else 0L

    fun hasActiveMedia(): Boolean = mediaPlayer != null

    fun stop() {
        shouldStartAfterSeek = false
        prepared = false
        isSeeking = false
        requestCounter += 1
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        updatePlaybackState(hasActiveMedia = false, isPlaying = false, currentPositionMs = 0L, durationMs = 0L)
    }

    fun release() {
        stop()
    }

    private fun applyPlaybackSpeed(player: MediaPlayer) {
        player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
    }

    private fun isCurrentRequest(player: MediaPlayer, requestId: Long): Boolean {
        return player === mediaPlayer && requestId == requestCounter
    }

    private fun updatePlaybackState(
        hasActiveMedia: Boolean,
        isPlaying: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
    ) {
        _playbackState.value = PlaybackState(
            hasActiveMedia = hasActiveMedia,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    data class PlaybackState(
        val hasActiveMedia: Boolean = false,
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
    )
}
