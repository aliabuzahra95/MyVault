package com.myvault.app.widget.note

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteWidgetStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: NoteWidgetStateStore

    @Before
    fun setUp() {
        store = NoteWidgetStateStore(context)
        store.delete(71)
        store.delete(72)
    }

    @Test
    fun settingsPersistAndRemainIndependentAcrossWidgets() {
        store.setNote(71, "study-note")
        store.setTextSizeLevel(71, 4)
        store.setShowTitle(71, false)
        store.setShowContext(71, false)
        store.setNote(72, "course-note")

        val first = NoteWidgetStateStore(context).state(71)
        val second = NoteWidgetStateStore(context).state(72)
        assertEquals("study-note", first.noteId)
        assertEquals(4, first.textSizeLevel)
        assertFalse(first.showTitle)
        assertFalse(first.showContext)
        assertEquals("course-note", second.noteId)
        assertEquals(DEFAULT_NOTE_TEXT_SIZE_LEVEL, second.textSizeLevel)
        assertTrue(second.showTitle)
        assertTrue(second.showContext)
    }

    @Test
    fun deletingWidgetClearsOnlyThatWidgetState() {
        store.setNote(71, "first")
        store.setNote(72, "second")
        store.delete(71)

        assertNull(store.state(71).noteId)
        assertEquals("second", store.state(72).noteId)
    }
}
