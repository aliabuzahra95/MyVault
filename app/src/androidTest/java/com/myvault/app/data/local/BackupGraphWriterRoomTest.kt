package com.myvault.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Disposable named databases and private fixture files only. No network transport or production Vault. */
@RunWith(AndroidJUnit4::class)
class BackupGraphWriterRoomTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = "graph-a@example.com"
    private val driveAccount = "disposable-permission-id"
    private val lineage = "disposable-lineage"

    internal class FilesStore(val directory: File, override val context: GraphWriterContext) : DisposableGraphObjectStore {
        var afterCreate: ((String, String) -> Unit)? = null
        val events = mutableListOf<String>()
        init { directory.mkdirs() }
        override suspend fun reserveId() = "fixture-${UUID.randomUUID()}"
        override suspend fun read(objectId: String) = File(directory, objectId).takeIf { it.isFile }?.inputStream()
        override suspend fun create(objectId: String, role: String, file: File) {
            val target = File(directory, objectId)
            synchronized(directory.internLock()) {
                if (!target.exists()) {
                    FileOutputStream(target).use { output -> file.inputStream().use { it.copyTo(output) }; output.fd.sync() }
                    File(directory, "$objectId.role").writeText(role)
                    events += role
                }
            }
            afterCreate?.invoke(objectId, role)
        }
        override suspend fun commits() = directory.listFiles()!!.filter { it.name.endsWith(".role") && it.readText() == "COMMIT" }.map {
            val id = it.name.removeSuffix(".role"); val bytes = File(directory, id).readBytes()
            GraphObject(GraphObjectRef(id, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong()), bytes)
        }
        fun bytes() = directory.listFiles()!!.filterNot { it.name.endsWith(".role") }.associate { it.name to it.readBytes() }
        private fun File.internLock() = canonicalPath.intern()
    }

    internal inner class Fixture(val shared: FilesStore? = null, nameOverride: String? = null,
        remoteStore: DisposableGraphObjectStore? = null) : AutoCloseable {
        val name = nameOverride ?: "graph-writer-disposable-${UUID.randomUUID()}.db"
        val root = File(base.filesDir, name.removeSuffix(".db")).apply { mkdirs() }
        val attachments = File(base.filesDir, "attachments/${root.name}").apply { mkdirs() }
        val ctx = remoteStore?.context ?: GraphWriterContext(account, driveAccount, lineage)
        val store = shared ?: FilesStore(File(root, "objects"), ctx)
        val publicationStore = remoteStore ?: store
        val queries = java.util.Collections.synchronizedList(mutableListOf<String>())
        fun querySnapshot(): List<String> = synchronized(queries) { queries.toList() }
        var db = open()
        var journal = BackupChangeJournal(db)
        val stagedBaselines = mutableListOf<File>()
        fun open() = Room.databaseBuilder(base, VaultDatabase::class.java, name).addMigrations(*VaultDatabase.ALL_MIGRATIONS)
            .setQueryCallback({ sql, _ -> queries.add(sql) }, java.util.concurrent.Executor { it.run() })
            .addCallback(VaultDatabase.BACKUP_JOURNAL_CALLBACK).build()
        fun writer(boundary: suspend (String) -> Unit = {}) = InternalBackupGraphWriter(db, journal,
            PendingBackupCapture(db, journal, VaultPreferences(base, journal)), base.filesDir, File(root, "staging"), publicationStore, boundary)
        suspend fun note(id: String = "n", text: String = "Original العربية") {
            db.noteDao().upsertAll(listOf(NoteEntity(id, null, title = "English العربية", bodyPlainText = text, isPinned = false,
                isFavourite = false, createdAt = 10, updatedAt = 11)))
        }
        suspend fun edit(text: String, id: String = "n") = db.noteDao().updateBodyPlainText(id, text, 12)
        suspend fun binary(size: Int, id: String = "pdf") {
            val file = File(attachments, "${UUID.randomUUID()}.pdf")
            val binaries = BackupBinaryStore(base, db, journal)
            val digest = binaries.writeNew(file, ByteArray(size) { if (size == 4096) 79 else 82 }.inputStream())
            val row = AttachmentEntity(id, "n", fileName = "pdf.pdf", mimeType = "application/pdf", sizeBytes = digest.size,
                localPath = file.absolutePath, remoteUrl = null, createdAt = 10)
            db.withTransaction { db.attachmentDao().upsertAll(listOf(row)); binaries.persistWritten(row, digest) }
        }
        suspend fun prepared(onProgress: suspend (GraphBackupProgress) -> Unit = {}): PreparedBackupBaseline {
            journal.registerAccount(ctx.accountScope)
            val prefs = VaultPreferences(base, journal)
            val repo = BackupRepository(base,db,db.folderDao(),db.folderStickyNoteDao(),db.noteDao(),db.blockDao(),db.courseDao(),db.tagDao(),db.attachmentDao(),db.searchDao(),db.noteTableDao(),db.noteVersionDao(),db.pdfReadingProgressDao(),db.pdfAnnotationDao(),db.pdfAnnotationSegmentDao(),db.sourceBacklinkDao(),db.knowledgeTagDao(),prefs)
            return BackupBaselinePreparer(base,db,journal,prefs,repo,BackupBinaryStore(base,db,journal)).prepare(ctx.accountScope,onProgress).also { stagedBaselines += it.directory }
        }
        suspend fun root(): GraphWriterResult { note(); return writer().createRoot(prepared()) }
        fun reopen() { db.close(); db = open(); journal = BackupChangeJournal(db) }
        suspend fun binding() = db.backupGraphDao().binding(ctx.accountScope, ctx.lineageId)!!
        suspend fun pending() = db.backupJournalDao().pending(ctx.accountScope)
        fun graph(objects: List<GraphObject>) = BackupGraph.discover(objects, driveAccount, lineage)
        suspend fun read() = BackupGraphReconstruction.read(graph(store.commits())) { store.bytes().getValue(it) }
        override fun close() { db.close(); base.deleteDatabase(name); root.deleteRecursively(); attachments.deleteRecursively(); stagedBaselines.forEach { it.deleteRecursively() } }
        suspend fun export(label: String, extra: JSONObject = JSONObject()) {
            val objects = JSONObject(); store.bytes().forEach { (id, bytes) -> objects.put(id, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)) }
            val refs = JSONArray(store.commits().map { it.objectRef.let { r -> JSONObject().put("cloudFileId", r.cloudFileId).put("sha256", r.sha256).put("size", r.size) } })
            val bundle = extra.put("accountId", driveAccount).put("lineageId", lineage).put("objects", objects).put("refs", refs)
            File(base.filesDir, "graph-writer-fixtures").apply { mkdirs() }.resolve("$label.json").writeText(bundle.toString())
        }
    }

    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected safe refusal") } catch (_: IllegalStateException) { } catch (_: java.io.IOException) { }
    }

    @Test fun baselineProgressTracksActualObjectsAndOnlyCompletesAfterVerifiedCommit() = runBlocking {
        Fixture().use { f ->
            f.note(); f.binary(4096)
            val preparation = mutableListOf<GraphBackupProgress>()
            val prepared = f.prepared { preparation += it }
            assertEquals(GraphBackupStage.READING_BASELINE, preparation.first().stage)
            assertEquals(1, preparation.last { it.stage == GraphBackupStage.STAGING_FILES }.current)
            assertEquals(prepared.objects.count { it.kind == "metadata" },
                preparation.last { it.stage == GraphBackupStage.STAGING_METADATA }.total)
            assertTrue(preparation.none { it.stage == GraphBackupStage.COMPLETE })
            val events = mutableListOf<GraphBackupProgress>()
            val writer = InternalBackupGraphWriter(f.db, f.journal,
                PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)),
                base.filesDir, File(f.root,"staging"), f.store, onProgress = { progress ->
                    events += progress
                    if (progress.stage == GraphBackupStage.COMPLETE) {
                        val binding = f.binding()
                        assertEquals("COMPLETE", f.db.backupGraphDao().publication(account,binding.commitId)!!.status)
                        assertTrue(f.pending().isEmpty())
                    }
                })
            val result = writer.createRoot(prepared)
            val objects = f.db.backupGraphDao().objects(account,result.operationId!!)
            assertEquals("COMMIT", f.store.events.last())
            assertEquals(objects.size, events.last { it.stage == GraphBackupStage.VERIFYING }.total)
            assertEquals(prepared.objects.size + 1, events.last { it.stage == GraphBackupStage.VERIFYING_BASELINE }.current)
            assertEquals(GraphBackupStage.COMPLETE, events.last().stage)
            events.clear(); f.store.events.clear()
            assertTrue(writer.publish().alreadyBackedUp)
            assertEquals(GraphBackupStage.ALREADY_BACKED_UP,events.last().stage)
            assertTrue(events.none { it.stage in setOf(GraphBackupStage.STAGING_PUBLICATION,GraphBackupStage.UPLOADING,GraphBackupStage.VERIFYING) })
            assertTrue(f.store.events.isEmpty())
        }
    }

    @Test fun interruptedCompletionNeverReportsSuccessAndExactRetryFinishes() = runBlocking {
        Fixture().use { f ->
            f.root(); f.edit("Pending change العربية")
            val events = mutableListOf<GraphBackupProgress>()
            fun writer(interrupt: Boolean) = InternalBackupGraphWriter(f.db,f.journal,
                PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)),
                base.filesDir,File(f.root,"staging"),f.store,
                boundary = { if (interrupt && it == "BEFORE_COMPLETION") error("Disposable interruption") },
                onProgress = { events += it })
            fails { writer(true).publish() }
            assertTrue(events.none { it.stage in setOf(GraphBackupStage.COMPLETE,GraphBackupStage.ALREADY_BACKED_UP) })
            assertEquals(1,f.pending().size)
            val operation = f.db.backupGraphDao().unfinished(account).single().operationId
            val count = f.store.commits().size
            f.reopen(); events.clear()
            val result = writer(false).resume(operation)
            assertEquals(operation,result.commitId)
            assertEquals(count,f.store.commits().size)
            assertEquals(GraphBackupStage.COMPLETE,events.last().stage)
            assertTrue(f.pending().isEmpty())
        }
    }

    @Test fun batchedReservationAndFreshInventoryProofPreserveExactReadback() = runBlocking {
        val f = Fixture()
        f.use {
            f.root(); f.edit("One pending note العربية")
            val reservations = mutableListOf<Int>()
            val reads = mutableMapOf<String, Int>()
            val remote = object : DisposableGraphObjectStore by f.store {
                override suspend fun reserveIds(count: Int): List<String> {
                    reservations += count; return List(count) { f.store.reserveId() }
                }
                override suspend fun read(objectId: String): java.io.InputStream? {
                    reads[objectId] = (reads[objectId] ?: 0) + 1; return f.store.read(objectId)
                }
            }
            val timing = BackupGraphTiming(true)
            val writer = InternalBackupGraphWriter(f.db,f.journal,PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)),
                base.filesDir,File(f.root,"staging"),remote,timing=timing)
            val parent = f.binding(); val result = writer.publish()
            assertEquals(listOf(2),reservations)
            assertNull("Parent bytes already came from fresh inventory",reads[parent.commitFileId])
            val objects=f.db.backupGraphDao().objects(f.ctx.accountScope,result.operationId!!)
            assertEquals(2,reads[objects.single { it.role=="DELTA" }.objectId]) // Missing probe + exact post-create readback.
            assertTrue(timing.snapshot().any { it.name=="verification.COMMIT" && it.endNanos>=it.startNanos })
            assertTrue(f.pending().isEmpty())
            reads.clear(); reservations.clear(); assertTrue(writer.publish().alreadyBackedUp)
            assertTrue(reservations.isEmpty()); assertTrue(reads.isEmpty())
        }
    }

    @Test fun duplicateReservedIdsFailBeforeAnyPublication() = runBlocking {
        val f=Fixture()
        f.use {
            f.root();f.edit("Unpublished newer edit")
            val remote=object:DisposableGraphObjectStore by f.store {
                override suspend fun reserveIds(count:Int)=List(count) { "duplicate-intended-id" }
            }
            val writer=InternalBackupGraphWriter(f.db,f.journal,PendingBackupCapture(f.db,f.journal,VaultPreferences(base,f.journal)),
                base.filesDir,File(f.root,"staging"),remote)
            val parent=f.binding();val count=f.store.commits().size
            fails { writer.publish() }; assertEquals(parent,f.binding());assertEquals(count,f.store.commits().size);assertEquals(1,f.pending().size)
        }
    }

    @Test fun migration34To35PreservesAllRepresentativeRowsAndFreshInstallWorks() = runBlocking {
        val name = "graph-migration-disposable-${UUID.randomUUID()}.db"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.myvault.app.data.local.VaultDatabase/34.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = base.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val body = "English العربية — unchanged"
        val rich = """{"text":"العربية English","styleMarks":[{"start":0,"end":7,"bold":true}]}"""
        val before = linkedMapOf<String, String>()
        fun dump(db: android.database.sqlite.SQLiteDatabase, table: String): String = db.rawQuery("SELECT * FROM `$table` ORDER BY rowid", null).use { c ->
            val rows = JSONArray(); while(c.moveToNext()) rows.put(JSONArray((0 until c.columnCount).map { if(c.isNull(it)) JSONObject.NULL else c.getString(it) })); rows.toString()
        }
        var db: VaultDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
                val entities = schema.getJSONArray("entities")
                for (entity in BackupGraphIntentCodec.array(entities)) {
                    val table = entity.getString("tableName")
                    raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    entity.optJSONArray("indices")?.let { indices -> BackupGraphIntentCodec.array(indices).forEach { raw.execSQL(it.getString("createSql").replace("\${TABLE_NAME}", table)) } }
                    entity.optJSONArray("contentSyncTriggers")?.let { triggers -> for(i in 0 until triggers.length()) raw.execSQL(triggers.getString(i)) }
                }
                raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                raw.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(schema.getString("identityHash")))
                raw.execSQL("INSERT INTO notes VALUES('n',NULL,NULL,'عنوان',?,0,0,0,0,10,11,NULL)", arrayOf(body))
                raw.execSQL("INSERT INTO blocks VALUES('b','n','rich_text',?,0)", arrayOf(rich))
                raw.execSQL("INSERT INTO folders VALUES('f',NULL,'Folder العربية',NULL,0,0,'study',10,11,NULL,'blue')")
                raw.execSQL("INSERT INTO attachments VALUES('pdf','n',NULL,'pdf.pdf','application/pdf',4096,'/fixture/pdf',NULL,0,10,NULL,0)")
                raw.execSQL("INSERT INTO pdf_annotations(id,attachmentId,libraryFolderId,pageIndex,`left`,top,`right`,bottom,color,noteText,annotationType,textSize,backgroundColor,selectedText,displayTitle,displayFolderId,createdAt,updatedAt) VALUES('ann','pdf',NULL,0,1,2,3,4,'#FF00FF','Arabic highlight','highlight',16,'none','الْعَرَبِيَّة',NULL,NULL,10,11)")
                raw.execSQL("INSERT INTO pdf_annotation_segments VALUES('ann',0,0,1,2,3,4)")
                raw.execSQL("INSERT INTO pdf_reading_progress VALUES('pdf',2,20,10,10,11)")
                raw.execSQL("INSERT INTO backup_journal_state VALUES(1,42,2,0,NULL)")
                raw.execSQL("INSERT INTO backup_tracking_accounts VALUES('a@example.com',0,NULL,NULL,NULL,NULL,'baseline_required')")
                raw.execSQL("INSERT INTO backup_pending_changes VALUES('a@example.com','notes.json','n','','','UPSERT',42)")
                raw.execSQL("INSERT INTO backup_binary_fingerprints VALUES('pdf','/fixture/pdf',4096,NULL,'UNKNOWN',42)")
                for (entity in BackupGraphIntentCodec.array(entities)) if (entity.getString("tableName") != "notes_fts") {
                    val table = entity.getString("tableName"); before[table] = dump(raw, table)
                }
                raw.version = 34
            }
            db = Room.databaseBuilder(base,VaultDatabase::class.java,name).addMigrations(*VaultDatabase.ALL_MIGRATIONS).build()
            val migrated = db.openHelper.writableDatabase
            assertEquals(36, migrated.version)
            before.forEach { (table, expected) -> migrated.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
                val rows = JSONArray(); while(c.moveToNext()) rows.put(JSONArray((0 until c.columnCount).map { if(c.isNull(it)) JSONObject.NULL else c.getString(it) })); assertEquals(table,expected,rows.toString())
            } }
            listOf("backup_graph_bindings","backup_graph_publications","backup_graph_publication_objects").forEach { table ->
                migrated.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0,it.getInt(0)) }
            }
        } finally { db?.close(); base.deleteDatabase(name) }
        Fixture().use { f -> assertEquals(36, f.db.openHelper.writableDatabase.version); assertTrue(f.db.backupGraphDao().unfinished(account).isEmpty()) }
    }

    @Test fun pendingOnlyCoalescingExactDeletesAndGenerationRace() = runBlocking {
        Fixture().use { f ->
            f.root(); val initial = f.binding(); f.queries.clear(); val zero = f.writer().publish()
            assertTrue(zero.alreadyBackedUp); assertEquals(GraphWriterMetrics(0,0,0,0,0,0),zero.metrics)
            assertTrue(f.querySnapshot().none { it.startsWith("SELECT * FROM `notes`") || it.startsWith("SELECT * FROM notes") })
            repeat(20) { f.edit("Settled $it العربية") }
            f.queries.clear()
            val one = f.writer { if(it == "BEFORE_COMPLETION") f.edit("N+1 العربية") }.publish()
            assertEquals(GraphWriterMetrics(1,1,0,0,1,1),one.metrics)
            assertEquals(1,f.querySnapshot().count { it.startsWith("SELECT * FROM `notes`") })
            assertFalse(f.querySnapshot().any { it.startsWith("SELECT * FROM `attachments`") || it.startsWith("SELECT * FROM `blocks`") || it.startsWith("SELECT * FROM `folders`") })
            assertEquals(1,f.pending().size); assertEquals("Settled 19 العربية", JSONArray(f.read().files.getValue("notes.json")).getJSONObject(0).getString("bodyPlainText"))
            assertNotEquals(initial.commitId, f.binding().commitId); assertNotEquals(f.binding().commitId,f.binding().deltaHeadId)
            f.writer().publish(); assertTrue(f.pending().isEmpty())
            f.note("two"); f.note("three"); f.edit("One more")
            val three = f.writer().publish(); assertEquals(3,three.metrics.pendingRows); assertEquals(3,three.metrics.payloadRows)
            val intent = f.db.backupGraphDao().publication(account,three.operationId!!)!!
            assertEquals(3,BackupGraphIntentCodec.records(intent.frozenBatchJson).size)
            f.db.noteDao().updateDeletedAt(listOf("two"),100,100)
            f.writer().publish()
            val trashedRows = JSONArray(f.read().files.getValue("notes.json"))
            val trashed = (0 until trashedRows.length()).map(trashedRows::getJSONObject).single { it.getString("id") == "two" }
            assertEquals(100L, trashed.getLong("deletedAt"))
            f.db.noteDao().deleteByIds(listOf("two")); val deletion = f.writer().publish()
            val deleted = BackupGraphIntentCodec.records(f.db.backupGraphDao().publication(account,deletion.operationId!!)!!.frozenBatchJson).single()
            assertEquals(listOf("two"),deleted.key); assertEquals("DELETE",deleted.operation); assertNull(deleted.payloadJson)
            assertEquals(2,JSONArray(f.read().files.getValue("notes.json")).length())
            assertTrue(f.writer().publish().alreadyBackedUp) // Absence cannot invent another deletion.
            f.export("linear",JSONObject().put("expectedBody","One more").put("expectedNoteCount",2))
        }
    }

    @Test fun binaryReplacementMetadataReuseDeletionAndUnknownFailClosed() = runBlocking {
        Fixture().use { f ->
            f.note(); f.binary(4096); f.writer().createRoot(f.prepared())
            val old = f.db.backupJournalDao().binaryReference(account,"pdf")!!
            f.binary(8192)
            val replacement = f.writer().publish(); assertEquals(GraphWriterMetrics(1,1,1,1,1,1),replacement.metrics)
            assertEquals(8192L,f.read().binaries!!.single().size); assertNotEquals(old.cloudFileId,f.read().binaries!!.single().cloudFileId)
            val row = f.db.attachmentDao().getByIdIncludingDeleted("pdf")!!
            f.db.attachmentDao().updateFileName(row.id,"renamed.pdf")
            val metadata = f.writer().publish(); assertEquals(GraphWriterMetrics(1,1,0,0,1,1),metadata.metrics)
            assertEquals(8192L,f.read().binaries!!.single().size)
            f.export("binary",JSONObject().put("expectedBody","Original العربية").put("expectedNoteCount",1).put("expectedBinarySize",8192))
            File(row.localPath).writeBytes(ByteArray(8192) { 3 })
            f.db.noteDao().updateBodyPlainText("n","Unrelated edit",12)
            // Unrelated note does not stage/read unrelated file bytes.
            assertEquals(0,f.writer().publish().metrics.binariesStaged)
            f.db.attachmentDao().updateFileName(row.id,"changed.pdf")
            val fp = f.db.backupJournalDao().fingerprint("pdf")!!
            f.db.backupJournalDao().putFingerprint(fp.copy(status="UNKNOWN",sha256=null))
            val before = f.store.commits().size
            fails { f.writer().publish() }; assertEquals(before,f.store.commits().size); assertFalse(f.pending().isEmpty())
            f.db.backupJournalDao().putFingerprint(fp)
            // Fingerprint with no reusable reference must verify bytes, never trust old claims.
            f.db.backupJournalDao().removeBinaryReference(account,"pdf")
            fails { f.writer().publish() }; assertEquals(before,f.store.commits().size)
            f.db.attachmentDao().deleteByIds(listOf("pdf"))
            f.writer().publish(); assertTrue(f.read().binaries!!.isEmpty()); assertNull(f.db.backupJournalDao().binaryReference(account,"pdf"))
        }
    }

    @Test fun restartAtEveryDeltaPublicationBoundaryRecoversSameOperationAndAtomicCompletion() = runBlocking {
        val points = listOf("PUBLISHING","CREATED_DELTA","VERIFIED_DELTA","CREATED_COMMIT","VERIFIED_COMMIT","COMMIT_VERIFIED","BEFORE_COMPLETION","IN_COMPLETION_TRANSACTION","COMPLETE")
        for(point in points) Fixture().use { f ->
            f.root(); val parent = f.binding(); f.edit("Crash $point العربية")
            fails { f.writer { if(it==point) error("Simulated process loss") }.publish() }
            val p = f.db.openHelper.writableDatabase.query("SELECT operationId,status FROM backup_graph_publications WHERE operationId != '${parent.commitId}'").use { it.moveToFirst(); it.getString(0) to it.getString(1) }
            if(point!="COMPLETE") { assertEquals(parent,f.binding()); assertEquals(1,f.pending().size) }
            else assertTrue(f.pending().isEmpty())
            val filesBefore = f.store.commits().size
            f.reopen()
            val result = f.writer().resume(p.first)
            assertEquals(p.first,result.commitId); assertTrue(f.pending().isEmpty()); assertEquals("COMPLETE",f.db.backupGraphDao().publication(account,p.first)!!.status)
            val binding = f.binding(); f.writer().resume(p.first); assertEquals(binding,f.binding())
            if(point in listOf("CREATED_COMMIT","VERIFIED_COMMIT","COMMIT_VERIFIED","BEFORE_COMPLETION","IN_COMPLETION_TRANSACTION","COMPLETE")) assertEquals(filesBefore,f.store.commits().size)
            assertEquals(2,f.store.commits().size)
            val objects = f.db.backupGraphDao().objects(account,p.first); assertEquals("COMMIT",objects.last().role)
        }
    }

    @Test fun rootAndBinaryCrashRecoveryNeverAcknowledgesBeforeVerifiedCommit() = runBlocking {
        for(point in listOf("CREATED_BINARY","VERIFIED_BINARY","CREATED_METADATA","VERIFIED_METADATA","CREATED_CHECKPOINT","VERIFIED_CHECKPOINT","CREATED_COMMIT")) Fixture().use { f ->
            f.note(); f.binary(4096); val prepared = f.prepared()
            fails { f.writer { if(it==point) error("Simulated loss") }.createRoot(prepared) }
            assertFalse(f.db.backupJournalDao().account(account)!!.trusted); assertFalse(f.pending().isEmpty()); assertNull(f.db.backupGraphDao().binding(account,lineage))
            val op = f.db.backupGraphDao().unfinished(account).single().operationId
            f.reopen(); f.writer().resume(op)
            assertEquals(op,f.binding().commitId); assertEquals(f.binding().checkpointId,f.binding().deltaHeadId)
            assertTrue(f.pending().isEmpty()); assertEquals(4096L,f.read().binaries!!.single().size)
        }
    }

    @Test fun corruptReceiptsStagedLossMissingParentAndRestoreInvalidationFailClosed() = runBlocking {
        for(mode in listOf("missing-stage","corrupt-stage","conflicting-object","bad-receipt","restore","missing-parent")) Fixture().use { f ->
            f.root(); val parent=f.binding(); f.edit("Pending العربية")
            fails { f.writer { if(it=="PUBLISHING") error("Pause") }.publish() }
            val p = f.db.backupGraphDao().unfinished(account).single()
            val objects = f.db.backupGraphDao().objects(account,p.operationId); val delta = objects.single { it.role=="DELTA" }
            when(mode) {
                "missing-stage" -> File(delta.stagedPath).delete()
                "corrupt-stage" -> File(delta.stagedPath).writeText("broken")
                "conflicting-object" -> File(f.store.directory,delta.objectId).writeText("different immutable bytes")
                "bad-receipt" -> f.db.openHelper.writableDatabase.execSQL("UPDATE backup_graph_publication_objects SET verifiedSha256='bad' WHERE objectId=?",arrayOf(delta.objectId))
                "restore" -> f.journal.withRestoreOrigin { f.edit("Restored") }
                "missing-parent" -> File(f.store.directory,parent.commitFileId).delete()
            }
            f.reopen(); fails { f.writer().resume(p.operationId) }
            assertEquals(parent,f.binding()); assertFalse(f.pending().isEmpty())
        }
    }

    @Test fun concurrentSiblingsSurviveAndAccountIsolationIsStrict() = runBlocking {
        Fixture().use { a ->
            a.root(); val parent=a.binding()
            Fixture(a.store).use { b ->
                b.note(); b.journal.registerAccount(account)
                b.db.withTransaction {
                    b.db.backupJournalDao().acknowledge(account,b.db.backupJournalDao().clock().generation)
                    b.db.backupJournalDao().trust(account,parent.checkpointId,parent.deltaHeadId,parent.checkpointFileId,parent.checkpointSha256)
                    b.db.backupGraphDao().putBinding(parent.copy(originEpoch=b.db.backupJournalDao().clock().originEpoch))
                }
                a.edit("Client A العربية"); b.edit("Client B English")
                var publishedA: String? = null
                val rb = b.writer { if(it=="PUBLISHING") publishedA=a.writer().publish().commitId }.publish()
                assertTrue(rb.forkDetected)
                val graph=a.graph(a.store.commits()); assertEquals(GraphStatus.FORK,graph.status)
                assertEquals(setOf(publishedA,rb.commitId),graph.tips.toSet()); assertEquals(3,graph.commits.size)
                assertTrue(graph.commits.values.filter { it.kind=="delta" }.all { it.parents.single().commitId==parent.commitId })
                a.edit("Third branch blocked"); fails { a.writer().publish() }
                a.export("fork",JSONObject().put("expectedStatus","FORK"))
                val wrong = object : DisposableGraphObjectStore by a.store { override val context=GraphWriterContext("graph-b@example.com","other-permission",lineage) }
                val writer = InternalBackupGraphWriter(a.db,a.journal,PendingBackupCapture(a.db,a.journal,VaultPreferences(base,a.journal)),base.filesDir,File(a.root,"other-staging"),wrong)
                fails { writer.resume(rb.operationId!!) }; fails { writer.publish() }
                assertNull(a.db.backupGraphDao().binding("graph-b@example.com",lineage))
                assertFalse(a.pending().isEmpty())
            }
        }
    }

    @Test fun uncertainCreateResponseDoesNotDuplicateCommitAndGatesStayDisabled() = runBlocking {
        Fixture().use { f ->
            fails { f.writer().publish() }; f.root(); f.edit("Lost create response")
            var failed = false
            f.store.afterCreate = { _,role -> if(role=="COMMIT" && !failed) { failed=true; error("Response lost") } }
            fails { f.writer().publish() }
            val p = f.db.backupGraphDao().unfinished(account).single(); assertEquals(2,f.store.commits().size)
            f.reopen(); f.store.afterCreate=null
            f.writer().resume(p.operationId); assertEquals(2,f.store.commits().size); assertTrue(f.pending().isEmpty())
            assertEquals("COMMIT",f.store.events.last())
            assertFalse(BackupGraphPublicationEnabled); assertFalse(IncrementalBackupPublicationEnabled)
        }
    }

    @Test fun newAttachmentAndLostBinaryOrDeltaResponseRecoverExactObjects() = runBlocking {
        for (role in listOf("BINARY", "DELTA")) Fixture().use { f ->
            f.root(); val parent = f.binding()
            f.binary(8192, "new-attachment")
            var interrupted = false
            f.store.afterCreate = { _, createdRole ->
                if (createdRole == role && !interrupted) { interrupted = true; error("Disposable lost response") }
            }
            fails { f.writer().publish() }
            assertTrue(interrupted)
            assertEquals(parent, f.binding())
            assertEquals(1, f.store.commits().size)
            assertEquals(1, f.pending().size)
            val publication = f.db.backupGraphDao().unfinished(account).single()
            val intended = f.db.backupGraphDao().objects(account, publication.operationId)
            assertEquals(listOf("BINARY", "DELTA", "COMMIT"), intended.map { it.role })
            val createdBefore = f.store.bytes().keys.toSet()
            f.reopen(); f.store.afterCreate = null
            val result = f.writer().resume(publication.operationId)
            assertEquals(publication.operationId, result.commitId)
            assertEquals(0, result.metrics.binariesCreated)
            assertEquals(2, f.store.commits().size)
            assertTrue(f.store.bytes().keys.containsAll(createdBefore))
            assertEquals(1, f.store.events.count { it == "BINARY" })
            assertTrue(f.pending().isEmpty())
            val binary = f.read().binaries!!.single()
            assertEquals("new-attachment", binary.attachmentId)
            assertEquals(8192L, binary.size)
            assertEquals(IncrementalBackupFormat.sha256(ByteArray(8192) { 82 }), binary.sha256)
            val after = f.store.bytes().keys.toSet()
            f.writer().resume(publication.operationId)
            assertEquals(after, f.store.bytes().keys)
        }
        Fixture().use { f ->
            f.root(); f.binary(8192, "new-attachment")
            val result = f.writer().publish()
            assertEquals(GraphWriterMetrics(1, 1, 1, 1, 1, 1), result.metrics)
            assertEquals(8192L, f.read().binaries!!.single().size)
            f.export("new-attachment", JSONObject().put("expectedBody", "Original العربية").put("expectedNoteCount", 1))
        }
    }

    @Test fun lostAcknowledgementWithNewerEditAndBinaryOrphanRecovery() = runBlocking {
        Fixture().use { f ->
            f.root(); f.edit("Frozen N")
            fails { f.writer { if(it=="VERIFIED_COMMIT") error("Lost local acknowledgement") }.publish() }
            val p=f.db.backupGraphDao().unfinished(account).single()
            f.edit("Newer N+1 العربية"); f.reopen()
            f.writer().resume(p.operationId)
            assertEquals(1,f.pending().size)
            assertEquals("Frozen N",JSONArray(f.read().files.getValue("notes.json")).getJSONObject(0).getString("bodyPlainText"))
            f.writer().publish(); assertTrue(f.pending().isEmpty())
        }
        Fixture().use { f ->
            f.note(); f.binary(4096); f.writer().createRoot(f.prepared()); val parent=f.binding()
            f.binary(8192)
            fails { f.writer { if(it=="CREATED_BINARY") error("Binary published; process stopped") }.publish() }
            assertEquals(parent,f.binding()); assertEquals(GraphStatus.SINGLE_TIP,f.graph(f.store.commits()).status)
            assertEquals(4096L,f.read().binaries!!.single().size)
            val op=f.db.backupGraphDao().unfinished(account).single().operationId
            f.reopen(); val recovered=f.writer().resume(op)
            assertEquals(0,recovered.metrics.binariesCreated); assertEquals(8192L,f.read().binaries!!.single().size)
        }
    }

    @Test fun failedReadbackAndUnsupportedGraphCannotComplete() = runBlocking {
        for(role in listOf("DELTA","COMMIT")) Fixture().use { f ->
            f.root(); val parent=f.binding(); f.edit("Must remain pending")
            f.store.afterCreate = { id,createdRole -> if(createdRole==role) File(f.store.directory,id).writeText("corrupt readback") }
            fails { f.writer().publish() }
            assertEquals(parent,f.binding()); assertEquals(1,f.pending().size)
            assertTrue(f.store.commits().any { it.objectRef.cloudFileId==parent.commitFileId && it.objectRef.sha256==parent.commitSha256 })
            if(role=="DELTA") assertEquals(GraphStatus.SINGLE_TIP,f.graph(f.store.commits()).status)
        }
        Fixture().use { f ->
            f.root(); f.edit("Unsupported graph blocks ordinary publication")
            val raw=JSONObject(f.db.backupGraphDao().publication(account,f.binding().commitId)!!.commitJson)
                .put("commitId",UUID.randomUUID().toString()).put("requiredReaders",JSONArray(listOf(BackupGraphCapability,"future-reader")))
            val file=File(f.root,"unsupported.json").apply { writeText(raw.toString()) }
            f.store.create("unsupported-commit","COMMIT",file)
            assertEquals(GraphStatus.UNSUPPORTED,f.graph(f.store.commits()).status)
            fails { f.writer().publish() }; assertTrue(f.db.backupGraphDao().unfinished(account).isEmpty())
        }
    }
}

