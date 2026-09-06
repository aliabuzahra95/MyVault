package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationSegmentDao {
    @Query(
        """
        SELECT segment.* FROM pdf_annotation_segments AS segment
        INNER JOIN pdf_annotations AS annotation ON annotation.id = segment.annotationId
        WHERE annotation.attachmentId = :attachmentId
        ORDER BY segment.annotationId ASC, segment.orderIndex ASC
        """,
    )
    fun observeForAttachment(attachmentId: String): Flow<List<PdfAnnotationSegmentEntity>>

    @Query("SELECT * FROM pdf_annotation_segments ORDER BY annotationId ASC, orderIndex ASC")
    fun observeAll(): Flow<List<PdfAnnotationSegmentEntity>>

    @Query("SELECT * FROM pdf_annotation_segments ORDER BY annotationId ASC, orderIndex ASC")
    suspend fun getAll(): List<PdfAnnotationSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(segments: List<PdfAnnotationSegmentEntity>)

    @Query("DELETE FROM pdf_annotation_segments WHERE annotationId = :annotationId")
    suspend fun deleteForAnnotation(annotationId: String)

    @Query("DELETE FROM pdf_annotation_segments WHERE annotationId IN (:annotationIds)")
    suspend fun deleteForAnnotations(annotationIds: List<String>)
}
