package com.myvault.app.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicitly disposable Android installation only. No real Drive or production Vault. */
@RunWith(AndroidJUnit4::class)
class BackupLocalCaptureRoomTest {
    @Test fun captureBaselineFingerprintsAndAccountIsolationWithRealRoom() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "backup-local-capture-disposable-${System.nanoTime()}.db"
        val root = File(base.cacheDir, "backup-local-capture-disposable-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        fun open() = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabase.ALL_MIGRATIONS).addCallback(VaultDatabase.BACKUP_JOURNAL_CALLBACK).build()
        var db = open()
        try {
            val journal = BackupChangeJournal(db)
            val preferences = VaultPreferences(context, journal)
            val binaries = BackupBinaryStore(context, db, journal)
            val capture = PendingBackupCapture(db, journal, preferences)
            journal.registerAccount("a@example.com"); journal.registerAccount("b@example.com")
            assertTrue(capture.capture("a@example.com").queriedRecords.isEmpty())
            val note = NoteEntity("n", null, title = "عنوان English", bodyPlainText = "English العربية", isPinned = false,
                isFavourite = false, orderIndex = 0, createdAt = 10, updatedAt = 11)
            db.noteDao().upsertAll(listOf(note))
            val file = File(context.filesDir, "attachments/n/pdf.pdf")
            val bytes = "Disposable PDF bytes العربية".toByteArray()
            val digest = binaries.writeNew(file, bytes.inputStream())
            val attachment = AttachmentEntity("pdf", "n", fileName = "pdf.pdf", mimeType = "application/pdf", sizeBytes = digest.size,
                localPath = file.absolutePath, remoteUrl = null, createdAt = 10)
            db.attachmentDao().upsertAll(listOf(attachment)); binaries.persistWritten(attachment, digest)
            assertEquals(digest.sha256, db.backupJournalDao().fingerprint("pdf")!!.sha256)
            val pending = capture.capture("a@example.com")
            assertEquals(2, pending.records.size); assertEquals(2, pending.queriedRecords.size)
            assertEquals(digest.sha256, pending.binaries.single().sha256)
            assertNull(pending.binaries.single().reusableCloudFileId)
            val repo = BackupRepository(context,db,db.folderDao(),db.folderStickyNoteDao(),db.noteDao(),db.blockDao(),db.courseDao(),db.tagDao(),db.attachmentDao(),db.searchDao(),db.noteTableDao(),db.noteVersionDao(),db.pdfReadingProgressDao(),db.pdfAnnotationDao(),db.pdfAnnotationSegmentDao(),db.sourceBacklinkDao(),db.knowledgeTagDao(),preferences)
            val staged = BackupBaselinePreparer(context,db,journal,preferences,repo,binaries).prepare("a@example.com")
            val entries = staged.objects.mapIndexed { index, obj -> JSONObject().put("path", obj.path).put("backupEntry", obj.backupEntry)
                .put("kind",obj.kind).put("size",obj.byteSize).put("sha256",obj.sha256).put("cloudFileId","fixture-$index") }
            val manifest = JSONObject().put("schemaVersion",1).put("storage","google-drive-api").put("cloudVersion",123)
                .put("entries",JSONArray(entries)).toString().toByteArray()
            val verified = staged.objects.mapIndexed { index, obj -> obj.file.inputStream().use { VerifiedRemoteBackupObject.read("a@example.com","fixture-$index",it) } }
            val proof = VerifiedBackupBaseline.verify(staged,"a@example.com","fixture-manifest",manifest,manifest,verified)
            db.noteDao().updateTitle("n","Newer than capture",12)
            journal.establishVerifiedBaseline(staged,proof)
            assertTrue(db.backupJournalDao().account("a@example.com")!!.trusted)
            assertFalse(db.backupJournalDao().account("b@example.com")!!.trusted)
            val newer = capture.capture("a@example.com")
            assertEquals(1,newer.records.size); assertEquals(1,newer.queriedRecords.size)
            assertEquals("Newer than capture",JSONObject(newer.records.single().payloadJson!!).getString("title"))
            assertTrue(newer.journal.generation > staged.journal.generation)
            assertNull(db.backupJournalDao().binaryReference("b@example.com","pdf"))
            assertEquals(digest.sha256,db.backupJournalDao().binaryReference("a@example.com","pdf")!!.sha256)
            val showTitles = !preferences.userPreferences.first().showFullNoteTitles
            preferences.setShowFullNoteTitles(showTitles)
            assertEquals(0,capture.capture("a@example.com").settingsReadCount) // Not a field in historical settings.json.
            val accent = if (preferences.userPreferences.first().accentColor == "#123456") "#654321" else "#123456"
            preferences.setAccentColor(accent)
            val withSettings = capture.capture("a@example.com")
            assertEquals(1,withSettings.settingsReadCount)
            assertEquals(1,withSettings.queriedRecords.size)
            assertEquals(accent,JSONObject(withSettings.records.single { it.group == "settings.json" }.payloadJson!!).getString("accentColor"))
            val beforeRestore = db.backupJournalDao().pending("a@example.com")
            journal.withRestoreOrigin { db.noteDao().updateTitle("n","Restored fixture",13) }
            assertEquals(beforeRestore,db.backupJournalDao().pending("a@example.com"))
            assertFalse(db.backupJournalDao().account("a@example.com")!!.trusted)
            db.close(); db = open()
            assertEquals(digest.sha256,db.backupJournalDao().fingerprint("pdf")!!.sha256)
            assertEquals(proof.commit.checkpointId,db.backupJournalDao().account("a@example.com")!!.checkpointId)
            assertFalse(db.backupJournalDao().account("a@example.com")!!.trusted)
            val reopenedBinaries = BackupBinaryStore(context,db,BackupChangeJournal(db))
            val pendingBeforeFileWrite = db.backupJournalDao().pending("a@example.com")
            reopenedBinaries.invalidateBeforeRestoreWrite("pdf")
            assertEquals("UNKNOWN",db.backupJournalDao().fingerprint("pdf")!!.status)
            assertNull(db.backupJournalDao().fingerprint("pdf")!!.sha256)
            assertEquals(pendingBeforeFileWrite,db.backupJournalDao().pending("a@example.com"))
        } finally {
            db.close()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }
}
