package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarrationDemandPolicyTest {
    @Test fun cueHoldsThroughPauseAndAdvancesAtNextStart() {
        val first = NarrationCue(0, 0, 1000, 0, 5, "First")
        val second = NarrationCue(0, 2000, 3000, 6, 12, "Second")
        val nextChunk = NarrationCue(1, 0, 1000, 0, 4, "Next")
        val cues = listOf(second, nextChunk, first)
        assertEquals(first, NarrationDemandPolicy.activeCue(cues, 0, 1500))
        assertEquals(second, NarrationDemandPolicy.activeCue(cues, 0, 2000))
        assertEquals(second, NarrationDemandPolicy.activeCue(cues, 0, 3500))
        assertEquals(nextChunk, NarrationDemandPolicy.activeCue(cues, 1, 0))
        assertEquals(first, NarrationDemandPolicy.activeCue(cues, 0, 100))
        assertNull(NarrationDemandPolicy.activeCue(cues, 2, 100))
    }
    @Test
    fun testChunkForPosition() {
        val plans = listOf(
            NarrationChunkPlan(0, "chunk 1", 0L, 1000L, 0L, 1000L),
            NarrationChunkPlan(1, "chunk 2", 1000L, 2000L, 1000L, 2000L)
        )
        assertEquals(0, NarrationDemandPolicy.chunkForPosition(500L, plans))
        assertEquals(1, NarrationDemandPolicy.chunkForPosition(1500L, plans))
        assertEquals(1, NarrationDemandPolicy.chunkForPosition(5000L, plans)) // fallback to last
    }
}
