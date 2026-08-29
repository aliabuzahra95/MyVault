package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class NoteSearchResult(
    val id: String,
    val title: String,
    val bodyPlainText: String,
    val folderId: String?,
    val folderName: String?,
    val folderMode: String?,
)

@Dao
interface SearchDao {
    @Query(
        """
        SELECT notes.id AS id, COALESCE(notes.title, '') AS title, COALESCE(notes.bodyPlainText, '') AS bodyPlainText,
            folders.id AS folderId, folders.name AS folderName, folders.mode AS folderMode
        FROM notes
        LEFT JOIN folders ON folders.id = notes.folderId
        WHERE notes.deletedAt IS NULL
        AND (
            COALESCE(notes.title, '') COLLATE NOCASE LIKE :pattern ESCAPE char(92) OR
            COALESCE(notes.bodyPlainText, '') COLLATE NOCASE LIKE :pattern ESCAPE char(92)
        )
        ORDER BY notes.updatedAt DESC
        LIMIT :limit
        """,
    )
    fun searchActiveNotes(pattern: String, limit: Int): Flow<List<NoteSearchResult>>

    @Query(
        """
        SELECT notes.id AS id, COALESCE(notes.title, '') AS title, COALESCE(notes.bodyPlainText, '') AS bodyPlainText,
            folders.id AS folderId, folders.name AS folderName, folders.mode AS folderMode
        FROM notes_fts
        INNER JOIN notes ON notes_fts.rowid = notes.rowid
        LEFT JOIN folders ON folders.id = notes.folderId
        WHERE notes_fts MATCH :query
        AND notes.deletedAt IS NULL
        ORDER BY notes.updatedAt DESC
        LIMIT :limit
        """,
    )
    fun searchNotes(query: String, limit: Int): Flow<List<NoteSearchResult>>
}
