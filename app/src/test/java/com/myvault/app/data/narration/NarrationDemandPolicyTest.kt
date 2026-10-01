package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationDemandPolicyTest {
    @Test
    fun initialPlaybackStartsAtFirstChunkOnly() {
        assertEquals(0, NarrationDemandPolicy.chunkForPosition(0L, totalChunks = 23))
    }

    @Test
    fun prefetchStartsNearEndOfTwoMinuteChunk() {
        assertFalse(NarrationDemandPolicy.shouldPrefetch(89_999L, 120_000L))
        assertTrue(NarrationDemandPolicy.shouldPrefetch(95_000L, 120_000L))
    }

    @Test
    fun farSeekTargetsOnlyRequiredChunk() {
        assertEquals(5, NarrationDemandPolicy.chunkForPosition(10L * 60L * 1_000L, totalChunks = 30))
    }

    @Test
    fun bilingualSentenceCuesFollowPlaybackAndSeek() {
        val cues = listOf(
            NarrationCue(2, 0L, 8_000L, 0, 18, "English sentence.", "English sentence."),
            NarrationCue(2, 8_001L, 15_000L, 19, 36, "جملة عربية واضحة.", "جملة عربية واضحة."),
        )

        assertEquals("English sentence.", NarrationDemandPolicy.activeCue(cues, 2, 4_000L)?.displayText)
        assertEquals("جملة عربية واضحة.", NarrationDemandPolicy.activeCue(cues, 2, 11_000L)?.displayText)
        assertNull(NarrationDemandPolicy.activeCue(cues, 1, 11_000L))
    }

    @Test
    fun duplicateChunkRequestsArePreventedUntilCompletion() {
        val tracker = NarrationChunkRequestTracker()

        assertTrue(tracker.tryStart(3))
        assertFalse(tracker.tryStart(3))
        tracker.complete(3)
        assertTrue(tracker.tryStart(3))
    }
}
