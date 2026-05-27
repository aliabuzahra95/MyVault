package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_versions",
    indices = [
        Index("noteId", "createdAt"),
    ],
)
data class NoteVersionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val title: String,
    val bodyPlainText: String,
    val richTextJson: String?,
    val richHtml: String?,
    val wordCount: Int,
    val characterCount: Int,
    val createdAt: Long,
)
