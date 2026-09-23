package com.myvault.app.ui.screens

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfCompanionPaneTest {
    @Test
    fun companionPaneOnlyAppearsWhenWindowIsWideEnough() {
        assertFalse(supportsPdfCompanionPane(412))
        assertFalse(supportsPdfCompanionPane(699))
        assertTrue(supportsPdfCompanionPane(700))
        assertTrue(supportsPdfCompanionPane(840))
    }

    @Test
    fun companionPdfListExcludesCurrentMissingAndNonPdfFiles() {
        val firstPdf = File.createTempFile("myvault-first", ".pdf")
        val secondPdf = File.createTempFile("myvault-second", ".pdf")
        val textFile = File.createTempFile("myvault-note", ".txt")
        try {
            val attachments = listOf(
                attachment("current", "Current.pdf", "application/pdf", firstPdf.absolutePath),
                attachment("second", "Second.pdf", "application/pdf", secondPdf.absolutePath),
                attachment("text", "Notes.txt", "text/plain", textFile.absolutePath),
                attachment("missing", "Missing.pdf", "application/pdf", "/missing/myvault.pdf"),
            )

            assertEquals(
                listOf("second"),
                eligibleCompanionPdfs(attachments, "current").map(AttachmentEntity::id),
            )
        } finally {
            firstPdf.delete()
            secondPdf.delete()
            textFile.delete()
        }
    }

    @Test
    fun companionStudySearchMatchesTitleAndBody() {
        val notes = listOf(
            note("one", "Hadith principles", "Evidence and commentary"),
            note("two", "Arabic vocabulary", "Root notes"),
        )

        assertEquals(listOf("one"), matchingCompanionNotes(notes, "hadith").map(NoteEntity::id))
        assertEquals(listOf("two"), matchingCompanionNotes(notes, "root").map(NoteEntity::id))
        assertEquals(notes, matchingCompanionNotes(notes, ""))
    }

    @Test
    fun clipsTargetOnlyTheNoteOpenInTheCompanionPane() {
        assertEquals("note-1", activeCompanionClipNoteId(PdfCompanionMode.Note, "note-1"))
        assertEquals(null, activeCompanionClipNoteId(PdfCompanionMode.Pdf, "note-1"))
        assertEquals(null, activeCompanionClipNoteId(PdfCompanionMode.None, "note-1"))
        assertEquals(null, activeCompanionClipNoteId(PdfCompanionMode.Note, null))
    }

    private fun attachment(id: String, name: String, mimeType: String, path: String) = AttachmentEntity(
        id = id,
        noteId = "note-$id",
        fileName = name,
        mimeType = mimeType,
        sizeBytes = 1L,
        localPath = path,
        remoteUrl = null,
        createdAt = 1L,
    )

    private fun note(id: String, title: String, body: String) = NoteEntity(
        id = id,
        folderId = null,
        title = title,
        bodyPlainText = body,
        isPinned = false,
        isFavourite = false,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
