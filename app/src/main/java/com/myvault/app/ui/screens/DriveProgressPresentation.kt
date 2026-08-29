package com.myvault.app.ui.screens

import com.myvault.app.data.sync.DriveRestoreStage
import com.myvault.app.data.sync.DriveRestoreState
import com.myvault.app.data.sync.DriveSyncOperation

internal data class DriveProgressPresentation(
    val title: String,
    val detail: String,
    val percent: Int?,
    val itemProgress: String?,
    val failed: Boolean,
)

internal fun DriveRestoreState.toDriveProgressPresentation(): DriveProgressPresentation {
    val operationLabel = when (operation) {
        DriveSyncOperation.Backup -> "backup"
        DriveSyncOperation.Restore -> "restore"
        DriveSyncOperation.None -> "Google Drive operation"
    }
    val title = when (progress.stage) {
        DriveRestoreStage.Idle -> "Ready"
        DriveRestoreStage.Preparing -> "Preparing $operationLabel"
        DriveRestoreStage.Uploading -> "Uploading backup"
        DriveRestoreStage.Downloading -> "Downloading backup"
        DriveRestoreStage.Verifying -> "Verifying backup"
        DriveRestoreStage.RestoringDatabase -> "Restoring vault data"
        DriveRestoreStage.RestoringFiles -> "Restoring files"
        DriveRestoreStage.Finalising -> "Finalising $operationLabel"
        DriveRestoreStage.Complete -> if (operation == DriveSyncOperation.Backup) "Backup complete" else "Restore complete"
        DriveRestoreStage.Failed -> if (operation == DriveSyncOperation.Backup) "Backup failed" else "Restore failed"
    }
    val detail = when (progress.stage) {
        DriveRestoreStage.Preparing -> when {
            progress.message.contains("export", ignoreCase = true) -> "Preparing backup metadata"
            operation == DriveSyncOperation.Backup -> "Checking Google Drive and preparing your backup"
            operation == DriveSyncOperation.Restore -> "Checking Google Drive and preparing your restore"
            else -> progress.message.ifBlank { "Preparing Google Drive" }
        }
        DriveRestoreStage.Uploading -> when {
            progress.message.contains("metadata", ignoreCase = true) -> "Uploading backup metadata"
            progress.message.contains("file", ignoreCase = true) -> "Uploading attachments and files"
            else -> "Uploading changed vault items"
        }
        DriveRestoreStage.Finalising -> if (operation == DriveSyncOperation.Backup) {
            "Saving the final backup manifest"
        } else {
            "Finishing the restore"
        }
        DriveRestoreStage.Complete -> if (operation == DriveSyncOperation.Backup) {
            "Your Google Drive backup finished successfully"
        } else {
            "Your Google Drive restore finished successfully"
        }
        DriveRestoreStage.Failed -> progress.message.ifBlank { "The Google Drive operation did not complete" }
        else -> progress.message.ifBlank { title }
    }
    val percent = when {
        progress.stage == DriveRestoreStage.Complete -> 100
        progress.stage == DriveRestoreStage.Failed -> null
        else -> progress.percent
    }
    val itemProgress = if (progress.total > 0 && progress.stage != DriveRestoreStage.Complete) {
        buildString {
            append(progress.current.coerceIn(0, progress.total))
            append(" of ")
            append(progress.total)
            append(" items")
            percent?.let {
                append(" · ")
                append(it)
                append('%')
            }
        }
    } else {
        null
    }
    return DriveProgressPresentation(
        title = title.replaceFirstChar { it.uppercase() },
        detail = detail,
        percent = percent,
        itemProgress = itemProgress,
        failed = progress.stage == DriveRestoreStage.Failed,
    )
}

internal fun DriveRestoreState.confirmedLastBackupAt(persistedAt: Long): Long =
    if (
        operation == DriveSyncOperation.Backup &&
        progress.stage == DriveRestoreStage.Complete &&
        completedAt > persistedAt
    ) {
        completedAt
    } else {
        persistedAt
    }
