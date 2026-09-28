package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.BackupJournalSql
import com.myvault.app.data.local.entity.BackupJournalState
import com.myvault.app.data.local.entity.BackupPendingChange
import com.myvault.app.data.local.entity.BackupTrackingAccount

@Dao
interface BackupJournalDao {
    @Query(BackupJournalSql.Clock) suspend fun clock(): BackupJournalState
    @Query(BackupJournalSql.Tick) suspend fun tick()
    @Query("SELECT * FROM backup_tracking_accounts WHERE accountScope = :scope")
    suspend fun account(scope: String): BackupTrackingAccount?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enroll(account: BackupTrackingAccount): Long
    @Query(BackupJournalSql.SeedAccount) suspend fun seedAccount(scope: String)
    @Query("SELECT * FROM backup_pending_changes WHERE accountScope = :scope ORDER BY recordGroup, key0, key1, key2")
    suspend fun pending(scope: String): List<BackupPendingChange>
    @Query(BackupJournalSql.Ack) suspend fun acknowledge(scope: String, generation: Long)
    @Query(BackupJournalSql.Invalidate) suspend fun invalidate(reason: String)
    @Query("UPDATE backup_tracking_accounts SET trusted = 1, checkpointId = :checkpoint, headId = :head, manifestId = :manifest, manifestSha256 = :hash, reason = 'verified_commit' WHERE accountScope = :scope")
    suspend fun trust(scope: String, checkpoint: String, head: String, manifest: String, hash: String)
    @Query("UPDATE backup_journal_state SET suppressionDepth = suppressionDepth + 1, originEpoch = originEpoch + 1 WHERE id = 1")
    suspend fun enterRestore()
    @Query("UPDATE backup_journal_state SET suppressionDepth = suppressionDepth - 1 WHERE id = 1")
    suspend fun leaveRestore()
    @Query("UPDATE backup_journal_state SET settingsToken = :token WHERE id = 1")
    suspend fun setSettingsToken(token: String?)
    @Query(BackupJournalSql.SettingsDirty) suspend fun dirtySettings()
}
