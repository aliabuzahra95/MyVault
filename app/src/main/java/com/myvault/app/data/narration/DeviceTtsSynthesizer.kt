package com.myvault.app.data.narration

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class DeviceTtsSynthesizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cacheManager: NarrationCacheManager,
    private val textPreparer: NoteNarrationTextPreparer,
    private val segmenter: BilingualTextSegmenter,
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()
    private val utteranceCounter = AtomicLong(0)

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            val ready = status == TextToSpeech.SUCCESS
            isInitialized = ready
            if (!initDeferred.isCompleted) {
                initDeferred.complete(ready)
            }
        }
    }

    private suspend fun ensureReady(): Boolean {
        if (isInitialized && textToSpeech != null) return true
        return withTimeoutOrNull(InitializationTimeoutMs) {
            initDeferred.await()
        } ?: false
    }

    suspend fun synthesizeProgressively(
        noteId: String,
        noteTitle: String,
        narrationText: String,
        speed: Float = 1f,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val ready = ensureReady()
        if (!ready) {
            error("Device text-to-speech engine is not available on this device.")
        }

        val cleanText = narrationText.trim()
        if (cleanText.isBlank()) error("This note is empty.")

        val tts = textToSpeech ?: error("Device TTS is unavailable.")
        val contentHash = cacheManager.contentHash(cleanText)
        val clampedSpeed = speed.coerceIn(0.75f, 2.0f)
        val model = "device-tts-local"
        val voice = "device"
        val cacheKey = cacheManager.cacheKey(noteId, contentHash, model, voice, 1f)

        // Check if full session is already cached
        cacheManager.cachedSessionOrNull(cacheKey, noteId, noteTitle, model, voice, clampedSpeed, contentHash)?.let {
            onChunkReady(it, true, it.files.size)
            return@withContext it
        }

        val chunks = textPreparer.splitIntoChunks(cleanText, maxChars = DeviceChunkMaxChars)
        if (chunks.isEmpty()) error("This note is empty.")

        val generatedFiles = mutableListOf<File>()
        val dir = cacheManager.sessionDir(cacheKey)

        chunks.forEachIndexed { index, chunk ->
            coroutineContext.ensureActive()
            val chunkFile = File(dir, "chunk_${index.toString().padStart(3, '0')}.wav")

            if (chunkFile.exists() && chunkFile.length() >= MinValidAudioBytes) {
                generatedFiles += chunkFile
            } else {
                onChunkGenerating(index + 1, chunks.size)
                synthesizeChunkToFile(tts, chunk, chunkFile)
                if (!chunkFile.exists() || chunkFile.length() < MinValidAudioBytes) {
                    error("Device TTS failed to synthesize audio for part ${index + 1}.")
                }
                generatedFiles += chunkFile
            }

            val session = NarrationSession(
                cacheKey = cacheKey,
                noteId = noteId,
                noteTitle = noteTitle,
                model = model,
                voice = voice,
                speed = clampedSpeed,
                contentHash = contentHash,
                files = generatedFiles.toList(),
            )
            cacheManager.writeManifest(session, isComplete = index == chunks.lastIndex, totalChunks = chunks.size)
            onChunkReady(session, index == chunks.lastIndex, chunks.size)
        }

        NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = model,
            voice = voice,
            speed = clampedSpeed,
            contentHash = contentHash,
            files = generatedFiles.toList(),
        ).also { cacheManager.writeManifest(it, isComplete = true, totalChunks = generatedFiles.size) }
    }

    private suspend fun synthesizeChunkToFile(
        tts: TextToSpeech,
        chunkText: String,
        targetFile: File,
    ) {
        val segments = segmenter.segmentText(chunkText)
        val isArabic = segments.any { it.language == NarrationLanguage.Arabic }

        // Explicitly set language locale before synthesis
        if (isArabic) {
            val arLocale = Locale.forLanguageTag("ar-SA")
            val available = tts.isLanguageAvailable(arLocale)
            if (available >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = arLocale
            } else {
                tts.language = Locale.forLanguageTag("ar")
            }
        } else {
            val enLocale = Locale.forLanguageTag("en-AU")
            val available = tts.isLanguageAvailable(enLocale)
            if (available >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = enLocale
            } else {
                tts.language = Locale.ENGLISH
            }
        }

        val utteranceId = "device-tts-${utteranceCounter.incrementAndGet()}"
        val synthesisDeferred = CompletableDeferred<Boolean>()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    synthesisDeferred.complete(false)
                }
            }
        })

        if (targetFile.exists()) targetFile.delete()
        val params = Bundle()
        val result = tts.synthesizeToFile(chunkText, params, targetFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            error("Device text-to-speech could not begin synthesis.")
        }

        val completed = withTimeoutOrNull(SynthesisTimeoutPerChunkMs) {
            synthesisDeferred.await()
        } ?: false

        if (!completed) {
            error("Device TTS synthesis timed out.")
        }
    }

    companion object {
        private const val InitializationTimeoutMs = 5_000L
        private const val SynthesisTimeoutPerChunkMs = 30_000L
        private const val DeviceChunkMaxChars = 1_000
        private const val MinValidAudioBytes = 256L
    }
}
