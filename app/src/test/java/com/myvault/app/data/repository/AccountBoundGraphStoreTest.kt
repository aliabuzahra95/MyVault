package com.myvault.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID

class AccountBoundGraphStoreTest {
    private val account = "test@example.com"
    private val driveId = "verified-drive-account"
    private class Api(val expectedAccount: String, val driveId: String) : GraphDriveApi {
        val files = linkedMapOf<String, GraphDriveFile>()
        val bytes = mutableMapOf<String, ByteArray>()
        var activeAccount = expectedAccount
        var generated = 0; var downloads = 0; var folderCreates = 0; var objectCreates = 0; var metadataReads = 0
        var crashAfterFolder: Int? = null
        var childrenHook: ((String, List<GraphDriveFile>) -> List<GraphDriveFile>)? = null
        override suspend fun assertAccount(account: String, driveAccountId: String) {
            check(account == activeAccount && account == expectedAccount && driveAccountId == driveId)
        }
        override suspend fun roots(name: String) = files.values.filter { it.name == name && it.mimeType == GraphFolderMime && !it.trashed }
        override suspend fun children(parent: String): List<GraphDriveFile> {
            val rows = files.values.filter { parent in it.parents && !it.trashed }
            return childrenHook?.invoke(parent, rows) ?: rows
        }
        override suspend fun metadata(id: String) = files[id].also { metadataReads++ }
        override suspend fun download(id: String) = ByteArrayInputStream(bytes.getValue(id)).also { downloads++ }
        override suspend fun reserveIds(count: Int) = List(count) { "reserved-${++generated}" }
        override suspend fun createFolder(id: String, name: String, parent: String?) {
            if (files.containsKey(id)) return
            files[id] = GraphDriveFile(id, name, GraphFolderMime, parent?.let(::listOf).orEmpty()); folderCreates++
            if (folderCreates == crashAfterFolder) error("Simulated process death after remote create")
        }
        override suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File) {
            if (files.containsKey(id)) return // Models exclusive create/409, never update.
            val content = source.readBytes(); put(id, parent, content, name, mimeType); objectCreates++
        }
        fun put(id: String, parent: String, content: ByteArray, name: String = id, mime: String = "application/json") {
            bytes[id] = content.copyOf()
            files[id] = GraphDriveFile(id, name, mime, listOf(parent), size = content.size.toLong(), sha256 = IncrementalBackupFormat.sha256(content))
        }
    }
    private suspend fun setup(api: Api, file: File): AccountBoundGraphStore {
        val layout = GraphNamespaceEnrollment(file, api).enroll(account, driveId)
        return AccountBoundGraphStore(GraphWriterContext(account, driveId, layout.lineageId), api, layout)
    }
    private fun root(store: AccountBoundGraphStore, id: String = UUID.randomUUID().toString()): BackupGraphCommit {
        val ref = GraphObjectRef("checkpoint", "a".repeat(64), 10)
        return BackupGraphCommit(driveId, store.context.lineageId, id, "checkpoint", listOf(BackupGraphCapability), emptyList(),
            GraphCheckpoint("full-${ref.sha256}", ref), null)
    }
    private fun Api.commit(store: AccountBoundGraphStore, value: BackupGraphCommit): GraphObject {
        val content = BackupGraphProtocol.encode(value)
        val id = "commit-${value.commitId}"
        put(id, store.layout.folders.getValue("commits"), content)
        return GraphObject(GraphObjectRef(id, IncrementalBackupFormat.sha256(content), content.size.toLong()), content)
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected fail-closed rejection") } catch (_: IllegalStateException) { }
    }
    @Test fun enrollmentSurvivesUncertainFolderCreateWithoutDuplicateNamespace() = runBlocking {
        val directory = Files.createTempDirectory("graph-enrollment-test").toFile()
        try {
            val api = Api(account, driveId).apply { crashAfterFolder = 2 }
            val intent = File(directory, "intent.json")
            rejected { setup(api, intent) }
            assertTrue(intent.exists()); assertEquals(5, api.generated); assertEquals(2, api.folderCreates)
            api.crashAfterFolder = null
            val restarted = setup(api, intent)
            restarted.verifyLayout()
            assertEquals(5, api.generated); assertEquals(5, api.folderCreates)
            assertEquals(1, api.roots(BackupGraphNamespace).size)
            assertEquals(restarted.layout, GraphNamespaceEnrollment(intent, api).load(account, driveId))
            assertTrue(restarted.commits().isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun explicitRemovedTestGraphStartsFreshAndRetainsOldProofAcrossRestart() = runBlocking {
        val directory = Files.createTempDirectory("graph-test-restart").toFile()
        try {
            val api = Api(account, driveId)
            val intent = File(directory, "intent.json")
            val old = setup(api, intent)
            val oldProof = intent.readBytes()
            val oldCommit = api.commit(old, root(old))
            val enrollment = GraphNamespaceEnrollment(intent, api)
            rejected { enrollment.enrollAfterTestGraphRemoval(account, driveId) }
            api.files[old.layout.rootId] = api.files.getValue(old.layout.rootId).copy(trashed = true)
            api.crashAfterFolder = api.folderCreates + 2
            rejected { enrollment.enrollAfterTestGraphRemoval(account, driveId) }
            val next = enrollment.load(account, driveId)!!
            assertNotEquals(old.layout.rootId, next.rootId)
            assertNotEquals(old.layout.lineageId, next.lineageId)
            assertArrayEquals(oldProof, File(directory, "intent.json.${old.layout.lineageId}.abandoned").readBytes())
            assertArrayEquals(oldCommit.bytes, api.bytes.getValue(oldCommit.objectRef.cloudFileId))
            api.crashAfterFolder = null
            assertEquals(next, enrollment.enroll(account, driveId))
            assertEquals(10, api.generated)
            assertEquals(1, api.roots(BackupGraphNamespace).size)
            api.activeAccount = "another@example.com"
            rejected { enrollment.enrollAfterTestGraphRemoval(account, driveId) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun conflictingIntendedFolderFailsWithoutOverwriteOrTrust() = runBlocking {
        val directory = Files.createTempDirectory("graph-enrollment-test").toFile()
        try {
            val api = Api(account, driveId).apply { crashAfterFolder = 1 }
            val intent = File(directory, "intent.json")
            rejected { setup(api, intent) }
            val layout = GraphNamespaceEnrollment(intent, api).load(account, driveId)!!
            val conflicting = GraphDriveFile(layout.folders.getValue("commits"), "unrelated", GraphFolderMime, listOf("another-root"))
            api.files[conflicting.id] = conflicting; api.crashAfterFolder = null
            rejected { setup(api, intent) }
            assertEquals(conflicting, api.files[conflicting.id])
        } finally { directory.deleteRecursively() }
    }
    @Test fun existingRemoteRootCannotBeSilentlyEnrolledAsNewBaseline() = runBlocking {
        val directory = Files.createTempDirectory("graph-enrollment-test").toFile()
        try {
            val api = Api(account, driveId)
            setup(api, File(directory, "first.json"))
            rejected { setup(api, File(directory, "unrelated-client.json")) }
            assertFalse(File(directory, "unrelated-client.json").exists()); assertEquals(5, api.generated)
        } finally { directory.deleteRecursively() }
    }
    @Test fun freshInventoryDetectsSiblingEvenWithVerifiedCommitCache() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val r = root(store); val proof = api.commit(store, r)
            assertEquals(GraphStatus.SINGLE_TIP, BackupGraph.discover(store.commits(), driveId, store.context.lineageId).status)
            assertEquals(1, api.downloads)
            store.commits(); assertEquals(1, api.downloads)
            fun child() = BackupGraphCommit(driveId, store.context.lineageId, UUID.randomUUID().toString(), "delta",
                listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(), listOf(GraphParent(r.commitId, proof.objectRef)),
                r.checkpoint, GraphDelta(UUID.randomUUID().toString(), r.deltaHead, GraphObjectRef(UUID.randomUUID().toString(), "b".repeat(64), 10)))
            api.commit(store, child()); api.commit(store, child())
            val graph = BackupGraph.discover(store.commits(), driveId, store.context.lineageId)
            assertEquals(GraphStatus.FORK, graph.status); assertEquals(2, graph.tips.size); assertEquals(3, api.downloads)
        } finally { directory.deleteRecursively() }
    }
    @Test fun missingDuplicateOrChangedDirectoryFailsClosed() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val child = api.files.getValue(store.layout.folders.getValue("commits"))
            api.files["duplicate"] = child.copy(id = "duplicate")
            rejected { store.commits() }; api.files.remove("duplicate")
            api.files.remove(child.id); rejected { store.commits() }
        } finally { directory.deleteRecursively() }
    }
    @Test fun unrelatedObjectsAndAccountSwitchCannotBeReadOrPublished() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            api.put("real-backup-object", "legacy-folder", "protected".toByteArray())
            rejected { store.read("real-backup-object") }; assertEquals(0, api.downloads)
            api.activeAccount = "other@example.com"
            rejected { store.commits() }; rejected { store.reserveId() }
            val source = File(directory, "payload").apply { writeText("test") }
            rejected { store.create("new", "DELTA", source) }; assertEquals(0, api.objectCreates)
        } finally { directory.deleteRecursively() }
    }
    @Test fun sameIntendedObjectIdIsNeverOverwritten() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val source = File(directory, "payload").apply { writeText("original") }
            store.create("intended-id", "DELTA", source)
            source.writeText("different")
            store.create("intended-id", "DELTA", source)
            assertEquals("original", store.read("intended-id")!!.use { it.readBytes().toString(Charsets.UTF_8) })
            assertEquals(1, api.objectCreates)
            val expected = GraphObjectRef("intended-id", IncrementalBackupFormat.sha256(source.readBytes()), source.length())
            rejected { BackupGraphProtocol.verify(expected, api.bytes.getValue("intended-id")) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun verifiedExistingLayoutIsReadOnlyAndRequiresCorrectDriveIdentity() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            api.commit(store, root(store))
            assertEquals(store.layout, discoverGraphLayout(api, account, driveId))
            assertEquals(5, api.folderCreates); assertEquals(0, api.objectCreates)
            rejected { discoverGraphLayout(api, account, "another-drive-id") }
        } finally { directory.deleteRecursively() }
    }
    @Test fun corruptCommitReadbackAndOversizedBytesFailClosed() = runBlocking {
        val directory = Files.createTempDirectory("graph-transport-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val obj = api.commit(store, root(store))
            api.bytes[obj.objectRef.cloudFileId] = "corrupt".toByteArray()
            rejected { store.commits() }
            api.bytes[obj.objectRef.cloudFileId] = ByteArray(65537)
            rejected { store.commits() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun warmBytesAlwaysRequireFreshMetadataAndRejectMissingOrMovedObjects() = runBlocking {
        val directory = Files.createTempDirectory("graph-cache-test").toFile()
        try {
            val api = Api(account, driveId); val enrolled = setup(api, File(directory, "intent.json"))
            val cache = VerifiedGraphByteCache()
            fun store() = AccountBoundGraphStore(enrolled.context, api, enrolled.layout, cache)
            api.put("delta", enrolled.layout.folders.getValue("deltas"), "verified".toByteArray())
            store().read("delta")!!.use { assertEquals("verified", it.readBytes().toString(Charsets.UTF_8)) }
            val metadataReads = api.metadataReads
            store().read("delta")!!.close()
            assertEquals(1, api.downloads); assertEquals(metadataReads + 1, api.metadataReads)
            val original = api.files.remove("delta")!!
            assertNull(store().read("delta")); assertEquals(1, api.downloads)
            api.files["delta"] = original.copy(parents = listOf("outside"))
            rejected { store().read("delta") }
            api.files["delta"] = original
            api.activeAccount = "switched@example.com"; rejected { store().read("delta") }
            assertEquals(1, api.downloads)
        } finally { directory.deleteRecursively() }
    }

    @Test fun changedDescriptorCannotReturnPreviouslyCachedBytesAndCorruptDownloadNeverSeedsCache() = runBlocking {
        val directory = Files.createTempDirectory("graph-cache-test").toFile()
        try {
            val api = Api(account, driveId); val enrolled = setup(api, File(directory, "intent.json"))
            val cache = VerifiedGraphByteCache(); val store = AccountBoundGraphStore(enrolled.context, api, enrolled.layout, cache)
            val parent = enrolled.layout.folders.getValue("deltas")
            api.put("delta", parent, "original".toByteArray())
            store.read("delta")!!.close()
            api.put("delta", parent, "replacement".toByteArray())
            assertEquals("replacement", store.read("delta")!!.use { it.readBytes().toString(Charsets.UTF_8) })
            assertEquals(2, api.downloads)
            cache.clear(); api.dataCorruptionForTest("delta")
            rejected { store.read("delta") }; rejected { store.read("delta") }
            assertEquals(4, api.downloads)
        } finally { directory.deleteRecursively() }
    }

    private fun Api.dataCorruptionForTest(id: String) { bytes[id] = "corrupt".toByteArray() }

    @Test fun binariesRemainStreamingAndByteCacheLossIsAColdVerifiedRead() = runBlocking {
        val directory = Files.createTempDirectory("graph-cache-test").toFile()
        try {
            val api = Api(account, driveId); val enrolled = setup(api, File(directory, "intent.json"))
            val cache = VerifiedGraphByteCache(); val store = AccountBoundGraphStore(enrolled.context, api, enrolled.layout, cache)
            api.put("binary", enrolled.layout.folders.getValue("binaries"), "binary".toByteArray(), mime = "application/octet-stream")
            repeat(2) { store.read("binary")!!.close() }; assertEquals(2, api.downloads)
            api.put("metadata", enrolled.layout.folders.getValue("checkpoints"), "[]".toByteArray())
            store.read("metadata")!!.close(); store.read("metadata")!!.close(); assertEquals(3, api.downloads)
            cache.clear(); store.read("metadata")!!.close(); assertEquals(4, api.downloads)
        } finally { directory.deleteRecursively() }
    }

    @Test fun discoverySeedsOnlyVerifiedHeadersAndStillListsEveryTime() = runBlocking {
        val directory = Files.createTempDirectory("graph-cache-test").toFile()
        try {
            val api = Api(account, driveId); val enrolled = setup(api, File(directory, "intent.json"))
            val commit = api.commit(enrolled, root(enrolled)); val cache = VerifiedGraphByteCache()
            assertEquals(enrolled.layout, discoverGraphLayout(api, account, driveId, cache))
            val store = AccountBoundGraphStore(enrolled.context, api, enrolled.layout, cache)
            assertEquals(1, api.downloads); store.commits(); assertEquals(1, api.downloads)
            api.files.remove(commit.objectRef.cloudFileId)
            assertTrue(store.commits().isEmpty())
            rejected { discoverGraphLayout(api, account, driveId, cache) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun sameBoundaryFreshlyChecksEveryRequiredReadAndDetectsRemoval() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val bytes = "[]".toByteArray(); val ref = GraphObjectRef("metadata", IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
            api.put(ref.cloudFileId, store.layout.folders.getValue("checkpoints"), bytes)
            val boundary = store.metadataBoundary(); val gets = api.metadataReads
            boundary.readMetadata(ref, "checkpoints")!!.use { assertArrayEquals(bytes, it.readBytes()) }
            assertEquals(gets + 1, api.metadataReads)
            api.files.remove(ref.cloudFileId)
            assertNull(boundary.readMetadata(ref, "checkpoints"))
            assertEquals(gets + 2, api.metadataReads)
            assertNull(store.metadataBoundary().readMetadata(ref, "checkpoints"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun completeCommitInventoryStillRejectsDuplicateMovedAndFailedListings() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            api.commit(store, root(store))
            val folder = store.layout.folders.getValue("commits")
            api.childrenHook = { parent, rows -> if (parent == folder) rows + rows else rows }
            rejected { store.commits() }
            api.childrenHook = { parent, rows -> if (parent == folder) rows.map { it.copy(parents = listOf("outside")) } else rows }
            rejected { store.commits() }
            api.childrenHook = { parent, rows -> if (parent == folder) error("Page failed") else rows }
            rejected { store.commits() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun boundaryExactHashSizeRoleAndAccountCannotBeOverriddenByWarmBytes() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val bytes = "[]".toByteArray(); val ref = GraphObjectRef("metadata", IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
            api.put("metadata", store.layout.folders.getValue("checkpoints"), bytes)
            store.metadataBoundary().readMetadata(ref, "checkpoints")!!.close()
            val original = api.files.getValue("metadata")
            api.files["metadata"] = original.copy(sha256 = "a".repeat(64))
            rejected { store.metadataBoundary().readMetadata(ref, "checkpoints") }
            api.files["metadata"] = original.copy(size = 99)
            rejected { store.metadataBoundary().readMetadata(ref, "checkpoints") }
            api.files["metadata"] = original.copy(parents = listOf(store.layout.folders.getValue("deltas")))
            rejected { store.metadataBoundary().readMetadata(ref, "checkpoints") }
            api.files["metadata"] = original
            val boundary = store.metadataBoundary(); api.activeAccount = "other@example.com"
            rejected { boundary.readMetadata(ref, "checkpoints") }
        } finally { directory.deleteRecursively() }
    }

    @Test fun missingFreshChecksumDownloadsAndStillVerifiesExpectedBytes() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val bytes = "[]".toByteArray(); val ref = GraphObjectRef("metadata", IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
            api.put("metadata", store.layout.folders.getValue("checkpoints"), bytes)
            api.files["metadata"] = api.files.getValue("metadata").copy(sha256 = null, size = null)
            val boundary = store.metadataBoundary(); val gets = api.metadataReads
            boundary.readMetadata(ref, "checkpoints")!!.use { BackupGraphProtocol.verify(ref, it.readBytes()) }
            assertEquals(gets + 1, api.metadataReads); assertEquals(1, api.downloads)
        } finally { directory.deleteRecursively() }
    }

    @Test fun publicationReadbackDoesNotCachePreUploadAbsence() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val store = setup(api, File(directory, "intent.json"))
            val before = store.metadataBoundary()
            val source = File(directory, "source").apply { writeText("[]") }
            val ref = GraphObjectRef("new-object", IncrementalBackupFormat.sha256(source.readBytes()), source.length())
            assertNull(before.readMetadata(ref, "deltas"))
            store.create("new-object", "DELTA", source)
            before.readMetadata(ref, "deltas")!!.use { BackupGraphProtocol.verify(ref, it.readBytes()) }
            val gets = api.metadataReads
            store.read("new-object")!!.use { BackupGraphProtocol.verify(ref, it.readBytes()) }
            assertEquals(gets + 1, api.metadataReads)
            store.metadataBoundary().readMetadata(ref, "deltas")!!.close()
        } finally { directory.deleteRecursively() }
    }

    @Test fun warmDiscoveryReusesBytesOnlyAgainstFreshMembershipAndExactRefs() = runBlocking {
        val directory = Files.createTempDirectory("graph-inventory-test").toFile()
        try {
            val api = Api(account, driveId); val enrolled = setup(api, File(directory, "intent.json"))
            val cache = VerifiedGraphByteCache(); val store = AccountBoundGraphStore(enrolled.context, api, enrolled.layout, cache)
            val proof = api.commit(store, root(store))
            store.commits(); assertEquals(1, api.downloads)
            assertEquals(store.layout, discoverGraphLayout(api, account, driveId, cache)); assertEquals(1, api.downloads)
            val original = api.files.getValue(proof.objectRef.cloudFileId)
            api.files[original.id] = original.copy(sha256 = "a".repeat(64))
            rejected { discoverGraphLayout(api, account, driveId, cache) }; assertEquals(2, api.downloads)
            api.files.remove(original.id)
            rejected { discoverGraphLayout(api, account, driveId, cache) }
        } finally { directory.deleteRecursively() }
    }
}
