package com.myvault.app.data.narration

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val ConnectTimeoutMs = 15_000
private const val ReadTimeoutMs = 60_000
private const val MaxAttempts = 3
private const val MinValidAudioBytes = 512L
private const val TimingTag = "MyVaultNarrationTiming"

@Singleton
class GeminiTtsRepository @Inject constructor(
    private val cacheManager: NarrationCacheManager,
    private val director: NarrationDirector,
    private val cueBuilder: NarrationCueBuilder,
) {

    suspend fun generateNarrationProgressively(
        noteId: String,
        noteTitle: String,
        plan: NarrationPlan,
        model: String = GeminiNarrationConfig.MODEL_FLASH_LITE,
        voice: String = GeminiNarrationConfig.DEFAULT_VOICE,
        speed: Float = 1f,
        requestedChunkIndex: Int = 0,
        resumeProgress: NarrationProgress? = null,
        apiKeyOverride: String? = null,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val cleanText = plan.fullSpokenText.trim()
        if (cleanText.isBlank()) error("This content is empty.")

        val contentHash = cacheManager.contentHash(cleanText)
        val clampedSpeed = speed.coerceIn(0.75f, 2.0f)
        val normalizedVoice = voice.ifBlank { GeminiNarrationConfig.DEFAULT_VOICE }
        val effectiveModel = model.ifBlank { GeminiNarrationConfig.MODEL_FLASH_LITE }

        val cacheKey = cacheManager.cacheKey(noteId, contentHash, effectiveModel, normalizedVoice, 1f)

        val chunkPlanStartedAt = SystemClock.elapsedRealtime()
        val chunks = cacheManager.restoreTimeline(cacheKey, director.planToChunks(plan), noteId, effectiveModel, normalizedVoice)
        if (chunks.isEmpty()) error("This content is empty.")
        Log.d(TimingTag, "chunk-planning source=${noteId.take(96)} provider=${NarrationProvider.fromModel(effectiveModel).storedValue} chunks=${chunks.size} elapsedMs=${SystemClock.elapsedRealtime() - chunkPlanStartedAt}")
        val resumeTarget = if (requestedChunkIndex < 0) {
            val position = resumeProgress?.takeIf { it.cacheKey == cacheKey && !it.isStaleFor(contentHash) }?.positionMs ?: 0L
            NarrationTimeline.seekTarget(position, chunks)
        } else null
        val index = resumeTarget?.chunkIndex ?: requestedChunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[index]
        val chunkCacheKey = cacheManager.renditionChunkKey(noteId, chunk.text, effectiveModel, normalizedVoice)
        Log.d(TimingTag, "chunk-requested source=${noteId.take(96)} provider=${NarrationProvider.fromModel(effectiveModel).storedValue} chunk=${index + 1}/${chunks.size}")
        val legacyTarget = cacheManager.cachedChunkOrNull(
            cacheKey, index, noteId, effectiveModel, normalizedVoice, minimumBytes = MinValidAudioBytes,
        )
        val cachedTarget = legacyTarget ?: cacheManager.cachedChunkOrNull(
            chunkCacheKey, 0, noteId, effectiveModel, normalizedVoice, minimumBytes = MinValidAudioBytes,
        )
        val cueCacheKey = if (legacyTarget != null) cacheKey else chunkCacheKey
        val cueCacheIndex = if (legacyTarget != null) index else 0
        val target = cachedTarget ?: cacheManager.chunkFile(chunkCacheKey, 0)
        cacheManager.withChunkLock(chunkCacheKey) {
            coroutineContext.ensureActive()
            if (cachedTarget == null && (!target.exists() || target.length() < MinValidAudioBytes)) {
                onChunkGenerating(index + 1, chunks.size)
                val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() }
                    ?: BuildConfig.GEMINI_API_KEY.trim().takeIf { it.isNotBlank() }
                    ?: error("Gemini narration is not configured. Add MYVAULT_GEMINI_API_KEY to local.properties or settings.")
                Log.d(TimingTag, "generation-started source=${noteId.take(96)} chunk=${index + 1}")
                val requestStartedAt = SystemClock.elapsedRealtime()
                requestSpeechWithRetry(apiKey, effectiveModel, chunk.text, normalizedVoice, target, index + 1)
                Log.d(TimingTag, "generation-completed source=${noteId.take(96)} chunk=${index + 1} elapsedMs=${SystemClock.elapsedRealtime() - requestStartedAt} bytes=${target.length()}")
            }
        }
        coroutineContext.ensureActive()
        val cues = cacheManager.readChunkCues(cueCacheKey, cueCacheIndex)
            .map { it.copy(chunkIndex = index) }
            .ifEmpty {
                cueBuilder.buildEstimatedCues(index, chunk.text, target).also {
                    cacheManager.writeChunkCues(cueCacheKey, cueCacheIndex, it)
                }
            }
        val session = NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = effectiveModel,
            voice = normalizedVoice,
            speed = clampedSpeed,
            contentHash = contentHash,
            files = listOf(target),
            cues = cues,
            chunkIndices = listOf(index),
            totalChunks = chunks.size,
            demandDriven = true,
            chunkPlans = NarrationTimeline.rebuild(chunks, cacheManager.recordDuration(cacheKey, index, target)),
            resumeTarget = resumeTarget,
        )
        onChunkReady(session, index == chunks.lastIndex, chunks.size)
        session
    }

    private fun requestSpeechWithRetry(
        apiKey: String,
        model: String,
        input: String,
        voice: String,
        target: File,
        partNumber: Int,
    ) {
        val modelsToTry = listOf(model) + GeminiNarrationConfig.FallbackModels.filterNot { it.equals(model, ignoreCase = true) }
        var lastError: Throwable? = null

        for (candidateModel in modelsToTry) {
            repeat(MaxAttempts) { attempt ->
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.delete()
                runCatching {
                    requestSpeechOnce(apiKey, candidateModel, input, voice, temp)
                    if (temp.length() < MinValidAudioBytes) {
                        error("Gemini returned an empty audio file for part $partNumber.")
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
                    // If error is 404 model not found, try next candidate model immediately
                    if (error.message?.contains("404") == true) {
                        return@repeat
                    }
                    if (attempt == MaxAttempts - 1 && candidateModel == modelsToTry.last()) {
                        throw error
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("Failed to synthesize speech with Gemini.")
    }

    private fun requestSpeechOnce(
        apiKey: String,
        model: String,
        input: String,
        voice: String,
        target: File,
    ) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ConnectTimeoutMs
            readTimeout = ReadTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", input)))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
                    })
                })
            })
        }

        connection.outputStream.use { it.write(requestPayload.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Gemini API error ($responseCode): $errorBody")
        }

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
        var audioBase64: String? = null

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                val inlineData = part?.optJSONObject("inlineData")
                if (inlineData != null) {
                    audioBase64 = inlineData.optString("data")
                    break
                }
            }
        }

        if (audioBase64.isNullOrBlank()) {
            error("Gemini response did not contain audio data.")
        }

        val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
        FileOutputStream(target).use { it.write(audioBytes) }
    }
}
