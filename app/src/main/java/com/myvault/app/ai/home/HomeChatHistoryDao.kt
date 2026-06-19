package com.myvault.app.ai.home

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Query("SELECT * FROM home_chat_history ORDER BY createdAt DESC LIMIT 20")
    suspend fun recentHistory(): List<HomeChatHistoryEntity>

    @Query("SELECT * FROM home_chat_history WHERE id = :id LIMIT 1")
    suspend fun historyById(id: String): HomeChatHistoryEntity?

    @Query("DELETE FROM home_chat_history")
    suspend fun clearHomeInlineAiHistory()

    @Query(
        """
        DELETE FROM home_chat_history
        WHERE id NOT IN (
            SELECT id FROM home_chat_history ORDER BY createdAt DESC LIMIT 20
        )
        """,
    )
    suspend fun pruneHistory()

    @Query(
        """
        SELECT id, title, 'Study' AS type, '' AS subtitle, updatedAt
        FROM notes
        WHERE deletedAt IS NULL
          AND lower(title) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE '\'
        UNION ALL
        SELECT id, title, 'Course' AS type, '' AS subtitle, updatedAt
        FROM course_notes
        WHERE lower(title) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE '\'
        UNION ALL
        SELECT id, term AS title, 'ConceptCard' AS type, COALESCE(arabicTerm, '') AS subtitle, updatedAt
        FROM course_concept_cards
        WHERE lower(term) LIKE '%' || lower(:escapedQuery) || '%' ESCAPE '\'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchAttachableTitles(escapedQuery: String, limit: Int = 5): List<HomeAiAttachableItem>

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
}
