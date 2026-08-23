package com.myvault.app.data.formatting

import android.util.Log
import com.myvault.app.BuildConfig

/**
 * Formatting-output sanitation and lossless protection for both structural workflows.
 * This package deliberately has no chat, tutoring, history, or conversation dependency.
 */
internal object NoteFormattingOutputEngine {
    val retainedActions: Set<NoteFormattingAction> = NoteFormattingAction.entries.toSet()

    fun prepareOutput(
        action: NoteFormattingAction,
        generated: String,
        originalBody: String,
    ): String {
        val cleaned = generated.cleanForAction(action)
        return if (action.isLosslessStructureAction()) {
            cleaned.ensureLosslessStructurePreservesContent(
                originalBody = originalBody,
                action = action,
            )
        } else {
            cleaned
        }
    }

    fun chunkSource(body: String): List<String> = body.chunkForAi()
}

// Kept deliberately below the smallest provider output budget so a lossless formatted chunk can
// be returned in full instead of being cut off and forced into the local safety fallback.
private const val IntelligentStructureChunkSize = 12_000
private fun String.chunkForAi(): List<String> {
    if (length <= IntelligentStructureChunkSize) return listOf(this)
    val chunks = mutableListOf<String>()
    val paragraphs = splitIntoAiBlocks()
    val current = StringBuilder()
    paragraphs.forEach { paragraph ->
        val candidateLength = current.length + paragraph.length + 2
        if (current.isNotEmpty() && candidateLength > IntelligentStructureChunkSize) {
            chunks += current.toString().trim()
            current.clear()
        }
        if (paragraph.length > IntelligentStructureChunkSize) {
            paragraph.splitOversizedBlockSafely(IntelligentStructureChunkSize).forEach { chunks += it.trim() }
        } else {
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
    }
    if (current.isNotEmpty()) chunks += current.toString().trim()
    return chunks.filter { it.isNotBlank() }
}

private fun String.splitIntoAiBlocks(): List<String> {
    val blocks = mutableListOf<String>()
    val current = StringBuilder()
    lineSequence().forEach { line ->
        val trimmed = line.trim()
        val startsNewSection = current.isNotBlank() && trimmed.looksLikeSectionHeading()
        val blankBreak = trimmed.isBlank() && current.isNotBlank()
        if (startsNewSection || blankBreak) {
            blocks += current.toString().trim()
            current.clear()
        }
        if (trimmed.isNotBlank()) {
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
    }
    if (current.isNotBlank()) blocks += current.toString().trim()
    return blocks.ifEmpty { listOf(this) }
}

private fun String.looksLikeSectionHeading(): Boolean =
    length in 3..120 &&
        !endsWith(".") &&
        !endsWith(",") &&
        !endsWith("،") &&
        !endsWith(";") &&
        (startsWith("#") || all { it.isLetterOrDigit() || it.isWhitespace() || it in ":-'’()/،" })

private fun String.splitOversizedBlockSafely(maxSize: Int): List<String> {
    val parts = mutableListOf<String>()
    var remaining = trim()
    while (remaining.length > maxSize) {
        val boundary = remaining.safeSplitBoundary(maxSize)
        parts += remaining.substring(0, boundary).trim()
        remaining = remaining.substring(boundary).trimStart()
    }
    if (remaining.isNotBlank()) parts += remaining
    return parts
}

private fun String.safeSplitBoundary(maxSize: Int): Int {
    val search = substring(0, maxSize.coerceAtMost(length))
    val sentenceBoundary = listOf("\n", ". ", "? ", "! ", "؟ ", "۔ ", "؛ ", "; ", "، ")
        .map { search.lastIndexOf(it) }
        .filter { it >= maxSize / 2 }
        .maxOrNull()
    if (sentenceBoundary != null) return sentenceBoundary + 1
    val spaceBoundary = search.lastIndexOf(' ').takeIf { it >= maxSize / 2 }
    return spaceBoundary ?: maxSize.coerceAtMost(length)
}

private fun String.stripHtmlTags(): String =
    replace(Regex("<[^>]+>"), "")

private fun String.decodeCommonHtmlEntities(): String =
    replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

private fun String.escapeEditorHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun String.cleanForAction(action: NoteFormattingAction): String =
    when (action) {
        NoteFormattingAction.StructureOnly -> cleanEditorHtmlOutput().trim()
        NoteFormattingAction.IntelligentStructure -> normalizeIntelligentStructureColors().cleanEditorHtmlOutput().trim()
        NoteFormattingAction.CleanFormat,
        NoteFormattingAction.FormatNote,
        -> trim()
    }

private fun NoteFormattingAction.isLosslessStructureAction(): Boolean =
    this == NoteFormattingAction.StructureOnly || this == NoteFormattingAction.IntelligentStructure

private fun String.ensureLosslessStructurePreservesContent(
    originalBody: String,
    action: NoteFormattingAction,
): String {
    val originalPlain = originalBody.toPlainComparableText()
    val outputPlain = toPlainComparableText()

    if (originalPlain.isBlank()) return this
    if (outputPlain.isBlank()) return originalBody.toStructureOnlyHtml()

    val originalWords = originalPlain.wordCountForPreservation()
    val outputWords = outputPlain.wordCountForPreservation()
    val originalChars = originalPlain.length
    val outputChars = outputPlain.length
    val missingArabicSegments = originalPlain.significantArabicSegments()
        .filterNot { segment -> outputPlain.contains(segment) }
    val missingOriginalSegments = originalBody.structureOnlyPreservationSegments()
        .filterNot { segment -> outputPlain.contains(segment) }
    val missingOriginalWordOccurrences = originalPlain.missingWordOccurrencesFrom(outputPlain)

    val wordRatio = if (originalWords == 0) 1.0 else outputWords.toDouble() / originalWords.toDouble()
    val charRatio = if (originalChars == 0) 1.0 else outputChars.toDouble() / originalChars.toDouble()
    val unsafeExpansion = (originalWords >= 80 && wordRatio > 1.35) ||
        (originalChars >= 500 && charRatio > 1.45)

    // Structural formatting is presentation, not rewriting. Any missing source word, even a
    // short one that would not be caught by paragraph-ratio checks, makes the result unsafe.
    // Fall back to a conservative local HTML wrapper so the editor never loses source content.
    if (
        wordRatio < 0.96 ||
        charRatio < 0.92 ||
        unsafeExpansion ||
        missingArabicSegments.isNotEmpty() ||
        missingOriginalSegments.isNotEmpty() ||
        missingOriginalWordOccurrences.isNotEmpty()
    ) {
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.w(
                    "MyVaultLosslessStructure",
                    "Rejected unsafe ${action.displayName} output originalWords=$originalWords outputWords=$outputWords originalChars=$originalChars outputChars=$outputChars wordRatio=$wordRatio charRatio=$charRatio unsafeExpansion=$unsafeExpansion missingArabic=${missingArabicSegments.size} missingSegments=${missingOriginalSegments.size} missingWordOccurrences=${missingOriginalWordOccurrences.size}",
                )
            }
        }
        return originalBody.toStructureOnlyHtml()
            .normalizeEditorHtmlSafety()
            .normalizeListHtmlSafety()
            .trim()
    }

    return this
}

