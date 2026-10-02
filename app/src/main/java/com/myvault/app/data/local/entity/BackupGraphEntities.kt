package com.myvault.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "backup_graph_bindings", primaryKeys = ["accountScope", "lineageId"])
data class BackupGraphBinding(
    val accountScope: String,
    val lineageId: String,
    val driveAccountId: String,
    val commitId: String,
    val commitFileId: String,
    val commitSha256: String,
    val commitSize: Long,
    val checkpointId: String,
    val checkpointFileId: String,
    val checkpointSha256: String,
    val checkpointSize: Long,
    val deltaHeadId: String,
    val originEpoch: Long,
)

/** Exact frozen intent, not a recipe that re-reads user rows on retry. */
@Entity(tableName = "backup_graph_publications", primaryKeys = ["accountScope", "operationId"])
data class BackupGraphPublication(
    val accountScope: String,
    val operationId: String,
    val lineageId: String,
    val driveAccountId: String,
    val capturedGeneration: Long,
    val capturedOriginEpoch: Long,
    val originalAccountJson: String,
    val originalBindingJson: String?,
    val frozenBatchJson: String,
    val commitJson: String,
    val status: String,
)

@Entity(tableName = "backup_graph_publication_objects", primaryKeys = ["accountScope", "operationId", "objectId"])
data class BackupGraphPublicationObject(
    val accountScope: String,
    val operationId: String,
    val objectId: String,
    val ordinal: Int,
    val role: String,
    val attachmentId: String?,
    val stagedPath: String,
    val sha256: String,
    val byteCount: Long,
    val verifiedSha256: String?,
    val verifiedByteCount: Long?,
)

data class BackupGraphPublicationMetadata(
    val accountScope: String,
    val operationId: String,
    val lineageId: String,
    val driveAccountId: String,
    val capturedGeneration: Long,
    val capturedOriginEpoch: Long,
    val originalAccountJson: String,
    val originalBindingJson: String?,
    val commitJson: String,
    val status: String,
)
