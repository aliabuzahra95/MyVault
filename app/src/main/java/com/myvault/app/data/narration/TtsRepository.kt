package com.myvault.app.data.narration

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
) {
    suspend fun generateNarrationProgressively(
        noteId: String,
        noteTitle: String,
        narrationText: String,
        voice: String = NarrationConfig.DEFAULT_VOICE,
        speed: Float = 1f,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val cleanText = narrationText.trim()
        if (cleanText.isBlank()) error("This note is empty.")
        if (cleanText.length > NarrationConfig.MAX_TOTAL_CHARS) {
            error("This note is too long for Listen Mode. Please shorten it below ${NarrationConfig.MAX_TOTAL_CHARS} characters before generating narration.")
        }
        val contentHash = cacheManager.contentHash(cleanText)
        val clampedSpeed = speed.coerceIn(0.75f, 1.5f)
        val normalizedVoice = voice.ifBlank { NarrationConfig.DEFAULT_VOICE }
        // Speed is handled by MediaPlayer playback params so the same generated MP3 can be reused
        // across playback speeds without paying for another TTS request.
        val cacheKey = cacheManager.cacheKey(noteId, contentHash, NarrationConfig.MODEL, normalizedVoice, 1f)
        cacheManager.cachedSessionOrNull(cacheKey, noteId, noteTitle, NarrationConfig.MODEL, normalizedVoice, clampedSpeed, contentHash)?.let {
            OpenAiRequestGuard.logCacheDecision(
                featureName = OpenAiFeature.ListenMode,
                endpointUrl = SpeechEndpoint,
                model = NarrationConfig.MODEL,
                noteId = noteId,
                characterCount = cleanText.length,
                cacheStatus = "hit:session",
            )
            onChunkReady(it, true, it.files.size)
            return@withContext it
        }
        OpenAiRequestGuard.logCacheDecision(
            featureName = OpenAiFeature.ListenMode,
            endpointUrl = SpeechEndpoint,
            model = NarrationConfig.MODEL,
            noteId = noteId,
            characterCount = cleanText.length,
            cacheStatus = "miss:session",
        )

        var apiKey: String? = null

        val chunks = textPreparer.splitIntoChunks(cleanText)
        if (chunks.isEmpty()) error("This note is empty.")
        val generatedFiles = mutableListOf<File>()
        runCatching {
            chunks.forEachIndexed { index, chunk ->
                coroutineContext.ensureActive()
                val target = cacheManager.chunkFile(cacheKey, index)
                if (target.exists() && target.length() >= MinValidMp3Bytes) {
                    OpenAiRequestGuard.logCacheDecision(
                        featureName = OpenAiFeature.ListenMode,
                        endpointUrl = SpeechEndpoint,
                        model = NarrationConfig.MODEL,
                        noteId = noteId,
                        characterCount = chunk.length,
                        cacheStatus = "hit:chunk-${index + 1}",
                    )
                } else {
                    onChunkGenerating(index + 1, chunks.size)
                    val resolvedApiKey = apiKey ?: BuildConfig.OPENAI_API_KEY.trim().also { resolved ->
                        if (resolved.isBlank()) {
                            error("OpenAI narration is not configured. Add MYVAULT_OPENAI_API_KEY to the project or build environment.")
                        }
                        apiKey = resolved
                    }
                    OpenAiRequestGuard.validateAndLogRequest(
                        featureName = OpenAiFeature.ListenMode,
                        endpointUrl = SpeechEndpoint,
                        model = NarrationConfig.MODEL,
                        noteId = noteId,
                        characterCount = chunk.length,
                        cacheStatus = "miss:chunk-${index + 1}",
                    )
                    requestSpeechWithRetry(resolvedApiKey, chunk, normalizedVoice, target, index + 1)
                }
                generatedFiles += target
                val partialSession = NarrationSession(
                    cacheKey = cacheKey,
                    noteId = noteId,
                    noteTitle = noteTitle,
                    model = NarrationConfig.MODEL,
                    voice = normalizedVoice,
                    speed = clampedSpeed,
                    contentHash = contentHash,
                    files = generatedFiles.toList(),
                )
                cacheManager.writeManifest(partialSession, isComplete = index == chunks.lastIndex, totalChunks = chunks.size)
                onChunkReady(partialSession, index == chunks.lastIndex, chunks.size)
            }
        }.onFailure { error ->
            throw error
        }
        NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = NarrationConfig.MODEL,
            voice = normalizedVoice,
            speed = clampedSpeed,
            contentHash = contentHash,
            files = generatedFiles.toList(),
        ).also { cacheManager.writeManifest(it, isComplete = true, totalChunks = generatedFiles.size) }
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
    }
}
