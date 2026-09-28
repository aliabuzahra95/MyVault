package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Reader capability is independent of publication. Do not enable until both clients ship. */
internal const val IncrementalBackupPublicationEnabled = false
internal const val IncrementalBackupField = "incrementalBackup"

internal val BackupRecordKeys = linkedMapOf(
    "folders.json" to listOf("id"), "notes.json" to listOf("id"),
    "blocks.json" to listOf("id"), "tags.json" to listOf("name"),
    "note_tags.json" to listOf("noteId", "tagName"), "note_tables.json" to listOf("id"),
    "note_versions.json" to listOf("id"), "attachments.json" to listOf("id"),
    "folder_sticky_notes.json" to listOf("id"), "courses.json" to listOf("id"),
    "course_concept_cards.json" to listOf("id"), "course_folders.json" to listOf("id"),
    "course_notes.json" to listOf("id"), "course_sticky_notes.json" to listOf("id"),
    "pdf_reading_progress.json" to listOf("attachmentId"), "pdf_annotations.json" to listOf("id"),
    "pdf_annotation_geometry.json" to listOf("annotationId", "orderIndex"),
    "source_backlinks.json" to listOf("id"), "knowledge_tags.json" to listOf("id"),
    "knowledge_tag_links.json" to listOf("tagId", "targetType", "targetId"),
)

internal fun backupRecordTable(file: String): String {
    check(file in BackupRecordKeys)
    return if (file == "pdf_annotation_geometry.json") "pdf_annotation_segments" else file.removeSuffix(".json")
}

internal data class BackupRecordChange(
    val file: String,
    val key: List<String>,
    val value: JSONObject? = null,
) {
    val deleted: Boolean get() = value == null
    fun toJson(): JSONObject = JSONObject().put("file", file).put("key", JSONArray(key))
        .put("operation", if (deleted) "delete" else "upsert")
        .also { if (value != null) it.put("value", value) }
}

internal data class ReconstructedBackup(
    val files: Map<String, String>,
    val permanentDeletions: List<BackupRecordChange>,
    val headId: String?,
)

/** Checkpoint entries stay immutable. Each descriptor identifies one verified change object. */
internal object IncrementalBackupFormat {
    private const val MaxDeltas = 4096
    private const val MaxChanges = 100_000
    private const val MaxDeltaBytes = 16 * 1024 * 1024

    fun extension(manifest: JSONObject): JSONObject? {
        if (!manifest.has(IncrementalBackupField)) return null
        val extension = manifest.getJSONObject(IncrementalBackupField)
        check(extension.getInt("version") == 1) { "Unsupported incremental backup version." }
        check(extension.getString("requiredReader") == "checkpoint-delta-v1") { "Unsupported backup reader capability." }
        requireId(extension.getString("checkpointId"))
        requireId(extension.getString("headId"))
        descriptors(extension)
        return extension
    }

    fun descriptors(extension: JSONObject): List<JSONObject> {
        val array = extension.getJSONArray("deltas")
        check(array.length() <= MaxDeltas) { "Backup needs a new checkpoint before more changes can be published." }
        var parent = extension.getString("checkpointId")
        val seen = mutableSetOf(parent)
        val result = (0 until array.length()).map { index ->
            array.getJSONObject(index).also { descriptor ->
                val id = descriptor.getString("deltaId")
                requireId(id)
                check(seen.add(id) && descriptor.getString("parentId") == parent) { "Broken or duplicate backup delta ancestry." }
                check(descriptor.getString("cloudFileId").isNotBlank()) { "Backup delta has no Drive object ID." }
                check(descriptor.getLong("size") in 1..MaxDeltaBytes.toLong()) { "Invalid backup delta size." }
                check(descriptor.getString("sha256").matches(Regex("[a-f0-9]{64}"))) { "Invalid backup delta checksum." }
                parent = id
            }
        }
        check(parent == extension.getString("headId")) { "Backup head does not match its delta chain." }
        return result
    }

    fun deltasAfter(extension: JSONObject, appliedHead: String): List<JSONObject>? {
        val chain = descriptors(extension)
        if (appliedHead == extension.getString("checkpointId")) return chain
        val index = chain.indexOfFirst { it.getString("deltaId") == appliedHead }
        return if (index < 0) null else chain.drop(index + 1)
    }

    fun createDelta(checkpointId: String, parentId: String, deltaId: String, changes: List<BackupRecordChange>): JSONObject {
        listOf(checkpointId, parentId, deltaId).forEach(::requireId)
        check(deltaId != checkpointId && deltaId != parentId) { "Delta ID must be new." }
        check(changes.isNotEmpty() && changes.size <= MaxChanges) { "A delta must contain actual changes." }
        val result = JSONObject().put("format", "myvault-backup-delta").put("version", 1)
            .put("checkpointId", checkpointId).put("parentId", parentId).put("deltaId", deltaId)
            .put("changes", JSONArray(changes.map { it.toJson() }))
        parseChanges(result)
        check(result.toString().toByteArray(Charsets.UTF_8).size <= MaxDeltaBytes) { "Backup delta is too large." }
        return result
    }

