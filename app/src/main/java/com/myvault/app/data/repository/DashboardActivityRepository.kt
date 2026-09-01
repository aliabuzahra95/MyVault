package com.myvault.app.data.repository

import android.content.Context
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.CourseDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.PdfReadingProgressDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class DashboardActivityKind { Note, Library, Course }

data class DashboardActivityItem(
    val kind: DashboardActivityKind,
    val destinationId: String,
    val title: String,
    val context: String,
    val openedAt: Long,
    val folderId: String? = null,
    val courseId: String? = null,
    val pageIndex: Int? = null,
    val pageCount: Int? = null,
)

data class DashboardActivityState(
    val lastNote: DashboardActivityItem? = null,
    val lastLibrary: DashboardActivityItem? = null,
    val lastCourse: DashboardActivityItem? = null,
    val recents: List<DashboardActivityItem> = emptyList(),
)

@Singleton
class DashboardActivityRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val attachmentDao: AttachmentDao,
    private val courseDao: CourseDao,
    private val pdfReadingProgressDao: PdfReadingProgressDao,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<DashboardActivityState> = _state.asStateFlow()

    suspend fun recordNoteOpened(noteId: String) = mutex.withLock {
        val note = noteDao.getById(noteId) ?: return@withLock
        val folders = folderDao.getAll()
        val foldersById = folders.associateBy { it.id }
        val folder = note.folderId?.let(foldersById::get)
        val courseId = folder?.mode?.takeIf { it.startsWith(COURSE_MODE_PREFIX) }
            ?.removePrefix(COURSE_MODE_PREFIX)
        val path = buildFolderPath(note.folderId, foldersById)
        val item = if (courseId != null) {
            val course = courseDao.getCourse(courseId)
            DashboardActivityItem(
                kind = DashboardActivityKind.Course,
                destinationId = note.id,
                title = note.title.ifBlank { "Untitled note" },
                context = listOfNotNull(course?.title, path.takeIf(String::isNotBlank)).joinToString(" / ")
                    .ifBlank { "Course note" },
                openedAt = System.currentTimeMillis(),
                folderId = note.folderId,
                courseId = courseId,
            )
        } else {
            DashboardActivityItem(
                kind = DashboardActivityKind.Note,
                destinationId = note.id,
                title = note.title.ifBlank { "Untitled note" },
                context = listOf("Study", path).filter(String::isNotBlank).joinToString(" / "),
                openedAt = System.currentTimeMillis(),
                folderId = note.folderId,
            )
        }
        store(item)
    }

    suspend fun recordLibraryOpened(attachmentId: String) = mutex.withLock {
        val attachment = attachmentDao.getByIdIncludingDeleted(attachmentId)
            ?.takeIf { it.deletedAt == null && (it.noteId.isBlank() || it.libraryFolderId != null) }
            ?: return@withLock
        val folders = folderDao.getAll().associateBy { it.id }
        val path = buildFolderPath(attachment.libraryFolderId, folders)
        val progress = pdfReadingProgressDao.getByAttachmentId(attachmentId)
        store(
            DashboardActivityItem(
                kind = DashboardActivityKind.Library,
                destinationId = attachment.id,
                title = attachment.fileName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Document" },
                context = listOf("Library", path).filter(String::isNotBlank).joinToString(" / "),
                openedAt = System.currentTimeMillis(),
                folderId = attachment.libraryFolderId,
                pageIndex = progress?.pageIndex,
                pageCount = progress?.pageCount,
            ),
        )
    }

    suspend fun updateLibraryProgress(attachmentId: String, pageIndex: Int, pageCount: Int) = mutex.withLock {
        if (pageCount <= 0) return@withLock
        val current = _state.value
        val safePage = pageIndex.coerceIn(0, pageCount - 1)
        fun DashboardActivityItem.updated(): DashboardActivityItem =
            if (kind == DashboardActivityKind.Library && destinationId == attachmentId) {
                copy(pageIndex = safePage, pageCount = pageCount)
            } else {
                this
            }
        val next = current.copy(
            lastLibrary = current.lastLibrary?.updated(),
            recents = current.recents.map { it.updated() },
        )
        if (next != current) {
            _state.value = next
            preferences.edit().putString(STATE_KEY, next.toJson().toString()).apply()
        }
    }

    private fun store(item: DashboardActivityItem) {
        val next = updateDashboardActivityState(_state.value, item, MAX_RECENTS)
        _state.value = next
        preferences.edit().putString(STATE_KEY, next.toJson().toString()).apply()
    }

    private fun readState(): DashboardActivityState = runCatching {
        preferences.getString(STATE_KEY, null)?.let(::JSONObject)?.toDashboardState()
    }.getOrNull() ?: DashboardActivityState()

    private fun buildFolderPath(folderId: String?, folders: Map<String, com.myvault.app.data.local.entity.FolderEntity>): String {
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = folderId
        while (current != null && visited.add(current)) {
            val folder = folders[current] ?: break
            folder.name.trim().takeIf(String::isNotBlank)?.let(names::add)
            current = folder.parentId
        }
        return names.asReversed().joinToString(" / ")
    }

    private fun DashboardActivityState.toJson(): JSONObject = JSONObject()
        .put("lastNote", lastNote?.toJson())
        .put("lastLibrary", lastLibrary?.toJson())
        .put("lastCourse", lastCourse?.toJson())
        .put("recents", JSONArray().apply { recents.forEach { put(it.toJson()) } })

    private fun DashboardActivityItem.toJson(): JSONObject = JSONObject()
        .put("kind", kind.name)
        .put("destinationId", destinationId)
        .put("title", title)
        .put("context", context)
        .put("openedAt", openedAt)
        .put("folderId", folderId)
        .put("courseId", courseId)
        .put("pageIndex", pageIndex)
        .put("pageCount", pageCount)

    private fun JSONObject.toDashboardState(): DashboardActivityState {
        val recentJson = optJSONArray("recents") ?: JSONArray()
        return DashboardActivityState(
            lastNote = optJSONObject("lastNote")?.toActivityItem(),
            lastLibrary = optJSONObject("lastLibrary")?.toActivityItem(),
            lastCourse = optJSONObject("lastCourse")?.toActivityItem(),
            recents = (0 until recentJson.length()).mapNotNull { recentJson.optJSONObject(it)?.toActivityItem() },
        )
    }

    private fun JSONObject.toActivityItem(): DashboardActivityItem? = runCatching {
        DashboardActivityItem(
            kind = DashboardActivityKind.valueOf(getString("kind")),
            destinationId = getString("destinationId"),
            title = getString("title"),
            context = optString("context"),
            openedAt = getLong("openedAt"),
            folderId = optString("folderId").takeIf(String::isNotBlank),
            courseId = optString("courseId").takeIf(String::isNotBlank),
            pageIndex = optInt("pageIndex").takeIf { has("pageIndex") && !isNull("pageIndex") },
            pageCount = optInt("pageCount").takeIf { has("pageCount") && !isNull("pageCount") },
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "dashboard_activity"
        const val STATE_KEY = "state_v1"
        const val COURSE_MODE_PREFIX = "course:"
        const val MAX_RECENTS = 24
    }
}

internal fun updateDashboardActivityState(
    current: DashboardActivityState,
    item: DashboardActivityItem,
    maxRecents: Int = 24,
): DashboardActivityState {
    val recents = (listOf(item) + current.recents.filterNot {
        it.kind == item.kind && it.destinationId == item.destinationId
    }).sortedByDescending { it.openedAt }.take(maxRecents)
    return DashboardActivityState(
        lastNote = if (item.kind == DashboardActivityKind.Note) item else current.lastNote,
        lastLibrary = if (item.kind == DashboardActivityKind.Library) item else current.lastLibrary,
        lastCourse = if (item.kind == DashboardActivityKind.Course) item else current.lastCourse,
        recents = recents,
    )
}
