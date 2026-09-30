package com.myvault.app.data.repository

import com.myvault.app.data.local.BackupGraphRestoreSchema
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupGraphRestoreGateTest {
    @Test fun migrationIsOnlyThreeAdditiveTablesAndProductionRoutesStayLegacy() {
        assertEquals(3,BackupGraphRestoreSchema.statements.size)
        assertTrue(BackupGraphRestoreSchema.statements.all { it.startsWith("CREATE TABLE IF NOT EXISTS backup_graph_") })
        assertFalse(BackupGraphTargetedRestoreEnabled)
        assertFalse(BackupGraphPublicationEnabled)
        assertFalse(IncrementalBackupPublicationEnabled)
        val root=File("src/main/java/com/myvault/app")
        val references=root.walkTopDown().filter {it.isFile && it.extension=="kt" && it.name!="InternalBackupGraphRestore.kt"}.filter {it.readText().contains("InternalBackupGraphRestore(")}.toList()
        assertEquals(listOf("GoogleDriveIncrementalSyncRepository.kt"), references.map { it.name })
        val routing = references.single().readText()
        assertTrue(routing.contains("if (BackupGraphTargetedRestoreEnabled)"))
        assertTrue(routing.contains("check(BackupGraphPublicationEnabled && BackupGraphTargetedRestoreEnabled)"))
        assertTrue(routing.contains("backupRepository.restoreBackupFromFile("))
    }
}
