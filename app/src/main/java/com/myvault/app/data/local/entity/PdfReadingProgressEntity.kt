package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_reading_progress")
data class PdfReadingProgressEntity(
    @PrimaryKey val attachmentId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val progressPercent: Float,
    val lastOpenedAt: Long,
    val updatedAt: Long,
)
