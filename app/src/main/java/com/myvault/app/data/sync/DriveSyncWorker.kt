package com.myvault.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
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
        val result = try {
            publishProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = operation.progressTitle()))
            when (operation) {
                OperationPull -> googleDriveSyncRepository.pullLatestFromDrive { publishProgress(it) }
                OperationForcePush -> googleDriveSyncRepository.pushToDrive(force = true) { publishProgress(it) }
                else -> googleDriveSyncRepository.pushToDrive { publishProgress(it) }
            }
        } catch (error: Throwable) {
            Log.e(Tag, "Google Drive ${operation.operationLabel()} crashed", error)
            DriveSyncResult.Failure("Google Drive ${operation.operationLabel()} failed: ${error.message ?: "Unknown error"}")
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
            -> Result.success(output)
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
        val operation = inputData.getString(KeyOperation) ?: OperationPush
        safeSetForeground(operation, progress)
    }

    private suspend fun safeSetForeground(operation: String, progress: DriveRestoreProgress) {
        runCatching {
            setForeground(createForegroundInfo(operation, progress))
        }.onFailure { error ->
            Log.e(Tag, "Unable to show Google Drive ${operation.operationLabel()} foreground notification", error)
        }
    }

    private fun createForegroundInfo(operation: String, progress: DriveRestoreProgress): ForegroundInfo {
        ensureNotificationChannel()
        val percent = progress.percent
        val notification = NotificationCompat.Builder(applicationContext, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (operation == OperationPull) "MyVault restore" else "MyVault backup")
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

    private fun String.operationLabel(): String =
        if (this == OperationPull) "restore" else "backup"

    companion object {
        const val UniqueWorkName = "myvault-google-drive-sync"
        const val Tag = "google-drive-sync"
        const val OperationPush = "push"
        const val OperationForcePush = "force_push"
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
