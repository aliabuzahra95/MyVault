package com.myvault.app.ui.screens

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.myvault.app.ui.theme.LightVaultColors
import org.junit.Assert.*
import org.junit.Test

class EditorViewportTest {
    @Test fun keyboardCentersCaretInsideResizedViewportWithoutChangingOffset() {
        assertEquals(24_320, editorCaretTarget(24_500f, 24_540f, 24_000, 400, 50_000, true))
        assertEquals(24_000, editorCaretTarget(24_100f, 24_120f, 24_000, 400, 50_000, false))
        assertEquals(0, editorCaretTarget(0f, 20f, 0, 400, 50_000, true))
    }

    @Test fun edgesAccelerateBothDirectionsAndMiddleDoesNotScroll() {
        assertEquals(-600f, editorSelectionVelocity(100f, 100f, 600f, 60f, 600f))
        assertEquals(-300f, editorSelectionVelocity(130f, 100f, 600f, 60f, 600f))
        assertEquals(0f, editorSelectionVelocity(300f, 100f, 600f, 60f, 600f))
        assertEquals(600f, editorSelectionVelocity(650f, 100f, 600f, 60f, 600f))
    }

    @Test fun selectionExtendsOnlyTheDraggedEdgeIncludingReversedMixedTextSelection() {
        assertEquals(TextRange(20, 1000), editorExtendedSelection(TextRange(20, 40), false, 1000))
        assertEquals(TextRange(0, 1000), editorExtendedSelection(TextRange(20, 1000), true, 0))
        assertEquals(TextRange(1000, 0), editorExtendedSelection(TextRange(1000, 20), false, 0))
    }

    @Test fun cursorChangesReuseRenderedRichTextAndBidiMappingRoundTrips() {
        val text = (1..200).joinToString("\n") { "English العربية https://example.com نهاية" }
        val transform = VaultRichTextVisualTransformation(emptyList(), colors = LightVaultColors)
        val first = transform.filter(AnnotatedString(text))
        assertSame(first, transform.filter(AnnotatedString(text)))
        for (offset in 0..text.length) {
            assertEquals(offset, first.offsetMapping.transformedToOriginal(first.offsetMapping.originalToTransformed(offset)))
        }
        assertNotEquals(first.text.text, text)
        assertNotSame(first, transform.filter(AnnotatedString(text + "!")))
    }

    @Test fun bidiIsolationDoesNotExplodeDocumentWideFormattingIntoThousandsOfSpans() {
        val text = (1..5000).joinToString(" ") { "English العربية" }
        val display = buildVaultDisplayText(text, listOf(VaultStyleMark(0, text.length, VaultInlineStyle.Bold)),
            listOf(VaultNoteLink(0, 7, "linked-note")), LightVaultColors)
        assertEquals(2, display.text.spanStyles.size) // bold + link colour
        assertEquals("linked-note", display.text.getStringAnnotations("noteLink", 0, display.text.length).single().item)
        assertEquals(text, display.text.text.replace("\u2067", "").replace("\u2069", ""))
    }

    @Test fun allocationFreeEditBoundsMatchOldAlgorithm() {
        for ((old, new) in listOf("abcabc" to "abc", "" to "عربي", "abc" to "abc", "abcdef" to "abXYef",
            "العربية English العربية" to "العربية العربية", "a\uD83D\uDE00z" to "a\uD83D\uDE01z")) {
            val prefix = old.commonPrefixWith(new).length
            val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
            assertEquals(prefix to suffix, vaultChangeBounds(old, new))
        }
    }

    @Test fun mixedWordCountAndSelectionOnlyHistoryAreStable() {
        assertEquals(5, editorWordCount(" English\nالعربية\tword  نص\nlast "))
        val value = TextFieldValue("English العربية")
        assertSame(value, sanitizeVaultTextFieldValue(value))
        val snapshot = EditorHistorySnapshot(TextFieldValue("Title"), value, emptyList(), emptyList(), emptySet())
        val history = EditorHistory(listOf(snapshot))
        assertSame(history, history.record(snapshot.copy(body = value.copy(selection = TextRange(2, 8)))))
    }

    @Test fun hugeDeletionUndoAndStoragePreserveAllFormattingAndLinks() {
        val text = (1..50_000).joinToString(" ") { if (it % 2 == 0) "العربية" else "English" }
        val marks = listOf(VaultStyleMark(0, 20, VaultInlineStyle.Heading), VaultStyleMark(10, text.length, VaultInlineStyle.Bold))
        val links = listOf(VaultNoteLink(text.length - 15, text.length - 1, "linked-note"))
        val body = TextFieldValue(text, TextRange(text.length / 4, text.length * 3 / 4))
        val snapshot = EditorHistorySnapshot(TextFieldValue("Large"), body, marks, links, emptySet())
        val changed = body.copy(text = text.removeRange(body.selection.start, body.selection.end), selection = TextRange(body.selection.start))
        val next = snapshot.copy(body = changed,
            styleMarks = handleVaultRichTextChange(body, changed, marks, emptySet()),
            noteLinks = handleVaultNoteLinkChange(body, changed, links))
        val restored = EditorHistory(listOf(snapshot)).record(next).undo(next)!!.snapshot
        assertEquals(snapshot, restored)
        val document = VaultRichTextDocument(restored.body.text, restored.styleMarks, restored.noteLinks)
        val reopened = parseVaultRichTextDocument(document.toStorageJson())!!
        assertEquals(document.text, reopened.text)
        assertEquals(document.styleMarks.toSet(), reopened.styleMarks.toSet())
        assertEquals(document.noteLinks, reopened.noteLinks)
    }
}
