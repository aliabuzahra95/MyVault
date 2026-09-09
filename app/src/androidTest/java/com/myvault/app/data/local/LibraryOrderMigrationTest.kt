package com.myvault.app.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryOrderMigrationTest {
    @Test fun production29MigratesWithoutChangingExistingColumns() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "library-order-migration-disposable.db"
        context.deleteDatabase(name)
        val schema = JSONObject(instrumentation.context.assets.open("com.myvault.app.data.local.VaultDatabase/29.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}", table))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val old = helper.writableDatabase
        old.execSQL("INSERT INTO folders (id,parentId,name,description,orderIndex,isFavourite,mode,createdAt,updatedAt,deletedAt,colorKey) VALUES ('folder',NULL,'Folder',NULL,0,0,'library',10,20,NULL,NULL)")
        listOf("z" to "Zulu.pdf", "b" to "alpha.pdf", "a" to "Alpha.pdf").forEach { (id, title) ->
            old.execSQL("INSERT INTO attachments (id,noteId,libraryFolderId,fileName,mimeType,sizeBytes,localPath,remoteUrl,isPinned,createdAt,deletedAt) VALUES (?, '', NULL, ?, 'application/pdf', 12, ?, NULL, 0, 42, NULL)", arrayOf(id, title, "/unchanged/$id.pdf"))
        }
        old.execSQL("INSERT INTO pdf_reading_progress (attachmentId,pageIndex,pageCount,progressPercent,lastOpenedAt,updatedAt) VALUES ('a',3,20,20.0,99,100)")
        old.execSQL("INSERT INTO pdf_annotations (id,attachmentId,libraryFolderId,pageIndex,`left`,`top`,`right`,`bottom`,color,noteText,selectedText,annotationType,textSize,backgroundColor,displayTitle,displayFolderId,createdAt,updatedAt) VALUES ('annotation','a',NULL,3,0.1,0.2,0.4,0.5,'yellow','Preserve','Text','highlight',12,'',NULL,NULL,44,45)")
        fun rows(db: SupportSQLiteDatabase, table: String, columns: String = "*"): List<List<String?>> = db.query("SELECT $columns FROM $table ORDER BY 1").use { c ->
            buildList { while(c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) }
        }
        val attachmentColumns = "id,noteId,libraryFolderId,fileName,mimeType,sizeBytes,localPath,remoteUrl,isPinned,createdAt,deletedAt"
        val attachments = rows(old,"attachments",attachmentColumns)
        val folders = rows(old,"folders")
        val annotations = rows(old,"pdf_annotations")
        val progress = rows(old,"pdf_reading_progress")
        helper.close()
        val db = Room.databaseBuilder(context,VaultDatabase::class.java,name).addMigrations(VaultDatabase.MIGRATION_29_30).build()
        try {
            val migrated = db.openHelper.writableDatabase // Room validates the actual exported schema.
            assertEquals(attachments,rows(migrated,"attachments",attachmentColumns))
            assertEquals(folders,rows(migrated,"folders"))
            assertEquals(annotations,rows(migrated,"pdf_annotations"))
            assertEquals(progress,rows(migrated,"pdf_reading_progress"))
            assertEquals(listOf("a","b","z"),db.attachmentDao().getAll().sortedBy { it.orderIndex }.map { it.id })
            assertEquals(listOf(1,2,3),db.attachmentDao().getAll().mapNotNull { it.orderIndex }.sorted())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
