package com.myvault.app.data.narration

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class NarrationLanguage {
    English,
    Arabic,
}

data class NarrationTextSegment(
    val text: String,
    val language: NarrationLanguage,
    val locale: String,
    val voice: String,
)

@Singleton
class BilingualTextSegmenter @Inject constructor() {

    fun segmentText(
        text: String,
        englishVoice: String = AzureNarrationConfig.DEFAULT_VOICE,
        englishLocale: String = DEFAULT_ENGLISH_LOCALE,
        arabicVoice: String = AzureNarrationConfig.DEFAULT_ARABIC_VOICE,
        arabicLocale: String = DEFAULT_ARABIC_LOCALE,
    ): List<NarrationTextSegment> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawSpans = splitIntoRawSpans(trimmed)
        if (rawSpans.isEmpty()) return emptyList()

        // Smooth out punctuation, whitespace, and micro-fragments
        val smoothedSpans = smoothSpans(rawSpans)

        // Map to typed segments with explicit voice and locale on every segment
        return smoothedSpans.map { span ->
            val isArabic = span.language == NarrationLanguage.Arabic
            NarrationTextSegment(
                text = span.text,
                language = span.language,
                locale = if (isArabic) arabicLocale else englishLocale,
                voice = if (isArabic) arabicVoice else englishVoice,
            )
        }
    }

    fun buildMultilingualSsml(
        text: String,
        englishVoice: String = AzureNarrationConfig.DEFAULT_VOICE,
        englishLocale: String = DEFAULT_ENGLISH_LOCALE,
        arabicVoice: String = AzureNarrationConfig.DEFAULT_ARABIC_VOICE,
        arabicLocale: String = DEFAULT_ARABIC_LOCALE,
    ): String {
        val segments = segmentText(text, englishVoice, englishLocale, arabicVoice, arabicLocale)
        if (segments.isEmpty()) {
            return """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="$englishLocale"/>"""
        }

        return buildString {
            append("""<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="$englishLocale">""")
            segments.forEach { segment ->
                val trimmedText = segment.text.trim()
                if (trimmedText.isNotEmpty()) {
                    val escapedVoice = segment.voice.xmlEscape()
                    val escapedLocale = segment.locale.xmlEscape()
                    val escapedText = trimmedText.xmlEscape()
                    append("""<voice name="$escapedVoice"><lang xml:lang="$escapedLocale">$escapedText</lang></voice>""")
                }
            }
            append("</speak>")
        }
    }

    private data class RawSpan(val text: String, val language: NarrationLanguage)

    private fun splitIntoRawSpans(text: String): List<RawSpan> {
        // Break text by paragraph/sentence delimiters while preserving content
        val tokens = Regex("""([^\n.!؟?]+[\n.!؟?]*|\n+)""").findAll(text)
            .map { it.value }
            .filter { it.isNotEmpty() }
            .toList()

        val spans = mutableListOf<RawSpan>()

        tokens.forEach { token ->
            // Check if token contains internal script transitions
            val subSpans = splitTokenByScript(token)
            spans.addAll(subSpans)
        }

        return spans
    }

    private fun splitTokenByScript(token: String): List<RawSpan> {
        val result = mutableListOf<RawSpan>()
        val current = StringBuilder()
        var currentLang: NarrationLanguage? = null

        var i = 0
        while (i < token.length) {
            val codePoint = token.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val isAr = isArabicCodePoint(codePoint)
            val isLat = isLatinCodePoint(codePoint)

            val charLang = when {
                isAr -> NarrationLanguage.Arabic
                isLat -> NarrationLanguage.English
                else -> null // Punctuation, digits, whitespace, symbols
            }

            if (charLang != null) {
                if (currentLang == null) {
                    currentLang = charLang
                } else if (currentLang != charLang) {
                    // Script transition detected
                    if (current.isNotEmpty()) {
                        result += RawSpan(current.toString(), currentLang)
                        current.clear()
                    }
                    currentLang = charLang
                }
            }

            current.append(token.substring(i, i + charCount))
            i += charCount
        }

        if (current.isNotEmpty()) {
            result += RawSpan(current.toString(), currentLang ?: NarrationLanguage.English)
        }

        return result
    }

    private fun smoothSpans(spans: List<RawSpan>): List<RawSpan> {
        if (spans.isEmpty()) return emptyList()

        val merged = mutableListOf<RawSpan>()

        for (span in spans) {
            val last = merged.lastOrNull()

            // If the span contains no letters (e.g. only spaces, digits, punctuation)
            val hasArabic = span.text.any { isArabicCodePoint(it.code) }
            val hasLatin = span.text.any { isLatinCodePoint(it.code) }

            if (!hasArabic && !hasLatin) {
                // Attach neutral content to preceding span if available
                if (last != null) {
                    merged[merged.lastIndex] = last.copy(text = last.text + span.text)
                } else {
                    merged += span.copy(language = NarrationLanguage.English)
                }
                continue
            }

            // Normal merge of adjacent spans of the same language
            if (last != null && last.language == span.language) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged += span
            }
        }

        // Second pass: filter out completely blank or whitespace-only spans that didn't merge
        return merged.filter { it.text.isNotBlank() }
    }

    companion object {
        const val DEFAULT_ENGLISH_LOCALE = "en-AU"
        const val DEFAULT_ARABIC_LOCALE = "ar-SA"

        fun isArabicCodePoint(codePoint: Int): Boolean =
            codePoint in 0x0600..0x06FF || // Arabic
                codePoint in 0x0750..0x077F || // Arabic Supplement
                codePoint in 0x08A0..0x08FF || // Arabic Extended-A
                codePoint in 0xFB50..0xFDFF || // Arabic Presentation Forms-A
                codePoint in 0xFE70..0xFEFF    // Arabic Presentation Forms-B

        fun isLatinCodePoint(codePoint: Int): Boolean =
            codePoint in 0x0041..0x005A || // A-Z
                codePoint in 0x0061..0x007A || // a-z
                codePoint in 0x00C0..0x00FF || // Latin-1 Supplement letters
                codePoint in 0x0100..0x017F || // Latin Extended-A
                codePoint in 0x0180..0x024F    // Latin Extended-B

        fun xmlEscape(value: String): String =
            value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}

private fun String.xmlEscape(): String = BilingualTextSegmenter.xmlEscape(this)
