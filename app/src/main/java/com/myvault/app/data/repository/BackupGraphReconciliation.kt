package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.BackupJournalSchema
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount

internal enum class BackupGraphReadinessState {
    FIRST_BASELINE_REQUIRED, LEGACY_BASELINE_REQUIRED, CURRENT, LOCAL_PENDING,
    RESTORE_REQUIRED, LOCAL_REMOTE_CHANGES, ADOPTION_REQUIRED, FORK, DIVERGENT,
    UNSUPPORTED, CORRUPT, MISSING_ANCESTRY, ACCOUNT_MISMATCH, AMBIGUOUS_NAMESPACE,
    MISSING_NAMESPACE, RECOVERY_REQUIRED,
}

internal data class LocalBackupReadiness(
    val pendingCount: Int, val hasData: Boolean, val position: String?,
    val expectedReference: GraphObjectRef?, val lineage: String?, val accountId: String?,
    val proofValid: Boolean, val recoveryRequired: Boolean,
)

internal data class BackupGraphReadiness(val state: BackupGraphReadinessState, val local: LocalBackupReadiness, val legacyVisible: Boolean) {
    // Discovery alone cannot prove production cross-client visibility or establish a baseline.
    val publicationAllowed: Boolean get() = false
    fun message(): String {
        val explanation = when (state) {
            BackupGraphReadinessState.FIRST_BASELINE_REQUIRED -> "No graph backup is visible. A verified first full baseline is required."
            BackupGraphReadinessState.LEGACY_BASELINE_REQUIRED -> "Your legacy backup is visible. A verified graph baseline is still required; the legacy backup must be preserved."
            BackupGraphReadinessState.CURRENT -> "The verified local graph position is current."
            BackupGraphReadinessState.LOCAL_PENDING -> "Local changes are pending at the current graph position."
            BackupGraphReadinessState.RESTORE_REQUIRED -> "A newer graph backup is visible. Restore is required before another graph backup."
            BackupGraphReadinessState.LOCAL_REMOTE_CHANGES -> "Local and remote changes both exist. They require reconciliation; do not Restore over local edits."
            BackupGraphReadinessState.ADOPTION_REQUIRED -> "A graph backup is visible, but this Vault has no matching verified position. Reconciliation is required."
            BackupGraphReadinessState.FORK -> "Fork detected. Ordinary graph Backup and Restore must remain blocked."
            BackupGraphReadinessState.DIVERGENT -> "Local and remote graph histories diverge. Reconciliation is required."
            BackupGraphReadinessState.UNSUPPORTED -> "The graph requires an unsupported backup reader."
            BackupGraphReadinessState.CORRUPT -> "The graph could not be verified. Nothing was applied."
            BackupGraphReadinessState.MISSING_ANCESTRY -> "Graph ancestry is incomplete. Nothing was applied."
            BackupGraphReadinessState.ACCOUNT_MISMATCH -> "The verified Drive account does not match the local graph proof."
            BackupGraphReadinessState.AMBIGUOUS_NAMESPACE -> "Multiple graph namespaces or lineages are visible. Reconciliation is required."
            BackupGraphReadinessState.MISSING_NAMESPACE -> "The local graph position cannot be found in the visible Drive namespace."
            BackupGraphReadinessState.RECOVERY_REQUIRED -> "An unfinished operation or invalidated local proof requires recovery first."
        }
        return "$explanation\n\nPending local changes: ${local.pendingCount}. Local Vault data: ${if (local.hasData) "present" else "none"}.\n\nRead-only check: no upload, Restore, deletion, acknowledgement or trust change."
    }
}

