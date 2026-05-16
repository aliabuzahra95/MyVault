package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val type: String,
    val content: String,
    val orderIndex: Int,
)
