package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.RecordSyncConflictEntity
import com.myvault.app.data.local.entity.RecordSyncControlEntity
import com.myvault.app.data.local.entity.RecordSyncFileEntity
import com.myvault.app.data.local.entity.RecordSyncHeadEntity
import com.myvault.app.data.local.entity.RecordSyncPendingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordSyncDao {
    @Query("SELECT * FROM record_sync_control WHERE id = 1")
    suspend fun control(): RecordSyncControlEntity?

    @Query("SELECT * FROM record_sync_control WHERE id = 1")
    fun observeControl(): Flow<RecordSyncControlEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveControl(value: RecordSyncControlEntity)

    @Query("SELECT * FROM record_sync_pending WHERE accountId = :accountId AND excludedReason IS NULL ORDER BY changedAt LIMIT :limit")
    suspend fun pending(accountId: String, limit: Int = 100): List<RecordSyncPendingEntity>

    @Query("SELECT * FROM record_sync_pending WHERE accountId = :accountId AND excludedReason IS NOT NULL")
    suspend fun excluded(accountId: String): List<RecordSyncPendingEntity>

    @Query("SELECT MAX(changedAt) FROM record_sync_pending WHERE accountId = :accountId")
    fun observeLatestPendingChange(accountId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedPending(value: RecordSyncPendingEntity): Long

    @Query("SELECT * FROM record_sync_pending WHERE accountId = :accountId AND entityType = :type AND entityId = :id")
    suspend fun pendingFor(accountId: String, type: String, id: String): RecordSyncPendingEntity?

    @Query("UPDATE record_sync_pending SET preparedGeneration = :generation, preparedRevisionJson = :json WHERE accountId = :accountId AND entityType = :type AND entityId = :id AND generation = :generation")
    suspend fun prepare(accountId: String, type: String, id: String, generation: Long, json: String): Int

    @Query("UPDATE record_sync_pending SET excludedReason = :reason, preparedGeneration = NULL, preparedRevisionJson = NULL WHERE accountId = :accountId AND entityType = :type AND entityId = :id")
    suspend fun exclude(accountId: String, type: String, id: String, reason: String)

    @Query("DELETE FROM record_sync_pending WHERE accountId = :accountId AND entityType = :type AND entityId = :id AND generation = :generation")
    suspend fun acknowledge(accountId: String, type: String, id: String, generation: Long): Int

    @Query("UPDATE record_sync_pending SET baseRevisionId = :revisionId, preparedGeneration = NULL, preparedRevisionJson = NULL WHERE accountId = :accountId AND entityType = :type AND entityId = :id AND generation != :generation")
    suspend fun advancePendingBase(accountId: String, type: String, id: String, generation: Long, revisionId: String)

    @Query("DELETE FROM record_sync_pending WHERE accountId = :accountId")
    suspend fun clearPending(accountId: String)

    @Query("SELECT * FROM record_sync_heads WHERE accountId = :accountId AND entityType = :type AND entityId = :id")
    suspend fun head(accountId: String, type: String, id: String): RecordSyncHeadEntity?

    @Query("SELECT * FROM record_sync_heads WHERE accountId = :accountId")
    suspend fun heads(accountId: String): List<RecordSyncHeadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHead(value: RecordSyncHeadEntity)

    @Query("SELECT * FROM record_sync_files WHERE accountId = :accountId AND mutationId = :mutationId LIMIT 1")
    suspend fun fileByMutation(accountId: String, mutationId: String): RecordSyncFileEntity?

    @Query("SELECT * FROM record_sync_files WHERE fileId = :fileId")
    suspend fun fileById(fileId: String): RecordSyncFileEntity?

    @Query("SELECT * FROM record_sync_files WHERE accountId = :accountId AND revisionId = :revisionId LIMIT 1")
    suspend fun fileByRevision(accountId: String, revisionId: String): RecordSyncFileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveFile(value: RecordSyncFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConflict(value: RecordSyncConflictEntity)

    @Query("SELECT * FROM record_sync_conflicts WHERE accountId = :accountId AND resolvedAt IS NULL")
    suspend fun unresolvedConflicts(accountId: String): List<RecordSyncConflictEntity>

    @Query("SELECT COUNT(*) FROM record_sync_conflicts WHERE accountId = :accountId AND entityType = :type AND entityId = :id AND resolvedAt IS NULL")
    suspend fun conflictCount(accountId: String, type: String, id: String): Int
}
