package com.myvault.app.data.repository

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupGraphLocalNotePreservationTest {
    private fun note(id: String) = JSONObject().put("id", id).put("folderId", JSONObject.NULL).put("parentNoteId", JSONObject.NULL)
    private fun record(group: String, row: JSONObject) = CapturedBackupRecord(group, IncrementalBackupFormat.key(group, row), "UPSERT", 10, row.toString(), backupRecordDependencies(group, row))
    private val existing = BackupRecordChange("notes.json", listOf("a"), note("a"))

    @Test fun newNoteAndRichBodySurviveAlongsideDifferentRemoteNote() {
        val local = listOf(record("notes.json", note("b")), record("blocks.json", JSONObject().put("id", "body-b").put("noteId", "b").put("content", "English العربية")))
        assertTrue(canPreserveGraphLocalNotes(local, listOf(existing), listOf(existing)))
        val frozen = JSONObject().put("preservedLocalNotes", preservedGraphNotesJson(local))
        assertEquals(local, readPreservedGraphNotes(frozen))
    }

    @Test fun existingEditAndPreviouslyDeletedIdentityCannotMasqueradeAsNew() {
        val local = listOf(record("notes.json", note("a")))
        assertFalse(canPreserveGraphLocalNotes(local, listOf(existing), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(local, listOf(BackupRecordChange("notes.json", listOf("a"))), emptyList()))
    }

    @Test fun deletesSettingsUnrelatedRowsAndMissingNoteOwnersRemainBlocked() {
        val new = record("notes.json", note("b"))
        assertFalse(canPreserveGraphLocalNotes(listOf(new.copy(operation = "DELETE", payloadJson = null)), emptyList(), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(listOf(new, record("folders.json", JSONObject().put("id", "folder"))), emptyList(), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(listOf(record("blocks.json", JSONObject().put("id", "body").put("noteId", "b"))), emptyList(), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(listOf(new, record("blocks.json", JSONObject().put("id", "body").put("noteId", "a"))), emptyList(), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(listOf(new.copy(group = "settings.json")), emptyList(), emptyList()))
    }

    @Test fun incomingChangeOrDeletionOfParentBlocksBeforeAnyApplication() {
        val child = record("notes.json", note("b").put("parentNoteId", "a"))
        assertTrue(canPreserveGraphLocalNotes(listOf(child), listOf(existing), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(listOf(child), listOf(existing), listOf(existing)))
        assertFalse(canPreserveGraphLocalNotes(listOf(child), listOf(existing), listOf(BackupRecordChange("notes.json", listOf("a")))))
    }

    @Test fun newAttachmentAndExistingTagLinkMustBelongToPreservedNote() {
        val local = listOf(record("notes.json", note("b")),
            record("attachments.json", JSONObject().put("id", "pdf-b").put("noteId", "b")),
            record("note_tags.json", JSONObject().put("noteId", "b").put("tagName", "study")))
        assertTrue(canPreserveGraphLocalNotes(local, emptyList(), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(local, listOf(BackupRecordChange("attachments.json", listOf("pdf-b"))), emptyList()))
        assertFalse(canPreserveGraphLocalNotes(local, emptyList(), listOf(BackupRecordChange("tags.json", listOf("study")))))
    }
}