private fun String.missingWordOccurrencesFrom(output: String): List<String> {
    val sourceCounts = preservationWordTokens().groupingBy { it }.eachCount()
    if (sourceCounts.isEmpty()) return emptyList()
    val outputCounts = output.preservationWordTokens().groupingBy { it }.eachCount()
    return sourceCounts.entries
        .asSequence()
        .filter { (token, requiredCount) -> outputCounts.getOrDefault(token, 0) < requiredCount }
        .map { (token, requiredCount) ->
            val missingCount = requiredCount - outputCounts.getOrDefault(token, 0)
            if (missingCount == 1) token else "$token x$missingCount"
        }
        .take(100)
        .toList()
}

private fun String.preservationWordTokens(): List<String> =
    Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’\\-][\\p{L}\\p{M}\\p{N}]+)*")
        .findAll(this)
        .map { it.value }
        .toList()

private fun String.toPlainComparableText(): String =
    replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|li|h[1-3]|blockquote)>"), "\n")
        .stripHtmlTags()
        .decodeCommonHtmlEntities()
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.wordCountForPreservation(): Int =
    split(Regex("\\s+")).count { it.isNotBlank() }

private fun String.significantArabicSegments(): List<String> =
    Regex("[\\u0600-\\u06FF][\\u0600-\\u06FF\\s\\u064B-\\u065F\\u0670\\u06D6-\\u06ED،؛؟ـ-]{2,}")
        .findAll(this)
        .map { match -> match.value.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length >= 3 }
        .distinct()
        .take(60)
        .toList()

