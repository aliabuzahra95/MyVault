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

    @Query("UPDATE pdf_annotations SET noteText = :text, color = :color, textSize = :textSize, backgroundColor = :backgroundColor, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTextBox(id: String, text: String, color: String, textSize: Float, backgroundColor: String, updatedAt: Long)

    @Query("UPDATE pdf_annotations SET left = :left, top = :top, right = :right, bottom = :bottom, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBounds(id: String, left: Float, top: Float, right: Float, bottom: Float, updatedAt: Long)

    @Query("UPDATE pdf_annotations SET displayTitle = :displayTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDisplayTitle(id: String, displayTitle: String?, updatedAt: Long)

    @Query("UPDATE pdf_annotations SET displayFolderId = :displayFolderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDisplayFolder(id: String, displayFolderId: String?, updatedAt: Long)

    @Query("UPDATE pdf_annotations SET libraryFolderId = :folderId, updatedAt = :updatedAt WHERE attachmentId = :attachmentId")
    suspend fun updateSourceFolderForAttachment(attachmentId: String, folderId: String?, updatedAt: Long)

    @Query(
        """
        UPDATE pdf_annotations
        SET displayFolderId = :newFolderId, updatedAt = :updatedAt
        WHERE attachmentId = :attachmentId
          AND (
            (:oldFolderId IS NULL AND displayFolderId IS NULL)
            OR displayFolderId = :oldFolderId
          )
        """,
    )
    suspend fun updateDisplayFolderForAttachmentIfMatching(
        attachmentId: String,
        oldFolderId: String?,
        newFolderId: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pdf_annotations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM pdf_annotations WHERE attachmentId IN (:attachmentIds)")
    suspend fun deleteForAttachments(attachmentIds: List<String>)

    @Query(
        """
        SELECT id FROM pdf_annotations
        WHERE annotationType = 'text_box'
           OR annotationType NOT IN ('highlight', 'page_note')
           OR (
                annotationType = 'highlight'
                AND (
                    attachmentId = ''
                    OR pageIndex < 0
                    OR right <= left
                    OR bottom <= top
                    OR right - left < 0.5
                    OR bottom - top < 0.5
                    OR (right <= 1.2 AND bottom <= 1.2)
                )
           )
           OR (
                annotationType = 'page_note'
                AND (attachmentId = '' OR pageIndex < 0 OR noteText IS NULL OR TRIM(noteText) = '')
           )
        """,
    )
    suspend fun getLegacyIncompatibleIds(): List<String>
}
