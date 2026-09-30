package com.myvault.app.data.repository

import com.myvault.app.data.local.BackupGraphSchema
import com.myvault.app.data.local.entity.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupGraphPersistenceTest {
    @Test fun migrationIsThreeAdditiveTablesOnly() {
        assertEquals(3,BackupGraphSchema.statements.size)
        assertTrue(BackupGraphSchema.statements.all { it.startsWith("CREATE TABLE IF NOT EXISTS backup_graph_") })
        assertTrue(BackupGraphSchema.statements.none { it.contains("DROP ") || it.contains("ALTER ") || it.contains("DELETE ") || it.contains("UPDATE ") })
    }
    @Test fun localIntentRoundTripsAccountBindingCompositeKeysAndFrozenArabic() {
        val account = BackupTrackingAccount("a@example.com",true,"full-cp","delta-A","cp-file","a".repeat(64),"verified_commit")
        assertEquals(account,BackupGraphIntentCodec.account(BackupGraphIntentCodec.account(account)))
        val binding = BackupGraphBinding("a@example.com","lineage","permission","commit-A","commit-file","b".repeat(64),200,
            "full-cp","cp-file","a".repeat(64),1000,"delta-A",2)
        assertEquals(binding,BackupGraphIntentCodec.binding(BackupGraphIntentCodec.binding(binding)))
        assertNotEquals(binding.commitId,binding.deltaHeadId)
        val text = "English العربية • unchanged"
        val row = JSONObject().put("id","n").put("bodyPlainText",text).put("styleMarks",org.json.JSONArray().put(JSONObject().put("start",0).put("end",7).put("bold",true))).toString()
        val records = listOf(CapturedBackupRecord("notes.json",listOf("n"),"UPSERT",10,row,emptyList()),
            CapturedBackupRecord("knowledge_tag_links.json",listOf("tag:1","note","n:2"),"DELETE",10,null,emptyList()))
        val batch = PendingBackupBatch(CapturedBackupChanges(account,10,2,emptyList()),records,emptyList(),listOf(BackupRecordIdentity("notes.json",listOf("n"))),0)
        val frozen = BackupGraphIntentCodec.batch(batch)
        assertEquals(records,BackupGraphIntentCodec.records(frozen))
        val restoredPayload = requireNotNull(BackupGraphIntentCodec.records(frozen).first().payloadJson)
        assertEquals(text,JSONObject(restoredPayload).getString("bodyPlainText"))
        val p = BackupGraphPublication("a@example.com","op","lineage","permission",10,2,BackupGraphIntentCodec.account(account),
            BackupGraphIntentCodec.binding(binding),frozen,"{}","PREPARED")
        val captured = BackupGraphIntentCodec.snapshot(p)
        assertEquals(listOf("tag:1","note","n:2"),captured.changes.last().stableKey())
    }
    @Test fun productionConnectionRemainsGatedAndLegacyRoutingStaysActive() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it,"app/src/main").isDirectory }
        val main = File(root,"app/src/main/java/com/myvault/app")
        val references = main.walkTopDown().filter { it.extension=="kt" && it.name!="InternalBackupGraphWriter.kt" }.filter { it.readText().contains("InternalBackupGraphWriter(") }.toList()
        assertEquals(listOf("GoogleDriveIncrementalSyncRepository.kt"), references.map { it.name })
        val legacy = File(main,"data/sync/GoogleDriveIncrementalSyncRepository.kt").readText()
        assertTrue(legacy.contains("backupRepository.exportMetadataForDriveSync(metadataDir)"))
        assertTrue(legacy.contains("backupRepository.restoreBackupFromFile("))
        assertTrue(legacy.contains("if (BackupGraphPublicationEnabled)"))
        assertTrue(legacy.contains("check(BackupGraphPublicationEnabled && BackupGraphTargetedRestoreEnabled)"))
        assertTrue(legacy.indexOf("if (BackupGraphPublicationEnabled)") < legacy.indexOf("drive.ensureMyVaultLayout()"))
        assertFalse(BackupGraphPublicationEnabled); assertFalse(IncrementalBackupPublicationEnabled)
    }
    @Test fun schema35PreservesEverySchema34EntityExactly() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it,"app/schemas").isDirectory }
        fun entities(version: Int): Map<String,String> {
            val rows=JSONObject(File(root,"app/schemas/com.myvault.app.data.local.VaultDatabase/$version.json").readText()).getJSONObject("database").getJSONArray("entities")
            return (0 until rows.length()).map(rows::getJSONObject).associate { it.getString("tableName") to it.toString() }
        }
        val before=entities(34); val after=entities(35)
        before.forEach { (name, definition) -> assertEquals(name,definition,after[name]) }
        assertEquals(setOf("backup_graph_bindings","backup_graph_publications","backup_graph_publication_objects"),after.keys-before.keys)
    }
}
