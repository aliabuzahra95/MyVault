package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
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
    val displayTitle: String? = null,
    val displayFolderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
