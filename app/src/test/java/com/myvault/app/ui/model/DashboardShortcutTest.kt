package com.myvault.app.ui.model

import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.repository.*
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.viewmodel.LibraryFileItem
import org.junit.Assert.*
import org.junit.Test

class DashboardShortcutTest {
    private fun recent(id: String, time: Long = 1) = DashboardActivityItem(DashboardActivityKind.Library, id, "Same title", "Library", time)
    private fun folder(id: String, parent: String? = null) = FolderEntity(id, parent, "Same name", orderIndex = 0,
        isFavourite = false, createdAt = 1, updatedAt = 1)
    private fun file(id: String) = LibraryFileItem(id, "Same name.pdf", "PDF", "1 KB", "", "application/pdf", "/fixture")

    @Test fun fiveDistinctDocumentsUseIdentityNotTitle() {
        val records = (0..7).map { recent("$it", it.toLong()) } + recent("7", 10)
        assertEquals(listOf("7", "6", "5", "4", "3"), dashboardLibraryRecents(records).map { it.destinationId })
    }
    @Test fun reopeningMovesDocumentWithoutDuplicate() {
        var state = DashboardActivityState()
        listOf(recent("a", 1), recent("b", 2), recent("a", 3)).forEach { state = updateDashboardActivityState(state, it) }
        assertEquals(listOf("a", "b"), state.libraryRecents.map { it.destinationId })
    }
    @Test fun manyStudyOpensDoNotEraseLibraryHistory() {
        var state = updateDashboardActivityState(DashboardActivityState(), recent("pdf"))
        repeat(100) { state = updateDashboardActivityState(state,
            DashboardActivityItem(DashboardActivityKind.Note, "note-$it", "Note", "Study", it + 2L)) }
        assertEquals("pdf", state.libraryRecents.single().destinationId)
    }
    @Test fun existingRecentHistorySeedsDocumentHistory() {
        val state = updateDashboardActivityState(DashboardActivityState(recents = listOf(recent("old"))),
            DashboardActivityItem(DashboardActivityKind.Note, "note", "Note", "Study", 2))
        assertEquals("old", state.libraryRecents.single().destinationId)
    }
    @Test fun pageNumbersAreOneBasedOnlyWhenAvailable() {
        assertEquals("Last read · Page 42", dashboardReadingPosition(41, 500))
        listOf(null to null, 41 to null, -1 to 50, 51 to 50).forEach { (page, count) ->
            assertEquals("Last opened · Position unavailable", dashboardReadingPosition(page, count))
        }
    }
    @Test fun currentSavedPageReplacesStaleDashboardPage() {
        val old = recent("pdf").copy(pageIndex = 1, pageCount = 300)
        val state = DashboardActivityState(lastLibrary = old, recents = listOf(old), libraryRecents = listOf(old))
        val resolved = withDashboardReadingPositions(state, listOf(PdfReadingProgressEntity("pdf", 41, 300, 0.1f, 1, 1)))
        assertEquals(41, resolved.libraryRecents.single().pageIndex)
        assertEquals(41, resolved.lastLibrary?.pageIndex)
        assertNull(withDashboardReadingPositions(state, emptyList()).lastLibrary?.pageIndex)
    }
    @Test fun stablePinsDoNotFollowOpenOrderOrDuplicateIds() {
        val notes = listOf(VaultNoteCardData("Same title", "", "b"), VaultNoteCardData("Same title", "", "a"))
        val files = listOf(file("b"), file("a"))
        val first = dashboardShortcuts(notes, files, listOf(folder("a")))
        val second = dashboardShortcuts(notes.reversed() + notes, files.reversed(), listOf(folder("a")))
        assertEquals(first.map { it.key }, second.map { it.key })
        assertEquals(5, first.size)
    }
    @Test fun deletedMissingAndCyclicFolderTargetsAreNotOffered() {
        val folders = listOf(folder("ok"), folder("deleted").copy(deletedAt = 2), folder("child", "deleted"),
            folder("orphan", "missing"), folder("cycle", "cycle"))
        assertEquals(listOf("ok"), availableDashboardFolders(folders.map { it.id }.toSet(), folders).map { it.id })
        assertEquals(5, folders.size)
    }
    @Test fun fortyPinsHaveStableDistinctShortcutIdentity() {
        val pins = dashboardShortcuts((0 until 40).map { VaultNoteCardData("Note $it", "", "$it") }, emptyList(), emptyList())
        assertEquals(40, pins.size)
        assertEquals(40, pins.map { it.key }.distinct().size)
    }

    @Test fun customOrderPreservedAcrossMixedTypes() {
        val notes = listOf(VaultNoteCardData("Note 1", "", "n1"), VaultNoteCardData("Note 2", "", "n2"))
        val files = listOf(file("f1"))
        val folders = listOf(folder("d1"))
        val desiredOrder = listOf("Folder:d1", "Document:f1", "Note:n2", "Note:n1")
        val shortcuts = dashboardShortcuts(notes, files, folders, order = desiredOrder)
        assertEquals(desiredOrder, shortcuts.map { it.key })
    }

    @Test fun itemsNotInCustomOrderSortAtEndAlphabetically() {
        val notes = listOf(VaultNoteCardData("Note 1", "", "n1"), VaultNoteCardData("Note 2", "", "n2"))
        val files = listOf(file("f1"))
        val desiredOrder = listOf("Note:n2")
        val shortcuts = dashboardShortcuts(notes, files, emptyList(), order = desiredOrder)
        assertEquals(listOf("Note:n2", "Document:f1", "Note:n1"), shortcuts.map { it.key })
    }
}
