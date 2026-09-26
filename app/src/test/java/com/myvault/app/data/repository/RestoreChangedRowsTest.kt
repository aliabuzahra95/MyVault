package com.myvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreChangedRowsTest {
    private data class Row(val id: String, val content: String, val version: Int = 0, val folderId: String? = null)

    @Test fun identicalRowsAreSkippedWhileChangedAndMissingRowsAreApplied() {
        val current = (1..1_000).map { Row("note-$it", "body-$it") } + Row("extra-local", "keep")
        val incoming = (1..995).map { Row("note-$it", "body-$it") } +
            (996..999).map { Row("note-$it", "changed-$it") } + Row("new-note", "new")

        val changed = changedRestoreRows(incoming, current) { it.id }
        assertEquals(995, incoming.size - changed.size)
        assertEquals(setOf("note-996", "note-997", "note-998", "note-999", "new-note"), changed.map { it.id }.toSet())

        val oldResult = current.associateBy { it.id }.toMutableMap().apply { incoming.forEach { put(it.id, it) } }
        val optimizedResult = current.associateBy { it.id }.toMutableMap().apply { changed.forEach { put(it.id, it) } }
        assertEquals(oldResult, optimizedResult)
        assertEquals(Row("extra-local", "keep"), optimizedResult["extra-local"])
    }

    @Test fun comparisonUsesFullContentRatherThanTimestampsAlone() {
        val current = listOf(Row("same-id", "old body", 5))
        val incoming = listOf(Row("same-id", "new body", 5))
        assertEquals(incoming, changedRestoreRows(incoming, current) { it.id })
        assertEquals(emptyList<Row>(), changedRestoreRows(current, current) { it.id })
    }

    @Test fun duplicateIdsRetainExistingLastRowWinsBehavior() {
        val current = listOf(Row("n", "first"))
        val incoming = listOf(Row("n", "second"), Row("n", "first"))
        assertEquals(incoming, changedRestoreRows(incoming, current) { it.id })
    }

    @Test fun changedFolderRelationshipIsNotSkipped() {
        val current = listOf(Row("n", "same text", folderId = "old-folder"))
        val incoming = listOf(Row("n", "same text", folderId = "new-folder"))
        assertEquals(incoming, changedRestoreRows(incoming, current) { it.id })
    }
}
