package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.CourseDao
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun courseFolderMode(courseId: String) = "course:$courseId"

@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao,
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val stickyNoteRepository: FolderStickyNoteRepository,
) {
    val courses = courseDao.observeCourses()
    val concepts = courseDao.observeConceptCards()

    suspend fun createCourse(title: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val cleanTitle = title.trim().ifBlank { "Untitled course" }
        val rootFolderId = folderRepository.createFolder(null, cleanTitle, courseFolderMode(id))
        courseDao.upsertCourse(CourseEntity(id, cleanTitle, rootFolderId = rootFolderId, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun ensureWorkspace(course: CourseEntity): String {
        course.rootFolderId?.let { return it }
        val rootFolderId = folderRepository.createFolder(null, course.title, courseFolderMode(course.id))
        val legacyFolders = courseDao.getLegacyFolders(course.id)
        val folderMap = legacyFolders.associate { legacy ->
            legacy.id to folderRepository.createFolder(rootFolderId, legacy.title, courseFolderMode(course.id))
        }
        courseDao.getLegacyNotes(course.id).forEach { legacy ->
            val folderId = folderMap[legacy.folderId] ?: rootFolderId
            val noteId = noteRepository.createNote(folderId, legacy.title)
            if (legacy.body.isNotBlank()) noteRepository.saveRichText(noteId, legacy.body, "[]")
        }
        courseDao.getLegacyStickyNotes(course.id).forEach { stickyNoteRepository.create(rootFolderId, it.text) }
        courseDao.upsertCourse(course.copy(rootFolderId = rootFolderId, updatedAt = System.currentTimeMillis()))
        return rootFolderId
    }

    suspend fun renameCourse(id: String, title: String) {
        val current = courseDao.getCourse(id) ?: return
        val clean = title.trim().ifBlank { current.title }
        courseDao.upsertCourse(current.copy(title = clean, updatedAt = System.currentTimeMillis()))
        current.rootFolderId?.let { folderRepository.updateFolderDetails(it, clean, null) }
    }

    suspend fun deleteCourse(id: String) {
        courseDao.getCourse(id)?.rootFolderId?.let { folderRepository.deleteFolderTree(it) }
        courseDao.deleteCourse(id)
    }

    suspend fun markNoteOpened(courseId: String, noteId: String) {
        val current = courseDao.getCourse(courseId) ?: return
        courseDao.upsertCourse(current.copy(lastOpenedNoteId = noteId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun createConcept(courseId: String, term: String, arabicTerm: String?, definition: String, details: String?) {
        val now = System.currentTimeMillis()
        courseDao.upsertConceptCard(
            CourseConceptCardEntity(
                UUID.randomUUID().toString(),
                courseId,
                term.trim().ifBlank { "Untitled concept" },
                arabicTerm?.trim()?.takeIf { it.isNotBlank() },
                definition.trim(),
                details?.trim()?.takeIf { it.isNotBlank() },
                courseDao.nextConceptOrder(courseId),
                now,
                now,
            ),
        )
    }

    suspend fun saveConcept(id: String, term: String, arabicTerm: String?, definition: String, details: String?) {
        val current = courseDao.getConceptCard(id) ?: return
        courseDao.upsertConceptCard(
            current.copy(
                term = term.trim().ifBlank { current.term },
                arabicTerm = arabicTerm?.trim()?.takeIf { it.isNotBlank() },
                definition = definition.trim(),
                details = details?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteConcept(id: String) = courseDao.deleteConceptCard(id)
}
