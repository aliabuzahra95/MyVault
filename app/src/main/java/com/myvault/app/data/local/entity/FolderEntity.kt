package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val FOLDER_MODE_STUDY = "study"
const val FOLDER_MODE_PERSONAL = "personal"
const val FOLDER_MODE_LIBRARY = "library"

@Entity(
    tableName = "folders",
    indices = [
        Index("parentId", "orderIndex"),
        Index("mode", "parentId", "orderIndex"),
        Index("deletedAt", "orderIndex"),
    ],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val orderIndex: Int,
    val isFavourite: Boolean,
    val mode: String = FOLDER_MODE_STUDY,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
