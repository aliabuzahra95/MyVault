package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfAnnotationRepository @Inject constructor(
    private val annotationDao: PdfAnnotationDao,
) {
    fun observeAll() = annotationDao.observeAll()

    fun observeForAttachment(attachmentId: String) = annotationDao.observeForAttachment(attachmentId)

    suspend fun addHighlight(
        attachmentId: String,
        libraryFolderId: String?,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: String,
    ) {
        if (attachmentId.isBlank()) return
        val normalizedLeft = minOf(left, right).coerceIn(0f, 1f)
        val normalizedRight = maxOf(left, right).coerceIn(0f, 1f)
        val normalizedTop = minOf(top, bottom).coerceIn(0f, 1f)
        val normalizedBottom = maxOf(top, bottom).coerceIn(0f, 1f)
        if (normalizedRight - normalizedLeft < 0.003f || normalizedBottom - normalizedTop < 0.003f) return

        val now = System.currentTimeMillis()
        annotationDao.upsert(
            PdfAnnotationEntity(
                id = UUID.randomUUID().toString(),
                attachmentId = attachmentId,
                libraryFolderId = libraryFolderId,
                pageIndex = pageIndex.coerceAtLeast(0),
                left = normalizedLeft,
                top = normalizedTop,
                right = normalizedRight,
                bottom = normalizedBottom,
                color = color,
                noteText = null,
                displayTitle = null,
                displayFolderId = libraryFolderId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateColor(id: String, color: String) {
        if (id.isBlank()) return
        annotationDao.updateColor(id, color, System.currentTimeMillis())
    }

    suspend fun updateNote(id: String, noteText: String) {
        if (id.isBlank()) return
        annotationDao.updateNote(id, noteText.trim().ifBlank { null }, System.currentTimeMillis())
    }

    suspend fun updateDisplayTitle(id: String, displayTitle: String) {
        if (id.isBlank()) return
        annotationDao.updateDisplayTitle(id, displayTitle.trim().ifBlank { null }, System.currentTimeMillis())
    }

    suspend fun updateDisplayFolder(id: String, folderId: String?) {
        if (id.isBlank()) return
        annotationDao.updateDisplayFolder(id, folderId, System.currentTimeMillis())
    }

    suspend fun deleteNoteOnly(id: String) {
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        annotationDao.updateNote(id, null, now)
        annotationDao.updateDisplayTitle(id, null, now)
    }

    suspend fun delete(id: String) {
        if (id.isBlank()) return
        annotationDao.deleteById(id)
    }
}
