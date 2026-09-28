package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.util.Base64

class BackupBinaryCompatibilityTest {
    private val original = ByteArray(4096) { 79 }
    private val replacement = ByteArray(8192) { 82 }
    private fun binary(id: String = "pdf", objectId: String = "replacement", bytes: ByteArray = replacement) =
        BackupBinaryDescriptor(id, objectId, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
    private fun row(id: String = "pdf", size: Long = 8192) = JSONObject().put("id", id)
        .put("sizeBytes", size).put("fileEntry", "files/$id").put("displayName", "English العربية")
    private fun upsert(value: JSONObject) = BackupRecordChange("attachments.json", listOf(value.getString("id")), value)
    private fun base() = mapOf("attachments.json" to JSONArray().put(row(size = 4096)).toString(), "notes.json" to "[{\"id\":\"n\",\"bodyPlainText\":\"العربية English\"}]")
    private fun initial() = JSONObject().put("cloudVersion", 100).put("entries", JSONArray())
    private fun fails(block: () -> Unit) {
        try { block(); fail("Invalid backup must fail closed") } catch (expected: IllegalStateException) { /* Expected validation failure. */ }
    }

    @Test fun replacementAndMetadataOnlyRetentionKeepCheckpointImmutable() {
        val checkpoint = base()
        val before = checkpoint.toMap()
        val d1 = IncrementalBackupFormat.createDelta("b", "b", "d1", listOf(upsert(row())), listOf(binary()))
        val d2 = IncrementalBackupFormat.createDelta("b", "d1", "d2", listOf(upsert(row().put("displayName", "New title"))))
        val first = IncrementalBackupFormat.append(initial(), d1, "o1", 101)
        val manifest = IncrementalBackupFormat.append(first, d2, "o2", 102)
        val result = IncrementalBackupFormat.reconstruct(checkpoint, IncrementalBackupFormat.extension(manifest), listOf(binary(objectId = "old", bytes = original))) {
            (if (it.getString("cloudFileId") == "o1") d1 else d2).toString().toByteArray()
        }
        assertEquals(2, IncrementalBackupFormat.extension(manifest)!!.getInt("version"))
        assertEquals(BackupBinaryReaderCapability, IncrementalBackupFormat.extension(manifest)!!.getString("requiredReader"))
        assertEquals(8192L, result.binaries!!.single().size)
        assertEquals("replacement", result.binaries.single().cloudFileId)
        result.binaries.single().verify(replacement)
        fails { result.binaries.single().verify(original) }
        fails { result.binaries.single().verify(ByteArray(8192) { 88 }) }
        assertEquals(before, checkpoint)
        assertEquals(initial().getJSONArray("entries").toString(), manifest.getJSONArray("entries").toString())
        assertEquals(checkpoint.getValue("notes.json"), result.files.getValue("notes.json"))
        assertEquals("New title", JSONArray(result.files.getValue("attachments.json")).getJSONObject(0).getString("displayName"))
    }

    @Test fun newAttachmentRequiresDescriptorAndSizeChangesRequireReplacement() {
        listOf(row("new", 20), row(size = 8192)).forEach { value ->
            val delta = IncrementalBackupFormat.createDelta("b", "b", "d", listOf(upsert(value)), emptyList())
            val manifest = IncrementalBackupFormat.append(initial(), delta, "object", 101)
            fails { IncrementalBackupFormat.reconstruct(base(), IncrementalBackupFormat.extension(manifest), listOf(binary(objectId = "old", bytes = original))) { delta.toString().toByteArray() } }
        }
    }

    @Test fun onlyExactDeletionRemovesLogicalMapping() {
        val delta = IncrementalBackupFormat.createDelta("b", "b", "d", listOf(BackupRecordChange("attachments.json", listOf("pdf"))), emptyList())
        val manifest = IncrementalBackupFormat.append(initial(), delta, "object", 101)
        val other = binary("unrelated", "unrelated-object", ByteArray(10))
        val result = IncrementalBackupFormat.reconstruct(base(), IncrementalBackupFormat.extension(manifest), listOf(binary(objectId = "old", bytes = original), other)) { delta.toString().toByteArray() }
        assertEquals(listOf(other), result.binaries)
        assertEquals(listOf("pdf"), result.permanentDeletions.single().key)
        assertEquals(0, JSONArray(result.files.getValue("attachments.json")).length())
    }

    @Test fun descriptorsMustBeBoundUniqueSafeAndImmutable() {
        val changes = listOf(upsert(row()))
        fails { IncrementalBackupFormat.createDelta("b", "b", "d", changes, listOf(binary(), binary())) }
        fails { IncrementalBackupFormat.createDelta("b", "b", "d", changes, listOf(binary("other"))) }
        fails { IncrementalBackupFormat.createDelta("b", "b", "d", listOf(BackupRecordChange("attachments.json", listOf("pdf"))), listOf(binary())) }
        fails { binary("../pdf").validate() }
        fails { binary().copy(sha256 = "bad").validate() }
        fails { binary().copy(size = -1).validate() }
        val delta = IncrementalBackupFormat.createDelta("b", "b", "d", changes, listOf(binary(objectId = "old")))
        val manifest = IncrementalBackupFormat.append(initial(), delta, "object", 101)
        fails { IncrementalBackupFormat.reconstruct(base(), IncrementalBackupFormat.extension(manifest), listOf(binary(objectId = "old", bytes = original))) { delta.toString().toByteArray() } }
    }

    @Test fun capabilityCannotBeHiddenAndHistoricalCheckpointStillWorks() {
        assertEquals(base(), IncrementalBackupFormat.reconstruct(base(), null) { error("No download") }.files)
        val delta = IncrementalBackupFormat.createDelta("b", "b", "d", listOf(upsert(row())), listOf(binary()))
        val manifest = IncrementalBackupFormat.append(initial(), delta, "object", 101)
        val extension = IncrementalBackupFormat.extension(manifest)!!
        fails { IncrementalBackupFormat.reconstruct(base(), extension) { delta.toString().toByteArray() } }
        extension.put("version", 1).put("requiredReader", "checkpoint-delta-v1")
        fails { IncrementalBackupFormat.reconstruct(base(), extension) { delta.toString().toByteArray() } }
        fails { IncrementalBackupFormat.parseChanges(JSONObject(delta.toString()).put("version", 1)) }
        extension.put("version", 2).put("requiredReader", "unknown")
        fails { IncrementalBackupFormat.extension(manifest) }
        assertFalse(IncrementalBackupPublicationEnabled)
    }

    @Test fun webToAndroidAndAndroidToWebDisposableBinaryFixtures() {
        val path = System.getenv("MYVAULT_BINARY_COMPAT_DIR")
        assumeNotNull(path)
        val directory = File(path!!)
        fun storage(fixture: JSONObject) = fixture.getJSONObject("objects").let { json ->
            json.keys().asSequence().associateWith { Base64.getDecoder().decode(json.getString(it)) }.toMutableMap()
        }
        fun checkpoint(fixture: JSONObject, objects: Map<String, ByteArray>): Pair<Map<String, String>, List<BackupBinaryDescriptor>> {
            val metadata = linkedMapOf<String, String>()
            val binaries = mutableListOf<BackupBinaryDescriptor>()
            val entries = fixture.getJSONObject("manifest").getJSONArray("entries")
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val bytes = objects.getValue(entry.getString("cloudFileId"))
                assertEquals(entry.getLong("size"), bytes.size.toLong())
                assertEquals(entry.getString("sha256"), IncrementalBackupFormat.sha256(bytes))
                if (entry.getString("kind") == "metadata") metadata[entry.getString("fileName")] = bytes.toString(Charsets.UTF_8)
                else binaries += BackupBinaryDescriptor(entry.getString("backupEntry").removePrefix("files/"), entry.getString("cloudFileId"), entry.getString("sha256"), entry.getLong("size")).validate()
            }
            return metadata to binaries
        }
        val web = JSONObject(File(directory, "web.json").readText())
        val webObjects = storage(web)
        val (files, binaries) = checkpoint(web, webObjects)
        val result = IncrementalBackupFormat.reconstruct(files, IncrementalBackupFormat.extension(web.getJSONObject("manifest")), binaries) { webObjects.getValue(it.getString("cloudFileId")) }
        result.binaries!!.forEach { it.verify(webObjects.getValue(it.cloudFileId)) }
        assertEquals(8192L, result.binaries.first { it.attachmentId == "pdf-aqidah" }.size)
        assertEquals("web-replacement-8192", result.binaries.first { it.attachmentId == "pdf-aqidah" }.cloudFileId)
        assertTrue(result.binaries.any { it.attachmentId == "new-clip" })
        assertFalse(result.binaries.any { it.attachmentId == "delete-me" })
        assertEquals(files.getValue("notes.json"), result.files.getValue("notes.json"))
        assertEquals(files.getValue("blocks.json"), result.files.getValue("blocks.json"))
        val missing = webObjects.toMutableMap().also { it.remove("web-replacement-8192") }
        assertThrows(NoSuchElementException::class.java) {
            result.binaries.forEach { it.verify(missing.getValue(it.cloudFileId)) }
        }
        fails { result.binaries.first { it.attachmentId == "pdf-aqidah" }.verify(original) }

        val legacy = JSONObject(File(directory, "legacy.json").readText())
        val objects = storage(legacy)
        val (baseFiles, baseBinaries) = checkpoint(legacy, objects)
        var manifest = legacy.getJSONObject("manifest")
        val rows = JSONArray(baseFiles.getValue("attachments.json"))
        val originalRow = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("id") == "pdf-aqidah" }
        val newBinary = binary("pdf-aqidah", "android-replacement-8192")
        objects[newBinary.cloudFileId] = replacement
        val newClip = binary("new-clip", "android-new-clip", ByteArray(1024) { 67 })
        val deleteMe = binary("delete-me", "android-delete-me", ByteArray(512) { 68 })
        objects[newClip.cloudFileId] = ByteArray(1024) { 67 }
        objects[deleteMe.cloudFileId] = ByteArray(512) { 68 }
        fun copyRow(id: String, size: Long) = JSONObject(originalRow.toString()).put("id", id).put("sizeBytes", size).put("fileEntry", "files/$id")
        val chain = listOf(
            IncrementalBackupFormat.createDelta("binary-base", "binary-base", "android-binary-1", listOf(upsert(copyRow("pdf-aqidah", 8192))), listOf(newBinary)),
            IncrementalBackupFormat.createDelta("binary-base", "android-binary-1", "android-binary-2", listOf(upsert(copyRow("pdf-aqidah", 8192).put("displayName", "Metadata only العربية")))),
            IncrementalBackupFormat.createDelta("binary-base", "android-binary-2", "android-binary-3", listOf(upsert(copyRow("new-clip", 1024)), upsert(copyRow("delete-me", 512))), listOf(newClip, deleteMe)),
            IncrementalBackupFormat.createDelta("binary-base", "android-binary-3", "android-binary-4", listOf(BackupRecordChange("attachments.json", listOf("delete-me")))),
        )
        chain.forEach { delta ->
            val objectId = "android-object-${delta.getString("deltaId")}"; objects[objectId] = delta.toString().toByteArray()
            manifest = IncrementalBackupFormat.append(manifest, delta, objectId, manifest.getLong("cloudVersion") + 1)
        }
        val android = IncrementalBackupFormat.reconstruct(baseFiles, IncrementalBackupFormat.extension(manifest), baseBinaries) { objects.getValue(it.getString("cloudFileId")) }
        android.binaries!!.forEach { it.verify(objects.getValue(it.cloudFileId)) }
        assertEquals(8192L, android.binaries.first { it.attachmentId == "pdf-aqidah" }.size)
        val exported = JSONObject().also { json -> objects.forEach { (id, bytes) -> json.put(id, Base64.getEncoder().encodeToString(bytes)) } }
        File(directory, "android.json").writeText(JSONObject().put("manifest", manifest).put("objects", exported).toString())
    }
}
