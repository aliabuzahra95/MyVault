package com.myvault.app.data.repository

import com.myvault.app.ui.screens.VaultInlineStyle
import com.myvault.app.ui.screens.VaultRichTextDocument
import com.myvault.app.ui.screens.VaultStyleMark

internal data class PdfTextRun(val text: String, val styles: Set<VaultInlineStyle>)

internal data class PdfTextParagraph(
    val text: String,
    val runs: List<PdfTextRun>,
    val listPrefixLength: Int,
    val rightToLeft: Boolean,
)

internal object RichTextPdfDocument {
    fun paragraphs(source: VaultRichTextDocument): List<PdfTextParagraph> {
        val lines = source.text.split('\n')
        var offset = 0
        return lines.map { line ->
            val lineEnd = offset + line.length
            val activeMarks = source.styleMarks.filter { it.start < lineEnd && it.end > offset }
            val boundaries = mutableSetOf(0, line.length)
            activeMarks.forEach { mark ->
                boundaries += (mark.start - offset).coerceIn(0, line.length)
                boundaries += (mark.end - offset).coerceIn(0, line.length)
            }
            val runs = boundaries.sorted().zipWithNext().mapNotNull { (start, end) ->
                if (start == end) null else PdfTextRun(
                    text = line.substring(start, end),
                    styles = activeMarks.filter { it.start < offset + end && it.end > offset + start }
                        .map(VaultStyleMark::style).toSet(),
                )
            }
            offset = lineEnd + 1
            PdfTextParagraph(
                text = line,
                runs = runs,
                listPrefixLength = when {
                    line.startsWith("\u2022 ") -> 2
                    else -> Regex("^\\d+\\.\\s").find(line)?.value?.length ?: 0
                },
                rightToLeft = line.firstOrNull { Character.getDirectionality(it) == Character.DIRECTIONALITY_LEFT_TO_RIGHT ||
                    Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                    Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC }
                    ?.let { Character.getDirectionality(it) != Character.DIRECTIONALITY_LEFT_TO_RIGHT } ?: false,
            )
        }
    }
}
