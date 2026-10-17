package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.BackupGraphBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID

class BackupOptimizationFixesTest {

    private val account = "test@example.com"
    private val driveId = "verified-drive-account"

    private class MockApi(val expectedAccount: String, val driveId: String) : GraphDriveApi {
        val files = linkedMapOf<String, GraphDriveFile>()
        val bytes = mutableMapOf<String, ByteArray>()
        var downloads = 0
        var folderCreates = 0
        var objectCreates = 0

        override suspend fun assertAccount(account: String, driveAccountId: String) {
            check(account == expectedAccount && driveAccountId == driveId)
        }
        override suspend fun roots(name: String) = files.values.filter { it.name == name && it.mimeType == GraphFolderMime && !it.trashed }
        override suspend fun children(parent: String): List<GraphDriveFile> = files.values.filter { parent in it.parents && !it.trashed }
        override suspend fun metadata(id: String) = files[id]
        override suspend fun download(id: String) = ByteArrayInputStream(bytes.getValue(id)).also { downloads++ }
        override suspend fun reserveIds(count: Int) = List(count) { "res-${System.nanoTime()}-$it" }
        override suspend fun createFolder(id: String, name: String, parent: String?) {
            files[id] = GraphDriveFile(id, name, GraphFolderMime, parent?.let(::listOf).orEmpty())
            folderCreates++
        }
        override suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File) {
            val content = source.readBytes()
            bytes[id] = content
            files[id] = GraphDriveFile(id, name, mimeType, listOf(parent), size = content.size.toLong(), sha256 = IncrementalBackupFormat.sha256(content))
            objectCreates++
        }
        fun put(id: String, parent: String, content: ByteArray, name: String = id, mime: String = "application/json") {
            bytes[id] = content.copyOf()
            files[id] = GraphDriveFile(id, name, mime, listOf(parent), size = content.size.toLong(), sha256 = IncrementalBackupFormat.sha256(content))
        }
    }

    private suspend fun setupStore(api: MockApi, intentFile: File): AccountBoundGraphStore {
        val layout = GraphNamespaceEnrollment(intentFile, api).enroll(account, driveId)
        return AccountBoundGraphStore(GraphWriterContext(account, driveId, layout.lineageId), api, layout)
    }

    private fun commitObject(store: AccountBoundGraphStore, commit: BackupGraphCommit, api: MockApi): GraphObject {
        val content = BackupGraphProtocol.encode(commit)
        val fileId = "commit-${commit.commitId}"
        api.put(fileId, store.layout.folders.getValue("commits"), content)
        return GraphObject(GraphObjectRef(fileId, IncrementalBackupFormat.sha256(content), content.size.toLong()), content)
    }

    @Test
    fun verifiedCommitGraphCacheStoresAndClearsCorrectly() {
        VerifiedCommitGraphCache.clear()
        val ref = GraphObjectRef("test-file-1", "a".repeat(64), 100)
        val graphObject = GraphObject(ref, "test-bytes".toByteArray())

        assertNull(VerifiedCommitGraphCache.getCommitObject("test-file-1"))
        VerifiedCommitGraphCache.putCommitObject("test-file-1", graphObject)
        assertEquals(graphObject, VerifiedCommitGraphCache.getCommitObject("test-file-1"))

        val checkpoint = GraphCheckpoint("cp-1", GraphObjectRef("cp-ref", "b".repeat(64), 50))
        val commit = BackupGraphCommit(
            driveId, "lineage-1", "c1", "checkpoint",
            listOf(BackupGraphCapability), emptyList(), checkpoint, null
        )
        assertNull(VerifiedCommitGraphCache.getParsedCommit("test-file-1"))
        VerifiedCommitGraphCache.putParsedCommit("test-file-1", commit)
        assertEquals(commit, VerifiedCommitGraphCache.getParsedCommit("test-file-1"))

        val known = VerifiedCommitGraphCache.getKnownCommitFileIds("user@example.com", "lineage-1")
        assertTrue(known.isEmpty())

        VerifiedCommitGraphCache.addKnownCommitFileId("user@example.com", "lineage-1", "file-x")
        assertTrue(VerifiedCommitGraphCache.getKnownCommitFileIds("user@example.com", "lineage-1").contains("file-x"))

        VerifiedCommitGraphCache.clear()
        assertNull(VerifiedCommitGraphCache.getCommitObject("test-file-1"))
        assertNull(VerifiedCommitGraphCache.getParsedCommit("test-file-1"))
        assertTrue(VerifiedCommitGraphCache.getKnownCommitFileIds("user@example.com", "lineage-1").isEmpty())
    }

    @Test
    fun multiCommitLineageRecordsAllAncestorsAndEnablesFastPathValidation() = runBlocking {
        VerifiedCommitGraphCache.clear()
        val directory = Files.createTempDirectory("multi-commit-test").toFile()
        try {
            val api = MockApi(account, driveId)
            val store = setupStore(api, File(directory, "intent.json"))

            val cpSha = "c".repeat(64)
            val cpRef = GraphObjectRef("cp-obj", cpSha, 128)
            val rootId = UUID.randomUUID().toString()
            val rootCommit = BackupGraphCommit(
                driveId, store.context.lineageId, rootId, "checkpoint",
                listOf(BackupGraphCapability), emptyList(),
                GraphCheckpoint("full-$cpSha", cpRef), null
            )
            val rootObj = commitObject(store, rootCommit, api)

            val delta1Id = UUID.randomUUID().toString()
            val d1Id = UUID.randomUUID().toString()
            val delta1 = BackupGraphCommit(
                driveId, store.context.lineageId, delta1Id, "delta",
                listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(),
                listOf(GraphParent(rootId, rootObj.objectRef)),
                GraphCheckpoint("full-$cpSha", cpRef),
                GraphDelta(d1Id, "full-$cpSha", GraphObjectRef("d1-obj", "d".repeat(64), 64))
            )
            val delta1Obj = commitObject(store, delta1, api)

            val delta2Id = UUID.randomUUID().toString()
            val d2Id = UUID.randomUUID().toString()
            val delta2 = BackupGraphCommit(
                driveId, store.context.lineageId, delta2Id, "delta",
                listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(),
                listOf(GraphParent(delta1Id, delta1Obj.objectRef)),
                GraphCheckpoint("full-$cpSha", cpRef),
                GraphDelta(d2Id, d1Id, GraphObjectRef("d2-obj", "e".repeat(64), 64))
            )
            val delta2Obj = commitObject(store, delta2, api)

            // Step 1: verify commitFiles() is lightweight and does zero downloads
            val commitFiles = store.commitFiles()
            assertEquals(3, commitFiles.size)
            assertEquals(0, api.downloads)

            // Step 2: verify discovery and lineage recording
            val commits = store.commits()
            assertEquals(3, commits.size)
            assertEquals(3, api.downloads)

            // Second commits() call reuses cache, zero extra downloads
            store.commits()
            assertEquals(3, api.downloads)

            val graph = BackupGraph.discover(commits, driveId, store.context.lineageId)
            assertEquals(GraphStatus.SINGLE_TIP, graph.status)

            VerifiedCommitGraphCache.putParsedCommit(rootObj.objectRef.cloudFileId, rootCommit)
            VerifiedCommitGraphCache.putParsedCommit(delta1Obj.objectRef.cloudFileId, delta1)
            VerifiedCommitGraphCache.putParsedCommit(delta2Obj.objectRef.cloudFileId, delta2)

            val binding = BackupGraphBinding(
                accountScope = account,
                lineageId = store.context.lineageId,
                driveAccountId = driveId,
                commitId = delta2Id,
                commitFileId = delta2Obj.objectRef.cloudFileId,
                commitSha256 = delta2Obj.objectRef.sha256,
                commitSize = delta2Obj.objectRef.size,
                checkpointId = "full-$cpSha",
                checkpointFileId = "cp-obj",
                checkpointSha256 = cpSha,
                checkpointSize = 128,
                deltaHeadId = d2Id,
                originEpoch = 1
            )

            VerifiedCommitGraphCache.recordLineage(account, store.context.lineageId, graph, delta2Id)
            val knownIds = VerifiedCommitGraphCache.getKnownCommitFileIds(account, store.context.lineageId, binding)

            // Lineage must contain all 3 commit file IDs
            assertTrue(knownIds.contains(rootObj.objectRef.cloudFileId))
            assertTrue(knownIds.contains(delta1Obj.objectRef.cloudFileId))
            assertTrue(knownIds.contains(delta2Obj.objectRef.cloudFileId))

            // Check fast-path condition evaluation
            val remoteFileIds = commitFiles.map { it.id }.toSet()
            val noUnknownRemoteCommits = remoteFileIds.contains(binding.commitFileId) &&
                remoteFileIds.all { it in knownIds }
            assertTrue("Multi-commit graph with verified lineage must satisfy noUnknownRemoteCommits", noUnknownRemoteCommits)

            // Now simulate another device pushing a new commit (Phone B)
            val delta3Id = UUID.randomUUID().toString()
            val d3Id = UUID.randomUUID().toString()
            val delta3 = BackupGraphCommit(
                driveId, store.context.lineageId, delta3Id, "delta",
                listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(),
                listOf(GraphParent(delta2Id, delta2Obj.objectRef)),
                GraphCheckpoint("full-$cpSha", cpRef),
                GraphDelta(d3Id, d2Id, GraphObjectRef("d3-obj", "f".repeat(64), 64))
            )
            val delta3Obj = commitObject(store, delta3, api)

            val updatedRemoteFiles = store.commitFiles().map { it.id }.toSet()
            val unknownRefusal = updatedRemoteFiles.contains(binding.commitFileId) &&
                updatedRemoteFiles.all { it in knownIds }

            // MUST be false because delta3Obj is not in knownIds!
            assertFalse("Unknown remote commit from another device must reject the fast path", unknownRefusal)
        } finally {
            directory.deleteRecursively()
            VerifiedCommitGraphCache.clear()
        }
    }

    @Test
    fun batchMutexHoldingEnsuresSequentialIntegrity() = runBlocking {
        val mutex = Mutex()
        var batchRunning = false
        var intermediateConflict = false

        kotlinx.coroutines.coroutineScope {
            // Simulate batch import holding mutex
            val batchJob = async {
                mutex.lock()
                try {
                    batchRunning = true
                    kotlinx.coroutines.delay(50)
                    // Mutex is held throughout entire batch
                } finally {
                    batchRunning = false
                    mutex.unlock()
                }
            }

            val concurrentJob = async {
                kotlinx.coroutines.delay(10)
                // Attempt to acquire lock while batch is running
                if (mutex.isLocked) {
                    intermediateConflict = true
                }
                mutex.lock()
                try {
                    // Must only enter after batch completes
                    assertFalse("Concurrent operation must not run while batch is in progress", batchRunning)
                } finally {
                    mutex.unlock()
                }
            }

            batchJob.await()
            concurrentJob.await()
        }
        assertTrue("Mutex was observed locked during batch execution", intermediateConflict)
    }

    @Test
    fun restoreCanonicalPromotionPathFormatting() {
        val filesDir = File("/data/user/0/com.myvault.app/files")
        val noteId = "note-uuid-123"
        val folderId = "folder-uuid-456"

        // Note attachment
        val noteTargetDir = File(filesDir, "attachments/$noteId")
        val cleanNoteFileName = sanitizeAttachmentFileName("clip:1?.png").ifBlank { "attachment-att1" }
        val noteTarget = File(noteTargetDir, "att1_$cleanNoteFileName")
        assertEquals("/data/user/0/com.myvault.app/files/attachments/note-uuid-123/att1_clip_1_.png", noteTarget.path)

        // Library attachment in folder
        val libTargetDir = File(filesDir, "library/$folderId")
        val cleanLibFileName = sanitizeAttachmentFileName("Sahih Bukhari.pdf")
        val libTarget = File(libTargetDir, "att2_$cleanLibFileName")
        assertEquals("/data/user/0/com.myvault.app/files/library/folder-uuid-456/att2_Sahih Bukhari.pdf", libTarget.path)

        // Library root attachment
        val rootTargetDir = File(filesDir, "library/root")
        val cleanRootFileName = sanitizeAttachmentFileName("Root Book.pdf")
        val rootTarget = File(rootTargetDir, "att3_$cleanRootFileName")
        assertEquals("/data/user/0/com.myvault.app/files/library/root/att3_Root Book.pdf", rootTarget.path)
    }

    @Test
    fun restoreCrashSafePromotionMovesWithoutRehashing() {
        val tempDir = Files.createTempDirectory("restore-promo-test").toFile()
        try {
            val stagingDir = File(tempDir, "restore-staging").apply { mkdirs() }
            val targetDir = File(tempDir, "attachments/note-1").apply { mkdirs() }

            val staged = File(stagingDir, "obj-1.verified")
            val payload = "Authoritative PDF Content 12345".toByteArray()
            staged.writeBytes(payload)

            val destination = File(targetDir, "att1_document.pdf")

            // Promotion logic: check length without rehashing
            assertEquals(payload.size.toLong(), staged.length())
            assertTrue(staged.renameTo(destination))

            assertFalse(staged.exists())
            assertTrue(destination.exists())
            assertEquals(payload.size.toLong(), destination.length())
            assertArrayEquals(payload, destination.readBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun persistentCacheSurvivesColdStartAndEnablesInstantFastPath() {
        val tempDir = Files.createTempDirectory("cache-persist-test").toFile()
        val cacheFile = File(tempDir, "graph_commit_cache.json")
        try {
            VerifiedCommitGraphCache.initFile(cacheFile)

            val cpSha = "a".repeat(64)
            val cpRef = GraphObjectRef("cp-file-1", cpSha, 256)
            val rootId = UUID.randomUUID().toString()
            val rootCommit = BackupGraphCommit(
                driveId, "lin-persist-1", rootId, "checkpoint",
                listOf(BackupGraphCapability), emptyList(),
                GraphCheckpoint("full-$cpSha", cpRef), null
            )
            val rootBytes = BackupGraphProtocol.encode(rootCommit)
            val rootRef = GraphObjectRef("commit-file-root", IncrementalBackupFormat.sha256(rootBytes), rootBytes.size.toLong())
            val rootObj = GraphObject(rootRef, rootBytes)

            val deltaId = UUID.randomUUID().toString()
            val d1Id = UUID.randomUUID().toString()
            val deltaCommit = BackupGraphCommit(
                driveId, "lin-persist-1", deltaId, "delta",
                listOf(BackupGraphCapability, "checkpoint-delta-v1").sorted(),
                listOf(GraphParent(rootId, rootRef)),
                GraphCheckpoint("full-$cpSha", cpRef),
                GraphDelta(d1Id, "full-$cpSha", GraphObjectRef("delta-obj-1", "b".repeat(64), 64))
            )
            val deltaBytes = BackupGraphProtocol.encode(deltaCommit)
            val deltaRef = GraphObjectRef("commit-file-delta", IncrementalBackupFormat.sha256(deltaBytes), deltaBytes.size.toLong())
            val deltaObj = GraphObject(deltaRef, deltaBytes)

            VerifiedCommitGraphCache.putCommitObject(rootRef.cloudFileId, rootObj)
            VerifiedCommitGraphCache.putParsedCommit(rootRef.cloudFileId, rootCommit)
            VerifiedCommitGraphCache.addKnownCommitFileId(account, "lin-persist-1", rootRef.cloudFileId)

            VerifiedCommitGraphCache.putCommitObject(deltaRef.cloudFileId, deltaObj)
            VerifiedCommitGraphCache.putParsedCommit(deltaRef.cloudFileId, deltaCommit)
            VerifiedCommitGraphCache.addKnownCommitFileId(account, "lin-persist-1", deltaRef.cloudFileId)

            // Cache file must now exist on disk and be non-empty
            assertTrue("Disk cache file must be created", cacheFile.exists())
            assertTrue("Disk cache file must have content", cacheFile.length() > 0)

            // SIMULATE PROCESS DEATH: Wipe in-memory structures without deleting the disk file
            VerifiedCommitGraphCache.clear(wipeDisk = false)
            assertNull(VerifiedCommitGraphCache.getCommitObject(rootRef.cloudFileId))
            assertNull(VerifiedCommitGraphCache.getParsedCommit(rootRef.cloudFileId))
            assertTrue(VerifiedCommitGraphCache.getKnownCommitFileIds(account, "lin-persist-1").isEmpty())

            // SIMULATE COLD START: Initialize from disk cache file
            VerifiedCommitGraphCache.initFile(cacheFile)

            // Assert everything was restored into memory on cold start
            val restoredKnownIds = VerifiedCommitGraphCache.getKnownCommitFileIds(account, "lin-persist-1")
            assertEquals(2, restoredKnownIds.size)
            assertTrue(restoredKnownIds.contains(rootRef.cloudFileId))
            assertTrue(restoredKnownIds.contains(deltaRef.cloudFileId))

            val restoredRootObj = VerifiedCommitGraphCache.getCommitObject(rootRef.cloudFileId)
            assertNotNull("Root commit object must be restored from disk", restoredRootObj)
            assertEquals(rootRef, restoredRootObj!!.objectRef)
            assertArrayEquals(rootBytes, restoredRootObj.bytes)

            val restoredDeltaCommit = VerifiedCommitGraphCache.getParsedCommit(deltaRef.cloudFileId)
            assertNotNull("Delta commit must be restored from disk", restoredDeltaCommit)
            assertEquals(deltaId, restoredDeltaCommit!!.commitId)

            // Verify cold-start sub-second fast path check
            val remoteCommitFileIds = setOf(rootRef.cloudFileId, deltaRef.cloudFileId)
            val fastPathSatisfiedOnColdStart = remoteCommitFileIds.all { it in restoredKnownIds }
            assertTrue("Cold start must instantly satisfy fast path without network re-scans", fastPathSatisfiedOnColdStart)

            // Verify fork / new remote commit detection on cold start
            val foreignRemoteFileIds = setOf(rootRef.cloudFileId, deltaRef.cloudFileId, "unknown-remote-from-phone-b")
            val foreignFastPathRefusal = foreignRemoteFileIds.all { it in restoredKnownIds }
            assertFalse("Cold start must refuse fast path when foreign commit exists", foreignFastPathRefusal)
        } finally {
            VerifiedCommitGraphCache.clear(wipeDisk = true)
            tempDir.deleteRecursively()
        }
    }
}
