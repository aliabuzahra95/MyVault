package com.myvault.app.data.narration

import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private data class WordBoundary(
    val startMs: Long,
    val durationMs: Long,
    val textOffset: Int,
    val wordLength: Int,
    val text: String,
)

@Singleton
class AzureTtsRepository @Inject constructor(
    private val cacheManager: NarrationCacheManager,
    private val textPreparer: NoteNarrationTextPreparer,
    private val segmenter: BilingualTextSegmenter,
) {
    suspend fun generateNarrationProgressively(
        noteId: String,
        noteTitle: String,
        narrationText: String,
        apiKey: String,
        region: String,
        voice: String,
        arabicVoice: String,
        speed: Float = 1f,
        requestedChunkIndex: Int = 0,
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val originalText = narrationText.trim()
        val cleanText = textPreparer.prepareAzureNarration(originalText)
        if (cleanText.isBlank()) error("This note is empty.")
        val normalizedRegion = region.trim().lowercase()
        if (!normalizedRegion.matches(RegionPattern)) error("Azure Speech region is invalid.")
        val normalizedVoice = voice.ifBlank { AzureNarrationConfig.DEFAULT_VOICE }
        val normalizedArabicVoice = arabicVoice.ifBlank { AzureNarrationConfig.DEFAULT_ARABIC_VOICE }
        val originalHash = cacheManager.contentHash(originalText)
        val cleanedHash = cacheManager.contentHash(cleanText)
        val contentHash = cacheManager.contentHash("$originalHash:$cleanedHash:$CleanupVersion")
        val model = "azure-speech-$normalizedRegion-mixed-$normalizedArabicVoice-$CleanupVersion"
        val clampedSpeed = speed.coerceIn(0.75f, 1.5f)
        val cacheKey = cacheManager.cacheKey("azure", contentHash, model, normalizedVoice, 1f)
        val chunks = textPreparer.splitIntoChunks(cleanText, CloudNarrationChunkChars)
        if (chunks.isEmpty()) error("This note is empty.")
        var chunkSearchFrom = 0
        val chunkTextStarts = chunks.map { chunk ->
            val start = cleanText.indexOf(chunk, chunkSearchFrom).takeIf { it >= 0 } ?: chunkSearchFrom
            chunkSearchFrom = (start + chunk.length).coerceAtMost(cleanText.length)
            start
        }
        val index = requestedChunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[index]
        val chunkTextStart = chunkTextStarts[index]
        val chunkCacheKey = cacheManager.renditionChunkKey(noteId, chunk, model, normalizedVoice)
        android.util.Log.d(TimingTag, "chunk-requested source=${noteId.take(96)} provider=azure chunk=${index + 1}/${chunks.size}")
        val legacyTarget = cacheManager.cachedChunkOrNull(
            cacheKey, index, noteId, model, normalizedVoice,
            minimumBytes = MinValidMp3Bytes,
            requiredSidecarSuffix = "_cues.json",
        )
        val cachedTarget = legacyTarget ?: cacheManager.cachedChunkOrNull(
            chunkCacheKey, 0, noteId, model, normalizedVoice,
            minimumBytes = MinValidMp3Bytes,
            requiredSidecarSuffix = "_cues.json",
        )
        val cueCacheKey = if (legacyTarget != null) cacheKey else chunkCacheKey
        val cueCacheIndex = if (legacyTarget != null) index else 0
        val chunkCueFile = cacheManager.chunkCueFile(cueCacheKey, cueCacheIndex)
        val target = cachedTarget ?: cacheManager.chunkFile(chunkCacheKey, 0)
        if (cachedTarget == null) {
            if (apiKey.isBlank()) error("Azure Speech API key is missing. Add it in Settings.")
            onChunkGenerating(index + 1, chunks.size)
            android.util.Log.d(TimingTag, "generation-started source=${noteId.take(96)} chunk=${index + 1}")
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val cues = requestSpeechWithRetry(
                apiKey = apiKey.trim(),
                region = normalizedRegion,
                voice = normalizedVoice,
                arabicVoice = normalizedArabicVoice,
                text = chunk,
                target = target,
                partNumber = index + 1,
                chunkIndex = index,
                chunkTextStart = chunkTextStart,
            ).withDisplayText(originalText, textPreparer)
            chunkCueFile.writeText(cues.toCueJson())
            android.util.Log.d(TimingTag, "generation-completed source=${noteId.take(96)} chunk=${index + 1} elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt} bytes=${target.length()}")
        }
        coroutineContext.ensureActive()
        val cues = chunkCueFile.readCuesOrEmpty().map { it.copy(chunkIndex = index) }
        NarrationSession(
            cacheKey = cacheKey,
            noteId = noteId,
            noteTitle = noteTitle,
            model = model,
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

    private fun requestSpeechWithRetry(
        apiKey: String,
        region: String,
        voice: String,
        arabicVoice: String,
        text: String,
        target: File,
        partNumber: Int,
        chunkIndex: Int,
        chunkTextStart: Int,
    ): List<NarrationCue> {
        var lastError: Throwable? = null
        repeat(MaxAttempts) { attempt ->
            val temp = File(target.parentFile, "${target.name}.tmp").apply { delete() }
            runCatching {
                val cues = requestSpeechOnce(apiKey, region, voice, arabicVoice, text, temp, chunkIndex, chunkTextStart)
                if (temp.length() < MinValidMp3Bytes) error("Azure Speech returned empty audio for part $partNumber.")
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return cues
            }.onFailure {
                lastError = it
                temp.delete()
                if (attempt == MaxAttempts - 1) throw it
            }
        }
        throw lastError ?: IllegalStateException("Couldn’t generate Azure narration part $partNumber.")
    }

    private fun requestSpeechOnce(
        apiKey: String,
        region: String,
        voice: String,
        arabicVoice: String,
        text: String,
        target: File,
        chunkIndex: Int,
        chunkTextStart: Int,
    ): List<NarrationCue> {
        val wordBoundaries = mutableListOf<WordBoundary>()
        val config = SpeechConfig.fromSubscription(apiKey, region).apply {
            speechSynthesisVoiceName = voice
            setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Audio24Khz48KBitRateMonoMp3)
        }
        val synthesizer = SpeechSynthesizer(config, null)
        try {
            synthesizer.WordBoundary.addEventListener { _, event ->
                wordBoundaries += WordBoundary(
                    startMs = event.audioOffset / TicksPerMillisecond,
                    durationMs = event.duration / TicksPerMillisecond,
                    textOffset = event.textOffset.toInt(),
                    wordLength = event.wordLength.toInt(),
                    text = event.text.orEmpty(),
                )
            }
            val result = synthesizer.SpeakSsmlAsync(
                segmenter.buildMultilingualSsml(
                    text = text,
                    englishVoice = voice,
                    arabicVoice = arabicVoice,
                ),
            ).get()
            val bytes = result.audioData ?: error("Azure Speech returned no audio.")
            FileOutputStream(target).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            return buildSentenceCues(text, wordBoundaries, chunkIndex, chunkTextStart)
        } finally {
            synthesizer.close()
            config.close()
        }
    }

    private companion object {
        val RegionPattern = Regex("^[a-z0-9-]+$")
        const val MaxAttempts = 2
        const val MinValidMp3Bytes = 512L
        const val TicksPerMillisecond = 10_000L
        const val CleanupVersion = "cleanup-v1"
        const val TimingTag = "MyVaultNarrationTiming"
    }
}

private fun List<NarrationCue>.withDisplayText(
    originalText: String,
    textPreparer: NoteNarrationTextPreparer,
): List<NarrationCue> {
    val originalSentences = Regex("[^.!?؟\\n]+[.!?؟]?").findAll(originalText)
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .toList()
    var originalIndex = 0
    return map { cue ->
        val matchIndex = (originalIndex until originalSentences.size).firstOrNull { index ->
            textPreparer.prepareAzureNarration(originalSentences[index]).trim() == cue.text.trim()
        }
        if (matchIndex == null) {
            cue
        } else {
            originalIndex = matchIndex + 1
            cue.copy(displayText = originalSentences[matchIndex])
        }
    }
}

private fun buildSentenceCues(
    text: String,
    words: List<WordBoundary>,
    chunkIndex: Int,
    chunkTextStart: Int,
): List<NarrationCue> {
    if (words.isEmpty()) return emptyList()
    var wordSearchFrom = 0
    val positionedWords = words.map { word ->
        val found = word.text
            .takeIf { it.isNotBlank() }
            ?.let { text.indexOf(it, wordSearchFrom) }
            ?.takeIf { it >= 0 }
        val offset = found ?: word.textOffset.takeIf { it in text.indices } ?: wordSearchFrom
        wordSearchFrom = (offset + word.wordLength.coerceAtLeast(word.text.length)).coerceAtMost(text.length)
        word.copy(textOffset = offset)
    }
    val sentenceRanges = Regex("[^.!?؟\\n]+[.!?؟]?").findAll(text)
        .map { it.range.first to (it.range.last + 1) }
        .filter { (start, end) -> text.substring(start, end).isNotBlank() }
        .toList()
    val cues = sentenceRanges.mapNotNull { (start, end) ->
        val sentenceWords = positionedWords.filter { it.textOffset < end && it.textOffset + it.wordLength > start }
        val first = sentenceWords.firstOrNull() ?: return@mapNotNull null
        val last = sentenceWords.last()
        NarrationCue(
            chunkIndex = chunkIndex,
            startMs = first.startMs,
            endMs = last.startMs + last.durationMs.coerceAtLeast(800L),
            textStart = chunkTextStart + start,
            textEnd = chunkTextStart + end,
            text = text.substring(start, end).trim(),
        )
    }
    return cues.mapIndexed { index, cue ->
        val nextStart = cues.getOrNull(index + 1)?.startMs
        if (nextStart == null) cue else cue.copy(endMs = nextStart.coerceAtLeast(cue.startMs + 1L))
    }
}

private fun List<NarrationCue>.toCueJson(): String = JSONArray().apply {
    forEach { cue ->
        put(
            JSONObject()
                .put("chunkIndex", cue.chunkIndex)
                .put("startMs", cue.startMs)
                .put("endMs", cue.endMs)
                .put("textStart", cue.textStart)
                .put("textEnd", cue.textEnd)
                .put("text", cue.text)
                .put("displayText", cue.displayText),
        )
    }
}.toString()

private fun File.readCuesOrEmpty(): List<NarrationCue> = runCatching {
    val array = JSONArray(readText())
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                NarrationCue(
                    chunkIndex = item.optInt("chunkIndex"),
                    startMs = item.optLong("startMs"),
                    endMs = item.optLong("endMs"),
                    textStart = item.optInt("textStart"),
                    textEnd = item.optInt("textEnd"),
                    text = item.optString("text"),
                    displayText = item.optString("displayText", item.optString("text")),
                ),
            )
        }
    }
}.getOrDefault(emptyList())
