package com.myvault.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CoursesPresentationTest {
    @Test
    fun courseNoteCountUsesTruthfulSingularAndPluralLabels() {
        assertEquals("0 notes", courseNoteCountLabel(0))
        assertEquals("1 note", courseNoteCountLabel(1))
        assertEquals("12 notes", courseNoteCountLabel(12))
    }
}
