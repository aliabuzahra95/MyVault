package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.data.local.dao.readIntent
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Real journal/writer/restore, synthetic two-phone databases and a local immutable store only. */
@RunWith(AndroidJUnit4::class)
class BackupGraphDeletionRoomTest {
    private val restores = BackupGraphRestoreRoomTest()
    private fun folders(f: BackupGraphWriterRoomTest.Fixture) = FolderRepository(f.db,
        f.db.folderDao(), f.db.folderStickyNoteDao(), f.db.noteDao(), f.db.attachmentDao(),
        f.db.blockDao(), f.db.tagDao(), f.db.noteTableDao(), f.db.noteVersionDao(),
        f.db.pdfAnnotationDao(), f.db.pdfReadingProgressDao(), f.db.sourceBacklinkDao(), f.db.knowledgeTagDao())

    private suspend fun tree(f: BackupGraphWriterRoomTest.Fixture, count: Int = 1) {
        for (i in 0 until count) {
            f.db.folderDao().upsertAll(listOf(FolderEntity("folder-$i", if (i == 0) null else "folder-${i - 1}",
                "Disposable", orderIndex = i, isFavourite = false, createdAt = 1, updatedAt = 1)))
            f.note("note-$i")
            f.db.noteDao().upsertAll(listOf(f.db.noteDao().getById("note-$i")!!.copy(folderId = "folder-$i")))
        }
    }

