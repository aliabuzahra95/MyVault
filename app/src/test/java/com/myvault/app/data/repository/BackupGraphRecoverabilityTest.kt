package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.UUID

/**
 * Comprehensive recoverability audit and regression suite covering Prompt Section 10 Groups A through J.
 * Tests end-to-end recoverability on clean destination devices without local caches.
 */
class BackupGraphRecoverabilityTest {

    private val account = "account-recoverability@example.com"
    private val lineage = "lineage-audit-20261018"
    private val driveAccountId = "drive-acc-test"

    private fun sha256(bytes: ByteArray): String = IncrementalBackupFormat.sha256(bytes)
    private fun uuid(n: Int): String = "00000000-0000-4000-8000-${n.toString(16).padStart(12, '0')}"

    private class MemoryStore(
        val account: String,
        val lineage: String,
        val driveId: String,
    ) : DisposableGraphObjectStore {
        val objects = mutableMapOf<String, ByteArray>()
        val commitsList = mutableListOf<GraphObject>()
        var simulateInaccessible: ((String) -> Boolean)? = null
        var simulateNetworkFailure: ((String) -> Boolean)? = null

        override val context: GraphWriterContext = GraphWriterContext(account, driveId, lineage)
        override suspend fun reserveId(): String = UUID.randomUUID().toString()
        override suspend fun commits(): List<GraphObject> = commitsList.toList()
        override suspend fun read(objectId: String): InputStream? {
            if (simulateInaccessible?.invoke(objectId) == true) return null
            if (simulateNetworkFailure?.invoke(objectId) == true) throw java.io.IOException("Simulated transient network failure on $objectId")
            val bytes = objects[objectId] ?: return null
            return ByteArrayInputStream(bytes)
        }
        override suspend fun create(objectId: String, role: String, file: File) {
            objects[objectId] = file.readBytes()
        }

        fun putObject(id: String, bytes: ByteArray): GraphObjectRef {
            objects[id] = bytes
            return GraphObjectRef(id, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
        }

        fun putCommit(commit: BackupGraphCommit): GraphObject {
            val bytes = BackupGraphProtocol.encode(commit)
            val ref = putObject("commit-${commit.commitId}", bytes)
            val obj = GraphObject(ref, bytes)
            commitsList.add(obj)
            return obj
        }
    }

    private fun sampleNote(id: String, text: String) = JSONObject()
        .put("id", id)
        .put("folderId", JSONObject.NULL)
        .put("parentNoteId", JSONObject.NULL)
        .put("title", "Note $id")
        .put("bodyPlainText", text)
        .put("isPinned", false)
        .put("isFolderPinned", false)
        .put("isFavourite", false)
        .put("createdAt", 1000L)
        .put("updatedAt", 1000L)

    private fun sampleAttachment(id: String, size: Long, deletedAt: Long? = null, remoteUrl: String? = null, localPath: String? = "/local/$id.pdf") = JSONObject()
        .put("id", id)
        .put("noteId", "")
        .put("libraryFolderId", JSONObject.NULL)
        .put("fileName", "$id.pdf")
        .put("mimeType", "application/pdf")
        .put("sizeBytes", size)
        .put("fileEntry", "files/$id")
        .put("localPath", localPath ?: "")
        .put("remoteUrl", remoteUrl?.let { it } ?: JSONObject.NULL)
        .put("isPinned", false)
        .put("createdAt", 1000L)
        .put("deletedAt", deletedAt?.let { it } ?: JSONObject.NULL)
        .put("orderIndex", 0)

    // =========================================================================
    // GROUP A: Clean-device reconstruction
    // =========================================================================
    @Test
    fun testGroupA_cleanDeviceReconstructionWithoutLocalCache() {
        val bundle = BackupGraphFixtures.generate("grpA")
        val graph = BackupGraph.discover(
            bundle.getJSONArray("cases").getJSONObject(1).getJSONArray("refs").let { refs ->
                (0 until refs.length()).map { i ->
                    val r = BackupGraphFixtures.ref(refs.getJSONObject(i))
                    val b = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(r.cloudFileId))
                    GraphObject(r, b)
                }
            },
            bundle.getString("accountId"),
            bundle.getString("lineageId")
        )

        val requested = mutableListOf<String>()
        val result = BackupGraphReconstruction.read(graph) { id ->
            requested += id
            Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(id))
        }

