package com.myvault.app.data.sync.record

import com.myvault.app.data.local.entity.BlockEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.RecordSyncPendingEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecordSyncPilotTest {
    private val id = "SYNC_TEST_CODEX_12345678"

    @Test fun onlyReservedIdsMayEnterPilot() {
        assertTrue(isPilotNoteId(id))
        assertFalse(isPilotNoteId("ordinary-note-id"))
        assertFalse(isPilotNoteId(PilotNotePrefix))
    }

    @Test fun rootStudyRichTextNoteIsAccepted() {
        validatePilotRevision(revision())
    }

    @Test fun renamingARegisteredTestNoteIsAllowedButMovingItIsNot() {
        validatePilotRevision(revision(title = "My renamed lesson"))
        rejects(revision(folderId = "existing-folder"))
    }

    @Test fun binaryBlocksAndInternalNoteLinksAreRejected() {
        rejects(revision(blockType = "image"))
        rejects(revision(noteLinks = JSONArray().put(JSONObject().put("noteId", "real-note"))))
    }

    @Test fun deletionRequiresAnExistingParentRevision() {
        rejects(RecordSyncRevision.create("note", id, "client", emptyList(), null))
        validatePilotRevision(RecordSyncRevision.create("note", id, "client", listOf("base"), null))
    }

    @Test fun ownInterruptedUploadCanBecomeBaseForNewerLocalEdit() {
        val pending = RecordSyncPendingEntity("pilot:account", "note", id, 2, 1, null)
        val firstUpload = revision().copy(clientId = "this-phone")
        assertTrue(canAdoptOwnEarlierRevision("this-phone", null, pending, firstUpload))
        assertFalse(canAdoptOwnEarlierRevision("other-phone", null, pending, firstUpload))
        assertFalse(canAdoptOwnEarlierRevision("this-phone", "different-base", pending, firstUpload))
        assertFalse(canAdoptOwnEarlierRevision("this-phone", null, null, firstUpload))
    }

    private fun revision(
        title: String = id,
        folderId: String? = null,
        blockType: String = "rich_text",
        noteLinks: JSONArray = JSONArray(),
    ): RecordSyncRevision {
        val note = NoteEntity(
            id = id, folderId = folderId, title = title, bodyPlainText = "Test body",
            isPinned = false, isFavourite = false, createdAt = 1L, updatedAt = 2L,
        )
        val rich = JSONObject().put("text", "Test body").put("styleMarks", JSONArray()).put("noteLinks", noteLinks)
        val block = BlockEntity("$id-rich-text", id, blockType, rich.toString(), 0)
        return RecordSyncRevision.create("note", id, "client", emptyList(), notePayload(note, listOf(block)))
    }

    private fun rejects(revision: RecordSyncRevision) {
        try {
            validatePilotRevision(revision)
            fail("Expected pilot guard to reject the revision")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
