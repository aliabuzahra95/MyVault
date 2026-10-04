package com.myvault.app.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    private fun snapshot(text: String, title: String = "Note") = EditorHistorySnapshot(
        TextFieldValue(title), TextFieldValue(text, TextRange(text.length)),
        emptyList(), emptyList(), emptySet(),
    )

    @Test
    fun `typing immediately enables undo and redo restores text`() {
        val initial = snapshot("Original")
        val edited = snapshot("Original updated")
        val history = EditorHistory(listOf(initial))
        assertTrue(history.canUndo(edited))
        val undone = history.undo(edited)!!
        assertEquals(initial, undone.snapshot)
        assertTrue(undone.history.canRedo(initial))
        assertEquals(edited, undone.history.redo(initial)!!.snapshot)
    }

    @Test
    fun `recording an undo restoration does not erase redo`() {
        val initial = snapshot("A")
        val edited = snapshot("AB")
        val undone = EditorHistory(listOf(initial)).record(edited).undo(edited)!!
        val afterRecomposition = undone.history.record(undone.snapshot)
        assertEquals(edited, afterRecomposition.redo(initial)!!.snapshot)
    }

    @Test
    fun `rapid repeated undo and redo preserve ordering`() {
        val a = snapshot("A")
        val b = snapshot("AB")
        val c = snapshot("ABC")
        val first = EditorHistory(listOf(a)).record(b).record(c).undo(c)!!
        val second = first.history.undo(first.snapshot)!!
        assertEquals(a, second.snapshot)
        val redoOne = second.history.redo(a)!!
        assertEquals(b, redoOne.snapshot)
        assertEquals(c, redoOne.history.redo(b)!!.snapshot)
    }

    @Test
    fun `new edits invalidate redo even before history recording`() {
        val a = snapshot("A")
        val b = snapshot("AB")
        val undone = EditorHistory(listOf(a)).undo(b)!!
        val newEdit = snapshot("AC")
        assertFalse(undone.history.canRedo(newEdit))
        assertNull(undone.history.redo(newEdit))
        assertTrue(undone.history.record(newEdit).future.isEmpty())
    }

    @Test
    fun `cursor movement and IME composition do not create history entries`() {
        val initial = snapshot("Arabic العربية English")
        val moved = initial.copy(body = initial.body.copy(selection = TextRange(2), composition = TextRange(0, 2)))
        val history = EditorHistory(listOf(initial)).record(moved)
        assertEquals(1, history.past.size)
        assertFalse(history.canUndo(moved))
    }

    @Test
    fun `title changes are undoable`() {
        val a = snapshot("Body", "Old title")
        val b = snapshot("Body", "New title")
        assertEquals(a, EditorHistory(listOf(a)).undo(b)!!.snapshot)
    }

    @Test
    fun `rich text styles links and pending styles restore together`() {
        val initial = snapshot("العربية English")
        val formatted = initial.copy(
            styleMarks = listOf(VaultStyleMark(0, 7, VaultInlineStyle.Bold)),
            noteLinks = listOf(VaultNoteLink(8, 15, "linked-note")),
            pendingInlineStyles = setOf(VaultInlineStyle.Italic),
        )
        val undone = EditorHistory(listOf(initial)).undo(formatted)!!
        assertEquals(initial, undone.snapshot)
        assertEquals(formatted, undone.history.redo(initial)!!.snapshot)
    }

    @Test
    fun `history is bounded and cannot undo beyond its baseline`() {
        var history = EditorHistory(listOf(snapshot("0")))
        for (i in 1..60) history = history.record(snapshot(i.toString()))
        assertEquals(48, history.past.size)
        val baseline = snapshot("A")
        assertNull(EditorHistory(listOf(baseline)).undo(baseline))
        assertNull(EditorHistory(listOf(baseline)).redo(baseline))
    }
}
