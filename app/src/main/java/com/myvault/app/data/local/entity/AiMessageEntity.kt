package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val noteId: String,
    val role: String,
    val content: String,
    val action: String?,
    val provider: String?,
    val model: String?,
    val selectedTextContext: String?,
    val createdAt: Long,
)
