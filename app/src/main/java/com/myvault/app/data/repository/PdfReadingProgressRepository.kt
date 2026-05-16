package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.PdfReadingProgressDao
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReadingProgressRepository @Inject constructor(
    private val progressDao: PdfReadingProgressDao,
) {
    fun observeAll() = progressDao.observeAll()

    fun observeForAttachment(attachmentId: String) = progressDao.observeForAttachment(attachmentId)

    suspend fun updateProgress(attachmentId: String, pageIndex: Int, pageCount: Int) {
        if (attachmentId.isBlank() || pageCount <= 0) return
        val safePage = pageIndex.coerceIn(0, pageCount - 1)
        val now = System.currentTimeMillis()
        progressDao.upsert(
            PdfReadingProgressEntity(
                attachmentId = attachmentId,
                pageIndex = safePage,
                pageCount = pageCount,
                progressPercent = ((safePage + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f),
                lastOpenedAt = now,
                updatedAt = now,
            ),
        )
    }
}
