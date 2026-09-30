package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

/** One-time comparison of a verified graph tip with an explicitly authoritative frozen Vault. */
internal data class BackupGraphTransition(
    val records: List<CapturedBackupRecord>,
    val binaries: List<CapturedBackupBinary>,
    val oldBinaryReferences: List<BackupBinaryDescriptor>,
) {
    val upserts get() = records.count { it.operation == "UPSERT" }
    val deletes get() = records.count { it.operation == "DELETE" }
    val uploads get() = binaries.count { it.reusableCloudFileId == null }
}

internal suspend fun verifiedGraphSnapshot(store: DisposableGraphObjectStore, graph: BackupGraph): ReconstructedBackup {
    val plan = graph.plan()
    check(plan.status == GraphStatus.SINGLE_TIP && plan.checkpoint != null)
    suspend fun bytes(ref: GraphObjectRef): ByteArray {
        val value = store.read(ref.cloudFileId)?.use { it.readBytes() } ?: error("Graph object is missing.")
        BackupGraphProtocol.verify(ref, value)
        return value
    }
    suspend fun verifyBinary(binary: BackupBinaryDescriptor) {
        val digest = store.read(binary.cloudFileId)?.use { fingerprintBackupBytes(it) } ?: error("Graph binary is missing.")
        check(digest.sha256 == binary.sha256 && digest.size == binary.size) { "Graph binary verification failed." }
    }
    val manifest = JSONObject(BackupGraphProtocol.utf8(bytes(plan.checkpoint.objectRef)))
    check(manifest.getInt("schemaVersion") == 1 && manifest.getString("storage") == "google-drive-api" && !manifest.has(IncrementalBackupField))
    val checkpointFiles = linkedMapOf<String, String>()
    val checkpointBinaries = mutableListOf<BackupBinaryDescriptor>()
    for (entry in BackupGraphIntentCodec.array(manifest.getJSONArray("entries"))) {
        val ref = GraphObjectRef(entry.getString("cloudFileId"), entry.getString("sha256"), entry.getLong("size"))
        when (entry.getString("kind")) {
            "metadata" -> {
                val group = entry.getString("fileName")
                check(group in BackupRecordKeys || group in listOf("settings.json", "manifest.json"))
                check(checkpointFiles.put(group, BackupGraphProtocol.utf8(bytes(ref))) == null)
            }
            "file" -> {
                val entryName = entry.getString("backupEntry")
                check(entryName.startsWith("files/"))
                checkpointBinaries += BackupBinaryDescriptor(entryName.removePrefix("files/"), ref.cloudFileId, ref.sha256, ref.size).validate()
            }
            else -> error("Unknown graph checkpoint entry.")
        }
    }
    check(checkpointFiles.keys.containsAll(BackupRecordKeys.keys)) { "Incomplete graph checkpoint." }
    val deltas = linkedMapOf<String, ByteArray>()
    val descriptors = JSONArray()
    var previous = plan.checkpoint.checkpointId
    for (delta in plan.deltas) {
        val raw = bytes(delta.objectRef)
        val value = JSONObject(BackupGraphProtocol.utf8(raw))
        check(value.getString("checkpointId") == plan.checkpoint.checkpointId && value.getString("parentId") == previous && value.getString("deltaId") == delta.deltaId)
        val commit = plan.commits.single { it.delta == delta }
        check(value.getInt("version") != 2 || BackupBinaryReaderCapability in commit.requiredReaders)
        val changes = IncrementalBackupFormat.parseChanges(value)
        for (binary in parseBackupBinaries(value, changes)) verifyBinary(binary)
        deltas[delta.objectRef.cloudFileId] = raw
        descriptors.put(JSONObject().put("deltaId", delta.deltaId).put("parentId", previous)
            .put("cloudFileId", delta.objectRef.cloudFileId).put("sha256", delta.objectRef.sha256).put("size", delta.objectRef.size))
        previous = delta.deltaId
    }
    checkpointBinaries.forEach { verifyBinary(it) }
    val extension = JSONObject().put("version", 2).put("requiredReader", BackupBinaryReaderCapability)
        .put("checkpointId", plan.checkpoint.checkpointId).put("headId", previous).put("deltas", descriptors)
    return IncrementalBackupFormat.reconstruct(checkpointFiles, extension, checkpointBinaries) { descriptor ->
        deltas.getValue(descriptor.getString("cloudFileId"))
    }
}

internal fun calculateGraphTransition(old: ReconstructedBackup, current: PreparedBackupBaseline): BackupGraphTransition {
    current.objects.forEach { staged ->
        val fingerprint = staged.file.inputStream().use { fingerprintBackupBytes(it) }
        check(fingerprint.sha256 == staged.sha256 && fingerprint.size == staged.byteSize) {
            "Frozen transition snapshot changed or is incomplete."
        }
    }
    val records = mutableListOf<CapturedBackupRecord>()
    val generation = current.journal.generation
    val oldBinaries = old.binaries.orEmpty().associateBy { it.attachmentId }
    val files = current.objects.filter { it.kind == "file" }.associateBy { it.attachmentId }
    val binaries = mutableListOf<CapturedBackupBinary>()
    for (group in BackupRecordKeys.keys) {
        fun rows(text: String): Map<List<String>, JSONObject> {
            val result = linkedMapOf<List<String>, JSONObject>()
            for (row in BackupGraphIntentCodec.array(JSONArray(text))) {
                val key = IncrementalBackupFormat.key(group, row)
                check(result.put(key, row) == null) { "Duplicate stable record key." }
            }
            return result
        }
        val before = rows(old.files[group] ?: error("Unverified old graph group: $group"))
        val after = rows(current.objects.single { it.backupEntry == group }.file.readText())
        for ((key, row) in after) {
            val prior = before[key]
            val binaryChanged = if (group == "attachments.json") {
                val id = key.single()
                val file = files[id] ?: error("Current attachment binary is missing.")
                val previous = oldBinaries[id]
                previous == null || previous.sha256 != file.sha256 || previous.size != file.byteSize
            } else false
            if (prior == null || !sameBackupJson(prior, row) || binaryChanged) {
                records += CapturedBackupRecord(group, key, "UPSERT", generation, row.toString(), backupRecordDependencies(group, row))
                if (group == "attachments.json") {
                    val id = key.single()
                    val file = files.getValue(id)
                    val previous = oldBinaries[id]?.takeIf { it.sha256 == file.sha256 && it.size == file.byteSize }
                    binaries += CapturedBackupBinary(id, file.file.absolutePath, file.byteSize, file.sha256, "VERIFIED", previous?.cloudFileId)
                }
            }
        }
        for (key in before.keys - after.keys) records += CapturedBackupRecord(group, key, "DELETE", generation, null, emptyList())
    }
    val oldSettings = JSONObject(old.files["settings.json"] ?: error("Old graph settings are missing."))
    val newSettings = JSONObject(current.objects.single { it.backupEntry == "settings.json" }.file.readText())
    if (!sameBackupJson(oldSettings, newSettings)) records += CapturedBackupRecord("settings.json", listOf("settings"), "UPSERT", generation, newSettings.toString(), emptyList())
    check(files.keys == BackupGraphIntentCodec.array(JSONArray(current.objects.single { it.backupEntry == "attachments.json" }.file.readText()))
        .map { it.getString("id") }.toSet()) { "Current attachment inventory is incomplete." }
    return BackupGraphTransition(records, binaries, oldBinaries.values.toList())
}

internal fun sameBackupJson(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { sameBackupJson(a.get(it), b.get(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameBackupJson(a.get(it), b.get(it)) }
    else -> a == b
}
