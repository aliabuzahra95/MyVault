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
        var generated = 0; var downloads = 0; var folderCreates = 0; var objectCreates = 0
        var crashAfterFolder: Int? = null
        override suspend fun assertAccount(account: String, driveAccountId: String) {
            check(account == activeAccount && account == expectedAccount && driveAccountId == driveId)
        }
        override suspend fun roots(name: String) = files.values.filter { it.name == name && it.mimeType == GraphFolderMime && !it.trashed }
        override suspend fun children(parent: String) = files.values.filter { parent in it.parents && !it.trashed }
        override suspend fun metadata(id: String) = files[id]
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
}
