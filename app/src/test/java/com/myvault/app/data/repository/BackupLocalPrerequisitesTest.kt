package com.myvault.app.data.repository

import androidx.sqlite.db.SupportSQLiteDatabase
import com.myvault.app.data.local.BackupBinarySchema
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.preferences.VaultUserPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

class BackupLocalPrerequisitesTest {
    private val temporary = mutableListOf<File>()
    @After fun cleanFixtures() { temporary.forEach { it.deleteRecursively() } }
    private fun temp(): File = Files.createTempDirectory("myvault-baseline-disposable-").toFile().also { temporary += it }
    private fun root(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java/com/myvault/app/data/local/VaultDatabase.kt").exists() }
    private fun rejects(block: () -> Unit) { try { block() } catch (_: Exception) { return }; fail("Expected safe rejection") }

    private data class Fixture(val prepared: PreparedBackupBaseline, val manifest: ByteArray, val receipts: List<VerifiedRemoteBackupObject>)
    private fun fixture(): Fixture {
        val directory = temp()
        val binary = File(directory, "binary").apply { writeText("PDF العربية bytes") }
        val digest = binary.inputStream().use { fingerprintBackupBytes(it) }
        val attachmentJson = JSONArray().put(JSONObject().put("id", "pdf").put("sizeBytes", digest.size).put("fileEntry", "files/pdf")).toString()
        val objects = (BackupRecordKeys.keys + setOf("settings.json", "manifest.json")).map { name ->
            val bytes = (if (name == "attachments.json") attachmentJson else if (name == "settings.json") VaultUserPreferences().toBackupJson().toString() else if (name == "manifest.json") "{\"format\":\"myvault-backup\",\"version\":1}" else "[]").toByteArray()
            val file = File(directory, name).apply { writeBytes(bytes) }
            PreparedBaselineObject("metadata/$name", name, "metadata", file, bytes.size.toLong(), IncrementalBackupFormat.sha256(bytes))
        }.toMutableList()
        objects += PreparedBaselineObject("files/pdf", "files/pdf", "file", binary, digest.size, digest.sha256, "pdf")
        val prepared = PreparedBackupBaseline("fixture-preparation", CapturedBackupChanges(BackupTrackingAccount("a@example.com"), 10, 2, emptyList()), directory, objects)
        val entries = objects.mapIndexed { index, item -> JSONObject().put("path", item.path).put("backupEntry", item.backupEntry)
            .put("kind", item.kind).put("size", item.byteSize).put("sha256", item.sha256).put("cloudFileId", "remote-$index") }
        val manifest = JSONObject().put("schemaVersion", 1).put("storage", "google-drive-api").put("cloudVersion", 123L).put("entries", JSONArray(entries)).toString().toByteArray()
        val receipts = objects.mapIndexed { index, item -> item.file.inputStream().use { VerifiedRemoteBackupObject.read("a@example.com", "remote-$index", it) } }
        return Fixture(prepared, manifest, receipts)
    }
    private fun verify(f: Fixture) = VerifiedBackupBaseline.verify(f.prepared, " A@EXAMPLE.COM ", "manifest-id", f.manifest, f.manifest, f.receipts)

    @Test fun freshAndExistingAccountCannotTrustATimestamp() {
        assertFalse(BackupTrackingAccount("a@example.com").trusted)
        val f = fixture()
        val timestamp = "{\"lastBackupAt\":123456}".toByteArray()
        rejects { VerifiedBackupBaseline.verify(f.prepared, "a@example.com", "manifest", timestamp, timestamp, emptyList()) }
    }
    @Test fun verifiedFullBaselineBindsExactIdentityAndAccount() {
        val f = fixture(); val proof = verify(f)
        assertEquals("full-${IncrementalBackupFormat.sha256(f.manifest)}", proof.commit.checkpointId)
        assertEquals(proof.commit.checkpointId, proof.commit.headId)
        assertEquals("manifest-id", proof.commit.manifestId)
        assertEquals(IncrementalBackupFormat.sha256(f.manifest), proof.commit.manifestSha256)
        assertEquals("a@example.com", proof.binaries.single().accountScope)
        assertEquals("pdf", proof.binaries.single().attachmentId)
    }
    @Test fun laterMutationSurvivesBaselineAcknowledgementContract() {
        val f = fixture(); val proof = verify(f)
        validateBackupAcknowledgement(f.prepared.journal, proof.commit, f.prepared.journal.account, BackupJournalState(generation = 11, originEpoch = 2), true)
        assertEquals(10L, proof.commit.capturedGeneration)
    }
    @Test fun wrongManifestReadbackCannotEstablishTrust() {
        val f = fixture()
        rejects { VerifiedBackupBaseline.verify(f.prepared, "a@example.com", "manifest", f.manifest, f.manifest + byteArrayOf(32), f.receipts) }
    }
    @Test fun partialCheckpointCannotEstablishTrust() {
        val f = fixture()
        rejects { VerifiedBackupBaseline.verify(f.prepared.copy(objects = f.prepared.objects.drop(1)), "a@example.com", "manifest", f.manifest, f.manifest, f.receipts) }
    }
    @Test fun corruptRemoteObjectCannotEstablishTrust() {
        val f = fixture()
        val wrong = VerifiedRemoteBackupObject.read("a@example.com", f.receipts.first().fileId, "wrong".byteInputStream())
        rejects { VerifiedBackupBaseline.verify(f.prepared, "a@example.com", "manifest", f.manifest, f.manifest, listOf(wrong) + f.receipts.drop(1)) }
    }
    @Test fun accountBCannotAcknowledgeAccountA() {
        val f = fixture()
        rejects { VerifiedBackupBaseline.verify(f.prepared, "b@example.com", "manifest", f.manifest, f.manifest, f.receipts) }
        val other = f.prepared.objects.mapIndexed { index, obj -> obj.file.inputStream().use { VerifiedRemoteBackupObject.read("b@example.com", "remote-$index", it) } }
        rejects { VerifiedBackupBaseline.verify(f.prepared, "a@example.com", "manifest", f.manifest, f.manifest, other) }
    }
    @Test fun restoreEpochInvalidatesPreparedBaseline() {
        val f = fixture()
        rejects { validateBackupAcknowledgement(f.prepared.journal, verify(f).commit, f.prepared.journal.account, BackupJournalState(generation = 11, originEpoch = 3), true) }
    }
    @Test fun failedVerificationDoesNotMutateCapturedState() {
        val f = fixture(); val before = f.prepared.journal
        rejects { VerifiedBackupBaseline.verify(f.prepared, "a@example.com", "manifest", f.manifest, f.manifest, emptyList()) }
        assertEquals(before, f.prepared.journal); assertFalse(before.account.trusted)
    }
    @Test fun streamingSha256CopiesExactBytesIncludingArabic() {
        val bytes = "abc العربية".toByteArray()
        val output = ByteArrayOutputStream()
        val digest = fingerprintBackupBytes(bytes.inputStream(), output)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), digest.size)
        assertEquals(IncrementalBackupFormat.sha256(bytes), digest.sha256)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fingerprintBackupBytes("abc".byteInputStream()).sha256)
    }
    @Test fun unchangedFingerprintIsReusedWithoutReadingAFile() {
        val row = JSONObject().put("id", "pdf").put("localPath", "/not/read/library/pdf").put("sizeBytes", 3)
        val known = BackupBinaryFingerprint("pdf", row.getString("localPath"), 3, "a".repeat(64), "VERIFIED", 1)
        assertSame(known, captureBinaryFingerprint(row, known))
    }
    @Test fun sameLogicalIdWithNewBytesHasDifferentContentIdentity() {
        val old = fingerprintBackupBytes("old".byteInputStream()); val changed = fingerprintBackupBytes("new".byteInputStream())
        assertEquals(old.size, changed.size); assertNotEquals(old.sha256, changed.sha256)
        val a = CapturedBackupBinary("same-id", "/library/same", old.size, old.sha256, "VERIFIED")
        val b = a.copy(sha256 = changed.sha256)
        assertEquals(a.attachmentId, b.attachmentId); assertNotEquals(a.sha256, b.sha256)
    }
    @Test fun preExistingOrChangedPathIsExplicitlyUnknown() {
        val row = JSONObject().put("id", "pdf").put("localPath", "/library/new").put("sizeBytes", 3)
        assertEquals("UNKNOWN", captureBinaryFingerprint(row, null).status)
        assertNull(captureBinaryFingerprint(row, null).sha256)
        val stale = BackupBinaryFingerprint("pdf", "/library/old", 3, "a".repeat(64), "VERIFIED", 1)
        assertEquals("UNKNOWN", captureBinaryFingerprint(row, stale).status)
    }
    @Test fun remoteBinaryReuseRequiresOwnTrustedCheckpointAndExactBytes() {
        val state = BackupTrackingAccount("a@example.com", true, "checkpoint")
        val hash = "a".repeat(64)
        val fp = BackupBinaryFingerprint("pdf", "/library/pdf", 3, hash, "VERIFIED", 1)
        val ref = BackupBinaryReference("a@example.com", "pdf", "checkpoint", "head", "remote", 3, hash)
        assertEquals("remote", reusableBackupBinary(state, fp, ref))
        assertNull(reusableBackupBinary(state.copy(trusted = false), fp, ref))
        assertNull(reusableBackupBinary(state, fp, ref.copy(accountScope = "b@example.com")))
        assertNull(reusableBackupBinary(state, fp.copy(sha256 = "b".repeat(64)), ref))
        assertNull(reusableBackupBinary(state, fp.copy(status = "DELETED"), ref))
    }
    @Test fun cacheAndUnownedFilesAreNotDurableBackupBinaries() {
        val files = File(temp(), "files")
        assertTrue(isDurableBackupBinary(files, File(files, "library/root/pdf")))
        assertTrue(isDurableBackupBinary(files, File(files, "attachments/n/clip.png")))
        assertFalse(isDurableBackupBinary(files, File(files, "note_narration_cache/audio")))
        assertFalse(isDurableBackupBinary(files, File(files, "library/../../cache/preview")))
    }
    @Test fun attachmentAndAnnotationDependenciesResolveToStableBinaryIdentity() {
        assertEquals(listOf(BackupRecordIdentity("attachments.json", listOf("pdf"))), backupRecordDependencies("pdf_annotations.json", JSONObject().put("attachmentId", "pdf")))
        assertEquals(listOf(BackupRecordIdentity("pdf_annotations.json", listOf("annotation"))), backupRecordDependencies("pdf_annotation_geometry.json", JSONObject().put("annotationId", "annotation")))
        assertEquals(listOf(BackupRecordIdentity("tags.json", listOf("null"))), backupRecordDependencies("note_tags.json", JSONObject().put("tagName", "null")))
        assertTrue(backupRecordDependencies("attachments.json", JSONObject().put("noteId", JSONObject.NULL)).isEmpty())
    }
    @Test fun deletedRowNeedsNoPayloadAndCompositeKeyRemainsSerializable() {
        val record = CapturedBackupRecord("pdf_annotation_geometry.json", listOf("exact:id", "2"), "DELETE", 10, null, emptyList())
        val change = record.protocolChange()
        assertTrue(change.deleted); assertEquals(listOf("exact:id", "2"), change.key)
        assertFalse(change.toJson().has("value"))
    }
    @Test fun settingsPayloadUsesOnlyExistingBackupSerializer() {
        val text = VaultUserPreferences().toBackupJson().toString()
        val record = CapturedBackupRecord("settings.json", listOf("settings"), "UPSERT", 1, text, emptyList())
        assertEquals(JSONObject(text).toString(), record.protocolChange().value!!.toString())
        assertFalse(text.contains("googleDriveAccountEmail"))
    }
    @Test fun allGroupQueriesAreIndexedStableKeyReadsNotInventoryScans() {
        BackupRecordKeys.forEach { (group, keys) ->
            val query = pendingBackupRecordSql(group)
            assertTrue(query.startsWith("SELECT * FROM `${backupRecordTable(group)}` WHERE "))
            assertEquals(keys.size, query.count { it == '?' })
            keys.forEach { assertTrue(query.contains("`$it` = ?")) }
        }
    }
    @Test fun captureHasTypedAdapterForEveryActualProtocolGroup() {
        val source = File(root(), "app/src/main/java/com/myvault/app/data/repository/PendingBackupCapture.kt").readText()
        BackupRecordKeys.keys.forEach { assertTrue(it, source.contains("\"$it\" ->")) }
        assertFalse(source.contains("getAll(") || source.contains("inputStream()") || source.contains("listFiles()") || source.contains("fingerprintBackupBytes("))
    }
    @Test fun migrationIsTwoAdditiveLocalTablesOnly() {
        val sql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") sql += args!![0] as String
            null
        } as SupportSQLiteDatabase
        VaultDatabase.MIGRATION_33_34.migrate(db)
        assertEquals(BackupBinarySchema.createStatements, sql)
        assertEquals(2, sql.size); assertTrue(sql.all { it.startsWith("CREATE TABLE IF NOT EXISTS backup_binary_") })
    }
    @Test fun schema34PreservesEveryExistingSchema33Entity() {
        fun entities(version: Int): Map<String, String> {
            val rows = JSONObject(File(root(), "app/schemas/com.myvault.app.data.local.VaultDatabase/$version.json").readText()).getJSONObject("database").getJSONArray("entities")
            return (0 until rows.length()).associate { rows.getJSONObject(it).getString("tableName") to rows.getJSONObject(it).toString() }
        }
        val before = entities(33); val after = entities(34)
        before.forEach { (name, definition) -> assertEquals(name, definition, after[name]) }
        assertEquals(setOf("backup_binary_fingerprints", "backup_binary_references"), after.keys - before.keys)
    }
    @Test fun binaryInvalidationNeverTurnsTrashIntoPermanentDeletion() {
        assertEquals(3, BackupBinarySchema.triggers.size)
        assertTrue(BackupBinarySchema.triggers[0].contains("'UNKNOWN'"))
        assertFalse(BackupBinarySchema.triggers[1].contains("deletedAt"))
        assertTrue(BackupBinarySchema.triggers[2].contains("AFTER DELETE ON attachments"))
        assertFalse(BackupBinarySchema.triggers.any { it.contains("cloudFileId") })
    }
    @Test fun graphWriterRouteEnabledWhileLegacyCompatibilityRemains() {
        assertFalse(IncrementalBackupPublicationEnabled)
        assertTrue(BackupGraphPublicationEnabled)
        assertTrue(BackupGraphTargetedRestoreEnabled)
        val drive = File(root(), "app/src/main/java/com/myvault/app/data/sync/GoogleDriveIncrementalSyncRepository.kt").readText()
        assertTrue(drive.contains("backupRepository.exportMetadataForDriveSync(")); assertTrue(drive.contains("backupRepository.restoreBackupFromFile("))
        assertTrue(drive.contains("if (BackupGraphPublicationEnabled)"))
        assertTrue(drive.contains("if (BackupGraphTargetedRestoreEnabled)"))
        assertTrue(drive.contains("BackupGraphPublicationEnabled && BackupGraphTargetedRestoreEnabled"))
        assertFalse(drive.contains("establishVerifiedBaseline"))
    }
    @Test fun exportActualSqlAndVerifiedProofForDisposableIntegrationChecks() {
        val f = fixture(); val proof = verify(f)
        val output = File(System.getenv("MYVAULT_JOURNAL_TEST_DIR") ?: "${System.getProperty("java.io.tmpdir")}/myvault-backup-journal-fixtures").apply { mkdirs() }
        File(output, "local-prerequisites.json").writeText(JSONObject()
            .put("schema33", JSONObject(File(root(), "app/schemas/com.myvault.app.data.local.VaultDatabase/33.json").readText()))
            .put("migration", JSONArray(BackupBinarySchema.createStatements)).put("binaryTriggers", JSONArray(BackupBinarySchema.triggers))
            .put("queries", JSONObject(BackupRecordKeys.keys.associateWith(::pendingBackupRecordSql)))
            .put("proof", JSONObject().put("account", proof.commit.accountEmail).put("checkpoint", proof.commit.checkpointId).put("head", proof.commit.headId)
                .put("manifestId", proof.commit.manifestId).put("manifestHash", proof.commit.manifestSha256).put("generation", proof.commit.capturedGeneration)
                .put("binaryId", proof.binaries.single().attachmentId).put("binaryHash", proof.binaries.single().sha256).put("binarySize", proof.binaries.single().byteSize)
                .put("cloudFileId", proof.binaries.single().cloudFileId)).toString())
    }
}
