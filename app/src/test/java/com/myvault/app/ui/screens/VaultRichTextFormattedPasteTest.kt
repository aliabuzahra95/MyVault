package com.myvault.app.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRichTextFormattedPasteTest {
    @Test
    fun formattedPasteIntoBlankNoteKeepsImportedMarks() {
        val imported = VaultRichTextDocument(
            text = "Heading\nقال الله\nEnglish",
            styleMarks = listOf(
                VaultStyleMark(0, 7, VaultInlineStyle.Heading),
                VaultStyleMark(8, 16, VaultInlineStyle.ColorRed),
                VaultStyleMark(17, 24, VaultInlineStyle.Bold),
            ),
        )

        val result = insertVaultRichTextDocumentAtSelection(
            value = TextFieldValue("", selection = TextRange(0)),
            marks = emptyList(),
            noteLinks = emptyList(),
            inserted = imported,
        )

        assertEquals(imported.text, result.value.text)
        assertEquals(TextRange(imported.text.length), result.value.selection)
        assertHasMark(result.styleMarks, VaultInlineStyle.Heading, 0, 7)
        assertHasMark(result.styleMarks, VaultInlineStyle.ColorRed, 8, 16)
        assertHasMark(result.styleMarks, VaultInlineStyle.Bold, 17, 24)
    }

    @Test
    fun formattedPasteInMiddlePreservesSurroundingFormatting() {
        val existing = TextFieldValue("Start  end", selection = TextRange(6))
        val imported = VaultRichTextDocument(
            text = "middle",
            styleMarks = listOf(VaultStyleMark(0, 6, VaultInlineStyle.Italic)),
        )

        val result = insertVaultRichTextDocumentAtSelection(
            value = existing,
            marks = listOf(
                VaultStyleMark(0, 5, VaultInlineStyle.Bold),
                VaultStyleMark(7, 10, VaultInlineStyle.Underline),
            ),
            noteLinks = emptyList(),
            inserted = imported,
        )

        assertEquals("Start middle end", result.value.text)
        assertEquals(TextRange(12), result.value.selection)
        assertHasMark(result.styleMarks, VaultInlineStyle.Bold, 0, 5)
        assertHasMark(result.styleMarks, VaultInlineStyle.Italic, 6, 12)
        assertHasMark(result.styleMarks, VaultInlineStyle.Underline, 13, 16)
    }

    @Test
    fun formattedPasteReplacementDropsSelectedFormattingOnly() {
        val existing = TextFieldValue("Red old tail", selection = TextRange(4, 7))
        val imported = VaultRichTextDocument(
            text = "new",
            styleMarks = listOf(VaultStyleMark(0, 3, VaultInlineStyle.ColorBlue)),
        )

        val result = insertVaultRichTextDocumentAtSelection(
            value = existing,
            marks = listOf(
                VaultStyleMark(0, 3, VaultInlineStyle.ColorRed),
                VaultStyleMark(4, 7, VaultInlineStyle.Bold),
                VaultStyleMark(8, 12, VaultInlineStyle.Underline),
            ),
            noteLinks = listOf(VaultNoteLink(8, 12, "tail-note")),
            inserted = imported,
        )

        assertEquals("Red new tail", result.value.text)
        assertHasMark(result.styleMarks, VaultInlineStyle.ColorRed, 0, 3)
        assertHasMark(result.styleMarks, VaultInlineStyle.ColorBlue, 4, 7)
        assertFalse(result.styleMarks.any { it.style == VaultInlineStyle.Bold })
        assertHasMark(result.styleMarks, VaultInlineStyle.Underline, 8, 12)
        assertEquals(listOf(VaultNoteLink(8, 12, "tail-note")), result.noteLinks)
    }

    @Test
    fun markdownFallbackCreatesEditableFormatting() {
        val imported = parseRichImport(
            html = null,
            plainText = "# Title\n**Bold** and __underlined__\n- نقطة عربية",
        )

        assertTrue(imported.formattingPreserved)
        assertEquals("Title\nBold and underlined\n• نقطة عربية", imported.document.text)
        assertHasMark(imported.document.styleMarks, VaultInlineStyle.Heading, 0, 5)
        assertHasMark(imported.document.styleMarks, VaultInlineStyle.Bold, 6, 10)
        assertHasMark(imported.document.styleMarks, VaultInlineStyle.Underline, 15, 25)
    }

    @Test
    fun plainTextHtmlSourceIsRecognisedBeforeMarkdownFallback() {
        val source = """
            <h2>Evidence</h2>
            <p><span data-color="red">قُلْ هُوَ اللَّهُ أَحَدٌ</span></p>
            <p><strong>Explanation:</strong> Allah is One.</p>
        """.trimIndent()

        assertEquals(source, htmlSourceFromPlainTextForRichImport(source))
    }

    @Test
    fun fencedPlainTextHtmlSourceIsUnwrappedForHtmlImport() {
        val importedSource = htmlSourceFromPlainTextForRichImport(
            """
                ```html
                <h3>Point</h3>
                <p><b>Bold line</b></p>
                ```
            """.trimIndent(),
        )

        assertEquals("<h3>Point</h3>\n<p><b>Bold line</b></p>", importedSource)
    }

    @Test
    fun unrecognisedAngleBracketsStayPlainText() {
        assertEquals(null, htmlSourceFromPlainTextForRichImport("Use x < y and y > z literally."))
    }

    @Test
    fun plainTextHtmlInsertionRoundTripPreservesSurroundingFormatting() {
        val htmlSource = "<p><span data-color=\"red\">قال الله</span> <strong>clear</strong></p>"
        val imported = VaultRichTextDocument(
            text = "قال الله clear\n",
            styleMarks = listOf(
                VaultStyleMark(0, 8, VaultInlineStyle.ColorRed),
                VaultStyleMark(9, 14, VaultInlineStyle.Bold),
            ),
        )

        assertEquals(htmlSource, htmlSourceFromPlainTextForRichImport(htmlSource))
        val result = insertVaultRichTextDocumentAtSelection(
            value = TextFieldValue("Before\nAfter", selection = TextRange(7)),
            marks = listOf(VaultStyleMark(0, 6, VaultInlineStyle.Heading2)),
            noteLinks = emptyList(),
            inserted = imported,
        )

        val restored = parseVaultRichTextDocument(
            VaultRichTextDocument(
                text = result.value.text,
                styleMarks = result.styleMarks,
                noteLinks = result.noteLinks,
            ).toStorageJson(),
        )

        assertNotNull(restored)
        assertEquals("Before\nقال الله clear\nAfter", restored?.text)
        assertEquals(result.styleMarks, restored?.styleMarks)
        assertHasMark(restored?.styleMarks.orEmpty(), VaultInlineStyle.Heading2, 0, 6)
        assertHasMark(restored?.styleMarks.orEmpty(), VaultInlineStyle.ColorRed, 7, 15)
        assertHasMark(restored?.styleMarks.orEmpty(), VaultInlineStyle.Bold, 16, 21)
    }

    @Test
    fun smartDirectPasteImportsPlainTextHtmlSource() {
        val clipboardHtml = "<h2>Evidence</h2><p><span data-color=\"red\">قال الله</span></p>"
        val imported = RichImportResult(
            document = VaultRichTextDocument(
                text = "Evidence\nقال الله",
                styleMarks = listOf(
                    VaultStyleMark(0, 8, VaultInlineStyle.Heading2),
                    VaultStyleMark(9, 17, VaultInlineStyle.ColorRed),
                ),
            ),
            formattingPreserved = true,
        )

        val result = insertVaultRichTextDocumentForDirectPaste(
            oldValue = TextFieldValue("", selection = TextRange(0)),
            newValue = TextFieldValue(clipboardHtml, selection = TextRange(clipboardHtml.length)),
            marks = emptyList(),
            noteLinks = emptyList(),
            imported = imported,
            clipboardText = clipboardHtml,
        )

        assertNotNull(result)
        assertEquals("Evidence\nقال الله", result?.value?.text)
        assertHasMark(result?.styleMarks.orEmpty(), VaultInlineStyle.Heading2, 0, 8)
        assertHasMark(result?.styleMarks.orEmpty(), VaultInlineStyle.ColorRed, 9, 17)
    }

    @Test
    fun smartDirectPastePreservesStyledClipboardText() {
        val imported = RichImportResult(
            document = VaultRichTextDocument(
                text = "Styled",
                styleMarks = listOf(VaultStyleMark(0, 6, VaultInlineStyle.Underline)),
            ),
            formattingPreserved = true,
        )

        val result = insertVaultRichTextDocumentForDirectPaste(
            oldValue = TextFieldValue("", selection = TextRange(0)),
            newValue = TextFieldValue("Styled", selection = TextRange(6)),
            marks = emptyList(),
            noteLinks = emptyList(),
            imported = imported,
            clipboardText = "Styled",
        )

        assertNotNull(result)
        assertEquals("Styled", result?.value?.text)
        assertHasMark(result?.styleMarks.orEmpty(), VaultInlineStyle.Underline, 0, 6)
    }

    @Test
    fun smartDirectPasteCanUseMarkdownFallback() {
        val clipboardMarkdown = "# Title\n**Bold**"
        val imported = parseRichImport(html = null, plainText = clipboardMarkdown)

        val result = insertVaultRichTextDocumentForDirectPaste(
            oldValue = TextFieldValue("", selection = TextRange(0)),
            newValue = TextFieldValue(clipboardMarkdown, selection = TextRange(clipboardMarkdown.length)),
            marks = emptyList(),
            noteLinks = emptyList(),
            imported = imported,
            clipboardText = clipboardMarkdown,
        )

        assertNotNull(result)
        assertEquals("Title\nBold", result?.value?.text)
        assertHasMark(result?.styleMarks.orEmpty(), VaultInlineStyle.Heading, 0, 5)
        assertHasMark(result?.styleMarks.orEmpty(), VaultInlineStyle.Bold, 6, 10)
    }

    @Test
    fun smartDirectPasteLeavesPlainTextToOrdinaryPath() {
        val result = insertVaultRichTextDocumentForDirectPaste(
            oldValue = TextFieldValue("", selection = TextRange(0)),
            newValue = TextFieldValue("plain text", selection = TextRange(10)),
            marks = emptyList(),
            noteLinks = emptyList(),
            imported = RichImportResult(
                document = VaultRichTextDocument(text = "plain text", styleMarks = emptyList()),
                formattingPreserved = false,
            ),
            clipboardText = "plain text",
        )

        assertEquals(null, result)
    }

    @Test
    fun smartDirectPasteGuardSkipsSingleCharacterTyping() {
        assertFalse(
            shouldAttemptVaultSmartDirectPaste(
                oldValue = TextFieldValue("abc", selection = TextRange(3)),
                newValue = TextFieldValue("abcd", selection = TextRange(4)),
            ),
        )
        assertTrue(
            shouldAttemptVaultSmartDirectPaste(
                oldValue = TextFieldValue("abc", selection = TextRange(3)),
                newValue = TextFieldValue("abc pasted", selection = TextRange(10)),
            ),
        )
    }

    @Test
    fun insertedFormattingSurvivesStorageRoundTrip() {
        val result = insertVaultRichTextDocumentAtSelection(
            value = TextFieldValue("Before\nAfter", selection = TextRange(7)),
            marks = listOf(VaultStyleMark(0, 6, VaultInlineStyle.Heading2)),
            noteLinks = emptyList(),
            inserted = VaultRichTextDocument(
                text = "قال الله\n",
                styleMarks = listOf(VaultStyleMark(0, 8, VaultInlineStyle.ColorRed)),
            ),
        )

        val restored = parseVaultRichTextDocument(
            VaultRichTextDocument(
                text = result.value.text,
                styleMarks = result.styleMarks,
                noteLinks = result.noteLinks,
            ).toStorageJson(),
        )

        assertNotNull(restored)
        assertEquals(result.value.text, restored?.text)
        assertEquals(result.styleMarks, restored?.styleMarks)
        assertEquals(result.noteLinks, restored?.noteLinks)
    }

    @Test
    fun ordinaryPastePathDoesNotInterpretMarkdownAutomatically() {
        val oldValue = TextFieldValue("", selection = TextRange(0))
        val newValue = TextFieldValue("**literal**", selection = TextRange(11))

        val marks = handleVaultRichTextChange(
            oldValue = oldValue,
            newValue = newValue,
            marks = emptyList(),
            pendingStyles = emptySet(),
        )

        assertTrue(marks.isEmpty())
    }

    private fun assertHasMark(
        marks: List<VaultStyleMark>,
        style: VaultInlineStyle,
        start: Int,
        end: Int,
    ) {
        assertTrue(
            "Expected $style mark at $start..$end in $marks",
            marks.any { it.style == style && it.start == start && it.end == end },
        )
    }
}
