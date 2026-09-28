package com.myvault.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.repository.BackupChangeJournal
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on a disposable emulator/test installation, never the user's production Vault. */
@RunWith(AndroidJUnit4::class)
class BackupJournalRoomTest {
    @Test fun schema32MigratesAndJournalSurvivesRoomReopen() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "backup-journal-disposable-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = instrumentation.context.assets.open("com.myvault.app.data.local.VaultDatabase/32.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        val richText = """{"text":"English العربية","styleMarks":[{"start":0,"end":7,"style":"bold"}],"noteLinks":[]}"""
        fun open() = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabase.ALL_MIGRATIONS)
            .addCallback(VaultDatabase.BACKUP_JOURNAL_CALLBACK).build()
        var room: VaultDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    entity.optJSONArray("indices")?.let { indices ->
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    entity.optJSONArray("contentSyncTriggers")?.let { triggers ->
                        for (j in 0 until triggers.length()) db.execSQL(triggers.getString(j))
                    }
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
                db.execSQL("INSERT INTO notes VALUES ('existing-note',NULL,NULL,'عنوان English','English العربية',0,0,0,0,10,11,NULL)")
                db.execSQL("INSERT INTO blocks VALUES ('existing-block','existing-note','rich_text',?,0)", arrayOf(richText))
                db.version = 32
            }
            room = open()
            val first = room
            assertEquals(34, first.openHelper.writableDatabase.version)
            assertEquals("English العربية", first.noteDao().getAllIncludingDeleted().single().bodyPlainText)
            first.openHelper.writableDatabase.query("SELECT content FROM blocks WHERE id='existing-block'").use {
                assertTrue(it.moveToFirst()); assertEquals(richText, it.getString(0))
            }
            val journal = BackupChangeJournal(first)
            journal.registerAccount("a@example.com")
            journal.registerAccount("b@example.com")
            assertTrue(first.backupJournalDao().pending("a@example.com").isEmpty())
            assertFalse(first.backupJournalDao().account("a@example.com")!!.trusted)
            repeat(20) { first.noteDao().updateTitle("existing-note", "Renamed $it", 12L + it) }
            val captured = first.backupJournalDao().clock().generation
            assertEquals(1, first.backupJournalDao().pending("a@example.com").size)
            first.noteDao().updateTitle("existing-note", "Newer edit", 40)
            first.backupJournalDao().acknowledge("a@example.com", captured)
            assertEquals(1, first.backupJournalDao().pending("a@example.com").size)
            assertEquals(1, first.backupJournalDao().pending("b@example.com").size)
            first.close()
            room = open()
            assertEquals("Newer edit", room.noteDao().getAllIncludingDeleted().single().title)
            assertEquals(1, room.backupJournalDao().pending("a@example.com").size)
            assertEquals("existing-note", room.backupJournalDao().pending("a@example.com").single().key0)
        } finally {
            room?.close()
            context.deleteDatabase(name)
        }
    }
}
