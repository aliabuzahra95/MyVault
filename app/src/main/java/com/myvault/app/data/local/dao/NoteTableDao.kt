package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.NoteTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTableDao {
    @Query("SELECT * FROM note_tables ORDER BY noteId ASC, orderIndex ASC")
    fun observeAll(): Flow<List<NoteTableEntity>>

    @Query("SELECT * FROM note_tables ORDER BY noteId ASC, orderIndex ASC")
    suspend fun getAll(): List<NoteTableEntity>

    @Query("SELECT * FROM note_tables WHERE noteId IN (:noteIds) ORDER BY noteId ASC, orderIndex ASC")
    suspend fun getForNotes(noteIds: List<String>): List<NoteTableEntity>

    @Query("SELECT * FROM note_tables WHERE noteId = :noteId ORDER BY orderIndex ASC")
    fun observeForNote(noteId: String): Flow<List<NoteTableEntity>>

    @Query("SELECT * FROM note_tables WHERE noteId = :noteId ORDER BY orderIndex ASC")
    suspend fun getForNote(noteId: String): List<NoteTableEntity>

    @Query("UPDATE note_tables SET cellsJson = :cellsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCells(id: String, cellsJson: String, updatedAt: Long)

    @Query("DELETE FROM note_tables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM note_tables WHERE noteId IN (:noteIds)")
    suspend fun deleteForNotes(noteIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tables: List<NoteTableEntity>)
}
