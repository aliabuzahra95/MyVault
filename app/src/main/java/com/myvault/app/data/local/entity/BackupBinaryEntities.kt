package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local byte identity is shared; permission to reuse a remote object is account-specific. */
@Entity(tableName = "backup_binary_fingerprints")
data class BackupBinaryFingerprint(
    @PrimaryKey val attachmentId: String,
    val localPath: String,
    val byteSize: Long,
    val sha256: String?,
    val status: String,
    val generation: Long,
)

@Entity(tableName = "backup_binary_references", primaryKeys = ["accountScope", "attachmentId"])
data class BackupBinaryReference(
    val accountScope: String,
    val attachmentId: String,
    val checkpointId: String,
    val headId: String,
    val cloudFileId: String,
    val byteSize: Long,
    val sha256: String,
)
