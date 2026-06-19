package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rootFolderId: String? = null,
    val lastOpenedNoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "course_folders",
    indices = [Index("courseId", "sortOrder")],
)
data class CourseFolderEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "course_notes",
    indices = [Index("courseId", "folderId", "sortOrder"), Index("lastOpenedAt")],
)
data class CourseNoteEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val folderId: String,
    val title: String,
    val body: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
)

@Entity(
    tableName = "course_sticky_notes",
    indices = [Index("courseId", "sortOrder")],
)
data class CourseStickyNoteEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val text: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "course_concept_cards",
    indices = [Index("courseId", "sortOrder")],
)
data class CourseConceptCardEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val term: String,
    val arabicTerm: String? = null,
    val definition: String,
    val details: String? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
