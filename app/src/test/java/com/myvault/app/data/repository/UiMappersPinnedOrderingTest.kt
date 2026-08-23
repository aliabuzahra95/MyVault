package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class UiMappersPinnedOrderingTest {
    @Test
    fun pinnedNotesAreStableAtTheTopAndUnpinningRestoresNormalOrder() {
        val folder = FolderEntity(
            id = "folder",
            parentId = null,
            name = "Folder",
            orderIndex = 0,
            isFavourite = false,
            mode = FOLDER_MODE_STUDY,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val notes = listOf(
            note("first", order = 0),
            note("pinned-a", order = 1, pinned = true),
            note("middle", order = 2),
            note("pinned-b", order = 3, folderPinned = true),
            note("last", order = 4),
        )

        val pinnedOrder = buildTree(listOf(folder), notes, emptyList(), emptyList())
            .single()
            .children
            .map { it.id }

        assertEquals(listOf("pinned-a", "pinned-b", "first", "middle", "last"), pinnedOrder)

        val unpinnedOrder = buildTree(
            listOf(folder),
            notes.map { it.copy(isPinned = false, isFolderPinned = false) },
            emptyList(),
            emptyList(),
        ).single().children.map { it.id }

        assertEquals(listOf("first", "pinned-a", "middle", "pinned-b", "last"), unpinnedOrder)
    }

    private fun note(
        id: String,
        order: Int,
        pinned: Boolean = false,
        folderPinned: Boolean = false,
    ) = NoteEntity(
        id = id,
        folderId = "folder",
        title = id,
        bodyPlainText = "",
        isPinned = pinned,
        isFolderPinned = folderPinned,
        isFavourite = false,
        orderIndex = order,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
