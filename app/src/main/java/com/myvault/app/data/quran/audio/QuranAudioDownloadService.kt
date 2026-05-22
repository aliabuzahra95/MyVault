package com.myvault.app.data.quran.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myvault.app.MainActivity
import com.myvault.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QuranAudioDownloadService : Service() {
    @Inject lateinit var audioRepository: QuranAudioRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingQueue = ArrayDeque<DownloadRequest>()
    private var stateObserverJob: Job? = null
    private var queueProcessorJob: Job? = null
    private var activeDownload: DownloadRequest? = null
    private var activeCompletionSignal: CompletableDeferred<Unit>? = null
    private var currentTitle = "Preparing Qur'an audio download"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDownloadStates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val reciterId = intent.getIntExtra(EXTRA_RECITER_ID, -1)
            val reciterName = intent.getStringExtra(EXTRA_RECITER_NAME).orEmpty()
            val surahNumbers = intent.getIntArrayExtra(EXTRA_SURAH_NUMBERS) ?: intArrayOf()
            if (reciterId > 0 && reciterName.isNotBlank() && surahNumbers.isNotEmpty()) {
                val reciter = AudioReciterUiModel(reciterId, reciterName)
                surahNumbers.forEach { surahNumber ->
                    if (surahNumber > 0) enqueueDownload(reciter, surahNumber)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateObserverJob?.cancel()
        queueProcessorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enqueueDownload(reciter: AudioReciterUiModel, surahNumber: Int) {
        val request = DownloadRequest(reciter, surahNumber)
        val alreadyQueued = pendingQueue.any { it.downloadKey == request.downloadKey }
        val alreadyActive = activeDownload?.downloadKey == request.downloadKey
        val currentState = audioRepository.currentDownloadState(reciter.id, surahNumber)
        if (
            alreadyQueued ||
            alreadyActive ||
            currentState is SurahDownloadState.Downloaded ||
            currentState is SurahDownloadState.Downloading ||
            currentState is SurahDownloadState.Preparing
        ) {
            return
        }
        pendingQueue += request
        publishQueueStates()
        startForeground(NOTIFICATION_ID, buildNotification(progress = null, status = queueStatus()))
        processQueue()
    }

    private fun processQueue() {
        if (queueProcessorJob?.isActive == true) return
        queueProcessorJob = serviceScope.launch(Dispatchers.IO) {
            while (activeDownload != null || pendingQueue.isNotEmpty()) {
                if (activeDownload == null) {
                    val next = pendingQueue.removeFirstOrNull() ?: break
                    activeDownload = next
                    publishQueueStates()
                    currentTitle = "Downloading ${next.reciter.name} - Surah ${next.surahNumber}"
                    activeCompletionSignal = CompletableDeferred()
                    audioRepository.downloadSurah(next.reciter, next.surahNumber)
                    activeCompletionSignal?.await()
                    activeCompletionSignal = null
                    activeDownload = null
                    publishQueueStates()
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun publishQueueStates() {
        pendingQueue.forEachIndexed { index, request ->
            audioRepository.markQueued(
                reciterId = request.reciter.id,
                surahNumber = request.surahNumber,
                position = index + 1,
            )
        }
    }

    private fun observeDownloadStates() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            audioRepository.surahDownloadStates.collectLatest { states ->
                val active = activeDownload ?: return@collectLatest
                when (val state = states[active.downloadKey]) {
                    is SurahDownloadState.Queued -> {
                        startForeground(NOTIFICATION_ID, buildNotification(progress = null, status = queueStatus()))
                    }
                    SurahDownloadState.Preparing -> {
                        startForeground(NOTIFICATION_ID, buildNotification(progress = null, status = "Preparing metadata..."))
                    }
                    is SurahDownloadState.Downloading -> {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(progress = state.progressPercent, status = "${state.progressPercent}% downloaded"),
                        )
                    }
                    SurahDownloadState.Downloaded -> {
                        startForeground(NOTIFICATION_ID, buildNotification(progress = 100, status = "Download complete"))
                        activeCompletionSignal?.complete(Unit)
                    }
                    is SurahDownloadState.Failed -> {
                        startForeground(NOTIFICATION_ID, buildNotification(progress = null, status = state.message))
                        activeCompletionSignal?.complete(Unit)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun queueStatus(): String {
        val queuedCount = pendingQueue.size
        return if (queuedCount > 0) "$queuedCount more in queue" else "Starting download..."
    }

    private fun buildNotification(progress: Int?, status: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            21,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentTitle)
            .setContentText(status)
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(progress == null || progress < 100)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, progress ?: 0, progress == null)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Qur'an audio downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background downloads for Qur'an audio"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "quran_audio_downloads"
        private const val NOTIFICATION_ID = 4042
        private const val ACTION_START_DOWNLOAD = "com.myvault.app.audio.START_DOWNLOAD"
        private const val EXTRA_RECITER_ID = "reciter_id"
        private const val EXTRA_RECITER_NAME = "reciter_name"
        private const val EXTRA_SURAH_NUMBERS = "surah_numbers"

        fun startDownload(context: Context, reciter: AudioReciterUiModel, surahNumber: Int) {
            startDownloads(context, reciter, intArrayOf(surahNumber))
        }

        fun startDownloads(context: Context, reciter: AudioReciterUiModel, surahNumbers: IntArray) {
            val intent = Intent(context, QuranAudioDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_RECITER_ID, reciter.id)
                putExtra(EXTRA_RECITER_NAME, reciter.name)
                putExtra(EXTRA_SURAH_NUMBERS, surahNumbers)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private data class DownloadRequest(
        val reciter: AudioReciterUiModel,
        val surahNumber: Int,
    ) {
        val downloadKey: String = "${reciter.id}:$surahNumber"
    }
}
