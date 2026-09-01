package com.myvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardActivityStateTest {
    @Test
    fun `each category retains its own latest destination`() {
        val note = item(DashboardActivityKind.Note, "note", 10)
        val library = item(DashboardActivityKind.Library, "pdf", 20)
        val course = item(DashboardActivityKind.Course, "course-note", 30)
        val state = listOf(note, library, course).fold(DashboardActivityState()) { current, next ->
            updateDashboardActivityState(current, next)
        }
        assertEquals("note", state.lastNote?.destinationId)
        assertEquals("pdf", state.lastLibrary?.destinationId)
        assertEquals("course-note", state.lastCourse?.destinationId)
        assertEquals(listOf("course-note", "pdf", "note"), state.recents.map { it.destinationId })
    }

    @Test
    fun `reopening an item moves it to front without replacing other category slots`() {
        val note = item(DashboardActivityKind.Note, "note", 10)
        val pdf = item(DashboardActivityKind.Library, "pdf", 20)
        val reopened = note.copy(openedAt = 30)
        val state = updateDashboardActivityState(
            updateDashboardActivityState(updateDashboardActivityState(DashboardActivityState(), note), pdf),
            reopened,
        )
        assertEquals(listOf("note", "pdf"), state.recents.map { it.destinationId })
        assertEquals("pdf", state.lastLibrary?.destinationId)
        assertNull(state.lastCourse)
    }

    private fun item(kind: DashboardActivityKind, id: String, openedAt: Long) = DashboardActivityItem(
        kind = kind,
        destinationId = id,
        title = id,
        context = kind.name,
        openedAt = openedAt,
    )
}
