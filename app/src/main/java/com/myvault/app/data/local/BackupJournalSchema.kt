package com.myvault.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myvault.app.data.repository.BackupRecordKeys
import com.myvault.app.data.repository.backupRecordTable

/** Backup-only capture. No JSON SQLite extensions or record-sync tables are required. */
internal object BackupJournalSchema {
    const val Unassigned = "local-unassigned"
    val createStatements = listOf(
        "CREATE TABLE IF NOT EXISTS backup_journal_state (id INTEGER NOT NULL PRIMARY KEY, generation INTEGER NOT NULL, originEpoch INTEGER NOT NULL, suppressionDepth INTEGER NOT NULL, settingsToken TEXT)",
        "CREATE TABLE IF NOT EXISTS backup_tracking_accounts (accountScope TEXT NOT NULL PRIMARY KEY, trusted INTEGER NOT NULL, checkpointId TEXT, headId TEXT, manifestId TEXT, manifestSha256 TEXT, reason TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS backup_pending_changes (accountScope TEXT NOT NULL, recordGroup TEXT NOT NULL, key0 TEXT NOT NULL, key1 TEXT NOT NULL, key2 TEXT NOT NULL, operation TEXT NOT NULL, generation INTEGER NOT NULL, PRIMARY KEY(accountScope, recordGroup, key0, key1, key2))",
        "INSERT OR IGNORE INTO backup_journal_state VALUES (1, 0, 0, 0, NULL)",
        "INSERT OR IGNORE INTO backup_tracking_accounts VALUES ('$Unassigned', 0, NULL, NULL, NULL, NULL, 'baseline_required')",
    )

    fun migrate(db: SupportSQLiteDatabase) = createStatements.forEach(db::execSQL)

    fun triggerStatements(file: String, columns: List<String>): List<String> {
        val table = backupRecordTable(file)
        val keys = BackupRecordKeys.getValue(file)
        check(keys.size in 1..3 && columns.containsAll(keys))
        check(columns.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) })
        return listOf("INSERT", "UPDATE", "DELETE").map { operation ->
            val row = if (operation == "DELETE") "OLD" else "NEW"
            val keySql = (0..2).map { index -> keys.getOrNull(index)?.let { "CAST($row.`$it` AS TEXT)" } ?: "''" }
            val changed = if (operation == "UPDATE") " AND NOT (${columns.joinToString(" AND ") { "OLD.`$it` IS NEW.`$it`" }})" else ""
            """
            CREATE TRIGGER IF NOT EXISTS backup_journal_${table}_${operation.lowercase()}_v1
            AFTER $operation ON `$table`
            WHEN (SELECT suppressionDepth FROM backup_journal_state WHERE id = 1) = 0$changed
            BEGIN
                UPDATE backup_journal_state SET generation = generation + 1 WHERE id = 1;
                INSERT OR REPLACE INTO backup_pending_changes
                SELECT accountScope, '$file', ${keySql.joinToString(", ")}, '${if (operation == "DELETE") "DELETE" else "UPSERT"}',
                    (SELECT generation FROM backup_journal_state WHERE id = 1)
                FROM backup_tracking_accounts;
            END
            """.trimIndent()
        }
    }

    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Only schema metadata is inspected; existing user rows are never enumerated.
            db.beginTransaction()
            try {
                migrate(db)
                BackupBinarySchema.migrate(db)
                BackupBinarySchema.triggers.forEach(db::execSQL)
                BackupRecordKeys.keys.forEach { file ->
                    val columns = mutableListOf<String>()
                    db.query("PRAGMA table_info(`${backupRecordTable(file)}`)").use { cursor ->
                        while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    }
                    triggerStatements(file, columns).forEach(db::execSQL)
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
}

internal object BackupJournalSql {
    const val Clock = "SELECT * FROM backup_journal_state WHERE id = 1"
    const val Tick = "UPDATE backup_journal_state SET generation = generation + 1 WHERE id = 1"
    const val SeedAccount = "INSERT OR IGNORE INTO backup_pending_changes SELECT :scope, recordGroup, key0, key1, key2, operation, generation FROM backup_pending_changes WHERE accountScope = 'local-unassigned'"
    const val SettingsDirty = "INSERT OR REPLACE INTO backup_pending_changes SELECT accountScope, 'settings.json', 'settings', '', '', 'UPSERT', (SELECT generation FROM backup_journal_state WHERE id = 1) FROM backup_tracking_accounts"
    const val Ack = "DELETE FROM backup_pending_changes WHERE accountScope = :scope AND generation <= :generation"
    const val Invalidate = "UPDATE backup_tracking_accounts SET trusted = 0, reason = :reason"
}
