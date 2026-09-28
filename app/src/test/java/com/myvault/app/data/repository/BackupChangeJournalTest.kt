package com.myvault.app.data.repository

import com.myvault.app.data.local.BackupJournalSchema
import com.myvault.app.data.local.BackupJournalSql
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.BackupJournalState
import com.myvault.app.data.local.entity.BackupPendingChange
import com.myvault.app.data.local.entity.BackupTrackingAccount
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import androidx.sqlite.db.SupportSQLiteDatabase

class BackupChangeJournalTest {
    private val account = BackupTrackingAccount("a@example.com", true, "checkpoint", "head-1", "manifest", "a".repeat(64), "verified_commit")
    private val snapshot = CapturedBackupChanges(account, 10, 2, emptyList())
    private val proof = ConfirmedBackupCommit(" A@EXAMPLE.COM ", 10, "manifest", "b".repeat(64), "checkpoint", "head-2")

    @Test fun newerGenerationDoesNotInvalidateCapturedCommitButUsesBoundedAck() {
        validateBackupAcknowledgement(snapshot, proof, account, BackupJournalState(generation = 11, originEpoch = 2), false)
        assertEquals("DELETE FROM backup_pending_changes WHERE accountScope = :scope AND generation <= :generation", BackupJournalSql.Ack)
    }

    @Test fun accountAndCaptureGenerationCannotBeSubstituted() {
        rejects { validateBackupAcknowledgement(snapshot, proof.copy(accountEmail = "b@example.com"), account, BackupJournalState(originEpoch = 2), false) }
        rejects { validateBackupAcknowledgement(snapshot, proof.copy(capturedGeneration = 11), account, BackupJournalState(originEpoch = 2), false) }
    }

    @Test fun restoreAndBaselineChangesRejectOldAcknowledgement() {
        rejects { validateBackupAcknowledgement(snapshot, proof, account, BackupJournalState(originEpoch = 3), false) }
        rejects { validateBackupAcknowledgement(snapshot, proof, account.copy(headId = "other-head"), BackupJournalState(originEpoch = 2), false) }
        rejects { validateBackupAcknowledgement(snapshot, proof, account, BackupJournalState(originEpoch = 2, suppressionDepth = 1), false) }
    }

    @Test fun preferenceCommitInFlightOrInterruptedBlocksAcknowledgement() {
        rejects { validateBackupAcknowledgement(snapshot, proof, account, BackupJournalState(originEpoch = 2, settingsToken = "durable-intent"), false) }
    }

    @Test fun untrustedBaselineRequiresVerifiedFullCheckpointNotDelta() {
        val untrusted = snapshot.copy(account = BackupTrackingAccount("a@example.com"))
        rejects { validateBackupAcknowledgement(untrusted, proof, untrusted.account, BackupJournalState(originEpoch = 2), false) }
        validateBackupAcknowledgement(untrusted, proof, untrusted.account, BackupJournalState(generation = 10, originEpoch = 2), true)
    }

    @Test fun acknowledgementCannotAcceptAResetGeneration() {
        rejects { validateBackupAcknowledgement(snapshot, proof, account, BackupJournalState(generation = 9, originEpoch = 2), false) }
    }

    @Test fun schema33RetainsEveryExistingEntityUnchanged() {
        val directory = File(projectRoot(), "app/schemas/com.myvault.app.data.local.VaultDatabase")
        fun entities(version: Int): Map<String, String> {
            val rows = JSONObject(File(directory, "$version.json").readText()).getJSONObject("database").getJSONArray("entities")
            return (0 until rows.length()).associate { rows.getJSONObject(it).getString("tableName") to rows.getJSONObject(it).toString() }
        }
        val before = entities(32)
        val after = entities(33)
        before.forEach { (table, definition) -> assertEquals(table, definition, after[table]) }
        assertEquals(setOf("backup_journal_state", "backup_pending_changes", "backup_tracking_accounts"), after.keys - before.keys)
    }

