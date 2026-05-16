package com.myvault.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagName"],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagName: String,
)
