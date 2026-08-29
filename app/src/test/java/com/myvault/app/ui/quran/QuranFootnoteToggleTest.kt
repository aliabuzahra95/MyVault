package com.myvault.app.ui.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranFootnoteToggleTest {

    @Test
    fun `tap opens footnote and same marker closes it`() {
        assertEquals("footnote-1", nextExpandedFootnoteId(null, "footnote-1"))
        assertNull(nextExpandedFootnoteId("footnote-1", "footnote-1"))
    }

    @Test
    fun `different marker replaces the expanded footnote`() {
        assertEquals("footnote-2", nextExpandedFootnoteId("footnote-1", "footnote-2"))
    }
}
