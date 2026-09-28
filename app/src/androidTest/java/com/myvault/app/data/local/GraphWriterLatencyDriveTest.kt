package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit test-APK entry point; synthetic named Room database, host-owned disposable Drive IDs only. */
@RunWith(AndroidJUnit4::class)
class GraphWriterLatencyDriveTest {
    @Test fun profile() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("latencyPhase")
        assumeTrue("Requires the explicitly provisioned disposable broker", phase in listOf("before", "after"))
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = JSONObject(File(base.filesDir, "disposable-drive-config.json").readText())
            .put("lineageId", UUID.randomUUID().toString()).put("dbName", "graph-drive-disposable-${UUID.randomUUID()}.db")
        val rootStore = AuthenticatedGraphDriveTest.RemoteStore(config)
        val f = BackupGraphWriterRoomTest().Fixture(nameOverride = config.getString("dbName"), remoteStore = rootStore)
        val results = JSONArray()
        val phaseStart = System.nanoTime()
        var setupMs = 0.0
        val output = File(base.filesDir, "graph-latency-$phase.json")
        try {
            rootStore.marker("$phase-setup")
            f.note(); f.note("two"); f.note("three"); f.binary(4096)
            f.writer().createRoot(f.prepared())
            setupMs = (System.nanoTime()-phaseStart)/1e6
            repeat(3) { repetition ->
                for (scenario in listOf("zero", "one-note", "three", "binary", "metadata-only")) {
                    when (scenario) {
                        "one-note" -> repeat(7) { f.edit("$phase-$repetition-$it English الْعَرَبِيَّة") }
                        "three" -> { f.edit("$phase-$repetition-n"); f.edit("$phase-$repetition-two", "two"); f.edit("$phase-$repetition-three", "three") }
                        "binary" -> f.binary(if (repetition % 2 == 0) 8192 else 4096)
                        "metadata-only" -> f.db.attachmentDao().updateFileName("pdf", "$phase-$repetition-metadata.pdf")
                    }
                    val timing = BackupGraphTiming(true)
                    val store = AuthenticatedGraphDriveTest.RemoteStore(config, timing)
                    val writer = InternalBackupGraphWriter(f.db, f.journal,
                        PendingBackupCapture(f.db, f.journal, VaultPreferences(base, f.journal)), base.filesDir,
                        File(f.root, "staging"), store, timing = timing)
                    val name = "$phase-$repetition-$scenario"
                    val begin = store.marker(name); f.queries.clear()
                    val started = System.nanoTime(); val result = writer.publish()
                    val coreMs = (System.nanoTime() - started) / 1e6
                    val end = store.marker("$name-crosscheck")
                    val queries = f.querySnapshot()
                    val crossStart = System.nanoTime()
                    val graph = BackupGraph.discover(store.commits(), store.context.driveAccountId, store.context.lineageId)
                    assertEquals(GraphStatus.SINGLE_TIP, graph.status)
                    assertEquals(listOf(result.commitId), graph.tips)
                    assertTrue(f.pending().isEmpty())
                    if (scenario == "zero") {
                        assertEquals(GraphWriterMetrics(0,0,0,0,0,0), result.metrics)
                        assertTrue(queries.none { it.startsWith("SELECT * FROM `notes`") })
                        assertTrue(BackupRecordKeys.keys.none { group -> queries.any { it.startsWith("SELECT * FROM `${backupRecordTable(group)}`") } })
                    } else {
                        assertEquals(if (scenario == "three") 3 else 1, result.metrics.payloadRows)
                        assertEquals(1, result.metrics.deltasCreated); assertEquals(1, result.metrics.commitsCreated)
                        assertEquals(if (scenario == "binary") 1 else 0, result.metrics.binariesCreated)
                        val commit = graph.commits.getValue(result.commitId!!)
                        val delta = checkNotNull(store.bytes(commit.delta!!.objectRef.cloudFileId))
                        IncrementalBackupFormat.parseChanges(JSONObject(delta.toString(Charsets.UTF_8)))
                    }
                    val crossMs = (System.nanoTime() - crossStart) / 1e6
                    results.put(JSONObject().put("name", name).put("coreMs",coreMs).put("crosscheckMs",crossMs)
                        .put("harnessMs",coreMs+crossMs).put("requestStart",begin.getInt("requests")).put("requestEnd",end.getInt("requests"))
                        .put("metrics",JSONObject().put("pending",result.metrics.pendingRows).put("payload",result.metrics.payloadRows)
                            .put("binaries",result.metrics.binariesCreated).put("deltas",result.metrics.deltasCreated).put("commits",result.metrics.commitsCreated))
                        .put("payloadQueries",queries.count { it.startsWith("SELECT * FROM `notes`") || it.startsWith("SELECT * FROM `attachments`") })
                        .put("spans",JSONArray(timing.snapshot().map { JSONObject().put("name",it.name).put("startNanos",it.startNanos).put("endNanos",it.endNanos) })))
                    output.writeText(JSONObject().put("phase",phase).put("setupMs",setupMs).put("phaseMs",(System.nanoTime()-phaseStart)/1e6).put("config",JSONObject().put("accountId",store.context.driveAccountId).put("lineageId",store.context.lineageId))
                        .put("results",results).toString())
                    println("LATENCY $name: core=${coreMs.toLong()}ms crosscheck=${crossMs.toLong()}ms")
                }
            }
            if(phase=="after") {
                val startMarker=rootStore.marker("after-cold-cache")
                rootStore.call("clearCache")
                val began=System.nanoTime();assertTrue(f.writer().publish().alreadyBackedUp)
                val elapsed=(System.nanoTime()-began)/1e6
                val endMarker=rootStore.marker("after-cold-cache-crosscheck")
                val saved=JSONObject(output.readText()).put("coldCache",JSONObject().put("coreMs",elapsed)
                    .put("requestStart",startMarker.getInt("requests")).put("requestEnd",endMarker.getInt("requests")))
                output.writeText(saved.toString())
                println("LATENCY after cold-cache zero: ${elapsed}ms")
            }
        } finally { f.close() }
    }
}
