package com.myvault.app.data.narration

import org.junit.Assert.*
import org.junit.Test

class NarrationDemandRecoveryTest {
    private fun plans() = NarrationTimeline.rebuild(
        listOf(
            NarrationChunkPlan(0, "First", 0L, 120_000L),
            NarrationChunkPlan(1, "Second", 120_000L, 120_000L),
            NarrationChunkPlan(2, "Third", 240_000L, 120_000L),
        ), mapOf(0 to 40_000L),
    )

    @Test fun resumeAt103SecondsTargetsSecondMediaNotFastStartEof() {
        assertEquals(NarrationSeekTarget(1, 63_000L), NarrationTimeline.seekTarget(103_000L, plans()))
    }

    @Test fun physicalSeekStaysBelowEofIncludingShortFiles() {
        assertEquals(39_750L, NarrationTimeline.safeLocalOffset(103_000L, 40_000L))
        assertEquals(0L, NarrationTimeline.safeLocalOffset(900L, 100L))
    }

    @Test fun pendingSeekResolvesOnlyWhenRequestedCanonicalAudioArrives() {
        val pending = NarrationPendingSeek()
        pending.request(103_000L, plans())
        assertNull(pending.resolve(listOf(0, 2), listOf(40_000L, 80_000L)))
        assertEquals(103_000L, pending.positionMs)
        assertEquals(NarrationResolvedSeek(1, 63_000L), pending.resolve(listOf(0, 1, 2), listOf(40_000L, 83_000L, 80_000L)))
        assertNull(pending.positionMs)
        assertNull(pending.target)
    }

    @Test fun aSecondSeekReplacesFirstAndOldArrivalCannotResolveIt() {
        val pending = NarrationPendingSeek()
        pending.request(103_000L, plans())
        pending.request(200_000L, plans())
        assertNull(pending.resolve(listOf(0, 1), listOf(40_000L, 83_000L)))
        assertEquals(NarrationResolvedSeek(1, 40_000L), pending.resolve(listOf(0, 2), listOf(40_000L, 80_000L)))
    }

    @Test fun learnedDurationsDoNotRemapAnOutstandingSeek() {
        val pending = NarrationPendingSeek()
        pending.request(103_000L, plans())
        val shifted = NarrationTimeline.rebuild(plans(), mapOf(1 to 20_000L))
        assertEquals(2, NarrationDemandPolicy.chunkForPosition(103_000L, shifted))
        assertEquals(NarrationResolvedSeek(1, 19_750L), pending.resolve(listOf(0, 1), listOf(40_000L, 20_000L)))
    }

    @Test fun outOfOrderArrivalsInsertCanonicallyAndDuplicatesAreRejected() {
        var indices = listOf(0, 1, 2, 5)
        val insertion = NarrationTimeline.insertionIndex(indices, 3)!!
        indices = indices.take(insertion) + 3 + indices.drop(insertion)
        assertEquals(listOf(0, 1, 2, 3, 5), indices)
        assertNull(NarrationTimeline.insertionIndex(indices, 3))
        assertEquals(0, NarrationTimeline.insertionIndex(listOf(5), 0))
    }

    @Test fun duplicateRequestsArePreventedUntilReleased() {
        val tracker = NarrationChunkRequestTracker()
        assertTrue(tracker.tryStart(1))
        assertFalse(tracker.tryStart(1))
        tracker.complete(1)
        assertTrue(tracker.tryStart(1))
        tracker.clear()
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test fun initialPlayableAudioDoesNotWaitForFullBuffer() {
        val scheduler = NarrationBufferScheduler()
        assertEquals(1, scheduler.nextChunk(listOf(0), listOf(40_000L), 0, 0L, 10))
        assertEquals(40_000L, NarrationTimeline.contiguousPlayableAheadMs(listOf(0), listOf(40_000L), 0, 0L))
    }

    @Test fun refillContinuesAfterCrossing150SecondsUntil240Seconds() {
        val scheduler = NarrationBufferScheduler()
        assertEquals(1, scheduler.nextChunk(listOf(0), listOf(100_000L), 0, 0L, 6))
        assertEquals(2, scheduler.nextChunk(listOf(0, 1), listOf(100_000L, 80_000L), 0, 0L, 6))
        assertNull(scheduler.nextChunk(listOf(0, 1, 2), listOf(100_000L, 80_000L, 80_000L), 0, 0L, 6))
    }

    @Test fun refillRestartsAt150SecondsNotAboveIt() {
        val scheduler = NarrationBufferScheduler()
        val indices = listOf(0, 1, 2)
        val durations = listOf(100_000L, 100_000L, 100_000L)
        assertNull(scheduler.nextChunk(indices, durations, 0, 0L, 8))
        assertNull(scheduler.nextChunk(indices, durations, 1, 40_000L, 8))
        assertEquals(3, scheduler.nextChunk(indices, durations, 1, 50_000L, 8))
    }

    @Test fun seeksImmediatelyRecalculateDemandAndFillFirstGap() {
        val scheduler = NarrationBufferScheduler()
        val indices = listOf(0, 1, 2, 4)
        val durations = listOf(100_000L, 100_000L, 100_000L, 100_000L)
        assertNull(scheduler.nextChunk(indices, durations, 0, 0L, 10))
        assertEquals(3, scheduler.nextChunk(indices, durations, 1, 60_000L, 10))
        scheduler.reset()
        assertEquals(5, scheduler.nextChunk(indices, durations, 3, 5_000L, 10))
    }

    @Test fun actualDurationsSurviveIncrementalRebuildWithoutResupplyingThem() {
        val first = NarrationTimeline.rebuild(plans(), mapOf(0 to 83_000L))
        val next = NarrationTimeline.rebuild(first, mapOf(1 to 60_000L))
        assertEquals(83_000L, next[0].actualDurationMs)
        assertEquals(143_000L, next[2].actualStartMs)
    }

    @Test fun cueSelectionFollowsChunkLocalSeekAcrossEnglishAndArabic() {
        val cues = listOf(
            NarrationCue(0, 0L, 5_000L, 0, 5, "First."),
            NarrationCue(0, 5_000L, 10_000L, 6, 10, "العلم نور"),
            NarrationCue(1, 0L, 8_000L, 0, 5, "Next."),
        )
        assertEquals("العلم نور", NarrationDemandPolicy.activeCue(cues, 0, 6_000L)?.displayText)
        assertEquals("Next.", NarrationDemandPolicy.activeCue(cues, 1, 500L)?.displayText)
        assertEquals("First.", NarrationDemandPolicy.activeCue(cues, 0, 1_000L)?.displayText)
    }
}
