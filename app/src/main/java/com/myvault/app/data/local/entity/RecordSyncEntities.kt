package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "record_sync_control")
data class RecordSyncControlEntity(
    @PrimaryKey val id: Int = 1,
    val accountId: String? = null,
    val clientId: String = "",
    val enabled: Boolean = false,
    val paused: Boolean = true,
    val applyingRemote: Boolean = false,
    val cursor: String? = null,
)

@Entity(tableName = "record_sync_pending", primaryKeys = ["accountId", "entityType", "entityId"])
data class RecordSyncPendingEntity(
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val generation: Long,
    val changedAt: Long,
    val baseRevisionId: String?,
    val preparedGeneration: Long? = null,
    val preparedRevisionJson: String? = null,
    val excludedReason: String? = null,
)

@Entity(tableName = "record_sync_heads", primaryKeys = ["accountId", "entityType", "entityId"])
data class RecordSyncHeadEntity(
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val revisionId: String,
    val contentHash: String,
    val deleted: Boolean,
)

@Entity(tableName = "record_sync_files", indices = [Index(value = ["accountId", "entityType", "entityId"])])
data class RecordSyncFileEntity(
    @PrimaryKey val fileId: String,
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val revisionId: String,
    val mutationId: String,
)

@Entity(tableName = "record_sync_conflicts", indices = [Index(value = ["accountId", "entityType", "entityId"])])
data class RecordSyncConflictEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val localRevisionId: String?,
    val remoteRevisionId: String,
    val remotePayloadJson: String?,
    val remoteDeleted: Boolean,
    val conflictCopyId: String?,
    val createdAt: Long,
    val resolvedAt: Long? = null,
)
