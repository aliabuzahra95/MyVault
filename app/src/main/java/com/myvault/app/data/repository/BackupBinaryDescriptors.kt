package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

internal const val BackupBinaryReaderCapability = "checkpoint-delta-binaries-v1"

internal data class BackupBinaryDescriptor(
    val attachmentId: String,
    val cloudFileId: String,
    val sha256: String,
    val size: Long,
) {
    fun toJson(): JSONObject = JSONObject().put("attachmentId", attachmentId)
        .put("cloudFileId", cloudFileId).put("sha256", sha256).put("size", size)

    fun validate(): BackupBinaryDescriptor = apply {
        check(attachmentId.isNotBlank() && attachmentId.length <= 256 && attachmentId !in listOf(".", "..") &&
            attachmentId.none { it == '/' || it == '\\' || it.code < 32 }) { "Invalid binary attachment ID." }
        check(cloudFileId.isNotBlank() && cloudFileId.length <= 256) { "Invalid binary Drive object ID." }
        check(sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid binary checksum." }
        check(size in 0..9_007_199_254_740_991L) { "Invalid binary byte count." }
    }

    fun verify(bytes: ByteArray) {
        validate()
        check(bytes.size.toLong() == size && IncrementalBackupFormat.sha256(bytes) == sha256) {
            "Attachment binary verification failed. Checkpoint fallback is forbidden."
        }
    }

    companion object {
        fun parse(json: JSONObject): BackupBinaryDescriptor {
            listOf("attachmentId", "cloudFileId", "sha256").forEach { field ->
                check(json.get(field) is String) { "Binary descriptor identifiers and checksum must be strings." }
            }
            val size = json.get("size")
            check(size is Number && size.toDouble() == size.toLong().toDouble()) { "Invalid binary byte count." }
            return BackupBinaryDescriptor(json.getString("attachmentId"), json.getString("cloudFileId"),
                json.getString("sha256"), size.toLong()).validate()
        }
    }
}

internal fun parseBackupBinaries(delta: JSONObject, changes: List<BackupRecordChange>): List<BackupBinaryDescriptor> {
    if (delta.getInt("version") == 1) {
        check(!delta.has("binaries")) { "Binary descriptors require the new reader capability." }
        return emptyList()
    }
    val array = delta.getJSONArray("binaries")
    check(array.length() <= changes.size) { "Unbound backup binary descriptors." }
    val seen = mutableSetOf<String>()
    return (0 until array.length()).map { index ->
        BackupBinaryDescriptor.parse(array.getJSONObject(index)).also { binary ->
            check(seen.add(binary.attachmentId)) { "Duplicate binary descriptor." }
            val change = changes.singleOrNull { it.file == "attachments.json" && it.key == listOf(binary.attachmentId) }
            check(change != null && !change.deleted) { "Binary descriptor requires its attachment upsert." }
            validateBackupBinaryReference(change.value!!, binary)
        }
    }
}

private fun validateBackupBinaryReference(row: JSONObject, binary: BackupBinaryDescriptor?) {
    val id = row.getString("id")
    val entry = if (row.has("fileEntry")) row.getString("fileEntry") else "files/$id"
    if (entry.isEmpty()) {
        check(binary == null) { "A retained binary cannot silently become a missing file." }
        return
    }
    check(entry == "files/$id" && binary != null && binary.attachmentId == id) { "Attachment has no valid binary descriptor." }
    val size = row.get("sizeBytes")
    check(size is Number && size.toDouble() == size.toLong().toDouble() && size.toLong() == binary.size) {
        "Attachment metadata and binary byte count differ."
    }
}

internal class BackupBinaryResolution(checkpoint: List<BackupBinaryDescriptor>) {
    private val objects = linkedMapOf<String, Pair<String, Long>>()
    private val mappings = linkedMapOf<String, BackupBinaryDescriptor>()

    init {
        checkpoint.forEach { binary ->
            check(mappings.put(binary.attachmentId, binary.validate()) == null) { "Duplicate checkpoint attachment binary." }
            remember(binary)
        }
    }

    private fun remember(binary: BackupBinaryDescriptor) {
        val identity = binary.sha256 to binary.size
        val previous = objects.putIfAbsent(binary.cloudFileId, identity)
        check(previous == null || previous == identity) { "Immutable Drive object has conflicting byte identities." }
    }

    fun apply(changes: List<BackupRecordChange>, binaries: List<BackupBinaryDescriptor>) {
        val replacements = binaries.associateBy { it.attachmentId }
        binaries.forEach(::remember)
        changes.filter { it.file == "attachments.json" }.forEach { change ->
            val id = change.key.single()
            if (change.deleted) mappings.remove(id)
            else {
                val resolved = replacements[id] ?: mappings[id]
                validateBackupBinaryReference(change.value!!, resolved)
                if (resolved != null) mappings[id] = resolved
            }
        }
    }

    fun finish(files: Map<String, String>): List<BackupBinaryDescriptor> {
        val rows = JSONArray(files["attachments.json"] ?: "[]")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            validateBackupBinaryReference(row, mappings[row.getString("id")])
        }
        return mappings.values.toList()
    }
}
