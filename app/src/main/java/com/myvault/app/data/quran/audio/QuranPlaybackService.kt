package com.myvault.app.data.quran.audio

import android.app.*
import android.content.*
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.myvault.app.MainActivity
import com.myvault.app.R
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.widget.quran.QuranWidgetContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@AndroidEntryPoint
class QuranPlaybackService : Service() {
    @Inject lateinit var controller: QuranPlaybackController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private var commandReceived = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { controller.pause() }
    }

    // MediaSession notifications are exempt from Android 13's notification permission.
    @android.annotation.SuppressLint("NotificationPermission")
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Qur'an playback", NotificationManager.IMPORTANCE_LOW))
        mediaSession = MediaSession(this, "MyVault Quran")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = controller.resume()
            override fun onPause() = controller.pause()
            override fun onStop() = controller.stop()
            override fun onSeekTo(pos: Long) = controller.seek(pos)
            override fun onSkipToNext() = controller.adjacent(1)
            override fun onSkipToPrevious() = controller.adjacent(-1)
            override fun onSetPlaybackSpeed(speed: Float) = controller.speed(speed)
        })
        mediaSession.isActive = true
        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        startForeground(NOTIFICATION, notification(QuranPlaybackState(status = QuranPlaybackStatus.Preparing)))
        scope.launch {
            var previousSurah = 0
            controller.state.map { listOf(it.status, it.verseKey, it.reciter, it.mode, it.message) }.distinctUntilChanged().collect {
                val state = controller.state.value
                if (!commandReceived) return@collect
                com.myvault.app.widget.quran.QuranWidgetProvider.updatePlayback(this@QuranPlaybackService, setOf(previousSurah, state.surah))
                previousSurah = state.surah
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state))
                if (state.status == QuranPlaybackStatus.Error) Toast.makeText(this@QuranPlaybackService, state.message, Toast.LENGTH_LONG).show()
                if (state.status in setOf(QuranPlaybackStatus.Stopped, QuranPlaybackStatus.Error, QuranPlaybackStatus.Ended)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        scope.launch {
            while (isActive) {
                val state = controller.state.value
                mediaSession.setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SET_PLAYBACK_SPEED)
                    .setState(when (state.status) {
                        QuranPlaybackStatus.Playing -> PlaybackState.STATE_PLAYING
                        QuranPlaybackStatus.Preparing -> PlaybackState.STATE_BUFFERING
                        QuranPlaybackStatus.Paused -> PlaybackState.STATE_PAUSED
                        QuranPlaybackStatus.Error -> PlaybackState.STATE_ERROR
                        else -> PlaybackState.STATE_STOPPED
                    }, state.positionMs, state.speed).build())
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        commandReceived = true
        when (intent?.action) {
            PLAY -> controller.play(intent.getIntExtra(SURAH, 1), intent.getIntExtra(AYAH, 1),
                intent.getIntExtra(RECITER, -1).takeIf { it > 0 }?.let { AudioReciterUiModel(it, intent.getStringExtra(RECITER_NAME).orEmpty()) },
                if (intent.getBooleanExtra(CONTINUOUS, false)) QuranListeningMode.ContinueSurah else QuranListeningMode.ThisAyah)
            TOGGLE -> controller.toggle()
            PAUSE -> controller.pause()
            STOP -> controller.stop()
            CONTINUE -> controller.setMode(QuranListeningMode.ContinueSurah)
            else -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun notification(state: QuranPlaybackState): Notification {
        val title = quranCatalog.firstOrNull { it.num == state.surah }?.name ?: "Qur'an"
        mediaSession.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "$title ${state.verseKey.orEmpty()}")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.reciter?.name.orEmpty()).putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs).build())
        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("myvault://quran/${state.surah}/${state.verseKey?.substringAfter(':') ?: 1}")
            putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, state.surah.coerceAtLeast(1))
            putExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, state.verseKey?.substringAfter(':')?.toIntOrNull() ?: 1)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_widget_note)
            .setContentTitle(title).setContentText(if (state.status == QuranPlaybackStatus.Preparing) "Preparing recitation..." else "${state.reciter?.name.orEmpty()} ${state.verseKey.orEmpty()}")
            .setContentIntent(PendingIntent.getActivity(this, 7041, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setOngoing(state.isPlaying).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, if (state.isPlaying) "Pause" else "Play", actionIntent(TOGGLE)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1)).build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(this, action.hashCode(), Intent(this, QuranPlaybackService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(noisyReceiver)
        mediaSession.release()
        if (controller.state.value.status in setOf(QuranPlaybackStatus.Playing, QuranPlaybackStatus.Preparing, QuranPlaybackStatus.Paused)) controller.stop()
        super.onDestroy()
    }

    companion object {
        const val PLAY = "com.myvault.app.quran.audio.PLAY"
        const val TOGGLE = "com.myvault.app.quran.audio.TOGGLE"
        const val PAUSE = "com.myvault.app.quran.audio.PAUSE"
        const val STOP = "com.myvault.app.quran.audio.STOP"
        const val CONTINUE = "com.myvault.app.quran.audio.CONTINUE"
        private const val CHANNEL = "quran_playback"
        private const val NOTIFICATION = 7040
        private const val SURAH = "audio_surah"
        private const val AYAH = "audio_ayah"
        private const val RECITER = "audio_reciter"
        private const val RECITER_NAME = "audio_reciter_name"
        private const val CONTINUOUS = "audio_continuous"
        fun start(context: Context, action: String, surah: Int = 1, ayah: Int = 1, reciter: AudioReciterUiModel? = null, mode: QuranListeningMode = QuranListeningMode.ThisAyah) {
            val intent = Intent(context, QuranPlaybackService::class.java).setAction(action)
                .putExtra(SURAH, surah).putExtra(AYAH, ayah).putExtra(CONTINUOUS, mode == QuranListeningMode.ContinueSurah)
            reciter?.let { intent.putExtra(RECITER, it.id).putExtra(RECITER_NAME, it.name) }
            try { ContextCompat.startForegroundService(context, intent) }
            catch (error: RuntimeException) { Toast.makeText(context, "Qur'an playback could not start: ${error.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
