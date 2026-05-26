package com.myvault.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myvault.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val googleDriveSyncRepository: GoogleDriveIncrementalSyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operation = inputData.getString(KeyOperation) ?: OperationPush
        setForeground(createForegroundInfo(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = operation.progressTitle())))
        publishProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = operation.progressTitle()))

        val result = when (operation) {
            OperationPull -> googleDriveSyncRepository.pullLatestFromDrive(::publishProgress)
            else -> googleDriveSyncRepository.pushToDrive(::publishProgress)
        }
        val finalStage = when (result) {
            is DriveSyncResult.Success -> DriveRestoreStage.Complete
            else -> DriveRestoreStage.Failed
        }
        val output = outputData(finalStage, result.displayMessage())
        return when (result) {
            is DriveSyncResult.Success -> Result.success(output)
            is DriveSyncResult.Conflict,
            is DriveSyncResult.Skipped,
            is DriveSyncResult.Failure,
            -> Result.failure(output)
        }
    }

    private suspend fun publishProgress(progress: DriveRestoreProgress) {
        setProgress(
            workDataOf(
                KeyStage to progress.stage.name,
                KeyMessage to progress.message,
                KeyCurrent to progress.current,
                KeyTotal to progress.total,
            ),
        )
        setForeground(createForegroundInfo(progress))
    }

    private fun createForegroundInfo(progress: DriveRestoreProgress): ForegroundInfo {
        ensureNotificationChannel()
        val percent = progress.percent
        val notification = NotificationCompat.Builder(applicationContext, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (progress.stage == DriveRestoreStage.Uploading) "MyVault backup" else "MyVault restore")
            .setContentText(progress.message.ifBlank { progress.stage.label })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progress.total > 0) {
                    setProgress(100, percent ?: 0, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(ChannelId, "MyVault Drive sync", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows Google Drive backup and restore progress."
        }
        manager.createNotificationChannel(channel)
    }

    private fun outputData(stage: DriveRestoreStage, message: String): Data =
        workDataOf(KeyStage to stage.name, KeyMessage to message)

    private fun String.progressTitle(): String =
        if (this == OperationPull) "Starting Google Drive restore" else "Starting Google Drive backup"

    companion object {
        const val UniqueWorkName = "myvault-google-drive-sync"
        const val Tag = "google-drive-sync"
        const val OperationPush = "push"
        const val OperationPull = "pull"
        const val KeyOperation = "operation"
        const val KeyStage = "stage"
        const val KeyMessage = "message"
        const val KeyCurrent = "current"
        const val KeyTotal = "total"
        private const val ChannelId = "myvault_drive_sync"
        private const val NotificationId = 2407
    }
}
