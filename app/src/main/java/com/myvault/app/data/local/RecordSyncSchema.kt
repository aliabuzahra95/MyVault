package com.myvault.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object RecordSyncSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS record_sync_control (id INTEGER NOT NULL PRIMARY KEY, accountId TEXT, clientId TEXT NOT NULL, enabled INTEGER NOT NULL, paused INTEGER NOT NULL, applyingRemote INTEGER NOT NULL, cursor TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS record_sync_pending (accountId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, generation INTEGER NOT NULL, changedAt INTEGER NOT NULL, baseRevisionId TEXT, preparedGeneration INTEGER, preparedRevisionJson TEXT, excludedReason TEXT, PRIMARY KEY(accountId, entityType, entityId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS record_sync_heads (accountId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, revisionId TEXT NOT NULL, contentHash TEXT NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(accountId, entityType, entityId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS record_sync_files (fileId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, revisionId TEXT NOT NULL, mutationId TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_sync_files_accountId_entityType_entityId ON record_sync_files(accountId, entityType, entityId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS record_sync_conflicts (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, localRevisionId TEXT, remoteRevisionId TEXT NOT NULL, remotePayloadJson TEXT, remoteDeleted INTEGER NOT NULL, conflictCopyId TEXT, createdAt INTEGER NOT NULL, resolvedAt INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_sync_conflicts_accountId_entityType_entityId ON record_sync_conflicts(accountId, entityType, entityId)")
        installTriggers(db)
    }

    fun installTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO record_sync_control(id, accountId, clientId, enabled, paused, applyingRemote, cursor) VALUES(1, NULL, '', 0, 1, 0, NULL)")
        createTrigger(db, "note_insert", "AFTER INSERT ON notes", "NEW.id", "NEW.folderId IS NULL OR EXISTS(SELECT 1 FROM folders WHERE id = NEW.folderId AND mode = 'study')")
        createTrigger(db, "note_update", "AFTER UPDATE ON notes", "NEW.id", "OLD.folderId IS NULL OR NEW.folderId IS NULL OR EXISTS(SELECT 1 FROM folders WHERE id IN (OLD.folderId, NEW.folderId) AND mode = 'study')")
        createTrigger(db, "note_delete", "AFTER DELETE ON notes", "OLD.id", "OLD.folderId IS NULL OR EXISTS(SELECT 1 FROM folders WHERE id = OLD.folderId AND mode = 'study')")
        createTrigger(db, "folder_insert", "AFTER INSERT ON folders", "NEW.id", "NEW.mode = 'study'")
        createTrigger(db, "folder_update", "AFTER UPDATE ON folders", "NEW.id", "OLD.mode = 'study' OR NEW.mode = 'study'")
        createTrigger(db, "folder_delete", "AFTER DELETE ON folders", "OLD.id", "OLD.mode = 'study'")
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS record_sync_capture_folder_mode_notes AFTER UPDATE OF mode ON folders
            WHEN OLD.mode != NEW.mode
                AND (OLD.mode = 'study' OR NEW.mode = 'study')
                AND (SELECT enabled = 1 AND applyingRemote = 0 AND accountId IS NOT NULL FROM record_sync_control WHERE id = 1)
            BEGIN
                INSERT INTO record_sync_pending(accountId, entityType, entityId, generation, changedAt, baseRevisionId)
                SELECT (SELECT accountId FROM record_sync_control WHERE id = 1), 'note', notes.id, 1,
                    CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                    (SELECT revisionId FROM record_sync_heads
                        WHERE accountId = (SELECT accountId FROM record_sync_control WHERE id = 1)
                        AND entityType = 'note' AND entityId = notes.id)
                FROM notes WHERE notes.folderId = NEW.id
                ON CONFLICT(accountId, entityType, entityId) DO UPDATE SET
                    generation = generation + 1,
                    changedAt = excluded.changedAt,
                    excludedReason = NULL;
            END
            """.trimIndent(),
        )
    }

    private fun createTrigger(db: SupportSQLiteDatabase, suffix: String, event: String, id: String, scope: String) {
        val type = if (suffix.startsWith("note")) "note" else "folder"
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS record_sync_capture_$suffix $event
            WHEN (SELECT enabled = 1 AND applyingRemote = 0 AND accountId IS NOT NULL FROM record_sync_control WHERE id = 1)
                AND ($scope)
            BEGIN
                INSERT INTO record_sync_pending(accountId, entityType, entityId, generation, changedAt, baseRevisionId)
                VALUES(
                    (SELECT accountId FROM record_sync_control WHERE id = 1), '$type', $id, 1,
                    CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                    (SELECT revisionId FROM record_sync_heads WHERE accountId = (SELECT accountId FROM record_sync_control WHERE id = 1) AND entityType = '$type' AND entityId = $id)
                )
                ON CONFLICT(accountId, entityType, entityId) DO UPDATE SET
                    generation = generation + 1,
                    changedAt = excluded.changedAt,
                    excludedReason = NULL;
            END
            """.trimIndent(),
        )
    }
}
