package com.myvault.app.data.local

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

/** Real production store + Room writer/restore, disposable fake Drive only. No network calls. */
@RunWith(AndroidJUnit4::class)
class BackupGraphProductionPerformanceTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Api : GraphDriveApi {
        val context = GraphWriterContext("performance@example.com", "performance-drive", "lineage-${UUID.randomUUID()}")
        val layout = GraphDriveLayout("root-${UUID.randomUUID()}", context.lineageId,
            BackupGraphDirectories.associateWith { "$it-${UUID.randomUUID()}" })
        val files = linkedMapOf<String, GraphDriveFile>()
        val data = linkedMapOf<String, ByteArray>()
        val counts = linkedMapOf<String, Long>()
        var ids = 0
        val pageSize = 32
        init {
            files[layout.rootId] = GraphDriveFile(layout.rootId, BackupGraphNamespace, GraphFolderMime, emptyList())
            layout.folders.forEach { (name, id) -> files[id] = GraphDriveFile(id, name, GraphFolderMime, listOf(layout.rootId)) }
        }
        private fun count(name: String, value: Long = 1) { counts[name] = counts.getOrDefault(name, 0) + value }
        private fun GraphDriveFile.json() = JSONObject().put("id", id).put("name", name).put("mimeType", mimeType)
            .put("parents", JSONArray(parents)).put("trashed", trashed).also {
                size?.let { size -> it.put("size", size.toString()) }; sha256?.let { sha -> it.put("sha256Checksum", sha) }
            }
        private suspend fun list(rows: List<GraphDriveFile>) = completeGraphDriveListing { token ->
            val offset = token?.toInt() ?: 0
            val page = rows.drop(offset).take(pageSize)
            val next = (offset + page.size).takeIf { it < rows.size }?.toString()
            val body = JSONObject().put("files", JSONArray(page.map { it.json() })).put("incompleteSearch", false)
            next?.let { body.put("nextPageToken", it) }
            count("list"); count("listed_rows", page.size.toLong())
            count("list_response_bytes", body.toString().toByteArray().size.toLong())
            GraphDriveListingPage(page, next)
        }
        override suspend fun assertAccount(account: String, driveAccountId: String) {
            check(account == context.accountScope && driveAccountId == context.driveAccountId); count("account_checks")
        }
        override suspend fun roots(name: String): List<GraphDriveFile> {
            return list(files.values.filter { it.name == name && it.mimeType == GraphFolderMime && !it.trashed })
        }
        override suspend fun children(parent: String): List<GraphDriveFile> {
            return list(files.values.filter { parent in it.parents && !it.trashed })
        }
        override suspend fun metadata(id: String): GraphDriveFile? {
            count("metadata")
            return files[id].also { count("metadata_response_bytes", (it?.json()?.toString() ?: "{}").toByteArray().size.toLong()) }
        }
        override suspend fun download(id: String): ByteArrayInputStream {
            val bytes = data.getValue(id); count("media"); count("download_bytes", bytes.size.toLong())
            val folder = files.getValue(id).parents.single()
            count(if (folder == layout.folders.getValue("binaries")) "binary_media" else "metadata_media")
            return ByteArrayInputStream(bytes)
        }
        override suspend fun reserveIds(count: Int): List<String> { count("reserve"); return List(count) { "object-${++ids}" } }
        override suspend fun createFolder(id: String, name: String, parent: String?) = error("Pre-enrolled disposable namespace")
        override suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File) {
            count("upload")
            if (id !in files) {
                val bytes = source.readBytes(); count("upload_bytes", bytes.size.toLong()); data[id] = bytes
                files[id] = GraphDriveFile(id, name, mimeType, listOf(parent), size = bytes.size.toLong(), sha256 = IncrementalBackupFormat.sha256(bytes))
            }
        }
        // Retain the exact baseline test source: old adapter ignores the not-yet-integrated cache.
        fun store(cache: VerifiedGraphByteCache): AccountBoundGraphStore {
            val constructor = AccountBoundGraphStore::class.java.declaredConstructors.firstOrNull {
                it.parameterTypes.size == 4 && it.parameterTypes.last() == VerifiedGraphByteCache::class.java
            }
            return if (constructor == null) AccountBoundGraphStore(context, this, layout)
            else constructor.newInstance(context, this, layout, cache) as AccountBoundGraphStore
        }
        suspend fun open(cache: VerifiedGraphByteCache, discover: Boolean): AccountBoundGraphStore {
            // Existing-enrollment acquisition: account validation + fresh root lookup.
            assertAccount(context.accountScope, context.driveAccountId)
            count("about") // Synthetic stand-in for the production account endpoint, not Google auth.
            check(roots(BackupGraphNamespace).single().id == layout.rootId)
            if (discover) check(discoverGraphLayout(this, context.accountScope, context.driveAccountId) == layout)
            return store(cache)
        }
    }
    private fun writer(f: BackupGraphWriterRoomTest.Fixture, store: DisposableGraphObjectStore,
        timing: BackupGraphTiming = BackupGraphTiming()) = InternalBackupGraphWriter(f.db, f.journal,
        PendingBackupCapture(f.db, f.journal, VaultPreferences(base, f.journal)), base.filesDir,
        File(f.root, "staging"), store, timing = timing)
    private suspend fun restore(f: BackupGraphWriterRoomTest.Fixture, store: DisposableGraphObjectStore,
        timing: BackupGraphTiming = BackupGraphTiming()): GraphRestoreResult {
        val settings = BackupGraphRestoreRoomTest.FileSettings(File(f.root, "performance-settings.json"),
            VaultPreferences(base, f.journal).userPreferences.first().toBackupJson())
        return InternalBackupGraphRestore(f.db, f.journal, base.filesDir, File(f.root, "restored"), store, settings, timing = timing).restore()
    }

    @Test fun comparableProductionPathMatrix() = runBlocking {
        val label = InstrumentationRegistry.getArguments().getString("performanceLabel", "unspecified")
        val results = JSONArray()
        for ((notes, depth) in listOf(10 to 2, 100 to 24, 300 to 128)) for (warm in listOf(false, true)) {
            val api = Api(); val cache = processVerifiedGraphBytes
            val setupStart = System.nanoTime()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store(cache)).use { a ->
                a.note(); repeat(notes - 1) { a.note("note-$it", "Disposable content $it") }
                a.binary(if (notes == 10) 8192 else 262144)
                writer(a, api.store(cache)).createRoot(a.prepared())
                repeat(depth) { a.edit("History $it"); writer(a, api.store(cache)).publish() }
                BackupGraphWriterRoomTest().Fixture(remoteStore = api.store(cache)).use { b ->
                    restore(b, api.store(cache))
                    val fixtureSetupMs = (System.nanoTime() - setupStart) / 1_000_000.0
                    suspend fun measure(name: String, fixture: BackupGraphWriterRoomTest.Fixture,
                        action: suspend (AccountBoundGraphStore, BackupGraphTiming) -> String) {
                        cache.clear()
                        if (warm) {
                            val store = api.store(cache)
                            VerifiedGraphRecords(store).fold(BackupGraph.discover(store.commits(), api.context.driveAccountId, api.context.lineageId))
                        }
                        api.counts.clear()
                        val oldHits = cache.hits; val oldMisses = cache.misses; val oldHash = cache.hashedBytes
                        val queries = fixture.querySnapshot().size
                        val timing = BackupGraphTiming(true)
                        val start = System.nanoTime()
                        val restoreDiscovery = name.startsWith("restore")
                        val store = timing.measure("operation.setup") { api.open(cache, restoreDiscovery) }
                        val status = action(store, timing)
                        val elapsed = (System.nanoTime() - start) / 1_000_000.0
                        val requests = listOf("about", "list", "metadata", "media", "reserve", "upload").sumOf { api.counts[it] ?: 0 }
                        val row = JSONObject().put("label", label).put("case", name).put("notes", notes)
                            .put("initial_delta_depth", depth).put("warm_process_cache", warm).put("status", status)
                            .put("fixture_setup_excluded_ms", fixtureSetupMs).put("operation_including_setup_ms", elapsed)
                            .put("transport", "in-memory fake; NO real Drive latency").put("requests", requests)
                            .put("restore_without_enrollment_discovery", restoreDiscovery)
                            .put("fake_list_page_size", api.pageSize)
                            .put("transport_counts", JSONObject(api.counts.toMap())).put("sql_queries", fixture.querySnapshot().size - queries)
                            .put("verified_byte_cache_hits", cache.hits - oldHits).put("verified_byte_cache_misses", cache.misses - oldMisses)
                            .put("cache_hashed_bytes", cache.hashedBytes - oldHash).put("engine_counts", JSONObject(timing.counts()))
                            .put("timing_spans", JSONArray(timing.snapshot().map {
                                JSONObject().put("stage", it.name).put("ms", (it.endNanos - it.startNanos) / 1_000_000.0)
                            }))
                        results.put(row); Log.i("GraphPerformance", "PERF_RESULT $row")
                    }
                    suspend fun backup(name: String) = measure(name, a) { store, timing ->
                        val result = writer(a, store, timing).publish(); check(!result.forkDetected)
                        if (result.alreadyBackedUp) "ALREADY_BACKED_UP" else "PUBLISHED"
                    }
                    suspend fun read(name: String) = measure(name, b) { store, timing ->
                        restore(b, store, timing).also { check(it.status in setOf(GraphRestoreStatus.APPLIED, GraphRestoreStatus.ALREADY_CURRENT)) }.status.name
                    }
                    backup("backup_no_change")
                    a.edit("One changed note"); backup("backup_one_note")
                    a.note("new-note", "New local note"); backup("backup_new_item")
                    a.db.noteDao().deleteByIds(listOf("note-0")); backup("backup_delete")
                    a.binary(if (notes == 10) 16384 else 524288); backup("backup_changed_binary")
                    read("restore_multiple_and_deletion")
                    read("restore_already_current")
                    a.edit("Single descendant"); writer(a, api.store(cache)).publish(); read("restore_single_delta")
                    repeat(3) { a.edit("Missing delta $it"); writer(a, api.store(cache)).publish() }
                    read("restore_multiple_deltas")
                    a.db.noteDao().deleteByIds(listOf("note-1")); writer(a, api.store(cache)).publish(); read("restore_deletion")
                    b.note("phone-b-only", "Must survive"); b.edit("Stale local edit")
                    a.edit("Authoritative remote"); writer(a, api.store(cache)).publish(); read("restore_pending_local")
                    assertNotNull(b.db.noteDao().getById("phone-b-only"))
                    assertEquals("Authoritative remote", b.db.noteDao().getById("n")!!.bodyPlainText)
                    assertNull(b.db.noteDao().getById("note-0")); assertNull(b.db.noteDao().getById("note-1"))
                }
            }
        }
        val output = File(base.filesDir, "graph-performance-$label.json")
        output.writeText(JSONObject().put("results", results).toString(2))
        assertEquals(66, results.length())
    }
}
