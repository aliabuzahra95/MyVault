package com.myvault.app.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.AiConversationDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.NoteVersionDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.AiConversationEntity
import com.myvault.app.data.local.entity.AiMessageEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.KnowledgeTagEntity
import com.myvault.app.data.local.entity.KnowledgeTagLinkEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteFtsEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteVersionEntity
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.local.entity.SourceBacklinkEntity
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
        PdfReadingProgressEntity::class,
        PdfAnnotationEntity::class,
        SourceBacklinkEntity::class,
        KnowledgeTagEntity::class,
        KnowledgeTagLinkEntity::class,
        NoteVersionEntity::class,
    ],
    version = 13,
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
    abstract fun pdfReadingProgressDao(): PdfReadingProgressDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun sourceBacklinkDao(): SourceBacklinkDao
    abstract fun knowledgeTagDao(): KnowledgeTagDao
    abstract fun noteVersionDao(): NoteVersionDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN mode TEXT NOT NULL DEFAULT 'study'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN libraryFolderId TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_reading_progress (
                        attachmentId TEXT NOT NULL PRIMARY KEY,
                        pageIndex INTEGER NOT NULL,
                        pageCount INTEGER NOT NULL,
                        progressPercent REAL NOT NULL,
                        lastOpenedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_annotations (
                        id TEXT NOT NULL PRIMARY KEY,
                        attachmentId TEXT NOT NULL,
                        libraryFolderId TEXT,
                        pageIndex INTEGER NOT NULL,
                        left REAL NOT NULL,
                        top REAL NOT NULL,
                        right REAL NOT NULL,
                        bottom REAL NOT NULL,
                        color TEXT NOT NULL,
                        noteText TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN displayTitle TEXT")
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN displayFolderId TEXT")
                db.execSQL("UPDATE pdf_annotations SET displayFolderId = libraryFolderId WHERE displayFolderId IS NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_backlinks (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        attachmentId TEXT NOT NULL,
                        annotationId TEXT,
                        pageIndex INTEGER NOT NULL,
                        left REAL,
                        top REAL,
                        right REAL,
                        bottom REAL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_backlinks_noteId ON source_backlinks(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_backlinks_attachmentId ON source_backlinks(attachmentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_backlinks_annotationId ON source_backlinks(annotationId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_tags_name ON knowledge_tags(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_tag_links (
                        tagId TEXT NOT NULL,
                        targetType TEXT NOT NULL,
                        targetId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(tagId, targetType, targetId)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_tag_links_tagId ON knowledge_tag_links(tagId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_tag_links_targetType_targetId ON knowledge_tag_links(targetType, targetId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId_orderIndex ON folders(parentId, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_mode_parentId_orderIndex ON folders(mode, parentId, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_deletedAt_orderIndex ON folders(deletedAt, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId_deletedAt_updatedAt ON notes(folderId, deletedAt, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_deletedAt_updatedAt ON notes(deletedAt, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isPinned_deletedAt_updatedAt ON notes(isPinned, deletedAt, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_noteId_deletedAt_createdAt ON attachments(noteId, deletedAt, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_libraryFolderId_deletedAt_createdAt ON attachments(libraryFolderId, deletedAt, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_deletedAt_createdAt ON attachments(deletedAt, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_isPinned ON attachments(isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blocks_noteId_orderIndex ON blocks(noteId, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tables_noteId_orderIndex ON note_tables(noteId, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_tagName ON note_tags(tagName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_conversations_noteId_updatedAt ON ai_conversations(noteId, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_messages_conversationId_createdAt ON ai_messages(conversationId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_messages_noteId_createdAt ON ai_messages(noteId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_reading_progress_lastOpenedAt ON pdf_reading_progress(lastOpenedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_attachmentId_pageIndex_createdAt ON pdf_annotations(attachmentId, pageIndex, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_libraryFolderId_updatedAt ON pdf_annotations(libraryFolderId, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_displayFolderId_updatedAt ON pdf_annotations(displayFolderId, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_updatedAt ON pdf_annotations(updatedAt)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_versions (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        bodyPlainText TEXT NOT NULL,
                        richTextJson TEXT,
                        richHtml TEXT,
                        wordCount INTEGER NOT NULL,
                        characterCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_versions_noteId_createdAt ON note_versions(noteId, createdAt)")
            }
        }
    }
}