    @Test fun everyProtocolGroupHasStructuralKeysAndSqlCapture() {
        BackupRecordKeys.forEach { (file, fields) ->
            val change = BackupPendingChange("a@example.com", file, "id0", "id1", "id2", "UPSERT", 1)
            assertEquals(fields.size, change.stableKey().size)
            val triggers = BackupJournalSchema.triggerStatements(file, fields + "fixtureValue")
            assertEquals(3, triggers.size)
            assertTrue(triggers.all { it.contains("'$file'") && it.contains("backup_tracking_accounts") })
            assertTrue(triggers.last().contains("OLD.`${fields.first()}`"))
            assertTrue(triggers[1].contains("OLD.`fixtureValue` IS NEW.`fixtureValue`"))
            assertTrue(triggers.none { it.contains("record_sync_") || it.contains("json_array") })
        }
        assertEquals(listOf("settings"), BackupPendingChange("a@example.com", "settings.json", "settings", operation = "UPSERT", generation = 1).stableKey())
    }

    @Test fun preferenceWhitelistMatchesActualHistoricalSettingsSerializer() {
        val fields = VaultUserPreferences().toBackupJson().keys().asSequence().toSet() - "schemaVersion"
        assertEquals(fields, VaultPreferences.BackupFields.keys)
        assertFalse(VaultPreferences.BackupFields.values.any { it.name.contains("google_drive") || it.name.contains("backup_at") || it.name.contains("api_key") })
    }

    @Test fun migrationOnlyAddsBackupTablesAndSeedsUntrustedState() {
        val sql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") sql += args!![0] as String
            null
        } as SupportSQLiteDatabase
        VaultDatabase.MIGRATION_32_33.migrate(db)
        assertEquals(BackupJournalSchema.createStatements, sql)
        assertTrue(sql.all { it.startsWith("CREATE TABLE IF NOT EXISTS backup_") || it.startsWith("INSERT OR IGNORE INTO backup_") })
        assertTrue(sql.none { it.contains("DROP ") || it.contains("ALTER ") || it.contains("DELETE ") || it.contains("UPDATE notes") })
    }

    @Test fun activeBackupRestorePathsAndDisabledPublicationRemainProtected() {
        val root = projectRoot()
        val drive = File(root, "app/src/main/java/com/myvault/app/data/sync/GoogleDriveIncrementalSyncRepository.kt").readText()
        assertTrue(drive.contains("backupRepository.exportMetadataForDriveSync("))
        assertTrue(drive.contains("backupRepository.restoreBackupFromFile("))
        assertFalse(drive.contains("acknowledgeConfirmedDelta") || drive.contains("acceptVerifiedFullCheckpoint") || drive.contains("IncrementalBackupWriter("))
        assertFalse(IncrementalBackupPublicationEnabled)
        val module = File(root, "app/src/main/java/com/myvault/app/di/AppModule.kt").readText()
        assertTrue(module.contains("VaultDatabase.BACKUP_JOURNAL_CALLBACK"))
        assertFalse(module.contains("fallbackToDestructiveMigration"))
    }

    @Test fun exportActualMigrationAndTriggersForDisposableSqliteValidation() {
        val root = projectRoot()
        val schema = JSONObject(File(root, "app/schemas/com.myvault.app.data.local.VaultDatabase/32.json").readText())
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        val triggers = JSONObject()
        BackupRecordKeys.forEach { (file, _) ->
            val entity = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == backupRecordTable(file) }
            val fields = entity.getJSONArray("fields")
            triggers.put(file, JSONArray(BackupJournalSchema.triggerStatements(file, (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") })))
        }
        val queries = JSONObject().put("clock", BackupJournalSql.Clock).put("tick", BackupJournalSql.Tick)
            .put("seedAccount", BackupJournalSql.SeedAccount).put("settingsDirty", BackupJournalSql.SettingsDirty)
            .put("ack", BackupJournalSql.Ack).put("invalidate", BackupJournalSql.Invalidate)
        val destination = File(System.getenv("MYVAULT_JOURNAL_TEST_DIR") ?: "${System.getProperty("java.io.tmpdir")}/myvault-backup-journal-fixtures").apply { mkdirs() }
        File(destination, "journal-sql.json").writeText(JSONObject().put("migration", JSONArray(BackupJournalSchema.createStatements))
            .put("groups", JSONObject(BackupRecordKeys)).put("triggers", triggers).put("queries", queries).put("schema32", schema).toString())
    }

    private fun projectRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java/com/myvault/app/data/local/VaultDatabase.kt").exists() }
    private fun rejects(block: () -> Unit) { try { block() } catch (_: Exception) { return }; fail("Expected safe rejection") }
}
