package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folder_sticky_notes",
    indices = [Index("folderId", "updatedAt")],
)
data class FolderStickyNoteEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)
