package com.myvault.app.ai.home

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "library_ai_file_cache",
    primaryKeys = ["attachmentId", "provider"],
    indices = [Index("expiresAt")],
)
data class LibraryAiFileCacheEntity(
    val attachmentId: String,
    val provider: String,
    val fileResourceName: String,
    val fileUri: String,
    val mimeType: String,
    val displayName: String,
    val localPath: String,
    val sizeBytes: Long,
    val uploadedAt: Long,
    val lastVerifiedAt: Long,
    val expiresAt: Long,
)
