package com.myvault.app.data.repository

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupGraphRestoreStateTest {
    private fun row(group: String, id: String) = BackupRecordChange(group, listOf(id), JSONObject().put("id", id))

    @Test fun deltaOmissionNeverDeletes() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("notes.json", "old")), setOf("notes.json"))
        state.apply(listOf(row("notes.json", "new")))
        assertEquals(2, state.changes.count { !it.deleted })
    }

    @Test fun emptyAuthenticatedCheckpointGroupDeletesOnlyPreviouslyKnownIds() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("notes.json", "old")), setOf("notes.json"))
        val changes = state.apply(emptyList(), setOf("notes.json"))
        assertEquals(listOf("old"), changes.single().key)
        assertTrue(changes.single().deleted)
        assertTrue(state.changes.single().deleted)
        assertFalse(state.changes.any { it.key == listOf("phone-b-only") })
    }

    @Test fun omittedHistoricalOptionalGroupIsNotADeletion() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("notes.json", "n"), row("attachments.json", "pdf")), setOf("notes.json", "attachments.json"))
        state.apply(listOf(row("notes.json", "n")), setOf("notes.json"))
        assertFalse(state.changes.single { it.file == "attachments.json" }.deleted)
    }

    @Test fun checkpointRemovalsCannotBeResurrectedByLaterUnrelatedDeltas() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("folders.json", "f"), row("notes.json", "n")), setOf("folders.json", "notes.json"))
        state.apply(listOf(row("folders.json", "f")), setOf("folders.json", "notes.json"))
        state.apply(listOf(BackupRecordChange("folders.json", listOf("f"))))
        assertTrue(state.changes.all { it.deleted })
    }

    @Test fun laterExplicitUpsertCanReintroduceSameStableIdentity() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("notes.json", "n")), setOf("notes.json"))
        state.apply(emptyList(), setOf("notes.json"))
        state.apply(listOf(row("notes.json", "n")))
        assertFalse(state.changes.single().deleted)
    }

    @Test fun sameKeyInDifferentGroupsIsNotTheSameIdentity() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("notes.json", "id"), row("folders.json", "id")), setOf("notes.json", "folders.json"))
        state.apply(emptyList(), setOf("notes.json"))
        assertTrue(state.changes.single { it.file == "notes.json" }.deleted)
        assertFalse(state.changes.single { it.file == "folders.json" }.deleted)
    }

    @Test fun finalDanglingReferenceReportsFieldAndBothHistoryPositionsWithoutContents() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(row("folders.json", "f"), BackupRecordChange("attachments.json", listOf("pdf"),
            JSONObject().put("id", "pdf").put("libraryFolderId", "f").put("noteId", ""))), commitId = "root")
        state.apply(listOf(BackupRecordChange("folders.json", listOf("f"))), commitId = "deletion")
        val failure = backupReferenceViolations(state).single()
        assertEquals("libraryFolderId", failure.field)
        assertEquals("root", failure.dependentCommit)
        assertEquals("deletion", failure.dependencyCommit)
        assertTrue(failure.remoteDeleted)
        assertTrue(failure.diagnostic("restore_final_state_validation", "tip").contains("dependent=attachments.json:[pdf]"))
    }

    @Test fun intermediateMissingParentDoesNotInvalidateCompleteFinalState() {
        val state = BackupGraphRestoreState()
        state.apply(listOf(BackupRecordChange("notes.json", listOf("n"), JSONObject().put("id", "n").put("folderId", "f"))))
        assertEquals(1, backupReferenceViolations(state).size)
        state.apply(listOf(row("folders.json", "f")))
        requireBackupReferences(state, "tip", "test_final_state")
    }
}
