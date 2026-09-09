package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import org.junit.Assert.*
import org.junit.Test

class DashboardAvailableActivityTest {
    @Test fun `deleted note disappears from Continue and recents without destroying history`() {
        val deleted = item("deleted", 20)
        val active = item("active", 10)
        val history = DashboardActivityState(lastNote = deleted, recents = listOf(deleted, active))
        val actual = resolve(history, listOf(note("deleted").copy(deletedAt = 30), note("active")))
        assertEquals("active", actual.lastNote?.destinationId)
        assertEquals(listOf("active"), actual.recents.map { it.destinationId })
        assertEquals(listOf("deleted", "active"), history.recents.map { it.destinationId })
        assertEquals("deleted", resolve(history, listOf(note("deleted"), note("active"))).lastNote?.destinationId)
    }

    @Test fun `missing or deleted parent is not offered as a valid target`() {
        val history = DashboardActivityState(lastNote = item("n", 10))
        assertNull(resolve(history, listOf(note("n", "gone"))).lastNote)
        assertNull(resolve(history, listOf(note("n", "gone")), listOf(folder("gone").copy(deletedAt = 1))).lastNote)
    }

    @Test fun `personal note title and current folder replace stale Study metadata`() {
        val history = DashboardActivityState(lastNote = item("n", 10))
        val actual = resolve(history, listOf(note("n", "inbox").copy(title = "Renamed")),
            listOf(folder("inbox", "personal", "Inbox"))).lastNote!!
        assertEquals("Renamed", actual.title)
        assertEquals("Personal / Inbox", actual.context)
        assertEquals("inbox", actual.folderId)
        assertEquals(10, actual.openedAt)
    }

    @Test fun `move into course resolves exact current course and deduplicates old category`() {
        val old = item("n", 10)
        val history = DashboardActivityState(lastNote = old, recents = listOf(old, old.copy(kind = DashboardActivityKind.Course)))
        val course = CourseEntity("course", "Course", createdAt = 1, updatedAt = 1)
        val actual = resolveDashboardActivityState(history, listOf(note("n", "f")),
            listOf(folder("f", "course:course", "Course")), emptyList(), listOf(course))
        assertNull(actual.lastNote)
        assertEquals("course", actual.lastCourse?.courseId)
        assertEquals("f", actual.lastCourse?.folderId)
        assertEquals(1, actual.recents.size)
        assertNull(resolve(history, listOf(note("n", "f")), listOf(folder("f", "course:course"))).lastCourse)
    }

    @Test fun `deleted file is removed and surviving PDF preserves exact page`() {
        val pdf = item("pdf", 20).copy(kind = DashboardActivityKind.Library, pageIndex = 17, pageCount = 824)
        val history = DashboardActivityState(lastLibrary = pdf, recents = listOf(pdf))
        val file = AttachmentEntity("pdf", "", fileName = "Current.pdf", mimeType = "application/pdf",
            sizeBytes = 10, localPath = "/unchanged", remoteUrl = null, createdAt = 1)
        val resolved = resolveDashboardActivityState(history, emptyList(), emptyList(), listOf(file), emptyList())
        assertEquals("Current", resolved.lastLibrary?.title)
        assertEquals(17, resolved.lastLibrary?.pageIndex)
        assertEquals(824, resolved.lastLibrary?.pageCount)
        assertNull(resolveDashboardActivityState(history, emptyList(), emptyList(),
            listOf(file.copy(deletedAt = 2)), emptyList()).lastLibrary)
    }

    private fun resolve(history: DashboardActivityState, notes: List<NoteEntity>, folders: List<FolderEntity> = emptyList()) =
        resolveDashboardActivityState(history, notes, folders, emptyList(), emptyList())

    private fun note(id: String, folderId: String? = null) = NoteEntity(id, folderId, title = id,
        bodyPlainText = "preserved", isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)

    private fun folder(id: String, mode: String = "study", name: String = id) = FolderEntity(id, null,
        name, orderIndex = 0, isFavourite = false, mode = mode, createdAt = 1, updatedAt = 1)

    private fun item(id: String, time: Long) = DashboardActivityItem(DashboardActivityKind.Note, id,
        "Old title", "Study / Old folder", time)
}