/** Run prepare/recover as separate instrumentations with an intervening process stop on the disposable emulator. */
@RunWith(AndroidJUnit4::class)
class BackupGraphProcessRestartRoomTest {
    @Test fun exactPublicationSurvivesRealProcessRestart() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        val phase=requireNotNull(args.getString("graphProbePhase"))
        val name=requireNotNull(args.getString("graphProbeDb"))
        check(name.startsWith("graph-process-disposable-") && name.endsWith(".db"))
        val f=BackupGraphWriterRoomTest().Fixture(nameOverride=name)
        val pid=File(f.root,"preparation-process-id")
        if(phase=="prepare") {
            try {
                f.root(); f.edit("Frozen before process death العربية")
                var interrupted=false
                try { f.writer { if(it=="VERIFIED_COMMIT") error("Simulated interruption before completion") }.publish() }
                catch (_: IllegalStateException) { interrupted=true }
                assertTrue(interrupted)
                assertEquals(1,f.pending().size); assertEquals(1,f.db.backupGraphDao().unfinished("graph-a@example.com").size)
                pid.writeText(android.os.Process.myPid().toString())
            } finally { f.db.close() } // Deliberately preserve only this disposable recovery fixture.
        } else {
            check(phase=="recover")
            try {
                assertNotEquals(pid.readText().toInt(),android.os.Process.myPid())
                val op=f.db.backupGraphDao().unfinished("graph-a@example.com").single().operationId
                f.edit("Newer after restart N+1")
                val result=f.writer().resume(op)
                assertEquals(op,result.commitId); assertEquals(2,f.store.commits().size); assertEquals(1,f.pending().size)
                assertEquals("Frozen before process death العربية",JSONArray(f.read().files.getValue("notes.json")).getJSONObject(0).getString("bodyPlainText"))
                val binding=f.binding(); f.writer().resume(op); assertEquals(binding,f.binding())
            } finally { f.close() }
        }
    }
}
