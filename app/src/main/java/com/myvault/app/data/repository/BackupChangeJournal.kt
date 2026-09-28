package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.BackupPendingChange
import com.myvault.app.data.local.entity.BackupTrackingAccount
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class CapturedBackupChanges(
    val account: BackupTrackingAccount,
    val generation: Long,
    val originEpoch: Long,
    val changes: List<BackupPendingChange>,
)

/** Must be supplied only AFTER future transport verification and committed-manifest readback. */
internal data class ConfirmedBackupCommit(
    val accountEmail: String,
    val capturedGeneration: Long,
    val manifestId: String,
    val manifestSha256: String,
    val checkpointId: String,
    val headId: String,
)

internal fun BackupPendingChange.stableKey(): List<String> =
    listOf(key0, key1, key2).take(if (recordGroup == "settings.json") 1 else BackupRecordKeys.getValue(recordGroup).size)

internal fun validateBackupAcknowledgement(
    snapshot: CapturedBackupChanges,
    proof: ConfirmedBackupCommit,
    current: BackupTrackingAccount,
    clock: com.myvault.app.data.local.entity.BackupJournalState,
    fullCheckpoint: Boolean,
) {
    check(normalizeGoogleDriveAccount(proof.accountEmail) == snapshot.account.accountScope && proof.capturedGeneration == snapshot.generation)
    check(listOf(proof.manifestId, proof.checkpointId, proof.headId).all { it.isNotBlank() })
    check(proof.manifestSha256.matches(Regex("[a-f0-9]{64}")))
    check(clock.originEpoch == snapshot.originEpoch && clock.settingsToken == null && clock.suppressionDepth == 0)
    check(clock.generation >= snapshot.generation) { "Journal generation moved behind the captured state." }
    check(current == snapshot.account) { "Baseline changed after capture. Pending changes were preserved." }
    if (!fullCheckpoint) check(current.trusted && current.checkpointId == proof.checkpointId) { "A verified full baseline is required." }
}

@Singleton
class BackupChangeJournal @Inject constructor(private val database: VaultDatabase) {
    private val dao get() = database.backupJournalDao()
    internal val settingsMutex = Mutex()

    suspend fun registerAccount(email: String) = database.withTransaction {
        val scope = accountScope(email)
        if (dao.enroll(BackupTrackingAccount(scope)) != -1L) dao.seedAccount(scope)
    }

    internal suspend fun capture(email: String): CapturedBackupChanges = database.withTransaction {
        registerAccount(email)
        val clock = dao.clock()
        check(clock.settingsToken == null && clock.suppressionDepth == 0) { "Backup tracking requires preference recovery or completion first." }
        val account = dao.account(accountScope(email))!!
        CapturedBackupChanges(account, clock.generation, clock.originEpoch, dao.pending(account.accountScope))
    }

    internal suspend fun acknowledgeConfirmedDelta(snapshot: CapturedBackupChanges, proof: ConfirmedBackupCommit) =
        acknowledge(snapshot, proof, fullCheckpoint = false)

    internal suspend fun acceptVerifiedFullCheckpoint(snapshot: CapturedBackupChanges, proof: ConfirmedBackupCommit) =
        acknowledge(snapshot, proof, fullCheckpoint = true)

    private suspend fun acknowledge(snapshot: CapturedBackupChanges, proof: ConfirmedBackupCommit, fullCheckpoint: Boolean) = database.withTransaction {
        val clock = dao.clock()
        val current = dao.account(snapshot.account.accountScope)!!
        validateBackupAcknowledgement(snapshot, proof, current, clock, fullCheckpoint)
        dao.acknowledge(current.accountScope, snapshot.generation)
        dao.trust(current.accountScope, proof.checkpointId, proof.headId, proof.manifestId, proof.manifestSha256)
    }

    /** Transaction-local suppression rolls back with a failed Restore, never persists a global mode. */
    suspend fun <T> withRestoreOrigin(block: suspend () -> T): T = database.withTransaction {
        dao.enterRestore()
        dao.invalidate("restore_requires_verified_baseline")
        try { block() } finally { dao.leaveRestore() }
    }

    internal suspend fun beginSettingsWrite(restoring: Boolean): String = database.withTransaction {
        check(dao.clock().settingsToken == null)
        if (restoring) {
            dao.enterRestore()
            dao.invalidate("restore_requires_verified_baseline")
            dao.leaveRestore()
        } else {
            dao.tick()
            dao.dirtySettings()
        }
        UUID.randomUUID().toString().also { dao.setSettingsToken(it) }
    }

    internal suspend fun finishSettingsWrite(token: String, restoring: Boolean) = database.withTransaction {
        check(dao.clock().settingsToken == token)
        if (!restoring) { dao.tick(); dao.dirtySettings() }
        dao.setSettingsToken(null)
    }

    /** Caller holds settingsMutex and has read the durable DataStore after restart. */
    internal suspend fun recoverInterruptedSettingsWrite() = database.withTransaction {
        if (dao.clock().settingsToken != null) {
            dao.tick()
            dao.dirtySettings()
            dao.invalidate("interrupted_preferences_require_baseline")
            dao.setSettingsToken(null)
        }
    }

    private fun accountScope(email: String): String = normalizeGoogleDriveAccount(email).also {
        require(it.isNotBlank() && '@' in it) { "The existing verified Drive account email is required." }
    }
}