    fun append(manifest: JSONObject, delta: JSONObject, cloudFileId: String, cloudVersion: Long): JSONObject {
        val previous = extension(manifest)
        val checkpoint = previous?.getString("checkpointId") ?: delta.getString("checkpointId")
        val parent = previous?.getString("headId") ?: checkpoint
        check(delta.getString("checkpointId") == checkpoint && delta.getString("parentId") == parent) { "Delta does not extend this backup." }
        check(cloudVersion > manifest.getLong("cloudVersion")) { "Backup version must advance." }
        parseChanges(delta)
        val bytes = delta.toString().toByteArray(Charsets.UTF_8)
        val chain = JSONArray(previous?.getJSONArray("deltas")?.toString() ?: "[]")
        chain.put(JSONObject().put("deltaId", delta.getString("deltaId")).put("parentId", parent)
            .put("cloudFileId", cloudFileId).put("size", bytes.size).put("sha256", sha256(bytes)))
        return JSONObject(manifest.toString()).put("cloudVersion", cloudVersion)
            .put(IncrementalBackupField, JSONObject().put("version", 1).put("requiredReader", "checkpoint-delta-v1")
                .put("checkpointId", checkpoint).put("headId", delta.getString("deltaId")).put("deltas", chain))
            .also { extension(it) }
    }

    fun reconstruct(checkpointFiles: Map<String, String>, extension: JSONObject?, load: (JSONObject) -> ByteArray): ReconstructedBackup {
        if (extension == null) return ReconstructedBackup(checkpointFiles.toMap(), emptyList(), null)
        val files = checkpointFiles.toMutableMap()
        val deletions = linkedMapOf<String, BackupRecordChange>()
        descriptors(extension).forEach { descriptor ->
            val bytes = load(descriptor)
            check(bytes.size.toLong() == descriptor.getLong("size") && sha256(bytes) == descriptor.getString("sha256")) {
                "Backup delta byte verification failed. Nothing may be restored."
            }
            val delta = JSONObject(bytes.toString(Charsets.UTF_8))
            check(delta.getString("checkpointId") == extension.getString("checkpointId") &&
                delta.getString("parentId") == descriptor.getString("parentId") &&
                delta.getString("deltaId") == descriptor.getString("deltaId")) { "Delta content does not match its committed ancestry." }
            parseChanges(delta).groupBy { it.file }.forEach { (file, changes) ->
                if (file == "settings.json") {
                    files[file] = changes.single().value!!.toString()
                } else {
                    val rows = JSONArray(files[file] ?: "[]")
                    val indexed = linkedMapOf<String, JSONObject>()
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        val key = key(file, row)
                        check(indexed.put(keyToken(key), row) == null) { "Duplicate checkpoint record ID." }
                    }
                    changes.forEach { change ->
                        val token = "${change.file}:${keyToken(change.key)}"
                        if (change.deleted) {
                            indexed.remove(keyToken(change.key))
                            deletions[token] = change
                        } else {
                            indexed[keyToken(change.key)] = change.value!!
                            deletions.remove(token)
                        }
                    }
                    files[file] = JSONArray(indexed.values.toList()).toString()
                }
            }
        }
        return ReconstructedBackup(files, deletions.values.toList(), extension.getString("headId"))
    }

    fun parseChanges(delta: JSONObject): List<BackupRecordChange> {
        check(delta.getString("format") == "myvault-backup-delta" && delta.getInt("version") == 1) { "Unsupported backup delta." }
        listOf("checkpointId", "parentId", "deltaId").forEach { requireId(delta.getString(it)) }
        check(delta.getString("deltaId") != delta.getString("parentId") && delta.getString("deltaId") != delta.getString("checkpointId")) { "Delta ID must be new." }
        val changes = delta.getJSONArray("changes")
        check(changes.length() in 1..MaxChanges) { "Invalid backup change count." }
        val seen = mutableSetOf<String>()
        return (0 until changes.length()).map { index ->
            val json = changes.getJSONObject(index)
            val file = json.getString("file")
            val fields = if (file == "settings.json") listOf("settings") else BackupRecordKeys[file]
                ?: error("Unsupported backup record type: $file")
            val rawKey = json.getJSONArray("key")
            check(rawKey.length() == fields.size) { "Invalid backup record key." }
            val key = (0 until rawKey.length()).map { i ->
                check(rawKey.get(i) is String) { "Backup keys must be strings." }
                rawKey.getString(i).also { check(it.isNotBlank()) { "Empty backup record key." } }
            }
            check(seen.add("$file:${keyToken(key)}")) { "Duplicate changes for the same backup record." }
            when (json.getString("operation")) {
                "delete" -> {
                    check(file != "settings.json" && !json.has("value")) { "Invalid permanent deletion." }
                    BackupRecordChange(file, key)
                }
                "upsert" -> BackupRecordChange(file, key, json.getJSONObject("value")).also {
                    check(key == key(file, it.value!!)) { "Backup payload ID differs from its change ID." }
                }
                else -> error("Unsupported backup change operation.")
            }
        }
    }

    fun key(file: String, row: JSONObject): List<String> = if (file == "settings.json") listOf("settings") else
        BackupRecordKeys.getValue(file).map { field ->
            val value = row.get(field)
            check(value is String || (field == "orderIndex" && value is Number && value.toDouble() == value.toLong().toDouble())) { "Invalid stable backup record ID." }
            (if (value is Number) value.toLong().toString() else value.toString()).also { check(it.isNotBlank()) { "Empty backup record ID." } }
        }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun keyToken(key: List<String>) = JSONArray(key).toString()
    private fun requireId(id: String) { check(id.isNotBlank() && id.length <= 256) { "Invalid backup state ID." } }
}
