package com.myvault.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.repository.readLocalBackupReadiness
import com.myvault.app.data.repository.BackupChangeJournal
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupGraphReadinessRoomTest {
    @Test fun schema33UpgradePreservesExistingContentAndFresh36Reopens() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "readiness-upgrade-disposable-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = instrumentation.context.assets.open("com.myvault.app.data.local.VaultDatabase/33.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        var room: VaultDatabase? = null
        fun open() = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabase.ALL_MIGRATIONS).addCallback(VaultDatabase.BACKUP_JOURNAL_CALLBACK).build()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.getJSONArray("indices")
                    for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                raw.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(schema.getString("identityHash")))
                raw.execSQL("INSERT INTO notes VALUES ('existing-readiness',NULL,NULL,'Existing','Preserve exactly',0,0,0,0,10,11,NULL)")
                raw.version = 33
            }
            room = open()
            assertEquals(36, room.openHelper.writableDatabase.version)
            assertEquals("Preserve exactly", room.noteDao().getAllIncludingDeleted().single().bodyPlainText)
            assertNull(readLocalBackupReadiness(room, "a@example.com").position)
            room.close()
            room = open()
            assertEquals(36, room.openHelper.writableDatabase.version)
            assertEquals("Preserve exactly", room.noteDao().getAllIncludingDeleted().single().bodyPlainText)
        } finally {
            room?.close()
            context.deleteDatabase(name)
        }
    }
    @Test fun diagnosticDoesNotEnrollAcknowledgeOrRewriteUserData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "readiness-disposable-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabase.ALL_MIGRATIONS).addCallback(VaultDatabase.BACKUP_JOURNAL_CALLBACK).build()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO notes VALUES ('disposable-readiness',NULL,NULL,'English','English',0,0,0,0,10,11,NULL)")
            val dao = db.backupJournalDao()
            val clock = dao.clock()
            val unassigned = dao.pending(BackupJournalSchema.Unassigned)
            val first = readLocalBackupReadiness(db, "a@example.com")
            assertTrue(first.hasData)
            assertEquals(1, first.pendingCount)
            assertNull(first.position)
            assertFalse(first.proofValid)
            assertEquals(clock, dao.clock())
            assertEquals(unassigned, dao.pending(BackupJournalSchema.Unassigned))
            assertNull(dao.account("a@example.com"))
            BackupChangeJournal(db).registerAccount("a@example.com")
            BackupChangeJournal(db).registerAccount("b@example.com")
            db.noteDao().updateTitle("disposable-readiness", "Renamed", 12)
            val a = dao.pending("a@example.com")
            val b = dao.pending("b@example.com")
            assertEquals(1, readLocalBackupReadiness(db, "a@example.com").pendingCount)
            assertEquals(1, readLocalBackupReadiness(db, "b@example.com").pendingCount)
            assertEquals(a, dao.pending("a@example.com"))
            assertEquals(b, dao.pending("b@example.com"))
            assertFalse(dao.account("a@example.com")!!.trusted)
            assertEquals("Renamed", db.noteDao().getAllIncludingDeleted().single().title)
            assertEquals(36, sql.version)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
