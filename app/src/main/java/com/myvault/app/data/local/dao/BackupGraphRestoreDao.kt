package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.BackupGraphAppliedState
import com.myvault.app.data.local.entity.BackupGraphRestore
import com.myvault.app.data.local.entity.BackupGraphRestoreObject

@Dao
interface BackupGraphRestoreDao {
    @Query("SELECT * FROM backup_graph_applied_states WHERE accountScope=:scope")
    suspend fun appliedForAccount(scope: String): List<BackupGraphAppliedState>
    @Query("SELECT * FROM backup_graph_applied_states WHERE accountScope=:scope AND lineageId=:lineage")
    suspend fun applied(scope: String, lineage: String): BackupGraphAppliedState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putApplied(value: BackupGraphAppliedState)
    @Insert suspend fun insert(value: BackupGraphRestore)
    @Insert suspend fun insertObjects(values: List<BackupGraphRestoreObject>)
    @Query("SELECT * FROM backup_graph_restores WHERE accountScope=:scope AND operationId=:operation")
    suspend fun intentFull(scope: String, operation: String): BackupGraphRestore?

    @Query("SELECT accountScope, operationId, lineageId, driveAccountId, originalAppliedJson, capturedGeneration, capturedOriginEpoch, commitJson, commitFileId, commitSha256, commitSize, frozenChangesSha256, settingsBeforeJson, settingsPhase, status FROM backup_graph_restores WHERE accountScope=:scope AND operationId=:operation")
    suspend fun intentMetadata(scope: String, operation: String): com.myvault.app.data.local.entity.BackupGraphRestoreMetadata?

    @Query("SELECT SUBSTR(frozenChangesJson, :offset, :length) FROM backup_graph_restores WHERE accountScope=:scope AND operationId=:operation")
    suspend fun frozenChangesChunk(scope: String, operation: String, offset: Int, length: Int): String?

    @Query("SELECT COUNT(*) FROM backup_graph_restores WHERE status != 'COMPLETE'")
    suspend fun unfinishedCount(): Int

    @Query("SELECT * FROM backup_graph_restores WHERE status != 'COMPLETE'")
    suspend fun unfinished(): List<BackupGraphRestore>

    @Query("SELECT accountScope, operationId, lineageId, driveAccountId, originalAppliedJson, capturedGeneration, capturedOriginEpoch, commitJson, commitFileId, commitSha256, commitSize, frozenChangesSha256, settingsBeforeJson, settingsPhase, status FROM backup_graph_restores WHERE status != 'COMPLETE'")
    suspend fun unfinishedMetadata(): List<com.myvault.app.data.local.entity.BackupGraphRestoreMetadata>

    @Query("SELECT * FROM backup_graph_restore_objects WHERE accountScope=:scope AND operationId=:operation ORDER BY attachmentId")
    suspend fun objects(scope: String, operation: String): List<BackupGraphRestoreObject>
    @Query("UPDATE backup_graph_restores SET settingsPhase=:phase, status=:status WHERE accountScope=:scope AND operationId=:operation")
    suspend fun phase(scope: String, operation: String, phase: String, status: String)
    @Query("UPDATE backup_graph_restore_objects SET status=:status WHERE accountScope=:scope AND operationId=:operation AND attachmentId=:id")
    suspend fun objectStatus(scope: String, operation: String, id: String, status: String)
}

suspend fun BackupGraphRestoreDao.readIntent(scope: String, operation: String): BackupGraphRestore? {
    val meta = intentMetadata(scope, operation) ?: return null
    val builder = StringBuilder()
    var offset = 1
    val chunkSize = 25000
    while(true) {
        val chunk = frozenChangesChunk(scope, operation, offset, chunkSize) ?: break
        builder.append(chunk)
        if (chunk.length < chunkSize) break
        offset += chunkSize
    }
    return BackupGraphRestore(
        accountScope = meta.accountScope,
        operationId = meta.operationId,
        lineageId = meta.lineageId,
        driveAccountId = meta.driveAccountId,
        originalAppliedJson = meta.originalAppliedJson,
        capturedGeneration = meta.capturedGeneration,
        capturedOriginEpoch = meta.capturedOriginEpoch,
        commitJson = meta.commitJson,
        commitFileId = meta.commitFileId,
        commitSha256 = meta.commitSha256,
        commitSize = meta.commitSize,
        frozenChangesJson = builder.toString(),
        frozenChangesSha256 = meta.frozenChangesSha256,
        settingsBeforeJson = meta.settingsBeforeJson,
        settingsPhase = meta.settingsPhase,
        status = meta.status
    )
}
