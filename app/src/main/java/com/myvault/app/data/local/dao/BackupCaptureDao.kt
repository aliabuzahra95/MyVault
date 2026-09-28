package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.myvault.app.data.local.entity.*

/** Parameterized primary-key reads, using Room's existing entity adapters. */
@Dao
interface BackupCaptureDao {
    @RawQuery suspend fun folders(query: SupportSQLiteQuery): List<FolderEntity>
    @RawQuery suspend fun notes(query: SupportSQLiteQuery): List<NoteEntity>
    @RawQuery suspend fun blocks(query: SupportSQLiteQuery): List<BlockEntity>
    @RawQuery suspend fun tags(query: SupportSQLiteQuery): List<TagEntity>
    @RawQuery suspend fun noteTags(query: SupportSQLiteQuery): List<NoteTagCrossRef>
    @RawQuery suspend fun tables(query: SupportSQLiteQuery): List<NoteTableEntity>
    @RawQuery suspend fun versions(query: SupportSQLiteQuery): List<NoteVersionEntity>
    @RawQuery suspend fun attachments(query: SupportSQLiteQuery): List<AttachmentEntity>
    @RawQuery suspend fun stickyNotes(query: SupportSQLiteQuery): List<FolderStickyNoteEntity>
    @RawQuery suspend fun courses(query: SupportSQLiteQuery): List<CourseEntity>
    @RawQuery suspend fun cards(query: SupportSQLiteQuery): List<CourseConceptCardEntity>
    @RawQuery suspend fun courseFolders(query: SupportSQLiteQuery): List<CourseFolderEntity>
    @RawQuery suspend fun courseNotes(query: SupportSQLiteQuery): List<CourseNoteEntity>
    @RawQuery suspend fun courseStickyNotes(query: SupportSQLiteQuery): List<CourseStickyNoteEntity>
    @RawQuery suspend fun progress(query: SupportSQLiteQuery): List<PdfReadingProgressEntity>
    @RawQuery suspend fun annotations(query: SupportSQLiteQuery): List<PdfAnnotationEntity>
    @RawQuery suspend fun geometry(query: SupportSQLiteQuery): List<PdfAnnotationSegmentEntity>
    @RawQuery suspend fun backlinks(query: SupportSQLiteQuery): List<SourceBacklinkEntity>
    @RawQuery suspend fun knowledgeTags(query: SupportSQLiteQuery): List<KnowledgeTagEntity>
    @RawQuery suspend fun knowledgeLinks(query: SupportSQLiteQuery): List<KnowledgeTagLinkEntity>
}
