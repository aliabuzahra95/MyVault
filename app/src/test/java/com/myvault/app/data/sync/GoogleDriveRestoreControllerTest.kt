package com.myvault.app.data.sync

import androidx.work.Data
import androidx.work.WorkInfo
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveRestoreControllerTest {
    @Test
    fun `tracked work wins over stale historical records`() {
        val stale = workInfo(state = WorkInfo.State.SUCCEEDED)
        val current = workInfo(state = WorkInfo.State.RUNNING)

        assertSame(current, selectDriveWorkInfo(listOf(stale, current), current.id))
    }

    @Test
    fun `active work is recovered when controller has no tracked id`() {
        val stale = workInfo(state = WorkInfo.State.SUCCEEDED)
        val active = workInfo(state = WorkInfo.State.RUNNING)

        assertSame(active, selectDriveWorkInfo(listOf(stale, active), null))
        assertNull(selectDriveWorkInfo(listOf(stale), null))
    }

    @Test
    fun `worker progress restores operation counts and percentage`() {
        val info = workInfo(
            state = WorkInfo.State.RUNNING,
            progress = Data.Builder()
                .putString(DriveSyncWorker.KeyStage, DriveRestoreStage.Uploading.name)
                .putString(DriveSyncWorker.KeyMessage, "Uploading metadata notes.json")
                .putString(DriveSyncWorker.KeyOperation, DriveSyncWorker.OperationPush)
                .putInt(DriveSyncWorker.KeyCurrent, 4)
                .putInt(DriveSyncWorker.KeyTotal, 10)
                .build(),
        )

        val state = info.toDriveRestoreState()

        assertTrue(state.active)
        assertEquals(DriveSyncOperation.Backup, state.operation)
        assertEquals(4, state.progress.current)
        assertEquals(10, state.progress.total)
        assertEquals(40, state.progress.percent)
    }

    @Test
    fun `successful worker output restores completion timestamp`() {
        val completedAt = 1_788_000_000_000L
        val info = workInfo(
            state = WorkInfo.State.SUCCEEDED,
            output = Data.Builder()
                .putString(DriveSyncWorker.KeyStage, DriveRestoreStage.Complete.name)
                .putString(DriveSyncWorker.KeyMessage, "Drive push complete")
                .putString(DriveSyncWorker.KeyOperation, DriveSyncWorker.OperationPush)
                .putLong(DriveSyncWorker.KeyCompletedAt, completedAt)
                .putInt(DriveSyncWorker.KeyCurrent, 10)
                .putInt(DriveSyncWorker.KeyTotal, 10)
                .build(),
        )

        val state = info.toDriveRestoreState()

        assertFalse(state.active)
        assertEquals(DriveRestoreStage.Complete, state.progress.stage)
        assertEquals(completedAt, state.completedAt)
    }

    @Test
    fun `active state blocks duplicate operation`() {
        assertFalse(canStartDriveOperation(DriveRestoreState(active = true)))
        assertTrue(canStartDriveOperation(DriveRestoreState(active = false)))
    }

    private fun workInfo(
        state: WorkInfo.State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): WorkInfo = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        outputData = output,
        progress = progress,
    )
}
