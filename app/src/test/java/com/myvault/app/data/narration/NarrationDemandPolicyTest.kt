package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrationDemandPolicyTest {
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
