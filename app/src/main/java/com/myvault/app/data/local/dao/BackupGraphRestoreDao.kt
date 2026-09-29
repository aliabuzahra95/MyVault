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
    @Query("SELECT * FROM backup_graph_applied_states WHERE accountScope=:scope AND lineageId=:lineage")
    suspend fun applied(scope: String, lineage: String): BackupGraphAppliedState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putApplied(value: BackupGraphAppliedState)
    @Insert suspend fun insert(value: BackupGraphRestore)
    @Insert suspend fun insertObjects(values: List<BackupGraphRestoreObject>)
    @Query("SELECT * FROM backup_graph_restores WHERE accountScope=:scope AND operationId=:operation")
    suspend fun intent(scope: String, operation: String): BackupGraphRestore?
    @Query("SELECT * FROM backup_graph_restores WHERE status != 'COMPLETE'")
    suspend fun unfinished(): List<BackupGraphRestore>
    @Query("SELECT * FROM backup_graph_restore_objects WHERE accountScope=:scope AND operationId=:operation ORDER BY attachmentId")
    suspend fun objects(scope: String, operation: String): List<BackupGraphRestoreObject>
    @Query("UPDATE backup_graph_restores SET settingsPhase=:phase, status=:status WHERE accountScope=:scope AND operationId=:operation")
    suspend fun phase(scope: String, operation: String, phase: String, status: String)
    @Query("UPDATE backup_graph_restore_objects SET status=:status WHERE accountScope=:scope AND operationId=:operation AND attachmentId=:id")
    suspend fun objectStatus(scope: String, operation: String, id: String, status: String)
}
