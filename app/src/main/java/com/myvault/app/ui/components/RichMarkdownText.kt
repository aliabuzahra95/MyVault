package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun RichMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(block = block, color = color)
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, color: Color) {
    when (block) {
        is MarkdownBlock.Heading -> MarkdownHeading(block)
        is MarkdownBlock.Paragraph -> MarkdownParagraph(block.text, color)
        is MarkdownBlock.Bullet -> MarkdownBullet(block.text, color)
        is MarkdownBlock.Numbered -> MarkdownNumbered(block, color)
        is MarkdownBlock.Quote -> MarkdownQuote(block)
        is MarkdownBlock.Code -> MarkdownCode(block)
        is MarkdownBlock.Table -> MarkdownTable(block)
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading) {
    val colors = VaultThemeTokens.colors
    Text(
        text = inlineMarkdown(block.text),
        style = when (block.level) {
            1 -> MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.W800, lineHeight = 22.sp)
            2 -> MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.W800, lineHeight = 22.sp)
            else -> MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.W700, lineHeight = 17.sp)
        },
        color = if (block.level <= 2) colors.text else colors.accent,
    )
}

@Composable
private fun MarkdownParagraph(text: String, color: Color) {
    Text(
        text = inlineMarkdown(text),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 22.sp),
        color = color,
    )
}

@Composable
private fun MarkdownBullet(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "-",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W900, lineHeight = 22.sp),
            color = VaultThemeTokens.colors.accent,
        )
        Text(
            text = inlineMarkdown(text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 22.sp),
            color = color,
        )
    }
}

@Composable
private fun MarkdownNumbered(block: MarkdownBlock.Numbered, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${block.number}.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W900, lineHeight = 22.sp),
            color = VaultThemeTokens.colors.accent,
        )
        Text(
            text = inlineMarkdown(block.text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 22.sp),
            color = color,
        )
    }
}

@Composable
private fun MarkdownQuote(block: MarkdownBlock.Quote) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = colors.accent.copy(alpha = 0.75f),
                shape = VaultShapes.pill,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .widthIn(min = 4.dp, max = 4.dp),
            ) {
                Box(modifier = Modifier.padding(vertical = 24.dp))
            }
            Text(
                text = inlineMarkdown(block.text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W500, lineHeight = 22.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MarkdownCode(block: MarkdownBlock.Code) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = block.text,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { cell ->
                        Surface(
                            color = if (rowIndex == 0) colors.accentSoft else colors.surface,
                            shape = VaultShapes.sm,
                            border = BorderStroke(1.dp, colors.border.copy(alpha = 0.72f)),
                        ) {
                            Text(
                                text = inlineMarkdown(cell),
                                modifier = Modifier
                                    .widthIn(min = 92.dp, max = 190.dp)
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (rowIndex == 0) FontWeight.W800 else FontWeight.W500,
                                    lineHeight = 18.sp,
                                ),
                                color = if (rowIndex == 0) colors.text else colors.textSecondary,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Numbered(val number: Int, val text: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Code(val language: String, val text: String) : MarkdownBlock()
    data class Table(val rows: List<List<String>>) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.normalizeMarkdownForMyVault().lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var paragraph = mutableListOf<String>()
    var tableLines = mutableListOf<String>()
    var codeLanguage: String? = null
    var codeLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph = mutableListOf()
        }
    }

    fun flushTable() {
        if (tableLines.isEmpty()) return
        parseMarkdownTable(tableLines)?.let { table ->
            blocks += table
        } ?: run {
            tableLines.forEach { line ->
                paragraph += cleanMarkdownLine(line.tableRowToReadableText())
            }
        }
        tableLines = mutableListOf()
    }

    fun flushCode() {
        val language = codeLanguage ?: return
        blocks += MarkdownBlock.Code(language = language, text = codeLines.joinToString("\n").trimEnd())
        codeLanguage = null
        codeLines = mutableListOf()
    }

    lines.forEach { raw ->
        val line = raw.trim()
        val activeCodeLanguage = codeLanguage
        if (activeCodeLanguage != null) {
            if (line.startsWith("```")) {
                flushCode()
            } else {
                codeLines += raw.trimEnd()
            }
            return@forEach
        }

        if (line.startsWith("```")) {
            flushParagraph()
            flushTable()
            codeLanguage = line.removePrefix("```").trim()
            codeLines = mutableListOf()
            return@forEach
        }

        if (line.isBlank()) {
            flushParagraph()
            flushTable()
            return@forEach
        }

        if (line.isMarkdownTableLine()) {
            flushParagraph()
            tableLines += line
            return@forEach
        } else {
            flushTable()
        }

        val headerLevel = markdownHeaderLevel(line)
        if (headerLevel != null) {
            flushParagraph()
            val cleanLine = line.trimStart().drop(headerLevel).trimStart()
            blocks += MarkdownBlock.Heading(headerLevel.coerceAtMost(3), cleanMarkdownLine(cleanLine))
            return@forEach
        }

        Regex("^>\\s?(.*)$").find(line)?.let { quote ->
            flushParagraph()
            blocks += MarkdownBlock.Quote(cleanMarkdownLine(quote.groupValues[1]))
            return@forEach
        }

        Regex("^([*\\-+•])\\s+(.+)$").find(line)?.let { bullet ->
            flushParagraph()
            blocks += MarkdownBlock.Bullet(cleanMarkdownLine(bullet.groupValues[2]))
            return@forEach
        }

        Regex("^(\\d+)[.)]\\s+(.+)$").find(line)?.let { numbered ->
            flushParagraph()
            blocks += MarkdownBlock.Numbered(
                number = numbered.groupValues[1].toIntOrNull() ?: 1,
                text = cleanMarkdownLine(numbered.groupValues[2]),
            )
            return@forEach
        }

        paragraph += cleanMarkdownLine(line)
    }

    flushCode()
    flushTable()
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(text.trim())) }
}

