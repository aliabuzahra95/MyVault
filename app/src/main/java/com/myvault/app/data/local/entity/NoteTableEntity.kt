package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_tables")
data class NoteTableEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val rowCount: Int,
    val columnCount: Int,
    val cellsJson: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
