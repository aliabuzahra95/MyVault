package com.myvault.app.data.narration

object NarrationTimeline {
    fun wordCount(text: String): Int = Regex("\\S+").findAll(text).count()

    fun plansForTexts(texts: List<String>): List<NarrationChunkPlan> = rebuild(
        texts.mapIndexed { index, text ->
            NarrationChunkPlan(index, text, 0L, (wordCount(text) * 400L).coerceAtLeast(1L))
        },
        emptyMap(),
    )

    /**
     * Rebuilds the authoritative timeline.
     * For each chunk in canonical order:
     * start = cumulative duration of preceding resolved chunks
     * duration = actual duration if known, otherwise current estimate
     */
    fun rebuild(
        plans: List<NarrationChunkPlan>,
        knownDurations: Map<Int, Long>
    ): List<NarrationChunkPlan> {
        if (plans.isEmpty()) return emptyList()
        val result = ArrayList<NarrationChunkPlan>(plans.size)
        var currentStart = 0L
        for (i in plans.indices) {
            val original = plans[i]
            val actualDur = knownDurations[original.index]?.takeIf { it > 0L }
                ?: original.actualDurationMs?.takeIf { it > 0L }
            val resolvedDur = actualDur ?: original.estimatedDurationMs
            result.add(
                original.copy(
                    actualStartMs = currentStart,
                    actualDurationMs = actualDur
                )
            )
            currentStart += resolvedDur
        }
        return result
    }

    /**
     * Calculates the contiguous playable audio ahead of the current position.
     * Stops at the first missing canonical chunk.
     */
    fun contiguousPlayableAheadMs(
        activeChunkIndices: List<Int>,
        activeDurationsMs: List<Long>,
        playlistIndex: Int,
        chunkPositionMs: Long
    ): Long {
        if (playlistIndex < 0 || playlistIndex >= activeChunkIndices.size) return 0L

        var contiguousMs = ((activeDurationsMs.getOrNull(playlistIndex) ?: 0L) - chunkPositionMs).coerceAtLeast(0L)

        for (i in playlistIndex + 1 until activeChunkIndices.size) {
            if (activeChunkIndices[i] == activeChunkIndices[i - 1] + 1) {
                val duration = activeDurationsMs.getOrNull(i)?.takeIf { it > 0L } ?: break
                contiguousMs += duration
            } else {
                break // gap found
            }
        }
        return contiguousMs
    }

    fun safeLocalOffset(offsetMs: Long, durationMs: Long): Long =
        offsetMs.coerceIn(0L, (durationMs - 250L).coerceAtLeast(0L))

    internal fun seekTarget(positionMs: Long, plans: List<NarrationChunkPlan>): NarrationSeekTarget {
        val index = NarrationDemandPolicy.chunkForPosition(positionMs, plans)
        val plan = plans.firstOrNull { it.index == index }
        val start = plan?.actualStartMs ?: plan?.estimatedStartMs ?: 0L
        return NarrationSeekTarget(index, (positionMs - start).coerceAtLeast(0L))
    }

    fun insertionIndex(indices: List<Int>, chunkIndex: Int): Int? {
        if (chunkIndex in indices) return null
        return indices.indexOfFirst { it > chunkIndex }.takeIf { it >= 0 } ?: indices.size
    }
}

data class NarrationSeekTarget(val chunkIndex: Int, val offsetMs: Long)

internal data class NarrationResolvedSeek(val playlistIndex: Int, val offsetMs: Long)

// Pin the requested canonical chunk before new durations shift downstream timeline starts.
internal class NarrationPendingSeek {
    var positionMs: Long? = null
        private set
    var target: NarrationSeekTarget? = null
        private set

    fun request(positionMs: Long, plans: List<NarrationChunkPlan>) {
        requestTarget(positionMs, NarrationTimeline.seekTarget(positionMs, plans))
    }

    fun requestTarget(positionMs: Long, target: NarrationSeekTarget) {
        this.positionMs = positionMs
        this.target = target
    }

    fun requestStreaming(positionMs: Long) {
        this.positionMs = positionMs
        target = null
    }

    fun resolve(indices: List<Int>, durations: List<Long>): NarrationResolvedSeek? {
        val wanted = target ?: return null
        val playlistIndex = indices.indexOf(wanted.chunkIndex)
        val duration = durations.getOrNull(playlistIndex)?.takeIf { it > 0L } ?: return null
        val result = NarrationResolvedSeek(playlistIndex, NarrationTimeline.safeLocalOffset(wanted.offsetMs, duration))
        clear()
        return result
    }

    fun clear() {
        positionMs = null
        target = null
    }
}

internal class NarrationBufferScheduler {
    private var filling = true

    fun reset() { filling = true }

    fun nextChunk(indices: List<Int>, durations: List<Long>, playlistIndex: Int,
        positionMs: Long, totalChunks: Int): Int? {
        val current = indices.getOrNull(playlistIndex) ?: return null
        val ahead = NarrationTimeline.contiguousPlayableAheadMs(indices, durations, playlistIndex, positionMs)
        if (ahead <= RefillMs) filling = true
        if (ahead >= TargetMs) filling = false
        if (!filling) return null
        return ((current + 1) until totalChunks).firstOrNull { it !in indices }
    }

    companion object {
        const val RefillMs = 150_000L
        const val TargetMs = 240_000L
    }
}
