package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.BackupJournalSql
import com.myvault.app.data.local.entity.BackupJournalState
import com.myvault.app.data.local.entity.BackupPendingChange
import com.myvault.app.data.local.entity.BackupTrackingAccount
import com.myvault.app.data.local.entity.BackupBinaryFingerprint
import com.myvault.app.data.local.entity.BackupBinaryReference

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
    @Query("SELECT * FROM backup_binary_fingerprints WHERE attachmentId = :id")
    suspend fun fingerprint(id: String): BackupBinaryFingerprint?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFingerprint(value: BackupBinaryFingerprint)
    @Query("INSERT OR REPLACE INTO backup_pending_changes SELECT accountScope, 'attachments.json', :id, '', '', 'UPSERT', (SELECT generation FROM backup_journal_state WHERE id=1) FROM backup_tracking_accounts")
    suspend fun dirtyBinary(id: String)
    @Query("SELECT * FROM backup_binary_references WHERE accountScope=:scope AND attachmentId=:id")
    suspend fun binaryReference(scope: String, id: String): BackupBinaryReference?
    @Query("SELECT * FROM backup_binary_references WHERE accountScope=:scope AND cloudFileId=:file")
    suspend fun binaryReferencesForObject(scope: String, file: String): List<BackupBinaryReference>
    @Query("DELETE FROM backup_binary_references WHERE accountScope=:scope")
    suspend fun clearBinaryReferences(scope: String)
    @Query("DELETE FROM backup_binary_references WHERE accountScope=:scope AND attachmentId=:id")
    suspend fun removeBinaryReference(scope: String, id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBinaryReferences(values: List<BackupBinaryReference>)
}
