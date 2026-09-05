package com.myvault.app.data.quran.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.quran.quranCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

enum class QuranPlaybackStatus { Stopped, Preparing, Playing, Paused, Ended, Error }

data class QuranPlaybackState(
    val reciter: AudioReciterUiModel? = null,
    val surah: Int = 0,
    val verseKey: String? = null,
    val mode: QuranListeningMode = QuranListeningMode.ThisAyah,
    val status: QuranPlaybackStatus = QuranPlaybackStatus.Stopped,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val recordingId: String? = null,
    val synchronized: Boolean = false,
    val message: String? = null,
) {
    val isPlaying get() = status == QuranPlaybackStatus.Playing
    val active get() = status in setOf(QuranPlaybackStatus.Preparing, QuranPlaybackStatus.Playing, QuranPlaybackStatus.Paused)
}

@Singleton
class QuranPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val player: QuranAudioPlayer,
    private val repository: QuranAudioRepository,
    private val fullSource: QuranFullSurahSource,
    private val preferences: VaultPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(QuranPlaybackState())
    val state = mutableState.asStateFlow()
    private var preparation: Job? = null
    private var ticker: Job? = null
    private var generation = 0L
    private var timeline: QuranPlaybackTimeline? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var resumeAfterFocus = false
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) { resumeAfterFocus = false; resume() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    val wasPlaying = state.value.isPlaying
                    pause()
                    resumeAfterFocus = wasPlaying
                }
                AudioManager.AUDIOFOCUS_LOSS -> pause()
            }
        }.build()

    fun requestPlay(surah: Int, ayah: Int, reciter: AudioReciterUiModel, mode: QuranListeningMode = QuranListeningMode.ThisAyah) {
        QuranPlaybackService.start(context, QuranPlaybackService.PLAY, surah, ayah, reciter, mode)
    }

    internal fun play(surah: Int, ayah: Int, requestedReciter: AudioReciterUiModel?, mode: QuranListeningMode) {
        val catalog = quranCatalog.firstOrNull { it.num == surah } ?: return
        if (ayah !in 1..catalog.ayat) return
        val request = ++generation
        preparation?.cancel(); ticker?.cancel(); player.stop()
        timeline = null
        mutableState.value = QuranPlaybackState(reciter = requestedReciter, surah = surah, verseKey = "$surah:$ayah", mode = mode, status = QuranPlaybackStatus.Preparing)
        preparation = scope.launch {
            try {
                val prefs = preferences.userPreferences.first()
                val reciter = requestedReciter ?: repository.getSupportedReciters().let { available ->
                    available.firstOrNull { it.id == prefs.quranAudioReciterId } ?: available.firstOrNull()
                } ?: error("Choose a Qur'an reciter in MyVault first.")
                ensureActive()
                mutableState.value = state.value.copy(reciter = reciter, speed = prefs.quranAudioPlaybackSpeed.coerceIn(0.5f, 2f))
                val full = if (fullSource.supports(reciter)) {
                    try { fullSource.resolve(reciter, surah) } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        if (mode == QuranListeningMode.ContinueSurah) throw error
                        null
                    }
                } else null
                require(mode != QuranListeningMode.ContinueSurah || full != null) { "Continuous synchronized playback is unavailable for this reciter. Use This ayah." }
                val key = "$surah:$ayah"
                val file = if (full != null) full.second else {
                    val metadata = repository.getChapterAudio(reciter, surah)
                    require(metadata.mode == PlaybackMode.VerseByVerse) { "This reciter has no verified single-ayah resource." }
                    repository.ensurePlaybackFile(metadata, key)
                }
                ensureActive()
                if (request != generation) return@launch
                timeline = full?.first?.let { QuranPlaybackTimeline(it, ayah, mode) }
                mutableState.value = state.value.copy(synchronized = full != null, recordingId = full?.first?.recordingId,
                    message = if (full == null) "This ayah audio. Continuous synchronized playback is unavailable for this recording." else null)
                check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus is unavailable. Try again after other playback stops." }
                player.play(file, timeline?.target?.startMs ?: 0, state.value.speed, full == null,
                    onStarted = {
                        if (request == generation) {
                            mutableState.value = state.value.copy(status = QuranPlaybackStatus.Playing, durationMs = player.durationMs())
                            ticker = scope.launch {
                                while (request == generation && player.hasActiveMedia()) {
                                    sample()
                                    delay(50)
                                }
                            }
                        }
                    },
                    onCompleted = {
                        if (request == generation) {
                            mutableState.value = state.value.copy(status = QuranPlaybackStatus.Ended, positionMs = player.durationMs())
                            audioManager.abandonAudioFocusRequest(focusRequest)
                        }
                    },
                    onError = { if (request == generation) fail("This recording could not be played. Please retry.") })
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (request == generation) fail(error.message ?: "Audio could not be prepared.") }
        }
    }

    private fun sample() {
        if (player.isSeeking) return
        val current = state.value
        if (current.status !in setOf(QuranPlaybackStatus.Playing, QuranPlaybackStatus.Paused)) return
        val position = player.currentPositionMs()
        val policy = timeline
        if (player.isPlaying() && policy?.shouldPause(position) == true) {
            player.pause()
            player.seekTo(policy.target.endMs.coerceAtMost(player.durationMs()))
            policy.reachedBoundary()
            mutableState.value = current.copy(status = QuranPlaybackStatus.Paused, verseKey = policy.target.verseKey, positionMs = policy.target.endMs)
            return
        }
        val verse = if (policy?.boundaryReached == true) policy.target.verseKey else policy?.timing?.at(position)?.verseKey ?: current.verseKey
        mutableState.value = current.copy(positionMs = position, durationMs = player.durationMs(), verseKey = verse)
    }

    fun pause() {
        resumeAfterFocus = false
        if (state.value.status == QuranPlaybackStatus.Preparing) { stop(); return }
        player.pause()
        if (state.value.active) mutableState.value = state.value.copy(status = QuranPlaybackStatus.Paused)
    }

    fun resume() {
        if (!player.hasActiveMedia()) {
            val current = state.value
            if (current.status == QuranPlaybackStatus.Ended) current.reciter?.let {
                requestPlay(current.surah, current.verseKey?.substringAfter(':')?.toIntOrNull() ?: 1, it, current.mode)
            }
            return
        }
        if (state.value.status == QuranPlaybackStatus.Preparing) return
        if (timeline?.boundaryReached == true) {
            player.seekTo(timeline!!.target.startMs)
            timeline!!.seek(timeline!!.target.startMs)
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail("Audio focus is unavailable."); return }
        player.resume()
        mutableState.value = state.value.copy(status = QuranPlaybackStatus.Playing)
    }

    fun toggle() { if (state.value.isPlaying) pause() else resume() }

    fun setMode(mode: QuranListeningMode) {
        val policy = timeline ?: run { mutableState.value = state.value.copy(message = "This recording supports This ayah only. Continuous synchronized playback is unavailable."); return }
        val next = policy.changeMode(mode, player.currentPositionMs())
        mutableState.value = state.value.copy(mode = mode)
        if (next != null) { player.seekTo(next); resume() }
    }

    fun seek(position: Long) {
        if (!player.hasActiveMedia()) return
        val bounded = position.coerceIn(0, player.durationMs())
        timeline?.seek(bounded)
        player.seekTo(bounded)
    }

    fun adjacent(delta: Int) {
        val current = state.value
        val number = current.verseKey?.substringAfter(':')?.toIntOrNull() ?: return
        val target = number + delta
        if (target !in 1..(quranCatalog.firstOrNull { it.num == current.surah }?.ayat ?: return)) return
        val timing = timeline?.timing?.ayah(target)
        if (timing != null) { seek(timing.startMs); return }
        current.reciter?.let { requestPlay(current.surah, target, it, current.mode) }
    }

    fun speed(speed: Float) {
        val bounded = speed.coerceIn(0.5f, 2f)
        player.setPlaybackSpeed(bounded)
        mutableState.value = state.value.copy(speed = bounded)
    }

    fun stop() {
        ++generation
        preparation?.cancel(); ticker?.cancel(); player.stop()
        timeline = null
        resumeAfterFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        mutableState.value = QuranPlaybackState()
    }

    internal fun fail(message: String) {
        stop()
        mutableState.value = QuranPlaybackState(status = QuranPlaybackStatus.Error, message = message)
    }
}
