package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.BlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks ORDER BY noteId ASC, orderIndex ASC")
    suspend fun getAll(): List<BlockEntity>

    @Query("SELECT * FROM blocks ORDER BY noteId ASC, orderIndex ASC")
    fun observeAll(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY orderIndex ASC")
    fun observeForNote(noteId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY orderIndex ASC")
    suspend fun getForNote(noteId: String): List<BlockEntity>

    @Query("UPDATE blocks SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("DELETE FROM blocks WHERE noteId = :noteId AND type IN (:types) AND id != :keepId")
    suspend fun deleteTypesForNoteExcept(noteId: String, types: List<String>, keepId: String)

    @Query("DELETE FROM blocks WHERE noteId IN (:noteIds)")
    suspend fun deleteForNotes(noteIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<BlockEntity>)
}
