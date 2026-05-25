package com.myvault.app.ui.screens

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat

data class RichImportResult(
    val document: VaultRichTextDocument,
    val formattingPreserved: Boolean,
)

fun parseRichImport(html: String?, plainText: String?): RichImportResult {
    val htmlText = html?.takeIf { it.isNotBlank() }
    if (htmlText != null) {
        return runCatching { htmlText.parseHtmlImport() }.getOrElse {
            RichImportResult(
                document = VaultRichTextDocument(text = htmlText.htmlToPlainTextFallback(), styleMarks = emptyList()),
                formattingPreserved = false,
            )
        }
    }

    val text = plainText.orEmpty()
    return if (text.looksLikeMarkdown()) {
        text.parseMarkdownImport()
    } else {
        RichImportResult(VaultRichTextDocument(text = text, styleMarks = emptyList()), formattingPreserved = false)
    }
}

private fun String.parseHtmlImport(): RichImportResult {
    val spanned = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val text = spanned.toString().trimEnd()
    val marks = mutableListOf<VaultStyleMark>()
    spanned.getSpans(0, spanned.length, StyleSpan::class.java).forEach { span ->
        val style = when (span.style) {
            Typeface.BOLD -> VaultInlineStyle.Bold
            Typeface.ITALIC -> VaultInlineStyle.Italic
            Typeface.BOLD_ITALIC -> null
            else -> null
        }
        if (style != null) {
            marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), style)
        } else if (span.style == Typeface.BOLD_ITALIC) {
            marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), VaultInlineStyle.Bold)
            marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), VaultInlineStyle.Italic)
        }
    }
    spanned.getSpans(0, spanned.length, UnderlineSpan::class.java).forEach { span ->
        marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), VaultInlineStyle.Underline)
    }
    spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { span ->
        marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), VaultInlineStyle.ColorBlue)
        marks += VaultStyleMark(spanned.getSpanStart(span), spanned.getSpanEnd(span), VaultInlineStyle.Underline)
    }
    text.headingRangesFromHtml(this).forEach { range ->
        marks += VaultStyleMark(range.first, range.endExclusive(), VaultInlineStyle.Heading)
    }
    text.quoteRangesFromHtml(this).forEach { range ->
        marks += VaultStyleMark(range.first, range.endExclusive(), VaultInlineStyle.Quote)
    }
    text.colorRangesFromHtml(this).forEach { (range, style) ->
        marks += VaultStyleMark(range.first, range.endExclusive(), style)
    }
    return RichImportResult(
        document = VaultRichTextDocument(text = text, styleMarks = marks.cleanMarks(text.length)),
        formattingPreserved = marks.isNotEmpty() || this.contains(Regex("</?(ul|ol|li|h\\d|strong|b|em|i|u|a|blockquote|span)\\b", RegexOption.IGNORE_CASE)),
    )
}

private fun String.parseMarkdownImport(): RichImportResult {
    val marks = mutableListOf<VaultStyleMark>()
    val output = StringBuilder()

    lines().forEachIndexed { index, rawLine ->
        if (index > 0) output.append('\n')
        val lineStart = output.length
        val line = when {
            rawLine.startsWith("# ") -> rawLine.removePrefix("# ").also {
                marks += VaultStyleMark(lineStart, lineStart + it.length, VaultInlineStyle.Heading)
            }
            rawLine.startsWith("## ") -> rawLine.removePrefix("## ").also {
                marks += VaultStyleMark(lineStart, lineStart + it.length, VaultInlineStyle.Heading)
            }
            rawLine.startsWith("- ") -> "• ${rawLine.removePrefix("- ")}"
            rawLine.matches(Regex("^\\d+\\.\\s+.*")) -> rawLine
            else -> rawLine
        }
        appendMarkdownInline(line, output, marks)
    }

    val text = output.toString()
    return RichImportResult(
        document = VaultRichTextDocument(text = text, styleMarks = marks.cleanMarks(text.length)),
        formattingPreserved = marks.isNotEmpty() || this.looksLikeMarkdown(),
    )
}

