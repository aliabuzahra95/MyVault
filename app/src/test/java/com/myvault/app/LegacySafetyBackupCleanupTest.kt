package com.myvault.app

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySafetyBackupCleanupTest {
    @Test
    fun removesOnlyLegacySafetyBackupDirectory() {
        val filesDir = Files.createTempDirectory("myvault-files").toFile()
        try {
            val safetyDir = filesDir.resolve("emergency_backups").apply { mkdirs() }
            safetyDir.resolve("before-delete.vaultbackup").writeText("archive")
            val attachmentDir = filesDir.resolve("attachments/note-1").apply { mkdirs() }
            val attachment = attachmentDir.resolve("document.pdf").apply { writeText("pdf") }

            removeLegacySafetyBackups(filesDir)

            assertFalse(safetyDir.exists())
            assertTrue(attachment.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
