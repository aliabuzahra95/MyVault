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
    @Query("SELECT * FROM backup_graph_bindings WHERE accountScope=:scope")
    suspend fun bindings(scope: String): List<BackupGraphBinding>
    @Query("SELECT * FROM backup_graph_bindings WHERE accountScope=:scope AND lineageId=:lineage")
    suspend fun binding(scope: String, lineage: String): BackupGraphBinding?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBinding(binding: BackupGraphBinding)
    @Insert suspend fun insertPublication(publication: BackupGraphPublication)
    @Insert suspend fun insertObjects(objects: List<BackupGraphPublicationObject>)
    @Query("SELECT * FROM backup_graph_publications WHERE accountScope=:scope AND operationId=:operation")
    suspend fun publicationFull(scope: String, operation: String): BackupGraphPublication?

    @Query("SELECT accountScope, operationId, lineageId, driveAccountId, capturedGeneration, capturedOriginEpoch, originalAccountJson, originalBindingJson, commitJson, status FROM backup_graph_publications WHERE accountScope=:scope AND operationId=:operation")
    suspend fun publicationMetadata(scope: String, operation: String): com.myvault.app.data.local.entity.BackupGraphPublicationMetadata?

    @Query("SELECT SUBSTR(frozenBatchJson, :offset, :length) FROM backup_graph_publications WHERE accountScope=:scope AND operationId=:operation")
    suspend fun frozenBatchChunk(scope: String, operation: String, offset: Int, length: Int): String?

    @Query("SELECT COUNT(*) FROM backup_graph_publications WHERE accountScope=:scope AND status != 'COMPLETE'")
    suspend fun unfinishedCount(scope: String): Int

    @Query("SELECT * FROM backup_graph_publications WHERE accountScope=:scope AND status != 'COMPLETE'")
    suspend fun unfinished(scope: String): List<BackupGraphPublication>

    @Query("SELECT accountScope, operationId, lineageId, driveAccountId, capturedGeneration, capturedOriginEpoch, originalAccountJson, originalBindingJson, commitJson, status FROM backup_graph_publications WHERE accountScope=:scope AND status != 'COMPLETE'")
    suspend fun unfinishedMetadata(scope: String): List<com.myvault.app.data.local.entity.BackupGraphPublicationMetadata>

    @Query("SELECT * FROM backup_graph_publication_objects WHERE accountScope=:scope AND operationId=:operation ORDER BY ordinal")
    suspend fun objects(scope: String, operation: String): List<BackupGraphPublicationObject>
    @Query("SELECT * FROM backup_graph_publication_objects WHERE accountScope=:scope AND objectId=:objectId")
    suspend fun objectsWithId(scope: String, objectId: String): List<BackupGraphPublicationObject>
    @Query("UPDATE backup_graph_publication_objects SET verifiedSha256=:hash, verifiedByteCount=:size WHERE accountScope=:scope AND operationId=:operation AND objectId=:objectId AND sha256=:hash AND byteCount=:size")
    suspend fun receipt(scope: String, operation: String, objectId: String, hash: String, size: Long): Int
    @Query("UPDATE backup_graph_publications SET status=:status WHERE accountScope=:scope AND operationId=:operation")
    suspend fun status(scope: String, operation: String, status: String)
}

suspend fun BackupGraphDao.readPublication(scope: String, operation: String): BackupGraphPublication? {
    val meta = publicationMetadata(scope, operation) ?: return null
    val builder = StringBuilder()
    var offset = 1
    val chunkSize = 500000
    while(true) {
        val chunk = frozenBatchChunk(scope, operation, offset, chunkSize) ?: break
        builder.append(chunk)
        if (chunk.length < chunkSize) break
        offset += chunkSize
    }
    return BackupGraphPublication(
        accountScope = meta.accountScope,
        operationId = meta.operationId,
        lineageId = meta.lineageId,
        driveAccountId = meta.driveAccountId,
        capturedGeneration = meta.capturedGeneration,
        capturedOriginEpoch = meta.capturedOriginEpoch,
        originalAccountJson = meta.originalAccountJson,
        originalBindingJson = meta.originalBindingJson,
        frozenBatchJson = builder.toString(),
        commitJson = meta.commitJson,
        status = meta.status
    )
}
