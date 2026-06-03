package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    suspend fun getAllIncludingDeleted(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT COUNT(*) FROM attachments WHERE noteId = :noteId AND deletedAt IS NULL")
    fun observeCountForNote(noteId: String): Flow<Int>

    @Query("SELECT * FROM attachments WHERE libraryFolderId = :folderId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeForLibraryFolder(folderId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = '' AND libraryFolderId IS NULL AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeRootLibraryFiles(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE (noteId = '' OR libraryFolderId IS NOT NULL) AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeLibraryFiles(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<AttachmentEntity?>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE noteId IN (:noteIds)")
    suspend fun getForNotes(noteIds: List<String>): List<AttachmentEntity>

    @Query("UPDATE attachments SET deletedAt = :deletedAt WHERE noteId IN (:noteIds)")
    suspend fun updateDeletedAtForNotes(noteIds: List<String>, deletedAt: Long?)

    @Query("UPDATE attachments SET deletedAt = :deletedAt WHERE libraryFolderId IN (:folderIds)")
    suspend fun updateDeletedAtForLibraryFolders(folderIds: List<String>, deletedAt: Long?)

    @Query("UPDATE attachments SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun updateDeletedAt(id: String, deletedAt: Long?)

    @Query("UPDATE attachments SET fileName = :fileName WHERE id = :id")
    suspend fun updateFileName(id: String, fileName: String)

    @Query("UPDATE attachments SET libraryFolderId = :folderId WHERE id = :id")
    suspend fun updateLibraryFolder(id: String, folderId: String?)

    @Query("UPDATE attachments SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("DELETE FROM attachments WHERE noteId IN (:noteIds)")
    suspend fun deleteForNotes(noteIds: List<String>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)
}