    @Test fun replacementCheckpointDoesNotResurrectDeletedChildrenBeforeFolderDeletion() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a)
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                a.db.noteDao().deleteByIds(listOf("note-0"))
                // Authenticated full replacement represents the note's absence, not a delta omission.
                a.writer().replaceCheckpoint(a.prepared())
                folders(a).deleteFolderTree("folder-0")
                a.writer().publish()
                folders(a).permanentlyDeleteFolderTree("folder-0")
                a.writer().publish()
                b.note("phone-b-only") // Explicit Restore must preserve this new identity.
                val before = a.store.bytes()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertNull(b.db.noteDao().getById("note-0"))
                assertTrue(b.db.folderDao().getAllIncludingDeleted().isEmpty())
                assertNotNull(b.db.noteDao().getById("phone-b-only"))
                assertEquals(before.keys, a.store.bytes().keys)
                for ((id, bytes) in before) assertArrayEquals(bytes, a.store.bytes().getValue(id))
            }
        }
    }

    @Test fun nestedSoftAndPermanentDeletionAcrossSeveralBackupsRestoresIdempotently() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a, 120)
            a.note("keep")
            a.note("n")
            a.binary(4096)
            a.db.openHelper.writableDatabase.execSQL("UPDATE attachments SET noteId='note-119' WHERE id='pdf'")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                folders(a).deleteFolderTree("folder-0")
                a.writer().publish()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals(120, b.db.noteDao().getAllIncludingDeleted().count { it.deletedAt != null })
                folders(a).permanentlyDeleteFolderTree("folder-0")
                a.writer().publish()
                b.note("new-local")
                b.db.noteDao().upsertAll(listOf(b.db.noteDao().getById("new-local")!!.copy(folderId = "folder-119")))
                b.binary(8192, "new-local-pdf")
                val localPdf = b.db.attachmentDao().getByIdIncludingDeleted("new-local-pdf")!!
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertTrue(b.db.folderDao().getAllIncludingDeleted().isEmpty())
                assertEquals(setOf("keep", "n", "new-local"), b.db.noteDao().getAllIncludingDeleted().map { it.id }.toSet())
                assertNull(b.db.noteDao().getById("new-local")!!.folderId)
                assertNull(b.db.attachmentDao().getByIdIncludingDeleted("pdf"))
                assertEquals(localPdf, b.db.attachmentDao().getByIdIncludingDeleted("new-local-pdf"))
                assertEquals(8192L, File(localPdf.localPath).length())
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals(setOf("keep", "n", "new-local"), b.db.noteDao().getAllIncludingDeleted().map { it.id }.toSet())
            }
        }
    }

    @Test fun replacementCheckpointWithoutPendingLocalChangesStillAppliesKnownDeletions() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a, 3)
            a.note("keep")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                folders(a).permanentlyDeleteFolderTree("folder-0")
                a.writer().replaceCheckpoint(a.prepared())
                assertTrue(b.pending().isEmpty())
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertTrue(b.db.folderDao().getAllIncludingDeleted().isEmpty())
                assertEquals(listOf("keep"), b.db.noteDao().getAllIncludingDeleted().map { it.id })
                assertEquals(GraphRestoreStatus.ALREADY_CURRENT, restores.restorer(b).restore().status)
            }
        }
    }

    @Test fun interruptedDeletionRollsBackAndLegacyFrozenSnapshotIsRebuiltSafely() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a)
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val oldApplied = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                val oldNote = b.db.noteDao().getById("note-0")!!.toJson()
                folders(a).permanentlyDeleteFolderTree("folder-0")
                a.writer().replaceCheckpoint(a.prepared())
                b.note("new-local")
                expectFailure {
                    restores.restorer(b, boundary = { if (it == "ROOM_OPERATIONS_APPLIED") error("Disposable interruption") }).restore()
                }
                assertEquals(oldApplied, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("note-0"))
                assertEquals(1, b.db.folderDao().getAllIncludingDeleted().size)
                val intent = b.db.backupGraphRestoreDao().unfinished().single()
                // Emulate a durable plan frozen by the pre-fix build: obsolete note retained.
                val frozen = JSONObject(intent.frozenChangesJson).apply { remove("snapshotFoldVersion") }
                val changes = BackupGraphIntentCodec.array(frozen.getJSONArray("changes")).filterNot {
                    it.getString("file") == "notes.json" && it.getJSONArray("key").getString(0) == "note-0"
                }
                frozen.put("changes", JSONArray(changes + BackupRecordChange("notes.json", listOf("note-0"), oldNote).toJson()))
                val text = frozen.toString()
                b.db.openHelper.writableDatabase.execSQL("UPDATE backup_graph_restores SET frozenChangesJson=?, frozenChangesSha256=? WHERE operationId=?",
                    arrayOf(text, IncrementalBackupFormat.sha256(text.toByteArray()), intent.operationId))
                b.reopen()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertNull(b.db.noteDao().getById("note-0"))
                assertNotNull(b.db.noteDao().getById("new-local"))
                assertTrue(b.db.backupGraphRestoreDao().unfinished().isEmpty())
                assertEquals("SUPERSEDED", b.db.backupGraphRestoreDao().readIntent(b.ctx.accountScope, intent.operationId)!!.settingsPhase)
            }
        }
    }

    @Test fun genuinelyMissingRemoteParentIsRejectedEvenIfPhoneBHasThatLocalId() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a)
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val original = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                b.db.folderDao().upsertAll(listOf(FolderEntity("missing-remote", null, "Local-only parent",
                    orderIndex = 0, isFavourite = false, createdAt = 1, updatedAt = 1)))
                append(a, listOf(BackupRecordChange("notes.json", listOf("note-0"),
                    a.db.noteDao().getById("note-0")!!.copy(folderId = "missing-remote").toJson())))
                val error = expectFailure { restores.restorer(b).restore() }
                assertTrue(error is BackupGraphRestoreFailure)
                assertTrue((error as BackupGraphRestoreFailure).diagnostic.contains("folders.json:[missing-remote]"))
                assertTrue(error.diagnostic.contains("source=verified_snapshot"))
                assertEquals(original, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertEquals("folder-0", b.db.noteDao().getById("note-0")!!.folderId)
            }
        }
    }

    @Test fun unavailableAuthenticatedMetadataDoesNotApplyDeletionOrAdvanceCursor() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a)
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val original = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                folders(a).permanentlyDeleteFolderTree("folder-0")
                a.writer().publish()
                val commit = a.graph(a.store.commits()).commits.getValue(a.binding().commitId)
                val missing = commit.delta!!.objectRef.cloudFileId
                val unavailable = object : DisposableGraphObjectStore by a.store {
                    override suspend fun read(objectId: String) = if (objectId == missing) null else a.store.read(objectId)
                    override suspend fun metadataBoundary(): DisposableGraphObjectStore = this
                    override suspend fun readMetadata(ref: GraphObjectRef, folder: String) = read(ref.cloudFileId)
                }
                val error = expectFailure { restores.restorer(b, unavailable).restore() }
                assertTrue(error is BackupGraphRestoreFailure)
                assertTrue((error as BackupGraphRestoreFailure).diagnostic.contains("commit=${commit.commitId}"))
                assertEquals(original, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("note-0"))
                assertEquals(1, b.db.folderDao().getAllIncludingDeleted().size)
            }
        }
    }

    @Test fun deleteOnlyDeltaCannotLeaveAnUnchangedRemoteChildWithAMissingParent() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a)
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val original = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                append(a, listOf(BackupRecordChange("folders.json", listOf("folder-0"))))
                val error = expectFailure { restores.restorer(b).restore() }
                assertTrue(error is BackupGraphRestoreFailure)
                assertTrue((error as BackupGraphRestoreFailure).diagnostic.contains("remote_deleted=true"))
                assertEquals(original, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("note-0"))
                assertEquals(1, b.db.folderDao().getAllIncludingDeleted().size)
            }
        }
    }

    @Test fun missingLaterDeltaCannotPartiallyApplyAnEarlierDeletion() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            tree(a, 2)
            a.note("keep")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val original = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                folders(a).permanentlyDeleteFolderTree("folder-1")
                a.writer().publish()
                a.edit("Later change", "keep")
                a.writer().publish()
                val tip = a.graph(a.store.commits()).commits.getValue(a.binding().commitId)
                val unavailable = object : DisposableGraphObjectStore by a.store {
                    override suspend fun read(objectId: String) = if (objectId == tip.delta!!.objectRef.cloudFileId) null else a.store.read(objectId)
                    override suspend fun metadataBoundary(): DisposableGraphObjectStore = this
                    override suspend fun readMetadata(ref: GraphObjectRef, folder: String) = read(ref.cloudFileId)
                }
                expectFailure { restores.restorer(b, unavailable).restore() }
                assertEquals(original, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("note-1"))
                assertEquals(2, b.db.folderDao().getAllIncludingDeleted().size)
                assertEquals("Original العربية", b.db.noteDao().getById("keep")!!.bodyPlainText)
            }
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit): IllegalStateException {
        try { block() } catch (error: IllegalStateException) { return error }
        throw AssertionError("Expected safe failure")
    }

    private suspend fun append(source: BackupGraphWriterRoomTest.Fixture, changes: List<BackupRecordChange>) {
        val parent = source.store.commits().single { BackupGraphProtocol.parse(it).commitId == source.binding().commitId }
        val p = BackupGraphProtocol.parse(parent)
        val deltaId = UUID.randomUUID().toString()
        val delta = IncrementalBackupFormat.createDelta(p.checkpoint.checkpointId, p.deltaHead, deltaId, changes, emptyList()).toString().toByteArray()
        val file = File(source.root, "invalid-graph-fixture").apply { writeBytes(delta) }
        val id = source.store.reserveId()
        source.store.create(id, "DELTA", file)
        val ref = GraphObjectRef(id, IncrementalBackupFormat.sha256(delta), delta.size.toLong())
        val commit = BackupGraphCommit(p.accountId, p.lineageId, UUID.randomUUID().toString(), "delta",
            (p.requiredReaders + "checkpoint-delta-v1" + BackupBinaryReaderCapability).distinct().sorted(),
            listOf(GraphParent(p.commitId, parent.objectRef)), p.checkpoint, GraphDelta(deltaId, p.deltaHead, ref))
        file.writeBytes(BackupGraphProtocol.encode(commit))
        source.store.create(source.store.reserveId(), "COMMIT", file)
    }
}
