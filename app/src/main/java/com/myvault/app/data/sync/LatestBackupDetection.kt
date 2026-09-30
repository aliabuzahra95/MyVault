package com.myvault.app.data.sync

import com.myvault.app.data.repository.BackupGraphReadinessState
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount

enum class LatestBackupNoticeKind { NEWER, LOCAL_CHANGES, BLOCKED }

data class LatestBackupNotice(
    val kind: LatestBackupNoticeKind,
    val remoteCommitId: String?,
    val message: String,
)

internal fun latestBackupNoticeStorageKey(accountEmail: String, lineageId: String): String {
    val account = normalizeGoogleDriveAccount(accountEmail)
    require('@' in account && lineageId.isNotBlank())
    return "latest_backup_notice:$account:$lineageId"
}

internal fun latestBackupNotice(
    state: BackupGraphReadinessState,
    remoteTip: String?,
    lastNotifiedRemoteCommitId: String?,
): LatestBackupNotice? = when (state) {
    BackupGraphReadinessState.RESTORE_REQUIRED -> {
        if (remoteTip == null || remoteTip == lastNotifiedRemoteCommitId) null
        else LatestBackupNotice(
            LatestBackupNoticeKind.NEWER,
            remoteTip,
            "A newer MyVault backup is available from Google Drive.",
        )
    }
    BackupGraphReadinessState.LOCAL_REMOTE_CHANGES -> {
        if (remoteTip == null || remoteTip == lastNotifiedRemoteCommitId) null
        else LatestBackupNotice(
            LatestBackupNoticeKind.LOCAL_CHANGES,
            remoteTip,
            "A newer backup is available, but this device has local changes that must be backed up or resolved first.",
        )
    }
    BackupGraphReadinessState.FORK -> LatestBackupNotice(
        LatestBackupNoticeKind.BLOCKED,
        null,
        "Fork detected. MyVault cannot safely choose a backup branch.",
    )
    BackupGraphReadinessState.DIVERGENT,
    BackupGraphReadinessState.ADOPTION_REQUIRED -> LatestBackupNotice(
        LatestBackupNoticeKind.BLOCKED,
        null,
        "Local and Drive backup histories require reconciliation before Restore.",
    )
    BackupGraphReadinessState.UNSUPPORTED -> LatestBackupNotice(
        LatestBackupNoticeKind.BLOCKED,
        null,
        "This backup requires a newer MyVault backup reader.",
    )
    BackupGraphReadinessState.CORRUPT,
    BackupGraphReadinessState.MISSING_ANCESTRY,
    BackupGraphReadinessState.ACCOUNT_MISMATCH,
    BackupGraphReadinessState.AMBIGUOUS_NAMESPACE,
    BackupGraphReadinessState.MISSING_NAMESPACE,
    BackupGraphReadinessState.RECOVERY_REQUIRED -> LatestBackupNotice(
        LatestBackupNoticeKind.BLOCKED,
        null,
        "The Drive backup could not be verified safely. Nothing was restored.",
    )
    else -> null
}
