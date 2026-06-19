package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRelationshipGraphTest {
    @Test
    fun descendantIds_returnsEntireNestedBranchInTreeOrder() {
        val notes = listOf(
            note("parent"),
            note("child-a", parentId = "parent"),
            note("grandchild", parentId = "child-a"),
            note("child-b", parentId = "parent"),
            note("unrelated"),
        )

        assertEquals(
            listOf("child-a", "grandchild", "child-b"),
            NoteRelationshipGraph.descendantIds("parent", notes),
        )
    }

    @Test
    fun descendantIds_stopsSafelyWhenCorruptDataContainsCycle() {
        val notes = listOf(
            note("parent", parentId = "child"),
            note("child", parentId = "parent"),
        )

        assertEquals(
            listOf("child"),
            NoteRelationshipGraph.descendantIds("parent", notes),
        )
    }

    @Test
    fun sanitizedForPersistence_detachesMissingParentAndCrossFolderRelationships() {
        val notes = listOf(
            note("parent"),
            note("valid-child", parentId = "parent"),
            note("missing-parent", parentId = "gone"),
            note("cross-folder", parentId = "parent", folderId = "other-folder"),
        )

        val sanitized = NoteRelationshipGraph.sanitizedForPersistence(notes).associateBy { it.id }

        assertEquals("parent", sanitized.getValue("valid-child").parentNoteId)
        assertEquals(null, sanitized.getValue("missing-parent").parentNoteId)
        assertEquals(null, sanitized.getValue("cross-folder").parentNoteId)
    }

    @Test
    fun sanitizedForPersistence_detachesCyclesWithoutDroppingNotes() {
        val notes = listOf(
            note("a", parentId = "b"),
            note("b", parentId = "a"),
        )

        val sanitized = NoteRelationshipGraph.sanitizedForPersistence(notes)

        assertEquals(2, sanitized.size)
        assertEquals(listOf(null, null), sanitized.map { it.parentNoteId })
    }

    private fun note(id: String, parentId: String? = null, folderId: String = "folder") = NoteEntity(
        id = id,
        folderId = folderId,
        parentNoteId = parentId,
        title = id,
        bodyPlainText = "",
        isPinned = false,
        isFolderPinned = false,
        isFavourite = false,
        orderIndex = 0,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
