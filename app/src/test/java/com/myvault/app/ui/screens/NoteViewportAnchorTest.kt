package com.myvault.app.ui.screens

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class NoteViewportAnchorTest {
    private val text = "English paragraph\n\nفقرة عربية للاختبار\n\nLast paragraph"
    private fun anchor(offset: Int = 20, fraction: Float = .25f) =
        NoteViewportAnchor("note", text.length, text.hashCode(), offset, fraction)

    @Test fun arabicPositionKeepsItsCharacterIdentity() {
        val value = anchor(text.indexOf("عربية"))
        assertTrue(value.matches("note", text))
        assertEquals("عربية", text.substring(value.offset, value.offset + 5))
    }

    @Test fun staleNoteOrChangedDocumentCannotRestoreAnAnchor() {
        assertFalse(anchor().matches("other", text))
        assertFalse(anchor().matches("note", text.replace("English", "Changed")))
        assertFalse(anchor().matches("note", text + " more"))
    }

    @Test fun invalidOffsetsAndFractionsAreRejected() {
        assertFalse(anchor(-1).matches("note", text))
        assertFalse(anchor(text.length + 1).matches("note", text))
        assertFalse(anchor(fraction = Float.NaN).matches("note", text))
        assertFalse(anchor(fraction = -1f).matches("note", text))
        assertTrue(anchor(text.length, 1f).matches("note", text))
    }

    @Test fun lineAnchorAdaptsToDifferentEditorLineHeight() {
        assertEquals(1005, noteAnchorScroll(1000f, 1020f, .25f))
        assertEquals(2010, noteAnchorScroll(2000f, 2040f, .25f))
        assertEquals(0, noteAnchorScroll(0f, 20f, 0f))
        assertEquals(24005, noteAnchorScroll(24000f, 24020f, .25f))
    }

    @Test fun navigationStateRoundTripsWithoutNoteContent() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(anchor()) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(anchor(), restored)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(text))
    }

    @Test fun visibleCaretDoesNotMoveAndCoveredCaretMovesOnlyRequiredDistance() {
        assertEquals(0, noteCaretScrollDelta(24500f, 24530f, 24000, 1000))
        assertEquals(130, noteCaretScrollDelta(24500f, 24530f, 24000, 400))
        assertEquals(-20, noteCaretScrollDelta(23980f, 24010f, 24000, 1000))
        assertEquals(0, noteCaretScrollDelta(24500f, 24530f, 24000, 0))
    }
}