private fun String.structureOnlyPreservationSegments(): List<String> {
    val source = replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|li|h[1-3]|blockquote)>"), "\n")
        .stripHtmlTags()
        .decodeCommonHtmlEntities()
        .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    return source.lines()
        .flatMap { line ->
            val segment = line.normalizeStructureOnlySourceSegment()
            when {
                segment.isBlank() -> emptyList()
                segment.length <= 280 -> listOf(segment)
                else -> segment.splitIntoStructureOnlySentenceSegments()
            }
        }
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { segment ->
            segment.length >= 12 &&
                (segment.wordCountForPreservation() >= 3 || segment.contains(Regex("[\\u0600-\\u06FF]")))
        }
        .distinct()
        .take(500)
}

private fun String.normalizeStructureOnlySourceSegment(): String =
    trim()
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^[-•*+]\\s+"), "")
        .replace(Regex("^\\d+[.)]\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.splitIntoStructureOnlySentenceSegments(): List<String> {
    val sentences = Regex("[^.!?؟。]+(?:[.!?؟。]+|$)")
        .findAll(this)
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .flatMap { sentence ->
            if (sentence.length <= 280) listOf(sentence) else sentence.splitIntoStructureOnlyWordWindows()
        }
        .toList()

    return sentences.takeIf { it.isNotEmpty() } ?: splitIntoStructureOnlyWordWindows()
}

private fun String.splitIntoStructureOnlyWordWindows(): List<String> {
    val words = split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    words.forEach { word ->
        if (current.isNotEmpty() && current.length + word.length + 1 > 260) {
            segments += current.toString().trim()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotBlank()) segments += current.toString().trim()
    return segments
}

private fun String.cleanEditorHtmlOutput(): String {
    val cleaned = trim()
        .replace(Regex("(?i)^```html\\s*"), "")
        .replace(Regex("(?i)^```\\s*"), "")
        .replace(Regex("```$"), "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s+(.+)$"), "<h2>$1</h2>")
        .replace(Regex("\\*\\*([^\\n*]+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("__([^\\n_]+?)__"), "<strong>$1</strong>")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .normalizeEditorHtmlSafety()
        .normalizeListHtmlSafety()

    val hasEditorHtml = cleaned.contains(
        Regex("<(h1|h2|h3|p|ul|ol|li|blockquote|span|strong|em)\\b", RegexOption.IGNORE_CASE),
    )

    // Important: if the model already returned HTML, do not run semantic paragraph-to-list
    // reconstruction here. That older post-processing could accidentally drop text outside
    // recognised block tags and could reinterpret model semantics incorrectly. StructureOnly
    // must preserve content; the model performs semantic structure, this layer only cleans.
    return if (hasEditorHtml) {
        cleaned
    } else {
        cleaned.toStructureOnlyHtml()
            .normalizeEditorHtmlSafety()
            .normalizeListHtmlSafety()
            .trim()
    }
}

private fun String.normalizeEditorHtmlSafety(): String {
    var output = this
        .replace(Regex("(?i)</h([1-3])\\s*>"), "</h$1>")
        .replace(Regex("(?i)</(p|li|ul|ol|blockquote|strong|em|span)\\s*>"), "</$1>")

    // Repair a common malformed model output where the last heading character lands just
    // outside the closing heading tag, e.g. <h2>Exampl</h2>e<p>...
    output = Regex(
        "<h([1-3])([^>]*)>(.*?)</h\\1>\\s*([\\p{L}\\p{M}\\p{N}])(?=\\s*</?(?:p|h[1-3]|ul|ol|blockquote|br)\\b)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        "<h${match.groupValues[1]}${match.groupValues[2]}>${match.groupValues[3]}${match.groupValues[4]}</h${match.groupValues[1]}>"
    }

    // Headings are structural markers. Flatten their internals so broken nested spans/strong/em
    // cannot produce partial heading ranges in the rich-text importer.
    output = Regex(
        "<h([1-3])[^>]*>(.*?)</h\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        val level = match.groupValues[1]
        val heading = match.groupValues[2].stripHtmlTags().decodeCommonHtmlEntities().escapeEditorHtml().trim()
        if (heading.isBlank()) "" else "<h$level>$heading</h$level>"
    }

    output = output
        .replace(Regex("(?i)<span[^>]*data-color\\s*=\\s*['\"]?(red|blue)['\"]?[^>]*>")) { match ->
            """<span data-color="${match.groupValues[1].lowercase()}">"""
        }
        .replace(Regex("(?i)<span[^>]*dir\\s*=\\s*['\"]?(rtl|ltr)['\"]?[^>]*>")) { match ->
            """<span dir="${match.groupValues[1].lowercase()}">"""
        }
        .replace(Regex("(?i)<span(?!\\s+(?:data-color|dir)=)[^>]*>"), "")
        .replace(Regex("(?i)</span>"), "</span>")
        .replace(Regex("(?i)<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("(?i)</?(div|section|article|font|body|html)[^>]*>"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    return output
}


private fun String.normalizeListHtmlSafety(): String {
    var output = this

    // Keep list items compact and prevent stray line-breaks/paragraphs inside lists from
    // turning into visual gaps or bullet leakage in the editor.
    output = output
        .replace(Regex("(?i)</li>\\s*<br\\s*/?>\\s*<li>"), "</li>\n<li>")
        .replace(Regex("(?i)</li>\\s*<p>\\s*</p>\\s*<li>"), "</li>\n<li>")
        .replace(Regex("(?i)<li>\\s*<p>(.*?)</p>\\s*</li>", setOf(RegexOption.DOT_MATCHES_ALL)), "<li>$1</li>")
        .replace(Regex("(?i)<p>\\s*</p>"), "")
        .replace(Regex("(?i)<br\\s*/?>\\s*(?=</?(ul|ol|li)\\b)"), "")
        .replace(Regex("(?i)(</ul>|</ol>)\\s*(<ul>|<ol>)"), "$1\n$2")

    // Avoid ordered-list misuse leaking from either model output or markdown conversion.
    // StructureOnly's study-note style should default to bullets unless the order is explicit.
    output = Regex(
        "<ol\\b[^>]*>(.*?)</ol>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).replace(output) { match ->
        val inner = match.groupValues[1]
        val plainItems = Regex("<li\\b[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(inner)
            .map { it.groupValues[1].stripHtmlTags().decodeCommonHtmlEntities().trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (plainItems.isExplicitOrderedTextList()) {
            match.value
        } else {
            "<ul>$inner</ul>"
        }
    }

    return output
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun List<String>.isExplicitOrderedTextList(): Boolean {
    if (isEmpty()) return false
    val labels = map { it.orderedSequenceLabel() }
    if (labels.any { it == null }) return false
    val cleanLabels = labels.filterNotNull().toSet()
    return cleanLabels.any { it in ExplicitOrdinalLabels } ||
        cleanLabels.all { it in SyllogismLabels } ||
        cleanLabels.all { it in PremiseConclusionLabels }
}

private fun String.orderedSequenceLabel(): String? =
    Regex(
        "^(universal|particular|conclusion|premise|major premise|minor premise|step|stage|first|second|third|fourth|fifth|firstly|secondly|thirdly|finally)\\s*[:：-]",
        RegexOption.IGNORE_CASE,
    ).find(this)?.groupValues?.getOrNull(1)?.lowercase()

private val ExplicitOrdinalLabels = setOf(
    "step",
    "stage",
    "first",
    "second",
    "third",
    "fourth",
    "fifth",
    "firstly",
    "secondly",
    "thirdly",
    "finally",
)

private val SyllogismLabels = setOf("universal", "particular", "conclusion")

private val PremiseConclusionLabels = setOf("premise", "major premise", "minor premise", "conclusion")

private fun String.toStructureOnlyHtml(): String {
    val clean = trim()
    if (clean.isBlank()) return ""

    if (clean.contains(Regex("<(h1|h2|h3|p|ul|ol|li|blockquote|span|strong|em)\\b", RegexOption.IGNORE_CASE))) {
        return clean
    }

    val output = StringBuilder()
    var listType: String? = null
    var paragraph = StringBuilder()

    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun closeParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) {
            output.append("<p>").append(escapeHtml(text)).append("</p>\n")
        }
        paragraph = StringBuilder()
    }

    fun closeList() {
        listType?.let { output.append("</").append(it).append(">\n") }
        listType = null
    }

    fun ensureList(type: String) {
        closeParagraph()
        if (listType != type) {
            closeList()
            output.append("<").append(type).append(">\n")
            listType = type
        }
    }

    fun looksLikeHeading(line: String): Boolean {
        val value = line.trim()
        if (value.length !in 3..90) return false
        if (value.endsWith(".") || value.endsWith(",") || value.endsWith("،") || value.endsWith(":") || value.endsWith("؛")) return false
        if (value.split(Regex("\\s+")).size > 9) return false
        return value.any { it.isLetter() }
    }

    clean.lines().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) {
            closeParagraph()
            closeList()
            return@forEach
        }

        val explicitHeading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
        val bullet = Regex("^[-•*]\\s+(.+)$").matchEntire(line)
        val numbered = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)

        when {
            explicitHeading != null -> {
                closeParagraph()
                closeList()
                val level = explicitHeading.groupValues[1].length.coerceIn(1, 3)
                output.append("<h").append(level).append(">").append(escapeHtml(explicitHeading.groupValues[2].trim())).append("</h").append(level).append(">\n")
            }
            bullet != null -> {
                ensureList("ul")
                output.append("<li>").append(escapeHtml(bullet.groupValues[1].trim())).append("</li>\n")
            }
            numbered != null -> {
                ensureList("ol")
                output.append("<li>").append(escapeHtml(numbered.groupValues[1].trim())).append("</li>\n")
            }
            looksLikeHeading(line) -> {
                closeParagraph()
                closeList()
                output.append("<h2>").append(escapeHtml(line)).append("</h2>\n")
            }
            else -> {
                closeList()
                if (paragraph.isNotBlank()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }

    closeParagraph()
    closeList()

    return output.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.normalizeIntelligentStructureColors(): String {
    var output = this
    val colorSpanRegex = Regex(
        "<span[^>]*data-color\\s*=\\s*['\"]?(green|purple|orange|slate|pink)['\"]?[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    output = colorSpanRegex.replace(output) { match ->
        val inner = match.groupValues[2]
        val plainInner = inner.replace(Regex("<[^>]+>"), "")
        val correctedColor = when {
            plainInner.looksLikeQuranVerse() -> "red"
            plainInner.looksLikeScholarQuote() -> "blue"
            else -> null
        }
        if (correctedColor == null) inner else """<span data-color="$correctedColor">$inner</span>"""
    }
    return output
}

private fun String.looksLikeQuranVerse(): Boolean {
    val value = trim()
    return value.contains(Regex("[\\u0600-\\u06FF]{8,}")) &&
        (value.contains("قال الله") ||
            value.contains("الله تعالى") ||
            value.contains("القرآن") ||
            value.contains("سورة") ||
            value.contains(Regex("\\(\\d{1,3}:\\d{1,3}\\)|\\[\\d{1,3}:\\d{1,3}]")))
}

private fun String.looksLikeScholarQuote(): Boolean {
    val value = lowercase()
    return listOf(
        "ibn taymiyyah",
        "ibn al-qayyim",
        "imam ahmad",
        "ahmad ibn hanbal",
        "al-dhahabi",
        "ibn kathir",
        "al-ashari",
        "al-baqillani",
        "al-juwayni",
        "al-ghazali",
        "al-razi",
        "قال ابن",
        "قال الإمام",
        "ذكر ابن",
    ).any { value.contains(it) }
}
