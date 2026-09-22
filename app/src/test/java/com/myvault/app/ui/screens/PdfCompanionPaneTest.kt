package com.myvault.app.ui.screens

import com.myvault.app.data.local.entity.AttachmentEntity
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
}
