package com.myvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myvault.app.data.local.entity.BackupGraphBinding
import com.myvault.app.data.local.entity.BackupGraphPublication
import com.myvault.app.data.local.entity.BackupGraphPublicationObject

@Dao
interface BackupGraphDao {
    @Query("SELECT * FROM backup_graph_bindings WHERE accountScope=:scope AND lineageId=:lineage")
    suspend fun binding(scope: String, lineage: String): BackupGraphBinding?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBinding(binding: BackupGraphBinding)
    @Insert suspend fun insertPublication(publication: BackupGraphPublication)
    @Insert suspend fun insertObjects(objects: List<BackupGraphPublicationObject>)
    @Query("SELECT * FROM backup_graph_publications WHERE accountScope=:scope AND operationId=:operation")
    suspend fun publication(scope: String, operation: String): BackupGraphPublication?
    @Query("SELECT * FROM backup_graph_publications WHERE accountScope=:scope AND status != 'COMPLETE'")
    suspend fun unfinished(scope: String): List<BackupGraphPublication>
    @Query("SELECT * FROM backup_graph_publication_objects WHERE accountScope=:scope AND operationId=:operation ORDER BY ordinal")
    suspend fun objects(scope: String, operation: String): List<BackupGraphPublicationObject>
    @Query("SELECT * FROM backup_graph_publication_objects WHERE accountScope=:scope AND objectId=:objectId")
    suspend fun objectsWithId(scope: String, objectId: String): List<BackupGraphPublicationObject>
    @Query("UPDATE backup_graph_publication_objects SET verifiedSha256=:hash, verifiedByteCount=:size WHERE accountScope=:scope AND operationId=:operation AND objectId=:objectId AND sha256=:hash AND byteCount=:size")
    suspend fun receipt(scope: String, operation: String, objectId: String, hash: String, size: Long): Int
    @Query("UPDATE backup_graph_publications SET status=:status WHERE accountScope=:scope AND operationId=:operation")
    suspend fun status(scope: String, operation: String, status: String)
}
