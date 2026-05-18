package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagName"],
    indices = [
        Index("tagName"),
    ],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagName: String,
)
