package com.myvault.app.widget.note

import com.myvault.app.data.local.dao.CourseDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class NoteWidgetItem(
    val id: String,
    val title: String,
    val body: String,
    val context: String,
    val courseId: String?,
    val updatedAt: Long,
) {
    val fingerprint: String = listOf(title, body, context, courseId.orEmpty(), updatedAt.toString()).joinToString("\u0000")
}

@Singleton
class NoteWidgetDataSource @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val courseDao: CourseDao,
) {
    fun observeItems(): Flow<List<NoteWidgetItem>> = combine(
        noteDao.observeAll(),
        folderDao.observeAll(),
        courseDao.observeCourses(),
    ) { notes, folders, courses -> buildNoteWidgetItems(notes, folders, courses) }

    suspend fun items(): List<NoteWidgetItem> = buildNoteWidgetItems(
        notes = noteDao.getAll(),
        folders = folderDao.getAll(),
        courses = courseDao.getAllCourses(),
    )

    suspend fun item(noteId: String?): NoteWidgetItem? =
        noteId?.let { id -> items().firstOrNull { it.id == id } }
}

internal fun buildNoteWidgetItems(
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    courses: List<CourseEntity>,
): List<NoteWidgetItem> {
    val foldersById = folders.associateBy { it.id }
    val coursesById = courses.associateBy { it.id }
    return notes.mapNotNull { note ->
        if (note.deletedAt != null) return@mapNotNull null
        val folder = note.folderId?.let(foldersById::get)
        val mode = folder?.mode ?: FOLDER_MODE_STUDY
        if (mode != FOLDER_MODE_STUDY && !mode.startsWith(COURSE_MODE_PREFIX)) return@mapNotNull null
        val courseId = mode.removePrefix(COURSE_MODE_PREFIX).takeIf { mode.startsWith(COURSE_MODE_PREFIX) && it.isNotBlank() }
        NoteWidgetItem(
            id = note.id,
            title = note.title.ifBlank { "Untitled note" },
            body = note.bodyPlainText,
            context = buildNoteWidgetContext(note.folderId, mode, foldersById, coursesById),
            courseId = courseId,
            updatedAt = note.updatedAt,
        )
    }.sortedByDescending { it.updatedAt }
}

private fun buildNoteWidgetContext(
    folderId: String?,
    mode: String,
    foldersById: Map<String, FolderEntity>,
    coursesById: Map<String, CourseEntity>,
): String {
    val path = mutableListOf<FolderEntity>()
    val visited = mutableSetOf<String>()
    var currentId = folderId
    while (currentId != null && visited.add(currentId)) {
        val folder = foldersById[currentId] ?: break
        path += folder
        currentId = folder.parentId
    }
    val names = path.asReversed().mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
    if (!mode.startsWith(COURSE_MODE_PREFIX)) {
        return (listOf("Study") + names).joinToString(" · ")
    }
    val courseId = mode.removePrefix(COURSE_MODE_PREFIX)
    val courseTitle = coursesById[courseId]?.title?.trim()?.takeIf(String::isNotEmpty)
        ?: names.firstOrNull()
        ?: "Course"
    val nestedNames = names.dropWhile { it.equals(courseTitle, ignoreCase = true) }
    return (listOf("Courses", courseTitle) + nestedNames).distinctAdjacent().joinToString(" · ")
}

private fun List<String>.distinctAdjacent(): List<String> = filterIndexed { index, value ->
    index == 0 || !value.equals(get(index - 1), ignoreCase = true)
}

private const val COURSE_MODE_PREFIX = "course:"