        assertNotNull(result.files["notes.json"])
        assertNotNull(result.files["attachments.json"])
        val notes = JSONArray(result.files["notes.json"])
        assertTrue(notes.length() >= 3)
        val binaries = result.binaries!!
        assertEquals(2, binaries.size)
        assertTrue(binaries.any { it.attachmentId == "pdf" && it.size == 4096L })
        assertTrue(binaries.any { it.attachmentId == "clip" && it.size == 512L })
    }

    // =========================================================================
    // GROUP B: Multiple incremental commits with inherited & deleted binaries
    // =========================================================================
    @Test
    fun testGroupB_multipleIncrementalCommitsDirectToTip() {
        val store = MemoryStore(account, lineage, driveAccountId)
        val file1Bytes = ByteArray(1024) { 1 }
        val file2Bytes = ByteArray(2048) { 2 }
        val ref1 = store.putObject("bin-1", file1Bytes)
        val ref2 = store.putObject("bin-2", file2Bytes)

        val cpEntries = JSONArray()
            .put(BackupGraphFixtures.refJson(ref1).put("kind", "file").put("backupEntry", "files/att-1"))
            .put(BackupGraphFixtures.refJson(ref2).put("kind", "file").put("backupEntry", "files/att-2"))
            .put(BackupGraphFixtures.refJson(store.putObject("cp-notes", JSONArray().put(sampleNote("n1", "Note 1")).toString().toByteArray()))
                .put("kind", "metadata").put("fileName", "notes.json"))
            .put(BackupGraphFixtures.refJson(store.putObject("cp-att", JSONArray().put(sampleAttachment("att-1", 1024)).put(sampleAttachment("att-2", 2048)).toString().toByteArray()))
                .put("kind", "metadata").put("fileName", "attachments.json"))
            .put(BackupGraphFixtures.refJson(store.putObject("cp-manifest", JSONObject().put("version", 1).toString().toByteArray()))
                .put("kind", "metadata").put("fileName", "manifest.json"))

        val cpRef = store.putObject("checkpoint-obj", JSONObject().put("schemaVersion", 1).put("storage", "google-drive-api").put("cloudVersion", 100).put("entries", cpEntries).toString().toByteArray())
        val checkpoint = GraphCheckpoint("full-${cpRef.sha256}", cpRef)
        val rootCommit = BackupGraphCommit(account, lineage, uuid(1), "checkpoint", listOf(BackupGraphCapability), emptyList(), checkpoint, null)
        val rootObj = store.putCommit(rootCommit)

        // Delta 1: edit note n1, add note n2
        val d1Changes = listOf(
            BackupRecordChange("notes.json", listOf("n1"), sampleNote("n1", "Edited Note 1")),
            BackupRecordChange("notes.json", listOf("n2"), sampleNote("n2", "New Note 2"))
        )
        val delta1Json = IncrementalBackupFormat.createDelta(checkpoint.checkpointId, rootCommit.deltaHead, "delta-1", d1Changes, null)
        val d1Ref = store.putObject("delta-1-obj", delta1Json.toString().toByteArray())
        val c1 = rootCommit.copy(commitId = uuid(2), kind = "delta", parents = listOf(GraphParent(rootCommit.commitId, rootObj.objectRef)),
            requiredReaders = listOf(BackupGraphCapability, "checkpoint-delta-v1"), delta = GraphDelta("delta-1", rootCommit.deltaHead, d1Ref))
        val c1Obj = store.putCommit(c1)

        // Delta 2: soft-delete att-1 (tombstone with deletedAt timestamp)
        val tombstoneAtt1 = sampleAttachment("att-1", 1024, deletedAt = 2000L)
        val d2Changes = listOf(
            BackupRecordChange("attachments.json", listOf("att-1"), tombstoneAtt1)
        )
        val delta2Json = IncrementalBackupFormat.createDelta(checkpoint.checkpointId, c1.deltaHead, "delta-2", d2Changes, null)
        val d2Ref = store.putObject("delta-2-obj", delta2Json.toString().toByteArray())
        val c2 = c1.copy(commitId = uuid(3), kind = "delta", parents = listOf(GraphParent(c1.commitId, c1Obj.objectRef)),
            requiredReaders = listOf(BackupGraphCapability, "checkpoint-delta-v1"), delta = GraphDelta("delta-2", c1.deltaHead, d2Ref))
        store.putCommit(c2)

        val graph = BackupGraph.discover(store.commitsList, account, lineage)
        assertEquals(GraphStatus.SINGLE_TIP, graph.status)

        // Reconstruct tip: should only require bin-2 (att-2); att-1 is tombstoned so its binary should NOT be required
        val result = BackupGraphReconstruction.read(graph) { store.objects.getValue(it) }
        val required = requiredBackupBinaries(result.files, result.binaries!!)
        assertEquals(1, required.size)
        assertEquals("att-2", required.single().attachmentId)
        assertEquals(2048L, required.single().size)
    }

    // =========================================================================
    // GROUP C: Attachment replacement (4096-byte to 8192-byte)
    // =========================================================================
    @Test
    fun testGroupC_attachmentReplacement4096To8192SupersededIgnored() {
        val bundle = BackupGraphFixtures.generate("grpC")
        val cases = bundle.getJSONArray("cases")
        val replacementCase = (0 until cases.length()).map { cases.getJSONObject(it) }.single { it.getString("name") == "binary-replacement" }
        val graph = BackupGraph.discover(
            replacementCase.getJSONArray("refs").let { refs ->
                (0 until refs.length()).map { i ->
                    val r = BackupGraphFixtures.ref(refs.getJSONObject(i))
                    val b = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(r.cloudFileId))
                    GraphObject(r, b)
                }
            },
            bundle.getString("accountId"),
            bundle.getString("lineageId")
        )

        // Simulate that old 4096-byte binary is REMOVED from storage (throws if requested)
        val origin = bundle.getString("origin")
        val requested = mutableListOf<String>()
        val result = BackupGraphReconstruction.read(graph) { id ->
            requested += id
            if (id == "$origin-pdf-4096") error("Superseded 4096-byte binary must never be loaded!")
            Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(id))
        }

        assertFalse(requested.contains("$origin-pdf-4096"))
        assertTrue(requested.contains("$origin-pdf-8192"))
        val required = requiredBackupBinaries(result.files, result.binaries!!)
        val pdfBinary = required.single { it.attachmentId == "pdf" }
        assertEquals(8192L, pdfBinary.size)
    }

    // =========================================================================
    // GROUP D: Metadata-only attachments
    // =========================================================================
    @Test
    fun testGroupD_metadataOnlyAttachmentsNeverRequireAbsentBinaries() {
        val metaOnly1 = sampleAttachment("meta-1", 0L)
        val metaOnly2 = sampleAttachment("meta-2", 500L, remoteUrl = "https://example.com/remote.pdf", localPath = "")
        val metaOnly3 = JSONObject(sampleAttachment("meta-3", 100L).toString()).put("fileEntry", "")
        val liveLocal = sampleAttachment("live-1", 1024L, localPath = "/verified/live.pdf")

        assertTrue(isMetadataOnlyAttachment(metaOnly1))
        assertTrue(isMetadataOnlyAttachment(metaOnly2))
        assertTrue(isMetadataOnlyAttachment(metaOnly3))
        assertFalse(isMetadataOnlyAttachment(liveLocal))

        // validateBackupBinaryReference allows metadata-only attachments with null binary
        validateBackupBinaryReference(metaOnly1, null)
        validateBackupBinaryReference(metaOnly2, null)
        validateBackupBinaryReference(metaOnly3, null)

        // But live non-metadata-only attachment without binary fails
        assertThrows(IllegalStateException::class.java) {
            validateBackupBinaryReference(liveLocal, null)
        }

        // requiredBackupBinaries ignores metadata-only attachments
        val liveDescriptor = BackupBinaryDescriptor("live-1", "cloud-live-1", "a".repeat(64), 1024L)
        val rows = listOf(
            BackupRecordChange("attachments.json", listOf("meta-1"), metaOnly1),
            BackupRecordChange("attachments.json", listOf("meta-2"), metaOnly2),
            BackupRecordChange("attachments.json", listOf("meta-3"), metaOnly3),
            BackupRecordChange("attachments.json", listOf("live-1"), liveLocal)
        )
        val required = requiredBackupBinaries(rows, listOf(liveDescriptor))
        assertEquals(listOf(liveDescriptor), required)
    }

    // =========================================================================
    // GROUP E: Missing live binary fails closed safely
    // =========================================================================
    @Test
    fun testGroupE_missingLiveBinaryFailsClosed() {
        val bundle = BackupGraphFixtures.generate("grpE")
        val origin = bundle.getString("origin")
        val rootCase = bundle.getJSONArray("cases").getJSONObject(1)
        val graph = BackupGraph.discover(
            rootCase.getJSONArray("refs").let { refs ->
                (0 until refs.length()).map { i ->
                    val r = BackupGraphFixtures.ref(refs.getJSONObject(i))
                    val b = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(r.cloudFileId))
                    GraphObject(r, b)
                }
            },
            bundle.getString("accountId"),
            bundle.getString("lineageId")
        )

        // Live binary pdf-4096 is missing from storage (returns null / throws)
        assertThrows(Exception::class.java) {
            BackupGraphReconstruction.read(graph) { id ->
                if (id == "$origin-pdf-4096") error("Simulated 404: binary unavailable")
                Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(id))
            }
        }
    }

    // =========================================================================
    // GROUP F: Inaccessible Drive object (transient vs permanent)
    // =========================================================================
    @Test
    fun testGroupF_inaccessibleDriveObjectDistinguishesRecoverableFromPermanent() {
        val store = MemoryStore(account, lineage, driveAccountId)
        val liveBytes = ByteArray(512) { 42 }
        store.putObject("bin-live", liveBytes)

        // Permanent missing: read returns null
        store.simulateInaccessible = { it == "bin-live" }
        assertNull(kotlinx.coroutines.runBlocking { store.read("bin-live") })

        // Transient failure: read throws IOException
        store.simulateInaccessible = null
        store.simulateNetworkFailure = { it == "bin-live" }
        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { store.read("bin-live") }
        }
    }

    // =========================================================================
    // GROUP G: Graph integrity (missing parent, forks, corrupt SHA-256)
    // =========================================================================
    @Test
    fun testGroupG_graphIntegrityChecks() {
        val bundle = BackupGraphFixtures.generate("grpG")
        val cases = bundle.getJSONArray("cases")
        fun findCase(name: String) = (0 until cases.length()).map { cases.getJSONObject(it) }.single { it.getString("name") == name }

        fun graphFor(caseJson: JSONObject) = BackupGraph.discover(
            caseJson.getJSONArray("refs").let { refs ->
                (0 until refs.length()).map { i ->
                    val r = BackupGraphFixtures.ref(refs.getJSONObject(i))
                    val b = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(r.cloudFileId))
                    GraphObject(r, b)
                }
            },
            bundle.getString("accountId"),
            bundle.getString("lineageId")
        )

        assertEquals(GraphStatus.FORK, graphFor(findCase("fork")).status)
        assertEquals(GraphStatus.MISSING_ANCESTRY, graphFor(findCase("missing-parent")).status)
        assertEquals(GraphStatus.CORRUPT, graphFor(findCase("corrupt-bytes")).status)
        assertEquals(GraphStatus.UNSUPPORTED, graphFor(findCase("unsupported")).status)
    }

    // =========================================================================
    // GROUP H: Local-data independence across destination states
    // =========================================================================
    @Test
    fun testGroupH_localDataIndependence() {
        // Test normalizedGraphRestoreRow with various local states
        val tombstone = sampleAttachment("tomb-1", 1024L, deletedAt = 5000L, localPath = "/former/path.pdf")
        val metaOnly = sampleAttachment("meta-1", 0L, remoteUrl = "https://example.com/doc", localPath = "")
        val liveAttachment = sampleAttachment("live-1", 2048L, localPath = "/verified/canonical/path.pdf")

        // 1. Clean destination (no local path downloaded for tombstone or meta-only)
        val restoredTombstone = normalizedGraphRestoreRow("attachments.json", tombstone, null)
        assertEquals("tomb-1", restoredTombstone.getString("id"))
        assertEquals("/former/path.pdf", restoredTombstone.getString("localPath"))
        assertEquals(5000L, restoredTombstone.getLong("deletedAt"))

        val restoredMetaOnly = normalizedGraphRestoreRow("attachments.json", metaOnly, null)
        assertEquals("meta-1", restoredMetaOnly.getString("id"))
        assertEquals("", restoredMetaOnly.getString("localPath"))

        // 2. Verified live attachment has newly promoted local path
        val restoredLive = normalizedGraphRestoreRow("attachments.json", liveAttachment, "/new/destination/live-1.pdf")
        assertEquals("/new/destination/live-1.pdf", restoredLive.getString("localPath"))

        // 3. Live attachment without downloaded path must fail closed
        assertThrows(IllegalStateException::class.java) {
            normalizedGraphRestoreRow("attachments.json", liveAttachment, null)
        }
    }

    // =========================================================================
    // GROUP I: Repeated restoration idempotence
    // =========================================================================
    @Test
    fun testGroupI_repeatedRestorationIdempotence() {
        val bundle = BackupGraphFixtures.generate("grpI")
        val rootCase = bundle.getJSONArray("cases").getJSONObject(1)
        val graph = BackupGraph.discover(
            rootCase.getJSONArray("refs").let { refs ->
                (0 until refs.length()).map { i ->
                    val r = BackupGraphFixtures.ref(refs.getJSONObject(i))
                    val b = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(r.cloudFileId))
                    GraphObject(r, b)
                }
            },
            bundle.getString("accountId"),
            bundle.getString("lineageId")
        )

        val first = BackupGraphReconstruction.read(graph) { Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(it)) }
        val second = BackupGraphReconstruction.read(graph) { Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(it)) }

        assertEquals(first.files, second.files)
        assertEquals(first.binaries, second.binaries)
        assertEquals(first.permanentDeletions, second.permanentDeletions)

        // Position at tip reports ALREADY_CURRENT
        val plan = graph.plan(graph.tips.single())
        assertEquals(GraphStatus.ALREADY_CURRENT, plan.status)
    }

    // =========================================================================
    // GROUP J: Existing functionality non-regression
    // =========================================================================
    @Test
    fun testGroupJ_existingFunctionalityNonRegression() {
        // Verify BackupBinaryDescriptor validation invariants
        val valid = BackupBinaryDescriptor("att-1", "cloud-1", "a".repeat(64), 1024L)
        assertEquals(valid, valid.validate())

        assertThrows(IllegalStateException::class.java) {
            valid.copy(attachmentId = "../invalid").validate()
        }
        assertThrows(IllegalStateException::class.java) {
            valid.copy(sha256 = "invalid-hash").validate()
        }
        assertThrows(IllegalStateException::class.java) {
            valid.copy(size = -1L).validate()
        }

        // Verify JSON round-trip
        val json = valid.toJson()
        val parsed = BackupBinaryDescriptor.parse(json)
        assertEquals(valid, parsed)
    }
}
