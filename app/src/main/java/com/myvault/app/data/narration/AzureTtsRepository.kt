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

private data class LanguageSegment(val text: String, val isArabic: Boolean)

@Singleton
class AzureTtsRepository @Inject constructor(
    private val cacheManager: NarrationCacheManager,
    private val textPreparer: NoteNarrationTextPreparer,
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
        onChunkGenerating: (current: Int, total: Int) -> Unit = { _, _ -> },
        onChunkReady: (session: NarrationSession, isComplete: Boolean, totalChunks: Int) -> Unit,
    ): NarrationSession = withContext(Dispatchers.IO) {
        val originalText = narrationText.trim()
        val cleanText = textPreparer.prepareAzureNarration(originalText)
        if (cleanText.isBlank()) error("This note is empty.")
        if (apiKey.isBlank()) error("Azure Speech API key is missing. Add it in Settings.")
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
        cacheManager.cachedSessionOrNull(cacheKey, noteId, noteTitle, model, normalizedVoice, clampedSpeed, contentHash)?.let {
            if (it.cues.isNotEmpty()) {
                onChunkReady(it, true, it.files.size)
                return@withContext it
            }
            cacheManager.clearSession(cacheKey)
        }

        val chunks = textPreparer.splitIntoChunks(cleanText)
        if (chunks.isEmpty()) error("This note is empty.")
        val generatedFiles = mutableListOf<File>()
        val generatedCues = mutableListOf<NarrationCue>()
        var searchFrom = 0
        chunks.forEachIndexed { index, chunk ->
            coroutineContext.ensureActive()
            val chunkTextStart = cleanText.indexOf(chunk, searchFrom).takeIf { it >= 0 } ?: searchFrom
            searchFrom = (chunkTextStart + chunk.length).coerceAtMost(cleanText.length)
            val target = cacheManager.chunkFile(cacheKey, index)
            val chunkCueFile = File(cacheManager.sessionDir(cacheKey), "chunk_${index.toString().padStart(3, '0')}_cues.json")
            if (!target.exists() || target.length() < MinValidMp3Bytes || !chunkCueFile.exists()) {
                onChunkGenerating(index + 1, chunks.size)
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
            }
            val chunkCues = chunkCueFile.readCuesOrEmpty()
            generatedCues += chunkCues
            generatedFiles += target
            val session = NarrationSession(
                cacheKey = cacheKey,
                noteId = noteId,
                noteTitle = noteTitle,
                model = model,
                voice = normalizedVoice,
                speed = clampedSpeed,
                contentHash = contentHash,
                files = generatedFiles.toList(),
                cues = generatedCues.toList(),
            )
            cacheManager.writeManifest(session, isComplete = index == chunks.lastIndex, totalChunks = chunks.size)
            onChunkReady(session, index == chunks.lastIndex, chunks.size)
        }
        NarrationSession(cacheKey, noteId, noteTitle, model, normalizedVoice, clampedSpeed, contentHash, generatedFiles, generatedCues)
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
            val result = synthesizer.SpeakSsmlAsync(buildMixedLanguageSsml(text, voice, arabicVoice)).get()
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

private fun buildMixedLanguageSsml(text: String, englishVoice: String, arabicVoice: String): String {
    val segments = splitLanguageSegments(text)
    return buildString {
        append("""<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-AU">""")
        segments.forEach { segment ->
            val voice = if (segment.isArabic) arabicVoice else englishVoice
            append("""<voice name="${voice.xmlEscape()}">${segment.text.xmlEscape()}</voice>""")
        }
        append("</speak>")
    }
}

private fun splitLanguageSegments(text: String): List<LanguageSegment> {
    val raw = Regex("[^.!?؟\\n]+[.!?؟]?|\\n+").findAll(text)
        .map { it.value }
        .filter { it.isNotEmpty() }
        .toList()
    val result = mutableListOf<LanguageSegment>()
    raw.forEach { value ->
        val arabic = value.count { it.isArabicLetter() }
        val latin = value.count { it.isLatinLetter() }
        val isArabic = when {
            arabic > latin -> true
            latin > arabic -> false
            else -> result.lastOrNull()?.isArabic ?: false
        }
        val previous = result.lastOrNull()
        if (previous?.isArabic == isArabic) {
            result[result.lastIndex] = previous.copy(text = previous.text + value)
        } else {
            result += LanguageSegment(value, isArabic)
        }
    }
    return result.ifEmpty { listOf(LanguageSegment(text, false)) }
}

private fun Char.isArabicLetter(): Boolean =
    this in '\u0600'..'\u06FF' ||
        this in '\u0750'..'\u077F' ||
        this in '\u08A0'..'\u08FF' ||
        this in '\uFB50'..'\uFDFF' ||
        this in '\uFE70'..'\uFEFF'

private fun Char.isLatinLetter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z'

private fun String.xmlEscape(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

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
