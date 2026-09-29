package com.myvault.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object BackupGraphRestoreSchema {
    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS backup_graph_applied_states (accountScope TEXT NOT NULL, lineageId TEXT NOT NULL, driveAccountId TEXT NOT NULL, commitId TEXT NOT NULL, commitFileId TEXT NOT NULL, commitSha256 TEXT NOT NULL, commitSize INTEGER NOT NULL, checkpointId TEXT NOT NULL, checkpointFileId TEXT NOT NULL, checkpointSha256 TEXT NOT NULL, checkpointSize INTEGER NOT NULL, deltaHeadId TEXT NOT NULL, originEpoch INTEGER NOT NULL, PRIMARY KEY(accountScope, lineageId))",
        "CREATE TABLE IF NOT EXISTS backup_graph_restores (accountScope TEXT NOT NULL, operationId TEXT NOT NULL, lineageId TEXT NOT NULL, driveAccountId TEXT NOT NULL, originalAppliedJson TEXT, capturedGeneration INTEGER NOT NULL, capturedOriginEpoch INTEGER NOT NULL, commitJson TEXT NOT NULL, commitFileId TEXT NOT NULL, commitSha256 TEXT NOT NULL, commitSize INTEGER NOT NULL, frozenChangesJson TEXT NOT NULL, frozenChangesSha256 TEXT NOT NULL, settingsBeforeJson TEXT, settingsPhase TEXT NOT NULL, status TEXT NOT NULL, PRIMARY KEY(accountScope, operationId))",
        "CREATE TABLE IF NOT EXISTS backup_graph_restore_objects (accountScope TEXT NOT NULL, operationId TEXT NOT NULL, attachmentId TEXT NOT NULL, cloudFileId TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, stagingPath TEXT NOT NULL, destinationPath TEXT NOT NULL, status TEXT NOT NULL, PRIMARY KEY(accountScope, operationId, attachmentId))",
    )
    fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
}
