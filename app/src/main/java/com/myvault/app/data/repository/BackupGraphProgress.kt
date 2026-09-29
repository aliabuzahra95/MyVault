package com.myvault.app.data.repository

import com.myvault.app.data.sync.DriveRestoreProgress
import com.myvault.app.data.sync.DriveRestoreStage

internal enum class GraphBackupStage {
    CHECKING, READING_BASELINE, STAGING_METADATA, STAGING_FILES, CAPTURING_CHANGES,
    STAGING_PUBLICATION, UPLOADING, VERIFYING, VERIFYING_BASELINE, COMPLETING, COMPLETE, ALREADY_BACKED_UP,
}

/** Counts describe the current stage, not an estimated fraction of the entire operation. */
internal data class GraphBackupProgress(
    val stage: GraphBackupStage,
    val current: Int = 0,
    val total: Int = 0,
) {
    init { require(current >= 0 && total >= 0 && current <= total) }

    fun toDriveProgress(): DriveRestoreProgress {
        val driveStage = when (stage) {
            GraphBackupStage.UPLOADING -> DriveRestoreStage.Uploading
            GraphBackupStage.VERIFYING, GraphBackupStage.VERIFYING_BASELINE -> DriveRestoreStage.Verifying
            GraphBackupStage.COMPLETING -> DriveRestoreStage.Finalising
            GraphBackupStage.COMPLETE, GraphBackupStage.ALREADY_BACKED_UP -> DriveRestoreStage.Complete
            else -> DriveRestoreStage.Preparing
        }
        val detail = when (stage) {
            GraphBackupStage.CHECKING -> "Checking Google Drive and backup history"
            GraphBackupStage.READING_BASELINE -> "Reading current Vault data for the first backup"
            GraphBackupStage.STAGING_METADATA -> "Preparing initial backup metadata"
            GraphBackupStage.STAGING_FILES -> "Preparing and checking attachment files"
            GraphBackupStage.CAPTURING_CHANGES -> "Reading pending backup changes"
            GraphBackupStage.STAGING_PUBLICATION -> "Preparing immutable backup objects"
            GraphBackupStage.UPLOADING -> "Uploading backup objects"
            GraphBackupStage.VERIFYING -> "Verifying exact uploaded bytes"
            GraphBackupStage.VERIFYING_BASELINE -> "Verifying the complete initial checkpoint"
            GraphBackupStage.COMPLETING -> "Saving the verified backup position"
            GraphBackupStage.COMPLETE -> "Your Google Drive backup finished successfully"
            GraphBackupStage.ALREADY_BACKED_UP -> "Already backed up. No changes to upload."
        }
        return DriveRestoreProgress(driveStage, detail, current, total, detail)
    }
}
