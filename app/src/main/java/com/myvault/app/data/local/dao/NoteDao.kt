package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllIncludingDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND parentNoteId IS NULL AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeForFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE parentNoteId = :parentNoteId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeChildren(parentNoteId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isPinned = 1 AND parentNoteId IS NULL AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE parentNoteId IS NULL AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("UPDATE notes SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isFolderPinned = :isFolderPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFolderPinned(id: String, isFolderPinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrderIndex(id: String, orderIndex: Int, updatedAt: Long)

    @Query("UPDATE notes SET isFavourite = :isFavourite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavourite(id: String, isFavourite: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET bodyPlainText = :bodyPlainText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBodyPlainText(id: String, bodyPlainText: String, updatedAt: Long)

    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateDeletedAt(ids: List<String>, deletedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)
}
