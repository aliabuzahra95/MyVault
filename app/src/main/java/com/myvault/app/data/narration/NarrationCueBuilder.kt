package com.myvault.app.data.narration

import android.media.MediaMetadataRetriever
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class NarrationCueBuilder @Inject constructor(
    private val bilingualSegmenter: BilingualTextSegmenter,
) {
    fun buildEstimatedCues(chunkIndex: Int, text: String, audioFile: File): List<NarrationCue> {
        val durationMs = audioDurationMs(audioFile)
        if (durationMs <= 0L || text.isBlank()) return emptyList()

        val segments = sentenceRanges(text).flatMap { sentence ->
            val sentenceText = text.substring(sentence.first, sentence.last + 1)
            val bilingual = bilingualSegmenter.segmentText(sentenceText)
            if (bilingual.size <= 1) {
                listOf(sentence)
            } else {
                var searchFrom = sentence.first
                bilingual.mapNotNull { segment ->
                    val clean = segment.text.trim()
                    if (clean.isBlank()) return@mapNotNull null
                    val start = text.indexOf(clean, searchFrom).takeIf { it >= 0 } ?: searchFrom
                    searchFrom = (start + clean.length).coerceAtMost(text.length)
                    start until searchFrom
                }
            }
        }.ifEmpty { listOf(0 until text.length) }

        val weights = segments.map { range ->
            text.substring(range).count { !it.isWhitespace() }.coerceAtLeast(1)
        }
        val totalWeight = weights.sum().coerceAtLeast(1)
        var elapsed = 0L
        return segments.mapIndexed { index, range ->
            val end = if (index == segments.lastIndex) {
                durationMs
            } else {
                elapsed + (durationMs * (weights[index].toDouble() / totalWeight)).roundToLong()
            }
            val display = text.substring(range).trim()
            NarrationCue(
                chunkIndex = chunkIndex,
                startMs = elapsed,
                endMs = end.coerceAtLeast(elapsed + 1L),
                textStart = range.first,
                textEnd = range.last + 1,
                text = display,
                displayText = display,
            ).also { elapsed = it.endMs }
        }
    }

    private fun sentenceRanges(text: String): List<IntRange> =
        Regex("[^.!?؟\\n]+[.!?؟]?").findAll(text)
            .map { match ->
                val leading = match.value.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trailing = match.value.indexOfLast { !it.isWhitespace() }.coerceAtLeast(leading)
                (match.range.first + leading)..(match.range.first + trailing)
            }
            .filter { !it.isEmpty() }
            .toList()

    private fun audioDurationMs(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)
}
