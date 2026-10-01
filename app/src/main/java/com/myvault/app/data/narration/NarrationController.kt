package com.myvault.app.data.narration

import android.os.SystemClock
import android.util.Log
import com.myvault.app.data.preferences.VaultPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationController @Inject constructor(
    private val ttsRepository: TtsRepository,
    private val geminiTtsRepository: GeminiTtsRepository,
    private val azureTtsRepository: AzureTtsRepository,
    private val deviceTtsSynthesizer: DeviceTtsSynthesizer,
    private val preferences: VaultPreferences,
    private val textPreparer: NoteNarrationTextPreparer,
    private val director: NarrationDirector,
    private val playerManager: NarrationPlayerManager,
    private val progressStore: NarrationProgressStore,
) {
    val state: StateFlow<NarrationUiState> = playerManager.state
    val azureProgress: StateFlow<Map<String, AzureNarrationProgress>> = playerManager.azureProgress
    val unifiedProgress: StateFlow<Map<String, NarrationProgress>> = progressStore.unifiedProgress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generationJob: Job? = null
    private var selectionJob: Job? = null
    private var stoppingGeneration: Job? = null
    private var lastRequest: NarrationRequest? = null
    private val demandLock = Any()
    private val demandJobs = mutableMapOf<Int, Job>()
    private var demandChunkLoader: (suspend (Int) -> Unit)? = null

    init {
        playerManager.setChunkRequestListener(::requestDemandChunk)
    }

    fun startListening(
        sourceId: String,
        title: String,
        text: String,
        provider: NarrationProvider? = null,
        voiceOverride: String? = null,
        startOffset: Int = 0,
        resume: Boolean = false,
    ) {
        scope.launch {
            val activeProvider = provider ?: NarrationProvider.fromStoredValue(preferences.userPreferences.first().narrationProvider)
            when (activeProvider) {
                NarrationProvider.GeminiFlashLite -> startGemini(sourceId, title, text, GeminiNarrationConfig.MODEL_FLASH_LITE, voiceOverride, resume)
                NarrationProvider.GeminiFlash -> startGemini(sourceId, title, text, GeminiNarrationConfig.MODEL_FLASH, voiceOverride, resume)
                NarrationProvider.Azure -> startAzure(sourceId, title, text, voiceOverride, startOffset, resume)
                NarrationProvider.Device -> startDevice(sourceId, title, text, resume = resume)
                NarrationProvider.OpenAi -> start(sourceId, title, text, voiceOverride ?: preferences.openAiNarrationVoice.first(), resume = resume)
            }
        }
    }

    fun startGemini(
        noteId: String,
        title: String,
        body: String,
        model: String = GeminiNarrationConfig.MODEL_FLASH_LITE,
        voiceOverride: String? = null,
        resume: Boolean = false,
    ) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        if (generationJob?.isActive == true) return
        val provider = if (model == GeminiNarrationConfig.MODEL_FLASH) NarrationProvider.GeminiFlash else NarrationProvider.GeminiFlashLite

        generationJob?.cancel()
        generationJob = scope.launch {
            playerManager.beginStartup(noteId)
            val geminiSettings = preferences.geminiSpeechSettings.first()
            val voice = voiceOverride?.takeIf { it.isNotBlank() } ?: geminiSettings.voice
            val current = state.value

            if (current.noteId == noteId && current.voice.equals(voice, ignoreCase = true) && current.provider == provider &&
                lastRequest?.body == body && lastRequest?.title == noteTitle
            ) {
                when (current.status) {
                    NarrationPlaybackStatus.Playing,
                    NarrationPlaybackStatus.Paused,
                    NarrationPlaybackStatus.Stopped -> {
                        playerManager.toggle()
                        return@launch
                    }
                    NarrationPlaybackStatus.Preparing,
                    NarrationPlaybackStatus.Generating -> Unit
                    else -> Unit
                }
            }

            val request = NarrationRequest(noteId, noteTitle, body, voice, provider)
            lastRequest = request

            val planStartedAt = SystemClock.elapsedRealtime()
            val plan = director.createPlan(noteId, noteTitle, body)
            Log.d(TimingTag, "source-preparation source=${noteId.take(96)} provider=${provider.storedValue} elapsedMs=${SystemClock.elapsedRealtime() - planStartedAt}")
            if (plan.fullSpokenText.isBlank()) {
                playerManager.showError(noteId, noteTitle, "This content is empty.")
                return@launch
            }

            playerManager.markPreparing(noteId, noteTitle, voice, provider)
            var firstDelivery = true
            val loader: suspend (Int) -> Unit = { requestedChunk ->
                geminiTtsRepository.generateNarrationProgressively(
                    noteId = noteId,
                    noteTitle = noteTitle,
                    plan = plan,
                    model = model,
                    voice = voice,
                    speed = state.value.speed,
                    apiKeyOverride = geminiSettings.apiKey.takeIf { it.isNotBlank() },
                    requestedChunkIndex = requestedChunk,
                    onChunkGenerating = { currentChunk, totalChunks ->
                        coroutineContext.ensureActive()
                        playerManager.markGenerating(noteId, noteTitle, currentChunk, totalChunks, voice)
                    },
                    onChunkReady = { session, _, _ ->
                        coroutineContext.ensureActive()
                        if (firstDelivery) {
                            firstDelivery = false
                            val resumePosition = playerManager.resumePositionFor(session).takeIf { resume } ?: 0L
                            playerManager.acceptDemandChunk(session, resumePosition)
                        } else {
                            playerManager.acceptDemandChunk(session)
                        }
                    },
                )
            }
            installDemandLoader(loader)
            runDemandChunk(0, loader, noteId, noteTitle, "Couldn’t generate Gemini narration.")
        }
    }

    fun start(
        noteId: String,
        title: String,
        body: String,
        voice: String = NarrationConfig.DEFAULT_VOICE,
        resume: Boolean = false,
    ) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        val normalizedVoice = voice.ifBlank { NarrationConfig.DEFAULT_VOICE }
        val current = state.value
        if (current.noteId == noteId && current.voice.equals(normalizedVoice, ignoreCase = true) && current.provider == NarrationProvider.OpenAi &&
            lastRequest?.body == body && lastRequest?.title == noteTitle
        ) {
            when (current.status) {
                NarrationPlaybackStatus.Playing,
                NarrationPlaybackStatus.Paused,
                NarrationPlaybackStatus.Stopped -> {
                    playerManager.toggle()
                    return
                }
                NarrationPlaybackStatus.Preparing,
                NarrationPlaybackStatus.Generating -> Unit
                else -> Unit
            }
        }
        if (generationJob?.isActive == true) return

        val request = NarrationRequest(noteId, noteTitle, body, normalizedVoice, NarrationProvider.OpenAi)
        lastRequest = request
        generationJob?.cancel()
        generationJob = scope.launch {
            playerManager.beginStartup(noteId)
            val planStartedAt = SystemClock.elapsedRealtime()
            val plan = director.createPlan(noteId, noteTitle, body)
            Log.d(TimingTag, "source-preparation source=${noteId.take(96)} provider=${NarrationProvider.OpenAi.storedValue} elapsedMs=${SystemClock.elapsedRealtime() - planStartedAt}")
            val narrationText = plan.fullSpokenText
            if (narrationText.isBlank()) {
                playerManager.showError(request.noteId, request.title, "This note is empty.")
                return@launch
            }
            playerManager.markPreparing(request.noteId, request.title, request.voice, request.provider)
            var firstDelivery = true
            val loader: suspend (Int) -> Unit = { requestedChunk ->
                ttsRepository.generateNarrationProgressively(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    narrationText = narrationText,
                    voice = request.voice,
                    speed = state.value.speed,
                    requestedChunkIndex = requestedChunk,
                    onChunkGenerating = { currentChunk, totalChunks ->
                        coroutineContext.ensureActive()
                        playerManager.markGenerating(request.noteId, request.title, currentChunk, totalChunks, request.voice)
                    },
                    onChunkReady = { session, _, _ ->
                        coroutineContext.ensureActive()
                        if (firstDelivery) {
                            firstDelivery = false
                            val resumePosition = playerManager.resumePositionFor(session).takeIf { resume } ?: 0L
                            playerManager.acceptDemandChunk(session, resumePosition)
                        } else {
                            playerManager.acceptDemandChunk(session)
                        }
                    },
                )
            }
            installDemandLoader(loader)
            runDemandChunk(0, loader, request.noteId, request.title, "Couldn’t generate narration.")
        }
    }

    fun startAzure(
        noteId: String,
        title: String,
        body: String,
        voiceOverride: String? = null,
        bodyStartOffset: Int = 0,
        resume: Boolean = false,
    ) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        val safeStartOffset = body.paragraphStartAt(bodyStartOffset)
        val narrationTitle = if (safeStartOffset > 0) "" else noteTitle
        val narrationBody = body.substring(safeStartOffset)
        if (generationJob?.isActive == true) return
        generationJob?.cancel()
        generationJob = scope.launch {
            playerManager.beginStartup(noteId)
            val settings = preferences.azureSpeechSettings.first()
            val voice = voiceOverride?.takeIf { it.isNotBlank() } ?: settings.voice
            val current = state.value
            if (safeStartOffset == 0 && current.noteId == noteId && current.voice.equals(voice, ignoreCase = true) && current.provider == NarrationProvider.Azure &&
                lastRequest?.body == narrationBody && lastRequest?.title == noteTitle
            ) {
                when (current.status) {
                    NarrationPlaybackStatus.Playing,
                    NarrationPlaybackStatus.Paused,
                    NarrationPlaybackStatus.Stopped -> {
                        playerManager.toggle()
                        return@launch
                    }
                    NarrationPlaybackStatus.Preparing,
                    NarrationPlaybackStatus.Generating -> Unit
                    else -> Unit
                }
            }
            val request = NarrationRequest(noteId, noteTitle, narrationBody, voice, NarrationProvider.Azure)
            lastRequest = request
            val preparationStartedAt = SystemClock.elapsedRealtime()
            val narrationText = textPreparer.prepare(narrationTitle, request.body)
            Log.d(TimingTag, "source-preparation source=${noteId.take(96)} provider=${NarrationProvider.Azure.storedValue} elapsedMs=${SystemClock.elapsedRealtime() - preparationStartedAt}")
            if (narrationText.isBlank()) {
                playerManager.showError(request.noteId, request.title, "This note is empty.")
                return@launch
            }
            playerManager.markPreparing(request.noteId, request.title, request.voice, request.provider)
            var firstDelivery = true
            val loader: suspend (Int) -> Unit = { requestedChunk ->
                azureTtsRepository.generateNarrationProgressively(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    narrationText = narrationText,
                    apiKey = settings.apiKey,
                    region = settings.region,
                    voice = request.voice,
                    arabicVoice = settings.arabicVoice,
                    speed = state.value.speed,
                    requestedChunkIndex = requestedChunk,
                    onChunkGenerating = { currentChunk, totalChunks ->
                        coroutineContext.ensureActive()
                        playerManager.markGenerating(request.noteId, request.title, currentChunk, totalChunks, request.voice)
                    },
                    onChunkReady = { session, _, _ ->
                        coroutineContext.ensureActive()
                        if (firstDelivery) {
                            firstDelivery = false
                            val resumePosition = playerManager.resumePositionFor(session).takeIf { resume } ?: 0L
                            playerManager.acceptDemandChunk(session, resumePosition)
                        } else {
                            playerManager.acceptDemandChunk(session)
                        }
                    },
                )
            }
            installDemandLoader(loader)
            runDemandChunk(0, loader, request.noteId, request.title, "Couldn’t generate Azure narration.")
        }
    }

    fun startDevice(noteId: String, title: String, body: String, resume: Boolean = false) {
        val noteTitle = title.trim().ifBlank { "Untitled note" }
        val current = state.value
        if (current.noteId == noteId && current.voice == DeviceNarrationVoice &&
            lastRequest?.body == body && lastRequest?.title == noteTitle
        ) {
            when (current.status) {
                NarrationPlaybackStatus.Playing,
                NarrationPlaybackStatus.Paused,
                NarrationPlaybackStatus.Stopped -> {
                    playerManager.toggle()
                    return
                }
                NarrationPlaybackStatus.Preparing,
                NarrationPlaybackStatus.Generating -> Unit
                else -> Unit
            }
        }
        if (generationJob?.isActive == true) return
        clearDemandLoader()
        val request = NarrationRequest(noteId, noteTitle, body, DeviceNarrationVoice, NarrationProvider.Device)
        lastRequest = request
        generationJob?.cancel()
        generationJob = scope.launch {
            playerManager.beginStartup(noteId)
            val planStartedAt = SystemClock.elapsedRealtime()
            val plan = director.createPlan(noteId, noteTitle, body)
            Log.d(TimingTag, "source-preparation source=${noteId.take(96)} provider=${NarrationProvider.Device.storedValue} elapsedMs=${SystemClock.elapsedRealtime() - planStartedAt}")
            val narrationText = plan.fullSpokenText
            if (narrationText.isBlank()) {
                playerManager.showError(noteId, noteTitle, "This note is empty.")
                return@launch
            }
            playerManager.markPreparing(noteId, noteTitle, DeviceNarrationVoice, request.provider)
            var playbackStarted = false
            runCatching {
                deviceTtsSynthesizer.synthesizeProgressively(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    narrationText = narrationText,
                    speed = state.value.speed,
                    onChunkGenerating = { currentChunk, totalChunks ->
                        coroutineContext.ensureActive()
                        playerManager.markGenerating(request.noteId, request.title, currentChunk, totalChunks, request.voice)
                    },
                    onChunkReady = { session, isComplete, totalChunks ->
                        coroutineContext.ensureActive()
                        if (!playbackStarted) {
                            playbackStarted = true
                            val resumePosition = playerManager.resumePositionFor(session).takeIf { resume } ?: 0L
                            playerManager.startStreaming(session, totalChunks = totalChunks, initialPositionMs = resumePosition)
                        } else {
                            playerManager.appendStreamingChunk(session, totalChunks = totalChunks)
                        }
                        if (isComplete) playerManager.finishStreaming(session)
                    },
                )
            }.onFailure { error ->
                coroutineContext.ensureActive()
                if (error is CancellationException) return@launch
                playerManager.showError(
                    noteId = request.noteId,
                    noteTitle = request.title,
                    message = error.message?.takeIf { it.isNotBlank() } ?: "Couldn’t generate device narration.",
                )
            }
        }
    }

    private fun installDemandLoader(loader: suspend (Int) -> Unit) {
        val oldJobs = synchronized(demandLock) {
            val jobs = demandJobs.values.toList()
            demandJobs.clear()
            demandChunkLoader = loader
            jobs
        }
        oldJobs.forEach(Job::cancel)
    }

    private fun clearDemandLoader() {
        val oldJobs = synchronized(demandLock) {
            val jobs = demandJobs.values.toList()
            demandJobs.clear()
            demandChunkLoader = null
            jobs
        }
        oldJobs.forEach(Job::cancel)
    }

    private fun requestDemandChunk(chunkIndex: Int) {
        val loader = synchronized(demandLock) {
            if (demandJobs[chunkIndex]?.isActive == true) {
                Log.d(TimingTag, "duplicate-request-prevented chunk=${chunkIndex + 1} controller=true")
                return
            }
            demandChunkLoader
        } ?: return
        val source = lastRequest
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                runDemandChunk(
                    chunkIndex = chunkIndex,
                    loader = loader,
                    noteId = source?.noteId.orEmpty(),
                    noteTitle = source?.title.orEmpty(),
                    fallbackMessage = "Couldn’t generate the requested narration part.",
                )
            } finally {
                synchronized(demandLock) { demandJobs.remove(chunkIndex) }
            }
        }
        synchronized(demandLock) { demandJobs[chunkIndex] = job }
        job.start()
    }

    private suspend fun runDemandChunk(
        chunkIndex: Int,
        loader: suspend (Int) -> Unit,
        noteId: String,
        noteTitle: String,
        fallbackMessage: String,
    ) {
        try {
            loader(chunkIndex)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            playerManager.releaseChunkRequest(chunkIndex)
            playerManager.showError(noteId, noteTitle, error.message?.takeIf { it.isNotBlank() } ?: fallbackMessage)
        }
    }

    fun restartWithVoice(voice: String) {
        val provider = lastRequest?.provider ?: return
        if (voice !in provider.voiceOptions) return
        restartSelection(provider, voice)
    }

    fun restartWithProvider(provider: NarrationProvider) {
        restartSelection(provider)
    }

    private fun restartSelection(provider: NarrationProvider, voiceOverride: String? = null) {
        val request = lastRequest ?: return
        selectionJob?.cancel()
        val previousGeneration = generationJob ?: stoppingGeneration
        stoppingGeneration = previousGeneration
        stop(resetLastRequest = false)
        val immediateVoice = provider.resolveVoice(voiceOverride ?: request.voice)
        lastRequest = request.copy(provider = provider, voice = immediateVoice)
        playerManager.markPreparing(request.noteId, request.title, immediateVoice, provider)
        selectionJob = scope.launch {
            // Finish cancelling the previous synthesis before reusing its cache/staging paths.
            previousGeneration?.join()
            if (stoppingGeneration === previousGeneration) stoppingGeneration = null
            val voice = voiceOverride ?: when (provider) {
                NarrationProvider.GeminiFlashLite, NarrationProvider.GeminiFlash -> preferences.geminiSpeechSettings.first().voice
                NarrationProvider.OpenAi -> preferences.openAiNarrationVoice.first()
                NarrationProvider.Azure -> preferences.azureSpeechSettings.first().voice
                NarrationProvider.Device -> DeviceNarrationVoice
            }
            val selectedVoice = provider.resolveVoice(voice)
            preferences.setNarrationSelection(provider, selectedVoice)
            coroutineContext.ensureActive()
            lastRequest = request.copy(provider = provider, voice = selectedVoice)
            when (provider) {
                NarrationProvider.GeminiFlashLite, NarrationProvider.GeminiFlash ->
                    startGemini(request.noteId, request.title, request.body, provider.defaultModel, selectedVoice)
                NarrationProvider.Device -> startDevice(request.noteId, request.title, request.body)
                NarrationProvider.OpenAi -> start(request.noteId, request.title, request.body, selectedVoice)
                NarrationProvider.Azure -> startAzure(request.noteId, request.title, request.body, selectedVoice)
            }
        }
    }

    fun toggle() {
        playerManager.toggle()
    }

    fun pause() {
        playerManager.pause()
    }

    fun resume() {
        playerManager.resume()
    }

    fun stop(resetLastRequest: Boolean = true) {
        if (resetLastRequest) selectionJob?.cancel()
        generationJob?.cancel()
        generationJob = null
        clearDemandLoader()
        playerManager.stop()
        if (resetLastRequest) lastRequest = null
    }

    fun rewind10s() {
        playerManager.rewind10s()
    }

    fun forward10s() {
        playerManager.forward10s()
    }

    fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipBy(deltaMs: Long) {
        playerManager.seekTo(state.value.totalPositionMs + deltaMs)
    }

    fun refreshProgress() {
        playerManager.refreshProgress()
    }

    fun saveProgress() {
        playerManager.saveProgress()
    }

    fun progressFor(sourceId: String) = azureProgress.map { it[sourceId] }

    fun unifiedProgressFor(sourceId: String) = unifiedProgress.map { it[sourceId] }

    private data class NarrationRequest(
        val noteId: String,
        val title: String,
        val body: String,
        val voice: String,
        val provider: NarrationProvider,
    )

    private companion object {
        const val DeviceNarrationVoice = "device"
        const val TimingTag = "MyVaultNarrationTiming"
    }
}

private fun String.paragraphStartAt(offset: Int): Int {
    val safeOffset = offset.coerceIn(0, length)
    if (safeOffset == 0) return 0
    val preceding = substring(0, safeOffset)
    val paragraphBreak = Regex("\\n\\s*\\n").findAll(preceding).lastOrNull()
    return paragraphBreak?.range?.last?.plus(1) ?: 0
}
