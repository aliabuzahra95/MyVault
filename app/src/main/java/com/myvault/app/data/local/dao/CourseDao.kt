package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.CourseFolderEntity
import com.myvault.app.data.local.entity.CourseNoteEntity
import com.myvault.app.data.local.entity.CourseStickyNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY updatedAt DESC")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("SELECT * FROM course_concept_cards ORDER BY courseId, sortOrder, createdAt")
    suspend fun getAllConceptCards(): List<CourseConceptCardEntity>

    @Query("SELECT * FROM course_folders ORDER BY courseId, sortOrder, createdAt")
    suspend fun getAllLegacyFolders(): List<CourseFolderEntity>

    @Query("SELECT * FROM course_notes ORDER BY courseId, folderId, sortOrder, createdAt")
    suspend fun getAllLegacyNotes(): List<CourseNoteEntity>

    @Query("SELECT * FROM course_sticky_notes ORDER BY courseId, sortOrder, createdAt")
    suspend fun getAllLegacyStickyNotes(): List<CourseStickyNoteEntity>

    @Query("SELECT * FROM courses ORDER BY updatedAt DESC")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course_folders ORDER BY courseId, sortOrder, createdAt")
    fun observeFolders(): Flow<List<CourseFolderEntity>>

    @Query("SELECT * FROM course_notes ORDER BY courseId, folderId, sortOrder, createdAt")
    fun observeNotes(): Flow<List<CourseNoteEntity>>

    @Query("SELECT * FROM course_sticky_notes ORDER BY courseId, sortOrder, createdAt")
    fun observeStickyNotes(): Flow<List<CourseStickyNoteEntity>>

    @Query("SELECT * FROM course_concept_cards ORDER BY courseId, sortOrder, createdAt")
    fun observeConceptCards(): Flow<List<CourseConceptCardEntity>>

    @Query("SELECT * FROM course_folders WHERE courseId = :courseId ORDER BY sortOrder, createdAt")
    suspend fun getLegacyFolders(courseId: String): List<CourseFolderEntity>

    @Query("SELECT * FROM course_notes WHERE courseId = :courseId ORDER BY sortOrder, createdAt")
    suspend fun getLegacyNotes(courseId: String): List<CourseNoteEntity>

    @Query("SELECT * FROM course_sticky_notes WHERE courseId = :courseId ORDER BY sortOrder, createdAt")
    suspend fun getLegacyStickyNotes(courseId: String): List<CourseStickyNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: CourseFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: CourseNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStickyNote(stickyNote: CourseStickyNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConceptCard(concept: CourseConceptCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConceptCards(concepts: List<CourseConceptCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegacyFolders(folders: List<CourseFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegacyNotes(notes: List<CourseNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegacyStickyNotes(stickyNotes: List<CourseStickyNoteEntity>)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: String): CourseEntity?

    @Query("SELECT * FROM course_folders WHERE id = :id")
    suspend fun getFolder(id: String): CourseFolderEntity?

    @Query("SELECT * FROM course_notes WHERE id = :id")
    suspend fun getNote(id: String): CourseNoteEntity?

    @Query("SELECT * FROM course_sticky_notes WHERE id = :id")
    suspend fun getStickyNote(id: String): CourseStickyNoteEntity?

    @Query("SELECT * FROM course_concept_cards WHERE id = :id")
    suspend fun getConceptCard(id: String): CourseConceptCardEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM course_folders WHERE courseId = :courseId")
    suspend fun nextFolderOrder(courseId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM course_notes WHERE folderId = :folderId")
    suspend fun nextNoteOrder(folderId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM course_sticky_notes WHERE courseId = :courseId")
    suspend fun nextStickyOrder(courseId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM course_concept_cards WHERE courseId = :courseId")
    suspend fun nextConceptOrder(courseId: String): Int

    @Query("DELETE FROM course_notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Query("DELETE FROM course_sticky_notes WHERE id = :id")
    suspend fun deleteStickyNote(id: String)

    @Query("DELETE FROM course_concept_cards WHERE id = :id")
    suspend fun deleteConceptCard(id: String)

    @Query("DELETE FROM course_notes WHERE folderId = :folderId")
    suspend fun deleteNotesForFolder(folderId: String)

    @Query("DELETE FROM course_folders WHERE id = :folderId")
    suspend fun deleteFolderOnly(folderId: String)

    @Transaction
    suspend fun deleteFolder(folderId: String) {
        deleteNotesForFolder(folderId)
        deleteFolderOnly(folderId)
    }

    @Query("DELETE FROM course_notes WHERE courseId = :courseId")
    suspend fun deleteNotesForCourse(courseId: String)

    @Query("DELETE FROM course_folders WHERE courseId = :courseId")
    suspend fun deleteFoldersForCourse(courseId: String)

    @Query("DELETE FROM course_sticky_notes WHERE courseId = :courseId")
    suspend fun deleteStickyNotesForCourse(courseId: String)

    @Query("DELETE FROM course_concept_cards WHERE courseId = :courseId")
    suspend fun deleteConceptCardsForCourse(courseId: String)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseOnly(courseId: String)

    @Transaction
    suspend fun deleteCourse(courseId: String) {
        deleteNotesForCourse(courseId)
        deleteFoldersForCourse(courseId)
        deleteStickyNotesForCourse(courseId)
        deleteConceptCardsForCourse(courseId)
        deleteCourseOnly(courseId)
    }
}
