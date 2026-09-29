package com.myvault.app.data.repository

import com.myvault.app.data.sync.DriveRestoreStage
import org.junit.Assert.*
import org.junit.Test

class BackupGraphProgressTest {
    @Test fun unknownPreparationAndCompletionRemainUnmeasured() {
        for (stage in listOf(GraphBackupStage.CHECKING, GraphBackupStage.READING_BASELINE,
            GraphBackupStage.CAPTURING_CHANGES, GraphBackupStage.COMPLETING)) {
            val progress = GraphBackupProgress(stage).toDriveProgress()
            assertNull(progress.percent)
            assertNotEquals(DriveRestoreStage.Complete, progress.stage)
            assertEquals(progress.message, progress.detail)
        }
    }

    @Test fun objectCountsDescribeOnlyTheCurrentStage() {
        val progress = GraphBackupProgress(GraphBackupStage.VERIFYING, 2, 4).toDriveProgress()
        assertEquals(50, progress.percent)
        assertEquals(DriveRestoreStage.Verifying, progress.stage)
        val finalVerification = GraphBackupProgress(GraphBackupStage.VERIFYING, 4, 4).toDriveProgress()
        assertEquals(100, finalVerification.percent)
        assertNotEquals(DriveRestoreStage.Complete, finalVerification.stage)
    }

    @Test fun zeroChangeHasDistinctSuccessfulStatus() {
        val progress = GraphBackupProgress(GraphBackupStage.ALREADY_BACKED_UP).toDriveProgress()
        assertEquals(DriveRestoreStage.Complete, progress.stage)
        assertEquals("Already backed up. No changes to upload.", progress.detail)
    }

    @Test fun invalidCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GraphBackupProgress(GraphBackupStage.UPLOADING, 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { GraphBackupProgress(GraphBackupStage.UPLOADING, -1, 1) }
    }
}