internal fun reconcileBackupGraph(local: LocalBackupReadiness, accountId: String, legacyVisible: Boolean,
    namespaceCount: Int, graph: BackupGraph?, objects: List<GraphObject>): BackupGraphReadiness {
    fun result(state: BackupGraphReadinessState) = BackupGraphReadiness(state, local, legacyVisible)
    if (local.accountId != null && local.accountId != accountId) return result(BackupGraphReadinessState.ACCOUNT_MISMATCH)
    if (local.recoveryRequired || local.position != null && !local.proofValid) return result(BackupGraphReadinessState.RECOVERY_REQUIRED)
    if (namespaceCount > 1) return result(BackupGraphReadinessState.AMBIGUOUS_NAMESPACE)
    if (namespaceCount == 0) return result(if (local.position != null) BackupGraphReadinessState.MISSING_NAMESPACE else if (legacyVisible) BackupGraphReadinessState.LEGACY_BASELINE_REQUIRED else BackupGraphReadinessState.FIRST_BASELINE_REQUIRED)
    if (graph == null) return result(BackupGraphReadinessState.RECOVERY_REQUIRED)
    when (graph.status) {
        GraphStatus.FORK -> return result(BackupGraphReadinessState.FORK)
        GraphStatus.UNSUPPORTED -> return result(BackupGraphReadinessState.UNSUPPORTED)
        GraphStatus.MISSING_ANCESTRY -> return result(BackupGraphReadinessState.MISSING_ANCESTRY)
        GraphStatus.SINGLE_TIP -> Unit
        else -> return result(BackupGraphReadinessState.CORRUPT)
    }
    if (local.position == null) return result(BackupGraphReadinessState.ADOPTION_REQUIRED)
    val receipt = objects.singleOrNull { runCatching { BackupGraphProtocol.parse(it).commitId == local.position }.getOrDefault(false) }
    if (receipt?.objectRef != local.expectedReference) return result(BackupGraphReadinessState.DIVERGENT)
    val plan = graph.plan(local.position)
    return result(when (plan.status) {
        GraphStatus.ALREADY_CURRENT -> if (local.pendingCount == 0) BackupGraphReadinessState.CURRENT else BackupGraphReadinessState.LOCAL_PENDING
        GraphStatus.DESCENDANTS -> if (local.pendingCount == 0) BackupGraphReadinessState.RESTORE_REQUIRED else BackupGraphReadinessState.LOCAL_REMOTE_CHANGES
        else -> BackupGraphReadinessState.DIVERGENT
    })
}

/** Read-only local snapshot. Never enrolls an account, captures/acks work or hashes binaries. */
internal suspend fun readLocalBackupReadiness(db: VaultDatabase, email: String): LocalBackupReadiness = db.withTransaction {
    val account = normalizeGoogleDriveAccount(email)
    check('@' in account)
    val journal = db.backupJournalDao(); val clock = journal.clock()
    val bindings = db.backupGraphDao().bindings(account)
    val applied = db.backupGraphRestoreDao().appliedForAccount(account)
    val binding = bindings.singleOrNull(); val restored = applied.singleOrNull()
    val tracked = journal.account(account)
    val scope = if (tracked == null) BackupJournalSchema.Unassigned else account
    val pending = journal.pending(scope).size
    val publication = binding?.takeIf { tracked?.trusted == true && it.originEpoch == clock.originEpoch &&
        tracked.checkpointId == it.checkpointId && tracked.headId == it.deltaHeadId &&
        tracked.manifestId == it.checkpointFileId && tracked.manifestSha256 == it.checkpointSha256 }
    val position = publication?.commitId ?: restored?.commitId ?: binding?.commitId
    val reference = publication?.let { GraphObjectRef(it.commitFileId,it.commitSha256,it.commitSize) }
        ?: restored?.let { GraphObjectRef(it.commitFileId,it.commitSha256,it.commitSize) }
        ?: binding?.let { GraphObjectRef(it.commitFileId,it.commitSha256,it.commitSize) }
    val hasData = BackupRecordKeys.keys.any { group -> db.openHelper.readableDatabase.query("SELECT 1 FROM `${backupRecordTable(group)}` LIMIT 1").use { it.moveToFirst() } }
    LocalBackupReadiness(pending,hasData,position,reference,publication?.lineageId ?: restored?.lineageId ?: binding?.lineageId,
        publication?.driveAccountId ?: restored?.driveAccountId ?: binding?.driveAccountId,
        publication != null || restored?.originEpoch == clock.originEpoch,
        bindings.size > 1 || applied.size > 1 || binding != null && restored != null && binding.lineageId != restored.lineageId || clock.settingsToken != null || clock.suppressionDepth != 0 ||
            db.backupGraphDao().unfinished(account).isNotEmpty() || db.backupGraphRestoreDao().unfinished().isNotEmpty())
}
