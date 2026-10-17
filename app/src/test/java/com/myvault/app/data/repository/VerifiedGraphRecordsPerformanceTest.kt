package com.myvault.app.data.repository

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream

class VerifiedGraphRecordsPerformanceTest {
    private class Store : DisposableGraphObjectStore {
        override val context = GraphWriterContext("test@example.com", "drive", "lineage")
        var bytes: ByteArray? = null
        var reads = 0
        override suspend fun read(objectId: String): InputStream? { reads++; return bytes?.inputStream() }
        override suspend fun commits() = emptyList<GraphObject>()
        override suspend fun reserveId(): String = error("Read-only fixture")
        override suspend fun create(objectId: String, role: String, file: File) = error("Read-only fixture")
    }
    private fun delta(store: Store): BackupGraphCommit {
        val checkpoint = GraphCheckpoint("full-${"a".repeat(64)}", GraphObjectRef("checkpoint", "a".repeat(64), 1))
        val bytes = IncrementalBackupFormat.createDelta(checkpoint.checkpointId, checkpoint.checkpointId, "delta",
            listOf(BackupRecordChange("notes.json", listOf("n"), JSONObject().put("id", "n")))).toString().toByteArray()
        store.bytes = bytes
        return BackupGraphCommit("drive", "lineage", "commit", "delta", listOf(BackupGraphCapability), emptyList(),
            checkpoint, GraphDelta("delta", checkpoint.checkpointId, GraphObjectRef("delta-object", IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())))
    }
    @Test fun sharedParsingStillReverifiesBytesOncePerPreflightReader() = runBlocking {
        val store = Store(); val commit = delta(store); val cache = VerifiedGraphRecordCache(); val timing = BackupGraphTiming(true)
        val first = VerifiedGraphRecords(store, timing, cache, refreshCached = true)
        first.read(commit); first.read(commit)
        assertEquals(1, store.reads)
        val next = VerifiedGraphRecords(store, timing, cache, refreshCached = true)
        next.read(commit); next.read(commit)
        assertEquals(2, store.reads)
        assertEquals(1L, timing.counts()["metadata.commit_parses"])
        assertEquals(3L, timing.counts()["metadata.parsed_cache_hits"])
    }
    @Test fun missingOrCorruptedRequiredObjectCannotBeHiddenBySharedParsedRecords() = runBlocking {
        val store = Store(); val commit = delta(store); val cache = VerifiedGraphRecordCache()
        VerifiedGraphRecords(store, cache = cache, refreshCached = true).read(commit)
        for (bad in listOf(null, "corrupt".toByteArray())) {
            store.bytes = bad
            try {
                VerifiedGraphRecords(store, cache = cache, refreshCached = true).read(commit)
                fail("Cached parse cannot mask unavailable/corrupt required metadata")
            } catch (_: IllegalStateException) { }
        }
    }
}
