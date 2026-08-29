package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val FOLDER_MODE_STUDY = "study"
const val FOLDER_MODE_PERSONAL = "personal"
const val FOLDER_MODE_LIBRARY = "library"
const val FOLDER_MODE_PERSONAL_LIBRARY = "personal_library"

const val FOLDER_COLOR_RED = "red"
const val FOLDER_COLOR_BLUE = "blue"
const val FOLDER_COLOR_GREEN = "green"
const val FOLDER_COLOR_PURPLE = "purple"
const val FOLDER_COLOR_YELLOW = "yellow"

val SUPPORTED_FOLDER_COLOR_KEYS = setOf(
    FOLDER_COLOR_RED,
    FOLDER_COLOR_BLUE,
    FOLDER_COLOR_GREEN,
    FOLDER_COLOR_PURPLE,
    FOLDER_COLOR_YELLOW,
)

internal fun normalizeFolderColorKey(value: String?): String? =
    value?.trim()?.lowercase()?.takeIf(SUPPORTED_FOLDER_COLOR_KEYS::contains)

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
    val description: String? = null,
    val orderIndex: Int,
    val isFavourite: Boolean,
    val mode: String = FOLDER_MODE_STUDY,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val colorKey: String? = null,
)
