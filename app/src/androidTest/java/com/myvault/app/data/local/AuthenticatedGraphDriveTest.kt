package com.myvault.app.data.local

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.entity.BlockEntity
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
import java.io.InputStream
import java.net.Socket

/** Test APK only. OAuth stays on the host; the loopback broker admits only its newly created Drive IDs. */
@RunWith(AndroidJUnit4::class)
class AuthenticatedGraphDriveTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun config() = JSONObject(File(base.filesDir, "disposable-drive-config.json").readText())
    internal class RemoteStore(val config: JSONObject, private val timing: BackupGraphTiming? = null) : DisposableGraphObjectStore {
        override val context = GraphWriterContext(config.getString("accountScope"), config.getString("driveAccountId"), config.getString("lineageId"))
        var lostRole: String? = null
        var lost = false
        fun call(action: String, fields: JSONObject = JSONObject()): Any {
            fields.put("action", action).put("lineage", context.lineageId)
            fields.put("stage", timing?.current ?: "harness")
            val body = fields.toString().toByteArray(Charsets.UTF_8)
            val raw = Socket("127.0.0.1", config.getInt("port")).use { socket ->
                socket.soTimeout = 120000
                val header = "POST /call HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Test-Nonce: ${config.getString("nonce")}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(header.toByteArray(Charsets.US_ASCII)); socket.getOutputStream().write(body); socket.getOutputStream().flush()
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            val response = JSONObject(raw.substringAfter("\r\n\r\n"))
            check(raw.startsWith("HTTP/1.1 200")) { response.optString("error", "Disposable transport failed") }
            return response.get("value")
        }
        override suspend fun reserveId() = call("reserve") as String
        override suspend fun reserveIds(count: Int): List<String> = (call("reserveMany", JSONObject().put("count", count)) as JSONArray).let { ids ->
            (0 until ids.length()).map { ids.getString(it) }
        }
        fun bytes(id: String): ByteArray? = call("read", JSONObject().put("id", id)).let {
            if (it == JSONObject.NULL) null else android.util.Base64.decode(it as String, android.util.Base64.NO_WRAP)
        }
        override suspend fun read(objectId: String): InputStream? = bytes(objectId)?.inputStream()
        override suspend fun create(objectId: String, role: String, file: File) {
            call("create", JSONObject().put("id", objectId).put("role", role).put("bytes", android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)))
            if (role == lostRole && !lost) { lost = true; error("Disposable successful create response lost") }
        }
        override suspend fun commits(): List<GraphObject> = (call("commits") as JSONArray).let { array ->
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i); val r = obj.getJSONObject("objectRef")
                GraphObject(GraphObjectRef(r.getString("cloudFileId"), r.getString("sha256"), r.getLong("size")), android.util.Base64.decode(obj.getString("bytes"), android.util.Base64.NO_WRAP))
            }
        }
        fun marker(name: String) = call("marker", JSONObject().put("name", name)) as JSONObject
    }
    private fun fixture(store: RemoteStore) = BackupGraphWriterRoomTest().Fixture(nameOverride = store.config.getString("dbName"), remoteStore = store)
    private suspend fun graph(store: RemoteStore) = BackupGraph.discover(store.commits(), store.context.driveAccountId, store.context.lineageId)
    private suspend fun read(store: RemoteStore) = BackupGraphReconstruction.read(graph(store)) { checkNotNull(store.bytes(it)) }
    private suspend fun refused(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: IllegalStateException) { failed = true }; assertTrue("Must fail closed", failed) }
    private suspend fun export(f: BackupGraphWriterRoomTest.Fixture, store: RemoteStore, name: String, fork: Boolean = false) {
        val refs = store.commits(); val objects = JSONObject()
        val ids = f.db.openHelper.writableDatabase.query("SELECT objectId FROM backup_graph_publication_objects").use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        for(id in ids) store.bytes(id)?.let { objects.put(id, android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
        val summary = JSONObject().put("accountId", store.context.driveAccountId).put("lineageId", store.context.lineageId).put("objects", objects)
            .put("refs", JSONArray(refs.map { JSONObject().put("cloudFileId", it.objectRef.cloudFileId).put("sha256", it.objectRef.sha256).put("size", it.objectRef.size) }))
            .put("expectedStatus", if(fork) "FORK" else "SINGLE_TIP")
        if(!fork) {
            val result = read(store)
            val notes = JSONArray(result.files.getValue("notes.json"))
            summary.put("expectedNotes", notes).put("expectedAttachments", JSONArray(result.files.getValue("attachments.json")))
        }
        File(base.filesDir,"authenticated-graph-fixtures").apply { mkdirs() }.resolve("$name.json").writeText(summary.toString())
    }
    @Test fun verifyDisposableGraph() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("drivePhase")
        assumeTrue("Authenticated verification requires an explicit disposable phase", phase != null)
        check(phase in setOf("prepare", "recover", "corrupt"))
        val settings = config()
        check(settings.getString("dbName").matches(Regex("graph-drive-disposable-[0-9a-f-]{36}\\.db")))
        val store = RemoteStore(settings); val f = fixture(store)
        val account = store.context.accountScope
        if(phase == "prepare") {
            try {
                store.marker("root")
                f.note(); f.db.blockDao().upsertAll(listOf(BlockEntity("rich-block", "n", "rich_text", """{"text":"English الْعَرَبِيَّة","styleMarks":[{"start":0,"end":7,"bold":true}]}""", 0)))
                f.binary(4096); val root = f.writer().createRoot(f.prepared()); assertNotNull(root.commitId)
                assertEquals(4096L, read(store).binaries!!.single().size)
                assertTrue(read(store).files.getValue("blocks.json").contains("styleMarks"))
                export(f,store,"root")
                val measurements = JSONArray()
                suspend fun measured(name: String, operation: suspend () -> GraphWriterResult): GraphWriterResult {
                    val captureStart = System.nanoTime()
                    val captured = PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)).capture(account)
                    val captureMs = (System.nanoTime()-captureStart)/1000000.0
                    val start = store.marker(name); val began = System.nanoTime(); val result = operation()
                    val elapsed = (System.nanoTime()-began)/1000000.0; val end = store.marker("after-$name")
                    measurements.put(JSONObject().put("test",name).put("captureMs",captureMs).put("pendingRows",captured.records.size).put("payloadRows",result.metrics.payloadRows)
                        .put("binariesCreated",result.metrics.binariesCreated).put("deltasCreated",result.metrics.deltasCreated).put("commitsCreated",result.metrics.commitsCreated)
                        .put("driveRequests",end.getInt("requests")-start.getInt("requests")).put("totalMs",elapsed))
                    File(f.root,"measurements.json").writeText(measurements.toString()); return result
                }
                f.queries.clear(); val zero = measured("zero") { f.writer().publish() }
                assertTrue(zero.alreadyBackedUp); assertEquals(GraphWriterMetrics(0,0,0,0,0,0),zero.metrics)
                assertTrue(f.querySnapshot().none { it.startsWith("SELECT * FROM `notes`") })
                repeat(7) { f.edit("Settled edit $it العربية") }
                f.queries.clear(); val one = measured("one-note") { f.writer().publish() }
                assertEquals(GraphWriterMetrics(1,1,0,0,1,1),one.metrics)
                assertEquals(2,f.querySnapshot().count { it.startsWith("SELECT * FROM `notes`") }) // Instrumented capture + writer capture.
                val localA = one.commitId!!; export(f,store,"one-note")
                f.edit("Three n"); f.note("two","Three two"); f.note("three","Three three")
                val three = measured("three") { f.writer().publish() }; assertEquals(3,three.metrics.payloadRows)
                assertEquals(1,three.metrics.deltasCreated); assertEquals(1,three.metrics.commitsCreated)
                f.binary(8192); val replacement = measured("binary-replacement") { f.writer().publish() }
                assertEquals(GraphWriterMetrics(1,1,1,1,1,1),replacement.metrics)
                assertEquals(8192L,read(store).binaries!!.single().size)
                val plan = (graph(store)).plan(localA); assertEquals(GraphStatus.DESCENDANTS,plan.status)
                assertEquals(listOf(three.commitId,replacement.commitId),plan.descendants.map { it.commitId })
                assertEquals(GraphStatus.ALREADY_CURRENT,(graph(store)).plan(replacement.commitId).status)
                export(f,store,"replacement")
                f.binary(2048,"new-attachment"); assertEquals(1,measured("new-attachment") { f.writer().publish() }.metrics.binariesCreated)
                assertEquals(setOf(2048L,8192L),read(store).binaries!!.map { it.size }.toSet())
                f.db.attachmentDao().updateFileName("pdf","metadata-only.pdf")
                assertEquals(GraphWriterMetrics(1,1,0,0,1,1),measured("metadata-only") { f.writer().publish() }.metrics)
                f.db.noteDao().deleteByIds(listOf("two"))
                val deletion = measured("delete") { f.writer().publish() }; assertEquals(1,deletion.metrics.pendingRows)
                val change = BackupGraphIntentCodec.records(f.db.backupGraphDao().publication(account,deletion.operationId!!)!!.frozenBatchJson).single()
                assertEquals(listOf("two"),change.key); assertEquals("DELETE",change.operation)
                assertEquals(2,JSONArray(read(store).files.getValue("notes.json")).length())
                val parent = f.binding(); f.binary(3072,"new-attachment")
                store.marker("crash-before-commit")
                refused { f.writer { if(it=="CREATED_BINARY") error("Disposable process interruption") }.publish() }
                assertEquals(parent,f.binding()); assertEquals(listOf(parent.commitId),(graph(store)).tips)
                f.reopen(); f.writer().resume(f.db.backupGraphDao().unfinished(account).single().operationId)
                for(role in listOf("BINARY","DELTA")) {
                    if(role=="BINARY") f.binary(4096,"new-attachment") else f.edit("Uncertain delta العربية")
                    store.lostRole = role; store.lost = false; store.marker("uncertain-$role")
                    refused { f.writer().publish() }; assertTrue(store.lost)
                    val op = f.db.backupGraphDao().unfinished(account).single().operationId
                    f.reopen(); store.lostRole = null; val recovered = f.writer().resume(op)
                    assertEquals(op,recovered.commitId); assertEquals(0,recovered.metrics.binariesCreated)
                }
                export(f,store,"final-linear")
                store.marker("commit-before-ack")
                f.edit("Frozen before actual process death العربية")
                refused { f.writer { if(it=="VERIFIED_COMMIT") error("Stop before local acknowledgement") }.publish() }
                assertEquals(1,f.pending().size)
                File(f.root,"before-pid").writeText(android.os.Process.myPid().toString())
            } finally { f.db.close() }
        } else if(phase=="recover") {
            try {
                assertNotEquals(File(f.root,"before-pid").readText().toInt(),android.os.Process.myPid())
                val op = f.db.backupGraphDao().unfinished(account).single().operationId
                val before = store.commits().size; f.edit("Newer N+1 العربية"); store.marker("recover-after-commit")
                f.writer().resume(op); assertEquals(before,store.commits().size); assertEquals(1,f.pending().size)
                val completed = f.binding(); f.writer().resume(op); assertEquals(completed,f.binding())
                assertTrue(JSONArray(read(store).files.getValue("notes.json")).getJSONObject(0).getString("bodyPlainText").contains("Frozen"))
                f.writer().publish(); val parent = f.binding()
                val wrong = object: DisposableGraphObjectStore by store { override val context = GraphWriterContext("other@example.com","other-permission",store.context.lineageId) }
                val denied = InternalBackupGraphWriter(f.db,f.journal,PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)),base.filesDir,File(f.root,"wrong-stage"),wrong)
                refused { denied.resume(op) }; assertNull(f.db.backupGraphDao().binding("other@example.com",store.context.lineageId))
                val b = BackupGraphWriterRoomTest().Fixture(remoteStore=store)
                b.use {
                    b.note(text="Newer N+1 العربية"); b.journal.registerAccount(account)
                    b.db.withTransaction {
                        b.db.backupJournalDao().acknowledge(account,b.db.backupJournalDao().clock().generation)
                        b.db.backupJournalDao().trust(account,parent.checkpointId,parent.deltaHeadId,parent.checkpointFileId,parent.checkpointSha256)
                        b.db.backupGraphDao().putBinding(parent.copy(originEpoch=b.db.backupJournalDao().clock().originEpoch))
                    }
                    f.edit("Branch A English"); b.edit("Branch B العربية"); store.marker("concurrent-fork")
                    var commitA: String? = null
                    val resultB = b.writer { if(it=="PUBLISHING") commitA=f.writer().publish().commitId }.publish()
                    val g = graph(store); assertEquals(GraphStatus.FORK,g.status)
                    assertEquals(setOf(commitA,resultB.commitId),g.tips.toSet()); assertTrue(resultB.forkDetected)
                    f.edit("Third branch must be blocked"); val count = store.commits().size
                    refused { f.writer().publish() }; assertEquals(count,store.commits().size)
                    // Include B's immutable objects in the cross-client fixture without changing either publication.
                    val objects = JSONObject(); for(c in store.commits()) objects.put(c.objectRef.cloudFileId,android.util.Base64.encodeToString(c.bytes,android.util.Base64.NO_WRAP))
                    File(base.filesDir,"authenticated-graph-fixtures").resolve("fork-headers.json").writeText(JSONObject().put("accountId",store.context.driveAccountId).put("lineageId",store.context.lineageId)
                        .put("objects",objects).put("refs",JSONArray(store.commits().map { JSONObject().put("cloudFileId",it.objectRef.cloudFileId).put("sha256",it.objectRef.sha256).put("size",it.objectRef.size) })).toString())
                }
            } finally { f.db.close() }
        } else {
            check(phase=="corrupt")
            try {
                val g=graph(store); assertEquals(GraphStatus.CORRUPT,g.status); refused { f.writer().publish() }
                val missing=RemoteStore(JSONObject(store.config.toString()).put("lineageId",store.config.getString("missingLineageId")))
                assertEquals(GraphStatus.MISSING_ANCESTRY,graph(missing).status)
                val original = f.db.openHelper.writableDatabase.query("SELECT o.objectId,o.sha256,o.byteCount FROM backup_graph_publication_objects o JOIN backup_graph_publications p ON o.accountScope=p.accountScope AND o.operationId=p.operationId WHERE p.originalBindingJson IS NULL AND o.role='BINARY'").use { c ->
                    assertTrue(c.moveToFirst()); Triple(c.getString(0),c.getString(1),c.getLong(2))
                }
                val oldBytes=checkNotNull(store.bytes(original.first))
                assertEquals(4096L,original.third); assertEquals(original.third,oldBytes.size.toLong())
                assertEquals(original.second,IncrementalBackupFormat.sha256(oldBytes))
                val replacement=f.db.backupJournalDao().binaryReference(account,"pdf")!!
                val newBytes=checkNotNull(store.bytes(replacement.cloudFileId))
                assertEquals(8192,newBytes.size); assertEquals(replacement.sha256,IncrementalBackupFormat.sha256(newBytes))
                assertNotEquals(original.first,replacement.cloudFileId)
            }
            finally { f.close() }
        }
    }
}
