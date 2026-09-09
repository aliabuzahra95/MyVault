package com.myvault.app.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.BackupRepository
import com.myvault.app.data.repository.FolderRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class LibraryOrderBackupTest {
    @Test fun realBackupRestorePreservesFilesAndPdfStateWithOptionalOrder() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "library-order-backup-disposable-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
            override fun getCacheDir() = File(root,"cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context,VaultDatabase::class.java).build()
        val repo = BackupRepository(context,db,db.folderDao(),db.folderStickyNoteDao(),db.noteDao(),db.blockDao(),db.courseDao(),db.tagDao(),db.attachmentDao(),db.searchDao(),db.noteTableDao(),db.noteVersionDao(),db.pdfReadingProgressDao(),db.pdfAnnotationDao(),db.pdfAnnotationSegmentDao(),db.sourceBacklinkDao(),db.knowledgeTagDao(),VaultPreferences(context))
        val organiser = FolderRepository(db,db.folderDao(),db.folderStickyNoteDao(),db.noteDao(),db.attachmentDao(),db.blockDao(),db.tagDao(),db.noteTableDao(),db.noteVersionDao(),db.pdfAnnotationDao(),db.pdfReadingProgressDao(),db.sourceBacklinkDao(),db.knowledgeTagDao())
        val folder = FolderEntity("folder",null,"Folder",orderIndex=0,isFavourite=false,mode=FOLDER_MODE_LIBRARY,createdAt=10,updatedAt=11)
        val nested = folder.copy(id="nested",parentId="folder",name="Nested")
        val files = listOf("z","a","nested-file").mapIndexed { index, id ->
            val bytes = "Unchanged binary fixture $id".toByteArray()
            val path = File(context.filesDir,"$id.pdf").apply { writeBytes(bytes) }
            AttachmentEntity(id,"",if(index==2) "folder" else null,"$id.pdf","application/pdf",bytes.size.toLong(),path.absolutePath,null,createdAt=50L+index,orderIndex=index+1)
        }
        val annotation = PdfAnnotationEntity("ann","a",null,2,0.1f,0.2f,0.5f,0.6f,"yellow","Preserve note",selectedText="Preserve text",createdAt=60,updatedAt=61)
        val progress = PdfReadingProgressEntity("a",2,10,0.3f,70,71)
        try {
            db.folderDao().upsertAll(listOf(folder,nested)); db.attachmentDao().upsertAll(files)
            db.pdfAnnotationDao().upsert(annotation); db.pdfReadingProgressDao().upsert(progress)
            organiser.reorderLibrarySiblings(listOf("a","folder","z"))
            organiser.reorderLibrarySiblings(listOf("nested-file","nested"))
            assertEquals(11L,db.folderDao().getAll().first { it.id=="folder" }.updatedAt)
            assertEquals(progress,db.pdfReadingProgressDao().getByAttachmentId("a"))
            assertEquals(annotation,db.pdfAnnotationDao().getAll().single())
            val ordered = db.attachmentDao().getAll().associateBy { it.id }
            val orderedFolders = db.folderDao().getAll().sortedBy { it.id }
            val archive = File(root,"backup.vaultbackup")
            repo.exportBackupToFile(archive)
            val contents = ZipFile(archive).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() } }
            val metadata = JSONArray(contents.getValue("attachments.json").decodeToString())
            assertEquals(3,metadata.length())
            for(i in 0 until metadata.length()) assertEquals(ordered.getValue(metadata.getJSONObject(i).getString("id")).orderIndex,metadata.getJSONObject(i).getInt("orderIndex"))
            organiser.reorderLibrarySiblings(listOf("z","folder","a"))
            repo.restoreBackupFromFile(archive)
            suspend fun verifyPreserved() {
                val actual = db.attachmentDao().getAll().associateBy { it.id }
                assertEquals(ordered.keys,actual.keys)
                files.forEach { original ->
                    val restored = actual.getValue(original.id)
                    assertEquals(original.libraryFolderId,restored.libraryFolderId)
                    assertEquals(original.createdAt,restored.createdAt)
                    assertEquals(original.fileName,restored.fileName)
                    assertArrayEquals(File(original.localPath).readBytes(),File(restored.localPath).readBytes())
                }
                assertEquals(annotation,db.pdfAnnotationDao().getAll().single())
                assertEquals(progress,db.pdfReadingProgressDao().getByAttachmentId("a"))
                assertEquals(orderedFolders,db.folderDao().getAll().sortedBy { it.id })
            }
            verifyPreserved()
            assertEquals(ordered.mapValues { it.value.orderIndex },db.attachmentDao().getAll().associate { it.id to it.orderIndex })
            // Exercise the production reader with old/Web-style omitted and invalid values.
            for(i in 0 until metadata.length()) {
                val row = metadata.getJSONObject(i)
                if(row.getString("id")=="a") row.remove("orderIndex") else row.put("orderIndex","invalid")
            }
            val legacy = File(root,"legacy.vaultbackup")
            ZipOutputStream(legacy.outputStream()).use { zip -> contents.forEach { (name,bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(if(name=="attachments.json") metadata.toString().toByteArray() else bytes); zip.closeEntry()
            } }
            repo.restoreBackupFromFile(legacy)
            verifyPreserved()
            val fallback = db.attachmentDao().getAll().associate { it.id to it.orderIndex }
            assertTrue(fallback.getValue("a")!! > orderedFolders.first { it.id=="folder" }.orderIndex)
            assertTrue(fallback.getValue("z")!! > fallback.getValue("a")!!)
            repo.restoreBackupFromFile(legacy)
            assertEquals(fallback,db.attachmentDao().getAll().associate { it.id to it.orderIndex })
        } finally { db.close() }
    }
}
