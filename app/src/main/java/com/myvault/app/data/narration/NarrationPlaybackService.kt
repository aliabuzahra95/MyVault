package com.myvault.app.data.narration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.myvault.app.MainActivity
import com.myvault.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NarrationPlaybackService : Service() {

    @Inject lateinit var playerManager: NarrationPlayerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private lateinit var mediaSession: MediaSession

    // MediaSession notifications are exempt from Android 13's notification permission.
    @android.annotation.SuppressLint("NotificationPermission")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()

        player = exoPlayer

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession(this, "MyVault Narration").apply {
            setSessionActivity(sessionActivityPendingIntent)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = playerManager.resume()
                override fun onPause() = playerManager.pause()
                override fun onStop() = playerManager.stop()
                override fun onSeekTo(pos: Long) = playerManager.seekTo(pos)
                override fun onFastForward() = playerManager.forward10s()
                override fun onRewind() = playerManager.rewind10s()
                override fun onSetPlaybackSpeed(speed: Float) = playerManager.setSpeed(speed)
            })
            isActive = true
        }

        // Call startForeground IMMEDIATELY in onCreate to guarantee ForegroundServiceDidNotStartInTimeException never occurs
        val initialNotification = buildNotification(playerManager.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        playerManager.attachPlayer(exoPlayer)

        // Observe player manager state changes to update notification and handle teardown
        scope.launch {
            playerManager.state
                .map { listOf(it.status, it.noteId, it.noteTitle, it.currentChunk, it.totalChunks, it.label) }
                .distinctUntilChanged()
                .collect {
                    val state = playerManager.state.value
                    val notification = buildNotification(state)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, notification)

                    if (state.status in setOf(NarrationPlaybackStatus.Stopped, NarrationPlaybackStatus.Error, NarrationPlaybackStatus.Idle)) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }

        // Keep MediaSession PlaybackState synced for Quick Settings media controls and lock screen
        scope.launch {
            while (isActive) {
                val state = playerManager.state.value
                val playbackStateCode = when (state.status) {
                    NarrationPlaybackStatus.Playing -> PlaybackState.STATE_PLAYING
                    NarrationPlaybackStatus.Preparing,
                    NarrationPlaybackStatus.Generating -> PlaybackState.STATE_BUFFERING
                    NarrationPlaybackStatus.Paused -> PlaybackState.STATE_PAUSED
                    NarrationPlaybackStatus.Error -> PlaybackState.STATE_ERROR
                    else -> PlaybackState.STATE_STOPPED
                }
                val actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_SET_PLAYBACK_SPEED

                mediaSession.setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(actions)
                        .setState(playbackStateCode, state.totalPositionMs, state.speed)
                        .build(),
                )
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> playerManager.toggle()
            ACTION_PAUSE -> playerManager.pause()
            ACTION_RESUME -> playerManager.resume()
            ACTION_STOP -> playerManager.stop()
            ACTION_REWIND_10 -> playerManager.rewind10s()
            ACTION_FORWARD_10 -> playerManager.forward10s()
            ACTION_START -> Unit
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        playerManager.detachPlayer()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun buildNotification(state: NarrationUiState): Notification {
        val noteTitle = state.noteTitle.ifBlank { "Note Narration" }
        val isAttachment = state.noteId?.startsWith("attachment:") == true
        val contentSubtitle = if (isAttachment) "Library Document" else "MyVault Narration"
        val subText = when {
            state.status == NarrationPlaybackStatus.Generating -> state.label.ifBlank { "Generating narration..." }
            state.status == NarrationPlaybackStatus.Preparing -> state.label.ifBlank { "Loading narration..." }
            state.totalChunks > 1 -> "Part ${state.currentChunk} of ${state.totalChunks}"
            else -> contentSubtitle
        }

        if (::mediaSession.isInitialized) {
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, noteTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, contentSubtitle)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "MyVault")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.totalDurationMs)
                    .build(),
            )
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val isPlaying = state.status == NarrationPlaybackStatus.Playing

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_note)
            .setContentTitle(noteTitle)
            .setContentText(subText)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "Rewind 10s",
                    actionPendingIntent(ACTION_REWIND_10, 101),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    actionPendingIntent(ACTION_TOGGLE, 102),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "Forward 10s",
                    actionPendingIntent(ACTION_FORWARD_10, 103),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    actionPendingIntent(ACTION_STOP, 104),
                ).build(),
            )

        if (::mediaSession.isInitialized) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }

        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, NarrationPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            }
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "MyVault narration and audio listening controls"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "myvault_narration"
        const val CHANNEL_NAME = "MyVault Narration"
        const val NOTIFICATION_ID = 7050
        const val SEEK_INCREMENT_MS = 10_000L

        const val ACTION_START = "com.myvault.app.narration.ACTION_START"
        const val ACTION_TOGGLE = "com.myvault.app.narration.ACTION_TOGGLE"
        const val ACTION_PAUSE = "com.myvault.app.narration.ACTION_PAUSE"
        const val ACTION_RESUME = "com.myvault.app.narration.ACTION_RESUME"
        const val ACTION_STOP = "com.myvault.app.narration.ACTION_STOP"
        const val ACTION_REWIND_10 = "com.myvault.app.narration.ACTION_REWIND_10"
        const val ACTION_FORWARD_10 = "com.myvault.app.narration.ACTION_FORWARD_10"

        fun start(context: Context) {
            val intent = Intent(context, NarrationPlaybackService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("NarrationPlayback", "Failed to start NarrationPlaybackService", e)
            }
        }
    }
}
