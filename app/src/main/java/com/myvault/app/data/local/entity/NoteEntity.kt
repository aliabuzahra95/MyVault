package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("folderId", "deletedAt", "updatedAt"),
        Index("parentNoteId", "deletedAt", "updatedAt"),
        Index("folderId", "parentNoteId", "orderIndex"),
        Index("deletedAt", "updatedAt"),
        Index("isPinned", "deletedAt", "updatedAt"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val parentNoteId: String? = null,
    val title: String,
    val bodyPlainText: String,
    val isPinned: Boolean,
    val isFolderPinned: Boolean = false,
    val isFavourite: Boolean,
    val orderIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
