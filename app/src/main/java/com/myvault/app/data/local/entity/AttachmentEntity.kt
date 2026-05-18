package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [
        Index("noteId", "deletedAt", "createdAt"),
        Index("libraryFolderId", "deletedAt", "createdAt"),
        Index("deletedAt", "createdAt"),
        Index("isPinned"),
    ],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val libraryFolderId: String? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val remoteUrl: String?,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val deletedAt: Long? = null,
)
