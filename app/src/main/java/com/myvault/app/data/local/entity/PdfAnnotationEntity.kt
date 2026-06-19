package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_annotations",
    indices = [
        Index("attachmentId", "pageIndex", "createdAt"),
        Index("libraryFolderId", "updatedAt"),
        Index("displayFolderId", "updatedAt"),
        Index("updatedAt"),
    ],
)
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val attachmentId: String,
    val libraryFolderId: String?,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: String,
    val noteText: String?,
    val annotationType: String = TYPE_HIGHLIGHT,
    val textSize: Float = 16f,
    val backgroundColor: String = BACKGROUND_NONE,
    val displayTitle: String? = null,
    val displayFolderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val TYPE_HIGHLIGHT = "highlight"
        const val TYPE_TEXT_BOX = "text_box"
        const val TYPE_PAGE_NOTE = "page_note"
        const val BACKGROUND_NONE = "none"
    }
}

fun PdfAnnotationEntity.isCurrentPdfAnnotation(): Boolean =
    when (annotationType) {
        PdfAnnotationEntity.TYPE_HIGHLIGHT -> {
            attachmentId.isNotBlank() &&
                pageIndex >= 0 &&
                left.isFinite() &&
                top.isFinite() &&
                right.isFinite() &&
                bottom.isFinite() &&
                right > left &&
                bottom > top &&
                right - left >= 0.5f &&
                bottom - top >= 0.5f
        }

        PdfAnnotationEntity.TYPE_PAGE_NOTE -> {
            attachmentId.isNotBlank() &&
                pageIndex >= 0 &&
                !noteText.isNullOrBlank()
        }

        else -> false
    }
