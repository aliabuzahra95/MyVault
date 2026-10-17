package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Actual production store/Room orchestration with a disposable, paginated fake Drive. */
@RunWith(AndroidJUnit4::class)
class BackupGraphInventoryRoomTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Api : GraphDriveApi {
        val context = GraphWriterContext("inventory@example.com", "inventory-drive", "lineage-${UUID.randomUUID()}")
        val layout = GraphDriveLayout("root", context.lineageId, BackupGraphDirectories.associateWith { "folder-$it" })
        val files = linkedMapOf<String, GraphDriveFile>()
        val bytes = mutableMapOf<String, ByteArray>()
        val cache = VerifiedGraphByteCache()
        var next = 0; var uploads = 0; var checkpointPages = 0; var metadataGets = 0
        var failCommitContinuation = false
        var commitInventories = 0
        var onCommitInventory: (Int) -> Unit = {}
        init {
            files[layout.rootId] = GraphDriveFile(layout.rootId, BackupGraphNamespace, GraphFolderMime, emptyList())
            layout.folders.forEach { (name, id) -> files[id] = GraphDriveFile(id, name, GraphFolderMime, listOf(layout.rootId)) }
        }
        fun store() = AccountBoundGraphStore(context, this, layout, cache)
        override suspend fun assertAccount(account: String, driveAccountId: String) {
            check(account == context.accountScope && driveAccountId == context.driveAccountId)
        }
        override suspend fun roots(name: String) = files.values.filter { it.name == name && !it.trashed }
        override suspend fun children(parent: String): List<GraphDriveFile> {
            if (parent == layout.folders.getValue("commits")) onCommitInventory(++commitInventories)
            val rows = files.values.filter { parent in it.parents && !it.trashed }
            return completeGraphDriveListing { token ->
                if (parent == layout.folders.getValue("checkpoints")) {
                    checkpointPages++
                }
                if (parent == layout.folders.getValue("commits") && token != null && failCommitContinuation)
                    error("Disposable commit page failure")
                val offset = token?.toInt() ?: 0; val page = rows.drop(offset).take(3)
                GraphDriveListingPage(page, (offset + page.size).takeIf { it < rows.size }?.toString())
            }
        }
        override suspend fun metadata(id: String) = files[id].also { metadataGets++ }
        override suspend fun download(id: String) = bytes.getValue(id).inputStream().also { check(id in files) }
        override suspend fun reserveIds(count: Int) = List(count) { "object-${++next}" }
        override suspend fun createFolder(id: String, name: String, parent: String?) = error("Disposable namespace already enrolled")
        override suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File) {
            if (id in files) return
            val data = source.readBytes(); uploads++; bytes[id] = data
            files[id] = GraphDriveFile(id, name, mimeType, listOf(parent), size = data.size.toLong(), sha256 = IncrementalBackupFormat.sha256(data))
        }
    }
    private fun writer(f: BackupGraphWriterRoomTest.Fixture, api: Api,
        store: DisposableGraphObjectStore = api.store(),
        onProgress: suspend (GraphBackupProgress) -> Unit = {}) = InternalBackupGraphWriter(f.db, f.journal,
        PendingBackupCapture(f.db, f.journal, VaultPreferences(base, f.journal)), base.filesDir,
        File(f.root, "staging"), store, onProgress = onProgress)
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected safe refusal") } catch (_: IllegalStateException) { }
    }

    @Test fun freshResumeReadsRejectMissingOrChangedPriorMetadataDespiteWarmCaches() = runBlocking {
        for (missing in listOf(true, false)) {
            val api = Api()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { f ->
                f.root(); val binding = f.binding(); val original = api.files.getValue(binding.checkpointFileId)
                f.edit("Pending edit"); val uploads = api.uploads; var checks = 0
                fails { writer(f, api) {
                    if (it.stage == GraphBackupStage.CHECKING && ++checks == 2) {
                        if (missing) api.files.remove(original.id) else api.files[original.id] = original.copy(sha256 = "a".repeat(64))
                    }
                }.publish() }
                assertEquals(2, checks); assertEquals(binding, f.binding()); assertEquals(uploads, api.uploads)
                assertFalse(f.pending().isEmpty())
                val intent = f.db.backupGraphDao().unfinished(f.ctx.accountScope).single()
                api.files[original.id] = original; f.reopen()
                assertFalse(writer(f, api).resume(intent.operationId).forkDetected)
                assertTrue(f.pending().isEmpty()); assertTrue(api.metadataGets > 3)
            }
        }
    }

    @Test fun partialCommitPaginationCannotPublishOrAcknowledgeAndRetryCanRecover() = runBlocking {
        val api = Api()
        BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { f ->
            f.root()
            repeat(3) { f.edit("Earlier history $it"); writer(f, api).publish() }
            val binding = f.binding(); f.edit("Pending after incomplete listing")
            val uploads = api.uploads; api.failCommitContinuation = true
            fails { writer(f, api).publish() }
            assertEquals(binding, f.binding()); assertEquals(uploads, api.uploads)
            assertFalse(f.pending().isEmpty()); assertTrue(f.db.backupGraphDao().unfinished(f.ctx.accountScope).isEmpty())
            api.failCommitContinuation = false
            writer(f, api).publish(); assertTrue(f.pending().isEmpty())
        }
    }

    @Test fun restoredOutstandingIntentRechecksGraphAndSeesDescendantCreatedDuringRecovery() = runBlocking {
        val api = Api(); val restores = BackupGraphRestoreRoomTest()
        BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { a ->
            a.root()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { b ->
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b, api.store()).restore().status)
                a.edit("First delta"); writer(a, api).publish()
                fails { restores.restorer(b, api.store()) { if (it == "INTENT_PERSISTED") error("Disposable process interruption") }.restore() }
                var published = false
                val result = restores.restorer(b, api.store()) {
                    if (it == "COMMIT_APPLIED" && !published) {
                        published = true
                        a.edit("Newer delta published during recovery"); a.note("new-descendant-note")
                        writer(a, api).publish()
                    }
                }.restore()
                assertTrue(published); assertEquals(GraphRestoreStatus.APPLIED, result.status)
                assertEquals("Newer delta published during recovery", b.db.noteDao().getById("n")!!.bodyPlainText)
                assertNotNull(b.db.noteDao().getById("new-descendant-note"))
                assertEquals(a.binding().commitId, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)!!.commitId)
            }
        }
    }

    @Test fun cleanSingleDeltaUsesOneFreshGetAndMissingDeltaCannotAdvance() = runBlocking {
        for (missing in listOf(false, true)) {
            val api = Api(); val restores = BackupGraphRestoreRoomTest()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { a ->
                a.root()
                BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { b ->
                    restores.restorer(b, api.store()).restore()
                    val before = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                    a.edit("One verified descendant"); writer(a, api).publish()
                    val tip = BackupGraph.discover(api.store().commits(), api.context.driveAccountId, api.context.lineageId)
                        .commits.getValue(a.binding().commitId)
                    if (missing) api.files.remove(tip.delta!!.objectRef.cloudFileId)
                    api.checkpointPages = 0; api.metadataGets = 0
                    if (missing) {
                        fails { restores.restorer(b, api.store()).restore() }
                        assertEquals(before, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                        assertEquals("Original العربية", b.db.noteDao().getById("n")!!.bodyPlainText)
                    } else {
                        assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b, api.store()).restore().status)
                        assertEquals("One verified descendant", b.db.noteDao().getById("n")!!.bodyPlainText)
                    }
                    assertEquals(0, api.checkpointPages); assertEquals(1, api.metadataGets)
                }
            }
        }
    }

    @Test fun singleDeletionReadsFullHistoryAndMissingHistoryBlocksApplication() = runBlocking {
        val api = Api(); val restores = BackupGraphRestoreRoomTest()
        BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { a ->
            a.root()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { b ->
                restores.restorer(b, api.store()).restore()
                val before = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                val checkpoint = a.binding().checkpointFileId
                a.db.noteDao().deleteByIds(listOf("n")); writer(a, api).publish()
                api.files.remove(checkpoint); api.metadataGets = 0
                fails { restores.restorer(b, api.store()).restore() }
                assertTrue(api.metadataGets > 0)
                assertEquals(before, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("n"))
            }
        }
    }

    private fun mutate(api: Api, id: String, kind: String) {
        val original = api.files.getValue(id)
        when (kind) {
            "delete" -> api.files.remove(id)
            "change" -> api.files[id] = original.copy(sha256 = "a".repeat(64))
            "move" -> api.files[id] = original.copy(parents = listOf("outside-enrolled-namespace"))
            else -> error("Unknown disposable mutation")
        }
    }

    private fun saveFreshness(name: String, rows: JSONArray) {
        val label = InstrumentationRegistry.getArguments().getString("freshnessLabel", "unspecified")
        File(base.filesDir, "graph-freshness-$name-$label.json").writeText(JSONObject().put("results", rows).toString(2))
    }

    @Test fun writerMetadataMustBeFreshAfterResumeGraphInventory() = runBlocking {
        val results = JSONArray()
        for (kind in listOf("delete", "change", "move")) for (warm in listOf(false, true)) for (control in listOf(false, true)) {
            val api = Api()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { f ->
                f.root(); val original = f.binding(); val uploads = api.uploads
                f.edit("Unpublished disposable edit")
                if (!warm) api.cache.clear()
                val baseStore = api.store()
                val selected = if (!control) baseStore else object : DisposableGraphObjectStore by baseStore {
                    override suspend fun metadataBoundary(): DisposableGraphObjectStore = this
                    override suspend fun readMetadata(ref: GraphObjectRef, folder: String) = baseStore.readMetadata(ref, folder)
                }
                var fired = false
                api.commitInventories = 0
                api.onCommitInventory = { count ->
                    if (count == 4) { fired = true; mutate(api, original.checkpointFileId, kind) }
                }
                val error = try { writer(f, api, selected).publish(); null } catch (e: IllegalStateException) { e }
                val safe = fired && error != null && original == f.binding() && api.uploads == uploads && f.pending().isNotEmpty()
                results.put(JSONObject().put("mutation", kind).put("initial_cache_warm", warm).put("fresh_control", control)
                    .put("mutation_fired", fired).put("commit_inventories", api.commitInventories).put("rejected", error != null)
                    .put("binding_unchanged", original == f.binding()).put("new_uploads", api.uploads - uploads)
                    .put("pending_preserved", f.pending().isNotEmpty()).put("safe", safe))
            }
        }
        saveFreshness("writer", results)
        assertTrue("Every fourth-inventory mutation must block publication/acknowledgement: $results",
            (0 until results.length()).all { results.getJSONObject(it).getBoolean("safe") })
    }

    @Test fun restoreLaterRequiredMetadataMustBeFreshAfterManifestRead() = runBlocking {
        val results = JSONArray(); val restores = BackupGraphRestoreRoomTest()
        for (kind in listOf("delete", "change", "move")) for (control in listOf(false, true)) {
            val api = Api()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { a ->
                a.root(); val manifestId = a.binding().checkpointFileId
                val manifest = JSONObject(String(api.bytes.getValue(manifestId), Charsets.UTF_8))
                val entries = manifest.getJSONArray("entries")
                val laterId = (0 until entries.length()).map { entries.getJSONObject(it) }
                    .single { it.optString("fileName") == "notes.json" }.getString("cloudFileId")
                BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { b ->
                    restores.restorer(b, api.store()).restore()
                    val original = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                    a.edit("First later delta"); writer(a, api).publish()
                    a.edit("Second later delta"); writer(a, api).publish()
                    val baseStore = api.store(); var fired = false
                    fun wrap(delegate: DisposableGraphObjectStore): DisposableGraphObjectStore = object : DisposableGraphObjectStore by delegate {
                        override suspend fun metadataBoundary() = wrap(baseStore.metadataBoundary())
                        override suspend fun readMetadata(ref: GraphObjectRef, folder: String): java.io.InputStream? {
                            val input = if (control) baseStore.readMetadata(ref, folder) else delegate.readMetadata(ref, folder)
                            if (ref.cloudFileId != manifestId || fired || input == null) return input
                            val verifiedManifest = input.use { it.readBytes() }
                            BackupGraphProtocol.verify(ref, verifiedManifest)
                            fired = true; mutate(api, laterId, kind)
                            return verifiedManifest.inputStream()
                        }
                    }
                    val error = try { restores.restorer(b, wrap(baseStore)).restore(); null } catch (e: IllegalStateException) { e }
                    val samePosition = original == b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                    val sameRows = b.db.noteDao().getById("n")!!.bodyPlainText == "Original العربية"
                    results.put(JSONObject().put("mutation", kind).put("fresh_control", control).put("mutation_fired", fired)
                        .put("rejected", error != null).put("applied_unchanged", samePosition).put("rows_unchanged", sameRows)
                        .put("safe", fired && error != null && samePosition && sameRows))
                }
            }
        }
        saveFreshness("restore", results)
        assertTrue("Every post-manifest mutation must block application/advancement: $results",
            (0 until results.length()).all { results.getJSONObject(it).getBoolean("safe") })
    }

    @Test fun noChangeMustRefreshRequiredMetadataAfterCapture() = runBlocking {
        val results = JSONArray()
        for (kind in listOf("delete", "change", "move")) for (warm in listOf(false, true)) {
            val api = Api()
            BackupGraphWriterRoomTest().Fixture(remoteStore = api.store()).use { f ->
                f.root(); val original = f.binding(); val uploads = api.uploads
                if (!warm) api.cache.clear()
                var fired = false; var emittedAlready = false; var getsAtMutation = -1
                var result: GraphWriterResult? = null
                val error = try {
                    result = writer(f, api) { progress ->
                        if (progress.stage == GraphBackupStage.CAPTURING_CHANGES) {
                            fired = true; getsAtMutation = api.metadataGets
                            mutate(api, original.checkpointFileId, kind)
                        }
                        if (progress.stage == GraphBackupStage.ALREADY_BACKED_UP) emittedAlready = true
                    }.publish()
                    null
                } catch (e: IllegalStateException) { e }
                results.put(JSONObject().put("mutation", kind).put("initial_cache_warm", warm).put("mutation_fired", fired)
                    .put("rejected", error != null).put("reported_already_backed_up", result?.alreadyBackedUp == true)
                    .put("emitted_already_backed_up", emittedAlready).put("reads_after_mutation", api.metadataGets - getsAtMutation)
                    .put("binding_unchanged", original == f.binding()).put("new_uploads", api.uploads - uploads)
                    .put("safe", fired && error != null && !emittedAlready && result == null && original == f.binding() && api.uploads == uploads))
            }
        }
        saveFreshness("no-change", results)
        assertTrue("No-change status needs post-capture freshness: $results",
            (0 until results.length()).all { results.getJSONObject(it).getBoolean("safe") })
    }
}
