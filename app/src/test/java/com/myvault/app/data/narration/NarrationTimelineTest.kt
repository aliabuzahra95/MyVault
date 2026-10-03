package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrationTimelineTest {

    @Test
    fun `word count is accurate for long strings`() {
        val text = (1..100).joinToString(" ") { "word" }
        val words = NarrationTimeline.wordCount(text)
        assertEquals(100, words)
    }

    @Test
    fun `word count handles newlines and spaces`() {
        val text = " word \n word2 \t word3 \n\n word4 "
        val words = NarrationTimeline.wordCount(text)
        assertEquals(4, words)
    }

    @Test
    fun `timeline is reconstructed consistently`() {
        // Initial setup with estimates
        val plans = mutableListOf(
            NarrationChunkPlan(0, "c1", 0L, 1000L),
            NarrationChunkPlan(1, "c2", 1000L, 2000L),
            NarrationChunkPlan(2, "c3", 3000L, 1000L)
        )

        // Chunk 0 arrives, actual duration is much longer
        plans[0] = plans[0].copy(actualDurationMs = 5000L)

        var currentStart = 0L
        for (i in plans.indices) {
            val pt = plans[i]
            plans[i] = pt.copy(actualStartMs = currentStart)
            currentStart += pt.actualDurationMs ?: pt.estimatedDurationMs
        }

        assertEquals(0L, plans[0].actualStartMs)
        assertEquals(5000L, plans[1].actualStartMs) // shifted by chunk 0
        assertEquals(7000L, plans[2].actualStartMs) // shifted by chunk 0 + 1's estimate

        // Chunk 1 arrives, actual duration is shorter
        plans[1] = plans[1].copy(actualDurationMs = 500L)

        currentStart = 0L
        for (i in plans.indices) {
            val pt = plans[i]
            plans[i] = pt.copy(actualStartMs = currentStart)
            currentStart += pt.actualDurationMs ?: pt.estimatedDurationMs
        }

        assertEquals(0L, plans[0].actualStartMs)
        assertEquals(5000L, plans[1].actualStartMs)
        assertEquals(5500L, plans[2].actualStartMs) // shifted back down
    }
}
