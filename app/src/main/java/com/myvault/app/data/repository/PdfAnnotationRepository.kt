package com.myvault.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.myvault.app.BuildConfig
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.PdfAnnotationSegmentDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.data.local.entity.PdfAnnotationSegmentEntity
import com.myvault.app.data.local.entity.isValidPdfAnnotationSegment
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfAnnotationRepository @Inject constructor(
    private val database: VaultDatabase,
    private val annotationDao: PdfAnnotationDao,
    private val segmentDao: PdfAnnotationSegmentDao,
    private val knowledgeTagDao: KnowledgeTagDao,
    private val sourceBacklinkDao: SourceBacklinkDao,
) {
    fun observeAll() = annotationDao.observeAll()

    fun observeForAttachment(attachmentId: String) = annotationDao.observeForAttachment(attachmentId)

    fun observeAllSegments() = segmentDao.observeAll()

    fun observeSegmentsForAttachment(attachmentId: String) = segmentDao.observeForAttachment(attachmentId)

    suspend fun cleanupGenuinelyInvalidAnnotations() {
        val ids = annotationDao.getGenuinelyInvalidIds()
        if (ids.isEmpty()) return
        database.withTransaction {
            annotationDao.deleteByIds(ids)
            sourceBacklinkDao.deleteForAnnotations(ids)
            knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, ids)
        }
    }

    suspend fun addSelectedTextAnnotation(
        attachmentId: String,
        libraryFolderId: String?,
        segments: List<PdfAnnotationSegmentInput>,
        selectedText: String,
        color: String,
        noteText: String? = null,
    ): String? {
        val cleanText = selectedText.trim()
        if (attachmentId.isBlank() || cleanText.isBlank()) return null

        val normalizedSegments = segments.mapIndexedNotNull { orderIndex, segment ->
            val normalized = PdfAnnotationSegmentEntity(
                annotationId = "pending",
                orderIndex = orderIndex,
                pageIndex = segment.pageIndex.coerceAtLeast(0),
                left = minOf(segment.left, segment.right),
                top = minOf(segment.top, segment.bottom),
                right = maxOf(segment.left, segment.right),
                bottom = maxOf(segment.top, segment.bottom),
            )
            normalized.takeIf { it.isValidPdfAnnotationSegment() }
        }
        if (normalizedSegments.isEmpty()) return null

        val annotationId = UUID.randomUUID().toString()
        val firstSegment = normalizedSegments.first()
        val now = System.currentTimeMillis()
        val annotation = PdfAnnotationEntity(
            id = annotationId,
            attachmentId = attachmentId,
            libraryFolderId = libraryFolderId,
            pageIndex = firstSegment.pageIndex,
            left = firstSegment.left,
            top = firstSegment.top,
            right = firstSegment.right,
            bottom = firstSegment.bottom,
            color = color.sanitizedPdfAnnotationColor(defaultColor = "yellow"),
            noteText = noteText?.trim()?.ifBlank { null },
            annotationType = PdfAnnotationEntity.TYPE_HIGHLIGHT,
            selectedText = cleanText,
            displayTitle = noteText?.trim()?.ifBlank { null }?.take(60) ?: cleanText.take(60),
            displayFolderId = libraryFolderId,
            createdAt = now,
            updatedAt = now,
        )
        database.withTransaction {
            annotationDao.upsert(annotation)
            segmentDao.upsertAll(
                normalizedSegments.map { it.copy(annotationId = annotationId) },
            )
        }
        return annotationId
    }

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
            if (BuildConfig.DEBUG) Log.w("MyVaultPdfHighlight", "Repository rejected highlight: blank attachmentId")
            return false
        }
        val normalizedLeft = minOf(left, right)
        val normalizedRight = maxOf(left, right)
        val normalizedTop = minOf(top, bottom)
        val normalizedBottom = maxOf(top, bottom)
        if (!isValidPdfAnnotationRect(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)) {
            if (BuildConfig.DEBUG) Log.w("MyVaultPdfHighlight", "Repository rejected highlight: invalid rect=$left,$top,$right,$bottom")
            return false
        }
        val width = normalizedRight - normalizedLeft
        val height = normalizedBottom - normalizedTop
        if (width < 0.5f || height < 0.5f) {
            if (BuildConfig.DEBUG) Log.w("MyVaultPdfHighlight", "Repository rejected highlight: too small width=$width height=$height")
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
            color = color.sanitizedPdfAnnotationColor(defaultColor = "yellow"),
            noteText = null,
            annotationType = PdfAnnotationEntity.TYPE_HIGHLIGHT,
            displayTitle = null,
            displayFolderId = libraryFolderId,
            createdAt = now,
            updatedAt = now,
        )
        annotationDao.upsert(
            annotation,
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                "MyVaultPdfHighlight",
                "DAO insert success id=${annotation.id} page=${annotation.pageIndex} rect=${annotation.left},${annotation.top},${annotation.right},${annotation.bottom}",
            )
        }
        return true
    }

    suspend fun addTextBox(
        attachmentId: String,
        libraryFolderId: String?,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        text: String,
        color: String,
        textSize: Float,
        backgroundColor: String,
    ): Boolean {
        val cleanText = text.trim()
        if (attachmentId.isBlank() || cleanText.isBlank()) return false
        val normalizedLeft = minOf(left, right)
        val normalizedRight = maxOf(left, right)
        val normalizedTop = minOf(top, bottom)
        val normalizedBottom = maxOf(top, bottom)
        if (!isValidPdfAnnotationRect(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)) return false
        if (normalizedRight - normalizedLeft < 2f || normalizedBottom - normalizedTop < 2f) return false

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
                color = color.sanitizedPdfAnnotationColor(defaultColor = "black"),
                noteText = cleanText,
                annotationType = PdfAnnotationEntity.TYPE_TEXT_BOX,
                textSize = textSize.coerceIn(10f, 36f),
                backgroundColor = backgroundColor.sanitizedPdfTextBoxBackground(),
                displayTitle = cleanText.take(60),
                displayFolderId = libraryFolderId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }


    suspend fun addPageNote(
        attachmentId: String,
        libraryFolderId: String?,
        pageIndex: Int,
        noteText: String,
    ): Boolean {
        val cleanText = noteText.trim()
        if (attachmentId.isBlank() || cleanText.isBlank()) return false
        val now = System.currentTimeMillis()
        annotationDao.upsert(
            PdfAnnotationEntity(
                id = UUID.randomUUID().toString(),
                attachmentId = attachmentId,
                libraryFolderId = libraryFolderId,
                pageIndex = pageIndex.coerceAtLeast(0),
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1f,
                color = "yellow",
                noteText = cleanText,
                annotationType = PdfAnnotationEntity.TYPE_PAGE_NOTE,
                displayTitle = cleanText.take(60),
                displayFolderId = libraryFolderId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }

    suspend fun updateColor(id: String, color: String) {
        if (id.isBlank()) return
        annotationDao.updateColor(id, color.sanitizedPdfAnnotationColor(defaultColor = "yellow"), System.currentTimeMillis())
    }

    suspend fun updateNote(id: String, noteText: String) {
        if (id.isBlank()) return
        annotationDao.updateNote(id, noteText.trim().ifBlank { null }, System.currentTimeMillis())
    }

    suspend fun updateTextBox(id: String, text: String, color: String, textSize: Float, backgroundColor: String) {
        val cleanText = text.trim()
        if (id.isBlank() || cleanText.isBlank()) return
        annotationDao.updateTextBox(
            id = id,
            text = cleanText,
            color = color.sanitizedPdfAnnotationColor(defaultColor = "black"),
            textSize = textSize.coerceIn(10f, 36f),
            backgroundColor = backgroundColor.sanitizedPdfTextBoxBackground(),
            updatedAt = System.currentTimeMillis(),
        )
        annotationDao.updateDisplayTitle(id, cleanText.take(60), System.currentTimeMillis())
    }

    suspend fun updateBounds(id: String, left: Float, top: Float, right: Float, bottom: Float) {
        if (id.isBlank()) return
        val normalizedLeft = minOf(left, right)
        val normalizedRight = maxOf(left, right)
        val normalizedTop = minOf(top, bottom)
        val normalizedBottom = maxOf(top, bottom)
        if (!isValidPdfAnnotationRect(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)) return
        if (normalizedRight - normalizedLeft < 2f || normalizedBottom - normalizedTop < 2f) return
        annotationDao.updateBounds(id, normalizedLeft, normalizedTop, normalizedRight, normalizedBottom, System.currentTimeMillis())
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
            segmentDao.deleteForAnnotation(id)
            annotationDao.deleteById(id)
            sourceBacklinkDao.deleteForAnnotations(listOf(id))
            knowledgeTagDao.deleteLinksForTargets(KnowledgeRepository.TargetAnnotation, listOf(id))
        }
    }
}

data class PdfAnnotationSegmentInput(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun String.sanitizedPdfAnnotationColor(defaultColor: String): String =
    when (lowercase()) {
        "yellow", "blue", "green", "red", "black" -> lowercase()
        else -> defaultColor
    }

internal fun String.sanitizedPdfTextBoxBackground(): String =
    when (lowercase()) {
        PdfAnnotationEntity.BACKGROUND_NONE, "white", "yellow", "blue", "green", "red" -> lowercase()
        else -> PdfAnnotationEntity.BACKGROUND_NONE
    }

internal fun isValidPdfAnnotationRect(left: Float, top: Float, right: Float, bottom: Float): Boolean =
    left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        left < right &&
        top < bottom
