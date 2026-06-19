package com.myvault.app.data.repository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupAttachmentResilienceTest {
    @Test
    fun missingFileIsNotClaimedByNewBackupMetadata() {
        val missingFile = File(
            System.getProperty("java.io.tmpdir"),
            "myvault-missing-${System.nanoTime()}.pdf",
        )

        assertEquals("", backupFileEntryIfAvailable("attachment-id", missingFile.absolutePath))
    }

    @Test
    fun existingFileIsClaimedByNewBackupMetadata() {
        val file = File.createTempFile("myvault-existing-", ".pdf")
        try {
            assertEquals("files/attachment-id", backupFileEntryIfAvailable("attachment-id", file.absolutePath))
        } finally {
            file.delete()
        }
    }

    @Test
    fun oldBackupWithMissingFileRestoresOtherData() {
        val missing = missingClaimedAttachmentFileIds(
            claimedFileIds = setOf("available", "missing"),
            restoredFileIds = setOf("available"),
        )

        assertEquals(setOf("missing"), missing)
    }

    @Test
    fun restoreSkipsAttachmentsThatWereMissingWhenBackupWasCreated() {
        val unavailable = unavailableAttachmentFileIds(
            attachments = listOf(
                AttachmentFileRestoreMetadata("available", "files/available", hasFileEntryField = true),
                AttachmentFileRestoreMetadata("missing-at-backup", "", hasFileEntryField = true),
            ),
            restoredFileIds = setOf("available"),
        )

        assertEquals(setOf("missing-at-backup"), unavailable)
    }

    @Test
    fun restoreSkipsClaimedAttachmentsMissingFromZip() {
        val unavailable = unavailableAttachmentFileIds(
            attachments = listOf(
                AttachmentFileRestoreMetadata("available", "files/available", hasFileEntryField = true),
                AttachmentFileRestoreMetadata("missing-from-zip", "files/missing-from-zip", hasFileEntryField = true),
            ),
            restoredFileIds = setOf("available"),
        )

        assertEquals(setOf("missing-from-zip"), unavailable)
    }
}
