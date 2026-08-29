package com.myvault.app.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.CourseDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.FolderStickyNoteDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.NoteVersionDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfAnnotationSegmentDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.dao.SearchDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.dao.TagDao
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.CourseFolderEntity
import com.myvault.app.data.local.entity.CourseNoteEntity
import com.myvault.app.data.local.entity.CourseStickyNoteEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.FolderStickyNoteEntity
import com.myvault.app.data.local.entity.KnowledgeTagEntity
import com.myvault.app.data.local.entity.KnowledgeTagLinkEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.NoteFtsEntity
import com.myvault.app.data.local.entity.NoteTableEntity
import com.myvault.app.data.local.entity.NoteVersionEntity
import com.myvault.app.data.local.entity.NoteTagCrossRef
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.local.entity.SourceBacklinkEntity
import com.myvault.app.data.local.entity.TagEntity

@Database(
    entities = [
        FolderEntity::class,
        FolderStickyNoteEntity::class,
        NoteEntity::class,
        BlockEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        AttachmentEntity::class,
        NoteFtsEntity::class,
        NoteTableEntity::class,
        PdfReadingProgressEntity::class,
        PdfAnnotationEntity::class,
        PdfAnnotationSegmentEntity::class,
        SourceBacklinkEntity::class,
        KnowledgeTagEntity::class,
        KnowledgeTagLinkEntity::class,
        NoteVersionEntity::class,
        CourseEntity::class,
        CourseFolderEntity::class,
        CourseNoteEntity::class,
        CourseStickyNoteEntity::class,
        CourseConceptCardEntity::class,
    ],
    version = 29,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun folderStickyNoteDao(): FolderStickyNoteDao
    abstract fun noteDao(): NoteDao
    abstract fun blockDao(): BlockDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun searchDao(): SearchDao
    abstract fun noteTableDao(): NoteTableDao
    abstract fun pdfReadingProgressDao(): PdfReadingProgressDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun pdfAnnotationSegmentDao(): PdfAnnotationSegmentDao
    abstract fun sourceBacklinkDao(): SourceBacklinkDao
    abstract fun knowledgeTagDao(): KnowledgeTagDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun courseDao(): CourseDao

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

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN parentNoteId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_parentNoteId_deletedAt_updatedAt ON notes(parentNoteId, deletedAt, updatedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folder_sticky_notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        folderId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folder_sticky_notes_folderId_updatedAt ON folder_sticky_notes(folderId, updatedAt)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN description TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isFolderPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId_parentNoteId_orderIndex ON notes(folderId, parentNoteId, orderIndex)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS courses (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        lastOpenedNoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS course_folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_course_folders_courseId_sortOrder ON course_folders(courseId, sortOrder)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS course_notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        folderId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastOpenedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_course_notes_courseId_folderId_sortOrder ON course_notes(courseId, folderId, sortOrder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_course_notes_lastOpenedAt ON course_notes(lastOpenedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS course_sticky_notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_course_sticky_notes_courseId_sortOrder ON course_sticky_notes(courseId, sortOrder)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS course_concept_cards (
                        id TEXT NOT NULL PRIMARY KEY,
                        courseId TEXT NOT NULL,
                        term TEXT NOT NULL,
                        arabicTerm TEXT,
                        definition TEXT NOT NULL,
                        details TEXT,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_course_concept_cards_courseId_sortOrder ON course_concept_cards(courseId, sortOrder)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN rootFolderId TEXT")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN annotationType TEXT NOT NULL DEFAULT 'highlight'")
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN textSize REAL NOT NULL DEFAULT 16")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN backgroundColor TEXT NOT NULL DEFAULT 'none'")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_chat_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        userQuery TEXT NOT NULL,
                        assistantAnswer TEXT NOT NULL,
                        attachedTitles TEXT NOT NULL,
                        modelId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_chat_history_createdAt ON home_chat_history(createdAt)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_ai_file_cache (
                        attachmentId TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        fileResourceName TEXT NOT NULL,
                        fileUri TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        uploadedAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        PRIMARY KEY(attachmentId, provider)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_ai_file_cache_expiresAt ON library_ai_file_cache(expiresAt)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_chat_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE home_chat_history ADD COLUMN messagesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE home_chat_history SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_home_chat_history_updatedAt ON home_chat_history(updatedAt)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_ai_file_cache ADD COLUMN lastVerifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE library_ai_file_cache SET lastVerifiedAt = uploadedAt WHERE lastVerifiedAt = 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS notes_fts")
                db.execSQL("DROP TRIGGER IF EXISTS notes_fts_after_insert")
                db.execSQL("DROP TRIGGER IF EXISTS notes_fts_after_delete")
                db.execSQL("DROP TRIGGER IF EXISTS notes_fts_after_update")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING FTS4(
                        title TEXT NOT NULL,
                        bodyPlainText TEXT NOT NULL,
                        content='notes',
                        tokenize=unicode61,
                        prefix='2,3,4'
                    )
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
                createNotesFtsTriggers(db)
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_pdf_text_cache (
                        attachmentId TEXT NOT NULL PRIMARY KEY,
                        localPath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        sourceModifiedAt INTEGER NOT NULL,
                        extractedText TEXT NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT NOT NULL,
                        extractedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS ai_messages")
                db.execSQL("DROP TABLE IF EXISTS ai_conversations")
                db.execSQL("DROP TABLE IF EXISTS home_chat_history")
                db.execSQL("DROP TABLE IF EXISTS library_ai_file_cache")
                db.execSQL("DROP TABLE IF EXISTS library_pdf_text_cache")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_annotations ADD COLUMN selectedText TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_annotation_segments (
                        annotationId TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        `left` REAL NOT NULL,
                        `top` REAL NOT NULL,
                        `right` REAL NOT NULL,
                        `bottom` REAL NOT NULL,
                        PRIMARY KEY(annotationId, orderIndex),
                        FOREIGN KEY(annotationId) REFERENCES pdf_annotations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pdf_annotation_segments_annotationId ON pdf_annotation_segments(annotationId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pdf_annotation_segments_pageIndex ON pdf_annotation_segments(pageIndex)",
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN colorKey TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
        )

        private fun createNotesFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE BEFORE UPDATE ON notes BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE BEFORE DELETE ON notes BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE AFTER UPDATE ON notes BEGIN
                    INSERT INTO notes_fts(docid, title, bodyPlainText)
                    VALUES (NEW.rowid, NEW.title, NEW.bodyPlainText);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(docid, title, bodyPlainText)
                    VALUES (NEW.rowid, NEW.title, NEW.bodyPlainText);
                END
                """.trimIndent(),
            )
        }
    }
}
