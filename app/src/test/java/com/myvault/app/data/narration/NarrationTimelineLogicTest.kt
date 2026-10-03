package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrationTimelineLogicTest {

    @Test
    fun `rebuild preserves actual durations and recalculates downstream starts`() {
        val plans = listOf(
            NarrationChunkPlan(0, "c1", 0L, 1000L),
            NarrationChunkPlan(1, "c2", 1000L, 2000L),
            NarrationChunkPlan(2, "c3", 3000L, 1000L)
        )

        // Chunk 0 gets an actual duration
        val pass1 = NarrationTimeline.rebuild(plans, mapOf(0 to 5000L))
        assertEquals(0L, pass1[0].actualStartMs)
        assertEquals(5000L, pass1[1].actualStartMs) // shifted by chunk 0 actual
        assertEquals(7000L, pass1[2].actualStartMs) // shifted by chunk 0 actual + 1 estimate

        // Chunk 1 gets actual duration, Chunk 0 actual duration MUST survive (Bug 1)
        val pass2 = NarrationTimeline.rebuild(pass1, mapOf(0 to 5000L, 1 to 500L))
        assertEquals(0L, pass2[0].actualStartMs)
        assertEquals(5000L, pass2[1].actualStartMs)
        assertEquals(5500L, pass2[2].actualStartMs)

        // Original plans shouldn't affect known durations
        val pass3 = NarrationTimeline.rebuild(plans, mapOf(0 to 5000L, 1 to 500L))
        assertEquals(pass2, pass3)
    }

    @Test
    fun `contiguous buffer stops at first gap`() {
        // Indices: 0, 1, 2, 4 (gap at 3)
        val indices = listOf(0, 1, 2, 4)
        val durations = listOf(1000L, 1000L, 1000L, 1000L)

        // Playing chunk index 0 at position 500
        val playable = NarrationTimeline.contiguousPlayableAheadMs(indices, durations, 0, 500L)

        // Chunk 0 remainder: 500
        // Chunk 1: 1000
        // Chunk 2: 1000
        // Stops before Chunk 4 because 4 != 2 + 1
        assertEquals(2500L, playable)
    }
}
