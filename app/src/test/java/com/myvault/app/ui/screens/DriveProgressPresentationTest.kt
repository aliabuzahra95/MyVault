package com.myvault.app.ui.screens

import com.myvault.app.data.sync.DriveRestoreProgress
import com.myvault.app.data.sync.DriveRestoreStage
import com.myvault.app.data.sync.DriveRestoreState
import com.myvault.app.data.sync.DriveSyncOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveProgressPresentationTest {
    @Test
    fun `measurable upload presents real object percentage`() {
        val presentation = DriveRestoreState(
            active = true,
            operation = DriveSyncOperation.Backup,
            progress = DriveRestoreProgress(
                stage = DriveRestoreStage.Uploading,
                message = "Uploading metadata notes.json",
                current = 48,
                total = 229,
            ),
        ).toDriveProgressPresentation()

        assertEquals("Uploading backup", presentation.title)
        assertEquals("Uploading backup metadata", presentation.detail)
        assertEquals(20, presentation.percent)
        assertEquals("48 of 229 items · 20%", presentation.itemProgress)
        assertFalse(presentation.failed)
    }

    @Test
    fun `unmeasurable preparation remains indeterminate`() {
        val presentation = DriveRestoreState(
            active = true,
            operation = DriveSyncOperation.Backup,
            progress = DriveRestoreProgress(
                stage = DriveRestoreStage.Preparing,
                message = "Preparing Google Drive backup",
            ),
        ).toDriveProgressPresentation()

        assertEquals("Preparing backup", presentation.title)
        assertNull(presentation.percent)
        assertNull(presentation.itemProgress)
    }

    @Test
    fun `confirmed backup completion presents success and immediate timestamp`() {
        val state = DriveRestoreState(
            operation = DriveSyncOperation.Backup,
            completedAt = 2_000L,
            progress = DriveRestoreProgress(stage = DriveRestoreStage.Complete),
        )
        val presentation = state.toDriveProgressPresentation()

        assertEquals("Backup complete", presentation.title)
        assertEquals(100, presentation.percent)
        assertEquals(2_000L, state.confirmedLastBackupAt(persistedAt = 1_000L))
    }

    @Test
    fun `failed backup retains previous successful timestamp`() {
        val state = DriveRestoreState(
            operation = DriveSyncOperation.Backup,
            completedAt = 9_000L,
            progress = DriveRestoreProgress(
                stage = DriveRestoreStage.Failed,
                message = "Drive push failed: network unavailable",
            ),
        )
        val presentation = state.toDriveProgressPresentation()

        assertTrue(presentation.failed)
        assertNull(presentation.percent)
        assertEquals(4_000L, state.confirmedLastBackupAt(persistedAt = 4_000L))
    }
}