private fun appendMarkdownInline(input: String, output: StringBuilder, marks: MutableList<VaultStyleMark>) {
    var i = 0
    while (i < input.length) {
        when {
            input.startsWith("**", i) -> {
                val end = input.indexOf("**", i + 2)
                if (end > i) {
                    val start = output.length
                    output.append(input.substring(i + 2, end))
                    marks += VaultStyleMark(start, output.length, VaultInlineStyle.Bold)
                    i = end + 2
                } else output.append(input[i++])
            }
            input.startsWith("__", i) -> {
                val end = input.indexOf("__", i + 2)
                if (end > i) {
                    val start = output.length
                    output.append(input.substring(i + 2, end))
                    marks += VaultStyleMark(start, output.length, VaultInlineStyle.Underline)
                    i = end + 2
                } else output.append(input[i++])
            }
            input[i] == '*' -> {
                val end = input.indexOf('*', i + 1)
                if (end > i) {
                    val start = output.length
                    output.append(input.substring(i + 1, end))
                    marks += VaultStyleMark(start, output.length, VaultInlineStyle.Italic)
                    i = end + 1
                } else output.append(input[i++])
            }
            input[i] == '[' -> {
                val closeText = input.indexOf("](", i)
                val closeUrl = if (closeText >= 0) input.indexOf(')', closeText + 2) else -1
                if (closeText > i && closeUrl > closeText) {
                    val label = input.substring(i + 1, closeText)
                    val url = input.substring(closeText + 2, closeUrl)
                    val start = output.length
                    output.append(label)
                    if (url.isNotBlank() && url != label) output.append(" ($url)")
                    marks += VaultStyleMark(start, start + label.length, VaultInlineStyle.ColorBlue)
                    marks += VaultStyleMark(start, start + label.length, VaultInlineStyle.Underline)
                    i = closeUrl + 1
                } else output.append(input[i++])
            }
            else -> output.append(input[i++])
        }
    }
}

private fun String.looksLikeMarkdown(): Boolean =
    contains(Regex("(^|\\n)#{1,3}\\s+")) ||
        contains(Regex("(^|\\n)-\\s+")) ||
        contains(Regex("(^|\\n)\\d+\\.\\s+")) ||
        contains(Regex("\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|__[^_]+__|\\[[^]]+\\]\\([^)]+\\)"))

private fun String.headingRangesFromHtml(html: String): List<IntRange> {
    val headingTexts = Regex("<h[1-6][^>]*>(.*?)</h[1-6]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(html)
        .map { HtmlCompat.fromHtml(it.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
        .filter { it.isNotBlank() }
        .toList()
    var searchStart = 0
    return headingTexts.mapNotNull { heading ->
        val start = indexOf(heading, searchStart)
        if (start >= 0) {
            searchStart = start + heading.length
            start until searchStart
        } else {
            null
        }
    }
}

private fun String.quoteRangesFromHtml(html: String): List<IntRange> =
    rangesForHtmlTagContent(
        html = html,
        regex = Regex("<blockquote[^>]*>(.*?)</blockquote>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    )

private fun String.colorRangesFromHtml(html: String): List<Pair<IntRange, VaultInlineStyle>> {
    val regex = Regex(
        "<span[^>]*data-color\\s*=\\s*['\"]?([a-zA-Z]+)['\"]?[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    var searchStart = 0
    return regex.findAll(html).mapNotNull { match ->
        val style = match.groupValues.getOrNull(1)?.toVaultColorStyle() ?: return@mapNotNull null
        val spanText = HtmlCompat.fromHtml(match.groupValues[2], HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        if (spanText.isBlank()) return@mapNotNull null
        val start = indexOf(spanText, searchStart)
        if (start < 0) return@mapNotNull null
        val end = start + spanText.length
        searchStart = end
        (start until end) to style
    }.toList()
}

private fun String.rangesForHtmlTagContent(html: String, regex: Regex): List<IntRange> {
    val tagTexts = regex.findAll(html)
        .map { HtmlCompat.fromHtml(it.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
        .filter { it.isNotBlank() }
        .toList()
    var searchStart = 0
    return tagTexts.mapNotNull { value ->
        val start = indexOf(value, searchStart)
        if (start >= 0) {
            searchStart = start + value.length
            start until searchStart
        } else {
            null
        }
    }
}

private fun String.toVaultColorStyle(): VaultInlineStyle? =
    when (lowercase()) {
        "red" -> VaultInlineStyle.ColorRed
        "orange" -> VaultInlineStyle.ColorOrange
        "green" -> VaultInlineStyle.ColorGreen
        "blue" -> VaultInlineStyle.ColorBlue
        "purple" -> VaultInlineStyle.ColorPurple
        "pink" -> VaultInlineStyle.ColorPink
        "slate", "gray", "grey" -> VaultInlineStyle.ColorSlate
        else -> null
    }

private fun String.htmlToPlainTextFallback(): String =
    runCatching { HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trimEnd() }
        .getOrDefault(replace(Regex("<[^>]+>"), "").trim())

private fun IntRange.endExclusive(): Int = last + 1

private fun List<VaultStyleMark>.cleanMarks(textLength: Int): List<VaultStyleMark> =
    mapNotNull { mark ->
        val start = mark.start.coerceIn(0, textLength)
        val end = mark.end.coerceIn(0, textLength)
        if (start < end) VaultStyleMark(start, end, mark.style) else null
    }
