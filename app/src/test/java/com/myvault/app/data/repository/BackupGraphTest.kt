package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class BackupGraphTest {
    private fun load(bundle: JSONObject, id: String): ByteArray = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(id))
    private fun objects(bundle: JSONObject, fixture: JSONObject) = fixture.getJSONArray("refs").let { refs ->
        (0 until refs.length()).map { i -> BackupGraphFixtures.ref(refs.getJSONObject(i)).let { ref -> GraphObject(ref, load(bundle, ref.cloudFileId)) } }
    }
    private fun graph(bundle: JSONObject, fixture: JSONObject) = BackupGraph.discover(objects(bundle, fixture), bundle.getString("accountId"), bundle.getString("lineageId"))
    private fun verify(bundle: JSONObject): JSONArray {
        val summaries = JSONArray()
        val cases = bundle.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val fixture = cases.getJSONObject(index); val name = fixture.getString("name")
            val graph = graph(bundle, fixture)
            val plan = if (name == "historical") BackupGraph.historicalPlan() else graph.plan(if (fixture.isNull("applied")) null else fixture.getString("applied"))
            val expected = fixture.getJSONObject("expected")
            assertEquals("$name: ${graph.issues}", expected.getString("status"), plan.status.name)
            fun compare(field: String, actual: List<String>) { if (expected.has(field)) assertEquals("$name/$field", expected.getJSONArray(field).toString(), JSONArray(actual).toString()) }
            compare("roots", graph.roots); compare("tips", graph.tips)
            compare("ordered", plan.commits.map { it.commitId }); compare("descendants", plan.descendants.map { it.commitId })
            compare("deltas", plan.deltas.map { it.deltaId })
            if (fixture.has("restore")) {
                val requested = mutableListOf<String>()
                val result = BackupGraphReconstruction.read(graph) { requested += it; load(bundle, it) }
                val target = fixture.getJSONObject("restore")
                val notes = JSONArray(result.files.getValue("notes.json"))
                val rows = (0 until notes.length()).map { notes.getJSONObject(it) }
                assertEquals(target.getString("body"), rows.single { it.getString("id") == "n" }.getString("bodyPlainText"))
                assertEquals(target.getJSONArray("notes").toString(), JSONArray(rows.map { it.getString("id") }.sorted()).toString())
                assertEquals(target.getLong("pdfSize"), result.binaries!!.single { it.attachmentId == "pdf" }.size)
                assertEquals(target.getBoolean("clipPresent"), result.binaries.any { it.attachmentId == "clip" })
                assertTrue(rows.single { it.getString("id") == "n" }.getJSONArray("styleMarks").getJSONObject(0).getBoolean("bold"))
                if (target.getLong("pdfSize") == 8192L) {
                    val origin = bundle.getString("origin")
                    assertFalse(requested.contains("$origin-pdf-4096"))
                    assertThrows(Exception::class.java) { BackupGraphReconstruction.read(graph) { if (it == "$origin-pdf-8192") error("Missing replacement") else load(bundle, it) } }
                    assertThrows(IllegalStateException::class.java) { BackupGraphReconstruction.read(graph) { if (it == "$origin-pdf-8192") ByteArray(4096) { 79 } else load(bundle, it) } }
                    assertThrows(IllegalStateException::class.java) { BackupGraphReconstruction.read(graph) { if (it == "$origin-pdf-8192") ByteArray(8192) { 88 } else load(bundle, it) } }
                }
                if (name == "explicit-delete") assertEquals(setOf("attachments.json" to listOf("clip"), "notes.json" to listOf("delete-note")), result.permanentDeletions.map { it.file to it.key }.toSet())
            }
            summaries.put(JSONObject().put("name", name).put("status", plan.status.name).put("roots", JSONArray(graph.roots)).put("tips", JSONArray(graph.tips))
                .put("valid", JSONArray(graph.commits.keys.sorted())).put("ordered", JSONArray(plan.commits.map { it.commitId }))
                .put("descendants", JSONArray(plan.descendants.map { it.commitId })).put("deltas", JSONArray(plan.deltas.map { it.deltaId }))
                .put("capabilities", JSONArray(plan.commits.map { JSONArray(it.requiredReaders) })).put("checkpoint", plan.checkpoint?.checkpointId ?: JSONObject.NULL))
        }
        val vector = bundle.getJSONObject("vector"); val ref = BackupGraphFixtures.ref(vector.getJSONObject("ref")); val raw = load(bundle, ref.cloudFileId)
        val parsed = BackupGraphProtocol.parse(GraphObject(ref, raw))
        assertArrayEquals(raw, BackupGraphProtocol.encode(parsed))
        val intent = vector.getJSONObject("intent")
        assertEquals(intent.getString("accountId"), parsed.accountId); assertEquals(intent.getString("lineageId"), parsed.lineageId)
        assertEquals(intent.getString("commitId"), parsed.commitId); assertEquals(intent.getString("kind"), parsed.kind)
        assertEquals(intent.getJSONArray("requiredReaders").toString(), JSONArray(parsed.requiredReaders).toString())
        assertEquals(BackupGraphFixtures.ref(intent.getJSONObject("checkpoint")), parsed.checkpoint.objectRef)
        assertEquals(ref.sha256, IncrementalBackupFormat.sha256(BackupGraphProtocol.encode(parsed.copy())))
        return summaries
    }

    @Test fun independentFixturesAndBidirectionalWireCompatibility() {
        val android = BackupGraphFixtures.generate("android")
        val results = verify(android)
        System.getenv("MYVAULT_GRAPH_COMPAT_DIR")?.let { path ->
            val directory = File(path).apply { mkdirs() }
            File(directory, "android.json").writeText(android.toString())
            File(directory, "android-read-android.json").writeText(results.toString(2))
            val web = File(directory, "web.json")
            assertTrue("Web-generated fixtures must exist for bidirectional run", web.exists())
            File(directory, "android-read-web.json").writeText(verify(JSONObject(web.readText())).toString(2))
        }
    }

    @Test fun strictWireRejectsDuplicateKeysUnknownFieldsCoercionsAndUnsafeSizes() {
        val bundle = BackupGraphFixtures.generate("unit")
        val vector = bundle.getJSONObject("vector").getJSONObject("ref")
        val original = BackupGraphProtocol.utf8(load(bundle, vector.getString("cloudFileId")))
        fun reject(text: String) {
            val raw = text.toByteArray(Charsets.UTF_8)
            assertThrows(Exception::class.java) { BackupGraphProtocol.parse(GraphObject(GraphObjectRef("bad", IncrementalBackupFormat.sha256(raw), raw.size.toLong()), raw)) }
        }
        reject(original.replace("\"version\":1", "\"version\":1,\"version\":1"))
        reject(original.replace("\"version\":1", "\"version\":1.0"))
        reject(original + " {}")
        reject(original.replace("\"version\":1", "\"version\":\"1\""))
        reject(original.replace("\"parents\":[]", "\"parents\":[],\"unknown\":true"))
        reject(original.replace(Regex("\"size\":[0-9]+"), "\"size\":9007199254740992"))
        val raw = load(bundle, vector.getString("cloudFileId"))
        val parsed = BackupGraphProtocol.parse(GraphObject(BackupGraphFixtures.ref(vector), raw))
        assertThrows(IllegalStateException::class.java) { BackupGraphProtocol.encode(parsed.copy(accountId = "\ud800")) }
        assertThrows(IllegalStateException::class.java) { BackupGraphProtocol.encode(parsed.copy(parents = listOf(GraphParent(parsed.commitId, BackupGraphFixtures.ref(vector))))) }
        assertEquals(GraphStatus.MISSING_ANCESTRY, BackupGraph.discover(emptyList(), BackupGraphFixtures.account, BackupGraphFixtures.lineage).status)
    }

    @Test fun graphDiscoveryDoesNotReadBinaryObjectsAndCheckpointRolloversKeepAncestry() {
        val bundle = BackupGraphFixtures.generate("unit")
        val fixture = bundle.getJSONArray("cases").getJSONObject(2)
        val graph = graph(bundle, fixture)
        assertEquals(4, graph.commits.size)
        // Discovery input contains only four commit bodies, not the checkpoint/delta/binary store.
        val previous = graph.commits.getValue(BackupGraphFixtures.uuid(4))
        val parentRef = objects(bundle, fixture).single { BackupGraphProtocol.parse(it).commitId == previous.commitId }.objectRef
        val cp = previous.checkpoint.copy(checkpointId = "full-${"b".repeat(64)}", objectRef = previous.checkpoint.objectRef.copy(cloudFileId = "new-cp", sha256 = "b".repeat(64)))
        val newCheckpoint = previous.copy(commitId = BackupGraphFixtures.uuid(50), kind = "checkpoint", parents = listOf(GraphParent(previous.commitId, parentRef)), checkpoint = cp, delta = null)
        val raw = BackupGraphProtocol.encode(newCheckpoint)
        val rollover = BackupGraph.discover(objects(bundle, fixture) + GraphObject(GraphObjectRef("rollover", IncrementalBackupFormat.sha256(raw), raw.size.toLong()), raw), BackupGraphFixtures.account, BackupGraphFixtures.lineage)
        val plan = rollover.plan(previous.commitId)
        assertEquals(GraphStatus.DESCENDANTS, plan.status); assertTrue(plan.requiresCheckpoint)
        assertEquals(listOf(newCheckpoint), plan.descendants); assertEquals(5, plan.commits.size); assertTrue(plan.deltas.isEmpty()); assertEquals(cp, plan.checkpoint)
    }

    @Test fun boundedDiscoveryAndAncestryBenchmarks() {
        val performance = JSONArray()
        for (count in listOf(10, 100, 1000, 10000)) {
            val objects = mutableListOf<GraphObject>()
            val cpRef = GraphObjectRef("cp", "a".repeat(64), 100)
            var parent = BackupGraphCommit(BackupGraphFixtures.account, BackupGraphFixtures.lineage, BackupGraphFixtures.uuid(100), "checkpoint", listOf(BackupGraphCapability), emptyList(), GraphCheckpoint("full-${cpRef.sha256}", cpRef), null)
            fun add(c: BackupGraphCommit, id: String) {
                val bytes = BackupGraphProtocol.encode(c)
                objects += GraphObject(GraphObjectRef(id, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong()), bytes)
            }
            add(parent, "benchmark-0")
            for (n in 1 until count) {
                val ancestor = GraphParent(parent.commitId, objects.last().objectRef)
                parent = if (n % 1000 == 0) {
                    val hash = n.toString(16).padStart(64, '0')
                    parent.copy(commitId = BackupGraphFixtures.uuid(100 + n), parents = listOf(ancestor), kind = "checkpoint", delta = null,
                        checkpoint = GraphCheckpoint("full-$hash", GraphObjectRef("cp-$n", hash, 100)))
                } else parent.copy(commitId = BackupGraphFixtures.uuid(100 + n), parents = listOf(ancestor), kind = "delta",
                    requiredReaders = listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(),
                    delta = GraphDelta("d-$n", parent.deltaHead, GraphObjectRef("delta-$n", "a".repeat(64), 100)))
                add(parent, "benchmark-$n")
            }
            val runtime = Runtime.getRuntime(); val memory = runtime.totalMemory() - runtime.freeMemory()
            val start = System.nanoTime(); val graph = BackupGraph.discover(objects, BackupGraphFixtures.account, BackupGraphFixtures.lineage)
            val discovered = System.nanoTime(); val plan = graph.plan(); val finished = System.nanoTime()
            assertEquals(GraphStatus.SINGLE_TIP, plan.status); assertEquals(count, plan.commits.size)
            performance.put(JSONObject().put("count", count).put("commitBytes", objects.sumOf { it.bytes.size })
                .put("discoveryMs", (discovered - start) / 1_000_000.0).put("ancestryMs", (finished - discovered) / 1_000_000.0)
                .put("heapDeltaBytes", runtime.totalMemory() - runtime.freeMemory() - memory))
        }
        System.getenv("MYVAULT_GRAPH_COMPAT_DIR")?.let { File(it, "android-performance.json").writeText(performance.toString(2)) }
        println("Disposable graph benchmark: $performance")
    }

    @Test fun publicationAndProductionRoutingRemainOffAndRoomRemains34() {
        assertFalse(BackupGraphPublicationEnabled); assertFalse(IncrementalBackupPublicationEnabled)
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java/com/myvault/app/data/local/VaultDatabase.kt").exists() }
        val drive = File(root, "app/src/main/java/com/myvault/app/data/sync/GoogleDriveIncrementalSyncRepository.kt").readText()
        assertTrue(drive.contains("backupRepository.exportMetadataForDriveSync(")); assertTrue(drive.contains("backupRepository.restoreBackupFromFile("))
        assertFalse(drive.contains("InternalBackupGraphWriter(") || drive.contains("InternalBackupGraphRestore(") || drive.contains("IncrementalBackupWriter("))
        val schema = JSONObject(File(root, "app/schemas/com.myvault.app.data.local.VaultDatabase/34.json").readText())
        assertEquals(34, schema.getJSONObject("database").getInt("version"))
    }

    @Test fun graphCommitCannotMasqueradeAsAnExistingDeltaAndEmptyCheckpointFailsClosed() {
        val bundle = BackupGraphFixtures.generate("unit")
        val rootFixture = bundle.getJSONArray("cases").getJSONObject(1)
        val source = objects(bundle, rootFixture).single()
        val root = BackupGraphProtocol.parse(source)
        assertThrows(IllegalStateException::class.java) { IncrementalBackupFormat.parseChanges(JSONObject(BackupGraphProtocol.utf8(source.bytes))) }
        val empty = JSONObject().put("schemaVersion", 1).put("storage", "google-drive-api").put("entries", JSONArray()).toString().toByteArray()
        val cpRef = GraphObjectRef("empty-checkpoint", IncrementalBackupFormat.sha256(empty), empty.size.toLong())
        val bytes = BackupGraphProtocol.encode(root.copy(checkpoint = GraphCheckpoint("full-${cpRef.sha256}", cpRef)))
        val graph = BackupGraph.discover(listOf(GraphObject(GraphObjectRef("empty-root", IncrementalBackupFormat.sha256(bytes), bytes.size.toLong()), bytes)), root.accountId, root.lineageId)
        assertThrows(IllegalStateException::class.java) { BackupGraphReconstruction.read(graph) { empty } }
    }
}
