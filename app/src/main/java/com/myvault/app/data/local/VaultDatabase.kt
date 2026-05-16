package com.myvault.app.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.AiConversationEntity
import com.myvault.app.data.local.entity.AiMessageEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteFtsEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.TagEntity

@Database(
    entities = [
        FolderEntity::class,
        NoteEntity::class,
        BlockEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        AttachmentEntity::class,
        NoteFtsEntity::class,
        NoteTableEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao
    abstract fun blockDao(): BlockDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun searchDao(): SearchDao
    abstract fun noteTableDao(): NoteTableDao
    abstract fun aiConversationDao(): AiConversationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE folders ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE attachments ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_tables (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        rowCount INTEGER NOT NULL,
                        columnCount INTEGER NOT NULL,
                        cellsJson TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_conversations (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        noteId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        action TEXT,
                        provider TEXT,
                        model TEXT,
                        selectedTextContext TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
