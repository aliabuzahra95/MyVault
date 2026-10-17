package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.data.local.dao.readIntent
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual writer, authenticated fold, journal and restore; two disposable Room databases. */
@RunWith(AndroidJUnit4::class)
class BackupGraphIntegrityRoomTest {
    private val restores = BackupGraphRestoreRoomTest()
    private fun folders(f: BackupGraphWriterRoomTest.Fixture) = FolderRepository(f.db,
        f.db.folderDao(), f.db.folderStickyNoteDao(), f.db.noteDao(), f.db.attachmentDao(),
        f.db.blockDao(), f.db.tagDao(), f.db.noteTableDao(), f.db.noteVersionDao(),
        f.db.pdfAnnotationDao(), f.db.pdfReadingProgressDao(), f.db.sourceBacklinkDao(), f.db.knowledgeTagDao())
    private fun folder(id: String, parent: String? = null, mode: String = "study") = FolderEntity(
        id, parent, "Disposable", orderIndex = 0, isFavourite = false, mode = mode, createdAt = 1, updatedAt = 1)

    private suspend fun fails(block: suspend () -> Unit): IllegalStateException {
        try { block() } catch (e: IllegalStateException) { return e }
        throw AssertionError("Expected safe failure")
    }

    @Test fun oneHundredExplicitNoteDeletionsApplyAndNewPhoneBNoteSurvives() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            repeat(100) { a.note("n-$it") }; a.note("keep")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore(); b.note("phone-b-new")
                b.edit("Stale local edit", "keep"); a.edit("Authoritative", "keep")
                a.db.noteDao().deleteByIds((0 until 100).map { "n-$it" })
                a.writer().publish()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals(setOf("keep", "phone-b-new"), b.db.noteDao().getAllIncludingDeleted().map { it.id }.toSet())
                assertEquals("Authoritative", b.db.noteDao().getById("keep")!!.bodyPlainText)
            }
        }
    }

    @Test fun courseFolderSoftAndPermanentDeletionIncludesStandalonePdfsAndNestedNotes() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.db.folderDao().upsertAll(listOf(folder("root", mode = "course:c"), folder("child", "root")))
            a.db.courseDao().upsertCourse(CourseEntity("c", "Disposable", "root", createdAt = 1, updatedAt = 1))
            a.note("n"); a.note("nested-note")
            a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = "child"),
                a.db.noteDao().getById("nested-note")!!.copy(parentNoteId = "n")))
            repeat(6) { a.binary(4096, "pdf-$it") }
            a.db.openHelper.writableDatabase.execSQL("UPDATE attachments SET noteId='',libraryFolderId='root'")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                folders(a).deleteFolderTree("root"); a.writer().publish()
                assertEquals(6, a.db.attachmentDao().getAllIncludingDeleted().count { it.deletedAt != null })
                assertNotNull(a.db.noteDao().getAllIncludingDeleted().single { it.id == "nested-note" }.deletedAt)
                folders(a).restoreFolderTree("root"); a.writer().publish()
                assertTrue(a.db.attachmentDao().getAllIncludingDeleted().all { it.deletedAt == null })
                folders(a).deleteFolderTree("root"); a.writer().publish()
                folders(a).permanentlyDeleteFolderTree("root"); a.writer().publish()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertTrue(b.db.attachmentDao().getAllIncludingDeleted().isEmpty())
                assertTrue(b.db.noteDao().getAllIncludingDeleted().isEmpty())
                assertTrue(b.db.folderDao().getAllIncludingDeleted().isEmpty())
                assertNull(b.db.courseDao().getAllCourses().single().rootFolderId)
            }
        }
    }

    @Test fun movedNoteDoesNotRequireOldDeletedParentAndDeltaOmissionDoesNotDeleteNewParent() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.db.folderDao().upsertAll(listOf(folder("old"), folder("new")))
            a.note("n"); a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = "old")))
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = "new")))
                folders(a).permanentlyDeleteFolderTree("old"); a.writer().publish()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals("new", b.db.noteDao().getById("n")!!.folderId)
                assertEquals(listOf("new"), b.db.folderDao().getAllIncludingDeleted().map { it.id })
            }
        }
    }

    @Test fun multipleCheckpointGenerationsAndHundredsOfDeletesFoldToFinalSnapshot() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            repeat(320) { a.note("n-$it") }; a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                repeat(4) { round ->
                    a.db.noteDao().deleteByIds((round * 80 until (round + 1) * 80).map { "n-$it" })
                    a.writer().publish(); a.writer().replaceCheckpoint(a.prepared())
                    a.note("new-$round"); a.writer().publish()
                }
                b.note("phone-b-new")
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals((0..3).map { "new-$it" }.toSet() + "phone-b-new",
                    b.db.noteDao().getAllIncludingDeleted().map { it.id }.toSet())
                val position = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                restores.restorer(b).restore()
                assertEquals(position!!.commitId, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)!!.commitId)
                assertEquals(5, b.db.noteDao().getAllIncludingDeleted().size)
            }
        }
    }

    @Test fun writerRejectsInvalidRootAndDeleteOnlyDeltaBeforeCommitAndCanRetryCorrectedData() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.note()
            val prepared = a.prepared()
            val source = prepared.objects.single { it.backupEntry == "notes.json" }
            val invalid = org.json.JSONArray(source.file.readText()).apply { getJSONObject(0).put("folderId", "missing") }.toString().toByteArray()
            source.file.writeBytes(invalid)
            val invalidPrepared = prepared.copy(objects = prepared.objects.map { if (it == source)
                it.copy(byteSize = invalid.size.toLong(), sha256 = IncrementalBackupFormat.sha256(invalid)) else it })
            val rootFailure = fails { a.writer().createRoot(invalidPrepared) } as BackupGraphRestoreFailure
            assertTrue(rootFailure.diagnostic.contains("field=folderId"))
            assertTrue(a.store.commits().isEmpty()); assertTrue(a.db.backupGraphDao().unfinished(a.ctx.accountScope).isEmpty())
            a.db.folderDao().upsertAll(listOf(folder("missing")))
            a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = "missing")))
            a.writer().createRoot(a.prepared())
            val binding = a.binding()
            a.db.folderDao().deleteByIds(listOf("missing"))
            val failure = fails { a.writer().publish() } as BackupGraphRestoreFailure
            assertTrue(failure.diagnostic.contains("backup_precommit_validation"))
            assertTrue(failure.diagnostic.contains("remote_deleted=true"))
            assertEquals(binding, a.binding()); assertEquals(1, a.store.commits().size)
            assertTrue(a.db.backupGraphDao().unfinished(a.ctx.accountScope).isEmpty())
            assertFalse(a.pending().isEmpty())
            a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = null)))
            a.writer().publish()
            restores.target(a).use { b ->
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertNull(b.db.noteDao().getById("n")!!.folderId)
            }
        }
    }

    @Test fun existingDanglingCourseFolderBackupIsCorrectedForwardWithoutDeletingSixSurvivingPdfs() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.note(); a.db.folderDao().upsertAll(listOf(folder("deleted-course", mode = "course:gone")))
            repeat(6) { a.binary(4096, "pdf-$it") }
            a.db.openHelper.writableDatabase.execSQL("UPDATE attachments SET noteId='',libraryFolderId='deleted-course'")
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                // Reproduce the OLD production writer's immutable invalid publication.
                a.db.folderDao().deleteByIds(listOf("deleted-course"))
                val tip = appendLegacy(a, listOf(BackupRecordChange("folders.json", listOf("deleted-course"))))
                val old = a.binding()
                val c = BackupGraphProtocol.parse(tip)
                a.db.backupGraphDao().putBinding(old.copy(commitId = c.commitId, commitFileId = tip.objectRef.cloudFileId,
                    commitSha256 = tip.objectRef.sha256, commitSize = tip.objectRef.size, deltaHeadId = c.deltaHead))
                a.db.backupJournalDao().trust(a.ctx.accountScope, old.checkpointId, c.deltaHead, old.checkpointFileId, old.checkpointSha256)
                a.db.backupJournalDao().acknowledge(a.ctx.accountScope, a.db.backupJournalDao().clock().generation)
                val priorPosition = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                val failure = fails { restores.restorer(b).restore() } as BackupGraphRestoreFailure
                assertTrue(failure.diagnostic.contains("dependent=attachments.json:[pdf-0]"))
                assertTrue(failure.diagnostic.contains("dependency_commit=${c.commitId}"))
                assertEquals(priorPosition, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                val before = a.store.bytes()
                b.note("new-local"); b.db.noteDao().upsertAll(listOf(b.db.noteDao().getById("new-local")!!.copy(folderId = "deleted-course")))
                b.binary(8192, "new-local-pdf")
                b.db.openHelper.writableDatabase.execSQL("UPDATE attachments SET noteId='',libraryFolderId='deleted-course' WHERE id='new-local-pdf'")
                val correction = a.writer().publish()
                assertEquals(6, correction.metrics.pendingRows)
                assertEquals(0, correction.metrics.binariesCreated)
                for ((id, bytes) in before) assertArrayEquals(bytes, a.store.bytes().getValue(id))
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertEquals(7, b.db.attachmentDao().getAllIncludingDeleted().size)
                assertTrue(b.db.attachmentDao().getAllIncludingDeleted().all { it.libraryFolderId == null })
                assertNull(b.db.noteDao().getById("new-local")!!.folderId)
                assertTrue(b.db.folderDao().getAllIncludingDeleted().isEmpty())
                assertEquals(8192L, File(b.db.attachmentDao().getByIdIncludingDeleted("new-local-pdf")!!.localPath).length())
            }
        }
    }

    @Test fun newRequiredChildrenOfDeletedParentsGetLocalRecoveryIdsAndRemainBackuppable() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.note(); a.binary(4096); a.db.folderDao().upsertAll(listOf(folder("gone")))
            a.db.pdfAnnotationDao().upsertAll(listOf(PdfAnnotationEntity("old-annotation", "pdf", null, 0,
                1f, 1f, 10f, 10f, "yellow", "Existing", createdAt = 1, updatedAt = 1)))
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                b.db.blockDao().upsertAll(listOf(BlockEntity("local-block", "n", "paragraph", "New local content", 0)))
                b.db.noteTableDao().upsertAll(listOf(NoteTableEntity("local-table", "n", 1, 1, "[[\"new\"]]", 0, 1, 1)))
                b.db.folderStickyNoteDao().upsertAll(listOf(FolderStickyNoteEntity("local-sticky", "gone", "New sticky", 1, 1)))
                b.db.pdfAnnotationDao().upsertAll(listOf(PdfAnnotationEntity("local-annotation", "pdf", null, 0,
                    1f, 1f, 10f, 10f, "yellow", "New annotation", createdAt = 1, updatedAt = 1)))
                b.db.pdfAnnotationSegmentDao().upsertAll(listOf(PdfAnnotationSegmentEntity("old-annotation", 4, 0, 2f, 2f, 12f, 12f)))
                a.db.pdfAnnotationDao().deleteByIds(listOf("old-annotation"))
                a.db.attachmentDao().deleteByIds(listOf("pdf")); a.db.noteDao().deleteByIds(listOf("n"))
                a.db.folderDao().deleteByIds(listOf("gone")); a.writer().publish()
                val priorPosition = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                fails { restores.restorer(b, boundary = { if (it == "ROOM_OPERATIONS_APPLIED") error("Disposable recovery interruption") }).restore() }
                assertEquals(priorPosition, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertNotNull(b.db.noteDao().getById("n"))
                assertFalse(b.db.noteDao().getAllIncludingDeleted().any { it.id.startsWith("local-recovery-") })
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(b).restore().status)
                assertNull(b.db.noteDao().getById("n")); assertNull(b.db.attachmentDao().getByIdIncludingDeleted("pdf"))
                val note = b.db.noteDao().getAllIncludingDeleted().single()
                assertTrue(note.id.startsWith("local-recovery-")); assertTrue(note.title.startsWith("Recovered local"))
                val annotation = b.db.pdfAnnotationDao().getAll().single { it.id == "local-annotation" }
                assertEquals("local-annotation", annotation.id)
                assertNotEquals("pdf", annotation.attachmentId)
                val file = b.db.attachmentDao().getByIdIncludingDeleted(annotation.attachmentId)!!
                assertEquals(4096L, File(file.localPath).length())
                val state = BackupGraphRestoreState()
                for (group in BackupRecordKeys.keys) {
                    val keys = BackupRecordKeys.getValue(group)
                    val ids = mutableListOf<List<String>>()
                    b.db.openHelper.readableDatabase.query("SELECT ${keys.joinToString { "`$it`" }} FROM `${backupRecordTable(group)}`").use { cursor ->
                        while (cursor.moveToNext()) ids += keys.indices.map { cursor.getString(it) }
                    }
                    state.apply(ids.map { BackupRecordChange(group, it, b.db.backupCaptureDao().readBackupRecord(group, it)!!) })
                }
                assertTrue(backupReferenceViolations(state).isEmpty())
                val pending = PendingBackupCapture(b.db, b.journal, com.myvault.app.data.preferences.VaultPreferences(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, b.journal)).capture(b.ctx.accountScope)
                assertTrue(pending.records.any { it.group == "notes.json" && it.key == listOf(note.id) })
                assertTrue(pending.records.any { it.group == "pdf_annotations.json" && it.key == listOf("local-annotation") })
                assertTrue(pending.binaries.all { it.status == "VERIFIED" })
                assertTrue(adoptVerifiedRestoredParent(b.db, b.ctx, b.store.commits()))
                b.writer().publish()
                assertEquals(GraphRestoreStatus.APPLIED, restores.restorer(a).restore().status)
                assertNotNull(a.db.noteDao().getById(note.id))
                assertNull(a.db.noteDao().getById("n"))
                assertTrue(a.db.pdfAnnotationDao().getAll().any { it.id == "local-annotation" })
                assertEquals(4, a.db.pdfAnnotationSegmentDao().getAll().single().orderIndex)
                assertTrue(a.db.pdfAnnotationSegmentDao().getAll().single().annotationId.startsWith("local-recovery-"))
            }
        }
    }

    @Test fun legacyNonAuthoritativeDeleteIntentCannotBypassFinalStateValidation() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { a ->
            a.db.folderDao().upsertAll(listOf(folder("parent"))); a.note()
            a.db.noteDao().upsertAll(listOf(a.db.noteDao().getById("n")!!.copy(folderId = "parent")))
            a.writer().createRoot(a.prepared())
            restores.target(a).use { b ->
                restores.restorer(b).restore()
                val position = b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId)
                a.edit("New remote body"); a.writer().publish()
                fails { restores.restorer(b, boundary = { if (it == "INTENT_PERSISTED") error("Disposable process death") }).restore() }
                val intent = b.db.backupGraphRestoreDao().readIntent(b.ctx.accountScope,
                    b.db.backupGraphRestoreDao().unfinishedMetadata().single().operationId)!!
                val frozen = JSONObject(intent.frozenChangesJson).put("authoritativeSnapshot", false)
                    .put("changes", org.json.JSONArray(listOf(BackupRecordChange("folders.json", listOf("parent")).toJson())))
                val text = frozen.toString()
                b.db.openHelper.writableDatabase.execSQL("UPDATE backup_graph_restores SET frozenChangesJson=?,frozenChangesSha256=? WHERE operationId=?",
                    arrayOf(text, IncrementalBackupFormat.sha256(text.toByteArray()), intent.operationId))
                appendLegacy(a, listOf(BackupRecordChange("folders.json", listOf("parent"))))
                assertTrue(b.pending().isEmpty())
                val failure = fails { restores.restorer(b).restore() } as BackupGraphRestoreFailure
                assertTrue(failure.diagnostic.contains("restore_final_state_validation"))
                assertEquals(position, b.db.backupGraphRestoreDao().applied(b.ctx.accountScope, b.ctx.lineageId))
                assertEquals("parent", b.db.noteDao().getById("n")!!.folderId)
                assertEquals("Original العربية", b.db.noteDao().getById("n")!!.bodyPlainText)
            }
        }
    }

    private suspend fun appendLegacy(a: BackupGraphWriterRoomTest.Fixture, changes: List<BackupRecordChange>): GraphObject {
        val parent = a.store.commits().single { BackupGraphProtocol.parse(it).commitId == a.binding().commitId }
        val p = BackupGraphProtocol.parse(parent); val deltaId = UUID.randomUUID().toString()
        val bytes = IncrementalBackupFormat.createDelta(p.checkpoint.checkpointId, p.deltaHead, deltaId, changes, emptyList()).toString().toByteArray()
        val file = File(a.root, "old-writer-fixture").apply { writeBytes(bytes) }
        val id = a.store.reserveId(); a.store.create(id, "DELTA", file)
        val commit = BackupGraphCommit(p.accountId, p.lineageId, UUID.randomUUID().toString(), "delta",
            (p.requiredReaders + "checkpoint-delta-v1" + BackupBinaryReaderCapability).distinct().sorted(),
            listOf(GraphParent(p.commitId, parent.objectRef)), p.checkpoint,
            GraphDelta(deltaId, p.deltaHead, GraphObjectRef(id, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())))
        val raw = BackupGraphProtocol.encode(commit); file.writeBytes(raw)
        val commitId = a.store.reserveId(); a.store.create(commitId, "COMMIT", file)
        return GraphObject(GraphObjectRef(commitId, IncrementalBackupFormat.sha256(raw), raw.size.toLong()), raw)
    }
}
