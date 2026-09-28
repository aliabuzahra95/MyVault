package com.myvault.app.data.repository

import com.myvault.app.data.sync.IncrementalBackupTransport
import com.myvault.app.data.sync.IncrementalBackupWriter
import com.myvault.app.data.sync.PublishedDriveBackup
import com.myvault.app.data.sync.coalescedBackupChanges
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class IncrementalBackupCompatibilityTest {
    private class Store(initial: PublishedDriveBackup, val objects: MutableMap<String, ByteArray>) : IncrementalBackupTransport {
        var committed = initial
        var creates = 0
        var failCommit = false
        var loseCommitResponse = false
        val history = mutableListOf<PublishedDriveBackup>()
        override suspend fun readCommitted() = committed
        override suspend fun createDelta(deltaId: String, bytes: ByteArray): String {
            creates++
            return "android-object-$deltaId".also { objects[it] = bytes.copyOf() }
        }
        override suspend fun readObject(id: String) = objects.getValue(id)
        override suspend fun preserve(previous: PublishedDriveBackup) { history += previous }
        override suspend fun commit(previous: PublishedDriveBackup, manifest: String) {
            check(committed == previous)
            if (failCommit) error("simulated publication failure")
            committed = PublishedDriveBackup(previous.id, manifest)
            if (loseCommitResponse) error("simulated lost response")
        }
    }

    @Test fun historicalCheckpointAndExplicitDeletionRules() {
        val checkpoint = mapOf("notes.json" to "[{\"id\":\"n\",\"title\":\"English العربية\",\"deletedAt\":null}]")
        assertEquals(checkpoint, IncrementalBackupFormat.reconstruct(checkpoint, null) { error("No delta download allowed") }.files)
        val trash = BackupRecordChange("notes.json", listOf("n"), JSONArray(checkpoint.getValue("notes.json")).getJSONObject(0).put("deletedAt", 123))
        val delta = IncrementalBackupFormat.createDelta("base", "base", "d1", listOf(trash))
        val manifest = IncrementalBackupFormat.append(JSONObject().put("cloudVersion", 1), delta, "object", 2)
        val result = IncrementalBackupFormat.reconstruct(checkpoint, IncrementalBackupFormat.extension(manifest)) { delta.toString().toByteArray() }
        assertEquals(123, JSONArray(result.files.getValue("notes.json")).getJSONObject(0).getInt("deletedAt"))
        assertTrue(result.permanentDeletions.isEmpty())
        assertTrue(readPermanentBackupDeletions(mapOf("manifest.json" to "{}", "permanent_deletions.json" to "ignored legacy extra")).isEmpty())
    }

    @Test fun IDsAreBoundNeverInferredAndCompositeKeysRemainExact() {
        val deletion = BackupRecordChange("notes.json", listOf("n' OR 1=1 --"))
        val (sql, args) = permanentBackupDeletionSql(deletion)
        assertEquals("DELETE FROM `notes` WHERE `id` = ?", sql)
        assertArrayEquals(arrayOf<Any>("n' OR 1=1 --"), args)
        assertEquals("DELETE FROM `pdf_annotation_segments` WHERE `annotationId` = ? AND `orderIndex` = ?",
            permanentBackupDeletionSql(BackupRecordChange("pdf_annotation_geometry.json", listOf("a", "2"))).first)
        val wrong = BackupRecordChange("notes.json", listOf("wrong"), JSONObject().put("id", "other"))
        assertFails { IncrementalBackupFormat.createDelta("base", "base", "d", listOf(wrong)) }
        assertFails { IncrementalBackupFormat.createDelta("base", "base", "d", listOf(BackupRecordChange("../../notes.json", listOf("n")))) }
        assertFails { IncrementalBackupFormat.createDelta("base", "base", "d", listOf(deletion, deletion)) }
        assertFalse(IncrementalBackupPublicationEnabled)
    }

    @Test fun writerFailureRetryAndZeroChangeUseDisposableTransport() = runBlocking {
        val manifest = JSONObject().put("schemaVersion", 1).put("cloudVersion", 100).put("entries", JSONArray())
        val previous = PublishedDriveBackup("manifest", manifest.toString())
        val store = Store(previous, mutableMapOf())
        val writer = IncrementalBackupWriter(store)
        val changes = coalescedBackupChanges(listOf("H", "He", "Hello").map { text ->
            BackupRecordChange("notes.json", listOf("n"), JSONObject().put("id", "n").put("bodyPlainText", text))
        })
        assertEquals(1, changes.size)
        val delta = IncrementalBackupFormat.createDelta("base", "base", "d1", changes)
        assertFailsSuspend { writer.publish(previous, delta) }
        assertEquals(0, store.creates)
        assertEquals(previous, writer.publishVerifiedDelta(previous, null))
        assertEquals(0, store.creates)
        store.failCommit = true
        assertFailsSuspend { writer.publishVerifiedDelta(previous, delta) }
        assertEquals(previous, store.committed)
        store.failCommit = false
        store.loseCommitResponse = true
        val committed = writer.publishVerifiedDelta(previous, delta)
        assertEquals(2, store.creates) // One retained staging object from the failed attempt.
        assertEquals(committed, writer.publishVerifiedDelta(previous, delta))
        assertEquals(2, store.creates) // Retry after successful commit does not allocate another object.
        assertEquals(previous, store.history.first())
    }

    @Test fun webToAndroidAndAndroidFixtureGeneration() = runBlocking {
        val path = System.getenv("MYVAULT_BACKUP_COMPAT_DIR")
        assumeNotNull(path)
        val directory = File(path!!)
        val legacy = JSONObject(File(directory, "legacy.json").readText())
        val web = JSONObject(File(directory, "web.json").readText())
        fun objects(fixture: JSONObject): MutableMap<String, ByteArray> = fixture.getJSONObject("objects").let { json ->
            json.keys().asSequence().associateWith { json.getString(it).toByteArray(Charsets.UTF_8) }.toMutableMap()
        }
        fun checkpoint(fixture: JSONObject): Map<String, String> {
            val entries = fixture.getJSONObject("manifest").getJSONArray("entries")
            val storage = objects(fixture)
            return buildMap {
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    if (entry.getString("kind") != "metadata") continue
                    val bytes = storage.getValue(entry.getString("cloudFileId"))
                    assertEquals(entry.getLong("size"), bytes.size.toLong())
                    assertEquals(entry.getString("sha256"), IncrementalBackupFormat.sha256(bytes))
                    put(entry.getString("fileName"), bytes.toString(Charsets.UTF_8))
                }
            }
        }
        val oldFiles = checkpoint(legacy)
        val historical = IncrementalBackupFormat.reconstruct(oldFiles, null) { error("Legacy backups have no deltas") }
        assertLogicalFiles(legacy.getJSONObject("expected"), historical.files)
        JSONObject(historical.files.getValue("settings.json")).toValidatedBackupPreferences()
        val historicalNotes = JSONArray(historical.files.getValue("notes.json")).let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).toNoteEntity() }
        }
        val historicalBlocks = JSONArray(historical.files.getValue("blocks.json")).let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).toBlockEntity() }
        }
        // The same production folder decoder used by historical Android restore.
        val folders = JSONArray(historical.files.getValue("folders.json"))
        for (index in 0 until folders.length()) folders.getJSONObject(index).toBackupFolderEntity()

        val webObjects = objects(web)
        val webExtension = IncrementalBackupFormat.extension(web.getJSONObject("manifest"))!!
        val webRestored = IncrementalBackupFormat.reconstruct(checkpoint(web), webExtension) { webObjects.getValue(it.getString("cloudFileId")) }
        assertLogicalFiles(web.getJSONObject("expected"), webRestored.files)
        val decodedNotes = JSONArray(webRestored.files.getValue("notes.json")).let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).toNoteEntity() }
        }
        assertEquals(historicalNotes.filter { it.id != "note-tawakkul" && it.id != "explicitly-deleted-note" },
            decodedNotes.filter { it.id != "note-tawakkul" })
        val decodedBlocks = JSONArray(webRestored.files.getValue("blocks.json")).let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).toBlockEntity() }
        }
        assertEquals(historicalBlocks, decodedBlocks)
        assertEquals(listOf("explicitly-deleted-note"), webRestored.permanentDeletions.single().key)
        assertEquals(2, IncrementalBackupFormat.deltasAfter(webExtension, "web-delta-1")!!.size)
        assertEquals(0, IncrementalBackupFormat.deltasAfter(webExtension, "web-delta-3")!!.size)
        assertNull(IncrementalBackupFormat.deltasAfter(webExtension, "unknown"))
        val corrupt = webObjects.toMutableMap().also { it[webExtension.getJSONArray("deltas").getJSONObject(0).getString("cloudFileId")] = "corrupt".toByteArray() }
        assertFails { IncrementalBackupFormat.reconstruct(checkpoint(web), webExtension) { corrupt.getValue(it.getString("cloudFileId")) } }
        val broken = JSONObject(webExtension.toString()).also { it.getJSONArray("deltas").getJSONObject(1).put("parentId", "wrong") }
        assertFails { IncrementalBackupFormat.descriptors(broken) }

        val store = Store(PublishedDriveBackup("fixture-manifest", legacy.getJSONObject("manifest").toString()), objects(legacy))
        val writer = IncrementalBackupWriter(store)
        val note = JSONArray(oldFiles.getValue("notes.json")).let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("id") == "note-tawakkul" }
        }
        val richBefore = oldFiles.getValue("blocks.json")
        var parent = "checkpoint-100"
        repeat(3) { index ->
            val id = "android-delta-${index + 1}"
            val changes = if (index == 1) listOf(BackupRecordChange("notes.json", listOf("explicitly-deleted-note"))) else
                listOf(BackupRecordChange("notes.json", listOf(note.getString("id")), JSONObject(note.toString()).put("title", "Android title ${index + 1}").put("bodyPlainText", "English العربية — الكليات • concepts.")))
            val delta = IncrementalBackupFormat.createDelta("checkpoint-100", parent, id, changes)
            writer.publishVerifiedDelta(store.committed, delta)
            parent = id
        }
        assertEquals(3, store.creates)
        val androidManifest = JSONObject(store.committed.text)
        val reconstructed = IncrementalBackupFormat.reconstruct(oldFiles, IncrementalBackupFormat.extension(androidManifest)) { store.objects.getValue(it.getString("cloudFileId")) }
        assertEquals(normalized(JSONArray(richBefore)), normalized(JSONArray(reconstructed.files.getValue("blocks.json"))))
        val expected = JSONObject().also { json -> reconstructed.files.forEach { (file, text) -> json.put(file, if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)) } }
        val exportedObjects = JSONObject().also { json -> store.objects.forEach { (id, bytes) -> json.put(id, bytes.toString(Charsets.UTF_8)) } }
        File(directory, "android.json").writeText(JSONObject().put("manifest", androidManifest).put("objects", exportedObjects).put("expected", expected).toString())
        val deletionSql = BackupRecordKeys.map { (file, fields) ->
            val change = BackupRecordChange(file, fields.map { if (it == "orderIndex") "2" else "explicitly-deleted-note" })
            val (sql, args) = permanentBackupDeletionSql(change)
            JSONObject().put("file", file).put("sql", sql).put("args", JSONArray(args.toList()))
        }
        File(directory, "deletion-sql.json").writeText(JSONArray(deletionSql).toString())
    }

    private fun assertLogicalFiles(expected: JSONObject, actual: Map<String, String>) {
        assertEquals(expected.keys().asSequence().toSet(), actual.keys)
        actual.forEach { (file, text) ->
            if (text.trimStart().startsWith("[")) assertEquals(file, normalized(expected.getJSONArray(file)), normalized(JSONArray(text)))
            else assertEquals(file, normalized(expected.getJSONObject(file)), normalized(JSONObject(text)))
        }
    }
    private fun normalized(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().sorted().associateWith { normalized(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { normalized(value.get(it)) }
        else -> value
    }
    private fun assertFails(block: () -> Unit) { try { block() } catch (_: Exception) { return }; fail("Expected rejection") }
    private suspend fun assertFailsSuspend(block: suspend () -> Unit) { try { block() } catch (_: Exception) { return }; fail("Expected rejection") }
}
