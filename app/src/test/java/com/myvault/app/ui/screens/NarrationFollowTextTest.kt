package com.myvault.app.ui.screens

import org.junit.Assert.*
import org.junit.Test

class NarrationFollowTextTest {
    @Test fun mapsNormalizedBilingualTextToOriginalOffsets() {
        val source = "Heading\nEnglish   paragraph.\n\nهذا نص عربي.\nEnglish again."
        val range = narrationTextRange(source, "paragraph. هذا نص عربي.")!!
        assertEquals("paragraph.\n\nهذا نص عربي.", source.substring(range))
    }

    @Test fun repeatedPassageUsesCurrentSourceHint() {
        val source = "Repeated sentence. Other text. Repeated sentence."
        assertEquals(31, narrationTextRange(source, "Repeated sentence.", 35)!!.first)
    }

    @Test fun centersAbovePlayerRatherThanPhysicalScreen() {
        assertEquals(750, narrationCenteredScroll(1000f, 1100f, 600f, 2000))
        assertEquals(0, narrationCenteredScroll(0f, 30f, 600f, 2000))
        assertEquals(2000, narrationCenteredScroll(4000f, 4100f, 600f, 2000))
    }

    @Test fun onlyManualReverseScrollRevealsToolbar() {
        assertFalse(narrationToolbarVisible(true, -10f, true))
        assertTrue(narrationToolbarVisible(false, 1f, true))
        assertFalse(narrationToolbarVisible(false, 40f, false))
        assertTrue(narrationToolbarVisible(true, -40f, false))
    }

    @Test fun unrelatedOrEmptyPassageDoesNotHighlightStaleText() {
        assertNull(narrationTextRange("New document", "Old sentence"))
        assertNull(narrationTextRange("New document", ""))
    }
}
