package com.myvault.app.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRichTextDeletionTest {
    @Test
    fun deletingLargeSelectedSegmentClampsStyleMarksAndNoteLinks() {
        val oldText = (1..80).joinToString("\n") { "Line $it has some editable text" }
        val newText = oldText.replaceRange(20, oldText.length - 20, "")
        val oldValue = TextFieldValue(oldText, selection = TextRange(20, oldText.length - 20))
        val newValue = TextFieldValue(newText, selection = TextRange(20))
        val marks = listOf(
            VaultStyleMark(0, oldText.length, VaultInlineStyle.Bold),
            VaultStyleMark(15, oldText.length - 10, VaultInlineStyle.Italic),
            VaultStyleMark(oldText.length + 5, oldText.length + 30, VaultInlineStyle.Underline),
        )
        val links = listOf(
            VaultNoteLink(5, 18, "kept"),
            VaultNoteLink(30, oldText.length - 30, "deleted"),
            VaultNoteLink(oldText.length + 10, oldText.length + 40, "invalid"),
        )

        val updatedMarks = handleVaultRichTextChange(oldValue, newValue, marks, emptySet())
        val updatedLinks = handleVaultNoteLinkChange(oldValue, newValue, links)

        assertValidMarks(updatedMarks, newText.length)
        assertValidLinks(updatedLinks, newText.length)
        assertTrue(updatedLinks.none { it.noteId == "deleted" || it.noteId == "invalid" })
    }

    @Test
    fun repeatedBackspaceStyleUpdatesNeverProduceInvalidRanges() {
        var value = TextFieldValue(
            text = (1..40).joinToString("\n") { "Paragraph $it with styled words and a linked note" },
            selection = TextRange((1..40).joinToString("\n") { "Paragraph $it with styled words and a linked note" }.length),
        )
        var marks = listOf(
            VaultStyleMark(0, value.text.length, VaultInlineStyle.Bold),
            VaultStyleMark(20, 120, VaultInlineStyle.ColorBlue),
            VaultStyleMark(value.text.length - 50, value.text.length, VaultInlineStyle.Underline),
        )
        var links = listOf(
            VaultNoteLink(10, 70, "note-a"),
            VaultNoteLink(value.text.length - 90, value.text.length - 15, "note-b"),
        )

        while (value.text.isNotEmpty()) {
            val deleteCount = minOf(13, value.text.length)
            val newText = value.text.dropLast(deleteCount)
            val newValue = TextFieldValue(newText, selection = TextRange(newText.length + 50))
            marks = handleVaultRichTextChange(value, newValue, marks, emptySet())
            links = handleVaultNoteLinkChange(value, newValue, links)
            value = sanitizeVaultTextFieldValue(newValue)

            assertValidMarks(marks, value.text.length)
            assertValidLinks(links, value.text.length)
            assertEquals(value.text.length, value.selection.start)
        }
    }

    @Test
    fun sanitizingMalformedTextFieldValueClampsSelectionAndComposition() {
        val value = TextFieldValue(
            text = "short",
            selection = TextRange(99, 10),
            composition = TextRange(20, 30),
        )

        val sanitized = sanitizeVaultTextFieldValue(value)

        assertEquals(TextRange(5, 5), sanitized.selection)
        assertEquals(null, sanitized.composition)
    }

    @Test
    fun currentLineAtStartOfTextBeginningWithNewlineDoesNotCrash() {
        val value = TextFieldValue(
            text = "\nFirst line after blank\nSecond line",
            selection = TextRange(0),
        )

        assertEquals("", value.currentLine())
    }

    @Test
    fun pressingEnterAtVeryBeginningDoesNotCrashListContinuation() {
        val oldValue = TextFieldValue(
            text = "First line\nSecond line",
            selection = TextRange(0),
        )
        val newValue = TextFieldValue(
            text = "\nFirst line\nSecond line",
            selection = TextRange(1),
        )

        val continued = continueListOnNewline(oldValue, newValue)

        assertEquals("\nFirst line\nSecond line", continued.text)
        assertEquals(TextRange(1), continued.selection)
    }

    @Test
    fun togglingListAtBeginningOfTextStartingWithNewlineDoesNotCrash() {
        val value = TextFieldValue(
            text = "\nFirst line\nSecond line",
            selection = TextRange(0),
        )

        val transformed = applyBulletListTransform(value)

        assertValidTextField(transformed.value)
    }

    @Test
    fun deletingLargeBeginningSelectionClampsStylesAndLinks() {
        val oldText = (1..120).joinToString("\n") { "Opening line $it with substantial styled content" }
        val deleteEnd = oldText.length / 2
        val newText = oldText.removeRange(0, deleteEnd)
        val oldValue = TextFieldValue(oldText, selection = TextRange(0, deleteEnd))
        val newValue = TextFieldValue(newText, selection = TextRange(0))
        val marks = listOf(
            VaultStyleMark(0, oldText.length, VaultInlineStyle.Heading2),
            VaultStyleMark(0, deleteEnd + 50, VaultInlineStyle.Bold),
            VaultStyleMark(deleteEnd - 10, oldText.length, VaultInlineStyle.ColorBlue),
        )
        val links = listOf(
            VaultNoteLink(0, deleteEnd - 20, "deleted-link"),
            VaultNoteLink(deleteEnd + 5, oldText.length - 5, "kept-link"),
        )

        val updatedMarks = handleVaultRichTextChange(oldValue, newValue, marks, emptySet())
        val updatedLinks = handleVaultNoteLinkChange(oldValue, newValue, links)

        assertValidMarks(updatedMarks, newText.length)
        assertValidLinks(updatedLinks, newText.length)
        assertTrue(updatedLinks.none { it.noteId == "deleted-link" })
    }

    @Test
    fun currentLineWithStaleSelectionAfterDeletionDoesNotCrash() {
        val value = TextFieldValue(
            text = "\n" + (1..100).joinToString("\n") { "Line $it" },
            selection = TextRange(9_999),
        )

        assertEquals("Line 100", value.currentLine())
    }

    @Test
    fun typingAtEndOfStyledTextContinuesThatStyle() {
        val oldValue = TextFieldValue("Bold", selection = TextRange(4))
        val newValue = TextFieldValue("Bold text", selection = TextRange(9))
        val marks = listOf(VaultStyleMark(0, 4, VaultInlineStyle.Bold))

        val updatedMarks = handleVaultRichTextChange(oldValue, newValue, marks, emptySet())

        assertTrue(updatedMarks.any { it.style == VaultInlineStyle.Bold && it.start == 0 && it.end == 9 })
    }

    @Test
    fun pendingStyleCanBeToggledOffWithoutRemovingExistingStyledText() {
        val value = TextFieldValue("Bold", selection = TextRange(4))
        val marks = listOf(VaultStyleMark(0, 4, VaultInlineStyle.Bold))

        val update = applyVaultStyleFromToolbar(
            value = value,
            marks = marks,
            pendingStyles = setOf(VaultInlineStyle.Bold),
            style = VaultInlineStyle.Bold,
        )

        assertEquals(marks, update.marks)
        assertTrue(VaultInlineStyle.Bold !in update.pendingStyles)
    }

    private fun assertValidMarks(marks: List<VaultStyleMark>, textLength: Int) {
        marks.forEach { mark ->
            assertTrue("Invalid mark: $mark for length $textLength", mark.start >= 0)
            assertTrue("Invalid mark: $mark for length $textLength", mark.end <= textLength)
            assertTrue("Invalid mark: $mark for length $textLength", mark.start < mark.end)
        }
    }

    private fun assertValidLinks(links: List<VaultNoteLink>, textLength: Int) {
        links.forEach { link ->
            assertTrue("Invalid link: $link for length $textLength", link.start >= 0)
            assertTrue("Invalid link: $link for length $textLength", link.end <= textLength)
            assertTrue("Invalid link: $link for length $textLength", link.start < link.end)
            assertTrue("Invalid link: $link", link.noteId.isNotBlank())
        }
    }

    private fun assertValidTextField(value: TextFieldValue) {
        assertTrue(value.selection.start in 0..value.text.length)
        assertTrue(value.selection.end in 0..value.text.length)
        value.composition?.let { composition ->
            assertTrue(composition.start in 0..value.text.length)
            assertTrue(composition.end in 0..value.text.length)
            assertTrue(composition.start < composition.end)
        }
    }
}
