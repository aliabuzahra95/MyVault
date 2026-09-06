package com.myvault.app.widget.note

import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.ui.navigation.VaultDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteWidgetLogicTest {
    @Test
    fun studyAndCourseNotesRetainReadableLocationAndCourseIdentity() {
        val course = CourseEntity("course-1", "Fiqh", rootFolderId = "course-root", createdAt = 1, updatedAt = 1)
        val folders = listOf(
            folder("study-folder", null, "Aqeedah", FOLDER_MODE_STUDY),
            folder("course-root", null, "Fiqh", "course:course-1"),
            folder("course-week", "course-root", "Week 2", "course:course-1"),
        )
        val items = buildNoteWidgetItems(
            notes = listOf(
                note("study-note", "study-folder", "Study note"),
                note("course-note", "course-week", "Course note"),
            ),
            folders = folders,
            courses = listOf(course),
        ).associateBy { it.id }

        assertEquals("Study · Aqeedah", items.getValue("study-note").context)
        assertNull(items.getValue("study-note").courseId)
        assertEquals("Courses · Fiqh · Week 2", items.getValue("course-note").context)
        assertEquals("course-1", items.getValue("course-note").courseId)
    }

    @Test
    fun deletedAndUnsupportedNotesAreNotOffered() {
        val deleted = note("deleted", null, "Deleted").copy(deletedAt = 5)
        val personalFolder = folder("personal", null, "Personal", "personal")
        val notes = listOf(deleted, note("personal-note", personalFolder.id, "Private"))
        val items = buildNoteWidgetItems(notes, listOf(personalFolder), emptyList())

        assertTrue(items.isEmpty())
    }

    @Test
    fun longBodyIsSplitWithoutDroppingParagraphsOrListMarkers() {
        val body = "First paragraph.\n\n• Evidence one\n• Evidence two\n\n" + "Arabic العربية ".repeat(120)
        val chunks = body.toNoteWidgetChunks(maxCharacters = 180)

        assertTrue(chunks.size > 3)
        assertTrue(chunks.joinToString(" ").contains("• Evidence one"))
        assertTrue(chunks.joinToString(" ").contains("العربية"))
        assertTrue(chunks.all { it.length <= 200 })
    }

    @Test
    fun textSizeAndResponsiveBucketsStayBounded() {
        assertEquals(MIN_NOTE_TEXT_SIZE_LEVEL, adjustedNoteTextSizeLevel(1, -1))
        assertEquals(MAX_NOTE_TEXT_SIZE_LEVEL, adjustedNoteTextSizeLevel(4, 1))
        assertEquals(NoteWidgetSizeBucket.Compact, noteWidgetSizeBucket(140, 100))
        assertEquals(NoteWidgetSizeBucket.ExtraLarge, noteWidgetSizeBucket(500, 600))
        assertTrue(
            noteWidgetBodyTextSize(NoteWidgetSizeBucket.ExtraLarge, 4) >
                noteWidgetBodyTextSize(NoteWidgetSizeBucket.Compact, 1),
        )
    }

    @Test
    fun quickNoteUsesCompactLayoutUntilFullWideLabelFits() {
        assertEquals(QuickNoteSizeBucket.Compact, quickNoteSizeBucket(64))
        assertEquals(QuickNoteSizeBucket.Compact, quickNoteSizeBucket(144))
        assertEquals(QuickNoteSizeBucket.Wide, quickNoteSizeBucket(145))
        assertEquals(QuickNoteSizeBucket.Wide, quickNoteSizeBucket(220))
    }

    @Test
    fun quickCreateWaitsForUnlockAndRejectsRapidDuplicateTap() {
        assertFalse(shouldCreateQuickNote(unlocked = false, pending = true, creationInFlight = false))
        assertTrue(shouldCreateQuickNote(unlocked = true, pending = true, creationInFlight = false))
        assertFalse(shouldCreateQuickNote(unlocked = true, pending = true, creationInFlight = true))
        assertTrue(isQuickNoteTapAccepted(Long.MIN_VALUE, 100))
        assertFalse(isQuickNoteTapAccepted(100, 1_000))
        assertTrue(isQuickNoteTapAccepted(100, 1_600))
    }

    @Test
    fun widgetUsesExactEditorRouteIncludingQuickFocus() {
        assertEquals("editor/note-42?quickFocus=false", VaultDestination.Editor.route("note-42"))
        assertEquals("editor/note-42?quickFocus=true", VaultDestination.Editor.route("note-42", quickFocus = true))
    }

    private fun note(id: String, folderId: String?, title: String) = NoteEntity(
        id = id,
        folderId = folderId,
        title = title,
        bodyPlainText = "Body",
        isPinned = false,
        isFavourite = false,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun folder(id: String, parentId: String?, name: String, mode: String) = FolderEntity(
        id = id,
        parentId = parentId,
        name = name,
        orderIndex = 0,
        isFavourite = false,
        mode = mode,
        createdAt = 1,
        updatedAt = 1,
    )
}
