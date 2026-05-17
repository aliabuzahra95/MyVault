package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class KnowledgeTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)
