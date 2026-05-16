package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.NoteFtsEntity
import kotlinx.coroutines.flow.Flow

data class NoteSearchResult(
    val id: String,
    val title: String,
    val bodyPlainText: String,
    val folderName: String?,
)

@Dao
interface SearchDao {
    @Query(
        """
        SELECT notes.id AS id, notes.title AS title, notes.bodyPlainText AS bodyPlainText, folders.name AS folderName
        FROM notes_fts
        INNER JOIN notes ON notes_fts.title = notes.title
        LEFT JOIN folders ON folders.id = notes.folderId
        WHERE notes_fts MATCH :query
        ORDER BY notes.updatedAt DESC
        """,
    )
    fun searchNotes(query: String): Flow<List<NoteSearchResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(notes: List<NoteFtsEntity>)
}
