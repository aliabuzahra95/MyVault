package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_backlinks",
    indices = [
        Index("noteId"),
        Index("attachmentId"),
        Index("annotationId"),
    ],
)
data class SourceBacklinkEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val attachmentId: String,
    val annotationId: String?,
    val pageIndex: Int,
    val left: Float?,
    val top: Float?,
    val right: Float?,
    val bottom: Float?,
    val createdAt: Long,
)
