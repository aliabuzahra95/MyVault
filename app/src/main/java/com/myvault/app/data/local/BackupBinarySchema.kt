package com.myvault.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object BackupBinarySchema {
    val createStatements = listOf(
        "CREATE TABLE IF NOT EXISTS backup_binary_fingerprints (attachmentId TEXT NOT NULL PRIMARY KEY, localPath TEXT NOT NULL, byteSize INTEGER NOT NULL, sha256 TEXT, status TEXT NOT NULL, generation INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS backup_binary_references (accountScope TEXT NOT NULL, attachmentId TEXT NOT NULL, checkpointId TEXT NOT NULL, headId TEXT NOT NULL, cloudFileId TEXT NOT NULL, byteSize INTEGER NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(accountScope, attachmentId))",
    )

    // Run for Restore too: byte claims must never survive a path/size change unverified.
    val triggers = listOf("INSERT", "UPDATE").map { operation ->
        """
        CREATE TRIGGER IF NOT EXISTS backup_binary_attachments_${operation.lowercase()}_v1 AFTER $operation ON attachments
        BEGIN
            INSERT OR IGNORE INTO backup_binary_fingerprints
            VALUES (NEW.id, NEW.localPath, NEW.sizeBytes, NULL, 'UNKNOWN', (SELECT generation FROM backup_journal_state WHERE id=1));
            UPDATE backup_binary_fingerprints SET localPath=NEW.localPath, byteSize=NEW.sizeBytes, sha256=NULL, status='UNKNOWN',
                generation=(SELECT generation FROM backup_journal_state WHERE id=1)
            WHERE attachmentId=NEW.id AND (localPath IS NOT NEW.localPath OR byteSize IS NOT NEW.sizeBytes OR status='DELETED');
        END
        """.trimIndent()
    } + """
        CREATE TRIGGER IF NOT EXISTS backup_binary_attachments_delete_v1 AFTER DELETE ON attachments
        BEGIN
            INSERT OR IGNORE INTO backup_binary_fingerprints
            VALUES (OLD.id, OLD.localPath, OLD.sizeBytes, NULL, 'DELETED', (SELECT generation FROM backup_journal_state WHERE id=1));
            UPDATE backup_binary_fingerprints SET status='DELETED', generation=(SELECT generation FROM backup_journal_state WHERE id=1)
            WHERE attachmentId=OLD.id;
        END
    """.trimIndent()

    fun migrate(db: SupportSQLiteDatabase) = createStatements.forEach(db::execSQL)
}
