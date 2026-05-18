package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_conversations",
    indices = [
        Index("noteId", "updatedAt"),
    ],
)
data class AiConversationEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
