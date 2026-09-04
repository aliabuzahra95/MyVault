package com.myvault.app.data.narration

import android.text.Html
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteNarrationTextPreparer @Inject constructor() {
    fun prepareAzureNarration(text: String): String =
        normalizePlainText(stripHtmlPreservingBreaks(text))
            .lines()
            .map(::cleanAzureNarrationLine)
            .filterNot { it.isBlank() }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    fun prepare(title: String, body: String): String {
        val cleanTitle = normalizePlainText(title)
        val cleanBody = normalizePlainText(stripHtmlPreservingBreaks(body))
        return listOf(cleanTitle, cleanBody)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    fun splitIntoChunks(text: String, maxChars: Int = NarrationConfig.MAX_CHARS_PER_CHUNK): List<String> {
        val normalized = normalizePlainText(text)
        if (normalized.length <= maxChars) return listOf(normalized).filter { it.isNotBlank() }

        val chunks = mutableListOf<String>()
        val paragraphs = normalized.split(Regex("\\n{2,}"))
        val current = StringBuilder()

        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) chunks += value
            current.clear()
        }

        paragraphs.forEach { paragraph ->
            val cleanParagraph = paragraph.trim()
            if (cleanParagraph.isBlank()) return@forEach
            if (cleanParagraph.length > maxChars) {
                flush()
                chunks += splitLongParagraph(cleanParagraph, maxChars)
                return@forEach
            }
            val candidateLength = current.length + if (current.isEmpty()) 0 else 2 + cleanParagraph.length
            if (candidateLength > maxChars) flush()
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(cleanParagraph)
        }
        flush()
        return chunks
    }

    private fun splitLongParagraph(paragraph: String, maxChars: Int): List<String> {
        val sentences = paragraph.split(Regex("(?<=[.!؟?])\\s+"))
        val result = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) result += value
            current.clear()
        }
        sentences.forEach { sentence ->
            if (sentence.length > maxChars) {
                flush()
                result += sentence.chunkedByWordBoundary(maxChars)
                return@forEach
            }
            val candidateLength = current.length + if (current.isEmpty()) 0 else 1 + sentence.length
            if (candidateLength > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()
        return result
    }

    private fun stripHtmlPreservingBreaks(input: String): String {
        if (!input.contains('<') || !input.contains('>')) return input
        val withBreaks = input
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr|blockquote)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun normalizePlainText(input: String): String = input
        .replace('\u00A0', ' ')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .lines()
        .map { it.trim() }
        .joinToString("\n")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun cleanAzureNarrationLine(line: String): String {
        val trimmed = line.trim()
        if (trimmed.matches(Regex("\\d{1,4}"))) return ""
        return trimmed
            .replace(Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE), " link omitted ")
            .replace(Regex("\\[(\\d{1,3})]"), "")
            .replace(Regex("(?<![A-Za-z0-9])\\((\\d{1,3})\\)(?![A-Za-z0-9])"), "")
            .replace(Regex("\\bpp?\\.\\s*(\\d{1,4})\\s*[-–]\\s*(\\d{1,4})\\b", RegexOption.IGNORE_CASE), "pages $1 to $2")
            .replace(Regex("\\bpp?\\.\\s*(\\d{1,4})\\b", RegexOption.IGNORE_CASE), "page $1")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }
}

private fun String.chunkedByWordBoundary(maxChars: Int): List<String> {
    val result = mutableListOf<String>()
    var remaining = trim()
    while (remaining.length > maxChars) {
        val cut = remaining.lastIndexOf(' ', startIndex = maxChars).takeIf { it > maxChars / 2 } ?: maxChars
        result += remaining.substring(0, cut).trim()
        remaining = remaining.substring(cut).trim()
    }
    if (remaining.isNotBlank()) result += remaining
    return result
}
