package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.FOLDER_MODE_PERSONAL
import com.myvault.app.data.local.entity.FOLDER_MODE_STUDY
import com.myvault.app.data.local.entity.NoteEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteWorkspaceRoutingTest {
    private val folderModes = mapOf(
        "personal-inbox" to FOLDER_MODE_PERSONAL,
        "islamic-folder" to FOLDER_MODE_STUDY,
    )

    @Test
    fun `Study root note stays in Islamic workspace`() {
        val note = note(folderId = null)
        assertTrue(noteBelongsToMode(note, folderModes, FOLDER_MODE_STUDY))
        assertFalse(noteBelongsToMode(note, folderModes, FOLDER_MODE_PERSONAL))
    }

    @Test
    fun `Personal Inbox is excluded from Islamic note picker`() {
        val note = note(folderId = "personal-inbox")
        assertTrue(noteBelongsToMode(note, folderModes, FOLDER_MODE_PERSONAL))
        assertFalse(noteBelongsToMode(note, folderModes, FOLDER_MODE_STUDY))
    }

    @Test
    fun `Islamic folder note remains available to PDF tools`() {
        assertTrue(noteBelongsToMode(note("islamic-folder"), folderModes, FOLDER_MODE_STUDY))
    }

    @Test
    fun `deleted note is never offered as a destination`() {
        assertFalse(noteBelongsToMode(note(null, deletedAt = 9L), folderModes, FOLDER_MODE_STUDY))
    }

    private fun note(folderId: String?, deletedAt: Long? = null) = NoteEntity(
        id = "note",
        folderId = folderId,
        title = "Note",
        bodyPlainText = "",
        isPinned = false,
        isFavourite = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deletedAt,
    )
}
