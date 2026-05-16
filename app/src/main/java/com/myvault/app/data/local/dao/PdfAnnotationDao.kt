package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations WHERE attachmentId = :attachmentId ORDER BY pageIndex ASC, createdAt ASC")
    fun observeForAttachment(attachmentId: String): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PdfAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: PdfAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(annotations: List<PdfAnnotationEntity>)

    @Query("UPDATE pdf_annotations SET color = :color, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColor(id: String, color: String, updatedAt: Long)

    @Query("UPDATE pdf_annotations SET noteText = :noteText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNote(id: String, noteText: String?, updatedAt: Long)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pdf_annotations WHERE attachmentId IN (:attachmentIds)")
    suspend fun deleteForAttachments(attachmentIds: List<String>)
}
