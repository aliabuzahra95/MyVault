package com.myvault.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object BackupGraphSchema {
    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS backup_graph_bindings (accountScope TEXT NOT NULL, lineageId TEXT NOT NULL, driveAccountId TEXT NOT NULL, commitId TEXT NOT NULL, commitFileId TEXT NOT NULL, commitSha256 TEXT NOT NULL, commitSize INTEGER NOT NULL, checkpointId TEXT NOT NULL, checkpointFileId TEXT NOT NULL, checkpointSha256 TEXT NOT NULL, checkpointSize INTEGER NOT NULL, deltaHeadId TEXT NOT NULL, originEpoch INTEGER NOT NULL, PRIMARY KEY(accountScope, lineageId))",
        "CREATE TABLE IF NOT EXISTS backup_graph_publications (accountScope TEXT NOT NULL, operationId TEXT NOT NULL, lineageId TEXT NOT NULL, driveAccountId TEXT NOT NULL, capturedGeneration INTEGER NOT NULL, capturedOriginEpoch INTEGER NOT NULL, originalAccountJson TEXT NOT NULL, originalBindingJson TEXT, frozenBatchJson TEXT NOT NULL, commitJson TEXT NOT NULL, status TEXT NOT NULL, PRIMARY KEY(accountScope, operationId))",
        "CREATE TABLE IF NOT EXISTS backup_graph_publication_objects (accountScope TEXT NOT NULL, operationId TEXT NOT NULL, objectId TEXT NOT NULL, ordinal INTEGER NOT NULL, role TEXT NOT NULL, attachmentId TEXT, stagedPath TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, verifiedSha256 TEXT, verifiedByteCount INTEGER, PRIMARY KEY(accountScope, operationId, objectId))",
    )

    fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
}
