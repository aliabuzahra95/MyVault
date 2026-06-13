package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.FolderStickyNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderStickyNoteDao {
    @Query("SELECT * FROM folder_sticky_notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun observeForFolder(folderId: String): Flow<List<FolderStickyNoteEntity>>

    @Query("SELECT * FROM folder_sticky_notes WHERE id = :id")
    suspend fun getById(id: String): FolderStickyNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stickyNote: FolderStickyNoteEntity)

    @Query("DELETE FROM folder_sticky_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
