package com.myvault.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfAnnotationRepository @Inject constructor(
    private val database: VaultDatabase,
    private val annotationDao: PdfAnnotationDao,
    private val knowledgeTagDao: KnowledgeTagDao,
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
    ): Boolean {
        if (attachmentId.isBlank()) {
            Log.w("MyVaultPdfHighlight", "Repository rejected highlight: blank attachmentId")
            return false
        }
        val normalizedLeft = minOf(left, right).coerceIn(0f, 1f)
        val normalizedRight = maxOf(left, right).coerceIn(0f, 1f)
        val normalizedTop = minOf(top, bottom).coerceIn(0f, 1f)
        val normalizedBottom = maxOf(top, bottom).coerceIn(0f, 1f)
        val width = normalizedRight - normalizedLeft
        val height = normalizedBottom - normalizedTop
        if (width < 0.003f || height < 0.003f) {
            Log.w("MyVaultPdfHighlight", "Repository rejected highlight: too small width=$width height=$height")
            return false
        }

        val now = System.currentTimeMillis()
        val annotation = PdfAnnotationEntity(
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
        )
        annotationDao.upsert(
            annotation,
        )
        Log.d(
            "MyVaultPdfHighlight",
            "DAO insert success id=${annotation.id} page=${annotation.pageIndex} rect=${annotation.left},${annotation.top},${annotation.right},${annotation.bottom}",
        )
        return true
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
        database.withTransaction {
            annotationDao.deleteById(id)
            knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, listOf(id))
        }
    }
}
