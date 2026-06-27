package com.myvault.app.ai.home

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.AttachmentEntity

data class HomeAiContextRow(
    val id: String,
    val title: String,
    val type: String,
    val body: String,
    val updatedAt: Long,
)

@Dao
interface HomeChatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: HomeChatHistoryEntity)

    @Query("SELECT * FROM home_chat_history ORDER BY updatedAt DESC LIMIT 20")
    suspend fun recentHistory(): List<HomeChatHistoryEntity>

    @Query("SELECT * FROM home_chat_history ORDER BY updatedAt DESC")
    suspend fun allHistory(): List<HomeChatHistoryEntity>

    @Query("SELECT * FROM home_chat_history WHERE id = :id LIMIT 1")
    suspend fun historyById(id: String): HomeChatHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entities: List<HomeChatHistoryEntity>)

    @Query("DELETE FROM home_chat_history")
    suspend fun clearHomeInlineAiHistory()

    @Query(
        """
        DELETE FROM home_chat_history
        WHERE id NOT IN (
            SELECT id FROM home_chat_history ORDER BY updatedAt DESC LIMIT 20
        )
        """,
    )
    suspend fun pruneHistory()

    @Query(
        """
        SELECT id, title, 'Study' AS type, '' AS subtitle, updatedAt
        FROM notes
        WHERE deletedAt IS NULL
          AND lower(title) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        UNION ALL
        SELECT id, title, 'Course' AS type, '' AS subtitle, updatedAt
        FROM course_notes
        WHERE lower(title) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        UNION ALL
        SELECT id, term AS title, 'ConceptCard' AS type, COALESCE(arabicTerm, '') AS subtitle, updatedAt
        FROM course_concept_cards
        WHERE lower(term) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchAttachableTitles(escapedQuery: String, limit: Int = 5): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT attachments.id, attachments.fileName AS title, 'Pdf' AS type,
            COALESCE(folders.name, '') AS subtitle, attachments.createdAt AS updatedAt
        FROM attachments
        LEFT JOIN folders ON folders.id = attachments.libraryFolderId
        WHERE attachments.deletedAt IS NULL
          AND lower(attachments.mimeType) = 'application/pdf'
          AND lower(attachments.fileName) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchPdfTitles(escapedQuery: String, limit: Int = 5): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, title, 'Course' AS type, '' AS subtitle, updatedAt
        FROM course_notes
        WHERE (:courseId IS NULL OR courseId = :courseId)
          AND lower(title) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        UNION ALL
        SELECT id, term AS title, 'ConceptCard' AS type, COALESCE(arabicTerm, '') AS subtitle, updatedAt
        FROM course_concept_cards
        WHERE (:courseId IS NULL OR courseId = :courseId)
          AND lower(term) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE char(92)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchCourseAttachableTitles(escapedQuery: String, courseId: String?, limit: Int = 5): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, title, 'Study' AS type, '' AS subtitle, updatedAt
        FROM notes
        WHERE deletedAt IS NULL
        ORDER BY isPinned DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentStudyItems(limit: Int = 20): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, title, 'Course' AS type, '' AS subtitle, updatedAt
        FROM course_notes
        ORDER BY COALESCE(lastOpenedAt, updatedAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun recentCourseItems(limit: Int = 16): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, term AS title, 'ConceptCard' AS type, COALESCE(arabicTerm, '') AS subtitle, updatedAt
        FROM course_concept_cards
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentConceptItems(limit: Int = 16): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT attachments.id, attachments.fileName AS title, 'Pdf' AS type,
            COALESCE(folders.name, '') AS subtitle, attachments.createdAt AS updatedAt
        FROM attachments
        LEFT JOIN folders ON folders.id = attachments.libraryFolderId
        WHERE attachments.deletedAt IS NULL
          AND lower(attachments.mimeType) = 'application/pdf'
        ORDER BY attachments.isPinned DESC, attachments.createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentPdfItems(limit: Int = 36): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, title, 'Course' AS type, '' AS subtitle, updatedAt
        FROM course_notes
        WHERE (:courseId IS NULL OR courseId = :courseId)
        UNION ALL
        SELECT id, term AS title, 'ConceptCard' AS type, COALESCE(arabicTerm, '') AS subtitle, updatedAt
        FROM course_concept_cards
        WHERE (:courseId IS NULL OR courseId = :courseId)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentCourseScopeItems(courseId: String?, limit: Int = 36): List<HomeAiAttachableItem>

    @Query(
        """
        SELECT id, title, 'Study' AS type, bodyPlainText AS body, updatedAt
        FROM notes
        WHERE id IN (:ids) AND deletedAt IS NULL
        """,
    )
    suspend fun studyContexts(ids: List<String>): List<HomeAiContextRow>

    @Query(
        """
        SELECT id, title, 'Course' AS type, body, updatedAt
        FROM course_notes
        WHERE id IN (:ids)
        """,
    )
    suspend fun courseContexts(ids: List<String>): List<HomeAiContextRow>

    @Query(
        """
        SELECT id, term AS title, 'ConceptCard' AS type,
            definition || CASE WHEN details IS NULL OR details = '' THEN '' ELSE char(10) || char(10) || details END AS body,
            updatedAt
        FROM course_concept_cards
        WHERE id IN (:ids)
        """,
    )
    suspend fun conceptContexts(ids: List<String>): List<HomeAiContextRow>

    @Query(
        """
        SELECT attachments.id, attachments.fileName AS title, 'Pdf' AS type,
            'PDF file: ' || attachments.fileName ||
            char(10) || 'Saved PDF annotation notes:' || char(10) ||
            COALESCE((
                SELECT group_concat(
                    'Page ' || (pdf_annotations.pageIndex + 1) || ': ' ||
                    COALESCE(NULLIF(pdf_annotations.displayTitle, ''), '') ||
                    CASE
                        WHEN pdf_annotations.displayTitle IS NOT NULL AND pdf_annotations.displayTitle != '' THEN char(10)
                        ELSE ''
                    END ||
                    pdf_annotations.noteText,
                    char(10) || char(10)
                )
                FROM pdf_annotations
                WHERE pdf_annotations.attachmentId = attachments.id
                  AND pdf_annotations.noteText IS NOT NULL
                  AND trim(pdf_annotations.noteText) != ''
            ), 'No saved PDF annotation notes are available for this file.') AS body,
            attachments.createdAt AS updatedAt
        FROM attachments
        WHERE attachments.id IN (:ids)
          AND attachments.deletedAt IS NULL
          AND lower(attachments.mimeType) = 'application/pdf'
        """,
    )
    suspend fun pdfContexts(ids: List<String>): List<HomeAiContextRow>

    @Query(
        """
        SELECT 'course-concepts:' || :courseId AS id,
            'Course concept cards' AS title,
            'CourseContext' AS type,
            COALESCE(group_concat(
                'Concept: ' || term ||
                CASE WHEN arabicTerm IS NULL OR arabicTerm = '' THEN '' ELSE char(10) || 'Arabic: ' || arabicTerm END ||
                char(10) || 'Definition: ' || definition ||
                CASE WHEN details IS NULL OR details = '' THEN '' ELSE char(10) || 'Details: ' || details END,
                char(10) || char(10)
            ), '') AS body,
            COALESCE(MAX(updatedAt), 0) AS updatedAt
        FROM course_concept_cards
        WHERE courseId = :courseId
        UNION ALL
        SELECT 'course-sticky:' || :courseId AS id,
            'Course sticky notes' AS title,
            'CourseContext' AS type,
            COALESCE(group_concat(text, char(10) || char(10)), '') AS body,
            COALESCE(MAX(updatedAt), 0) AS updatedAt
        FROM course_sticky_notes
        WHERE courseId = :courseId
        UNION ALL
        SELECT 'course-root-sticky:' || :courseId AS id,
            'Visible course sticky notes' AS title,
            'CourseContext' AS type,
            COALESCE(group_concat(folder_sticky_notes.text, char(10) || char(10)), '') AS body,
            COALESCE(MAX(folder_sticky_notes.updatedAt), 0) AS updatedAt
        FROM folder_sticky_notes
        JOIN courses ON courses.rootFolderId = folder_sticky_notes.folderId
        WHERE courses.id = :courseId
        """,
    )
    suspend fun courseScreenContexts(courseId: String): List<HomeAiContextRow>

    @Query(
        """
        SELECT *
        FROM attachments
        WHERE id IN (:ids)
          AND deletedAt IS NULL
          AND lower(mimeType) = 'application/pdf'
        """,
    )
    suspend fun pdfAttachments(ids: List<String>): List<AttachmentEntity>

    @Query(
        """
        SELECT *
        FROM library_ai_file_cache
        WHERE attachmentId IN (:attachmentIds)
          AND provider = :provider
        """,
    )
    suspend fun cachedLibraryAiFiles(
        attachmentIds: List<String>,
        provider: String = HomeAiProvider.GEMINI.name,
    ): List<LibraryAiFileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryAiFileCache(entity: LibraryAiFileCacheEntity)

    @Query("DELETE FROM library_ai_file_cache WHERE attachmentId = :attachmentId AND provider = :provider")
    suspend fun deleteLibraryAiFileCache(
        attachmentId: String,
        provider: String = HomeAiProvider.GEMINI.name,
    )

    @Query("DELETE FROM library_ai_file_cache WHERE attachmentId IN (:attachmentIds) AND provider = :provider")
    suspend fun deleteLibraryAiFileCaches(
        attachmentIds: List<String>,
        provider: String = HomeAiProvider.GEMINI.name,
    )
}