private fun String.normalizeMarkdownForMyVault(): String =
    replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("(?m)^(#{1,6})([^#\\s].*)$")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]}"
        }
        .replace(Regex("([.!?])\\s+(#{1,6})\\s*([^#\\s])")) { match ->
            "${match.groupValues[1]}\n\n${match.groupValues[2]} ${match.groupValues[3]}"
        }
        .replace(Regex("\\n{4,}"), "\n\n\n")
        .trim()

private fun markdownHeaderLevel(line: String): Int? {
    val trimmed = line.trimStart()
    val hashes = trimmed.takeWhile { it == '#' }.length
    if (hashes !in 1..6) return null
    val hasSpaceOrCompactTitle = trimmed.getOrNull(hashes)?.let { it == ' ' || it != '#' } == true
    return hashes.takeIf { hasSpaceOrCompactTitle }
}

private fun parseMarkdownTable(lines: List<String>): MarkdownBlock.Table? {
    val rawRows = lines.map { it.toMarkdownTableCells() }
        .filter { it.isNotEmpty() }
    if (rawRows.size < 2) return null
    val rows = rawRows.filterNot { it.isMarkdownDividerRow() }
    if (rows.size < 2) return null
    val columnCount = rows.maxOf { it.size }
    return MarkdownBlock.Table(
        rows = rows.map { row ->
            row + List((columnCount - row.size).coerceAtLeast(0)) { "" }
        },
    )
}

private fun String.isMarkdownTableLine(): Boolean {
    val trimmed = trim()
    if (trimmed.isMarkdownTableDividerLine()) return true
    val pipeCount = trimmed.count { it == '|' }
    return pipeCount >= 2 && (
        trimmed.startsWith("|") ||
            trimmed.endsWith("|") ||
            Regex("\\S\\s*\\|\\s*\\S").containsMatchIn(trimmed)
        )
}

private fun String.toMarkdownTableCells(): List<String> =
    trim()
        .trim('|')
        .split('|')
        .map { cleanMarkdownLine(it) }

private fun List<String>.isMarkdownDividerRow(): Boolean =
    isNotEmpty() && all { it.trim().matches(Regex(":?-{3,}:?")) }

private fun String.isMarkdownTableDividerLine(): Boolean =
    trim().trim('|')
        .split('|')
        .filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.all { it.trim().matches(Regex(":?-{3,}:?")) } == true

private fun String.tableRowToReadableText(): String =
    toMarkdownTableCells().filter { it.isNotBlank() }.joinToString(" - ")

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val source = text
        .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
        .replace(Regex("~~([^~]+)~~"), "$1")
    var index = 0
    var bold = false
    var code = false

    fun appendSegment(segment: String) {
        val style = when {
            code -> SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.W700)
            bold -> SpanStyle(fontWeight = FontWeight.W900)
            else -> null
        }
        if (style == null) {
            append(segment)
        } else {
            withStyle(style) { append(segment) }
        }
    }

    while (index < source.length) {
        when {
            source.startsWith("**", index) -> {
                bold = !bold
                index += 2
            }
            source[index] == '`' -> {
                code = !code
                index += 1
            }
            else -> {
                val nextBold = source.indexOf("**", startIndex = index).let { if (it == -1) source.length else it }
                val nextCode = source.indexOf('`', startIndex = index).let { if (it == -1) source.length else it }
                val next = minOf(nextBold, nextCode)
                appendSegment(source.substring(index, next))
                index = next
            }
        }
    }
}

private fun cleanMarkdownLine(text: String): String = text
    .trim()
    .removePrefix("- ")
    .removePrefix("* ")
    .removePrefix("+ ")
    .removePrefix("• ")
