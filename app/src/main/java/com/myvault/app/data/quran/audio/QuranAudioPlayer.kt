package com.myvault.app.data.quran.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranAudioPlayer @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null
    private var playbackSpeed = 1f
    private var shouldStartAfterSeek = false
    private var requestCounter = 0L

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
                applyPlaybackSpeed(it)
                if (requestedStartMs > 0L) {
                    shouldStartAfterSeek = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.seekTo(requestedStartMs, MediaPlayer.SEEK_PREVIOUS_SYNC)
                    } else {
                        @Suppress("DEPRECATION")
                        it.seekTo(requestedStartMs.toInt())
                    }
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
            player.setOnErrorListener { _, _, _ ->
                stop()
                onError()
                true
            }
            player.prepareAsync()
        }.onFailure {
            stop()
            onError()
        }
    }

    fun pause() {
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
        mediaPlayer?.let {
            val clamped = positionMs.coerceIn(0L, durationMs())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.seekTo(clamped, MediaPlayer.SEEK_CLOSEST_SYNC)
            } else {
                @Suppress("DEPRECATION")
                it.seekTo(clamped.toInt())
            }
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
        mediaPlayer?.let(::applyPlaybackSpeed)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun currentPositionMs(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun durationMs(): Long = mediaPlayer?.duration?.takeIf { it > 0 }?.toLong() ?: 0L

    fun hasActiveMedia(): Boolean = mediaPlayer != null

    fun stop() {
        shouldStartAfterSeek = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
        }
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
