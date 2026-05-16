package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfReadingProgressDao {
    @Query("SELECT * FROM pdf_reading_progress ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<PdfReadingProgressEntity>>

    @Query("SELECT * FROM pdf_reading_progress WHERE attachmentId = :attachmentId")
    fun observeForAttachment(attachmentId: String): Flow<PdfReadingProgressEntity?>

    @Query("SELECT * FROM pdf_reading_progress ORDER BY lastOpenedAt DESC")
    suspend fun getAll(): List<PdfReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PdfReadingProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progress: List<PdfReadingProgressEntity>)

    @Query("DELETE FROM pdf_reading_progress WHERE attachmentId IN (:attachmentIds)")
    suspend fun deleteForAttachments(attachmentIds: List<String>)
}
