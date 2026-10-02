package com.myvault.app.data.local.entity

import androidx.room.Entity

/** Restore position only. Never used as a publication parent or journal acknowledgement. */
@Entity(tableName = "backup_graph_applied_states", primaryKeys = ["accountScope", "lineageId"])
data class BackupGraphAppliedState(
    val accountScope: String, val lineageId: String, val driveAccountId: String,
    val commitId: String, val commitFileId: String, val commitSha256: String, val commitSize: Long,
    val checkpointId: String, val checkpointFileId: String, val checkpointSha256: String, val checkpointSize: Long,
    val deltaHeadId: String, val originEpoch: Long,
)

@Entity(tableName = "backup_graph_restores", primaryKeys = ["accountScope", "operationId"])
data class BackupGraphRestore(
    val accountScope: String, val operationId: String, val lineageId: String, val driveAccountId: String,
    val originalAppliedJson: String?, val capturedGeneration: Long, val capturedOriginEpoch: Long,
    val commitJson: String, val commitFileId: String, val commitSha256: String, val commitSize: Long,
    val frozenChangesJson: String, val frozenChangesSha256: String,
    val settingsBeforeJson: String?, val settingsPhase: String, val status: String,
)

@Entity(tableName = "backup_graph_restore_objects", primaryKeys = ["accountScope", "operationId", "attachmentId"])
data class BackupGraphRestoreObject(
    val accountScope: String, val operationId: String, val attachmentId: String,
    val cloudFileId: String, val sha256: String, val byteCount: Long,
    val stagingPath: String, val destinationPath: String, val status: String,
)

data class BackupGraphRestoreMetadata(
    val accountScope: String, val operationId: String, val lineageId: String, val driveAccountId: String,
    val originalAppliedJson: String?, val capturedGeneration: Long, val capturedOriginEpoch: Long,
    val commitJson: String, val commitFileId: String, val commitSha256: String, val commitSize: Long,
    val frozenChangesSha256: String,
    val settingsBeforeJson: String?, val settingsPhase: String, val status: String,
)
