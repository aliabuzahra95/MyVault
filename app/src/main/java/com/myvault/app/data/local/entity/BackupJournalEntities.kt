package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_journal_state")
data class BackupJournalState(
    @PrimaryKey val id: Int = 1,
    val generation: Long = 0,
    val originEpoch: Long = 0,
    val suppressionDepth: Int = 0,
    val settingsToken: String? = null,
)

@Entity(tableName = "backup_tracking_accounts")
data class BackupTrackingAccount(
    @PrimaryKey val accountScope: String,
    val trusted: Boolean = false,
    val checkpointId: String? = null,
    val headId: String? = null,
    val manifestId: String? = null,
    val manifestSha256: String? = null,
    val reason: String = "baseline_required",
)

@Entity(tableName = "backup_pending_changes", primaryKeys = ["accountScope", "recordGroup", "key0", "key1", "key2"])
data class BackupPendingChange(
    val accountScope: String,
    val recordGroup: String,
    val key0: String,
    val key1: String = "",
    val key2: String = "",
    val operation: String,
    val generation: Long,
)
