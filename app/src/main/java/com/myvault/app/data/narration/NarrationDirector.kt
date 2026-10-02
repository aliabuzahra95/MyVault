package com.myvault.app.data.narration

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationDirector @Inject constructor(
    private val bilingualSegmenter: BilingualTextSegmenter,
) {

    /**
     * Parses raw document or note content into a structured, lecture-grade NarrationPlan.
     * Strictly preserves original words verbatim with zero paraphrasing or hallucinations.
     */
    fun createPlan(
        sourceId: String,
        title: String,
        rawContent: String,
    ): NarrationPlan {
        val cleanTitle = title.trim()
        val units = mutableListOf<NarrationUnit>()

        // 1. Initial Title Announcement Unit if present
        if (cleanTitle.isNotBlank()) {
            units += NarrationUnit(
                id = "unit_title_${UUID.randomUUID().toString().take(8)}",
                type = NarrationUnitType.Title,
                rawText = cleanTitle,
                spokenText = cleanTitle,
                language = detectPrimaryLanguage(cleanTitle),
                pauseBeforeMs = 300L,
                pauseAfterMs = 700L,
                emphasisLevel = 1.25f,
            )
        }

        // 2. Extract structural blocks from markdown / HTML / plaintext
        val normalizedBlocks = parseBlocks(rawContent)

        var unitCounter = 0
        normalizedBlocks.forEach { block ->
            val cleanText = block.content.trim()
            if (cleanText.isBlank()) return@forEach

            val segments = bilingualSegmenter.segmentText(cleanText)
            val hasMultipleLanguages = segments.any { it.language == NarrationLanguage.Arabic } &&
                segments.any { it.language == NarrationLanguage.English }

            if (hasMultipleLanguages && (block.type == NarrationUnitType.Paragraph || block.type == NarrationUnitType.BlockQuote)) {
                // Decompose mixed block into distinct language units with respectful pauses
                segments.forEach { segment ->
                    val segText = segment.text.trim()
                    if (segText.isBlank()) return@forEach
                    val isAr = segment.language == NarrationLanguage.Arabic
                    val segType = if (isAr) NarrationUnitType.ArabicQuote else block.type
                    val (pauseBefore, pauseAfter, emphasis) = calculatePacing(segType, units.isEmpty())

                    units += NarrationUnit(
                        id = "unit_${unitCounter++}_${UUID.randomUUID().toString().take(8)}",
                        type = segType,
                        rawText = segText,
                        spokenText = segText,
                        language = if (isAr) "ar" else "en",
                        pauseBeforeMs = pauseBefore,
                        pauseAfterMs = pauseAfter,
                        emphasisLevel = emphasis,
                    )
                }
            } else {
                val isArabic = isPredominantlyArabic(cleanText)
                val effectiveType = if (isArabic && block.type == NarrationUnitType.Paragraph) {
                    NarrationUnitType.ArabicQuote
                } else {
                    block.type
                }

                val (pauseBefore, pauseAfter, emphasis) = calculatePacing(effectiveType, units.isEmpty())

                units += NarrationUnit(
                    id = "unit_${unitCounter++}_${UUID.randomUUID().toString().take(8)}",
                    type = effectiveType,
                    rawText = cleanText,
                    spokenText = cleanText,
                    language = if (isArabic) "ar" else "en",
                    pauseBeforeMs = pauseBefore,
                    pauseAfterMs = pauseAfter,
                    emphasisLevel = emphasis,
                )
            }
        }

        val estimatedDuration = units.sumOf { unit ->
            val words = unit.spokenText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val readingTimeMs = (words * 320L) // ~180 words per minute
            readingTimeMs + unit.pauseBeforeMs + unit.pauseAfterMs
        }

        return NarrationPlan(
            sourceId = sourceId,
            title = cleanTitle,
            units = units,
            totalEstimatedDurationMs = estimatedDuration,
        )
    }

    /**
     * Splits a NarrationPlan into sized chunks aligned strictly at NarrationUnit boundaries.
     * Never splits inside a heading, list item, or Arabic quote unless a single unit exceeds maxChars.
     */
    fun planToChunks(plan: NarrationPlan, maxCharsPerChunk: Int = GeminiNarrationConfig.MAX_CHARS_PER_CHUNK): List<NarrationChunkPlan> {
        if (plan.units.isEmpty()) return emptyList()

        val chunks = mutableListOf<NarrationChunkPlan>()
        val currentChunkText = java.lang.StringBuilder()
        var currentChunkStartMs = 0L
        var accumulatedGlobalMs = 0L

        fun flush() {
            val text = currentChunkText.toString().trim()
            if (text.isNotBlank()) {
                val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val estimatedDur = words * 400L
                chunks += NarrationChunkPlan(
                    index = chunks.size,
                    text = text,
                    estimatedStartMs = currentChunkStartMs,
                    estimatedDurationMs = estimatedDur
                )
                accumulatedGlobalMs += estimatedDur
            }
            currentChunkText.clear()
            currentChunkStartMs = accumulatedGlobalMs
        }

        for (unit in plan.units) {
            val unitText = unit.spokenText.trim()
            if (unitText.isBlank()) continue

            if (unitText.length > maxCharsPerChunk) {
                flush()
                val subParts = splitOversizedText(unitText, maxCharsPerChunk)
                subParts.forEach { sub ->
                    val w = sub.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    val d = w * 400L
                    chunks += NarrationChunkPlan(
                        index = chunks.size,
                        text = sub,
                        estimatedStartMs = currentChunkStartMs,
                        estimatedDurationMs = d
                    )
                    accumulatedGlobalMs += d
                    currentChunkStartMs = accumulatedGlobalMs
                }
                continue
            }

            val candidateLen = currentChunkText.length + (if (currentChunkText.isEmpty()) 0 else 2) + unitText.length
            if (candidateLen > maxCharsPerChunk) {
                flush()
            }

            if (currentChunkText.isNotEmpty()) {
                currentChunkText.append("\n\n")
            }
            currentChunkText.append(unitText)
        }
        flush()

        return chunks
    }

    /**
     * Builds lecture-oriented prompt instructions for Gemini or OpenAI, emphasizing
     * pacing, respectful natural pauses, and faithful narration without deviations.
     */
    fun lectureNarratorSystemInstruction(): String =
        "You are an articulate, engaging, lecture-grade audiobook narrator reading structured study notes. " +
            "Deliver the narration clearly and calmly at a comfortable lecture pace. " +
            "Observe natural audible pauses at headings, subheadings, bullet items, and quotations. " +
            "Crucial: Faithfully read every word of the provided text verbatim without summarizing, changing, paraphrasing, or adding commentary."

    private data class ParsedBlock(val type: NarrationUnitType, val content: String)

    private fun parseBlocks(content: String): List<ParsedBlock> {
        val preprocessed = stripHtmlPreservingStructure(content)
        val lines = preprocessed.lines()
        val blocks = mutableListOf<ParsedBlock>()
        val currentParagraph = StringBuilder()

        fun flushParagraph() {
            val text = currentParagraph.toString().trim()
            if (text.isNotBlank()) {
                blocks += ParsedBlock(NarrationUnitType.Paragraph, text)
            }
            currentParagraph.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                flushParagraph()
                continue
            }

            when {
                // Markdown H1 or H2
                trimmed.startsWith("# ") || trimmed.startsWith("## ") -> {
                    flushParagraph()
                    val headingText = trimmed.replace(Regex("^#{1,2}\\s+"), "").trim()
                    if (headingText.isNotBlank()) {
                        blocks += ParsedBlock(NarrationUnitType.Heading, headingText)
                    }
                }
                // Markdown H3 or H4
                trimmed.startsWith("### ") || trimmed.startsWith("#### ") -> {
                    flushParagraph()
                    val subheadingText = trimmed.replace(Regex("^#{3,4}\\s+"), "").trim()
                    if (subheadingText.isNotBlank()) {
                        blocks += ParsedBlock(NarrationUnitType.Subheading, subheadingText)
                    }
                }
                // Blockquote
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quoteText = trimmed.replace(Regex("^>+\\s*"), "").trim()
                    if (quoteText.isNotBlank()) {
                        blocks += ParsedBlock(NarrationUnitType.BlockQuote, quoteText)
                    }
                }
                // Bulleted list item
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    flushParagraph()
                    val itemText = trimmed.replace(Regex("^[-*•]\\s+"), "").trim()
                    if (itemText.isNotBlank()) {
                        blocks += ParsedBlock(NarrationUnitType.ListItem, itemText)
                    }
                }
                // Numbered list item: e.g. "1. " or "1) "
                trimmed.matches(Regex("^\\d{1,3}[.)]\\s+.*")) -> {
                    flushParagraph()
                    blocks += ParsedBlock(NarrationUnitType.ListItem, trimmed)
                }
                else -> {
                    if (currentParagraph.isNotEmpty()) {
                        currentParagraph.append(' ')
                    }
                    currentParagraph.append(trimmed)
                }
            }
        }
        flushParagraph()

        return blocks
    }

    private fun stripHtmlPreservingStructure(input: String): String {
        if (!input.contains('<') || !input.contains('>')) return input
        return input
            .replace(Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE), "\n\n# $1\n\n")
            .replace(Regex("<h2[^>]*>(.*?)</h2>", RegexOption.IGNORE_CASE), "\n\n## $1\n\n")
            .replace(Regex("<h3[^>]*>(.*?)</h3>", RegexOption.IGNORE_CASE), "\n\n### $1\n\n")
            .replace(Regex("<h[4-6][^>]*>(.*?)</h[4-6]>", RegexOption.IGNORE_CASE), "\n\n### $1\n\n")
            .replace(Regex("<blockquote[^>]*>(.*?)</blockquote>", RegexOption.IGNORE_CASE), "\n\n> $1\n\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|tr)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun calculatePacing(type: NarrationUnitType, isVeryFirst: Boolean): Triple<Long, Long, Float> = when (type) {
        NarrationUnitType.Title -> Triple(if (isVeryFirst) 200L else 500L, 700L, 1.25f)
        NarrationUnitType.Heading -> Triple(if (isVeryFirst) 100L else 600L, 700L, 1.2f)
        NarrationUnitType.Subheading -> Triple(if (isVeryFirst) 100L else 400L, 500L, 1.1f)
        NarrationUnitType.Paragraph -> Triple(200L, 400L, 1.0f)
        NarrationUnitType.ListItem -> Triple(250L, 350L, 1.05f)
        NarrationUnitType.BlockQuote -> Triple(400L, 400L, 0.95f)
        NarrationUnitType.ArabicQuote -> Triple(500L, 500L, 1.0f)
    }

    private fun isPredominantlyArabic(text: String): Boolean {
        var arabicCount = 0
        var latinCount = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (BilingualTextSegmenter.isArabicCodePoint(cp)) arabicCount++
            if (BilingualTextSegmenter.isLatinCodePoint(cp)) latinCount++
            i += Character.charCount(cp)
        }
        return arabicCount > 0 && arabicCount >= latinCount
    }

    private fun detectPrimaryLanguage(text: String): String =
        if (isPredominantlyArabic(text)) "ar" else "en"

    private fun splitOversizedText(text: String, maxChars: Int): List<String> {
        val sentences = text.split(Regex("(?<=[.!؟?])\\s+"))
        val result = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val s = current.toString().trim()
            if (s.isNotBlank()) result += s
            current.clear()
        }

        sentences.forEach { s ->
            if (s.length > maxChars) {
                flush()
                result += s.chunked(maxChars)
                return@forEach
            }
            if (current.length + s.length + 1 > maxChars) {
                flush()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(s)
        }
        flush()
        return result
    }
}
