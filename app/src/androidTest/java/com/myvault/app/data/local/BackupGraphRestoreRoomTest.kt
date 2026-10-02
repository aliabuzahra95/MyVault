package com.myvault.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupGraphRestoreRoomTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    internal class FileSettings(val file: File, initial: JSONObject) : GraphRestoreSettings {
        init { if (!file.exists()) file.writeText(initial.toString()) }
        override suspend fun read() = JSONObject(file.readText())
        override suspend fun apply(value: JSONObject) { file.writeText(value.toString()) }
    }
    internal suspend fun target(source: BackupGraphWriterRoomTest.Fixture): BackupGraphWriterRoomTest.Fixture =
        BackupGraphWriterRoomTest().Fixture(shared=source.store)
    internal suspend fun restorer(target: BackupGraphWriterRoomTest.Fixture, store: DisposableGraphObjectStore = target.publicationStore,
        boundary: suspend (String) -> Unit = {}): InternalBackupGraphRestore {
        val settings = FileSettings(File(target.root,"restore-settings.json"),VaultPreferences(base,target.journal).userPreferences.first().toBackupJson())
        return InternalBackupGraphRestore(target.db,target.journal,base.filesDir,File(target.root,"restored"),store,settings,boundary)
    }
    private suspend fun applied(f: BackupGraphWriterRoomTest.Fixture) = f.db.backupGraphRestoreDao().applied(f.ctx.accountScope,f.ctx.lineageId)
    private suspend fun fails(block: suspend () -> Unit) { var failed=false;try { block() } catch(_: IllegalStateException) { failed=true } catch(_: java.io.IOException) { failed=true };assertTrue(failed) }

    @Test fun oldGraphDeviceAppliesAuthoritativePhoneTransitionExactly() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.note("a", "Old العربية"); source.note("b", "Deleted"); source.note("c", "Unchanged"); source.note("n", "PDF host")
            source.binary(4096)
            source.writer().createRoot(source.prepared())
            target(source).use { receiver ->
                assertEquals(GraphRestoreStatus.APPLIED, restorer(receiver).restore().status)
                val previous = applied(receiver)!!.commitId
                source.edit("Current العربية", "a")
                source.db.noteDao().deleteByIds(listOf("b"))
                source.note("d", "New")
                source.binary(8192)
                source.journal.invalidateBaseline("disposable_phone_authority")
                val (published, diff) = source.writer().transition(source.prepared())
                assertEquals(3, diff.upserts); assertEquals(1, diff.deletes); assertEquals(1, diff.uploads)
                val plan = BackupGraph.discover(source.store.commits(), source.ctx.driveAccountId, source.ctx.lineageId).plan(previous)
                assertEquals(listOf(published.commitId), plan.descendants.map { it.commitId })
                val result = restorer(receiver).restore()
                assertEquals(GraphRestoreStatus.APPLIED, result.status)
                assertEquals(1, result.commitsApplied)
                assertEquals(published.commitId, applied(receiver)!!.commitId)
                assertEquals("Current العربية", receiver.db.noteDao().getById("a")!!.bodyPlainText)
                assertNull(receiver.db.noteDao().getById("b"))
                assertEquals("Unchanged", receiver.db.noteDao().getById("c")!!.bodyPlainText)
                assertEquals("New", receiver.db.noteDao().getById("d")!!.bodyPlainText)
                assertEquals(8192L, receiver.db.attachmentDao().getByIdIncludingDeleted("pdf")!!.sizeBytes)
                assertTrue(receiver.pending().isEmpty())
            }
        }
    }

    @Test fun ownPublicationIsAlreadyCurrentWithoutInventingRestoredPosition() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { f ->
            f.root()
            val binding = f.binding()
            assertNull(applied(f))
            val result = restorer(f).restore()
            assertEquals(GraphRestoreStatus.ALREADY_CURRENT, result.status)
            assertEquals(0, result.rowsWritten)
            assertEquals(0, result.binariesDownloaded)
            assertNull(applied(f))
            assertEquals(binding, f.binding())
            f.edit("Unbacked local edit")
            val pending = f.pending()
            assertEquals(GraphRestoreStatus.ALREADY_CURRENT, restorer(f).restore().status)
            assertEquals(pending, f.pending())
            assertEquals("Unbacked local edit", f.db.noteDao().getById("n")!!.bodyPlainText)
        }
    }

    @Test fun publishedPhoneRestoresOnlyDescendantThenBacksUpNewLocalEdit() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { f ->
            f.root()
            val published = f.binding()
            val note = f.db.noteDao().getById("n")!!.copy(bodyPlainText = "Incoming other-device edit")
            val incoming = append(f, listOf(BackupRecordChange("notes.json", listOf("n"), note.toJson())))
            val result = restorer(f).restore()
            assertEquals(GraphRestoreStatus.APPLIED, result.status)
            assertEquals(1, result.commitsApplied)
            assertEquals(1, result.rowsWritten)
            assertEquals(published, f.binding())
            val restored = applied(f)!!
            assertEquals(BackupGraphProtocol.parse(incoming).commitId, restored.commitId)
            assertFalse(f.db.backupJournalDao().account(f.ctx.accountScope)!!.trusted)
            assertTrue(f.pending().isEmpty())
            f.edit("New local work after Restore")
            val pending = f.pending()
            assertTrue(adoptVerifiedRestoredParent(f.db, f.ctx, f.store.commits()))
            assertEquals(pending, f.pending())
            assertEquals(restored, applied(f))
            assertFalse(adoptVerifiedRestoredParent(f.db, f.ctx, f.store.commits()))
            val next = f.writer().publish()
            assertFalse(next.alreadyBackedUp)
            assertTrue(f.pending().isEmpty())
            assertEquals(restored, applied(f))
            assertEquals(GraphRestoreStatus.ALREADY_CURRENT, restorer(f).restore().status)
            assertEquals("New local work after Restore", f.db.noteDao().getById("n")!!.bodyPlainText)
        }
    }

    @Test fun publishedBaseWithPendingLocalEditBlocksIncomingWithoutAcknowledgingIt() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { f ->
            f.root()
            append(f, listOf(BackupRecordChange("notes.json", listOf("n"), f.db.noteDao().getById("n")!!.copy(bodyPlainText = "Incoming").toJson())))
            f.edit("Keep local")
            val pending = f.pending(); val binding = f.binding()
            assertEquals(GraphRestoreStatus.LOCAL_CHANGES, restorer(f).restore().status)
            assertEquals(pending, f.pending()); assertEquals(binding, f.binding()); assertNull(applied(f))
            assertEquals("Keep local", f.db.noteDao().getById("n")!!.bodyPlainText)
        }
    }

    @Test fun publicationSourceProofSurvivesRestartAndDoesNotReplayCheckpoint() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { f ->
            f.root()
            append(f, listOf(BackupRecordChange("notes.json", listOf("n"), f.db.noteDao().getById("n")!!.copy(bodyPlainText = "Resumed exact descendant").toJson())))
            fails { restorer(f, boundary = { if (it == "INTENT_PERSISTED") error("Disposable interruption") }).restore() }
            val intent = f.db.backupGraphRestoreDao().unfinished().single()
            assertTrue(JSONObject(intent.frozenChangesJson).has("publicationSource"))
            assertNull(intent.originalAppliedJson)
            val pending = f.pending()
            f.reopen()
            val result = restorer(f).restore()
            assertEquals(GraphRestoreStatus.APPLIED, result.status)
            assertEquals(1, result.rowsWritten)
            assertEquals(pending, f.pending())
            assertEquals("Resumed exact descendant", f.db.noteDao().getById("n")!!.bodyPlainText)
            assertEquals("COMPLETE", f.db.backupGraphRestoreDao().readIntent(f.ctx.accountScope, intent.operationId)!!.status)
        }
    }

    @Test fun restoredParentHandoffRejectsWrongAccountAndNewerRemoteWithoutChangingPending() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root()
            target(source).use { f ->
                restorer(f).restore()
                f.edit("Preserve pending")
                val pending = f.pending(); val restored = applied(f)
                fails { adoptVerifiedRestoredParent(f.db, f.ctx.copy(driveAccountId = "another-account"), source.store.commits()) }
                source.edit("New remote change"); source.writer().publish()
                fails { adoptVerifiedRestoredParent(f.db, f.ctx, source.store.commits()) }
                assertEquals(pending, f.pending()); assertEquals(restored, applied(f))
                assertNull(f.db.backupGraphDao().binding(f.ctx.accountScope, f.ctx.lineageId))
                assertFalse(f.db.backupJournalDao().account(f.ctx.accountScope)!!.trusted)
            }
        }
    }

    @Test fun realPreferencesAdapterPreservesUnrelatedAccountAndRestoresOnlyBackupFields() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { f ->
            val preferences = VaultPreferences(base, f.journal)
            val adapter = GraphRestorePreferences(preferences)
            val before = adapter.read()
            val account = preferences.userPreferences.first().googleDriveAccountEmail
            val pending = f.pending()
            val target = JSONObject(before.toString()).put("fontSize", if (before.getString("fontSize") == "small") "large" else "small")
            try {
                adapter.apply(target)
                assertEquals(target.toValidatedBackupPreferences(), adapter.read().toValidatedBackupPreferences())
                assertEquals(account, preferences.userPreferences.first().googleDriveAccountEmail)
                assertEquals(pending, f.pending())
            } finally {
                adapter.apply(before)
            }
        }
    }

    @Test fun richArabicBlockAndTrashStateRemainExactWithoutPermanentDeletion() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.note()
            val rich = "{\"text\":\"English الْعَرَبِيَّة\",\"styleMarks\":[{\"start\":0,\"end\":7,\"bold\":true}]}"
            source.db.blockDao().upsertAll(listOf(com.myvault.app.data.local.entity.BlockEntity("rich", "n", "rich_text", rich, 0)))
            source.writer().createRoot(source.prepared())
            target(source).use { f ->
                restorer(f).restore()
                assertEquals(rich, f.db.blockDao().getForNote("n").single().content)
                source.db.noteDao().updateDeletedAt(listOf("n"), 1234L, 1235L)
                source.writer().publish()
                restorer(f).restore()
                val trashed = f.db.noteDao().getAllIncludingDeleted().single { it.id == "n" }
                assertEquals(1234L, trashed.deletedAt)
                assertEquals(rich, f.db.blockDao().getForNote("n").single().content)
                assertTrue(f.pending().isEmpty())
            }
        }
    }

    private suspend fun append(source:BackupGraphWriterRoomTest.Fixture, changes:List<BackupRecordChange>, parentId:String?=null):GraphObject {
        val parent=source.store.commits().single { BackupGraphProtocol.parse(it).commitId==(parentId?:source.binding().commitId) }
        val p=BackupGraphProtocol.parse(parent);val deltaId=UUID.randomUUID().toString()
        val delta=IncrementalBackupFormat.createDelta(p.checkpoint.checkpointId,p.deltaHead,deltaId,changes,emptyList()).toString().toByteArray()
        val file=File(source.root,"manual-fixture-${UUID.randomUUID()}").apply {writeBytes(delta)};val id=source.store.reserveId();source.store.create(id,"DELTA",file)
        val ref=GraphObjectRef(id,IncrementalBackupFormat.sha256(delta),delta.size.toLong())
        val commit=BackupGraphCommit(p.accountId,p.lineageId,UUID.randomUUID().toString(),"delta",(p.requiredReaders+"checkpoint-delta-v1"+BackupBinaryReaderCapability).distinct().sorted(),listOf(GraphParent(p.commitId,parent.objectRef)),p.checkpoint,GraphDelta(deltaId,p.deltaHead,ref))
        val bytes=BackupGraphProtocol.encode(commit);file.writeBytes(bytes);val commitId=source.store.reserveId();source.store.create(commitId,"COMMIT",file)
        return GraphObject(GraphObjectRef(commitId,IncrementalBackupFormat.sha256(bytes),bytes.size.toLong()),bytes)
    }

    @Test fun identicalUpsertSkipsWriteAndExplicitDeleteDoesNotRemoveAbsentUnrelatedRows() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root();source.note("extra","Unrelated");source.writer().publish()
            target(source).use { f ->
                restorer(f).restore();val note=source.db.noteDao().getById("n")!!.toJson()
                append(source,listOf(BackupRecordChange("notes.json",listOf("n"),note)))
                val unchanged=restorer(f).restore();assertEquals(0,unchanged.rowsWritten)
                assertNotNull(f.db.noteDao().getById("extra"));assertTrue(f.pending().isEmpty())
                // Publish an exact deletion extending the manual fixture, without changing its sibling history.
                val tip=BackupGraph.discover(source.store.commits(),source.ctx.driveAccountId,source.ctx.lineageId).tips.single()
                append(source,listOf(BackupRecordChange("notes.json",listOf("n"))),tip)
                restorer(f).restore();assertNull(f.db.noteDao().getById("n"));assertNotNull(f.db.noteDao().getById("extra"));assertTrue(f.pending().isEmpty())
            }
        }
    }

    @Test fun forkDivergenceUnsupportedAndCorruptDeltaBlockWithoutAdvancement() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root();val root=source.binding();target(source).use { f ->
                restorer(f).restore();val before=applied(f)
                val a=append(source,listOf(BackupRecordChange("notes.json",listOf("n"),source.db.noteDao().getById("n")!!.toJson())))
                restorer(f).restore();val atA=applied(f)!!
                val b=append(source,listOf(BackupRecordChange("notes.json",listOf("n"),source.db.noteDao().getById("n")!!.toJson())),root.commitId)
                assertEquals(GraphRestoreStatus.FORK,restorer(f).restore().status);assertEquals(atA,applied(f))
                val onlyB=object:DisposableGraphObjectStore by source.store {override suspend fun commits()=source.store.commits().filter {it.objectRef!=a.objectRef}}
                assertEquals(GraphRestoreStatus.DIVERGENT,restorer(f,onlyB).restore().status);assertEquals(atA,applied(f))
                val unsupportedBytes=String(b.bytes).replace("\"backup-commit-graph-v1\"","\"aaa-unsupported-v99\",\"backup-commit-graph-v1\"").toByteArray()
                val unsupported=GraphObject(b.objectRef.copy(sha256=IncrementalBackupFormat.sha256(unsupportedBytes),size=unsupportedBytes.size.toLong()),unsupportedBytes)
                val unsupportedStore=object:DisposableGraphObjectStore by source.store {override suspend fun commits()=listOf(source.store.commits().first(),unsupported)}
                assertEquals(GraphRestoreStatus.UNSUPPORTED,restorer(f,unsupportedStore).restore().status)
                assertNotEquals(before,applied(f));assertEquals(atA,applied(f))
            }
        }
        BackupGraphWriterRoomTest().Fixture().use {source->
            source.root();target(source).use {f->restorer(f).restore();val before=applied(f);source.edit("Corrupt delta");source.writer().publish()
                val deltaId=source.db.backupGraphDao().objects(source.ctx.accountScope,source.binding().commitId).single {it.role=="DELTA"}.objectId
                val bad=object:DisposableGraphObjectStore by source.store {override suspend fun read(objectId:String)=if(objectId==deltaId)"bad".byteInputStream() else source.store.read(objectId)}
                fails {restorer(f,bad).restore()};assertEquals(before,applied(f))
            }
        }
    }

    @Test fun annotationUpsertRetainsGeometryAndSettingsDeltaOnlyTouchesItsSingleton() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use {source->
            source.note();source.binary(4096)
            val ann=com.myvault.app.data.local.entity.PdfAnnotationEntity(id="ann",attachmentId="pdf",libraryFolderId=null,pageIndex=0,left=1f,top=2f,right=3f,bottom=4f,color="yellow",noteText="Original",createdAt=10,updatedAt=11)
            source.db.pdfAnnotationDao().upsertAll(listOf(ann));source.db.pdfAnnotationSegmentDao().upsertAll(listOf(com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity("ann",0,0,1f,2f,3f,4f)))
            source.writer().createRoot(source.prepared());target(source).use {f->
                restorer(f).restore();val settingsFile=File(f.root,"restore-settings.json");val old=JSONObject(settingsFile.readText());val changed=JSONObject(old.toString()).put("fontSize",if(old.getString("fontSize")=="small")"large" else "small")
                append(source,listOf(BackupRecordChange("pdf_annotations.json",listOf("ann"),ann.copy(noteText="Changed",updatedAt=12).toJson()),BackupRecordChange("settings.json",listOf("settings"),changed)))
                assertEquals(1,restorer(f).restore().rowsWritten)
                f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM pdf_annotation_segments WHERE annotationId='ann'").use {it.moveToFirst();assertEquals(1,it.getInt(0))}
                assertEquals(changed.toValidatedBackupPreferences(),JSONObject(settingsFile.readText()).toValidatedBackupPreferences());assertTrue(f.pending().isEmpty())
            }
        }
    }

    @Test fun fullThenPendingOnlyAlreadyCurrentAndIndependentPublicationState() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root(); val root=source.binding()
            target(source).use { f ->
                val initial=restorer(f).restore();assertEquals(GraphRestoreStatus.APPLIED,initial.status);assertEquals(1,initial.commitsApplied)
                assertNull(f.db.backupGraphDao().binding(f.ctx.accountScope,f.ctx.lineageId))
                assertEquals(root.commitId,applied(f)!!.commitId);assertTrue(f.pending().isEmpty())
                f.queries.clear();val reads=mutableListOf<String>()
                val counted=object:DisposableGraphObjectStore by source.store { override suspend fun read(objectId:String)=source.store.read(objectId).also { reads+=objectId } }
                assertEquals(GraphRestoreStatus.ALREADY_CURRENT,restorer(f,counted).restore().status)
                assertTrue(reads.isEmpty());assertTrue(f.querySnapshot().none { it.startsWith("SELECT * FROM `notes`") || it.startsWith("PRAGMA table_info") })
                source.edit("B العربية");source.writer().publish();val b=source.binding()
                source.edit("C English");source.writer().publish();val c=source.binding()
                f.queries.clear();reads.clear();val result=restorer(f,counted).restore()
                assertEquals(2,result.commitsApplied);assertEquals(2,result.rowsWritten);assertEquals(0,result.binariesDownloaded)
                assertEquals(2,reads.size);assertTrue(root.checkpointFileId !in reads)
                assertEquals("C English",f.db.noteDao().getAllIncludingDeleted().single { it.id == "n" }.bodyPlainText)
                assertEquals(c.commitId,applied(f)!!.commitId);assertEquals(c.deltaHeadId,applied(f)!!.deltaHeadId)
                assertNotEquals(b.commitId,applied(f)!!.commitId);assertTrue(f.pending().isEmpty())
                f.reopen();assertEquals(c.commitId,applied(f)!!.commitId);assertEquals(GraphRestoreStatus.ALREADY_CURRENT,restorer(f).restore().status)
            }
        }
    }

    @Test fun replacementNewAttachmentMetadataOnlyAndExactDelete() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.note();source.binary(4096);source.writer().createRoot(source.prepared())
            target(source).use { f ->
                restorer(f).restore();val old=f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!;assertEquals(4096,File(old.localPath).length())
                source.binary(8192);source.writer().publish()
                val result=restorer(f).restore();assertEquals(1,result.binariesDownloaded)
                val changed=f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!;assertEquals(8192,File(changed.localPath).length());assertEquals(8192,changed.sizeBytes)
                assertTrue(File(old.localPath).isFile);assertNotEquals(old.localPath,changed.localPath)
                source.db.attachmentDao().updateFileName("pdf","renamed.pdf");source.writer().publish()
                assertEquals(0,restorer(f).restore().binariesDownloaded);assertEquals(changed.localPath,f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!.localPath)
                source.binary(1024,"new");source.writer().publish();assertEquals(1,restorer(f).restore().binariesDownloaded)
                source.db.attachmentDao().deleteByIds(listOf("new"));source.writer().publish();restorer(f).restore()
                assertNull(f.db.attachmentDao().getByIdIncludingDeleted("new"));assertNotNull(f.db.attachmentDao().getByIdIncludingDeleted("pdf"));assertTrue(f.pending().isEmpty())
            }
        }
    }

    @Test fun restartAtEveryBinaryAndRoomBoundaryAndIdempotentRetry() = runBlocking {
        for (point in listOf("INTENT_PERSISTED","BEFORE_BINARY_DOWNLOAD","BINARY_DOWNLOADED","BINARY_STAGED","BINARY_DESTINATION_READY","BEFORE_ROOM_APPLY","ROOM_OPERATIONS_APPLIED","COMMIT_APPLIED")) {
            BackupGraphWriterRoomTest().Fixture().use { source ->
                source.note();source.binary(4096);source.writer().createRoot(source.prepared())
                target(source).use { f ->
                    restorer(f).restore();val root=applied(f)!!;val old=f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!
                    source.binary(8192);source.writer().publish();val tip=source.binding()
                    fails { restorer(f,boundary={ if(it==point)error("Disposable interruption at $point") }).restore() }
                    assertEquals(if(point=="COMMIT_APPLIED")tip.commitId else root.commitId,applied(f)!!.commitId)
                    assertTrue(File(old.localPath).isFile)
                    f.reopen();restorer(f).restore();assertEquals(tip.commitId,applied(f)!!.commitId)
                    assertEquals(8192,File(f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!.localPath).length())
                    assertEquals(GraphRestoreStatus.ALREADY_CURRENT,restorer(f).restore().status);assertTrue(f.pending().isEmpty())
                }
            }
        }
    }

    @Test fun settingsRecoveryDoesNotAdvanceBeforeVerifiedCompletion() = runBlocking {
        for(point in listOf("SETTINGS_APPLYING","SETTINGS_WRITTEN","ROOM_OPERATIONS_APPLIED")) BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root()
            target(source).use { f ->
                fails { restorer(f,boundary={if(it==point)error("Disposable settings interruption")}).restore() }
                assertNull(applied(f));f.reopen();restorer(f).restore();assertNotNull(applied(f));assertTrue(f.pending().isEmpty())
                assertNull(f.db.backupJournalDao().clock().settingsToken)
            }
        }
    }

    @Test fun localEditsIncludingNPlusOneRemainUntouchedAndAccountRecoveryIsolated() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.root()
            target(source).use { f ->
                restorer(f).restore();val before=applied(f)!!;source.edit("Remote");source.writer().publish()
                val result=restorer(f,boundary={if(it=="BEFORE_ROOM_APPLY")f.edit("New local N+1")}).restore()
                assertEquals(GraphRestoreStatus.LOCAL_CHANGES,result.status);assertEquals(before,applied(f))
                assertEquals("New local N+1",f.db.noteDao().getAllIncludingDeleted().single { it.id == "n" }.bodyPlainText);val pending=f.pending();assertEquals(1,pending.size)
                val wrong=object:DisposableGraphObjectStore by source.store { override val context=GraphWriterContext("b@example.com","b-permission",source.ctx.lineageId) }
                assertEquals(GraphRestoreStatus.ACCOUNT_MISMATCH,restorer(f,wrong).restore().status)
                f.reopen();assertEquals(GraphRestoreStatus.LOCAL_CHANGES,restorer(f).restore().status);assertEquals(pending,f.pending())
            }
        }
    }

    @Test fun corruptOrMissingBinaryNeverFallsBackAndBRemainsAppliedWhenCFails() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.note();source.binary(4096);source.writer().createRoot(source.prepared())
            target(source).use { f ->
                restorer(f).restore();val old=f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!
                source.edit("B");source.writer().publish();val b=source.binding();source.binary(8192);source.writer().publish()
                val binaryId=source.db.backupJournalDao().binaryReference(source.ctx.accountScope,"pdf")!!.cloudFileId
                val missing=object:DisposableGraphObjectStore by source.store { override suspend fun read(objectId:String)=if(objectId==binaryId)null else source.store.read(objectId) }
                fails { restorer(f,missing).restore() };assertEquals(b.commitId,applied(f)!!.commitId);assertEquals(old,f.db.attachmentDao().getByIdIncludingDeleted("pdf"))
                val corrupt=object:DisposableGraphObjectStore by source.store { override suspend fun read(objectId:String)=if(objectId==binaryId)ByteArray(8192).inputStream() else source.store.read(objectId) }
                fails { restorer(f,corrupt).restore() };assertEquals(b.commitId,applied(f)!!.commitId)
                restorer(f).restore();assertEquals(8192,f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!.sizeBytes)
            }
        }
    }

    @Test fun corruptStagedBytesFailClosedAfterRestart() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            source.note();source.binary(4096);source.writer().createRoot(source.prepared())
            target(source).use { f ->
                restorer(f).restore();val before=applied(f);source.binary(8192);source.writer().publish()
                fails { restorer(f,boundary={if(it=="BINARY_STAGED")error("Stop")}).restore() }
                val intent=f.db.backupGraphRestoreDao().unfinished().single();val obj=f.db.backupGraphRestoreDao().objects(f.ctx.accountScope,intent.operationId).single()
                File(obj.stagingPath).writeBytes(ByteArray(8192));f.reopen();fails { restorer(f).restore() };assertEquals(before,applied(f))
                File(obj.stagingPath).delete();fails { restorer(f).restore() };assertEquals(before,applied(f))
            }
        }
    }

    @Test fun migration35To36PreservesEveryExistingTableAndFreshInstall() = runBlocking {
        val name="graph-restore-migration-disposable-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.myvault.app.data.local.VaultDatabase/35.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val before=linkedMapOf<String,String>();val path=base.getDatabasePath(name).apply {parentFile!!.mkdirs()};var room:VaultDatabase?=null
        fun dump(raw:android.database.sqlite.SQLiteDatabase,table:String)=raw.rawQuery("SELECT * FROM `$table` ORDER BY rowid",null).use { c -> val a=JSONArray();while(c.moveToNext())a.put(JSONArray((0 until c.columnCount).map {if(c.isNull(it))JSONObject.NULL else c.getString(it)}));a.toString() }
        try {
            SQLiteDatabase.openOrCreateDatabase(path,null).use {raw ->
                val entities=BackupGraphIntentCodec.array(schema.getJSONArray("entities"))
                for(e in entities) {val table=e.getString("tableName");raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));e.optJSONArray("indices")?.let {a->BackupGraphIntentCodec.array(a).forEach {raw.execSQL(it.getString("createSql").replace("\${TABLE_NAME}",table))}};e.optJSONArray("contentSyncTriggers")?.let {a->for(i in 0 until a.length())raw.execSQL(a.getString(i))}}
                raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");raw.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
                raw.execSQL("INSERT INTO notes VALUES('n',NULL,NULL,'عنوان','الْعَرَبِيَّة English',0,0,0,0,10,11,NULL)")
                raw.execSQL("INSERT INTO blocks VALUES('b','n','rich_text','{\"text\":\"Arabic العربية\",\"styleMarks\":[{\"bold\":true}]}',0)")
                raw.execSQL("INSERT INTO folders VALUES('f',NULL,'Folder',NULL,0,0,'study',10,11,NULL,'blue')")
                raw.execSQL("INSERT INTO attachments VALUES('pdf','n',NULL,'a.pdf','application/pdf',4096,'/fixture/a',NULL,0,10,NULL,0)")
                raw.execSQL("INSERT INTO pdf_annotations(id,attachmentId,libraryFolderId,pageIndex,`left`,top,`right`,bottom,color,noteText,annotationType,textSize,backgroundColor,selectedText,displayTitle,displayFolderId,createdAt,updatedAt) VALUES('ann','pdf',NULL,0,1,2,3,4,'yellow','Highlight','highlight',16,'none','الْعَرَبِيَّة',NULL,NULL,10,11)")
                raw.execSQL("INSERT INTO pdf_annotation_segments VALUES('ann',0,0,1,2,3,4)")
                raw.execSQL("INSERT INTO pdf_reading_progress VALUES('pdf',1,10,0.1,10,11)")
                raw.execSQL("INSERT INTO backup_journal_state VALUES(1,9,2,0,NULL)")
                raw.execSQL("INSERT INTO backup_tracking_accounts VALUES('a@example.com',0,NULL,NULL,NULL,NULL,'baseline_required')")
                raw.execSQL("INSERT INTO backup_pending_changes VALUES('a@example.com','notes.json','n','','','UPSERT',9)")
                raw.execSQL("INSERT INTO backup_graph_bindings VALUES('a@example.com','lineage','drive','commit','file','hash',99,'checkpoint','cpfile','cphash',100,'delta-head',2)")
                raw.execSQL("INSERT INTO backup_graph_publications VALUES('a@example.com','op','lineage','drive',9,2,'{}','{}','{}','{}','PREPARED')")
                raw.execSQL("INSERT INTO backup_graph_publication_objects VALUES('a@example.com','op','object',0,'COMMIT',NULL,'/private/staged','hash',99,NULL,NULL)")
                for(e in entities)if(e.getString("tableName")!="notes_fts")before[e.getString("tableName")]=dump(raw,e.getString("tableName"))
                raw.version=35
            }
            room=Room.databaseBuilder(base,VaultDatabase::class.java,name).addMigrations(*VaultDatabase.ALL_MIGRATIONS).build();val migrated=room.openHelper.writableDatabase;assertEquals(36,migrated.version)
            for((table,expected)in before)migrated.query("SELECT * FROM `$table` ORDER BY rowid").use {c->val a=JSONArray();while(c.moveToNext())a.put(JSONArray((0 until c.columnCount).map {if(c.isNull(it))JSONObject.NULL else c.getString(it)}));assertEquals(table,expected,a.toString())}
            for(table in listOf("backup_graph_applied_states","backup_graph_restores","backup_graph_restore_objects"))migrated.query("SELECT COUNT(*) FROM $table").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        }finally {room?.close();base.deleteDatabase(name)}
        BackupGraphWriterRoomTest().Fixture().use {assertEquals(36,it.db.openHelper.writableDatabase.version);assertTrue(it.db.backupGraphRestoreDao().unfinished().isEmpty())}
        assertTrue(BackupGraphTargetedRestoreEnabled);assertTrue(BackupGraphPublicationEnabled);assertFalse(IncrementalBackupPublicationEnabled)
}

    @Test fun hugePayloadCursorWindowDoesNotCrashFreshRestore() = runBlocking {
        BackupGraphWriterRoomTest().Fixture().use { source ->
            val builder = StringBuilder()
            while (builder.length < 4_000_000) {
                builder.append("A".repeat(10_000))
            }
            source.db.noteDao().insert(NoteEntity(id="huge-note", folderId="root", title="Huge", body=builder.toString(), pinned=false, trash=null, created=0, updated=0, displayOrder=0))
            source.writer().publish()
            target(source).use { target ->
                val r = restorer(target, source.publicationStore)
                assertEquals(GraphRestoreStatus.APPLIED, r.restore().status)
                assertEquals(builder.toString(), target.db.noteDao().note("huge-note")!!.body)
            }
        }
    }
}
