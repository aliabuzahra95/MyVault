package com.myvault.app.data.narration

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrationSeekMathTest {

    data class ChunkSeekResult(val chunkIndex: Int, val chunkOffsetMs: Long)

    private fun mapGlobalToChunk(globalMs: Long, chunkDurations: List<Long>): ChunkSeekResult {
        if (chunkDurations.isEmpty()) return ChunkSeekResult(0, 0L)
        val total = chunkDurations.sum()
        val clamped = globalMs.coerceIn(0L, total)

        var accumulated = 0L
        var targetChunk = 0
        for (index in chunkDurations.indices) {
            val dur = chunkDurations[index]
            val chunkEnd = accumulated + dur
            if (clamped < chunkEnd || index == chunkDurations.lastIndex) {
                targetChunk = index
                break
            }
            accumulated += dur
        }
        val chunkOffset = (clamped - accumulated).coerceAtLeast(0L)
        return ChunkSeekResult(targetChunk, chunkOffset)
    }

    private fun calculateRewind10s(currentPositionMs: Long): Long =
        (currentPositionMs - 10_000L).coerceAtLeast(0L)

    private fun calculateForward10s(currentPositionMs: Long, totalDurationMs: Long): Long =
        (currentPositionMs + 10_000L).coerceAtMost(totalDurationMs)

    @Test
    fun testRewindClampsToZeroWhenLessThanTenSeconds() {
        assertEquals(0L, calculateRewind10s(4_000L))
        assertEquals(0L, calculateRewind10s(9_999L))
        assertEquals(0L, calculateRewind10s(0L))
    }

    @Test
    fun testRewindSubtractsExactTenSeconds() {
        assertEquals(15_000L, calculateRewind10s(25_000L))
        assertEquals(50_000L, calculateRewind10s(60_000L))
    }

    @Test
    fun testForwardClampsToTotalDuration() {
        val totalDuration = 100_000L
        assertEquals(100_000L, calculateForward10s(95_000L, totalDuration))
        assertEquals(100_000L, calculateForward10s(100_000L, totalDuration))
        assertEquals(100_000L, calculateForward10s(92_000L, totalDuration))
    }

    @Test
    fun testForwardAddsExactTenSeconds() {
        val totalDuration = 100_000L
        assertEquals(30_000L, calculateForward10s(20_000L, totalDuration))
        assertEquals(65_000L, calculateForward10s(55_000L, totalDuration))
    }

    @Test
    fun testMultiChunkForwardAcrossChunkBoundary() {
        // Chunk 0: 30s, Chunk 1: 40s, Chunk 2: 50s. Total: 120s.
        val chunks = listOf(30_000L, 40_000L, 50_000L)

        // Position 25s (in Chunk 0). Forward 10s -> 35s (crosses into Chunk 1 at 5s)
        val targetGlobal = calculateForward10s(25_000L, 120_000L)
        assertEquals(35_000L, targetGlobal)

        val result = mapGlobalToChunk(targetGlobal, chunks)
        assertEquals(1, result.chunkIndex)
        assertEquals(5_000L, result.chunkOffsetMs)
    }

    @Test
    fun testMultiChunkBackwardAcrossChunkBoundary() {
        // Chunk 0: 30s, Chunk 1: 40s, Chunk 2: 50s. Total: 120s.
        val chunks = listOf(30_000L, 40_000L, 50_000L)

        // Position 35s (in Chunk 1 at 5s). Rewind 10s -> 25s (crosses back into Chunk 0 at 25s)
        val targetGlobal = calculateRewind10s(35_000L)
        assertEquals(25_000L, targetGlobal)

        val result = mapGlobalToChunk(targetGlobal, chunks)
        assertEquals(0, result.chunkIndex)
        assertEquals(25_000L, result.chunkOffsetMs)
    }

    @Test
    fun testSeekWithinSameChunk() {
        val chunks = listOf(30_000L, 40_000L, 50_000L)

        val result = mapGlobalToChunk(15_000L, chunks)
        assertEquals(0, result.chunkIndex)
        assertEquals(15_000L, result.chunkOffsetMs)

        val resultChunk2 = mapGlobalToChunk(80_000L, chunks) // 30 + 40 = 70. 80 is in chunk 2 at 10s.
        assertEquals(2, resultChunk2.chunkIndex)
        assertEquals(10_000L, resultChunk2.chunkOffsetMs)
    }
}
