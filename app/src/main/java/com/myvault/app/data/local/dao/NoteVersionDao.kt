package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.NoteVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: String): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForNote(noteId: String): NoteVersionEntity?

    @Query("SELECT * FROM note_versions ORDER BY createdAt DESC")
    suspend fun getAll(): List<NoteVersionEntity>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: String): NoteVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(versions: List<NoteVersionEntity>)

    @Query("DELETE FROM note_versions WHERE noteId IN (:noteIds)")
    suspend fun deleteForNotes(noteIds: List<String>)

    @Query(
        """
        DELETE FROM note_versions
        WHERE noteId = :noteId
        AND id NOT IN (
            SELECT id FROM note_versions
            WHERE noteId = :noteId
            ORDER BY createdAt DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun pruneForNote(noteId: String, keep: Int)
}
