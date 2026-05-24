package com.myvault.app.data.narration

import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsRepository @Inject constructor(
    private val cacheManager: NarrationCacheManager,
    private val textPreparer: NoteNarrationTextPreparer,
) {
    suspend fun getOrCreateNarration(
        noteId: String,
        noteTitle: String,
        narrationText: String,
        voice: String = NarrationConfig.DEFAULT_VOICE,
        speed: Float = 1f,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): NarrationSession = withContext(Dispatchers.IO) {
        val cleanText = narrationText.trim()
        if (cleanText.isBlank()) error("This note is empty.")
        val contentHash = cacheManager.contentHash(cleanText)
        val clampedSpeed = speed.coerceIn(0.75f, 1.5f)
        // Speed is handled by MediaPlayer playback params so the same generated MP3 can be reused
        // across playback speeds without paying for another TTS request.
        val cacheKey = cacheManager.cacheKey(noteId, contentHash, NarrationConfig.MODEL, voice, 1f)
        cacheManager.cachedSessionOrNull(cacheKey, noteId, noteTitle, NarrationConfig.MODEL, voice, clampedSpeed, contentHash)?.let { return@withContext it }

        val apiKey = BuildConfig.OPENAI_API_KEY.trim()
        if (apiKey.isBlank()) {
            error("OpenAI narration is not configured. Add MYVAULT_OPENAI_API_KEY to the project or build environment.")
        }

        val chunks = textPreparer.splitIntoChunks(cleanText)
        if (chunks.isEmpty()) error("This note is empty.")
        cacheManager.clearSession(cacheKey)
        val generatedFiles = mutableListOf<File>()
        runCatching {
            chunks.forEachIndexed { index, chunk ->
                onChunkGenerating(index + 1, chunks.size)
                val target = cacheManager.chunkFile(cacheKey, index)
                requestSpeech(apiKey, chunk, voice, target)
                generatedFiles += target
            }
        }.onFailure { error ->
            cacheManager.clearSession(cacheKey)
            throw error
        }
        NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = NarrationConfig.MODEL,
            voice = voice,
            speed = clampedSpeed,
            contentHash = contentHash,
            files = generatedFiles.toList(),
        ).also(cacheManager::writeManifest)
    }

    private fun requestSpeech(apiKey: String, input: String, voice: String, target: File) {
        val connection = (URL("https://api.openai.com/v1/audio/speech").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg")
        }
        val payload = JSONObject()
            .put("model", NarrationConfig.MODEL)
            .put("voice", voice)
            .put("input", input)
            .put("instructions", "Read this study note naturally and calmly as long-form narration. Preserve Arabic and Islamic terms as written. Do not summarize or add commentary.")
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
        val temp = File(target.parentFile, "${target.name}.tmp")
        connection.inputStream.use { inputStream ->
            temp.outputStream().use { output -> inputStream.copyTo(output) }
        }
        if (temp.length() <= 0L) {
            temp.delete()
            error("OpenAI returned empty narration audio.")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
