package com.myvault.app.data.sync

import com.myvault.app.data.repository.BackupGraphReadinessState
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount

enum class LatestBackupNoticeKind { NEWER, LOCAL_CHANGES, BLOCKED }

data class LatestBackupNotice(
    val kind: LatestBackupNoticeKind,
    val remoteCommitId: String?,
    val message: String,
    val accountEmail: String? = null,
    val lineageId: String? = null,
)

enum class BackupFreshness { UP_TO_DATE, NEWER, LOCAL_CHANGES, LOCAL_PENDING, NO_REMOTE_BACKUP, CHECK_FAILED }

data class BackupFreshnessCheck(
    val freshness: BackupFreshness,
    val accountEmail: String,
    val lineageId: String? = null,
    val remoteCommitId: String? = null,
    val notice: LatestBackupNotice? = null,
)

enum class PassiveBackupPresentation { QUIET, LATEST_BANNER, PROMPT }

data class PassiveBackupCheck(
    val presentation: PassiveBackupPresentation,
    val notice: LatestBackupNotice? = null,
    val accountEmail: String? = null,
)

internal fun backupFreshness(state: BackupGraphReadinessState): BackupFreshness = when (state) {
    BackupGraphReadinessState.CURRENT -> BackupFreshness.UP_TO_DATE
    BackupGraphReadinessState.LOCAL_PENDING -> BackupFreshness.LOCAL_PENDING
    BackupGraphReadinessState.RESTORE_REQUIRED -> BackupFreshness.NEWER
    BackupGraphReadinessState.LOCAL_REMOTE_CHANGES -> BackupFreshness.LOCAL_CHANGES
    BackupGraphReadinessState.FIRST_BASELINE_REQUIRED,
    BackupGraphReadinessState.LEGACY_BASELINE_REQUIRED -> BackupFreshness.NO_REMOTE_BACKUP
    else -> BackupFreshness.CHECK_FAILED
}

/** Passive errors stay quiet; manual readiness/Restore retains its detailed safety UI. */
internal class PassiveBackupCheckPolicy {
    private data class State(var nextCheckMs: Long = 0, var failures: Int = 0)
    private val accounts = mutableMapOf<String, State>()
    private val bannerTimes = mutableMapOf<String, Long>()

    fun begin(account: String, nowMs: Long): Boolean {
        val state = accounts.getOrPut(normalizeGoogleDriveAccount(account)) { State() }
        if (nowMs < state.nextCheckMs) return false
        state.nextCheckMs = nowMs + CheckIntervalMs
        return true
    }

    fun complete(result: BackupFreshnessCheck, nowMs: Long, lastNotifiedTip: String?): PassiveBackupCheck {
        val account = normalizeGoogleDriveAccount(result.accountEmail)
        val state = accounts.getOrPut(account) { State() }
        if (result.freshness == BackupFreshness.CHECK_FAILED) {
            state.failures = (state.failures + 1).coerceAtMost(4)
            state.nextCheckMs = nowMs + (FailureRetryMs * (1L shl (state.failures - 1))).coerceAtMost(MaxFailureRetryMs)
            return PassiveBackupCheck(PassiveBackupPresentation.QUIET)
        }
        state.failures = 0
        state.nextCheckMs = nowMs + CheckIntervalMs
        val lineage = result.lineageId ?: return PassiveBackupCheck(PassiveBackupPresentation.QUIET)
        val tip = result.remoteCommitId ?: return PassiveBackupCheck(PassiveBackupPresentation.QUIET)
        return when (result.freshness) {
            BackupFreshness.NEWER, BackupFreshness.LOCAL_CHANGES -> {
                if (tip == lastNotifiedTip || result.notice == null) PassiveBackupCheck(PassiveBackupPresentation.QUIET)
                else PassiveBackupCheck(PassiveBackupPresentation.PROMPT, result.notice)
            }
            BackupFreshness.UP_TO_DATE -> {
                val key = "${latestBackupNoticeStorageKey(account, lineage)}:$tip"
                val lastBanner = bannerTimes[key]
                if (lastBanner != null && nowMs - lastBanner < BannerIntervalMs) PassiveBackupCheck(PassiveBackupPresentation.QUIET)
                else {
                    bannerTimes[key] = nowMs
                    PassiveBackupCheck(PassiveBackupPresentation.LATEST_BANNER)
                }
            }
            else -> PassiveBackupCheck(PassiveBackupPresentation.QUIET)
        }.copy(accountEmail = account)
    }

    companion object {
        const val CheckIntervalMs = 15 * 60_000L
        const val BannerIntervalMs = 60 * 60_000L
        const val FailureRetryMs = 5 * 60_000L
        const val MaxFailureRetryMs = 30 * 60_000L
    }
}

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
            LatestBackupNoticeKind.NEWER,
            remoteTip,
            "A newer backup is available. Restore replaces local edits to backed-up items and keeps new local items.",
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
