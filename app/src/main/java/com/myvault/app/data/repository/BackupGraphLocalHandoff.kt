package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.BackupGraphBinding

/** A publication proves the captured local snapshot, not a separate Restore event. */
internal suspend fun trustedPublicationPosition(db: VaultDatabase, context: GraphWriterContext, settingsToken: String? = null): BackupGraphBinding? {
    context.validate()
    val binding = db.backupGraphDao().binding(context.accountScope, context.lineageId) ?: return null
    check(binding.driveAccountId == context.driveAccountId)
    val account = db.backupJournalDao().account(context.accountScope) ?: return null
    val clock = db.backupJournalDao().clock()
    return binding.takeIf {
        account.trusted && clock.originEpoch == it.originEpoch && clock.suppressionDepth == 0 &&
            (clock.settingsToken == null || settingsToken != null && clock.settingsToken == settingsToken) && account.checkpointId == it.checkpointId &&
            account.headId == it.deltaHeadId && account.manifestId == it.checkpointFileId &&
            account.manifestSha256 == it.checkpointSha256
    }
}

/** Explicit next-Backup handoff from a completed Restore. Never acknowledges local edits. */
internal suspend fun adoptVerifiedRestoredParent(
    db: VaultDatabase, context: GraphWriterContext, inventory: List<GraphObject>,
): Boolean {
    context.validate()
    val graph = BackupGraph.discover(inventory, context.driveAccountId, context.lineageId)
    check(graph.status == GraphStatus.SINGLE_TIP) { "A single verified graph tip is required." }
    return db.withTransaction {
        val restored = db.backupGraphRestoreDao().applied(context.accountScope, context.lineageId)
            ?: return@withTransaction false
        val clock = db.backupJournalDao().clock()
        check(restored.driveAccountId == context.driveAccountId && restored.originEpoch == clock.originEpoch)
        check(clock.settingsToken == null && clock.suppressionDepth == 0)
        check(db.backupGraphRestoreDao().unfinishedMetadata().isEmpty() && db.backupGraphDao().unfinishedMetadata(context.accountScope).isEmpty())
        val commit = graph.commits[restored.commitId] ?: error("Restored graph position is missing.")
        check(graph.tips.single() == restored.commitId) { "Restore newer graph changes before Backup." }
        val ref = GraphObjectRef(restored.commitFileId, restored.commitSha256, restored.commitSize)
        check(inventory.singleOrNull { it.objectRef.cloudFileId == restored.commitFileId }?.objectRef == ref)
        check(commit.checkpoint == GraphCheckpoint(restored.checkpointId,
            GraphObjectRef(restored.checkpointFileId, restored.checkpointSha256, restored.checkpointSize)))
        check(commit.deltaHead == restored.deltaHeadId)
        val account = db.backupJournalDao().account(context.accountScope) ?: error("Restored account is not enrolled.")
        val binding = BackupGraphBinding(context.accountScope, context.lineageId, context.driveAccountId,
            restored.commitId, restored.commitFileId, restored.commitSha256, restored.commitSize,
            restored.checkpointId, restored.checkpointFileId, restored.checkpointSha256, restored.checkpointSize,
            restored.deltaHeadId, restored.originEpoch)
        if (trustedPublicationPosition(db, context) == binding) return@withTransaction false
        // This establishes the next publication parent from verified applied state;
        // it does not claim that this device created the remote commit.
        check(!account.trusted || db.backupGraphDao().binding(context.accountScope, context.lineageId)?.commitId == restored.commitId)
        db.backupJournalDao().trust(context.accountScope, restored.checkpointId, restored.deltaHeadId,
            restored.checkpointFileId, restored.checkpointSha256)
        db.backupGraphDao().putBinding(binding)
        true
    }
}
