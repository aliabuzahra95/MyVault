package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.SourceBacklinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceBacklinkDao {
    @Query("SELECT * FROM source_backlinks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SourceBacklinkEntity>>

    @Query("SELECT * FROM source_backlinks WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: String): Flow<List<SourceBacklinkEntity>>

    @Query("SELECT * FROM source_backlinks ORDER BY createdAt DESC")
    suspend fun getAll(): List<SourceBacklinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: SourceBacklinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<SourceBacklinkEntity>)

    @Query("DELETE FROM source_backlinks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM source_backlinks WHERE noteId IN (:noteIds)")
    suspend fun deleteForNotes(noteIds: List<String>)

    @Query("DELETE FROM source_backlinks WHERE attachmentId IN (:attachmentIds)")
    suspend fun deleteForAttachments(attachmentIds: List<String>)

    @Query("DELETE FROM source_backlinks WHERE annotationId IN (:annotationIds)")
    suspend fun deleteForAnnotations(annotationIds: List<String>)
}
