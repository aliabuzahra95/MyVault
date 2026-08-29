package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLocationTest {
    @Test
    fun `nested Study location contains complete ancestry`() {
        val parent = folder(id = "parent", parentId = null, name = "Quranic Lessons")
        val child = folder(id = "child", parentId = parent.id, name = "Reflections")

        val location = buildSearchLocation(
            folderId = child.id,
            folderMode = FOLDER_MODE_STUDY,
            foldersById = listOf(parent, child).associateBy { it.id },
        )

        assertEquals("Study / Quranic Lessons / Reflections", location)
    }

    @Test
    fun `unfiled location has one clean separator`() {
        assertEquals(
            "Study / Unfiled",
            buildSearchLocation(folderId = null, folderMode = null, foldersById = emptyMap()),
        )
    }

    @Test
    fun `personal location uses its real workspace label`() {
        val inbox = folder(id = "inbox", parentId = null, name = "Inbox", mode = FOLDER_MODE_PERSONAL)

        assertEquals(
            "Personal / Inbox",
            buildSearchLocation(inbox.id, inbox.mode, mapOf(inbox.id to inbox)),
        )
    }

    private fun folder(
        id: String,
        parentId: String?,
        name: String,
        mode: String = FOLDER_MODE_STUDY,
    ) = FolderEntity(
        id = id,
        parentId = parentId,
        name = name,
        orderIndex = 0,
        isFavourite = false,
        mode = mode,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
