package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

private val NewNoteGroups = setOf("notes.json", "blocks.json", "note_tables.json", "note_versions.json", "note_tags.json", "attachments.json")

/** Only new note families, never edits of records that appear anywhere in verified graph history. */
internal fun canPreserveGraphLocalNotes(
    records: List<CapturedBackupRecord>,
    remoteHistory: List<BackupRecordChange>,
    incoming: List<BackupRecordChange>,
): Boolean {
    if (records.isEmpty()) return false
    val seen = remoteHistory.map { BackupRecordIdentity(it.file, it.key) }.toSet()
    val identities = records.map { BackupRecordIdentity(it.group, it.key) }.toSet()
    val notes = records.filter { it.group == "notes.json" }.map { it.key.single() }.toSet()
    val touched = incoming.map { BackupRecordIdentity(it.file, it.key) }.toSet()
    if (notes.isEmpty() || identities.size != records.size) return false
    return records.all { record ->
        val row = record.payloadJson?.let(::JSONObject)
        record.operation == "UPSERT" && record.group in NewNoteGroups && row != null &&
            BackupRecordIdentity(record.group, record.key) !in seen &&
            IncrementalBackupFormat.key(record.group, row) == record.key &&
            row.isNull("deletedAt") &&
            (record.group == "notes.json" || row.optString("noteId") in notes) &&
            record.dependencies.all { it in identities || it !in touched }
    }
}

internal fun preservedGraphNotesJson(records: List<CapturedBackupRecord>) = JSONArray(records.map { record ->
    record.protocolChange().toJson().put("generation", record.generation)
})

internal fun readPreservedGraphNotes(frozen: JSONObject): List<CapturedBackupRecord> =
    frozen.optJSONArray("preservedLocalNotes")?.let { array ->
        BackupGraphIntentCodec.array(array).map { value ->
            val group = value.getString("file")
            val keys = value.getJSONArray("key")
            val key = (0 until keys.length()).map { keys.getString(it) }
            val row = value.getJSONObject("value")
            check(group in NewNoteGroups && value.getString("operation") == "upsert")
            check(IncrementalBackupFormat.key(group, row) == key)
            CapturedBackupRecord(group, key, "UPSERT", value.getLong("generation"), row.toString(), backupRecordDependencies(group, row))
        }
    }.orEmpty()
