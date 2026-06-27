package com.myvault.app.ai.home

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_chat_history")
data class HomeChatHistoryEntity(
    @PrimaryKey val id: String,
    val userQuery: String,
    val assistantAnswer: String,
    val attachedTitles: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val messagesJson: String = "",
)
