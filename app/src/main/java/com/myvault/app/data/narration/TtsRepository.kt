package com.myvault.app.data.narration

import android.os.SystemClock
import android.util.Log
import com.myvault.app.BuildConfig
import com.myvault.app.data.openai.OpenAiFeature
import com.myvault.app.data.openai.OpenAiRequestGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class TtsRepository @Inject constructor(
    private val cacheManager: NarrationCacheManager,
    private val textPreparer: NoteNarrationTextPreparer,
    private val cueBuilder: NarrationCueBuilder,
) {
    suspend fun generateNarrationProgressively(
        noteId: String,
        noteTitle: String,
        narrationText: String,
        voice: String = NarrationConfig.DEFAULT_VOICE,
        speed: Float = 1f,
        requestedChunkIndex: Int = 0,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val cleanText = narrationText.trim()
        if (cleanText.isBlank()) error("This note is empty.")
        val contentHash = cacheManager.contentHash(cleanText)
        val clampedSpeed = speed.coerceIn(0.75f, 1.5f)
        val normalizedVoice = voice.ifBlank { NarrationConfig.DEFAULT_VOICE }
        // Speed is handled by MediaPlayer playback params so the same generated MP3 can be reused
        // across playback speeds without paying for another TTS request.
        val cacheKey = cacheManager.cacheKey(noteId, contentHash, NarrationConfig.MODEL, normalizedVoice, 1f)
        val chunkPlanStartedAt = SystemClock.elapsedRealtime()
        val chunks = textPreparer.splitIntoChunks(cleanText, CloudNarrationChunkChars)
        if (chunks.isEmpty()) error("This note is empty.")
        Log.d(TimingTag, "chunk-planning source=${noteId.take(96)} provider=${NarrationProvider.OpenAi.storedValue} chunks=${chunks.size} elapsedMs=${SystemClock.elapsedRealtime() - chunkPlanStartedAt}")
        val index = requestedChunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[index]
        val chunkCacheKey = cacheManager.renditionChunkKey(noteId, chunk, NarrationConfig.MODEL, normalizedVoice)
        Log.d(TimingTag, "chunk-requested source=${noteId.take(96)} provider=${NarrationProvider.OpenAi.storedValue} chunk=${index + 1}/${chunks.size}")
        val legacyTarget = cacheManager.cachedChunkOrNull(
            cacheKey, index, noteId, NarrationConfig.MODEL, normalizedVoice, minimumBytes = MinValidMp3Bytes,
        )
        val cachedTarget = legacyTarget ?: cacheManager.cachedChunkOrNull(
            chunkCacheKey, 0, noteId, NarrationConfig.MODEL, normalizedVoice, minimumBytes = MinValidMp3Bytes,
        )
        val cueCacheKey = if (legacyTarget != null) cacheKey else chunkCacheKey
        val cueCacheIndex = if (legacyTarget != null) index else 0
        val target = cachedTarget ?: cacheManager.chunkFile(chunkCacheKey, 0)
        if (cachedTarget == null) {
            onChunkGenerating(index + 1, chunks.size)
            val apiKey = BuildConfig.OPENAI_API_KEY.trim().takeIf { it.isNotBlank() }
                ?: error("OpenAI narration is not configured. Add MYVAULT_OPENAI_API_KEY to the project or build environment.")
            OpenAiRequestGuard.validateAndLogRequest(
                featureName = OpenAiFeature.ListenMode,
                endpointUrl = SpeechEndpoint,
                model = NarrationConfig.MODEL,
                noteId = noteId,
                characterCount = chunk.length,
                cacheStatus = "miss:chunk-${index + 1}",
            )
            Log.d(TimingTag, "generation-started source=${noteId.take(96)} chunk=${index + 1}")
            val requestStartedAt = SystemClock.elapsedRealtime()
            requestSpeechWithRetry(apiKey, chunk, normalizedVoice, target, index + 1)
            Log.d(TimingTag, "generation-completed source=${noteId.take(96)} chunk=${index + 1} elapsedMs=${SystemClock.elapsedRealtime() - requestStartedAt} bytes=${target.length()}")
        } else {
            OpenAiRequestGuard.logCacheDecision(
                featureName = OpenAiFeature.ListenMode,
                endpointUrl = SpeechEndpoint,
                model = NarrationConfig.MODEL,
                noteId = noteId,
                characterCount = chunk.length,
                cacheStatus = "hit:chunk-${index + 1}",
            )
        }
        coroutineContext.ensureActive()
        val cues = cacheManager.readChunkCues(cueCacheKey, cueCacheIndex)
            .map { it.copy(chunkIndex = index) }
            .ifEmpty {
                cueBuilder.buildEstimatedCues(index, chunk, target).also {
                    cacheManager.writeChunkCues(cueCacheKey, cueCacheIndex, it)
                }
            }
        NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = NarrationConfig.MODEL,
            voice = normalizedVoice,
            speed = clampedSpeed,
            contentHash = contentHash,
            files = listOf(target),
            cues = cues,
            chunkIndices = listOf(index),
            totalChunks = chunks.size,
            demandDriven = true,
        ).also { session -> onChunkReady(session, index == chunks.lastIndex, chunks.size) }
    }

    private fun requestSpeechWithRetry(apiKey: String, input: String, voice: String, target: File, partNumber: Int) {
        var lastError: Throwable? = null
        repeat(MaxAttempts) { attempt ->
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.delete()
            runCatching {
                requestSpeechOnce(apiKey, input, voice, temp)
                if (temp.length() < MinValidMp3Bytes) {
                    error("OpenAI returned an empty narration audio file for part $partNumber.")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return
            }.onFailure { error ->
                lastError = error
                temp.delete()
                if (attempt == MaxAttempts - 1) throw error
            }
        }
        throw lastError ?: IllegalStateException("Couldn’t generate narration part $partNumber.")
    }

    private fun requestSpeechOnce(apiKey: String, input: String, voice: String, target: File) {
        val connection = (URL(SpeechEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "audio/mpeg")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Connection", "close")
        }
        try {
            val payload = JSONObject()
                .put("model", NarrationConfig.MODEL)
                .put("voice", voice)
                .put("input", input)
                .put("instructions", "Speak in a calm, clear lecture pace suitable for study notes.")
                .put("response_format", NarrationConfig.RESPONSE_FORMAT)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "OpenAI narration request failed ($code)."
                error(message)
            }
            connection.inputStream.use { inputStream ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BufferSize)
                    var written = 0L
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                    }
                    output.fd.sync()
                    if (written <= 0L) error("OpenAI returned empty narration audio.")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SpeechEndpoint = "https://api.openai.com/v1/audio/speech"
        const val MaxAttempts = 2
        const val MinValidMp3Bytes = 512L
        const val BufferSize = 32 * 1024
        const val TimingTag = "MyVaultNarrationTiming"
    }
}
