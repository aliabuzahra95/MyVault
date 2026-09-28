package com.myvault.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupGraphTimingTest {
    @Test fun disabledDiagnosticsDoNotCollectOrAlterResults() = runBlocking {
        val timing=BackupGraphTiming()
        assertEquals(42,timing.measure("writer.total") { timing.local("payload.serialization") { 42 } })
        assertTrue(timing.snapshot().isEmpty());assertNull(timing.current)
    }
    @Test fun nestedMonotonicSpansRetainActualInclusiveIntervals() = runBlocking {
        val timing=BackupGraphTiming(true)
        timing.measure("writer.total") {
            assertEquals("writer.total",timing.current)
            timing.local("payload.serialization") { assertEquals("payload.serialization",timing.current) }
            assertEquals("writer.total",timing.current)
        }
        val spans=timing.snapshot();assertEquals(2,spans.size)
        val outer=spans.single { it.name=="writer.total" };val inner=spans.single { it.name=="payload.serialization" }
        assertTrue(outer.startNanos<=inner.startNanos && inner.endNanos<=outer.endNanos)
        assertTrue(spans.all { it.endNanos>=it.startNanos });assertNull(timing.current)
    }
    @Test fun failuresStillCloseSpanWithoutChangingFailureSemantics() = runBlocking {
        val timing=BackupGraphTiming(true)
        try { timing.measure("verification.COMMIT") { error("fixture failure") };fail("Expected failure") }
        catch(e:IllegalStateException) { assertEquals("fixture failure",e.message) }
        assertEquals(1,timing.snapshot().size);assertNull(timing.current)
    }
}
