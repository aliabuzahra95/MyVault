package com.myvault.app.ui.screens

import org.junit.Assert.*
import org.junit.Test
import com.myvault.app.data.narration.NarrationPlaybackStatus

class NarrationFollowTextTest {
    @Test fun followingResumesAfterGestureButNeverForPauseOrStop() {
        for (status in listOf(NarrationPlaybackStatus.Playing, NarrationPlaybackStatus.Generating,
            NarrationPlaybackStatus.Preparing)) {
            assertTrue(narrationShouldFollow(status, false))
            assertFalse(narrationShouldFollow(status, true))
            assertTrue(narrationShouldFollow(status, false))
        }
        for (status in listOf(NarrationPlaybackStatus.Paused, NarrationPlaybackStatus.Stopped,
            NarrationPlaybackStatus.Idle, NarrationPlaybackStatus.Error)) {
            assertFalse(narrationShouldFollow(status, false))
        }
    }

    @Test fun canonicalContextResolvesDuplicateSentencesDespiteWhitespaceDrift() {
        val source = "Repeated sentence.\n\nOther text.\nRepeated   sentence.\nFinal text."
        val context = "Other text. Repeated sentence. Final text."
        val offset = context.indexOf("Repeated")
        val range = narrationTextRange(source, "Repeated sentence.", 0, context, offset)!!
        assertEquals(source.lastIndexOf("Repeated"), range.first)
        assertEquals("Repeated   sentence.", source.substring(range))
    }
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
