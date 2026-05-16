package com.myvault.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.components.EditorTool
import com.myvault.app.ui.theme.VaultColors
import org.json.JSONArray
import org.json.JSONObject

enum class VaultInlineStyle {
    Bold,
    Italic,
    Underline,
    Heading,
    Quote,
    ColorRed,
    ColorOrange,
    ColorGreen,
    ColorBlue,
    ColorPurple,
    ColorPink,
    ColorSlate,
}

data class VaultStyleMark(
    val start: Int,
    val end: Int,
    val style: VaultInlineStyle,
)

data class VaultNoteLink(
    val start: Int,
    val end: Int,
    val noteId: String,
)

data class VaultRichTextDocument(
    val text: String,
    val styleMarks: List<VaultStyleMark>,
    val noteLinks: List<VaultNoteLink> = emptyList(),
)

internal class VaultRichTextVisualTransformation(
    private val marks: List<VaultStyleMark>,
    private val noteLinks: List<VaultNoteLink> = emptyList(),
    private val colors: VaultColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            sanitizeVaultStyleMarks(marks, text.length).forEach { mark ->
                if (mark.start < mark.end && mark.start < text.length) {
                    addStyle(
                        style = mark.style.toSpanStyle(colors),
                        start = mark.start,
                        end = mark.end,
                    )
                }
            }
            sanitizeVaultNoteLinks(noteLinks, text.length).forEach { link ->
                if (link.start < link.end && link.start < text.length) {
                    addStyle(
                        style = SpanStyle(
                            color = colors.accent,
                            textDecoration = TextDecoration.Underline,
                        ),
                        start = link.start,
                        end = link.end,
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

internal fun buildVaultAnnotatedString(
    text: String,
    marks: List<VaultStyleMark>,
    noteLinks: List<VaultNoteLink> = emptyList(),
    colors: VaultColors,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        sanitizeVaultStyleMarks(marks, text.length).forEach { mark ->
            if (mark.start < mark.end && mark.start < text.length) {
                addStyle(
                    style = mark.style.toSpanStyle(colors),
                    start = mark.start,
                    end = mark.end,
                )
            }
        }
        sanitizeVaultNoteLinks(noteLinks, text.length).forEach { link ->
            if (link.start < link.end && link.start < text.length) {
                addStringAnnotation(tag = "noteLink", annotation = link.noteId, start = link.start, end = link.end)
                addStyle(
                    style = SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                    start = link.start,
                    end = link.end,
                )
            }
        }
    }

internal data class VaultToolbarStyleUpdate(
    val marks: List<VaultStyleMark>,
    val pendingStyles: Set<VaultInlineStyle>,
)

internal fun handleVaultRichTextChange(
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
    marks: List<VaultStyleMark>,
    pendingStyles: Set<VaultInlineStyle>,
): List<VaultStyleMark> {
    val safeOldValue = sanitizeVaultTextFieldValue(oldValue)
    val safeNewValue = sanitizeVaultTextFieldValue(newValue)
    val oldText = safeOldValue.text
    val newText = safeNewValue.text
    val safeMarks = sanitizeVaultStyleMarks(marks, oldText.length)
    if (oldText == newText) return sanitizeVaultStyleMarks(safeMarks, newText.length)

    val prefix = oldText.commonPrefixWith(newText).length
    val suffix = oldText.substring(prefix).commonSuffixWith(newText.substring(prefix)).length
    val oldEnd = oldText.length - suffix
    val newEnd = newText.length - suffix
    val insertedLength = (newEnd - prefix).coerceAtLeast(0)
    val delta = newText.length - oldText.length
    val insertionStyles = stylesForInsertedText(safeOldValue, safeMarks, pendingStyles)

    val updated = buildList {
        safeMarks.forEach { mark ->
            when {
                mark.end <= prefix -> add(mark)
                mark.start >= oldEnd -> add(mark.shift(delta))
                oldEnd == prefix && prefix in mark.start..mark.end -> add(mark.copy(end = mark.end + delta))
                else -> {
                    val leftEnd = minOf(mark.end, prefix)
                    if (mark.start < leftEnd) add(mark.copy(end = leftEnd))
                    val rightStart = maxOf(mark.start, oldEnd)
                    if (rightStart < mark.end) {
                        add(mark.copy(start = rightStart + delta, end = mark.end + delta))
                    }
                }
            }
        }

        if (insertedLength > 0) {
            insertionStyles.forEach { style ->
                add(VaultStyleMark(start = prefix, end = prefix + insertedLength, style = style))
            }
        }
    }

    return sanitizeVaultStyleMarks(updated, newText.length)
}

internal fun handleVaultNoteLinkChange(
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
    links: List<VaultNoteLink>,
): List<VaultNoteLink> {
    val oldText = sanitizeVaultTextFieldValue(oldValue).text
    val newText = sanitizeVaultTextFieldValue(newValue).text
    val safeLinks = sanitizeVaultNoteLinks(links, oldText.length)
    if (oldText == newText) return sanitizeVaultNoteLinks(safeLinks, newText.length)

    val prefix = oldText.commonPrefixWith(newText).length
    val suffix = oldText.substring(prefix).commonSuffixWith(newText.substring(prefix)).length
    val oldEnd = oldText.length - suffix
    val delta = newText.length - oldText.length

    return sanitizeVaultNoteLinks(safeLinks.mapNotNull { link ->
        when {
            link.end <= prefix -> link
            link.start >= oldEnd -> link.copy(start = link.start + delta, end = link.end + delta)
            oldEnd == prefix && prefix in link.start..link.end -> link.copy(end = link.end + delta)
            else -> null
        }
    }, newText.length)
}

internal fun applyVaultStyleFromToolbar(
    value: TextFieldValue,
    marks: List<VaultStyleMark>,
    pendingStyles: Set<VaultInlineStyle>,
    style: VaultInlineStyle,
): VaultToolbarStyleUpdate {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val safeMarks = sanitizeVaultStyleMarks(marks, safeValue.text.length)
    val range = normalizedSelection(safeValue.selection, safeValue.text.length)
    if (!range.collapsed) {
        return VaultToolbarStyleUpdate(
            marks = sanitizeVaultStyleMarks(applyStyleToSelection(safeValue, safeMarks, style), safeValue.text.length),
            pendingStyles = pendingStyles,
        )
    }

    val cursor = range.start
    val relatedStyles = relatedStylesFor(style)
    if (pendingStyles.any { it in relatedStyles }) {
        return VaultToolbarStyleUpdate(
            marks = safeMarks,
            pendingStyles = togglePendingStyle(pendingStyles, style),
        )
    }

    val activeRelatedStyle = relatedStyles.firstOrNull { related ->
        styleMarkAtOffset(cursor, safeMarks, related) != null
    }

    if (activeRelatedStyle != null) {
        val activeRange = styleMarkAtOffset(cursor, safeMarks, activeRelatedStyle)
            ?.let { TextRange(it.start, it.end) }
            ?: return VaultToolbarStyleUpdate(marks = safeMarks, pendingStyles = pendingStyles)
        var updatedMarks = safeMarks
        relatedStyles.forEach { related ->
            updatedMarks = removeStyleFromRange(updatedMarks, activeRange, related)
        }
        if (activeRelatedStyle != style) {
            updatedMarks = sanitizeVaultStyleMarks(updatedMarks + VaultStyleMark(activeRange.start, activeRange.end, style), safeValue.text.length)
        } else {
            updatedMarks = sanitizeVaultStyleMarks(updatedMarks, safeValue.text.length)
        }
        return VaultToolbarStyleUpdate(
            marks = updatedMarks,
            pendingStyles = pendingStyles.filterNotTo(linkedSetOf()) { it in relatedStyles },
        )
    }

    return VaultToolbarStyleUpdate(
        marks = safeMarks,
        pendingStyles = togglePendingStyle(pendingStyles, style),
    )
}

internal fun activeVaultToolsForSelection(
    value: TextFieldValue,
    marks: List<VaultStyleMark>,
    pendingStyles: Set<VaultInlineStyle>,
): Set<EditorTool> {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val safeMarks = sanitizeVaultStyleMarks(marks, safeValue.text.length)
    val range = normalizedSelection(safeValue.selection, safeValue.text.length)
    val styles = if (range.collapsed) {
        pendingStyles + activeStylesAtOffset(range.start, safeMarks)
    } else {
        VaultInlineStyle.entries.filterTo(linkedSetOf()) { style ->
            selectionFullyStyled(range, safeMarks, style)
        }
    }
    return buildSet {
        if (VaultInlineStyle.Bold in styles) add(EditorTool.Bold)
        if (VaultInlineStyle.Italic in styles) add(EditorTool.Italic)
        if (VaultInlineStyle.Underline in styles) add(EditorTool.Underline)
        if (VaultInlineStyle.Heading in styles) add(EditorTool.Heading)
        if (VaultInlineStyle.Quote in styles) add(EditorTool.Quote)
        if (styles.any { it.isColor() }) add(EditorTool.TextColor)
    }
}

internal fun applyBulletList(value: TextFieldValue): TextFieldValue =
    applyBulletListTransform(value).value

internal fun applyNumberedList(value: TextFieldValue): TextFieldValue =
    applyNumberedListTransform(value).value

internal data class VaultTextTransform(
    val oldValueForMarks: TextFieldValue,
    val value: TextFieldValue,
)

internal fun applyBulletListTransform(value: TextFieldValue): VaultTextTransform =
    transformLines(value) { _, line ->
        when {
            NUMBERED_LIST_REGEX.containsMatchIn(line) -> "$BULLET_PREFIX${line.replaceFirst(NUMBERED_LIST_REGEX, "")}"
            line.startsWith(BULLET_PREFIX) -> line.removePrefix(BULLET_PREFIX)
            else -> "$BULLET_PREFIX$line"
        }
    }

internal fun applyNumberedListTransform(value: TextFieldValue): VaultTextTransform {
    val lines = selectedLines(value)
    val allNumbered = lines.all { line -> NUMBERED_LIST_REGEX.containsMatchIn(line.text) }
    return transformLines(value) { index, line ->
        when {
            allNumbered -> line.replaceFirst(NUMBERED_LIST_REGEX, "")
            line.startsWith(BULLET_PREFIX) -> "${index + 1}. ${line.removePrefix(BULLET_PREFIX)}"
            else -> "${index + 1}. ${line.replaceFirst(NUMBERED_LIST_REGEX, "")}"
        }
    }
}

internal fun clearVaultColorFromToolbar(
    value: TextFieldValue,
    marks: List<VaultStyleMark>,
    pendingStyles: Set<VaultInlineStyle>,
): VaultToolbarStyleUpdate {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val safeMarks = sanitizeVaultStyleMarks(marks, safeValue.text.length)
    val range = normalizedSelection(safeValue.selection, safeValue.text.length)
    if (!range.collapsed) {
        var updated = safeMarks
        colorStyles().forEach { color ->
            updated = removeStyleFromRange(updated, range, color)
        }
        return VaultToolbarStyleUpdate(marks = sanitizeVaultStyleMarks(updated, safeValue.text.length), pendingStyles = pendingStyles)
    }

    val activeColor = colorStyles().firstNotNullOfOrNull { color ->
        styleMarkAtOffset(range.start, safeMarks, color)
    }
    if (pendingStyles.any { it.isColor() }) {
        return VaultToolbarStyleUpdate(
            marks = safeMarks,
            pendingStyles = pendingStyles.filterNotTo(linkedSetOf()) { it.isColor() },
        )
    }

    val updatedMarks = if (activeColor != null) {
        var updated = safeMarks
        colorStyles().forEach { color ->
            updated = removeStyleFromRange(updated, TextRange(activeColor.start, activeColor.end), color)
        }
        sanitizeVaultStyleMarks(updated, safeValue.text.length)
    } else {
        safeMarks
    }
    return VaultToolbarStyleUpdate(
        marks = updatedMarks,
        pendingStyles = pendingStyles.filterNotTo(linkedSetOf()) { it.isColor() },
    )
}

internal fun continueListOnNewline(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val safeOldValue = sanitizeVaultTextFieldValue(oldValue)
    val safeNewValue = sanitizeVaultTextFieldValue(newValue)
    if (safeNewValue.text.length != safeOldValue.text.length + 1) return safeNewValue
    val cursor = safeNewValue.selection.start.coerceIn(0, safeNewValue.text.length)
    if (cursor == 0 || safeNewValue.text.getOrNull(cursor - 1) != '\n') return safeNewValue

    val previousLineStart = safeNewValue.text.lastIndexOf('\n', startIndex = (cursor - 2).coerceAtLeast(0))
        .let { if (it == -1) 0 else it + 1 }
    val previousLine = safeNewValue.text.substring(previousLineStart, cursor - 1)
    val prefix = when {
        previousLine == BULLET_PREFIX.trimEnd() || previousLine.matches(NUMBERED_LIST_EMPTY_REGEX) -> {
            val removeStart = previousLineStart
            val removeEnd = cursor
            val text = safeNewValue.text.removeRange(removeStart, removeEnd)
            return sanitizeVaultTextFieldValue(safeNewValue.copy(text = text, selection = TextRange(removeStart, removeStart)))
        }
        previousLine.startsWith(BULLET_PREFIX) -> BULLET_PREFIX
        NUMBERED_LIST_REGEX.containsMatchIn(previousLine) -> {
            val number = previousLine.substringBefore(".").toIntOrNull()?.plus(1) ?: 1
            "$number. "
        }
        else -> return safeNewValue
    }
    val text = safeNewValue.text.substring(0, cursor) + prefix + safeNewValue.text.substring(cursor)
    val nextCursor = cursor + prefix.length
    return sanitizeVaultTextFieldValue(safeNewValue.copy(text = text, selection = TextRange(nextCursor, nextCursor)))
}

internal fun VaultRichTextDocument.toStorageJson(): String =
    JSONObject()
        .put("text", text)
        .put("styleMarks", JSONArray().also { array ->
            styleMarks.forEach { mark ->
                array.put(
                    JSONObject()
                        .put("start", mark.start)
                        .put("end", mark.end)
                        .put("style", mark.style.name),
                )
            }
        })
        .put("noteLinks", JSONArray().also { array ->
            noteLinks.forEach { link ->
                array.put(
                    JSONObject()
                        .put("start", link.start)
                        .put("end", link.end)
                        .put("noteId", link.noteId),
                )
            }
        })
        .toString()

fun List<VaultStyleMark>.toJsonArrayString(): String =
    JSONArray().also { array ->
        forEach { mark ->
            array.put(
                JSONObject()
                    .put("start", mark.start)
                    .put("end", mark.end)
                    .put("style", mark.style.name),
            )
        }
    }.toString()

fun List<VaultNoteLink>.toNoteLinksJsonArrayString(): String =
    JSONArray().also { array ->
        forEach { link ->
            array.put(
                JSONObject()
                    .put("start", link.start)
                    .put("end", link.end)
                    .put("noteId", link.noteId),
            )
        }
    }.toString()

internal fun parseVaultRichTextDocument(content: String): VaultRichTextDocument? =
    runCatching {
        val json = JSONObject(content)
        val text = json.optString("text")
        VaultRichTextDocument(
            text = text,
            styleMarks = sanitizeVaultStyleMarks(json.optJSONArray("styleMarks").toStyleMarks(text.length), text.length),
            noteLinks = sanitizeVaultNoteLinks(json.optJSONArray("noteLinks").toNoteLinks(text.length), text.length),
        )
    }.getOrNull()

private fun JSONArray?.toStyleMarks(textLength: Int): List<VaultStyleMark> {
    if (this == null) return emptyList()
    return List(length()) { index -> optJSONObject(index) }
        .mapNotNull { json ->
            val style = runCatching { VaultInlineStyle.valueOf(json.optString("style")) }.getOrNull()
            val start = json.optInt("start").coerceIn(0, textLength)
            val end = json.optInt("end").coerceIn(0, textLength)
            if (style == null) {
                null
            } else {
                VaultStyleMark(
                    start = start,
                    end = end,
                    style = style,
                )
            }
        }
        .let(::normalizeMarks)
}

private fun JSONArray?.toNoteLinks(textLength: Int): List<VaultNoteLink> {
    if (this == null) return emptyList()
    return List(length()) { index -> optJSONObject(index) }
        .mapNotNull { json ->
            val noteId = json.optString("noteId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val start = json.optInt("start").coerceIn(0, textLength)
            val end = json.optInt("end").coerceIn(0, textLength)
            if (start < end) VaultNoteLink(start = start, end = end, noteId = noteId) else null
        }
}

internal fun sanitizeVaultStyleMarks(
    marks: List<VaultStyleMark>,
    textLength: Int,
): List<VaultStyleMark> {
    val safeLength = textLength.coerceAtLeast(0)
    return marks
        .mapNotNull { mark ->
            val start = mark.start.coerceIn(0, safeLength)
            val end = mark.end.coerceIn(0, safeLength)
            if (start < end) mark.copy(start = start, end = end) else null
        }
        .let(::normalizeMarks)
}

internal fun sanitizeVaultNoteLinks(
    links: List<VaultNoteLink>,
    textLength: Int,
): List<VaultNoteLink> {
    val safeLength = textLength.coerceAtLeast(0)
    return links.mapNotNull { link ->
        val start = link.start.coerceIn(0, safeLength)
        val end = link.end.coerceIn(0, safeLength)
        if (start < end && link.noteId.isNotBlank()) link.copy(start = start, end = end) else null
    }
}

internal fun sanitizeVaultTextFieldValue(value: TextFieldValue): TextFieldValue {
    val textLength = value.text.length
    val start = value.selection.start.coerceIn(0, textLength)
    val end = value.selection.end.coerceIn(0, textLength)
    val composition = value.composition?.let { range ->
        val compositionStart = range.start.coerceIn(0, textLength)
        val compositionEnd = range.end.coerceIn(0, textLength)
        if (compositionStart < compositionEnd) TextRange(compositionStart, compositionEnd) else null
    }
    return value.copy(selection = TextRange(start, end), composition = composition)
}

private fun applyStyleToSelection(
    value: TextFieldValue,
    marks: List<VaultStyleMark>,
    style: VaultInlineStyle,
): List<VaultStyleMark> {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val range = normalizedSelection(safeValue.selection, safeValue.text.length)
    if (range.collapsed) return marks

    val relatedStyles = relatedStylesFor(style)
    val shouldRemove = selectionFullyStyled(range, marks, style)
    var updated = marks
    relatedStyles.forEach { related ->
        updated = removeStyleFromRange(updated, range, related)
    }
    if (!shouldRemove) {
        updated = updated + VaultStyleMark(range.start, range.end, style)
    }
    return normalizeMarks(updated)
}

private fun transformLines(
    value: TextFieldValue,
    transform: (Int, String) -> String,
): VaultTextTransform {
    val safeValue = sanitizeVaultTextFieldValue(value)
    val lines = selectedLines(safeValue)
    if (lines.isEmpty()) return VaultTextTransform(safeValue, safeValue)
    val replacement = lines.mapIndexed { index, line -> transform(index, line.text) }.joinToString("\n")
    val selectedOriginal = safeValue.text.substring(lines.first().start, lines.last().end)
    val newText = buildString {
        append(safeValue.text.substring(0, lines.first().start))
        append(replacement)
        append(safeValue.text.substring(lines.last().end))
    }
    val selectionEnd = lines.first().start + replacement.length
    val oldValueForMarks = safeValue.copy(
        selection = TextRange(lines.first().start + selectedOriginal.length, lines.first().start + selectedOriginal.length),
    )
    return VaultTextTransform(
        oldValueForMarks = sanitizeVaultTextFieldValue(oldValueForMarks),
        value = sanitizeVaultTextFieldValue(safeValue.copy(text = newText, selection = TextRange(selectionEnd, selectionEnd))),
    )
}

private data class LineSlice(val start: Int, val end: Int, val text: String)

private fun selectedLines(value: TextFieldValue): List<LineSlice> {
    val text = value.text
    if (text.isEmpty()) return listOf(LineSlice(0, 0, ""))
    val range = normalizedSelection(value.selection, text.length)
    val blockStart = text.lastIndexOf('\n', startIndex = (range.start - 1).coerceAtLeast(0))
        .let { if (it == -1) 0 else it + 1 }
    val blockEndBase = text.indexOf('\n', startIndex = range.end)
    val blockEnd = if (blockEndBase == -1) text.length else blockEndBase
    val blockText = text.substring(blockStart, blockEnd)
    var offset = blockStart
    return blockText.split('\n').map { line ->
        val lineStart = offset
        val lineEnd = lineStart + line.length
        offset = lineEnd + 1
        LineSlice(lineStart, lineEnd, line)
    }
}

private fun stylesForInsertedText(
    oldValue: TextFieldValue,
    marks: List<VaultStyleMark>,
    pendingStyles: Set<VaultInlineStyle>,
): Set<VaultInlineStyle> =
    pendingStyles.ifEmpty { activeStylesAtOffset(oldValue.selection.start.coerceIn(0, oldValue.text.length), marks) }

private fun activeStylesAtOffset(offset: Int, marks: List<VaultStyleMark>): Set<VaultInlineStyle> =
    marks.filterTo(linkedSetOf()) { mark -> offset >= mark.start && (offset < mark.end || (offset == mark.end && offset > mark.start)) }.mapTo(linkedSetOf()) { it.style }

private fun styleMarkAtOffset(offset: Int, marks: List<VaultStyleMark>, style: VaultInlineStyle): VaultStyleMark? =
    normalizeMarks(marks.filter { it.style == style }).firstOrNull { offset >= it.start && (offset < it.end || (offset == it.end && offset > it.start)) }

private fun selectionFullyStyled(range: TextRange, marks: List<VaultStyleMark>, style: VaultInlineStyle): Boolean {
    if (range.collapsed) return false
    val sameStyleMarks = normalizeMarks(marks.filter { it.style == style })
    if (sameStyleMarks.isEmpty()) return false
    var cursor = range.start
    while (cursor < range.end) {
        val covering = sameStyleMarks.firstOrNull { cursor >= it.start && cursor < it.end } ?: return false
        cursor = covering.end
    }
    return true
}

private fun removeStyleFromRange(
    marks: List<VaultStyleMark>,
    range: TextRange,
    style: VaultInlineStyle,
): List<VaultStyleMark> =
    buildList {
        marks.forEach { mark ->
            if (mark.style != style || mark.end <= range.start || mark.start >= range.end) {
                add(mark)
            } else {
                if (mark.start < range.start) add(mark.copy(end = range.start))
                if (mark.end > range.end) add(mark.copy(start = range.end))
            }
        }
    }

private fun normalizeMarks(marks: List<VaultStyleMark>): List<VaultStyleMark> =
    marks
        .filter { it.start < it.end }
        .sortedWith(compareBy<VaultStyleMark> { it.style.ordinal }.thenBy { it.start }.thenBy { it.end })
        .fold(mutableListOf()) { acc, mark ->
            val previous = acc.lastOrNull()
            if (previous != null && previous.style == mark.style && mark.start <= previous.end) {
                acc[acc.lastIndex] = previous.copy(end = maxOf(previous.end, mark.end))
            } else {
                acc += mark
            }
            acc
        }

private fun normalizedSelection(selection: TextRange, textLength: Int = Int.MAX_VALUE): TextRange {
    val safeLength = textLength.coerceAtLeast(0)
    val start = minOf(selection.start, selection.end).coerceIn(0, safeLength)
    val end = maxOf(selection.start, selection.end).coerceIn(0, safeLength)
    return TextRange(start, end)
}

private fun VaultStyleMark.shift(delta: Int): VaultStyleMark = copy(start = start + delta, end = end + delta)

private fun togglePendingStyle(
    pendingStyles: Set<VaultInlineStyle>,
    style: VaultInlineStyle,
): Set<VaultInlineStyle> {
    if (style in pendingStyles) return pendingStyles - style
    val mutable = pendingStyles.toMutableSet()
    relatedStylesFor(style).forEach { mutable.remove(it) }
    mutable.add(style)
    return mutable
}

private fun relatedStylesFor(style: VaultInlineStyle): List<VaultInlineStyle> =
    when {
        style == VaultInlineStyle.Heading -> listOf(VaultInlineStyle.Heading)
        style.isColor() -> colorStyles()
        else -> listOf(style)
    }

private fun colorStyles(): List<VaultInlineStyle> =
    listOf(
        VaultInlineStyle.ColorRed,
        VaultInlineStyle.ColorOrange,
        VaultInlineStyle.ColorGreen,
        VaultInlineStyle.ColorBlue,
        VaultInlineStyle.ColorPurple,
        VaultInlineStyle.ColorPink,
        VaultInlineStyle.ColorSlate,
    )

private fun VaultInlineStyle.isColor(): Boolean = this in colorStyles()

private fun VaultInlineStyle.toSpanStyle(colors: VaultColors): SpanStyle =
    when (this) {
        VaultInlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        VaultInlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        VaultInlineStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        VaultInlineStyle.Heading -> SpanStyle(color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        VaultInlineStyle.Quote -> SpanStyle(color = colors.textSecondary, fontStyle = FontStyle.Italic)
        VaultInlineStyle.ColorRed -> SpanStyle(color = Color(0xFFE5484D))
        VaultInlineStyle.ColorOrange -> SpanStyle(color = Color(0xFFF97316))
        VaultInlineStyle.ColorGreen -> SpanStyle(color = Color(0xFF2F9E66))
        VaultInlineStyle.ColorBlue -> SpanStyle(color = Color(0xFF2F80ED))
        VaultInlineStyle.ColorPurple -> SpanStyle(color = Color(0xFF8B5CF6))
        VaultInlineStyle.ColorPink -> SpanStyle(color = Color(0xFFDB2777))
        VaultInlineStyle.ColorSlate -> SpanStyle(color = Color(0xFF64748B))
    }

private const val BULLET_PREFIX = "\u2022 "
private val NUMBERED_LIST_REGEX = Regex("^\\d+\\.\\s")
private val NUMBERED_LIST_EMPTY_REGEX = Regex("^\\d+\\.$")
